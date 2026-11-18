package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.CashPlan
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.SerifDisplay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

private val ES = Locale.forLanguageTag("es-ES")
private fun short(d: java.time.LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMM", ES)).replace(".", "")

// Paleta de la referencia (la tarjeta lleva su propio fondo, igual en claro y oscuro).
private val Peach = Color(0xFFDCEBD3)
private val PeachSoft = Color(0xFFEAF2E4)
private val PeachZone = Color(0xFFFBE3E8)
private val Paper = Color(0xFFF6F1E6)
private val Ink = Color(0xFF17150F)
private val Muted = Color(0xFF7C7566)
private val Grid = Color(0xFFEDE6D6)
private val Blue = Color(0xFF2E6B3F)
private val BlueBand = Color(0xFFDCEBD3)
private val Orange = Color(0xFFC2415C)
private val Green = Color(0xFF2E6B3F)
private val Red = Color(0xFFC2415C)
private val Mint = Color(0xFFDCEBD3)

/**
 * Tu dinero hasta la nómina, calcado de la referencia: saldo previsto, curva con la previsión de puntos y su banda,
 * la zona justa sombreada con su mínimo, la nómina marcada, cuatro tramos y el aviso del momento justo. Sin iconos del tiempo.
 */
@Composable
fun CashPlanCard(c: CashPlan, onCushion: (Double) -> Unit, onMoveFromSavings: (() -> Unit)? = null) {
    val money = LocalMoney.current
    fun eur(v: Double) = money.format(v).substringBefore(',').let { if (it.endsWith("€")) it else "$it €" }
    val all = c.series.map { it.second }.ifEmpty { listOf(c.balance) }

    // Desplegable: hasta la nómina, los próximos 7 días o hasta fin de mes.
    val monthEnd = c.today.withDayOfMonth(c.today.lengthOfMonth())
    val options = buildList {
        add("Hasta tu ${c.paydayName.lowercase()}" to all.size)
        if (all.size > 8) add("Próximos 7 días" to 8)
        val toMonthEnd = ChronoUnit.DAYS.between(c.today, monthEnd).toInt() + 1
        if (toMonthEnd in 2 until all.size) add("Hasta fin de mes" to toMonthEnd)
    }
    var pick by rememberSaveable { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    val (rangeLabel, len) = options[pick.coerceIn(0, options.size - 1)]
    val values = all.take(len)
    val lastDay = c.today.plusDays((values.size - 1).toLong())

    val minIdx = values.indices.minByOrNull { values[it] } ?: 0
    val minValue = values[minIdx]
    val endValue = values.last()
    val tightDay = c.today.plusDays(minIdx.toLong())
    val tight = c.perDayBefore < 0 || minValue < c.cushion + 25
    val tightCause = c.events.filter { it.amount < 0 && !it.date.isAfter(tightDay) }.maxByOrNull { -it.amount }
    val payIdx = ChronoUnit.DAYS.between(c.today, c.payday).toInt().takeIf { it in values.indices }
    val shownEvents = c.events.filter { !it.date.isBefore(c.today) && !it.date.isAfter(lastDay) }
    val need = (ceil((c.cushion + 25 - minValue).coerceAtLeast(10.0) / 10) * 10).toInt()
    fun perDay(d: java.time.LocalDate): Double {
        val after = c.perDayAfter
        val split = c.splitDate
        return maxOf(0.0, if (split != null && after != null && !d.isBefore(split)) after else c.perDayBefore)
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(0f to Peach, 0.35f to PeachSoft, 1f to Paper))
            .padding(16.dp),
    ) {
        Box {
            Row(
                Modifier.clip(CircleShape).background(Color(0xFFFFFDF8).copy(alpha = 0.6f)).clickable { open = true }
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rangeLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.KeyboardArrowDown, "Cambiar periodo", Modifier.size(16.dp), tint = Ink)
            }
            DropdownMenu(open, { open = false }, containerColor = Color(0xFFFFFDF8), shape = RoundedCornerShape(16.dp)) {
                options.forEachIndexed { i, (label, _) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 14.sp, fontWeight = if (i == pick) FontWeight.SemiBold else FontWeight.Normal, color = Ink) },
                        onClick = { pick = i; open = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---------- Tarjeta blanca: tu dinero en el banco, día a día ----------
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFFDF8)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Tu dinero en el banco", fontSize = 12.sp, color = Muted)
                    Text("${eur(c.balance)} → ${eur(endValue)}", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = Ink)
                    Text("hoy → ${short(lastDay)}", fontSize = 12.sp, color = Muted)
                }
                Row(Modifier.clip(CircleShape).background(Mint).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
                    Spacer(Modifier.width(4.dp))
                    Text("${eur(perDay(c.today))} al día", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Green)
                }
            }
            Spacer(Modifier.height(16.dp))

            val minV = minOf(values.minOrNull() ?: 0.0, 0.0)
            val maxV = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0) * 1.15
            val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
            val chartH = 160.dp
            BoxWithConstraints(Modifier.fillMaxWidth().height(chartH)) {
                val w = maxWidth
                fun fx(i: Int) = 0.04f + 0.92f * i / (values.size - 1).coerceAtLeast(1)
                fun fy(v: Double) = (0.18 + 0.72 * (1 - (v - minV) / range)).toFloat()
                Canvas(Modifier.fillMaxSize()) {
                    fun x(i: Int) = size.width * fx(i)
                    fun y(v: Double) = size.height * fy(v)
                    (1..3).forEach { k -> drawLine(Grid, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), 1.dp.toPx()) }
                    // El colchón: la raya por la que no quieres bajar.
                    drawLine(Orange.copy(alpha = 0.6f), Offset(0f, y(c.cushion)), Offset(size.width, y(c.cushion)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    payIdx?.let { drawLine(Muted.copy(alpha = 0.5f), Offset(x(it), 0f), Offset(x(it), size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))) }
                    if (values.size >= 2) {
                        val path = Path().apply {
                            moveTo(x(0), y(values[0]))
                            for (i in 1 until values.size) {
                                val mid = (x(i - 1) + x(i)) / 2
                                cubicTo(mid, y(values[i - 1]), mid, y(values[i]), x(i), y(values[i]))
                            }
                        }
                        drawPath(path, BlueBand, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(path, Blue, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                    // Cobros (verde) y recibos (rojo) sobre la línea.
                    shownEvents.forEach { e ->
                        val i = ChronoUnit.DAYS.between(c.today, e.date).toInt().coerceIn(0, values.size - 1)
                        drawCircle(Color(0xFFFFFDF8), 6.dp.toPx(), Offset(x(i), y(values[i])))
                        drawCircle(if (e.amount > 0) Green else Red, 4.dp.toPx(), Offset(x(i), y(values[i])))
                    }
                    drawCircle(Color(0xFFFFFDF8), 7.dp.toPx(), Offset(x(0), y(values[0])))
                    drawCircle(Blue, 7.dp.toPx(), Offset(x(0), y(values[0])), style = Stroke(2.5.dp.toPx()))
                    if (minIdx > 0) {
                        drawCircle(Color(0xFFFFFDF8), 7.dp.toPx(), Offset(x(minIdx), y(minValue)))
                        drawCircle(Orange, 5.dp.toPx(), Offset(x(minIdx), y(minValue)))
                    }
                }
                if (minIdx > 0) Floating(w * fx(minIdx), chartH * fy(minValue) - 40.dp, w, width = 168.dp) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Ink).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Lo más bajo: ${eur(minValue)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFFDF8), maxLines = 1)
                    }
                }
            }
            // Eje: hoy, el día más bajo (naranja), la nómina y el último día.
            BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp).padding(top = 8.dp)) {
                val w = maxWidth
                fun fx(i: Int) = 0.04f + 0.92f * i / (values.size - 1).coerceAtLeast(1)
                val labels = buildList {
                    add(Triple(fx(0), "Hoy", Muted))
                    if (minIdx > 0) add(Triple(fx(minIdx), short(tightDay), Orange))
                    payIdx?.let { if (it > 0 && it != minIdx) add(Triple(fx(it), c.paydayName.replaceFirstChar { ch -> ch.uppercase() }, Muted)) }
                    if (values.size - 1 != minIdx && values.size - 1 != payIdx) add(Triple(fx(values.size - 1), short(lastDay), Muted))
                }
                var last = -1f
                labels.sortedBy { it.first }.forEach { (f, t, col) ->
                    if (last < 0 || (f - last) * w.value >= 64) {
                        Floating(w * f, 0.dp, w, width = 64.dp) { Text(t, Modifier.fillMaxWidth(), fontSize = 12.sp, color = col, textAlign = TextAlign.Center, maxLines = 1) }
                        last = f
                    }
                }
            }
            // Leyenda: qué es cada cosa.
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(Blue, "Tu saldo"); LegendDot(Green, "Cobros"); LegendDot(Red, "Recibos"); LegendDash(Orange, "Colchón")
            }
            Text(
                "Lo que tendrás cada día si gastas ${eur(perDay(c.today))} al día. Baja con tus gastos y recibos, y sube cuando cobras.",
                fontSize = 12.sp, lineHeight = 16.sp, color = Muted, modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---------- Cuánto puedes gastar cada semana ----------
        Spacer(Modifier.height(16.dp))
        Text("Para gastar cada semana", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        val weeks = values.indices.toList().chunked(7).take(4).let { w ->
            // Un último trozo de 1–2 días se junta con la semana anterior.
            if (w.size >= 2 && w.last().size < 3) w.dropLast(2) + listOf(w[w.size - 2] + w.last()) else w
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weeks.forEachIndexed { k, idx ->
                val from = c.today.plusDays(idx.first().toLong())
                val to = c.today.plusDays(idx.last().toLong())
                val budget = idx.sumOf { perDay(c.today.plusDays(it.toLong())) }
                val ev = shownEvents.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.maxByOrNull { kotlin.math.abs(it.amount) }
                val hot = tight && minIdx in idx
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (hot) PeachZone else Color(0xFFFFFDF8)).padding(vertical = 16.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (k == 0) "Esta semana" else if (from == to) short(to) else if (from.month == to.month) "${from.dayOfMonth}–${short(to)}" else "${short(from)}–${short(to)}", fontSize = 12.sp, color = if (hot) Orange else Muted, maxLines = 1)
                    Text(eur(budget), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (hot) Orange else Ink, modifier = Modifier.padding(vertical = 8.dp), maxLines = 1)
                    Text(
                        ev?.let { (if (it.amount > 0) "+ " else "") + it.label } ?: "${idx.size} ${if (idx.size == 1) "día" else "días"}",
                        fontSize = 12.sp, color = when { ev == null -> Muted; ev.amount > 0 -> Green; else -> Red }, maxLines = 1,
                    )
                }
            }
        }

        // ---------- El aviso del momento justo ----------
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFFDF8)).padding(24.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (tight) PeachZone else Mint).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(16.dp), tint = if (tight) Orange else Green)
                Spacer(Modifier.width(4.dp))
                Text(if (tight) "${short(tightDay)} · Vas justo" else "Vas bien", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (tight) Orange else Green)
            }
            Text(
                when {
                    c.perDayBefore < 0 -> "No llegas con ${eur(c.cushion)} de colchón: te faltarían unos ${eur(c.shortfall)} antes de cobrar."
                    tightCause != null -> "${tightCause.label} llega antes de la ${c.paydayName.lowercase()}. Tu saldo podría bajar a ${eur(minValue)}."
                    else -> "Gastando lo de cada día, tu punto más bajo será ${eur(minValue)} el ${short(tightDay)}."
                },
                fontSize = 22.sp, lineHeight = 28.sp, fontFamily = SerifDisplay, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(top = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tight && onMoveFromSavings != null) {
                    Box(Modifier.weight(1.5f).clip(CircleShape).background(Ink).clickable(onClick = onMoveFromSavings).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text("Pasar $need € del ahorro", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFFDF8), maxLines = 1)
                    }
                }
                // Colchón: 50 → 100 → 150 → 50…
                val next = when { c.cushion < 75 -> 100.0; c.cushion < 125 -> 150.0; else -> 50.0 }
                Box(Modifier.weight(1f).clip(CircleShape).background(Grid).clickable { onCushion(next) }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("Colchón ${eur(c.cushion)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun LegendDash(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 16.dp, height = 2.dp).background(color.copy(alpha = 0.6f)))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = Muted)
    }
}

/** Coloca una etiqueta centrada en x (sin salirse por los lados). */
@Composable
private fun Floating(x: Dp, y: Dp, total: Dp, width: Dp = 96.dp, content: @Composable () -> Unit) {
    val left = (x - width / 2).coerceIn(0.dp, (total - width).coerceAtLeast(0.dp))
    Box(Modifier.offset(x = left, y = y.coerceAtLeast(0.dp)).width(width), contentAlignment = Alignment.Center) { content() }
}
