package com.digitalshield

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import com.digitalshield.services.SafeExitActions
import com.digitalshield.ui.theme.DigitalShieldTheme
import android.net.Uri
import androidx.compose.runtime.remember
import com.digitalshield.data.TrustedContactStore

class SafeExitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val band = intent.getStringExtra(EXTRA_BAND)
        val score = intent.getIntExtra(EXTRA_SCORE, -1)
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    SafeExitScreen(Modifier.padding(p), number, band, score) { finish() }
                }
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_BAND = "band"
        const val EXTRA_SCORE = "score"
        fun intent(context: Context, number: String?, band: String?, score: Int): Intent =
            Intent(context, SafeExitActivity::class.java)
                .putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_BAND, band).putExtra(EXTRA_SCORE, score)
    }
}

@Composable
fun SafeExitScreen(modifier: Modifier, number: String?, band: String?, score: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val red = Color(0xFFEF4444)
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("You are safe to end this call", style = MaterialTheme.typography.headlineSmall,
            color = red, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("There is no \u201Cdigital arrest\u201D in Indian law. Police, CBI, ED and RBI never arrest " +
                "anyone over a video call or ask for money to clear a case. You are allowed to hang up. " +
                "Ending the call is not a crime.")
        if (number != null) {
            Spacer(Modifier.height(8.dp))
            Text("Caller: $number" + if (band != null && score >= 0) "  ($band, $score/100)" else "",
                fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            toast(if (SafeExitActions.endCall(context)) "Call ended"
            else "Press the red button on the call screen to hang up")
        }, colors = ButtonDefaults.buttonColors(containerColor = red), modifier = Modifier.fillMaxWidth()) {
            Text("End this call now")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { toast(SafeExitActions.alertTrustedContacts(context, number, band, score)) },
            modifier = Modifier.fillMaxWidth()) { Text("Alert all my trusted contacts") }

        val contacts = remember { TrustedContactStore.loadAll(context) }
        if (contacts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Or call someone you trust right now:", fontWeight = FontWeight.Bold)
            Text("Hang up the scam call first, then tap a name.", style = MaterialTheme.typography.bodySmall)
            contacts.forEach { c ->
                OutlinedButton(onClick = { SafeExitActions.callPerson(context, c.phone) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("📞 Call ${c.name.ifBlank { c.phone }}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { SafeExitActions.dialHelpline(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Call cyber helpline 1930")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { SafeExitActions.openPortal(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Report at cybercrime.gov.in")
        }

        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Do not", fontWeight = FontWeight.Bold)
                Text("• Transfer money or pay any \u201Cfine\u201D or \u201Cdeposit\u201D")
                Text("• Share OTPs, PINs or bank details")
                Text("• Install any app they ask for, or share your screen")
                Text("• Stay alone on the call. Talk to someone you trust")
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("If they say you must stay on the call", fontWeight = FontWeight.Bold)
                Text("Say: \u201CI will come to the police station in person.\u201D Then hang up.")
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }
}