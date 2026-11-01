package com.digitalshield

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.material3.OutlinedTextField
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
import com.digitalshield.services.RecordingAnalyzer
import com.digitalshield.services.RecordingWatchService
import com.digitalshield.ui.AlertCard
import com.digitalshield.ui.BandPill
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        tick.value++   // re-check settings when coming back from Settings
    }
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

private fun mmss(s: Int) = "%d:%02d".format(s / 60, s % 60)

@Composable
fun CallRecordingScreen(modifier: Modifier, tick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    var micOn by remember { mutableStateOf(micGranted()) }
    var serviceOn by remember { mutableStateOf(CallRecorderService.isEnabled(context)) }
    var consent by remember { mutableStateOf(CallRecordingPrefs.enabled(context)) }
    var allFiles by remember { mutableStateOf(FileGuardActivity.hasAllFilesAccess()) }
    var autoRec by remember { mutableStateOf(CallRecordingPrefs.autoRecordings(context)) }
    var testing by remember { mutableStateOf(false) }
    var manualNumber by remember { mutableStateOf("") }
    var analysing by remember { mutableStateOf(false) }
    val live by LiveCallStore.state.collectAsState()
    val analysed by RecordingAnalyzer.results.collectAsState()

    LaunchedEffect(tick) {
        micOn = micGranted()
        serviceOn = CallRecorderService.isEnabled(context)
        allFiles = FileGuardActivity.hasAllFilesAccess()
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micOn = it }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            analysing = true
            try {
                val name = displayName(context, uri) ?: "recording.m4a"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalStateException("Cannot read the file")
                val number = manualNumber.trim().ifBlank { RecordingAnalyzer.numberFrom(name) ?: "" }
                RecordingAnalyzer.analyse(bytes, name, number)
            } catch (e: Exception) {
                toast("Check failed: ${e.message}")
            }
            analysing = false
        }
    }

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Call recording", style = MaterialTheme.typography.headlineSmall)
        Text("Digital Shield records calls from unknown numbers in 10-second chunks, turns them into text and warns you while the call is happening. Saved contacts are never recorded.",
            color = muted)
        Spacer(Modifier.height(12.dp))

        // ---------- Live recording (Part 2) ----------
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
                        toast("Test failed: ${e.message}")
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

        // ---------- Fallback: the phone's own recorder (Part 3) ----------
        Spacer(Modifier.height(24.dp))
        Text("Fallback: your phone's own call recorder", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Text("If live recording can't hear the caller, Digital Shield can check the recordings your phone makes itself, in 15-second parts, after the call.",
            color = muted)

        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Automatically check new call recordings", fontWeight = FontWeight.Bold)
                Text("Watches your phone's call-recording folder. Recordings of saved contacts are skipped.",
                    color = muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                when {
                    Build.VERSION.SDK_INT < 30 -> Text("Needs Android 11 or later.", color = muted)
                    !allFiles -> Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")))
                    }) { Text("Allow all-files access") }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = autoRec, onCheckedChange = { on ->
                            autoRec = on
                            CallRecordingPrefs.setAutoRecordings(context, on)
                            if (on) RecordingWatchService.start(context) else RecordingWatchService.stop(context)
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(if (autoRec) "On" else "Off")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Check a recording now", fontWeight = FontWeight.Bold)
                OutlinedTextField(manualNumber, { manualNumber = it }, label = { Text("Caller's number (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }, enabled = !analysing,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (analysing) "Checking… this can take a minute" else "Choose a recording")
                }
            }
        }

        analysed.forEach { a ->
            Spacer(Modifier.height(8.dp))
            RecordingResultCard(a)
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

/** Result of a checked recording: a timeline of 15-second parts with the score after each. */
@Composable
fun RecordingResultCard(a: RecordingAnalyzer.RecordingAnalysis) = AlertCard(a.result.liveRisk.band) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🎧 ${a.name}", fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        BandPill(a.result.liveRisk.band, a.result.liveRisk.score)
    }
    Text("Caller: ${a.number.ifBlank { "unknown" }} · ${a.result.chunks} part(s) of 15 s",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    a.result.timeline.forEach { t ->
        Spacer(Modifier.height(6.dp))
        Text("${mmss(t.offsetS)} – ${mmss(t.offsetS + 15)}  ·  ${t.bandAfter} ${t.scoreAfter}",
            color = bandColor(t.bandAfter), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        if (t.transcript.isNotBlank()) Text("\u201C${t.transcript}\u201D", maxLines = 3)
    }
}