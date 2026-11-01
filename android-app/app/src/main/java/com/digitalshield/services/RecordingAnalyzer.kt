package com.digitalshield.services

import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.RecordingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody

object RecordingAnalyzer {
    val AUDIO_EXT = setOf("m4a", "mp3", "amr", "aac", "wav", "3gp", "ogg", "opus")

    data class RecordingAnalysis(val name: String, val number: String, val result: RecordingResult)

    private val _results = MutableStateFlow<List<RecordingAnalysis>>(emptyList())
    val results: StateFlow<List<RecordingAnalysis>> = _results.asStateFlow()

    /**
     * Finds an Indian mobile number in a recording's file name, e.g.
     * "Call recording 98765 43210_241030_101530.m4a" or "+919876543210_20241030.mp3".
     * Date and time stamps are ignored because they don't look like mobile numbers.
     */
    fun numberFrom(name: String): String? {
        Regex("\\+?\\d[\\d\\s-]{8,14}\\d").findAll(name).forEach { m ->
            val d = m.value.filter(Char::isDigit)
            val candidate = when {
                d.length == 10 -> d
                d.length == 11 && d.startsWith("0") -> d.substring(1)
                d.length == 12 && d.startsWith("91") -> d.substring(2)
                else -> null
            }
            if (candidate != null && candidate.first() in '6'..'9') return candidate
        }
        return null
    }

    suspend fun analyse(bytes: ByteArray, name: String, number: String): RecordingResult =
        withContext(Dispatchers.IO) {
            val part = MultipartBody.Part.createFormData("file", name, RequestBody.create(MediaType.parse("audio/*"), bytes))
            val r = ApiClient.callAudioApi.uploadRecording(part, RequestBody.create(MediaType.parse("text/plain"), number))
            _results.update { listOf(RecordingAnalysis(name, number, r)) + it }
            r
        }
}