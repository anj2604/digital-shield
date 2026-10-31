package com.digitalshield

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.digitalshield.data.CallStore
import com.digitalshield.data.ResultStore
import com.digitalshield.data.ScreenshotStore
import com.digitalshield.services.SafeExitActions
import com.digitalshield.services.ScreenshotProcessor
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.launch

private const val MAX_SCREENSHOTS = 10

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(Modifier.padding(innerPadding), resumeTick.value,
                        onAnalyseScreenshots = { analyseScreenshots(it) })
                }
            }
        }
        handleShareIntent(intent)
        FileGuardActivity.resumeAutoScan(this)
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value++          // refresh the protection status when coming back
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

fun bandColor(band: String): Color = when (band) {
    "CRITICAL" -> Color(0xFFEF4444)
    "HIGH" -> Color(0xFFF59E0B)
    "MEDIUM" -> Color(0xFFEAB308)
    else -> Color(0xFF22C55E)
}

private data class QuickAction(val emoji: String, val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
fun HomeScreen(modifier: Modifier = Modifier, resumeTick: Int, onAnalyseScreenshots: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val wanted = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    fun screeningHeld() = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    fun downloadsProtected() = FileGuardActivity.autoScanWanted(context) && FileGuardActivity.hasAllFilesAccess()

    var smsOn by remember { mutableStateOf(granted(Manifest.permission.RECEIVE_SMS)) }
    var contactsOn by remember { mutableStateOf(granted(Manifest.permission.READ_CONTACTS)) }
    var screeningOn by remember { mutableStateOf(screeningHeld()) }
    var downloadsOn by remember { mutableStateOf(downloadsProtected()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        smsOn = res[Manifest.permission.RECEIVE_SMS] ?: smsOn
        contactsOn = res[Manifest.permission.READ_CONTACTS] ?: contactsOn
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        screeningOn = screeningHeld()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onAnalyseScreenshots(uris)
    }

    LaunchedEffect(Unit) {
        val missing = wanted.filter { !granted(it) }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
    LaunchedEffect(resumeTick) {
        smsOn = granted(Manifest.permission.RECEIVE_SMS)
        contactsOn = granted(Manifest.permission.READ_CONTACTS)
        screeningOn = screeningHeld()
        downloadsOn = downloadsProtected()
    }

    val smsResults by ResultStore.results.collectAsState()
    val callResults by CallStore.calls.collectAsState()
    val shots by ScreenshotStore.shots.collectAsState()
    val busy by ScreenshotStore.busy.collectAsState()
    var tab by remember { mutableStateOf(0) }

    fun open(cls: Class<*>) = context.startActivity(Intent(context, cls))
    val fix: () -> Unit = {
        val missing = wanted.filter { !granted(it) }
        when {
            missing.isNotEmpty() -> permLauncher.launch(missing.toTypedArray())
            !screeningOn -> roleManager?.let { roleLauncher.launch(it.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }
            !downloadsOn -> open(FileGuardActivity::class.java)
        }
    }

    val actions = listOf(
        QuickAction("📷", "Screenshots", if (busy) "Analysing…" else "Check chats and notices") { if (!busy) picker.launch("image/*") },
        QuickAction("🔗", "Link & QR", "Check before you open or pay") { open(LinkScanActivity::class.java) },
        QuickAction("📁", "Files", "Open downloads safely") { open(FileGuardActivity::class.java) },
        QuickAction("🔐", "OTP & SIM", "Forwarding and SIM checks") { open(SimOtpActivity::class.java) },
        QuickAction("📝", "Report incident", "Build a PDF case report") { open(ReportIncidentActivity::class.java) },
        QuickAction("☎️", "Call 1930", "Cyber-crime helpline") { SafeExitActions.dialHelpline(context) },
    )

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AppHeader() }
        item {
            StatusHero(
                listOf("SMS" to smsOn, "Contacts" to contactsOn, "Calls" to screeningOn, "Downloads" to downloadsOn),
                onFix = fix
            )
        }
        item { SafeExitButton { context.startActivity(SafeExitActivity.intent(context, null, null, -1)) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Check something")
                actions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { a -> ActionTile(a, Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { TrustedContactCard() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Recent activity")
                Segmented(
                    listOf("SMS (${smsResults.size})", "Calls (${callResults.size})", "Screens (${shots.size})"),
                    selected = tab
                ) { tab = it }
            }
        }
        when (tab) {
            0 -> if (smsResults.isEmpty()) item { EmptyState("No suspicious messages yet. Digital Shield checks every new SMS.") }
            else items(smsResults) { r ->
                RiskCard(r.riskBand, r.riskScore, "SMS from ${r.sender}", r.body,
                    "Indicators: ${r.indicators.joinToString().ifEmpty { "none" }}",
                    if (r.stored) "Unknown sender · stored as evidence" else "Saved contact · not stored")
            }
            1 -> if (callResults.isEmpty()) item { EmptyState("No risky calls yet. Unknown callers are screened automatically.") }
            else items(callResults) { r ->
                RiskCard(r.riskBand, r.riskScore, "Call from ${r.phoneNumber}", null,
                    "Indicators: ${r.indicators.joinToString().ifEmpty { "none" }}",
                    if (r.stored) "Unknown number · stored" else "Saved contact · not stored")
            }
            else -> if (shots.isEmpty()) item { EmptyState("No screenshots checked yet. Tap Screenshots above.") }
            else items(shots) { r ->
                RiskCard(r.riskBand, r.riskScore, "Screenshot", r.ocrText.ifBlank { "(no text found)" },
                    "Indicators: ${r.indicators.joinToString().ifEmpty { "none" }}",
                    "Evidence SHA-256: ${r.sha256.take(16)}…")
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF1677FF)))),
            contentAlignment = Alignment.Center
        ) { Text("🛡", fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Digital Shield", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Scam early-warning", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusHero(items: List<Pair<String, Boolean>>, onFix: () -> Unit) {
    val allOn = items.all { it.second }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B3B8C), Color(0xFF1677FF), Color(0xFF0891B2))))
            .padding(20.dp)
    ) {
        Column {
            Text(if (allOn) "You're protected" else "Protection incomplete",
                color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (allOn) "Calls, messages and downloads are being checked."
            else "Some protections are off. Turn them on below.",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            items.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, on) -> StatusChip(label, on, Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!allOn) {
                Button(onClick = onFix,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B3B8C))) {
                    Text("Turn on protection", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, on: Boolean, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (on) "✓" else "✕", color = if (on) Color(0xFFBBF7D0) else Color(0xFFFECACA), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SafeExitButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🆘  SAFE EXIT", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Being pressured on a call? Tap here", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTile(a: QuickAction, modifier: Modifier) {
    Card(
        onClick = a.onClick,
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text(a.emoji, fontSize = 18.sp) }
            Spacer(Modifier.height(8.dp))
            Text(a.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(a.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RiskCard(band: String, score: Int, title: String, body: String?, details: String, footer: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(bandColor(band)))
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$band $score",
                        color = if (band == "CRITICAL") Color.White else Color(0xFF1F2937),
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(bandColor(band))
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                body?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(text, Modifier.padding(20.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}