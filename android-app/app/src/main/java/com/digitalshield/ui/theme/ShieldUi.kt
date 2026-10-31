package com.digitalshield.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

object Gradients {
    val Brand = listOf(Color(0xFF1D4ED8), Color(0xFF0891B2))
    val Danger = listOf(Color(0xFFDC2626), Color(0xFFEA580C))
    val Purple = listOf(Color(0xFF7C3AED), Color(0xFFDB2777))
    val Ocean = listOf(Color(0xFF2563EB), Color(0xFF0E7490))
    val Emerald = listOf(Color(0xFF047857), Color(0xFF0F766E))
    val Amber = listOf(Color(0xFFB45309), Color(0xFFEA580C))
    val Indigo = listOf(Color(0xFF4338CA), Color(0xFF7C3AED))
}

fun bandGradient(band: String): List<Color> = when (band) {
    "CRITICAL" -> Gradients.Danger
    "HIGH" -> Gradients.Amber
    "MEDIUM" -> listOf(Color(0xFFCA8A04), Color(0xFFD97706))
    else -> Gradients.Emerald
}

@Composable
fun GradientTile(
    emoji: String, title: String, subtitle: String, colors: List<Color>,
    modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(colors))
            .clickable(enabled = enabled, onClick = onClick).padding(16.dp)
    ) {
        Column {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** #2: shield with radar rings that pulse while all protections are on. */
@Composable
fun RadarShield(active: Boolean) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "p")
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(Modifier.fillMaxSize()) {
                listOf(p, (p + 0.5f) % 1f).forEach { f ->
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - f) * 0.6f),
                        radius = size.minDimension / 2 * (0.5f + f * 0.9f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
        Text("🛡", fontSize = 30.sp)
    }
}

@Composable
fun ShieldHeader(
    active: Int, total: Int,
    chips: @Composable RowScope.() -> Unit,
    action: (@Composable () -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(Gradients.Brand))
            .pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress?.invoke() }) }
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarShield(active = active == total)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Digital Shield", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (active == total) "You're protected" else "$active of $total protections on",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = chips)
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                action()
            }
        }
    }
}

@Composable
fun StatusChip(label: String, on: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (on) 0.22f else 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Color(0xFF4ADE80) else Color(0xFFFCA5A5)))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun BandPill(band: String, score: Int) {
    Text("$band  $score/100", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(bandGradient(band)))
            .padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun AlertCard(band: String, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(Brush.verticalGradient(bandGradient(band))))
        Column(Modifier.padding(14.dp), content = content)
    }
}

/* ---------- #4 Daily safety tip ---------- */
private val TIPS = listOf(
    "There is no \u201Cdigital arrest\u201D in Indian law. No officer can arrest you over a video call.",
    "Police, CBI, ED and RBI never ask you to transfer money to \u201Cverify\u201D or \u201Cclear\u201D your name.",
    "Never share an OTP, PIN or CVV, not even with someone claiming to be from your bank.",
    "You scan a QR code to PAY money, never to receive it.",
    "Scammers keep you on the call so you can't ask anyone. Hang up and call someone you trust.",
    "Never install AnyDesk, TeamViewer or any other app a caller asks you to install.",
    "Real notices and summons come through official channels, not as WhatsApp messages.",
    "Caller ID can be faked. Call back on the official number from the organisation's website.",
    "Lost money to a scam? Call 1930 at once. The first hours matter most.",
    "Report scam calls and messages on Sanchar Saathi (Chakshu).",
    "Be wary of short links and bank-like websites with extra words such as \u201Ckyc\u201D or \u201Cverify\u201D.",
    "Banks send SMS from official sender IDs, not from personal mobile numbers."
)

@Composable
fun TipCard() {
    var i by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % TIPS.size) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { i = (i + 1) % TIPS.size }.padding(14.dp)
    ) {
        Text("💡", fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Did you know?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Text(TIPS[i])
            Text("Tap for another tip", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ---------- #6 Shield Score ---------- */
data class ScoreTip(val label: String, val points: Int, val done: Boolean, val action: (() -> Unit)? = null)

@Composable
fun ShieldScoreCard(items: List<ScoreTip>) {
    val score = items.filter { it.done }.sumOf { it.points }.coerceAtMost(100)
    val animated by animateFloatAsState(score / 100f, tween(900), label = "score")
    val (level, levelColor) = when {
        score >= 90 -> "Excellent" to Color(0xFF22C55E)
        score >= 70 -> "Good" to Color(0xFF22D3EE)
        score >= 40 -> "Fair" to Color(0xFFF59E0B)
        else -> "At risk" to Color(0xFFEF4444)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.Gray.copy(alpha = 0.2f), 135f, 270f, false, topLeft, arc,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFF22D3EE))),
                        135f, 270f * animated, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text("/ 100", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Shield Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(level, color = levelColor, fontWeight = FontWeight.Bold)
                Text(if (score == 100) "Every protection is on. Well done!" else "Turn on the items below to raise your score.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items.filter { !it.done }.forEach { t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = t.action != null) { t.action?.invoke() }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("+${t.points}", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                Text(t.label, modifier = Modifier.weight(1f))
                if (t.action != null) Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}