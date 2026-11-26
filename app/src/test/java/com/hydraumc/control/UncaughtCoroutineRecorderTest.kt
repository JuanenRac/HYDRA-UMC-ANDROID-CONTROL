// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/UncaughtCoroutineRecorderTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import java.io.File
import java.util.ServiceLoader
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import org.junit.Assert.assertTrue
import org.junit.Test

class UncaughtCoroutineRecorderTest {
    @Test
    fun theCoroutinesLibraryFindsTheRecorderAsAService() {
        val found = ServiceLoader.load(CoroutineExceptionHandler::class.java, CoroutineExceptionHandler::class.java.classLoader)
        assertTrue(found.any { it is UncaughtCoroutineRecorder })
    }

    @Test
    fun anExceptionIsWrittenWithItsMessageAndItsThread() {
        val file = File.createTempFile("uncaught", ".log")
        val before = System.getProperty(UncaughtCoroutineRecorder.LOG_PROPERTY)
        System.setProperty(UncaughtCoroutineRecorder.LOG_PROPERTY, file.absolutePath)
        try {
            UncaughtCoroutineRecorder().handleException(EmptyCoroutineContext, IllegalStateException("leaked on purpose"))
            val text = file.readText()
            assertTrue(text.contains("leaked on purpose"))
            assertTrue(text.contains("thread=${Thread.currentThread().name}"))
        } finally {
            if (before == null) System.clearProperty(UncaughtCoroutineRecorder.LOG_PROPERTY)
            else System.setProperty(UncaughtCoroutineRecorder.LOG_PROPERTY, before)
            file.delete()
        }
    }
}
