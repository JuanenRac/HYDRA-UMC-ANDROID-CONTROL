// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/TotpFlowTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.TotpEndpoint
import com.hydraumc.control.network.TotpFailure
import hydra.umc.client.HydraSuccess
import hydra.umc.client.HydraTotpConfirmed
import hydra.umc.client.HydraTotpEnrolment
import hydra.umc.client.HydraTotpStatus
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The second-factor screen as a state machine, with a fake server: turning it on, the codes shown once, new codes, turning it off, and every refusal. */
class TotpFlowTest {

    private val key = "JBSWY3DPEHPK3PXP"
    private val codes = listOf("0a1b2-c3d4e", "11111-22222", "33333-44444")

    private class Fake(
        var enabled: Boolean = false,
        var left: Long = 0,
        var refuse: HydraApiException? = null,
        var codes: List<String> = emptyList(),
        var key: String = "",
    ) : TotpEndpoint {
        val calls = mutableListOf<String>()
        private fun maybeRefuse() { refuse?.let { throw it } }
        override suspend fun totpStatus(): HydraTotpStatus { calls += "status"; maybeRefuse(); return HydraTotpStatus(enabled, left) }
        override suspend fun totpBegin(password: String): HydraTotpEnrolment {
            calls += "begin:$password"; maybeRefuse()
            return HydraTotpEnrolment(true, key, "otpauth://totp/HYDRA:ana?secret=$key")
        }
        override suspend fun totpConfirm(code: String): HydraTotpConfirmed { calls += "confirm:$code"; maybeRefuse(); return HydraTotpConfirmed(true, codes) }
        override suspend fun totpDisable(password: String, code: String): HydraSuccess { calls += "disable:$password:$code"; maybeRefuse(); return HydraSuccess(true) }
        override suspend fun totpNewRecoveryCodes(password: String, code: String): HydraTotpConfirmed {
            calls += "new:$password:$code"; maybeRefuse(); return HydraTotpConfirmed(true, codes)
        }
    }

    @Test
    fun itReadsTheStatusWhenItOpens() = runTest {
        val api = Fake(enabled = true, left = 7)
        val flow = TotpFlow(this) { api }
        flow.load()
        advanceUntilIdle()
        assertEquals(TotpStep.ON, flow.state.value.step)
        assertEquals(7, flow.state.value.recoveryLeft)
        assertFalse(flow.state.value.busy)
    }

    @Test
    fun turningItOnGoesThroughTheKeyThenTheCodeThenTheRecoveryCodes() = runTest {
        val api = Fake(key = key, codes = codes)
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        assertEquals(TotpStep.OFF, flow.state.value.step)

        flow.begin("pw"); advanceUntilIdle()
        assertEquals(TotpStep.ENROLLING, flow.state.value.step)
        assertEquals(key, flow.state.value.secret)
        assertTrue(flow.state.value.showsSecret)

        flow.confirm(" 123 456 "); advanceUntilIdle()
        assertEquals(TotpStep.RECOVERY, flow.state.value.step)
        assertEquals(codes, flow.state.value.recoveryCodes)
        assertEquals("confirm:123456", api.calls.last())

        flow.acknowledgeRecovery()
        val done = flow.state.value
        assertEquals(TotpStep.ON, done.step)
        assertEquals(TotpNotice.TURNED_ON, done.notice)
        assertEquals(3, done.recoveryLeft)
        assertTrue(done.recoveryCodes.isEmpty())
        assertEquals("", done.secret)
        assertEquals("", done.uri)
    }

    @Test
    fun givingUpBeforeConfirmingDropsTheKey() = runTest {
        val api = Fake(key = key)
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        flow.begin("pw"); advanceUntilIdle()
        flow.cancelEnrolment()
        assertEquals(TotpStep.OFF, flow.state.value.step)
        assertEquals("", flow.state.value.secret)
        assertFalse(flow.state.value.showsSecret)
    }

