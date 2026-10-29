package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class LinkScanRequest(val input: String, val source: String = "link")
data class LinkFinding(val code: String, val label: String, val weight: Int)
data class UpiInfo(val vpa: String, val name: String, val amount: String, val note: String)

data class LinkScanResult(
    val input: String,
    val kind: String,
    val host: String?,
    val findings: List<LinkFinding>,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_band") val riskBand: String,
    val verdict: String,
    val upi: UpiInfo?
)

data class ReportRequest(val type: String, val value: String)

interface LinkApi {
    @POST("links/scan")
    suspend fun scan(@Body request: LinkScanRequest): LinkScanResult

    @POST("threat-intel/report")
    suspend fun report(@Body request: ReportRequest): Map<String, Any?>
}