// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/MjpegStreamParserTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * MjpegStreamParser's own real frame decode, both branches added for the
 * hardware-accelerated ImageDecoder path (API 28+, see MjpegPlayer.kt's own
 * comment on why) and the pre-existing CPU-only BitmapFactory fallback (API
 * <28, or any hardware-decode failure). Uses a real JPEG - encoded with the
 * host JVM's own javax.imageio, not a hand-rolled byte constant or a mock -
 * as input; the point of this test is the parser's own frame-boundary and
 * decode-fallback logic, not re-verifying that a JPEG codec works.
 *
 * This does NOT verify a real GPU-resident Bitmap.Config.HARDWARE buffer -
 * there is no real GPU on the host JVM/Robolectric runs this on, and the
 * `useHardwareBitmap` requests below only prove decodeFrame() takes the
 * ImageDecoder branch (or falls back cleanly) without crashing, not that a
 * real device's own hardware decode path was exercised. Not verified
 * against real Android hardware in this dev environment (Windows-only, no
 * physical device) - same honestly-documented-gap precedent as
 * HYDRA-UMC-WATCH/IOS-CONTROL's own native gaps.
 */
@RunWith(RobolectricTestRunner::class)
class MjpegStreamParserTest {

    private fun realJpegFrame(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                image.setRGB(x, y, 0xFF0000)
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    @Test
    @Config(sdk = [24])
    fun `decodes a real frame via the pre-existing CPU path on API below 28, even when hardware is requested`() {
        val stream = ByteArrayInputStream(realJpegFrame())
        MjpegStreamParser(stream).use { parser ->
            val result = parser.readNextFrame(useHardwareBitmap = true)
            assertTrue("expected a decoded Frame, got $result", result is MjpegFrameResult.Frame)
        }
    }

    @Test
    @Config(sdk = [34])
    fun `decodes a real frame when useHardwareBitmap is requested on API 28+`() {
        val stream = ByteArrayInputStream(realJpegFrame())
        MjpegStreamParser(stream).use { parser ->
            val result = parser.readNextFrame(useHardwareBitmap = true)
            assertTrue("expected a decoded Frame, got $result", result is MjpegFrameResult.Frame)
        }
    }

    @Test
    @Config(sdk = [34])
    fun `decodes a real frame with useHardwareBitmap disabled, same as before this change`() {
        val stream = ByteArrayInputStream(realJpegFrame())
        MjpegStreamParser(stream).use { parser ->
            val result = parser.readNextFrame(useHardwareBitmap = false)
            assertTrue("expected a decoded Frame, got $result", result is MjpegFrameResult.Frame)
        }
    }

    @Test
    @Config(sdk = [34])
    fun `a genuinely undecodable frame never crashes readNextFrame`() {
        // Real, observed behavior (both before and after this change,
        // unrelated to the new hardware-decode branch): a byte sequence
        // with a valid SOI/EOI boundary but no valid JPEG in between makes
        // the decoder itself throw rather than return null, which
        // readNextFrame()'s own outer catch already treats the same as a
        // genuinely broken connection (StreamEnded) - not a new behavior,
        // just confirming this pre-existing contract still holds with the
        // decode call now going through decodeFrame() instead of a direct
        // BitmapFactory call.
        val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val stream = ByteArrayInputStream(garbage)
        MjpegStreamParser(stream).use { parser ->
            val result = parser.readNextFrame(useHardwareBitmap = true)
            assertTrue(
                "expected CorruptFrame or StreamEnded, never a thrown exception, got $result",
                result is MjpegFrameResult.CorruptFrame || result is MjpegFrameResult.StreamEnded,
            )
        }
    }
}
