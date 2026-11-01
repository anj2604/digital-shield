package com.digitalshield.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class LiveRisk(val score: Int, val band: String, val indicators: List<String>)

data class ChunkResult(
    @SerializedName("chunk_id") val chunkId: String,
    val seq: Int,
    val transcript: String,
    @SerializedName("new_indicators") val newIndicators: List<String>,
    @SerializedName("live_risk") val liveRisk: LiveRisk
)

data class TimelineItem(
    val seq: Int,
    @SerializedName("offset_s") val offsetS: Int,
    val transcript: String,
    @SerializedName("score_after") val scoreAfter: Int,
    @SerializedName("band_after") val bandAfter: String
)

data class RecordingResult(
    @SerializedName("call_id") val callId: String,
    val chunks: Int,
    val timeline: List<TimelineItem>,
    @SerializedName("live_risk") val liveRisk: LiveRisk
)
interface CallAudioApi {
    @Multipart
    @POST("calls/recording")
    suspend fun uploadRecording(
        @Part file: MultipartBody.Part,
        @Part("phone_number") phoneNumber: RequestBody
    ): RecordingResult
    @Multipart
    @POST("calls/{id}/chunks")
    suspend fun uploadChunk(
        @Path("id") callId: String,
        @Part file: MultipartBody.Part,
        @Part("seq") seq: RequestBody,
        @Part("sha256") sha256: RequestBody,
        @Part("duration_s") durationS: RequestBody
    ): ChunkResult

    @POST("calls/{id}/end")
    suspend fun end(@Path("id") callId: String): Map<String, Any?>
}