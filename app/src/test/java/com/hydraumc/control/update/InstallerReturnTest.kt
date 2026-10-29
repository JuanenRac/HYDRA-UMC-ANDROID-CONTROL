// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Update state after the system installer closes
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.update

import com.hydraumc.control.viewmodel.AppUpdateState
import com.hydraumc.control.viewmodel.stateAfterInstallerReturn
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallerReturnTest {
    private val update = AvailableUpdate(
        version = SemanticVersion(9, 9, 9),
        releaseName = "release",
        notes = "",
        assetUrl = "https://example.invalid/app.apk",
    )

    @Test
    fun cancelledInstallerOffersTheUpdateAgain() {
        assertEquals(AppUpdateState.Available(update), stateAfterInstallerReturn(AppUpdateState.Installing, update))
    }

    @Test
    fun otherStatesAreLeftAlone() {
        for (state in listOf(AppUpdateState.Idle, AppUpdateState.UpToDate, AppUpdateState.Failed("x"))) {
            assertEquals(state, stateAfterInstallerReturn(state, update))
        }
    }

    @Test
    fun withoutAPendingUpdateNothingChanges() {
        assertEquals(AppUpdateState.Installing, stateAfterInstallerReturn(AppUpdateState.Installing, null))
    }
}
