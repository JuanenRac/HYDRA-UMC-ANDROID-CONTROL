// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - SharedServices.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import android.app.Application
import android.content.Context
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.RobotCommandQueue

/**
 * What the screens, the watch relay and the other entry points of the app share for the whole life of the process, built once:
 * the stored login (opening the encrypted store is a keystore round-trip that took seconds, and every entry point used to open its own)
 * and the per-robot queue of writes (so two entry points never send to the same robot in parallel). It holds no Android class, so it is tested on the JVM.
 */
class SharedServices(authStoreFactory: () -> AuthStore, queueFactory: () -> RobotCommandQueue = { RobotCommandQueue() }) {
    /** The stored login, opened on first use and then reused. */
    val authStore: AuthStore by lazy(authStoreFactory)

    /** The writes of each robot, one line per robot for every entry point. */
    val commandQueue: RobotCommandQueue by lazy(queueFactory)
}

/** The application of the app: it only holds [SharedServices]. */
class HydraApplication : Application() {
    val shared: SharedServices by lazy { SharedServices(authStoreFactory = { AuthPrefs(this) }) }
}

/** The shared services of the process when the app runs under [HydraApplication]; null in a test host that uses a plain application. */
fun Context.sharedServicesOrNull(): SharedServices? = (applicationContext as? HydraApplication)?.shared
