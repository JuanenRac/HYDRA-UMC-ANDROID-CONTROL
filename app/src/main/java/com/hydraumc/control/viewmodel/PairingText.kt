// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/PairingText.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import androidx.annotation.StringRes
import com.hydraumc.control.R
import com.hydraumc.control.util.PairingLink

/** Which text tells the person why a pairing link was refused. */
object PairingText {
    @StringRes
    fun of(problem: PairingLink.Problem): Int = when (problem) {
        PairingLink.Problem.BAD_HOST, PairingLink.Problem.MISSING_HOST -> R.string.pairing_problem_host
        PairingLink.Problem.BAD_PORT -> R.string.pairing_problem_port
        PairingLink.Problem.BAD_FINGERPRINT -> R.string.pairing_problem_fingerprint
        PairingLink.Problem.MISSING_FINGERPRINT -> R.string.pairing_problem_missing_fingerprint
        PairingLink.Problem.BAD_NAME -> R.string.pairing_problem_name
        PairingLink.Problem.NOT_A_PAIRING_LINK, PairingLink.Problem.EMPTY -> R.string.pairing_problem_not_link
        PairingLink.Problem.TOO_LONG, PairingLink.Problem.REPEATED_FIELD, PairingLink.Problem.BAD_ENCODING -> R.string.pairing_problem_generic
    }
}
