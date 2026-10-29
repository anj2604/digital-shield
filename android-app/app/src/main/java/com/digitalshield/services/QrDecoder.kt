package com.digitalshield.services

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object QrDecoder {
    suspend fun decode(context: Context, uri: Uri): String? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromFilePath(context, uri)
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        BarcodeScanning.getClient(options).process(image)
            .addOnSuccessListener { codes -> cont.resume(codes.firstOrNull()?.rawValue) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}