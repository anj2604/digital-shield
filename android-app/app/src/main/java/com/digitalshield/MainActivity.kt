package com.digitalshield

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.digitalshield.data.CallStore
import com.digitalshield.data.ResultStore
import com.digitalshield.data.ScreenshotStore
import com.digitalshield.data.TrustedContactStore
import com.digitalshield.data.remote.CallResult
import com.digitalshield.data.remote.ScreenshotResult
import com.digitalshield.data.remote.SmsResponse
import com.digitalshield.services.ScamAlert
import com.digitalshield.services.ScreenshotProcessor
import com.digitalshield.ui.*
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.launch

private const val MAX_SCREENSHOTS = 10

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalShieldTheme {
                var onboarded by remember { mutableStateOf(Onboarding.done(this@MainActivity)) }
                if (!onboarded) {
                    OnboardingScreen { Onboarding.markDone(this@MainActivity); onboarded = true }
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        HomeScreen(
                            Modifier.padding(innerPadding),
                            onAnalyseScreenshots = { analyseScreenshots(it) },
                            onReplayIntro = { Onboarding.reset(this@MainActivity); onboarded = false }
                        )
                    }
                }
            }
        }
        handleShareIntent(intent)
        FileGuardActivity.resumeAutoScan(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShareIntent(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) analyseScreenshots(uris)
    }

    private fun analyseScreenshots(uris: List<Uri>) {
        val batch = uris.take(MAX_SCREENSHOTS)
        if (uris.size > MAX_SCREENSHOTS) {
            Toast.makeText(this, "Analysing the first $MAX_SCREENSHOTS screenshots", Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            ScreenshotStore.setBusy(true)
            var failed = 0
            for (uri in batch) {
                try {
                    ScreenshotStore.add(ScreenshotProcessor.process(applicationContext, uri))
                } catch (e: Exception) {
                    failed++
                    Log.e("DigitalShield", "Screenshot failed", e)
                }
            }
            ScreenshotStore.setBusy(false)
            val ok = batch.size - failed
            Toast.makeText(this@MainActivity,
                "Analysed $ok of ${batch.size} screenshot(s)" + if (failed > 0) " ($failed failed)" else "",
                Toast.LENGTH_LONG).show()
        }
    }
}

