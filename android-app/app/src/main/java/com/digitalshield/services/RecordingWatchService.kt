package com.digitalshield.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.digitalshield.FileGuardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections

/** Fallback for FR8: checks recordings made by the phone's own call recorder. */
class RecordingWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seen = Collections.synchronizedSet(mutableSetOf<String>())
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = serviceNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(SERVICE_ID, n)
        if (!started) {
            started = true
            folders().forEach { dir -> dir.listFiles()?.forEach { seen.add(key(it)) } }   // old recordings are not re-checked
            scope.launch {
                while (isActive) {
                    folders().forEach { dir -> dir.listFiles()?.forEach { consider(it) } }
                    delay(15_000)
                }
            }
        }
        return START_STICKY
    }

    private fun key(f: File) = "${f.absolutePath}|${f.length()}|${f.lastModified()}"

    private suspend fun consider(f: File) {
        if (!f.isFile || f.extension.lowercase() !in RecordingAnalyzer.AUDIO_EXT) return
        if (seen.contains(key(f))) return
        val before = f.length()
        delay(3000)                                         // wait until the recorder has finished writing
        if (!f.exists() || before == 0L || f.length() != before) return
        if (!seen.add(key(f))) return

        val number = RecordingAnalyzer.numberFrom(f.name)
        if (number == null) {
            notifyResult(f.name.hashCode(), "Call recording found",
                "${f.name}: the caller couldn't be identified, so it wasn't uploaded. Open Digital Shield to check it yourself.")
            return
        }
        if (ContactsHelper.isSavedContact(applicationContext, number)) return   // saved contacts stay private
        if (f.length() > 50L * 1024 * 1024) {
            notifyResult(f.name.hashCode(), "Call recording too large", "${f.name} is over 50 MB. Check it manually.")
            return
        }
        try {
            val r = RecordingAnalyzer.analyse(f.readBytes(), f.name, number)
            notifyResult(f.name.hashCode(), "Recorded call from $number: ${r.liveRisk.band} ${r.liveRisk.score}/100",
                "${r.chunks} part(s) of 15 seconds checked.")
            if (r.liveRisk.band == "CRITICAL") {
                ScamAlert.show(applicationContext, "Your recent call looks like a scam",
                    "Digital Shield checked the recording of your call with $number.", number, r.liveRisk.band, r.liveRisk.score)
            }
        } catch (e: Exception) {
            notifyResult(f.name.hashCode(), "Couldn't check a call recording", e.message ?: "Server unreachable")
        }
    }

    private fun channels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SERVICE_CH, "Call recording watcher", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(RESULT_CH, "Call recording results", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun serviceNotification(): Notification {
        channels()
        return NotificationCompat.Builder(this, SERVICE_CH)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Checking your call recordings")
            .setContentText("New recordings of unknown callers are checked automatically")
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun notifyResult(id: Int, title: String, text: String) {
        channels()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(this, RESULT_CH)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(id, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SERVICE_ID = 7400
        private const val SERVICE_CH = "recording_watch"
        private const val RESULT_CH = "recording_results"

        /** Where common phone brands save call recordings. */
        fun folders(): List<File> {
            val root = Environment.getExternalStorageDirectory()
            return listOf(
                "Recordings/Call", "Recordings/Call recordings", "Recordings/Call Recordings",
                "Recordings/PhoneRecord", "Call", "CallRecordings", "Call Recordings", "PhoneRecord",
                "MIUI/sound_recorder/call_rec", "Music/Recordings/Call Recordings", "Record/Call", "Sounds/CallRecord"
            ).map { File(root, it) }.filter { it.isDirectory }
        }

        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, RecordingWatchService::class.java))
        fun stop(c: Context) = c.stopService(Intent(c, RecordingWatchService::class.java))
        fun resume(c: Context) {
            if (CallRecordingPrefs.autoRecordings(c) && FileGuardActivity.hasAllFilesAccess()) start(c)
        }
    }
}