// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/TotpRules.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import hydra.umc.client.HydraTotpEnrolment

/*
 * The rules of the second sign-in factor (an authenticator-app code), away from the screens: how a refusal is told apart, how a typed
 * code is cleaned, how the key is shown, and what an answer must look like before the screen draws it. Pure Kotlin, tested on the JVM.
 * Nothing here stores or logs a key or a code.
 */

/** Why a call of the second factor did not work, in terms a screen can translate. */
sealed class TotpFailure {
    /** The server has no such route (an older server answers 404). */
    object Unsupported : TotpFailure()

    /** The password was refused. */
    object Password : TotpFailure()

    /** The code was refused, or none was sent where one is needed. */
    object Code : TotpFailure()

    /** Too many wrong codes: try again after [seconds]. */
    data class Wait(val seconds: Int) : TotpFailure()

    /** The second factor is already on (409). */
    object Already : TotpFailure()

    /** The account may not do this (403). */
    object Forbidden : TotpFailure()

    /** Any other refusal; [status] is the HTTP status. */
    data class Http(val status: Int) : TotpFailure()

    /** The server could not be reached, or its answer had not the shape of the contract. */
    object Network : TotpFailure()
}

object TotpRules {
    /** From this many recovery codes left down, the screen warns and offers new ones. */
    const val RECOVERY_LOW = 2

    /** The wait shown when a 429 names none. */
    const val DEFAULT_WAIT_SECONDS = 30

    private val SECRET = Regex("^[A-Z2-7]{16,128}$")
    private val RECOVERY_CODE = Regex("^[0-9a-f]{5}-[0-9a-f]{5}$")
    private val SIX_DIGITS = Regex("^\d{6}$")

    /** True when the account has so few recovery codes left that new ones should be made. */
    fun recoveryLow(left: Long): Boolean = left <= RECOVERY_LOW

    /** A code typed with spaces or dashes out of place: the six digits of an app code are kept as six digits, a recovery code is lower-cased as typed. */
    fun normalizeCode(text: String): String {
        val trimmed = text.trim()
        val digits = trimmed.replace(Regex("[\s-]"), "")
        return if (SIX_DIGITS.matches(digits)) digits else trimmed.lowercase()
    }

    /** The key in groups of four letters, easier to read and type. */
    fun groupedKey(secret: String): String = secret.chunked(4).joinToString(" ")

    /** The key hidden but for its length (a row of bullets in groups of four), for a screen that is not yet showing it. */
    fun maskedKey(secret: String): String = groupedKey("\u2022".repeat(secret.length))

    /** The enrolment as the screen may draw it, or null when its key or its address is not what the server contract describes. */
    fun checkedEnrolment(enrolment: HydraTotpEnrolment): HydraTotpEnrolment? =
        enrolment.takeIf { SECRET.matches(it.secret) && it.uri.startsWith("otpauth://totp/") }

    /** The recovery codes as the screen may draw them, or null when the list is empty or one of them is not of the form the server makes. */
    fun checkedRecoveryCodes(codes: List<String>): List<String>? =
        codes.takeIf { it.isNotEmpty() && it.all(RECOVERY_CODE::matches) }

    /**
     * Tells a refusal of one of the second-factor calls: [totpRequired] is the flag of the sign-in answer, [retryAfterSeconds] the `Retry-After` of a 429,
     * and [needsCodeIfPasswordRefused] says that the password was already checked before the code (turning it off, new recovery codes), so a 401 that
     * is not a sign-in problem is the code.
     */
    fun failureOf(
        status: Int?,
        totpRequired: Boolean,
        retryAfterSeconds: Int?,
        needsCodeIfPasswordRefused: Boolean = false,
        serverMessage: String? = null,
    ): TotpFailure = when {
        status == null -> TotpFailure.Network
        status == 404 -> TotpFailure.Unsupported
        status == 403 -> TotpFailure.Forbidden
        status == 429 -> TotpFailure.Wait(retryAfterSeconds?.takeIf { it > 0 } ?: DEFAULT_WAIT_SECONDS)
        status == 409 -> TotpFailure.Already
        status == 401 && totpRequired -> TotpFailure.Code
        status == 401 && needsCodeIfPasswordRefused && serverMessage?.contains("code", ignoreCase = true) == true -> TotpFailure.Code
        status == 401 -> TotpFailure.Password
        status == 400 -> TotpFailure.Code
        else -> TotpFailure.Http(status)
    }

    /** The failure a thrown call stands for. */
    fun failureOf(e: HydraApiException, needsCodeIfPasswordRefused: Boolean = false): TotpFailure =
        failureOf(e.httpCode, e.totpRequired, e.retryAfterSeconds, needsCodeIfPasswordRefused, e.serverMessage)
}
