// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/WorkFileFlow.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiException

/** What the work-file screens do with a failed read of the server, told apart from the reading so it is tested without a phone. */
object WorkFileFlow {
    enum class ListOutcome { EMPTY_LIST, ERROR }

    /** A folder that does not exist yet is an empty list, not an error. */
    fun listOutcome(error: Exception): ListOutcome =
        if (error is HydraApiException && error.isNotFound) ListOutcome.EMPTY_LIST else ListOutcome.ERROR

    /** True when the failure means the session ended, so it must be renewed. */
    fun sessionEnded(error: Exception): Boolean = error is HydraApiException && error.isSessionProblem
}
