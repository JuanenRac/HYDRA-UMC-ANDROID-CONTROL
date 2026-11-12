// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/SharedServicesTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.RobotCommandQueue
import com.hydraumc.control.network.UserProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the entry points of the app share: built on first use, once, and the same object for everyone. */
class SharedServicesTest {
    private class FakeStore : AuthStore {
        override suspend fun loadAuth(): UserProfile = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    @Test
    fun `nothing is built until it is asked for`() {
        val opened = AtomicInteger()
        SharedServices(authStoreFactory = { opened.incrementAndGet(); FakeStore() })
        assertEquals(0, opened.get())
    }

    @Test
    fun `the stored login is opened once and every caller gets the same one`() {
        val opened = AtomicInteger()
        val shared = SharedServices(authStoreFactory = { opened.incrementAndGet(); FakeStore() })
        val first = shared.authStore
        assertSame(first, shared.authStore)
        assertSame(first, shared.authStore)
        assertEquals(1, opened.get())
    }

    @Test
    fun `the queue of writes is one for all the entry points`() {
        val shared = SharedServices(authStoreFactory = { FakeStore() })
        assertSame(shared.commandQueue, shared.commandQueue)
    }

    @Test
    fun `callers from several threads at once still get a single login store`() {
        val opened = AtomicInteger()
        val shared = SharedServices(authStoreFactory = { opened.incrementAndGet(); Thread.sleep(20); FakeStore() })
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = (1..8).map { pool.submit<AuthStore> { start.await(); shared.authStore } }
        start.countDown()
        val stores = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, opened.get())
        assertTrue(stores.all { it === stores.first() })
    }

    @Test
    fun `a custom queue factory is used`() {
        val queue = RobotCommandQueue()
        val shared = SharedServices(authStoreFactory = { FakeStore() }, queueFactory = { queue })
        assertSame(queue, shared.commandQueue)
    }
}
