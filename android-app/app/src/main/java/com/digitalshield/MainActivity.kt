package com.digitalshield

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.digitalshield.data.ResultStore
import com.digitalshield.data.remote.SmsResponse
import com.digitalshield.ui.theme.DigitalShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    var smsOn by remember { mutableStateOf(granted(Manifest.permission.RECEIVE_SMS)) }
    var contactsOn by remember { mutableStateOf(granted(Manifest.permission.READ_CONTACTS)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        smsOn = res[Manifest.permission.RECEIVE_SMS] ?: smsOn
        contactsOn = res[Manifest.permission.READ_CONTACTS] ?: contactsOn
    }
    LaunchedEffect(Unit) {
        val missing = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS)
            .filter { !granted(it) }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    val results by ResultStore.results.collectAsState()

    Column(modifier.padding(16.dp)) {
        Text("Digital Shield", style = MaterialTheme.typography.headlineMedium)
        Text(if (smsOn) "SMS protection: ON" else "SMS protection: OFF (permission needed)")
        Text(if (contactsOn) "Contact check: ON" else "Contact check: OFF (all senders treated as unknown)")
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results) { SmsResultCard(it) }
        }
    }
}

@Composable
fun SmsResultCard(r: SmsResponse) {
    val bandColor = when (r.riskBand) {
        "CRITICAL" -> Color(0xFFEF4444)
        "HIGH" -> Color(0xFFF59E0B)
        "MEDIUM" -> Color(0xFFEAB308)
        else -> Color(0xFF22C55E)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${r.riskBand} — ${r.riskScore}/100", color = bandColor, style = MaterialTheme.typography.titleMedium)
            Text("From: ${r.sender}")
            Text(r.body, maxLines = 3)
            Spacer(Modifier.height(8.dp))
            Text("Indicators: ${r.indicators.joinToString()}")
            Text(
                if (r.stored) "Unknown sender: stored as evidence" else "Saved contact: analysed, not stored",
                color = Color(0xFF6B7A8F)
            )
        }
    }
}