package com.digitalshield.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.digitalshield.data.TrustedContactStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SafeExitActions {
    private fun granted(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun endCall(context: Context): Boolean {
        if (!granted(context, Manifest.permission.ANSWER_PHONE_CALLS)) return false
        return try {
            context.getSystemService(TelecomManager::class.java)?.endCall() == true
        } catch (e: SecurityException) { false }
    }

    @Suppress("DEPRECATION")
    fun alertTrustedContact(context: Context, number: String?, band: String?, score: Int): String {
        val contact = TrustedContactStore.load(context) ?: return "No trusted contact saved yet"
        if (!contact.consent) return "Trusted-contact alerts are switched off"
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val text = "Digital Shield alert: I may be on a scam call right now" +
                (number?.let { " from $it" } ?: "") +
                (if (band != null && score >= 0) " (risk $band, $score/100)" else "") +
                " at $time. Please call me now."
        if (granted(context, Manifest.permission.SEND_SMS)) {
            try {
                val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
                else SmsManager.getDefault()
                sms.sendMultipartTextMessage(contact.phone, null, sms.divideMessage(text), null, null)
                return "Alert sent to ${contact.name.ifBlank { contact.phone }}"
            } catch (e: Exception) { /* fall through to the message composer */ }
        }
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phone}"))
            .putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opened Messages to send the alert"
    }

    fun dialHelpline(context: Context) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openPortal(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}