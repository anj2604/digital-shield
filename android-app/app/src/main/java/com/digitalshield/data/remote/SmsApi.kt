package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class SmsRequest(val sender: String, val body: String)

data class SmsResponse(
    val id: Int,
    val sender: String,
    val body: String,
    @SerializedName("received_at") val receivedAt: String,
    val indicators: List<String>,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_band") val riskBand: String,
    @SerializedName("extracted_iocs") val extractedIocs: Map<String, List<String>>
)

interface SmsApi {
    @POST("sms/")
    suspend fun analyzeSms(@Body request: SmsRequest): SmsResponse
}