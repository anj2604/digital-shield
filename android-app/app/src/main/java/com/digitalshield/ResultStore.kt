package com.digitalshield.data

import com.digitalshield.data.remote.SmsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ResultStore {
    private val _results = MutableStateFlow<List<SmsResponse>>(emptyList())
    val results: StateFlow<List<SmsResponse>> = _results.asStateFlow()

    fun add(result: SmsResponse) {
        _results.value = listOf(result) + _results.value
    }
}