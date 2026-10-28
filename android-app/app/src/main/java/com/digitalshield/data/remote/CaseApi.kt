package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

data class EvidenceRef(val type: String, val id: String)

data class CaseRequest(
    val title: String,
    val description: String,
    val evidence: List<EvidenceRef>,
    @SerializedName("incident_type") val incidentType: String = "digital_arrest"
)

data class CaseResult(
    val id: String,
    val title: String,
    val status: String,
    @SerializedName("evidence_count") val evidenceCount: Int,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_band") val riskBand: String
)

interface CaseApi {
    @POST("cases/")
    suspend fun create(@Body request: CaseRequest): CaseResult
    @Streaming
    @GET("cases/{id}/report.pdf")
    suspend fun report(@Path("id") id: String): ResponseBody
}