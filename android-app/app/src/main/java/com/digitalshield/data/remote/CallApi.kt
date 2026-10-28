package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

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
    @GET("calls/")
    suspend fun list(@Query("limit") limit: Int = 100): List<CallResult>
}