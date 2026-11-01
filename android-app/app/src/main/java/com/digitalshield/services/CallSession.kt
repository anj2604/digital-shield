package com.digitalshield.services

import android.content.Context

/** Remembers the unknown call that call screening just registered, so the recorder can attach to it. */
object CallSession {
    data class Pending(val callId: String, val number: String, val at: Long)

    @Volatile private var pending: Pending? = null

    fun set(callId: String, number: String) { pending = Pending(callId, number, System.currentTimeMillis()) }

    /** The pending call, if it was registered in the last 90 seconds. It can only be taken once. */
    fun takeIfFresh(): Pending? {
        val p = pending ?: return null
        pending = null
        return if (System.currentTimeMillis() - p.at < 90_000) p else null
    }
}

/** The user's consent to recording unknown calls. */
object CallRecordingPrefs {
    private fun p(c: Context) = c.getSharedPreferences("call_recording", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) = p(c).edit().putBoolean("enabled", on).apply()
}