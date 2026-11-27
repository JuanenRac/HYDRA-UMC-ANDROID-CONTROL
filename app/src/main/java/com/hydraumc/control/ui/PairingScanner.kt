// =============================================================================
// HYDRA-UMC CONTROL - Reading the pairing QR code with the camera
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The code is read by the code scanner of Google Play services, as in the other apps of the family: the camera is
// opened by the system's scanner screen, only when the person taps the button, and this app holds no camera permission
// and never sees a picture - only the text of the code. That text is then checked exactly like a pasted link.
package com.hydraumc.control.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Reads one QR code with the camera. The tests plug in their own. */
fun interface QrReader {
    /**
     * Opens the camera once. [onText] gets what the code says; [onUnavailable] is called when this phone cannot
     * open a code reader. Closing the reader without reading calls neither.
     */
    fun read(context: Context, onText: (String) -> Unit, onUnavailable: () -> Unit)
}

object SystemQrReader : QrReader {
    override fun read(context: Context, onText: (String) -> Unit, onUnavailable: () -> Unit) {
        runCatching {
            val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode -> onText(barcode.rawValue.orEmpty()) }
                .addOnFailureListener { onUnavailable() }
        }.onFailure { onUnavailable() }
    }
}

val LocalQrReader = staticCompositionLocalOf<QrReader> { SystemQrReader }
