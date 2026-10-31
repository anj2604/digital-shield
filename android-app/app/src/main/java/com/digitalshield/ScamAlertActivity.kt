package com.digitalshield

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalshield.services.SafeExitActions
import com.digitalshield.ui.theme.DigitalShieldTheme

class ScamAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        vibrate()
        val title = intent.getStringExtra("title") ?: "Scam alert"
        val reason = intent.getStringExtra("reason") ?: ""
        val number = intent.getStringExtra("number")
        val band = intent.getStringExtra("band") ?: "HIGH"
        val score = intent.getIntExtra("score", -1)

        setContent {
            DigitalShieldTheme {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF7F1D1D), Color(0xFFDC2626), Color(0xFFEA580C))))
                        .systemBarsPadding().padding(28.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🚨", fontSize = 72.sp)
                        Text("SCAM ALERT", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(reason, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
                        if (number != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("From $number" + if (score >= 0) "  ($band, $score/100)" else "",
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No police, CBI or RBI officer will ever ask you to pay, share an OTP or stay on a call. You can hang up.",
                            color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = {
                                val ok = SafeExitActions.endCall(this@ScamAlertActivity)
                                Toast.makeText(this@ScamAlertActivity,
                                    if (ok) "Call ended" else "Press the red button on the call screen", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB91C1C)),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("Hang up now", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { startActivity(SafeExitActivity.intent(this@ScamAlertActivity, number, band, score)); finish() },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("Open Safe Exit", color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { finish() }) { Text("I'm safe, dismiss", color = Color.White.copy(alpha = 0.85f)) }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        else getSystemService(Vibrator::class.java)
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 800), -1))
    }
}