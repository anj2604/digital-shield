package com.digitalshield

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.digitalshield.data.remote.ApiClient
import com.digitalshield.data.remote.CaseRequest
import com.digitalshield.data.remote.EvidenceRef
import com.digitalshield.ui.theme.DigitalShieldTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton

data class EvidenceItem(
    val type: String, val id: String, val title: String, val detail: String,
    val band: String, val score: Int, val time: String
) {
    val key get() = "$type:$id"
}

class ReportIncidentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    ReportIncidentScreen(Modifier.padding(p)) { finish() }
                }
            }
        }
    }
}

suspend fun loadEvidence(): List<EvidenceItem> = coroutineScope {
    val sms = async { ApiClient.smsApi.list() }
    val shots = async { ApiClient.screenshotApi.list() }
    val calls = async { ApiClient.callApi.list() }
    val all = sms.await().mapNotNull { s ->
        s.id?.let { EvidenceItem("sms", it, "SMS from ${s.sender}", s.body, s.riskBand, s.riskScore, s.receivedAt) }
    } + shots.await().map { s ->
        EvidenceItem("screenshot", s.id, "Screenshot", s.ocrText.ifBlank { "(no text found)" }, s.riskBand, s.riskScore, s.sharedAt)
    } + calls.await().mapNotNull { c ->
        c.id?.let {
            EvidenceItem("call", it, "Call from ${c.phoneNumber}",
                "Indicators: ${c.indicators.joinToString().ifEmpty { "none" }}", c.riskBand, c.riskScore, c.createdAt)
        }
    }
    all.sortedByDescending { it.time }
}

@Composable
fun ReportIncidentScreen(modifier: Modifier, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val muted = Color(0xFF6B7A8F)
    var evidence by remember { mutableStateOf<List<EvidenceItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var title by remember { mutableStateOf("Suspected digital arrest scam") }
    var description by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val loaded = loadEvidence()
            evidence = loaded
        } catch (e: Exception) {
            error = e.message ?: "Could not load evidence"
        }
    }

    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                Text("Report incident", style = MaterialTheme.typography.headlineSmall)
                Text("Tick the SMS, calls and screenshots that belong to this incident.", color = muted)
                Row {
                    TextButton(onClick = { selected.clear(); selected.addAll(evidence.orEmpty().map { it.key }) }) { Text("Select all") }
                    TextButton(onClick = { selected.clear() }) { Text("Clear") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("What happened? (optional)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        }
        val list = evidence
        when {
            error != null -> item { Text("Could not load evidence: $error", color = Color(0xFFEF4444)) }
            list == null -> item { Text("Loading evidence…") }
            list.isEmpty() -> item { Text("No stored evidence yet. Unknown SMS, calls and screenshots appear here.") }
            else -> items(list, key = { it.key }) { ev ->
                EvidenceRow(ev, ev.key in selected) { checked ->
                    if (checked) selected.add(ev.key) else selected.remove(ev.key)
                }
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        creating = true
                        try {
                            val refs = evidence.orEmpty().filter { it.key in selected }.map { EvidenceRef(it.type, it.id) }
                            val case = ApiClient.caseApi.create(
                                CaseRequest(title.ifBlank { "Suspected digital arrest scam" }, description, refs)
                            )
                            context.startActivity(ReportViewerActivity.intent(context, case.id))
                            onDone()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Report failed: ${e.message}", Toast.LENGTH_LONG).show()
                            creating = false
                        }
                    }
                },
                enabled = selected.isNotEmpty() && !creating,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (creating) "Creating report…" else "Create report (${selected.size} selected)") }
        }
    }
}

@Composable
fun EvidenceRow(ev: EvidenceItem, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, onChange)
            Column(Modifier.weight(1f)) {
                Text("${ev.title}  •  ${ev.band} ${ev.score}", color = bandColor(ev.band),
                    style = MaterialTheme.typography.titleSmall)
                Text(ev.detail, maxLines = 2)
                Text(ev.time.take(16).replace("T", " "), color = Color(0xFF6B7A8F),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}