// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/TotpEndpoint.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import hydra.umc.client.HydraSuccess
import hydra.umc.client.HydraTotpConfirmed
import hydra.umc.client.HydraTotpEnrolment
import hydra.umc.client.HydraTotpStatus

/** What the second-factor screen needs from the server (the REST client in the app, a fake in the tests). Every call throws [HydraApiException] on a refusal. */
interface TotpEndpoint {
    /** GET /api/totp/status */
    suspend fun totpStatus(): HydraTotpStatus

    /** POST /api/totp/begin: a new key, not in force until a code made from it is confirmed. */
    suspend fun totpBegin(password: String): HydraTotpEnrolment

    /** POST /api/totp/confirm: turns the second factor on and returns the one-time recovery codes. */
    suspend fun totpConfirm(code: String): HydraTotpConfirmed

    /** POST /api/totp/disable */
    suspend fun totpDisable(password: String, code: String): HydraSuccess

    /** POST /api/totp/recovery-codes: replaces the recovery codes and returns the new ones. */
    suspend fun totpNewRecoveryCodes(password: String, code: String): HydraTotpConfirmed
}
