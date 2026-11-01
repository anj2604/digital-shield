package com.digitalshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.digitalshield.data.LiveCallState
import com.digitalshield.data.LiveCallStore
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.CallRequest
import com.digitalshield.services.CallRecorderService
import com.digitalshield.services.CallRecordingPrefs
import com.digitalshield.services.ChunkRecorder
import com.digitalshield.ui.AlertCard
import com.digitalshield.ui.BandPill
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallRecordingActivity : ComponentActivity() {
    private val tick = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p -> CallRecordingScreen(Modifier.padding(p), tick.value) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick.value++   // re-check settings when coming back from Accessibility
    }
}

@Composable
fun CallRecordingScreen(modifier: Modifier, tick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    var micOn by remember { mutableStateOf(micGranted()) }
    var serviceOn by remember { mutableStateOf(CallRecorderService.isEnabled(context)) }
    var consent by remember { mutableStateOf(CallRecordingPrefs.enabled(context)) }
    var testing by remember { mutableStateOf(false) }
    val live by LiveCallStore.state.collectAsState()

    LaunchedEffect(tick) {
        micOn = micGranted()
        serviceOn = CallRecorderService.isEnabled(context)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micOn = it }

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Call recording", style = MaterialTheme.typography.headlineSmall)
        Text("Digital Shield records calls from unknown numbers in 10-second chunks, turns them into text and warns you while the call is happening. Saved contacts are never recorded.",
            color = muted)
        Spacer(Modifier.height(12.dp))

        StepCard(1, "Allow the microphone", micOn) {
            Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
        }
        StepCard(2, "Turn on \u201CDigital Shield call protection\u201D in Accessibility", serviceOn) {
            Text("Android only allows accessibility services to record during a call.", color = muted,
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Open Accessibility settings")
            }
        }
        StepCard(3, "I agree to record calls from unknown numbers", consent, alwaysShow = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = consent, onCheckedChange = { consent = it; CallRecordingPrefs.setEnabled(context, it) })
                Spacer(Modifier.width(8.dp))
                Text(if (consent) "On" else "Off")
            }
        }

        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("📢 Tips", fontWeight = FontWeight.Bold)
                Text("• Turn on speakerphone during a suspicious call so the caller's voice is recorded clearly.")
                Text("• Every chunk is saved as evidence with a SHA-256 fingerprint.")
                Text("• Check whether the law where you live requires telling the other person they are being recorded.")
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            enabled = micOn && !testing,
            onClick = {
                scope.launch {
                    testing = true
                    try {
                        val r = ApiClient.callApi.analyzeCall(CallRequest("9000012345", false))
                        val id = r.id ?: throw IllegalStateException("the backend did not create a call")
                        val rec = ChunkRecorder(context.applicationContext, id, "9000012345 (test)")
                        rec.start()
                        delay(21_000)
                        rec.stop()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Test failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    testing = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (testing) "Recording test… speak now" else "Run a 20-second test") }
        Text("During the test, read a scam script aloud. The transcript and risk update every 10 seconds.",
            color = muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

        live?.let {
            Spacer(Modifier.height(12.dp))
            LiveCallCard(it)
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, done: Boolean, alwaysShow: Boolean = false, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (done) "✅" else "$n.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            if (!done || alwaysShow) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

/** Live banner shown during (and just after) a recorded call. Also used on the home screen. */
@Composable
fun LiveCallCard(s: LiveCallState) = AlertCard(s.band) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (s.recording) "🔴 Recording call from ${s.number}" else "⏹ Call from ${s.number} ended",
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        BandPill(s.band, s.score)
    }
    Text("${s.chunks} chunk(s) checked", color = MaterialTheme.colorScheme.onSurfaceVariant)
    s.transcripts.lastOrNull { it.isNotBlank() }?.let { Text("\u201C$it\u201D", maxLines = 3) }
    s.error?.let { Text(it, color = Color(0xFFEF4444)) }
    if (!s.recording) TextButton(onClick = { LiveCallStore.clear() }) { Text("Dismiss") }
}