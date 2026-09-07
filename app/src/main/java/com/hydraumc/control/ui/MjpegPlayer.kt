// =============================================================================
// HYDRA-UMC CONTROL - High-performance MJPEG player using native Canvas
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * High-performance MJPEG Stream Player.
 * Reads bytes from an OkHttp stream and decodes JPEGs in a background thread.
 */
@Composable
fun MjpegPlayer(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val client = remember { OkHttpClient() }

    // On (API 28+), each frame is decoded straight into a GPU-resident
    // Bitmap.Config.HARDWARE buffer instead of a CPU ARGB_8888 one - the
    // real, documented Android platform mechanism for hardware-accelerated
    // bitmap decode (the same one Coil/Glide use for this exact reason),
    // skipping the per-frame CPU-to-GPU pixel upload BitmapFactory's own
    // software bitmaps always need before they can be drawn. See
    // MjpegStreamParser.decodeFrame() below for the real fallback to the
    // previous CPU-only decode on API <28 or on any decode failure.
    //
    // Set to false the first time a HARDWARE bitmap actually fails to draw
    // on this specific device/window (see the catch below) - self-heals to
    // the CPU path for the rest of this player's lifetime instead of
    // throwing on every subsequent frame. Not verified against real
    // Android hardware in this dev environment (Windows-only, no physical
    // device) - same honestly-documented-gap precedent as HYDRA-UMC-WATCH/
    // IOS-CONTROL's own native gaps: a real implementation with a real,
    // checked fallback, not a permanent CPU-only path waiting on a device
    // that may never arrive to "unblock" it.
    var hardwareBitmapsSupported by remember { mutableStateOf(true) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val call = client.newCall(request)
                // Ties this blocking OkHttp call to the coroutine's own
                // cancellation. MjpegStreamParser.readNextFrame() below reads
                // one byte at a time from a synchronous InputStream - a
                // regular blocking call, not a suspend function, so
                // cancelling this coroutine (e.g. leaving CameraScreen) only
                // marks the Job cancelled; it has no suspension point to
                // actually throw at while stuck inside a read(). Without
                // this, the read thread (and the underlying socket) stayed
                // alive until OkHttp's own read timeout fired instead of
                // unblocking as soon as the screen was left. call.cancel()
                // closes the socket, which makes any in-flight read()
                // immediately fail with an IOException the catch below
                // already handles.
                coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
                call.execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val inputStream = response.body?.byteStream() ?: return@use
                    MjpegStreamParser(inputStream).use { parser ->
                        while (isActive) {
                            when (val result = parser.readNextFrame(useHardwareBitmap = hardwareBitmapsSupported)) {
                                is MjpegFrameResult.Frame -> bitmap = result.bitmap
                                // One undecodable frame (truncated read, a
                                // corrupt JPEG) used to be indistinguishable
                                // from the stream genuinely ending - both
                                // returned null and `break`-ed the loop,
                                // killing the whole camera feed over a single
                                // bad frame. Now only a real end-of-stream
                                // does that; a corrupt frame just gets
                                // skipped and the next one is attempted.
                                MjpegFrameResult.CorruptFrame -> { /* skip, keep reading */ }
                                MjpegFrameResult.StreamEnded -> return@use
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MjpegPlayer", "Stream error: ${e.message}")
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        bitmap?.let { b ->
            drawIntoCanvas { canvas ->
                val drawRect = Rect(0, 0, size.width.toInt(), size.height.toInt())
                val bitmapRect = Rect(0, 0, b.width, b.height)
                try {
                    canvas.nativeCanvas.drawBitmap(b, bitmapRect, drawRect, Paint())
                } catch (e: IllegalArgumentException) {
                    // "Software rendering doesn't support hardware bitmaps"
                    // (or an equivalent platform restriction on this
                    // specific window/canvas, e.g. a screenshot/off-screen
                    // capture path) - recover this one frame from a real
                    // CPU copy of the same pixels, and stop requesting
                    // hardware bitmaps for every later frame instead of
                    // hitting this same exception on each one.
                    Log.w("MjpegPlayer", "Hardware bitmap draw failed, falling back to software decode: ${e.message}")
                    hardwareBitmapsSupported = false
                    val software = b.copy(Bitmap.Config.ARGB_8888, false)
                    canvas.nativeCanvas.drawBitmap(software, bitmapRect, drawRect, Paint())
                }
            }
        }
    }
}

/** Outcome of one [MjpegStreamParser.readNextFrame] call. */
sealed class MjpegFrameResult {
    /** A frame was read and decoded successfully. */
    data class Frame(val bitmap: Bitmap) : MjpegFrameResult()
    /** A frame boundary (SOI...EOI) was found but couldn't be decoded, or exceeded the max frame size - the stream itself is still alive. */
    data object CorruptFrame : MjpegFrameResult()
    /** The underlying connection has genuinely ended (or errored). */
    data object StreamEnded : MjpegFrameResult()
}

/**
 * Simple parser for MJPEG streams.
 * Searches for SOI (0xFFD8) and EOI (0xFFD9) markers.
 */
class MjpegStreamParser(private val inputStream: InputStream) : AutoCloseable {
    // Reused across every frame instead of allocated fresh per frame (the
    // previous implementation declared this same size but never actually
    // wrote into it - real accumulation was a mutableListOf<Byte>, boxing
    // every single byte of every frame, then toByteArray()-ed once per
    // frame just to unbox it all again).
    private val buffer = ByteArray(1024 * 1024) // Max single-frame size: 1MB

    fun readNextFrame(useHardwareBitmap: Boolean = true): MjpegFrameResult {
        try {
            // Find start of image (SOI) - a -1 here means the underlying
            // connection has genuinely ended, not just one bad frame.
            var prev = -1
            while (true) {
                val current = inputStream.read()
                if (current == -1) return MjpegFrameResult.StreamEnded
                if (prev == 0xFF && current == 0xD8) break
                prev = current
            }

            buffer[0] = 0xFF.toByte()
            buffer[1] = 0xD8.toByte()
            var length = 2
            var overflowed = false
            prev = -1
            while (true) {
                val current = inputStream.read()
                if (current == -1) return MjpegFrameResult.StreamEnded // stream ended mid-frame
                if (!overflowed) {
                    if (length < buffer.size) {
                        buffer[length++] = current.toByte()
                    } else {
                        // Bigger than the max frame size this parser accepts -
                        // keep draining bytes (without writing them) until EOI
                        // so the NEXT readNextFrame() call starts cleanly at
                        // the next frame boundary instead of desyncing.
                        overflowed = true
                    }
                }
                if (prev == 0xFF && current == 0xD9) break
                prev = current
            }

            if (overflowed) return MjpegFrameResult.CorruptFrame

            val bitmap = decodeFrame(length, useHardwareBitmap)
            return if (bitmap != null) MjpegFrameResult.Frame(bitmap) else MjpegFrameResult.CorruptFrame
        } catch (e: Exception) {
            // A read() failure here means the connection itself broke
            // (socket closed, timeout) - treated as a real end, not a
            // single corrupt frame to skip past.
            return MjpegFrameResult.StreamEnded
        }
    }

    /**
     * Decodes the first [length] bytes of [buffer] as one JPEG frame.
     *
     * On API 28+ with [useHardwareBitmap] set, tries the real hardware-
     * accelerated [ImageDecoder] path first (GPU-resident
     * [Bitmap.Config.HARDWARE], see MjpegPlayer()'s own comment on why).
     * Any failure there - an unsupported/malformed frame, a device quirk,
     * or simply API <28 - falls back to the always-correct CPU
     * [BitmapFactory] decode this parser has always used, so a hardware
     * decode problem degrades one frame at a time rather than ever being
     * indistinguishable from [MjpegFrameResult.CorruptFrame].
     */
    private fun decodeFrame(length: Int, useHardwareBitmap: Boolean): Bitmap? {
        if (useHardwareBitmap && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(buffer, 0, length))
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                    decoder.isMutableRequired = false
                }
            } catch (e: Exception) {
                // Falls through to the CPU decode below.
            }
        }
        return BitmapFactory.decodeByteArray(buffer, 0, length)
    }

    override fun close() {
        inputStream.close()
    }
}
