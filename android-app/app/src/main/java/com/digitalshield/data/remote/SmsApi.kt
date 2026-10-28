package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

data class SmsRequest(
    val sender: String,
    val body: String,
    @SerializedName("is_known_contact") val isKnownContact: Boolean
)

data class SmsResponse(
    val id: String?,
    val stored: Boolean,
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
    @GET("sms/")
    suspend fun list(@Query("limit") limit: Int = 100): List<SmsResponse>
}