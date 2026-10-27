package com.digitalshield.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.digitalshield.SafeExitActivity
import com.digitalshield.data.remote.CallResult

object NotificationHelper {
    private const val CHANNEL_ID = "shield_alerts"

    @SuppressLint("MissingPermission") // checked just below
    fun showCallWarning(context: Context, r: CallResult) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Scam alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val safeExit = PendingIntent.getActivity(
            context, r.phoneNumber.hashCode(),
            SafeExitActivity.intent(context, r.phoneNumber, r.riskBand, r.riskScore),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = "Indicators: ${r.indicators.joinToString()}. " +
                "You can hang up. Do not share OTPs, pay money or install apps."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠ ${r.riskBand} risk call (${r.riskScore}/100): ${r.phoneNumber}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(safeExit)
            .addAction(android.R.drawable.ic_dialog_alert, "Safe Exit", safeExit)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(r.phoneNumber.hashCode(), n)
    }
}