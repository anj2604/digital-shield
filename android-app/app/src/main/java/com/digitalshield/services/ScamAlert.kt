package com.digitalshield.services

import android.Manifest
import android.annotation.SuppressLint
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
import com.digitalshield.ScamAlertActivity

object ScamAlert {
    private const val CHANNEL = "scam_takeover"

    /** Android 14+ lets the user switch off full-screen alerts; older versions always allow them. */
    fun canFullScreen(c: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
            c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun intent(c: Context, title: String, reason: String, number: String?, band: String, score: Int): Intent =
        Intent(c, ScamAlertActivity::class.java)
            .putExtra("title", title).putExtra("reason", reason).putExtra("number", number)
            .putExtra("band", band).putExtra("score", score)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** foreground = true when the app is open (for example demo mode): show the takeover screen directly. */
    @SuppressLint("MissingPermission")
    fun show(c: Context, title: String, reason: String, number: String?, band: String, score: Int,
             foreground: Boolean = false) {
        val i = intent(c, title, reason, number, band, score)
        if (foreground) { c.startActivity(i); return }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Full-screen scam alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(c, 9100, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("🚨 $title")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)   // takes over the screen when the phone is locked or idle
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(9100, n)
    }
}