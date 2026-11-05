package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.BillCalendar
import com.caudal.finanzas.domain.PaycheckSplit
import com.caudal.finanzas.domain.RealPrice
import com.caudal.finanzas.domain.Recoverable
import com.caudal.finanzas.domain.Recurring
import com.caudal.finanzas.domain.Trend
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.Slice
import com.caudal.finanzas.ui.StackedBar

private val Orange = Color(0xFFFF9F0A)

@Composable
private fun Header(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, color, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text(sub, fontSize = 14.sp, color = P.muted)
        }
    }
}

/** Suscripciones, recibos, cuotas y comisiones que se repiten, con su coste al año. */
@Composable
fun RecurringCard(list: List<Recurring>) {
    val money = LocalMoney.current
    var all by remember { mutableStateOf(false) }
    MCard {
        Header(Icons.Filled.Repeat, SeriesColors[3], "Gastos recurrentes", "Lo que se repite cada mes")
        if (list.isEmpty()) { Text("Aún no veo pagos que se repitan cada mes.", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp)); return@MCard }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SeriesColors[3].copy(alpha = 0.10f)).padding(14.dp)) {
            Text("${money.format(list.sumOf { it.monthly })} al mes · ${money.format(list.sumOf { it.annual })} al año", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        }
        list.take(if (all) 30 else 6).forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = P.line, thickness = 0.6.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
                    Text(
                        r.kind + (if (!r.fromBills) " · ${r.times} pagos" else "") +
                            if (r.change >= 1) " · ha subido ${money.format(r.change)}" else if (r.change <= -1) " · ha bajado ${money.format(-r.change)}" else "",
                        fontSize = 13.sp, color = if (r.change >= 1) Orange else P.muted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money.format(r.monthly) + "/mes", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink)
                    Text(money.format(r.annual) + "/año", fontSize = 12.sp, color = P.muted)
                }
            }
        }
        if (list.size > 6) Text(if (all) "Ver menos" else "Ver los ${list.size}", fontSize = 15.sp, color = P.gain, modifier = Modifier.clip(CircleShape).clickable { all = !all }.padding(6.dp))
    }
}

