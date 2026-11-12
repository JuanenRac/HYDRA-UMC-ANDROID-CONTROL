// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/MainDispatcherSwap.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Puts the test dispatcher in place of the main one and takes it out again, whatever a test before it left behind.
 *
 * The coroutines library refuses to change the main dispatcher while another thread is reading it at that very moment
 * (an IllegalStateException). A request of the previous test that is still finishing on a worker thread does exactly that,
 * so a bare setMain/resetMain failed now and then, depending on the speed of the runner. Here the change is tried again
 * for a short while, which makes it independent of the order and the timing of the tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object MainDispatcherSwap {
    /** How many times the change is tried before the error is let through. */
    internal const val ATTEMPTS = 100

    /** The pause between two tries, in milliseconds. */
    internal const val PAUSE_MS = 10L

    /** Installs a fresh test dispatcher as the main one; safe to call when one is already installed. */
    fun set() = retried { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    /** Restores the real main dispatcher; safe to call when none was installed. */
    fun reset() = retried { Dispatchers.resetMain() }

    internal fun retried(change: () -> Unit) {
        var last: IllegalStateException? = null
        repeat(ATTEMPTS) {
            try {
                change()
                return
            } catch (error: IllegalStateException) {
                last = error
                Thread.sleep(PAUSE_MS)
            }
        }
        throw last!!
    }
}
