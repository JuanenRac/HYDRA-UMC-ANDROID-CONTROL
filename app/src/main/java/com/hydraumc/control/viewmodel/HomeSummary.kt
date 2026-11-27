// =============================================================================
// HYDRA-UMC CONTROL - What the home screen says, away from the screen
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Both rules are plain functions of data the app already has: nothing here asks the server for anything.
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.WsStatus

/** The one line at the top of the home screen. The worst thing wins. */
sealed class HomeState {
    data object Connecting : HomeState()
    data object NotConnected : HomeState()
    data object NoRobots : HomeState()
    data class SafeMode(val count: Int) : HomeState()
    data class Offline(val count: Int) : HomeState()
    data class Moving(val count: Int) : HomeState()
    data class Ready(val robots: Int) : HomeState()

    companion object {
        fun of(connection: WsStatus, robots: List<RobotState>): HomeState = when {
            connection == WsStatus.CONNECTING -> Connecting
            connection != WsStatus.CONNECTED -> NotConnected
            robots.isEmpty() -> NoRobots
            robots.any { !it.isEnabled } -> SafeMode(robots.count { !it.isEnabled })
            robots.any { !it.online } -> Offline(robots.count { !it.online })
            robots.any { moving(it) } -> Moving(robots.count { moving(it) })
            else -> Ready(robots.size)
        }

        fun moving(robot: RobotState): Boolean = robot.isPlaying && !robot.isPaused
    }
}

/** The steps of an installation, the same rule as HYDRA-UMC STUDIO: what is not known is never shown as pending. */
enum class SetupStep { SERVER, CONTROLLER, ROBOT, SECOND_FACTOR }

data class SetupSteps(val pending: List<SetupStep>, val done: Int, val known: Int) {
    companion object {
        /** Each argument is null when the app does not know the answer. */
        fun of(server: Boolean?, controller: Boolean?, robot: Boolean?, secondFactor: Boolean?): SetupSteps {
            val answers = listOf(SetupStep.SERVER to server, SetupStep.CONTROLLER to controller, SetupStep.ROBOT to robot, SetupStep.SECOND_FACTOR to secondFactor)
            return SetupSteps(
                pending = answers.filter { it.second == false }.map { it.first },
                done = answers.count { it.second == true },
                known = answers.count { it.second != null },
            )
        }
    }
}
