package com.digitalshield.data

import com.digitalshield.data.remote.ScreenshotResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenshotStore {
    private val _shots = MutableStateFlow<List<ScreenshotResult>>(emptyList())
    val shots: StateFlow<List<ScreenshotResult>> = _shots.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun add(s: ScreenshotResult) { _shots.value = listOf(s) + _shots.value }
    fun setBusy(b: Boolean) { _busy.value = b }
}