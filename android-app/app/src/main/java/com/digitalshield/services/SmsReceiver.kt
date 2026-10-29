package com.digitalshield.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.digitalshield.data.ResultStore
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.SmsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val appContext = context.applicationContext

        // FR18: OTP guard. Warn at once, more strongly if the user is on a call right now
        if (OtpGuard.looksLikeOtp(body)) {
            NotificationHelper.showOtpWarning(appContext, OtpGuard.isInCall(appContext))
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val known = ContactsHelper.isSavedContact(appContext, sender)
                val result = ApiClient.smsApi.analyzeSms(SmsRequest(sender, body, known))
                Log.d("DigitalShield", "risk=${result.riskScore} band=${result.riskBand} known=$known stored=${result.stored}")
                // Saved contacts: only surface the result if it looks dangerous
                if (!known || result.riskBand == "HIGH" || result.riskBand == "CRITICAL") {
                    ResultStore.add(result)
                }
            } catch (e: Exception) {
                Log.e("DigitalShield", "Analyze failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}