    @Test
    fun aKeyOfAnotherFormIsNotShown() = runTest {
        val api = Fake(key = "short")
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        flow.begin("pw"); advanceUntilIdle()
        assertEquals(TotpStep.OFF, flow.state.value.step)
        assertEquals(TotpFailure.Network, flow.state.value.failure)
        assertEquals("", flow.state.value.secret)
    }

    @Test
    fun fewRecoveryCodesLeftWarnsAndNewCodesAreShownOnceMore() = runTest {
        val api = Fake(enabled = true, left = 1, codes = codes)
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        assertTrue(flow.state.value.recoveryLow)

        flow.newRecoveryCodes("pw", "654321"); advanceUntilIdle()
        assertEquals(TotpStep.RECOVERY, flow.state.value.step)
        assertEquals(codes, flow.state.value.recoveryCodes)
        assertEquals("new:pw:654321", api.calls.last())

        flow.acknowledgeRecovery()
        assertEquals(TotpNotice.NEW_CODES, flow.state.value.notice)
        assertEquals(3, flow.state.value.recoveryLeft)
        assertFalse(flow.state.value.recoveryLow)
        assertTrue(flow.state.value.recoveryCodes.isEmpty())
    }

    @Test
    fun turningItOffNeedsThePasswordAndACode() = runTest {
        val api = Fake(enabled = true, left = 5)
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        flow.disable("", "123456"); advanceUntilIdle()
        flow.disable("pw", "  "); advanceUntilIdle()
        assertEquals(listOf("status"), api.calls)

        flow.disable("pw", "123 456"); advanceUntilIdle()
        assertEquals("disable:pw:123456", api.calls.last())
        assertEquals(TotpStep.OFF, flow.state.value.step)
        assertEquals(TotpNotice.TURNED_OFF, flow.state.value.notice)
    }

    @Test
    fun aWrongCodeWhenTurningOffIsTheCodeAndTheStepStays() = runTest {
        val api = Fake(enabled = true, left = 5)
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        api.refuse = HydraApiException("x", httpCode = 401, serverMessage = "A current code or an unused recovery code is required")
        flow.disable("pw", "000000"); advanceUntilIdle()
        assertEquals(TotpFailure.Code, flow.state.value.failure)
        assertEquals(TotpStep.ON, flow.state.value.step)
        assertFalse(flow.state.value.busy)
    }

    @Test
    fun anOlderServerSaysSoAndALimitSaysHowLong() = runTest {
        val api = Fake(refuse = HydraApiException("x", httpCode = 404))
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        assertEquals(TotpStep.UNSUPPORTED, flow.state.value.step)
        assertEquals(TotpFailure.Unsupported, flow.state.value.failure)

        api.refuse = HydraApiException("x", httpCode = 429, retryAfterSeconds = 20)
        flow.load(); advanceUntilIdle()
        assertEquals(TotpFailure.Wait(20), flow.state.value.failure)
    }

    @Test
    fun withoutAServerNothingIsCalledAndTheFailureIsTheNetwork() = runTest {
        val flow = TotpFlow(this) { null }
        flow.begin("pw")
        assertEquals(TotpFailure.Network, flow.state.value.failure)
        assertFalse(flow.state.value.busy)
        flow.dismissMessages()
        assertNull(flow.state.value.failure)
    }

    @Test
    fun closingTheScreenDropsTheKeyAndTheCodes() = runTest {
        val api = Fake(key = key, codes = codes)
        val flow = TotpFlow(this) { api }
        flow.load(); advanceUntilIdle()
        flow.begin("pw"); advanceUntilIdle()
        flow.confirm("123456"); advanceUntilIdle()
        flow.clear()
        val state = flow.state.value
        assertEquals(TotpStep.LOADING, state.step)
        assertTrue(state.recoveryCodes.isEmpty())
        assertEquals("", state.secret)
    }
}
