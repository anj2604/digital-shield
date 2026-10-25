package com.digitalshield.data

import com.digitalshield.data.remote.CallResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallStore {
    private val _calls = MutableStateFlow<List<CallResult>>(emptyList())
    val calls: StateFlow<List<CallResult>> = _calls.asStateFlow()

    fun add(call: CallResult) {
        _calls.value = listOf(call) + _calls.value
    }
}
