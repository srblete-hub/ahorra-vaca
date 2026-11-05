package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.FinanceCircle
import com.caudal.finanzas.domain.FinancialCircles
import com.caudal.finanzas.domain.Pulse
import com.caudal.finanzas.domain.PulseCalculator
import com.caudal.finanzas.domain.Zone
import com.caudal.finanzas.domain.zoneOf
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.P
import java.time.LocalDate
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")
private val Bg = Color(0xFF14181C)
private val Panel = Color(0xFF22272C)
private val Track = Color(0xFF353B41)
private val Optimal = Color(0xFF8A9096)
private val Green = Color(0xFF16EC06)
private val Yellow = Color(0xFFFFDE00)
private val Red = Color(0xFFFF2B2B)
private val Teal = Color(0xFF1BE3B8)

fun zoneColor(z: Zone) = when (z) { Zone.GREEN -> Green; Zone.YELLOW -> Yellow; Zone.RED -> Red }

/** Anillo tipo WHOOP: pista gris, arco de color y, si hay, una franja gris con lo óptimo para hoy. */
@Composable
private fun Ring(fraction: Double, color: Color, optimal: Double?, size: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 9.dp.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(Track, 0f, 360f, false, tl, arc, style = Stroke(w))
            optimal?.let { o ->
                drawArc(Optimal.copy(alpha = 0.5f), -90f, (o.coerceIn(0.0, 1.0) * 360).toFloat(), false, tl, arc, style = Stroke(w))
                val a = Math.toRadians(-90.0 + o.coerceIn(0.0, 1.0) * 360)
                val r = arc.width / 2
                val c = Offset(this.size.width / 2, this.size.height / 2)
                drawLine(Color.White, Offset(c.x + (r - w) * Math.cos(a).toFloat(), c.y + (r - w) * Math.sin(a).toFloat()), Offset(c.x + (r + w / 2) * Math.cos(a).toFloat(), c.y + (r + w / 2) * Math.sin(a).toFloat()), 3.dp.toPx(), StrokeCap.Round)
            }
            drawArc(color, -90f, (fraction.coerceIn(0.0, 1.0) * 360).toFloat(), false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** El panel: gasto de la semana y presión de gasto. Cada cuadro abre su ficha ([onOpen] con su clave). */
@Composable
fun PulseHeader(p: Pulse, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Bg).padding(vertical = 22.dp, horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonitorBox("GASTO DE\nLA SEMANA", Modifier.weight(1f), { onOpen("week") }) {
                val c = zoneColor(p.weekZone)
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text("${p.weekScore.toInt()}", color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(when (p.weekZone) { Zone.GREEN -> "EN RANGO"; Zone.YELLOW -> "ALGO ALTO"; Zone.RED -> "ALTO" }, color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    Text("óptimo hoy: ${p.optimalScore.toInt()}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
            MonitorBox("PRESIÓN\nDE GASTO", Modifier.weight(1f), { onOpen("pressure") }) {
                val c = zoneColor(p.pressureZone)
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(String.format(es, "%.1f", p.pressure), color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(p.pressureLabel, color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    Text("hoy", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }
        Text(p.statement, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, start = 8.dp, end = 8.dp))
    }
}

@Composable
private fun Big(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (unit.isNotEmpty()) Text(unit, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun RingItem(label: String, onClick: () -> Unit, ring: @Composable () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ring()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun MonitorBox(title: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onClick).padding(14.dp)) {
        Row {
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, lineHeight = 17.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Ficha sencilla de un anillo o cuadro: qué mide, tu dato, lo óptimo y qué hacer hoy. */
@Composable
fun PulseInfoSheet(key: String, p: Pulse, onDismiss: () -> Unit) {
    if (key == "week") { WeekVisualSheet(p, onDismiss); return }
    if (key !in setOf("cushion", "margin", "health")) { PressureVisualSheet(p, onDismiss); return }
    val money = LocalMoney.current
    val i = PulseCalculator.info(key, p, money::format, LocalDate.now())
    val c = zoneColor(i.zone)
    FormSheet(i.title, onDismiss, primary = "Entendido", onPrimary = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(i.value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = when (i.zone) { Zone.GREEN -> Color(0xFF0E9E05); Zone.YELLOW -> Color(0xFF8A7500); Zone.RED -> Red })
            }
            Spacer(Modifier.width(14.dp))
            Text(
                when (i.zone) { Zone.GREEN -> "En verde"; Zone.YELLOW -> "En amarillo"; Zone.RED -> "En rojo" },
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink,
            )
        }
        Section("Qué mide", i.what)
        Section("Tu dato hoy", i.now)
        Section("Lo óptimo", i.optimal)
        Section("Qué hacer", i.todo)
    }
}

@Composable
private fun Section(title: String, text: String) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P.muted)
        Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = P.ink)
    }
}

/** Ficha de un círculo: puntuación, la pregunta que responde, qué ha cambiado y la mejora más útil. */
@Composable
fun CircleSheet(c: FinanceCircle, weekAgo: Double?, onDismiss: () -> Unit) {
    val z = zoneOf(c.score)
    val color = when (z) { Zone.GREEN -> Color(0xFF0E9E05); Zone.YELLOW -> Color(0xFF8A7500); Zone.RED -> Red }
    FormSheet(c.title, onDismiss, primary = "Entendido", onPrimary = onDismiss) {
        Text(c.question, fontSize = 15.sp, color = P.muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${c.score.toInt()}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
            Text("/100", fontSize = 18.sp, color = P.muted, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        }
        Text(
            "Confianza de los datos: ${(c.confidence * 100).toInt()} %" +
                if (c.confidence < 0.99) " · sin corregir sería ${c.raw.toInt()}; con pocos datos la nota se acerca a 50 hasta tener 6 meses de historial." else "",
            fontSize = 13.sp, color = P.muted,
        )
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).padding(14.dp)) {
            Column {
                Text("Qué ha cambiado", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P.muted)
                Text(FinancialCircles.changeText(c.score, weekAgo), fontSize = 15.sp, color = P.ink)
            }
        }
        c.factors.forEach { f ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.name} · ${(f.weight * 100).toInt()} %", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink)
                    val s = f.score
                    Text(
                        s?.let { String.format(es, "%.1f", it) } ?: "sin datos", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = when { s == null -> P.muted; zoneOf(s) == Zone.GREEN -> Color(0xFF0E9E05); zoneOf(s) == Zone.YELLOW -> Color(0xFF8A7500); else -> Red },
                    )
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(P.chip)) {
                    f.score?.let { s -> Box(Modifier.fillMaxWidth((s / 100).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(zoneColor(zoneOf(s)))) }
                }
                Text(f.detail, fontSize = 13.sp, color = P.muted)
                if (f.formula.isNotBlank()) Text("Fórmula: ${f.formula}", fontSize = 12.sp, color = P.muted.copy(alpha = 0.8f))
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)).padding(14.dp)) {
            Column {
                Text("Tu siguiente mejora", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P.muted)
                Text(c.nextStep, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink)
            }
        }
    }
}

private fun darkOf(z: Zone) = when (z) { Zone.GREEN -> Color(0xFF0E9E05); Zone.YELLOW -> Color(0xFF8A7500); Zone.RED -> Red }

/** Gasto de la semana, de un vistazo: barra con la marca de hoy y una columna por día. */
@Composable
private fun WeekVisualSheet(p: Pulse, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val perDay = (0..6).map { i -> val d = monday.plusDays(i.toLong()); d to (p.days.firstOrNull { it.date == d }?.spent ?: 0.0) }
    val dailyOpt = p.weekOptimal / 7
    val maxV = maxOf(perDay.maxOf { it.second }, dailyOpt, 1.0) * 1.15
    val zc = zoneColor(p.weekZone)
    val track = P.chip
    val left = p.weekOptimal - p.weekSpent
    FormSheet("Gasto de la semana", onDismiss, primary = "Entendido", onPrimary = onDismiss) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money.format(p.weekSpent), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = darkOf(p.weekZone))
            Text("  de ${money.format(p.weekOptimal)}", fontSize = 16.sp, color = P.muted, modifier = Modifier.padding(bottom = 7.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val w = size.width; val h = size.height * 0.7f; val y = size.height * 0.15f
            drawRoundRect(track, topLeft = Offset(0f, y), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            val f = (p.weekSpent / maxOf(1.0, p.weekOptimal)).toFloat().coerceIn(0f, 1f)
            if (f > 0) drawRoundRect(zc, topLeft = Offset(0f, y), size = Size(w * f, h), cornerRadius = CornerRadius(h / 2))
            val m = (p.weekOptimalToDate / maxOf(1.0, p.weekOptimal)).toFloat().coerceIn(0f, 1f)
            drawRect(Color(0xFF6E6E73), topLeft = Offset(w * m - 3f, 0f), size = Size(6f, size.height))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 12.dp).background(Color(0xFF6E6E73)))
            Text("  Lo que tocaba hasta hoy: ${money.format(p.weekOptimalToDate)}", fontSize = 13.sp, color = P.muted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            perDay.forEach { (d, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (v > 0) v.toInt().toString() else "", fontSize = 11.sp, color = P.muted)
                    val c = when { d.isAfter(today) -> track; v <= dailyOpt -> Green; v <= dailyOpt * 1.5 -> Yellow; else -> Red }
                    Box(Modifier.fillMaxWidth().height((110 * (v / maxV)).coerceAtLeast(4.0).dp).clip(RoundedCornerShape(6.dp)).background(c))
                    Text(
                        listOf("L", "M", "X", "J", "V", "S", "D")[d.dayOfWeek.value - 1], fontSize = 13.sp,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal, color = if (d == today) P.ink else P.muted,
                    )
                }
            }
        }
        Text("Ideal: ~${money.format(dailyOpt)} al día", fontSize = 13.sp, color = P.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(zc.copy(alpha = 0.16f)).padding(14.dp), contentAlignment = Alignment.Center) {
            Text(
                if (left >= 0) "Margen de gasto: ${money.format(left)} hasta el domingo" else "Te has pasado ${money.format(-left)} esta semana",
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = darkOf(p.weekZone),
            )
        }
    }
}