// Used by other screens too: keep it here
fun bandColor(band: String): Color = when (band) {
    "CRITICAL" -> Color(0xFFEF4444)
    "HIGH" -> Color(0xFFF59E0B)
    "MEDIUM" -> Color(0xFFEAB308)
    else -> Color(0xFF22C55E)
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onAnalyseScreenshots: (List<Uri>) -> Unit,
    onReplayIntro: () -> Unit = {}
) {
    val context = LocalContext.current
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    fun openAppSettings() = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    fun open(cls: Class<*>) = context.startActivity(Intent(context, cls))

    // Re-check everything whenever the user comes back to this screen
    var tick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val wanted = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    LaunchedEffect(Unit) {
        val missing = wanted.filter { !granted(it) }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    fun requestScreening() = roleManager?.let {
        roleLauncher.launch(it.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    // Current protection status (recomputed on every tick)
    val smsOn = remember(tick) { granted(Manifest.permission.RECEIVE_SMS) }
    val contactsOn = remember(tick) { granted(Manifest.permission.READ_CONTACTS) }
    val screeningOn = remember(tick) { roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true }
    val notifOn = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS) }
    val fullScreenOn = remember(tick) { ScamAlert.canFullScreen(context) }
    val trusted = remember(tick) { TrustedContactStore.loadAll(context).count { it.consent } }
    val downloadOn = remember(tick) { FileGuardActivity.autoScanWanted(context) && FileGuardActivity.hasAllFilesAccess() }

    val scoreItems = listOf(
        ScoreTip("SMS protection", 20, smsOn) { openAppSettings() },
        ScoreTip("Call screening", 20, screeningOn) { requestScreening() },
        ScoreTip("Contact privacy check", 10, contactsOn) { openAppSettings() },
        ScoreTip("Add at least 1 trusted contact (below)", 10, trusted >= 1),
        ScoreTip("Have 3 or more trusted contacts", 10, trusted >= 3),
        ScoreTip("Download protection", 15, downloadOn) { open(FileGuardActivity::class.java) },
        ScoreTip("Instant and full-screen alerts", 15, notifOn && fullScreenOn) {
            if (!notifOn || Build.VERSION.SDK_INT < 34) openAppSettings()
            else context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")))
        }
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onAnalyseScreenshots(uris)
    }

    val smsResults by ResultStore.results.collectAsState()
    val callResults by CallStore.calls.collectAsState()
    val shots by ScreenshotStore.shots.collectAsState()
    val busy by ScreenshotStore.busy.collectAsState()
    var demoOpen by remember { mutableStateOf(false) }

    if (demoOpen) DemoMenu(onDismiss = { demoOpen = false }, onReplayIntro = onReplayIntro)

    val active = listOf(smsOn, screeningOn, contactsOn).count { it }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ShieldHeader(
                active = active, total = 3,
                chips = {
                    StatusChip("SMS", smsOn)
                    StatusChip("Calls", screeningOn)
                    StatusChip("Contacts", contactsOn)
                },
                action = if (!screeningOn) {
                    {
                        Button(onClick = { requestScreening() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1D4ED8))
                        ) { Text("Turn on call screening", fontWeight = FontWeight.Bold) }
                    }
                } else null,
                onLongPress = { demoOpen = true }   // hidden demo mode
            )
        }

        item {
            GradientTile("🚨", "Safe Exit", "On a suspicious call right now? Tap for help.",
                Gradients.Danger, Modifier.fillMaxWidth()) {
                context.startActivity(SafeExitActivity.intent(context, null, null, -1))
            }
        }

        item { ShieldScoreCard(scoreItems) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientTile("🖼", "Screenshots",
                    if (busy) "Analysing…" else "Check chats, notices and video-call frames",
                    Gradients.Purple, Modifier.weight(1f).height(150.dp), enabled = !busy) { picker.launch("image/*") }
                GradientTile("🔗", "Links & QR", "Check before you open a link or pay",
                    Gradients.Ocean, Modifier.weight(1f).height(150.dp)) { open(LinkScanActivity::class.java) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientTile("📁", "Files", "Scan and open downloads safely",
                    Gradients.Emerald, Modifier.weight(1f).height(150.dp)) { open(FileGuardActivity::class.java) }
                GradientTile("🔐", "OTP & SIM", "Never share an OTP. Check call forwarding.",
                    Gradients.Amber, Modifier.weight(1f).height(150.dp)) { open(SimOtpActivity::class.java) }
            }
        }
        item {
            GradientTile("📄", "Report incident", "Bundle your evidence into a PDF report",
                Gradients.Indigo, Modifier.fillMaxWidth()) { open(ReportIncidentActivity::class.java) }
        }

        item { TipCard() }

        item { SectionTitle("Your trusted circle") }
        item { TrustedContactCard(onChanged = { tick++ }) }

        item { SectionTitle("Recent alerts") }
        if (shots.isEmpty() && callResults.isEmpty() && smsResults.isEmpty()) {
            item {
                AlertCard("LOW") {
                    Text("All clear", fontWeight = FontWeight.Bold)
                    Text("Digital Shield is watching your SMS and calls. Alerts will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(shots) { ScreenshotCard(it) }
        items(callResults) { CallResultCard(it) }
        items(smsResults) { SmsResultCard(it) }
    }
}

@Composable
fun ScreenshotCard(r: ScreenshotResult) = AlertCard(r.riskBand) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🖼  Screenshot", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        BandPill(r.riskBand, r.riskScore)
    }
    Spacer(Modifier.height(6.dp))
    Text("Text found: ${r.ocrText.ifBlank { "(none)" }}", maxLines = 3)
    Text("Indicators: ${r.indicators.joinToString().ifEmpty { "none" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Evidence SHA-256: ${r.sha256.take(16)}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun CallResultCard(r: CallResult) = AlertCard(r.riskBand) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("📞  Call from ${r.phoneNumber}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        BandPill(r.riskBand, r.riskScore)
    }
    Spacer(Modifier.height(6.dp))
    Text("Indicators: ${r.indicators.joinToString().ifEmpty { "none" }}")
    Text(if (r.stored) "Unknown number: stored as evidence" else "Saved contact: not stored",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SmsResultCard(r: SmsResponse) = AlertCard(r.riskBand) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("💬  SMS from ${r.sender}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        BandPill(r.riskBand, r.riskScore)
    }
    Spacer(Modifier.height(6.dp))
    Text(r.body, maxLines = 3)
    Text("Indicators: ${r.indicators.joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        if (r.stored) "Unknown sender: stored as evidence"
        else "Saved contact shows scam signs. Call them back on their usual number to check. (Not stored.)",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}