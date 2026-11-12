// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/PairingTextTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.R
import com.hydraumc.control.util.PairingLink
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every reason a pairing link is refused has a text, and the ones the person can fix name what to fix. */
class PairingTextTest {
    @Test
    fun everyProblemHasAText() {
        for (problem in PairingLink.Problem.values()) PairingText.of(problem)
    }

    @Test
    fun theProblemsThePersonCanFixNameWhatToFix() {
        assertEquals(R.string.pairing_problem_host, PairingText.of(PairingLink.Problem.BAD_HOST))
        assertEquals(R.string.pairing_problem_host, PairingText.of(PairingLink.Problem.MISSING_HOST))
        assertEquals(R.string.pairing_problem_port, PairingText.of(PairingLink.Problem.BAD_PORT))
        assertEquals(R.string.pairing_problem_fingerprint, PairingText.of(PairingLink.Problem.BAD_FINGERPRINT))
        assertEquals(R.string.pairing_problem_missing_fingerprint, PairingText.of(PairingLink.Problem.MISSING_FINGERPRINT))
        assertEquals(R.string.pairing_problem_name, PairingText.of(PairingLink.Problem.BAD_NAME))
        assertEquals(R.string.pairing_problem_not_link, PairingText.of(PairingLink.Problem.EMPTY))
        assertEquals(R.string.pairing_problem_generic, PairingText.of(PairingLink.Problem.REPEATED_FIELD))
    }
}
