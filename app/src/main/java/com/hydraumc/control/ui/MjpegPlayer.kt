// =============================================================================
// HYDRA-UMC CONTROL - High-performance MJPEG player using native Canvas
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * High-performance MJPEG Stream Player.
 * Reads bytes from an OkHttp stream and decodes JPEGs in a background thread.
 */
@Composable
fun MjpegPlayer(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val client = remember { OkHttpClient() }

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
                            when (val result = parser.readNextFrame()) {
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
                canvas.nativeCanvas.drawBitmap(b, bitmapRect, drawRect, Paint())
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

    fun readNextFrame(): MjpegFrameResult {
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

            val bitmap = BitmapFactory.decodeByteArray(buffer, 0, length)
            return if (bitmap != null) MjpegFrameResult.Frame(bitmap) else MjpegFrameResult.CorruptFrame
        } catch (e: Exception) {
            // A read() failure here means the connection itself broke
            // (socket closed, timeout) - treated as a real end, not a
            // single corrupt frame to skip past.
            return MjpegFrameResult.StreamEnded
        }
    }

    override fun close() {
        inputStream.close()
    }
}
