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

    private fun ready(context: Context): Boolean {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Scam alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        return Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun safeExitIntent(context: Context, number: String?, band: String?, score: Int, id: Int) =
        PendingIntent.getActivity(
            context, id, SafeExitActivity.intent(context, number, band, score),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    @SuppressLint("MissingPermission")
    fun showCallWarning(context: Context, r: CallResult) {
        if (!ready(context)) return
        val pi = safeExitIntent(context, r.phoneNumber, r.riskBand, r.riskScore, r.phoneNumber.hashCode())
        val text = "Indicators: ${r.indicators.joinToString()}. " +
                "You can hang up. Do not share OTPs, pay money or install apps."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠ ${r.riskBand} risk call (${r.riskScore}/100): ${r.phoneNumber}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_dialog_alert, "Safe Exit", pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(r.phoneNumber.hashCode(), n)
    }

    @SuppressLint("MissingPermission")
    fun showOtpWarning(context: Context, inCall: Boolean) {
        if (!ready(context)) return
        val title = if (inCall) "⚠ OTP received while you are on a call" else "OTP received: never share it"
        val text = if (inCall)
            "If the person on this call is asking for the OTP, it is a scam. Do not read it out. You can hang up."
        else
            "No bank, police, CBI or RBI officer will ever ask for your OTP."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(safeExitIntent(context, null, null, -1, 4242))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(4242, n)
    }
}