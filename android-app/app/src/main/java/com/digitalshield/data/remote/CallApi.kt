package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class CallRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("is_known_contact") val isKnownContact: Boolean,
    val channel: String = "carrier"
)

data class CallResult(
    val id: String?,
    val stored: Boolean,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("is_known_contact") val isKnownContact: Boolean,
    val indicators: List<String>,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_band") val riskBand: String,
    @SerializedName("created_at") val createdAt: String
)

interface CallApi {
    @POST("calls/")
    suspend fun analyzeCall(@Body request: CallRequest): CallResult
}