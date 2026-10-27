package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class ScreenshotResult(
    val id: String,
    val sha256: String,
    @SerializedName("ocr_text") val ocrText: String,
    val indicators: List<String>,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_band") val riskBand: String,
    @SerializedName("extracted_iocs") val extractedIocs: Map<String, List<String>>,
    @SerializedName("shared_at") val sharedAt: String
)

interface ScreenshotApi {
    @Multipart
    @POST("screenshots/")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("ocr_text") ocrText: RequestBody
    ): ScreenshotResult
}