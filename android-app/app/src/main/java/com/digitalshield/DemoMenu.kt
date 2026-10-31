package com.digitalshield

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.digitalshield.data.CallStore
import com.digitalshield.data.ResultStore
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.CallRequest
import com.digitalshield.data.remote.SmsRequest
import com.digitalshield.services.NotificationHelper
import com.digitalshield.services.ScamAlert
import kotlinx.coroutines.launch

private const val DEMO_SMS = "This is CBI. A case is registered against your Aadhaar. You will be arrested today. " +
        "Transfer Rs 50000 immediately to verify@upi and do not tell anyone."

/** Hidden menu (long-press the header): runs the real detection pipeline without adb commands. */
@Composable
fun DemoMenu(onDismiss: () -> Unit, onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎬 Demo mode") },
        text = {
            Column {
                Text("Runs the real detection pipeline without typing adb commands.",
                    style = MaterialTheme.typography.bodySmall)
                DemoItem("💬 Scam SMS from \u201CCBI\u201D") {
                    scope.launch {
                        try {
                            val r = ApiClient.smsApi.analyzeSms(SmsRequest("9876501234", DEMO_SMS, false))
                            ResultStore.add(r)
                            onDismiss()
                            ScamAlert.show(context, "Scam message detected",
                                "This SMS shows ${r.indicators.size} scam signs, including an arrest threat and a payment demand.",
                                "9876501234", r.riskBand, r.riskScore, foreground = true)
                        } catch (e: Exception) { toast("Backend not reachable: ${e.message}") }
                    }
                }
                DemoItem("📞 Call from a reported number") {
                    scope.launch {
                        try {
                            val r = ApiClient.callApi.analyzeCall(CallRequest("9999999999", false))
                            CallStore.add(r)
                            NotificationHelper.showCallWarning(context, r)
                            onDismiss()
                            ScamAlert.show(context, "Reported scam number calling",
                                "This number appears in scam reports. You can hang up. Never share OTPs or pay.",
                                "9999999999", r.riskBand, r.riskScore, foreground = true)
                        } catch (e: Exception) { toast("Backend not reachable: ${e.message}") }
                    }
                }
                DemoItem("🔢 OTP arrives during a call") {
                    NotificationHelper.showOtpWarning(context, inCall = true)
                    toast("Check the notification")
                    onDismiss()
                }
                DemoItem("🔗 Fake bank link") {
                    context.startActivity(Intent(context, LinkScanActivity::class.java)
                        .putExtra(LinkScanActivity.EXTRA_INPUT, "https://sbi-kyc-verify-2026.xyz/login"))
                    onDismiss()
                }
                DemoItem("👋 Show the welcome tour again") { onReplayIntro(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun DemoItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}