/** Presión de gasto como un medidor: 1,0 = justo lo que tocaba a estas alturas del mes. */
@Composable
private fun PressureVisualSheet(p: Pulse, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val z = p.pressureZone
    FormSheet("Presión de gasto", onDismiss, primary = "Entendido", onPrimary = onDismiss) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(es, "%.1f×", p.pressure), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = darkOf(z))
            Text("  presión ${p.pressureLabel.lowercase()}", fontSize = 18.sp, color = P.muted, modifier = Modifier.padding(bottom = 9.dp))
        }
        val needleColor = P.ink
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val w = size.width; val h = 16f * density / 2; val y = size.height / 2 - h / 2
            fun x(v: Double) = (v / 2.0).toFloat().coerceIn(0f, 1f) * w
            drawRoundRect(Green, topLeft = Offset(0f, y), size = Size(x(1.0), h), cornerRadius = CornerRadius(h / 2))
            drawRect(Yellow, topLeft = Offset(x(1.0), y), size = Size(x(1.3) - x(1.0), h))
            drawRoundRect(Red, topLeft = Offset(x(1.3), y), size = Size(w - x(1.3), h), cornerRadius = CornerRadius(h / 2))
            val nx = x(p.pressure)
            drawRoundRect(needleColor, topLeft = Offset(nx - 4f, 0f), size = Size(8f, size.height), cornerRadius = CornerRadius(4f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0", fontSize = 12.sp, color = P.muted)
            Text("1,0 lo de hoy", Modifier.weight(1f), fontSize = 12.sp, color = P.muted, textAlign = TextAlign.Center)
            Text("2×", fontSize = 12.sp, color = P.muted)
        }
        val top = maxOf(p.spentToday, p.todayAllowance, 1.0)
        listOf("Hoy has gastado" to p.spentToday, "Hoy te toca" to p.todayAllowance).forEachIndexed { i, (label, v) ->
            Column {
                Row {
                    Text(label, Modifier.weight(1f), fontSize = 14.sp, color = P.muted)
                    Text(money.format(v), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                }
                Box(
                    Modifier.padding(top = 4.dp).fillMaxWidth((v / top).toFloat().coerceIn(0.02f, 1f)).height(12.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (i == 0) zoneColor(z) else Color(0xFFB0B0B5)),
                )
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(zoneColor(z).copy(alpha = 0.16f)).padding(14.dp), contentAlignment = Alignment.Center) {
            Text(
                when (z) {
                    Zone.GREEN -> "Hoy aún puedes gastar ${money.format(maxOf(0.0, p.todayAllowance - p.spentToday))}"
                    Zone.YELLOW -> "Hoy vas ${money.format(p.spentToday - p.todayAllowance)} por encima"
                    Zone.RED -> "Hoy vas ${money.format(p.spentToday - p.todayAllowance)} por encima"
                },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = darkOf(z), textAlign = TextAlign.Center,
            )
        }
    }
}
