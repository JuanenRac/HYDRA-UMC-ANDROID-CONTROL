// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/TotpFlow.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.TotpEndpoint
import com.hydraumc.control.network.TotpFailure
import com.hydraumc.control.network.TotpRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of the second-factor screen of the signed-in account. */
enum class TotpStep { LOADING, UNSUPPORTED, OFF, ENROLLING, RECOVERY, ON }

/** What the screen says after a step went well (the screen turns each into its translated text). */
enum class TotpNotice { TURNED_ON, TURNED_OFF, NEW_CODES }

/**
 * What the second-factor screen shows. The key ([secret], [uri]) and the codes ([recoveryCodes]) live here only while the person is looking at
 * them: they are dropped when the step ends, are never written to a log, a store or a state that outlives the screen, and [toString] leaves them out.
 */
data class TotpUiState(
    val step: TotpStep = TotpStep.LOADING,
    val recoveryLeft: Long = 0,
    val secret: String = "",
    val uri: String = "",
    val recoveryCodes: List<String> = emptyList(),
    val busy: Boolean = false,
    val failure: TotpFailure? = null,
    val notice: TotpNotice? = null,
) {
    /** The warning to offer new recovery codes: the account has the second factor and few codes left. */
    val recoveryLow: Boolean get() = step == TotpStep.ON && TotpRules.recoveryLow(recoveryLeft)

    /** True while a key or a list of codes is on the state, so the screen can keep itself out of captures. */
    val showsSecret: Boolean get() = step == TotpStep.ENROLLING || step == TotpStep.RECOVERY

    override fun toString(): String = "TotpUiState(step=$step, recoveryLeft=$recoveryLeft, busy=$busy, failure=$failure, notice=$notice)"
}

/**
 * The second factor of the signed-in account as a small state machine, away from the screen so it is tested on the JVM with a fake endpoint:
 * read the status, turn it on (password, then the key, then the first code, then the one-time recovery codes), turn it off (password and a code)
 * and make new recovery codes (password and a code). [endpoint] is read at every call because the client changes with the session.
 */
class TotpFlow(private val scope: CoroutineScope, private val endpoint: () -> TotpEndpoint?) {
    private val mutable = MutableStateFlow(TotpUiState())
    val state: StateFlow<TotpUiState> = mutable.asStateFlow()

    /** Reads the status; the screen calls it when it opens. */
    fun load() {
        mutable.update { TotpUiState() }
        run { api ->
            val status = api.totpStatus()
            mutable.update { TotpUiState(step = if (status.enabled) TotpStep.ON else TotpStep.OFF, recoveryLeft = status.recoveryLeft.coerceAtLeast(0)) }
        }
    }

    /** First step of turning it on: the password asks the server for a new key. */
    fun begin(password: String) {
        if (password.isEmpty()) return
        run { api ->
            val enrolment = TotpRules.checkedEnrolment(api.totpBegin(password)) ?: throw HydraApiException("The key of the server has not the form of the contract")
            mutable.update { it.copy(step = TotpStep.ENROLLING, secret = enrolment.secret, uri = enrolment.uri) }
        }
    }

    /** The person gives up before confirming: the key is dropped (the server keeps nothing in force until a code is confirmed). */
    fun cancelEnrolment() {
        mutable.update { TotpUiState(step = TotpStep.OFF, recoveryLeft = it.recoveryLeft) }
    }

    /** Second step: the first code made from the key turns the second factor on and brings the recovery codes. */
    fun confirm(code: String) {
        val clean = TotpRules.normalizeCode(code)
        if (clean.isEmpty()) return
        run { api ->
            val codes = TotpRules.checkedRecoveryCodes(api.totpConfirm(clean).recoveryCodes) ?: throw HydraApiException("The recovery codes of the server have not the form of the contract")
            mutable.update { TotpUiState(step = TotpStep.RECOVERY, recoveryLeft = codes.size.toLong(), recoveryCodes = codes) }
        }
    }

    /** The person says the recovery codes are saved: they are dropped for good, the screen goes back to the status. */
    fun acknowledgeRecovery() {
        val notice = if (mutable.value.step == TotpStep.RECOVERY && mutable.value.notice == TotpNotice.NEW_CODES) TotpNotice.NEW_CODES else TotpNotice.TURNED_ON
        mutable.update { TotpUiState(step = TotpStep.ON, recoveryLeft = it.recoveryLeft, notice = notice) }
    }

    /** Turns the second factor off with the password and a current or recovery code. */
    fun disable(password: String, code: String) {
        val clean = TotpRules.normalizeCode(code)
        if (password.isEmpty() || clean.isEmpty()) return
        run(needsCodeIfPasswordRefused = true) { api ->
            api.totpDisable(password, clean)
            mutable.update { TotpUiState(step = TotpStep.OFF, notice = TotpNotice.TURNED_OFF) }
        }
    }

    /** Makes a new set of recovery codes (the old ones stop working); the codes are shown once, like the first set. */
    fun newRecoveryCodes(password: String, code: String) {
        val clean = TotpRules.normalizeCode(code)
        if (password.isEmpty() || clean.isEmpty()) return
        run(needsCodeIfPasswordRefused = true) { api ->
            val codes = TotpRules.checkedRecoveryCodes(api.totpNewRecoveryCodes(password, clean).recoveryCodes) ?: throw HydraApiException("The recovery codes of the server have not the form of the contract")
            mutable.update { TotpUiState(step = TotpStep.RECOVERY, recoveryLeft = codes.size.toLong(), recoveryCodes = codes, notice = TotpNotice.NEW_CODES) }
        }
    }

    /** Drops everything the state holds (the screen closed, the session ended). */
    fun clear() {
        mutable.value = TotpUiState()
    }

    /** Hides the last notice or refusal once the screen has shown it. */
    fun dismissMessages() {
        mutable.update { it.copy(failure = null, notice = null) }
    }

    private fun run(needsCodeIfPasswordRefused: Boolean = false, call: suspend (TotpEndpoint) -> Unit) {
        val api = endpoint()
        if (api == null) {
            mutable.update { it.copy(failure = TotpFailure.Network) }
            return
        }
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, failure = null, notice = null) }
        scope.launch {
            try {
                call(api)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: HydraApiException) {
                val failure = TotpRules.failureOf(e, needsCodeIfPasswordRefused)
                mutable.update { it.copy(busy = false, failure = failure, step = if (failure == TotpFailure.Unsupported) TotpStep.UNSUPPORTED else it.step) }
                return@launch
            } catch (e: RuntimeException) {
                mutable.update { it.copy(busy = false, failure = TotpFailure.Network) }
                return@launch
            }
            mutable.update { it.copy(busy = false) }
        }
    }
}
