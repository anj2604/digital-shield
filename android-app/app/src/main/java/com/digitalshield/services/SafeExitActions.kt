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

    /** Alerts EVERY trusted contact with alerts switched on, at the same time. */
    @Suppress("DEPRECATION")
    fun alertTrustedContacts(context: Context, number: String?, band: String?, score: Int): String {
        val people = TrustedContactStore.loadAll(context).filter { it.consent }
        if (people.isEmpty()) return "No trusted contacts with alerts switched on"
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val text = "Digital Shield alert: I may be on a scam call right now" +
                (number?.let { " from $it" } ?: "") +
                (if (band != null && score >= 0) " (risk $band, $score/100)" else "") +
                " at $time. Please call me now. If you can't reach me, call the cyber helpline 1930."

        if (granted(context, Manifest.permission.SEND_SMS)) {
            val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
            else SmsManager.getDefault()
            var sent = 0
            people.forEach {
                try {
                    sms.sendMultipartTextMessage(it.phone, null, sms.divideMessage(text), null, null)
                    sent++
                } catch (e: Exception) { /* try the next person */ }
            }
            if (sent > 0) return "Alert sent to $sent of ${people.size} trusted contact(s)"
        }
        // Fallback: one message addressed to everyone, the user taps Send
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + people.joinToString(";") { it.phone }))
                .putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Opened Messages to alert ${people.size} contact(s)"
    }

    fun callPerson(context: Context, phone: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun dialHelpline(context: Context) = callPerson(context, "1930")

    fun openPortal(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}