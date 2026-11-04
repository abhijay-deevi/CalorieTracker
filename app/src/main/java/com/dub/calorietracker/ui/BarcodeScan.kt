package com.dub.calorietracker.ui

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google's ready-made barcode scanner (from Play services — no camera permission needed)
 * and hands back the number from a food package barcode.
 */
fun scanFoodBarcode(context: Context, onCode: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
        )
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { code ->
            val value = code.rawValue
            if (value.isNullOrBlank()) onError("Couldn't read that barcode. Try again.") else onCode(value)
        }
        .addOnFailureListener { e ->
            onError("The barcode scanner couldn't start: ${e.message ?: "unknown error"}. " +
                "It needs Google Play services — try again in a moment.")
        }
    // Cancelled (back pressed) → nothing to do.
}
