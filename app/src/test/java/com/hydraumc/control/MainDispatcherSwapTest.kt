// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/MainDispatcherSwapTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The swap of the main dispatcher must work in any order and while a worker thread of an earlier test still reads it. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherSwapTest {
    @After
    fun cleanUp() = MainDispatcherSwap.reset()

    @Test
    fun `reset without a previous set does nothing wrong`() {
        MainDispatcherSwap.reset()
        MainDispatcherSwap.reset()
    }

    @Test
    fun `set twice in a row and then reset leaves the main dispatcher usable`() {
        MainDispatcherSwap.set()
        MainDispatcherSwap.set()
        val ran = AtomicBoolean(false)
        runBlocking { withContext(Dispatchers.Main) { ran.set(true) } }
        assertTrue(ran.get())
        MainDispatcherSwap.reset()
    }

    @Test
    fun `a try that fails once is repeated and then succeeds`() {
        val calls = AtomicInteger()
        MainDispatcherSwap.retried {
            if (calls.incrementAndGet() < 3) throw IllegalStateException("busy")
        }
        assertEquals(3, calls.get())
    }

    @Test
    fun `an error that never goes away is let through after the last try`() {
        val calls = AtomicInteger()
        val error = assertThrows(IllegalStateException::class.java) {
            MainDispatcherSwap.retried {
                calls.incrementAndGet()
                throw IllegalStateException("always busy")
            }
        }
        assertEquals("always busy", error.message)
        assertEquals(MainDispatcherSwap.ATTEMPTS, calls.get())
    }

    @Test
    fun `other errors are not swallowed`() {
        val calls = AtomicInteger()
        assertThrows(UnsupportedOperationException::class.java) {
            MainDispatcherSwap.retried {
                calls.incrementAndGet()
                throw UnsupportedOperationException("not a busy dispatcher")
            }
        }
        assertEquals(1, calls.get())
    }

    @Test
    fun `swapping while a worker thread keeps reading the main dispatcher does not fail`() {
        MainDispatcherSwap.set()
        val stop = AtomicBoolean(false)
        val scope = CoroutineScope(Dispatchers.Default)
        val reader = scope.launch {
            while (!stop.get()) {
                try {
                    withContext(Dispatchers.Main) { /* touch the main dispatcher */ }
                } catch (_: Throwable) {
                    // between the reset and the next set the real main dispatcher does not exist on the JVM: that is fine here
                }
            }
        }
        try {
            repeat(50) {
                MainDispatcherSwap.reset()
                MainDispatcherSwap.set()
            }
        } finally {
            stop.set(true)
            runBlocking { reader.join() }
        }
        assertFalse(reader.isActive)
    }
}