/** Dinero que quizá puedas recuperar: importe, evidencia y siguiente paso. */
@Composable
fun RecoverableCard(list: List<Recoverable>, onOpen: (Recoverable) -> Unit) {
    val money = LocalMoney.current
    MCard {
        Header(Icons.Filled.Replay, P.gain, "Dinero recuperable", "Situaciones concretas para revisar")
        if (list.isEmpty()) { Text("Nada que reclamar ni revisar ahora mismo.", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp)); return@MCard }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.gain.copy(alpha = 0.10f)).padding(14.dp)) {
            Text("Hasta ${money.format(list.sumOf { it.amount })} en juego", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        }
        list.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = P.line, thickness = 0.6.dp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = r.expense != null) { onOpen(r) }.padding(vertical = 10.dp)) {
                Row {
                    Text(r.title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                    Text(money.format(r.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.gain)
                }
                Text(r.evidence, fontSize = 13.sp, color = P.muted)
                Text("→ ${r.nextStep}", fontSize = 13.sp, color = P.ink, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text("Que algo sea poco habitual no significa que esté mal cobrado: compruébalo antes de reclamar.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Recibos de las próximas semanas y cómo aliviar las semanas apretadas. */
@Composable
fun BillCalendarCard(c: BillCalendar) {
    val money = LocalMoney.current
    MCard {
        Header(Icons.Filled.CalendarMonth, if (c.crunch) Orange else SeriesColors[1], "Tus recibos, semana a semana", if (c.crunch) "Hay semanas apretadas" else "Bien repartidos")
        Spacer(Modifier.height(12.dp))
        val max = (c.weeks.maxOfOrNull { it.second } ?: 0.0).takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            c.weeks.forEach { (_, v) ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (v > 0) Text(money.format(v).substringBefore(","), fontSize = 11.sp, color = P.muted)
                    Box(Modifier.fillMaxWidth().fillMaxHeight((v / max).toFloat().coerceIn(0.04f, 1f) * 0.7f).clip(RoundedCornerShape(6.dp)).background(if (v / max > 0.6 && c.crunch) Orange else SeriesColors[1]))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            c.weeks.forEach { (label, _) -> Text(label.substringBefore("–"), Modifier.weight(1f), fontSize = 10.sp, color = P.muted, maxLines = 1) }
        }
        if (c.beforePayday.isNotEmpty() && c.payday != null) {
            Text("Antes de cobrar (${c.payday.dayOfMonth}): " + c.beforePayday.joinToString(", ") { "${it.first} ${money.format(it.second)}" }, fontSize = 13.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp))
        }
        c.suggestions.forEach { Text("• $it", fontSize = 14.sp, color = P.ink, modifier = Modifier.padding(top = 6.dp)) }
        if (c.crunch) Text("No cambiamos nada por ti: son sugerencias para que lo pidas tú si te conviene.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * Cómo repartir la próxima nómina. Primero lo que no se puede evitar (recibos hasta la siguiente
 * nómina y lo básico); después tu seguridad (colchón) y tus objetivos; lo que sobra es libre.
 * Colchón y objetivos se ajustan con las barras y el dinero libre se recalcula al momento.
 */
@Composable
fun PaycheckSplitCard(s: PaycheckSplit) {
    val money = LocalMoney.current
    val colors = listOf(Color(0xFF5E5CE6), SeriesColors[0], SeriesColors[1], SeriesColors[3], P.gain)
    val fixedPart = s.parts[0].second + s.parts[1].second
    val room = maxOf(0.0, s.income - fixedPart)
    var cushion by remember(s) { mutableStateOf(s.parts[2].second.toFloat()) }
    var goals by remember(s) { mutableStateOf(s.parts[3].second.toFloat()) }
    var showBills by remember { mutableStateOf(false) }
    val free = s.income - fixedPart - cushion - goals
    val parts = listOf(s.parts[0].first to s.parts[0].second, s.parts[1].first to s.parts[1].second, s.parts[2].first to cushion.toDouble(), s.parts[3].first to goals.toDouble(), "Dinero libre" to free)
    MCard {
        Header(Icons.Filled.Payments, P.gain, "Reparte tu nómina", "${s.label} · ${money.format(s.income)}")
        Text(
            "Cuando te llegue, así te propongo repartirla: primero lo que no puedes evitar (los recibos que salen antes de la siguiente nómina y lo básico para vivir), " +
                "luego tu colchón y tus objetivos. Lo que queda es dinero libre para lo que quieras. Mueve las barras para probar otros repartos.",
            fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
        StackedBar(parts.mapIndexed { i, (n, v) -> Slice(n, maxOf(0.0, v), colors[i % colors.size]) }, height = 12.dp)
        Spacer(Modifier.height(8.dp))
        parts.forEachIndexed { i, (n, v) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = i == 0 && s.bills.isNotEmpty()) { showBills = !showBills }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                Spacer(Modifier.width(10.dp))
                Text(n + if (i == 0 && s.bills.isNotEmpty()) (if (showBills) " ▴" else " ▾") else "", Modifier.weight(1f), fontSize = 15.sp, color = P.ink)
                Text(money.format(v), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (v < 0) P.loss else P.ink)
            }
            if (i == 0 && showBills) s.bills.forEach { (bn, bv) ->
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp)) {
                    Text(bn, Modifier.weight(1f), fontSize = 13.sp, color = P.muted)
                    Text(money.format(bv), fontSize = 13.sp, color = P.muted)
                }
            }
            if (i == 2) androidx.compose.material3.Slider(
                cushion, { cushion = (Math.round(it / 5f) * 5f).coerceAtMost((room - goals).toFloat().coerceAtLeast(0f)) }, valueRange = 0f..room.toFloat().coerceAtLeast(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = colors[2], activeTrackColor = colors[2], inactiveTrackColor = P.chip),
            )
            if (i == 3) androidx.compose.material3.Slider(
                goals, { goals = (Math.round(it / 5f) * 5f).coerceAtMost((room - cushion).toFloat().coerceAtLeast(0f)) }, valueRange = 0f..room.toFloat().coerceAtLeast(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = colors[3], activeTrackColor = colors[3], inactiveTrackColor = P.chip),
            )
        }
        Text(
            if (free < 0) "Así no te llega: baja el colchón o los objetivos." else "Te quedan ${money.format(free)} libres: unos ${money.format(free / 30)} al día hasta la siguiente nómina.",
            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (free < 0) P.loss else P.gain, modifier = Modifier.padding(top = 6.dp),
        )
        s.notes.forEach { Text("• $it", fontSize = 13.sp, color = P.muted, modifier = Modifier.padding(top = 4.dp)) }
        Text("Es una propuesta: tú decides qué mover y cuándo.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Cambios que se mantienen en el tiempo, con lo que los explica. */
@Composable
fun TrendsCard(list: List<Trend>) {
    MCard {
        Header(Icons.Filled.ShowChart, Orange, "Cambios en tu economía", "Tendencias que se mantienen, no días sueltos")
        if (list.isEmpty()) Text("No veo cambios persistentes: tu forma de gastar y cobrar se mantiene.", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp))
        list.forEach { t ->
            Column(Modifier.padding(top = 12.dp)) {
                Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                Text(t.explanation, fontSize = 14.sp, color = P.muted)
            }
        }
    }
}

/** El precio real de una compra según tus circunstancias. */
@Composable
fun RealPriceCard(r: RealPrice, goalName: String?) {
    val money = LocalMoney.current
    MCard {
        Text("El precio real", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        Spacer(Modifier.height(10.dp))
        Fact("Días de trabajo", "${String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", r.incomeDays)} días de tus ingresos netos")
        if (r.goalDelayDays != null && goalName != null) Fact("Tu objetivo", "\"$goalName\" llegaría ${r.goalDelayDays} días más tarde")
        if (r.cushionMonthsBefore != null && r.cushionMonthsAfter != null) Fact(
            "Colchón de emergencia",
            "de ${String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", r.cushionMonthsBefore)} a ${String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", r.cushionMonthsAfter)} meses de gastos",
        )
        Spacer(Modifier.height(10.dp))
        Text("Tu ahorro en los próximos meses", fontSize = 13.sp, color = P.muted)
        r.months.forEach { (m, without, with) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(m, Modifier.width(56.dp), fontSize = 14.sp, color = P.muted)
                Text("sin comprar ${money.format(without)}", Modifier.weight(1f), fontSize = 14.sp, color = P.ink)
                Text("comprando ${money.format(with)}", fontSize = 14.sp, color = if (with < 0) P.loss else P.ink)
            }
        }
        Text(r.waitNote, fontSize = 14.sp, color = P.ink, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.width(140.dp), fontSize = 14.sp, color = P.muted)
        Text(value, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = P.ink)
    }
}
