// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/TotpRulesTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import hydra.umc.client.HydraTotpEnrolment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of the second sign-in factor without Android: typed codes, the key as shown, what an answer must look like, and how a refusal is told. */
class TotpRulesTest {

    @Test
    fun anAppCodeTypedWithSpacesOrDashesKeepsItsSixDigits() {
        assertEquals("123456", TotpRules.normalizeCode(" 123 456 "))
        assertEquals("123456", TotpRules.normalizeCode("123-456"))
        assertEquals("123456", TotpRules.normalizeCode("123456"))
    }

    @Test
    fun aRecoveryCodeIsKeptAsTypedInLowerCase() {
        assertEquals("a1b2c-d3e4f", TotpRules.normalizeCode("  A1B2C-D3E4F "))
        assertEquals("12345", TotpRules.normalizeCode("12345"))
        assertEquals("", TotpRules.normalizeCode("   "))
    }

    @Test
    fun theKeyIsShownInGroupsOfFourAndHiddenWithTheSameLength() {
        assertEquals("ABCD EFGH IJ", TotpRules.groupedKey("ABCDEFGHIJ"))
        val masked = TotpRules.maskedKey("ABCDEFGHIJKLMNOP")
        assertEquals(4 * 4 + 3, masked.length)
        assertFalse(masked.any { it.isLetter() })
    }

    @Test
    fun anEnrolmentWithAKeyOrAddressOfAnotherFormIsRefused() {
        val key = "JBSWY3DPEHPK3PXP"
        val good = HydraTotpEnrolment(true, key, "otpauth://totp/HYDRA:ana?secret=$key")
        assertNotNull(TotpRules.checkedEnrolment(good))
        assertNull(TotpRules.checkedEnrolment(good.copy(secret = "short")))
        assertNull(TotpRules.checkedEnrolment(good.copy(secret = key.lowercase())))
        assertNull(TotpRules.checkedEnrolment(good.copy(uri = "https://example.invalid/?secret=$key")))
    }

    @Test
    fun recoveryCodesMustBeTheFormTheServerMakes() {
        assertNotNull(TotpRules.checkedRecoveryCodes(listOf("0a1b2-c3d4e", "ffffe-00001")))
        assertNull(TotpRules.checkedRecoveryCodes(emptyList()))
        assertNull(TotpRules.checkedRecoveryCodes(listOf("0a1b2-c3d4e", "not a code")))
        assertNull(TotpRules.checkedRecoveryCodes(listOf("0A1B2-C3D4E")))
    }

    @Test
    fun theWarningStartsAtTwoCodesLeft() {
        assertTrue(TotpRules.recoveryLow(0))
        assertTrue(TotpRules.recoveryLow(2))
        assertFalse(TotpRules.recoveryLow(3))
    }

    @Test
    fun everyRefusalIsToldApart() {
        assertEquals(TotpFailure.Network, TotpRules.failureOf(null, false, null))
        assertEquals(TotpFailure.Unsupported, TotpRules.failureOf(404, false, null))
        assertEquals(TotpFailure.Forbidden, TotpRules.failureOf(403, false, null))
        assertEquals(TotpFailure.Already, TotpRules.failureOf(409, false, null))
        assertEquals(TotpFailure.Code, TotpRules.failureOf(400, false, null))
        assertEquals(TotpFailure.Password, TotpRules.failureOf(401, false, null))
        assertEquals(TotpFailure.Code, TotpRules.failureOf(401, true, null))
        assertEquals(TotpFailure.Http(500), TotpRules.failureOf(500, false, null))
    }

    @Test
    fun aWaitNamesTheSecondsOfTheServerOrThirty() {
        assertEquals(TotpFailure.Wait(45), TotpRules.failureOf(429, true, 45))
        assertEquals(TotpFailure.Wait(30), TotpRules.failureOf(429, true, null))
        assertEquals(TotpFailure.Wait(30), TotpRules.failureOf(429, true, 0))
    }

    @Test
    fun aWrongCodeWhenTurningOffIsTheCodeNotThePassword() {
        val message = "A current code or an unused recovery code is required"
        assertEquals(TotpFailure.Code, TotpRules.failureOf(401, false, null, needsCodeIfPasswordRefused = true, serverMessage = message))
        assertEquals(TotpFailure.Password, TotpRules.failureOf(401, false, null, needsCodeIfPasswordRefused = true, serverMessage = "Invalid credentials"))
        assertEquals(TotpFailure.Password, TotpRules.failureOf(401, false, null, needsCodeIfPasswordRefused = false, serverMessage = message))
    }

    @Test
    fun aThrownRefusalIsReadFromItsFields() {
        val wait = HydraApiException("x", httpCode = 429, totpRequired = true, retryAfterSeconds = 12)
        assertEquals(TotpFailure.Wait(12), TotpRules.failureOf(wait))
        assertEquals(TotpFailure.Network, TotpRules.failureOf(HydraApiException("down")))
    }

    @Test
    fun aKeyOrCodeNeverReachesTheTextOfTheState() {
        val state = com.hydraumc.control.viewmodel.TotpUiState(
            step = com.hydraumc.control.viewmodel.TotpStep.RECOVERY,
            secret = "JBSWY3DPEHPK3PXP",
            uri = "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP",
            recoveryCodes = listOf("0a1b2-c3d4e"),
        )
        val text = state.toString()
        assertFalse(text.contains("JBSWY3DPEHPK3PXP"))
        assertFalse(text.contains("0a1b2"))
    }
}
