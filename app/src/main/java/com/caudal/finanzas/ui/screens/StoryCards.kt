package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.GoalStatus
import com.caudal.finanzas.domain.PepeTip
import com.caudal.finanzas.domain.WeeklyRead
import com.caudal.finanzas.ui.LocalMoney
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")
private val Ink = Color(0xFF17150F)
private val Muted = Color(0xFF7C7566)
private val Chip = Color(0xFFEDE6D6)
private val Sparkle = Color(0xFF2E6B3F)
private val Serif = com.caudal.finanzas.ui.SerifDisplay
/** Euros sin céntimos, como en la referencia ("3.240 €"). */
private fun whole(v: Double) = String.format(ES, "%,.0f €", v)

/** Etiqueta pequeña con destello, como "✦ Mira suggests". */
@Composable
private fun SparkLabel(text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFDCEBD3), Color(0xFFEAF2E4))))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.caudal.finanzas.R.drawable.ic_pepe), null, Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun PillButton(text: String, dark: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(CircleShape).background(if (dark) Ink else Chip).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (dark) Color.White else Ink) }
}

/** "Pepe te recomienda": pasar una cantidad segura al ahorro esta semana. */
@Composable
fun PepeTipCard(tip: PepeTip, onMove: () -> Unit, onLater: () -> Unit) {
    val money = LocalMoney.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color.White).padding(24.dp)) {
        SparkLabel("Pepe te recomienda")
        Text(tip.text, fontSize = 22.sp, lineHeight = 28.sp, fontFamily = Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Pasar ${money.format(tip.amount).substringBefore(',')} €", true, Modifier.weight(1f), onMove)
            PillButton("Ahora no", false, Modifier.weight(1f), onLater)
        }
    }
}

/** "Lectura semanal": tarjetas deslizables con en qué se te fue el dinero esta semana. */
@Composable
fun WeeklyReadCard(r: WeeklyRead, onLimit: (com.caudal.finanzas.domain.SpendTag) -> Unit, onDetails: (com.caudal.finanzas.domain.SpendTag) -> Unit) {
    val pager = rememberPagerState { r.insights.size }
    val range = "${r.from.dayOfMonth}–${r.to.dayOfMonth} ${r.to.month.getDisplayName(java.time.format.TextStyle.SHORT, ES).trimEnd('.')}".uppercase(ES)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF6F1E6), Color(0xFFDCEBD3))))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("LECTURA SEMANAL · $range", fontSize = 11.sp, letterSpacing = 2.sp, color = Muted)
        Text("En qué se te fue el dinero sin darte cuenta", fontSize = 26.sp, lineHeight = 32.sp, fontFamily = Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        HorizontalPager(pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp), pageSpacing = 16.dp, modifier = Modifier.padding(top = 8.dp)) { i ->
            val ins = r.insights[i]
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(24.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.radialGradient(if (ins.up) listOf(Color(0xFFF5A3B4), Color(0xFFE0607A), Color(0xFFC2415C)) else listOf(Color(0xFF9FC9A8), Color(0xFF2E6B3F), Color(0xFF1F4D2C)), center = Offset(30f, 26f))),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Text(ins.title, fontSize = 22.sp, lineHeight = 28.sp, fontFamily = Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 16.dp))
                Text(ins.detail, fontSize = 14.sp, lineHeight = 20.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ins.checks.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Ink)
                            Spacer(Modifier.width(8.dp))
                            Text(c, fontSize = 14.sp, color = Ink)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ins.up) PillButton("Poner límite", true, Modifier.weight(1f)) { onLimit(ins.tag) }
                    PillButton("Detalles", false, Modifier.weight(1f)) { onDetails(ins.tag) }
                }
            }
        }
        if (r.insights.size > 1) Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(r.insights.size) { i ->
                Box(Modifier.size(width = if (i == pager.currentPage) 24.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i == pager.currentPage) Ink else Color(0x33000000)))
            }
        }
    }
}

/** Objetivo de ahorro al estilo de la referencia: cabecera bronce con el anillo, acciones y el detalle con las semanas. */
@Composable
fun SavingsGoalHero(
    g: GoalStatus, weekly: List<Pair<String, Double>>, thisWeek: Double?, locked: Boolean, weeklyTip: Boolean,
    onWithdraw: () -> Unit, onAdd: () -> Unit, onLock: () -> Unit, onWeeklyTip: (Boolean) -> Unit, onEdit: () -> Unit,
) {
    val money = LocalMoney.current
    val frac = if (g.goal.target > 0) (g.current / g.goal.target).coerceIn(0.0, 1.0) else 0.0
    val left = (g.goal.target - g.current).coerceAtLeast(0.0)
    val today = java.time.LocalDate.now()
    val eta = if (left <= 0) today else if (g.pace > 0) today.plusDays((left / g.pace * 30.4).toLong()) else null
    val fmtDate = DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", ES)
    val weeksDiff = eta?.let { java.time.temporal.ChronoUnit.WEEKS.between(it, g.goal.deadline) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2E6B3F), Color(0xFF24573A), Color(0xFF1F4D2C))))) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(g.goal.name.ifBlank { "Mi objetivo" }, fontSize = 32.sp, fontFamily = Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center)
            Text("${whole(g.current)} de ${whole(g.goal.target)}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
        }
        // Progreso con el anillo.
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.14f)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Progreso del objetivo", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(if (left <= 0) "¡Conseguido!" else "Te faltan ${whole(left)}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color.White.copy(alpha = 0.25f), -90f, 360f, false, style = Stroke(5.dp.toPx()))
                    drawArc(Color.White, -90f, (360 * frac).toFloat(), false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                }
                Text("${(frac * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        // Acciones.
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalAction(Icons.Filled.Remove, "Retirar", Modifier.weight(1f), enabled = !locked, onClick = onWithdraw)
            GoalAction(Icons.Filled.Add, "Añadir", Modifier.weight(1f), onClick = onAdd)
            GoalAction(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (locked) "Bloqueado" else "Bloquear", Modifier.weight(1f), onClick = onLock)
        }
        // Detalle en blanco.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color.White).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color(0xFFEDE6D6)))
            }
            Row(Modifier.clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFDCEBD3)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Flag, null, Modifier.size(24.dp), tint = Color(0xFF2E6B3F))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fecha estimada", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text(
                        when {
                            eta == null -> "Aún no ahorras para él: empieza con algo cada semana"
                            weeksDiff == null || weeksDiff == 0L -> eta.format(fmtDate)
                            weeksDiff > 0 -> eta.format(fmtDate) + " · $weeksDiff ${if (weeksDiff == 1L) "semana" else "semanas"} antes"
                            else -> eta.format(fmtDate) + " · ${-weeksDiff} ${if (weeksDiff == -1L) "semana" else "semanas"} tarde"
                        },
                        fontSize = 14.sp, color = Muted,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = Ink)
            }
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(Color(0xFFEDE6D6)))
            DetailRow(Icons.Filled.CalendarMonth, "Fecha de inicio", g.goal.start.format(DateTimeFormatter.ofPattern("d MMM yyyy", ES)))
            DetailRow(Icons.Filled.Schedule, "Quedan", "${java.time.temporal.ChronoUnit.DAYS.between(today, g.goal.deadline).coerceAtLeast(0)} días")
        }
    }
}

@Composable
private fun GoalAction(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = if (enabled) 1f else 0.5f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(16.dp), tint = Ink) }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = Muted)
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = Muted)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}

