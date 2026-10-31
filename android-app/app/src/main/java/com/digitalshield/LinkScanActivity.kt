package com.digitalshield

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.LinkScanRequest
import com.digitalshield.data.remote.LinkScanResult
import com.digitalshield.data.remote.ReportRequest
import com.digitalshield.services.QrDecoder
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

class LinkScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringExtra(EXTRA_INPUT)
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p -> LinkScanScreen(Modifier.padding(p), initial) }
            }
        }
    }
    companion object { const val EXTRA_INPUT = "input" }
}

@Composable
fun LinkScanScreen(modifier: Modifier, initial: String? = null) {    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initial ?: "") }
    var result by remember { mutableStateOf<LinkScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun runScan(text: String, source: String) {
        scope.launch {
            busy = true; msg = null
            try {
                result = ApiClient.linkApi.scan(LinkScanRequest(text, source))
            } catch (e: Exception) {
                msg = "Scan failed: ${e.message}"
            }
            busy = false
        }
    }
    LaunchedEffect(Unit) { if (!initial.isNullOrBlank()) runScan(initial, "link") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true; msg = null
            try {
                val raw = QrDecoder.decode(context, uri)
                busy = false
                if (raw == null) msg = "No QR code found in that image" else { input = raw; runScan(raw, "qr") }
            } catch (e: Exception) {
                busy = false
                msg = "Could not read the QR code: ${e.message}"
            }
        }
    }

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Scan a link or QR code", style = MaterialTheme.typography.headlineSmall)
        Text("Check it before you open it or pay.", color = Color(0xFF6B7A8F))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(input, { input = it }, label = { Text("Paste a link") },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { runScan(input.trim(), "link") }, enabled = input.isNotBlank() && !busy) {
                Text("Check link")
            }
            OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy) {
                Text("Scan QR from image")
            }
        }
        if (busy) Text("Checking…", Modifier.padding(top = 8.dp))
        msg?.let { Text(it, color = Color(0xFFEF4444), modifier = Modifier.padding(top = 8.dp)) }

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${r.riskBand} — ${r.riskScore}/100", color = bandColor(r.riskBand),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(r.verdict)
                    Spacer(Modifier.height(8.dp))
                    r.upi?.let { u ->
                        Text("UPI payment QR", fontWeight = FontWeight.Bold)
                        Text("Pays to: ${u.vpa}  (${u.name.ifBlank { "no name" }})")
                        if (u.amount.isNotBlank()) Text("Amount: Rs ${u.amount}")
                        if (u.note.isNotBlank()) Text("Note: ${u.note}")
                        Spacer(Modifier.height(8.dp))
                    }
                    r.host?.let { Text("Website: $it") }
                    if (r.findings.isEmpty()) Text("No warning signs found.")
                    r.findings.forEach { f -> Text("• ${f.label}  (+${f.weight})") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                val req = if (r.upi != null) ReportRequest("upi", r.upi.vpa) else ReportRequest("url", r.input)
                                ApiClient.linkApi.report(req)
                                Toast.makeText(context, "Reported. Thank you.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Report failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Report as scam") }
                }
            }
        }
    }
}