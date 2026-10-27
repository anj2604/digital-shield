package com.digitalshield.services

import android.content.Context
import android.net.Uri
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.ScreenshotResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ScreenshotProcessor {
    suspend fun process(context: Context, uri: Uri): ScreenshotResult {
        val text = recognizeText(context, uri)
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot read the image")
        }
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        val filePart = MultipartBody.Part.createFormData(
            "file", "screenshot", RequestBody.create(MediaType.parse(mime), bytes)
        )
        val textPart = RequestBody.create(MediaType.parse("text/plain"), text)
        return ApiClient.screenshotApi.upload(filePart, textPart)
    }

    private suspend fun recognizeText(context: Context, uri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromFilePath(context, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}