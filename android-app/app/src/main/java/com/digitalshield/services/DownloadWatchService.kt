package com.digitalshield.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.digitalshield.data.QuarantineStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections

class DownloadWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seen = Collections.synchronizedSet(mutableSetOf<String>())
    private var observer: FileObserver? = null
    private val dir by lazy { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = FileNotifications.serviceNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FileNotifications.SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FileNotifications.SERVICE_ID, notification)
        }
        if (observer == null) {
            dir.listFiles()?.forEach { seen.add(key(it)) }     // files already there are not re-scanned
            observer = object : FileObserver(dir, CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) scope.launch { consider(File(dir, path)) }
                }
            }.also { it.startWatching() }
            scope.launch {                                     // safety net
                while (isActive) {
                    delay(10_000)
                    dir.listFiles()?.forEach { consider(it) }
                }
            }
        }
        return START_STICKY
    }

    private fun key(f: File) = "${f.absolutePath}|${f.length()}|${f.lastModified()}"

    private fun ignored(n: String) = n.startsWith(".") || n.endsWith(".crdownload") ||
            n.endsWith(".part") || n.endsWith(".tmp") || n.endsWith(".download")

    private suspend fun consider(f: File) {
        if (!f.isFile || ignored(f.name)) return
        val before = f.length()
        delay(2000)                                            // wait until the download has finished
        if (!f.exists() || before == 0L || f.length() != before) return
        if (!seen.add(key(f))) return
        val v = FileGuard.check(applicationContext, f, f.name, "auto")
        if (v.sha256 in QuarantineStore.trusted(applicationContext)) return
        when (v.decision) {
            "blocked" -> {
                val moved = QuarantineStore.quarantine(applicationContext, f, f.name, v.band, v.sha256)
                FileNotifications.alert(applicationContext, v, quarantined = moved)
            }
            "caution" -> FileNotifications.alert(applicationContext, v, quarantined = false)
            else -> FileNotifications.safe(applicationContext, f.name)
        }
    }

    override fun onDestroy() {
        observer?.stopWatching()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, DownloadWatchService::class.java))
        fun stop(c: Context) = c.stopService(Intent(c, DownloadWatchService::class.java))
    }
}