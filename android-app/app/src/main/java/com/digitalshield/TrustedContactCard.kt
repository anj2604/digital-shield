package com.digitalshield

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digitalshield.data.TrustedContact
import com.digitalshield.data.TrustedContactStore

@Composable
fun TrustedContactCard() {
    val context = LocalContext.current
    val muted = Color(0xFF6B7A8F)
    var contacts by remember { mutableStateOf(TrustedContactStore.loadAll(context)) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    fun refresh() { contacts = TrustedContactStore.loadAll(context) }
    fun add(n: String, p: String) {
        status = TrustedContactStore.add(context, TrustedContact(n.trim(), p.trim(), consent = true))
        refresh()
    }

    // Pick a person straight from the phone's contacts
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { cur -> if (cur.moveToFirst()) add(cur.getString(0) ?: "", cur.getString(1) ?: "") }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Trusted contacts (${contacts.size}/${TrustedContactStore.MAX})", style = MaterialTheme.typography.titleMedium)
            Text("When you press Safe Exit, everyone with alerts on gets an SMS at the same time, so someone is always reachable.",
                color = muted, style = MaterialTheme.typography.bodySmall)

            contacts.forEach { c ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name.ifBlank { "No name" }, fontWeight = FontWeight.Bold)
                        Text(c.phone, color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = c.consent, onCheckedChange = {
                        TrustedContactStore.setConsent(context, c.phone, it); refresh()
                    })
                    TextButton(onClick = {
                        TrustedContactStore.remove(context, c.phone); refresh(); status = "${c.name.ifBlank { c.phone }} removed"
                    }) { Text("Remove") }
                }
            }
            if (contacts.isNotEmpty()) Text("Switch = alerts on/off for that person", color = muted, style = MaterialTheme.typography.bodySmall)

            if (contacts.size < TrustedContactStore.MAX) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Button(onClick = { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
                    modifier = Modifier.fillMaxWidth()) { Text("Choose from contacts") }
                Text("or type one in:", color = muted, modifier = Modifier.padding(vertical = 6.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { add(name, phone); if (status?.endsWith("added") == true) { name = ""; phone = "" } },
                    enabled = phone.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
            }
            status?.let { Text(it, color = muted, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}