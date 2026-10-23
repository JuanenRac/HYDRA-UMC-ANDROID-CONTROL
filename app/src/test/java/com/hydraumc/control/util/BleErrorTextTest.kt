// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/BleErrorTextTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.network.BleError
import com.hydraumc.control.network.BleErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every Bluetooth error reads as a sentence, with the platform code appended when there is one, in each language. */
@RunWith(RobolectricTestRunner::class)
class BleErrorTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `every error kind has its own non-empty text`() {
        val texts = BleErrorKind.entries.map { context.getString(bleErrorResource(it)) }
        assertTrue(texts.all { it.isNotBlank() })
        assertEquals("two kinds share a text", texts.size, texts.toSet().size)
    }

    @Test fun `the status code is appended`() {
        val plain = bleErrorMessage(context, BleError(BleErrorKind.WRITE_FAILED))
        val withCode = bleErrorMessage(context, BleError(BleErrorKind.WRITE_FAILED, 133))
        assertTrue(withCode.startsWith(plain))
        assertTrue(withCode.contains("133"))
    }

    @Test @Config(qualifiers = "es") fun `the spanish text is used on a spanish phone`() {
        assertEquals("Este dispositivo no tiene Bluetooth.", bleErrorMessage(context, BleError(BleErrorKind.BLUETOOTH_UNAVAILABLE)))
    }

    @Test @Config(qualifiers = "pt") fun `the portuguese text is used on a portuguese phone`() {
        assertEquals("Este dispositivo não tem Bluetooth.", bleErrorMessage(context, BleError(BleErrorKind.BLUETOOTH_UNAVAILABLE)))
    }
}
