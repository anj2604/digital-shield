package com.digitalshield

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalshield.ui.Gradients

object Onboarding {
    private fun p(c: Context) = c.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    fun done(c: Context) = p(c).getBoolean("done", false)
    fun markDone(c: Context) = p(c).edit().putBoolean("done", true).apply()
    fun reset(c: Context) = p(c).edit().putBoolean("done", false).apply()
}

private val PAGES = listOf(
    Triple("🎭", "What is a \u201Cdigital arrest\u201D scam?",
        "Fraudsters pose as police, CBI, customs or RBI officers on calls and video calls. They claim you are under \u201Cdigital arrest\u201D, keep you on the line for hours and pressure you to transfer money.\n\nThere is no such thing as a digital arrest in Indian law."),
    Triple("🛡", "How Digital Shield protects you",
        "• Warns you about scam SMS and calls as they arrive\n• Checks screenshots, links, QR codes and downloaded files\n• Safe Exit helps you hang up and alerts your trusted circle\n• Keeps tamper-proof evidence for your complaint"),
    Triple("🔐", "Permissions we need, and why",
        "• SMS: to check incoming messages\n• Contacts: so messages from people you know stay private\n• Phone and caller ID: to screen unknown callers and help you hang up\n• Notifications: to warn you instantly\n\nMessages from saved contacts are never stored, and your files never leave your phone.")
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val last = step == PAGES.lastIndex
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07111F), Color(0xFF0B2545))))
            .systemBarsPadding().padding(28.dp)
    ) {
        Crossfade(targetState = step, label = "onboarding") { i ->
            val (emoji, title, body) = PAGES[i]
            Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
                Text(emoji, fontSize = 64.sp)
                Spacer(Modifier.height(24.dp))
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
                Spacer(Modifier.height(16.dp))
                Text(body, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAGES.indices.forEach {
                    Box(Modifier.size(if (it == step) 24.dp else 8.dp, 8.dp).clip(RoundedCornerShape(50))
                        .background(if (it == step) Color(0xFF22D3EE) else Color.White.copy(alpha = 0.3f)))
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(Gradients.Brand))
                    .clickable { if (last) onFinish() else step++ },
                contentAlignment = Alignment.Center
            ) { Text(if (last) "Get started" else "Next", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Back", color = Color.White) } else Spacer(Modifier.width(1.dp))
                if (!last) TextButton(onClick = onFinish) { Text("Skip", color = Color.White.copy(alpha = 0.7f)) }
            }
        }
    }
}