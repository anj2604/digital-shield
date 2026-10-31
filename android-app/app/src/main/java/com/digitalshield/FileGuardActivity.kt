package com.digitalshield

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitalshield.data.QuarantineStore
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.ReportRequest
import com.digitalshield.services.DownloadWatchService
import com.digitalshield.services.FileGuard
import com.digitalshield.services.FileVerdict
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.launch

class FileGuardActivity : ComponentActivity() {
    private val resumeTick = mutableStateOf(0)
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming.value = uriFrom(intent)
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    FileGuardScreen(Modifier.padding(p), incoming.value, resumeTick.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = uriFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value++
    }

    @Suppress("DEPRECATION")
    private fun uriFrom(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND ->
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else i.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }

    companion object {
        private const val PREFS = "file_guard"
        fun autoScanWanted(c: Context) =
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_scan", false)
        fun setAutoScan(c: Context, on: Boolean) =
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto_scan", on).apply()
        fun hasAllFilesAccess() = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
        fun resumeAutoScan(c: Context) {
            if (autoScanWanted(c) && hasAllFilesAccess()) DownloadWatchService.start(c)
        }
    }
}

@Composable
fun FileGuardScreen(modifier: Modifier, incoming: Uri?, resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val muted = Color(0xFF6B7A8F)
    var verdict by remember { mutableStateOf<FileVerdict?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var allFiles by remember { mutableStateOf(FileGuardActivity.hasAllFilesAccess()) }
    var autoOn by remember { mutableStateOf(FileGuardActivity.autoScanWanted(context)) }
    var quarantine by remember { mutableStateOf(QuarantineStore.list(context)) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()

    fun scan(uri: Uri) {
        scope.launch {
            scanning = true; error = null; verdict = null
            try {
                val (file, name) = FileGuard.seal(context, uri)
                verdict = FileGuard.check(context, file, name, "manual")
            } catch (e: Exception) {
                error = "Could not check the file: ${e.message}"
            }
            scanning = false
        }
    }

    LaunchedEffect(incoming) { incoming?.let { scan(it) } }
    LaunchedEffect(resumeTick) {
        allFiles = FileGuardActivity.hasAllFilesAccess()
        quarantine = QuarantineStore.list(context)
        if (autoOn && allFiles) DownloadWatchService.start(context)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scan(uri)
    }

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("File safety", style = MaterialTheme.typography.headlineSmall)
        Text("Files are checked without opening them, and open only when they are safe.", color = muted)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !scanning) {
            Text(if (scanning) "Checking…" else "Scan a file")
        }
        error?.let { Text(it, color = Color(0xFFEF4444), modifier = Modifier.padding(top = 8.dp)) }

        verdict?.let { v ->
            VerdictCard(
                v,
                onOpen = { FileGuard.open(context, v.file, v.detectedType) },
                onReport = {
                    scope.launch {
                        try {
                            ApiClient.linkApi.report(ReportRequest("file", v.sha256))
                            toast("Reported. Thank you.")
                        } catch (e: Exception) { toast("Report failed: ${e.message}") }
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Automatic download protection", fontWeight = FontWeight.Bold)
                Text("New files in your Download folder are checked automatically. Dangerous ones are moved to quarantine so they cannot be opened.")
                Spacer(Modifier.height(8.dp))
                when {
                    Build.VERSION.SDK_INT < 30 -> Text("Needs Android 11 or later.", color = muted)
                    !allFiles -> {
                        Text("Needs \u201CAll files access\u201D so the app can check and move downloads.", color = muted)
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")))
                        }) { Text("Allow all-files access") }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = autoOn, onCheckedChange = { on ->
                            autoOn = on
                            FileGuardActivity.setAutoScan(context, on)
                            if (on) DownloadWatchService.start(context) else DownloadWatchService.stop(context)
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(if (autoOn) "On" else "Off")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Quarantine (${quarantine.size})", fontWeight = FontWeight.Bold)
                if (quarantine.isEmpty()) Text("Nothing in quarantine.", color = muted)
                quarantine.forEach { q ->
                    Spacer(Modifier.height(8.dp))
                    Text("${q.name}  •  ${q.band}", color = bandColor(q.band))
                    Row {
                        TextButton(onClick = {
                            QuarantineStore.delete(context, q)
                            quarantine = QuarantineStore.list(context)
                        }) { Text("Delete permanently") }
                        TextButton(enabled = allFiles, onClick = {
                            toast(if (QuarantineStore.restore(context, q)) "Restored to Download" else "Restore failed")
                            quarantine = QuarantineStore.list(context)
                        }) { Text("Restore (I trust it)") }
                    }
                }
            }
        }
    }
}

@Composable
fun VerdictCard(v: FileVerdict, onOpen: () -> Unit, onReport: () -> Unit) {
    var confirmed by remember(v.sha256) { mutableStateOf(false) }
    val muted = Color(0xFF6B7A8F)
    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            val headline = when (v.decision) {
                "safe" -> "✓ No threats found"
                "caution" -> "⚠ Be careful"
                else -> "⛔ Blocked: do not open"
            }
            Text(headline, color = bandColor(v.band), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${v.name}  •  ${v.band} ${v.score}/100")
            Text(v.verdict)
            if (v.offline) Text("Server unreachable: checked on this phone only.", color = muted)
            Spacer(Modifier.height(8.dp))
            if (v.findings.isEmpty()) Text("No warning signs found.")
            v.findings.forEach { Text("• ${it.label}  (+${it.weight})") }
            Text("SHA-256: ${v.sha256.take(24)}…", color = muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            when (v.decision) {
                "safe" -> Button(onClick = onOpen) { Text("Open file") }
                "caution" ->
                    if (!confirmed) OutlinedButton(onClick = { confirmed = true }) { Text("I trust the sender, let me open it") }
                    else Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))) {
                        Text("Open anyway")
                    }
                else -> Text("This file will not be opened.", fontWeight = FontWeight.Bold)
            }
            if (v.decision != "safe") {
                OutlinedButton(onClick = onReport, modifier = Modifier.padding(top = 8.dp)) { Text("Report this file") }
            }
        }
    }
}