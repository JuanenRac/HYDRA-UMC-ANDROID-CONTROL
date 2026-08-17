// =============================================================================
// HYDRA-UMC CONTROL - High-performance MJPEG player using native Canvas
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

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
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    
                    val inputStream = response.body?.byteStream() ?: return@use
                    MjpegStreamParser(inputStream).use { parser ->
                        while (isActive) {
                            val frame = parser.readNextFrame() ?: break
                            bitmap = frame
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MjpegPlayer", "Stream error: ${e.message}")
            }
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        bitmap?.let { b ->
            drawIntoCanvas { canvas ->
                val drawRect = Rect(0, 0, size.width.toInt(), size.height.toInt())
                val bitmapRect = Rect(0, 0, b.width, b.height)
                canvas.nativeCanvas.drawBitmap(b, bitmapRect, drawRect, Paint())
            }
        }
    }
}

/**
 * Simple parser for MJPEG streams.
 * Searches for SOI (0xFFD8) and EOI (0xFFD9) markers.
 */
class MjpegStreamParser(private val inputStream: InputStream) : AutoCloseable {
    private val buffer = ByteArray(1024 * 1024) // 1MB buffer

    fun readNextFrame(): Bitmap? {
        try {
            // Find start of image
            var prev = -1
            while (true) {
                val current = inputStream.read()
                if (current == -1) return null
                if (prev == 0xFF && current == 0xD8) break
                prev = current
            }

            // Read until end of image or max buffer
            val frameData = mutableListOf<Byte>()
            frameData.add(0xFF.toByte())
            frameData.add(0xD8.toByte())
            
            prev = -1
            while (true) {
                val current = inputStream.read()
                if (current == -1) break
                frameData.add(current.toByte())
                if (prev == 0xFF && current == 0xD9) break
                prev = current
                if (frameData.size > buffer.size) break
            }

            val bytes = frameData.toByteArray()
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            return null
        }
    }

    override fun close() {
        inputStream.close()
    }
}
