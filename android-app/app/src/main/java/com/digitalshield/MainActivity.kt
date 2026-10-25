package com.digitalshield

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Button
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
import com.digitalshield.data.CallStore
import com.digitalshield.data.ResultStore
import com.digitalshield.data.remote.CallResult
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

fun bandColor(band: String): Color = when (band) {
    "CRITICAL" -> Color(0xFFEF4444)
    "HIGH" -> Color(0xFFF59E0B)
    "MEDIUM" -> Color(0xFFEAB308)
    else -> Color(0xFF22C55E)
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val wanted = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    var smsOn by remember { mutableStateOf(granted(Manifest.permission.RECEIVE_SMS)) }
    var contactsOn by remember { mutableStateOf(granted(Manifest.permission.READ_CONTACTS)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        smsOn = res[Manifest.permission.RECEIVE_SMS] ?: smsOn
        contactsOn = res[Manifest.permission.READ_CONTACTS] ?: contactsOn
    }
    LaunchedEffect(Unit) {
        val missing = wanted.filter { !granted(it) }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    fun screeningHeld() = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    var screeningOn by remember { mutableStateOf(screeningHeld()) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        screeningOn = screeningHeld()
    }

    val smsResults by ResultStore.results.collectAsState()
    val callResults by CallStore.calls.collectAsState()

    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text("Digital Shield", style = MaterialTheme.typography.headlineMedium)
                Text(if (smsOn) "SMS protection: ON" else "SMS protection: OFF (permission needed)")
                Text(if (contactsOn) "Contact check: ON" else "Contact check: OFF (all senders treated as unknown)")
                Text(if (screeningOn) "Call screening: ON" else "Call screening: OFF")
                if (!screeningOn) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        roleManager?.let {
                            roleLauncher.launch(it.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                        }
                    }) { Text("Enable call screening") }
                }
            }
        }
        if (callResults.isNotEmpty()) {
            item { Text("Calls", style = MaterialTheme.typography.titleLarge) }
            items(callResults) { CallResultCard(it) }
        }
        if (smsResults.isNotEmpty()) {
            item { Text("Messages", style = MaterialTheme.typography.titleLarge) }
            items(smsResults) { SmsResultCard(it) }
        }
    }
}

@Composable
fun CallResultCard(r: CallResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Call: ${r.riskBand} — ${r.riskScore}/100", color = bandColor(r.riskBand),
                style = MaterialTheme.typography.titleMedium)
            Text("From: ${r.phoneNumber}")
            Text("Indicators: ${r.indicators.joinToString().ifEmpty { "none" }}")
            Text(if (r.stored) "Unknown number: stored" else "Saved contact: not stored",
                color = Color(0xFF6B7A8F))
        }
    }
}

@Composable
fun SmsResultCard(r: SmsResponse) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${r.riskBand} — ${r.riskScore}/100", color = bandColor(r.riskBand),
                style = MaterialTheme.typography.titleMedium)
            Text("From: ${r.sender}")
            Text(r.body, maxLines = 3)
            Spacer(Modifier.height(8.dp))
            Text("Indicators: ${r.indicators.joinToString()}")
            Text(
                if (r.stored) "Unknown sender: stored as evidence"
                else "Saved contact shows scam signs. Call them back on their usual number to check. (Not stored.)",
                color = Color(0xFF6B7A8F)
            )
        }
    }
}