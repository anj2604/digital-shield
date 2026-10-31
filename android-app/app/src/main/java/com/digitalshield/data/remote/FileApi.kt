package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class FileFinding(val code: String, val label: String, val weight: Int)

data class FileCheckRequest(
    val sha256: String,
    val name: String,
    val size: Long,
    @SerializedName("detected_type") val detectedType: String,
    val findings: List<FileFinding>,
    val source: String
)

data class FileCheckResult(
    val id: String?,
    val sha256: String,
    val name: String,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_band") val riskBand: String,
    val decision: String,
    val verdict: String,
    val findings: List<FileFinding>
)

interface FileApi {
    @POST("files/check")
    suspend fun check(@Body request: FileCheckRequest): FileCheckResult
}