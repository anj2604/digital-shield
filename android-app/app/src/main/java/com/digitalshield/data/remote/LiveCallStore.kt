package com.digitalshield.data

import com.digitalshield.data.remote.ChunkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LiveCallState(
    val callId: String,
    val number: String,
    val recording: Boolean = true,
    val chunks: Int = 0,
    val band: String = "LOW",
    val score: Int = 0,
    val transcripts: List<String> = emptyList(),
    val error: String? = null,
    val alerted: Boolean = false
)

object LiveCallStore {
    private val _state = MutableStateFlow<LiveCallState?>(null)
    val state: StateFlow<LiveCallState?> = _state.asStateFlow()

    fun start(callId: String, number: String) { _state.value = LiveCallState(callId, number) }

    fun update(r: ChunkResult) = _state.update {
        it?.copy(chunks = it.chunks + 1, band = r.liveRisk.band, score = r.liveRisk.score,
            transcripts = (it.transcripts + r.transcript).takeLast(5), error = null)
    }

    fun error(message: String) = _state.update { it?.copy(error = message) }
    fun finish() = _state.update { it?.copy(recording = false) }
    fun clear() { _state.value = null }

    /** True only the first time, so the full-screen alert fires once per call. */
    fun markAlerted(): Boolean {
        val cur = _state.value ?: return false
        if (cur.alerted) return false
        _state.value = cur.copy(alerted = true)
        return true
    }
}