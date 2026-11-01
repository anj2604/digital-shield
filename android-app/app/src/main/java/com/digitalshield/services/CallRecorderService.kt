package com.digitalshield.services

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android only lets accessibility services use the microphone during a call.
 * This service watches for calls; when an unknown call (registered by call screening)
 * is answered, it records it in 10-second chunks until the call ends.
 */
class CallRecorderService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: ChunkRecorder? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { watchCalls() }
    }

    private suspend fun watchCalls() {
        val am = getSystemService(AudioManager::class.java)
        while (scope.isActive) {
            val inCall = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
            if (inCall && recorder == null) {
                val pending = CallSession.takeIfFresh()   // only unknown numbers are ever registered
                if (pending != null && CallRecordingPrefs.enabled(this) && micAllowed()) {
                    recorder = ChunkRecorder(applicationContext, pending.callId, pending.number).also { it.start() }
                }
            } else if (!inCall && recorder != null) {
                recorder?.stop()
                recorder = null
            }
            delay(1000)
        }
    }

    private fun micAllowed() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not needed */ }
    override fun onInterrupt() { }

    override fun onDestroy() {
        recorder?.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun isEnabled(c: Context): Boolean {
            val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(c, CallRecorderService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}