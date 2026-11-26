// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/UncaughtCoroutineRecorder.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Writes down every exception that no coroutine handled during the unit tests (the coroutines library finds it as a service, see
 * META-INF/services). Without it such an exception is only reported when a later `runTest` happens to start, and fails that test,
 * which has nothing to do with it. The test task reads the file, prints it and fails with the stack of the real source.
 */
class UncaughtCoroutineRecorder : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        val file = File(System.getProperty(LOG_PROPERTY) ?: "build/uncaught-coroutines.log")
        synchronized(UncaughtCoroutineRecorder::class.java) {
            file.parentFile?.mkdirs()
            file.appendText("thread=${Thread.currentThread().name} context=$context\n${exception.stackTraceToString()}\n")
        }
    }

    companion object {
        const val LOG_PROPERTY = "hydraUmc.uncaughtCoroutineLog"
    }
}
