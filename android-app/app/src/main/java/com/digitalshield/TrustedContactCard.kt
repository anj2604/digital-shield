package com.digitalshield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digitalshield.data.TrustedContact
import com.digitalshield.data.TrustedContactStore

@Composable
fun TrustedContactCard() {
    val context = LocalContext.current
    val saved = remember { TrustedContactStore.load(context) }
    var name by remember { mutableStateOf(saved?.name ?: "") }
    var phone by remember { mutableStateOf(saved?.phone ?: "") }
    var consent by remember { mutableStateOf(saved?.consent ?: false) }
    var status by remember { mutableStateOf(if (saved != null) "Saved" else "") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Trusted contact", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it })
                Text("Alert this person when I press Safe Exit")
            }
            Button(onClick = {
                TrustedContactStore.save(context, TrustedContact(name.trim(), phone.trim(), consent))
                status = "Saved"
            }, enabled = phone.isNotBlank()) { Text("Save") }
            if (status.isNotEmpty()) Text(status, color = Color(0xFF6B7A8F))
        }
    }
}