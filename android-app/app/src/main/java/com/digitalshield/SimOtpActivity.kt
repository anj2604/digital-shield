package com.digitalshield

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitalshield.ui.theme.DigitalShieldTheme
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

class SimOtpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p -> SimOtpScreen(Modifier.padding(p)) }
            }
        }
    }
}

@Composable
fun SimOtpScreen(modifier: Modifier) {
    val context = LocalContext.current
    fun dial(code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("USSD code", code))
        Toast.makeText(context, "Code $code copied. Paste it in the dialer and press call.", Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Intent.ACTION_DIAL))
    }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("OTP and SIM safety", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Your OTP is only for you", fontWeight = FontWeight.Bold)
                Text("No bank, police, CBI or RBI officer will ever ask for your OTP. If anyone on a call asks for it, it is a scam. Digital Shield warns you automatically when an OTP arrives during a call.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { context.startActivity(SafeExitActivity.intent(context, null, null, -1)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                    Text("Someone is asking for my OTP")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Are your calls being forwarded?", fontWeight = FontWeight.Bold)
                Text("Scammers sometimes forward your calls to receive OTPs sent by voice call. These codes ask your network directly. Press the call button after the dialer opens.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { dial("*#21#") }) { Text("Check call forwarding (*#21#)") }
                OutlinedButton(onClick = { dial("##002#") }) { Text("Turn off all forwarding (##002#)") }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("SIMs registered in your name", fontWeight = FontWeight.Bold)
                Text("Check that no unknown SIM is using your identity, and report any you don't recognise.")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { open("https://sancharsaathi.gov.in") }) { Text("Open Sanchar Saathi") }
            }
        }
    }
}