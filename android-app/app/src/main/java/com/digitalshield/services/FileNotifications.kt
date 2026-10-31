package com.digitalshield.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.digitalshield.FileGuardActivity

object FileNotifications {
    const val SERVICE_ID = 7001
    private const val ALERTS = "file_alerts"
    private const val SERVICE = "file_guard_service"

    private fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ALERTS, "File safety alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(SERVICE, "Download protection", NotificationManager.IMPORTANCE_LOW))
    }

    private fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openGuard(c: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        c, id, Intent(c, FileGuardActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun serviceNotification(c: Context): Notification {
        channels(c)
        return NotificationCompat.Builder(c, SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Download protection is on")
            .setContentText("New files in Download are checked before you open them")
            .setOngoing(true)
            .setContentIntent(openGuard(c, 1))
            .build()
    }

    @SuppressLint("MissingPermission")
    fun alert(c: Context, v: FileVerdict, quarantined: Boolean) {
        if (!allowed(c)) return
        channels(c)
        val title = if (quarantined) "⛔ Dangerous file blocked: ${v.name}" else "⚠ Check before opening: ${v.name}"
        val text = (v.findings.firstOrNull()?.label ?: v.verdict) +
                if (quarantined) ". It was moved to quarantine." else ". Open it through Digital Shield."
        val n = NotificationCompat.Builder(c, ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openGuard(c, v.sha256.hashCode()))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(v.sha256.hashCode(), n)
    }

    @SuppressLint("MissingPermission")
    fun safe(c: Context, name: String) {
        if (!allowed(c)) return
        channels(c)
        val n = NotificationCompat.Builder(c, SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("✓ $name checked")
            .setContentText("No threats found.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(name.hashCode(), n)
    }
}