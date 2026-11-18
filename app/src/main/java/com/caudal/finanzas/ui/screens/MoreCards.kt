package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.GoalStatus
import com.caudal.finanzas.domain.SubscriptionCheck
import com.caudal.finanzas.domain.TypicalMonth
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MerchantLogo
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.SerifDisplay

private val Amber = Color(0xFF2E6B3F)
private val Blue = Color(0xFF2E6B3F)
private val Orange = Color(0xFFC2415C)

/** Este mes frente a tu mes típico: la línea gris es lo que sueles llevar gastado a cada día del mes. */
@Composable
fun TypicalMonthCard(t: TypicalMonth) {
    val money = LocalMoney.current
    val ink = P.ink
    val grey = P.muted
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(P.card).padding(24.dp)) {
        Text("Este mes frente a tu mes típico", fontSize = 24.sp, lineHeight = 28.sp, fontFamily = SerifDisplay, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = P.ink)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Amber, "Este mes", dashed = false)
            Legend(grey, "Tu mes típico", dashed = true)
        }
        val maxV = maxOf(t.typical.maxOrNull() ?: 0.0, t.now.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
        val len = t.typical.size
        Canvas(Modifier.fillMaxWidth().height(176.dp).padding(top = 16.dp)) {
            fun x(i: Int) = size.width * i / (len - 1).coerceAtLeast(1)
            fun y(v: Double) = (size.height * (1 - v / (maxV * 1.08))).toFloat()
            (1..3).forEach { k -> drawLine(grey.copy(alpha = 0.15f), Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), 1.dp.toPx()) }
            fun curve(v: List<Double>) = Path().apply {
                moveTo(x(0), y(v[0]))
                for (i in 1 until v.size) { val mid = (x(i - 1) + x(i)) / 2; cubicTo(mid, y(v[i - 1]), mid, y(v[i]), x(i), y(v[i])) }
            }
            if (t.typical.size >= 2) drawPath(curve(t.typical), grey, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            if (t.now.size >= 2) drawPath(curve(t.now), Amber, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            t.now.lastOrNull()?.let { drawCircle(Amber, 6.dp.toPx(), Offset(x(t.now.size - 1), y(it))); drawCircle(ink, 2.dp.toPx(), Offset(x(t.now.size - 1), y(it))) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("1", Modifier.weight(1f), fontSize = 12.sp, color = P.muted)
            Text("Hoy, ${t.today}", fontSize = 12.sp, color = P.muted)
            Spacer(Modifier.weight(1f))
            Text("$len", fontSize = 12.sp, color = P.muted)
        }
        val above = t.diff > 0
        Text(
            if (kotlin.math.abs(t.diff) < 5) "Vas justo como sueles: ${money.format(t.spent)} a día ${t.today}."
            else "Llevas ${money.format(t.spent)}: ${money.format(kotlin.math.abs(t.diff))} ${if (above) "más" else "menos"} que en tu mes típico a estas alturas.",
            fontSize = 16.sp, color = if (above) P.loss else P.gain, modifier = Modifier.padding(top = 16.dp),
        )
        Text("Un mes típico acaba en unos ${money.format(t.typicalTotal)} (media de ${t.monthsUsed} ${if (t.monthsUsed == 1) "mes" else "meses"}, sin alquiler ni fijos).", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Legend(c: Color, text: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) Box(Modifier.size(16.dp).clip(CircleShape).border(2.dp, c, CircleShape)) else Box(Modifier.size(16.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = P.muted)
    }
}

/** "Mis ahorros": cada objetivo con lo que llevas y su anillo de %. */
@Composable
fun GoalsRingList(goals: List<GoalStatus>, onOpen: (Int) -> Unit) {
    val money = LocalMoney.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mis ahorros", fontSize = 28.sp, fontFamily = SerifDisplay, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = P.ink, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        goals.forEachIndexed { i, g ->
            val frac = if (g.goal.target > 0) (g.current / g.goal.target).coerceIn(0.0, 1.0) else 0.0
            val ringTrack = P.chip
            val ink = P.ink
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).clickable { onOpen(i) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(P.chip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Flag, null, Modifier.size(24.dp), tint = P.ink)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.goal.name.ifBlank { "Objetivo" }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money.format(g.current), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                        Text(" de ${money.format(g.goal.target)}", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(ringTrack, -90f, 360f, false, style = Stroke(4.dp.toPx()))
                        drawArc(Amber, -90f, (360 * frac).toFloat(), false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Text("${(frac * 100).toInt()} %", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
                }
            }
        }
    }
}

/** Fila "Estado [Bueno] (i)" para la edad financiera. */
@Composable
fun StatusRow(label: String, status: String, good: Boolean, onInfo: () -> Unit, dark: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (dark) Color.White.copy(alpha = 0.10f) else P.chip).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (dark) Color.White else P.ink)
        Box(Modifier.clip(CircleShape).background(if (dark) Color.White else P.card).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(status, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (good) Color(0xFF2E6B3F) else Orange)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Info, "Qué significa", Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onInfo), tint = if (dark) Color.White.copy(alpha = 0.7f) else P.muted)
    }
}

/** Tarjeta de Inicio: "Revisión de suscripciones", con los logos apilados y lo que podrías ahorrar. */
@Composable
fun SubscriptionCheckCard(c: SubscriptionCheck, pending: Int, onOpen: () -> Unit) {
    val money = LocalMoney.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(P.card).clickable(onClick = onOpen).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(P.chip), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.WorkspacePremium, null, Modifier.size(24.dp), tint = Blue)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Revisión de suscripciones", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = P.ink)
                Text("Repasa tus planes antes de que se renueven", fontSize = 14.sp, color = P.muted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (pending == 0) "Todo revisado" else "$pending ${if (pending == 1) "suscripción necesita" else "suscripciones necesitan"} atención", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                if (c.maxSaving >= 1) Row {
                    Text("Podrías ahorrar hasta ", fontSize = 14.sp, color = P.muted)
                    Text("${money.format(c.maxSaving)}/mes", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Blue)
                }
            }
            // Logos apilados.
            Box(Modifier.size(width = 96.dp, height = 56.dp)) {
                c.subs.take(3).forEachIndexed { i, s ->
                    Box(Modifier.offset(x = (i * 24).dp, y = if (i == 1) 0.dp else 12.dp).size(40.dp).clip(CircleShape).background(Color.White).border(2.dp, P.card, CircleShape), contentAlignment = Alignment.Center) {
                        MerchantLogo(s.name, com.caudal.finanzas.domain.SpendTag.SUBSCRIPTIONS, 36.dp)
                    }
                }
                if (c.subs.size > 3) Box(Modifier.offset(x = 64.dp, y = 0.dp).size(24.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) {
                    Text("+${c.subs.size - 3}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** Detalle: progreso de la revisión, salud del ahorro, acciones a marcar y estado/prioridad. */
@Composable
fun SubscriptionSheet(c: SubscriptionCheck, reviewed: Set<String>, savingsHealth: Int, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val done = c.actions.count { it.key in reviewed }
    FormSheet("Tus suscripciones", onDismiss, primary = "Hecho", onPrimary = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Segments("$done de ${c.actions.size} revisadas", if (c.actions.isEmpty()) 1.0 else done.toDouble() / c.actions.size, Blue, Modifier.weight(1f))
            Segments("Salud del ahorro", savingsHealth / 100.0, Orange, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(vertical = 8.dp)) {
            if (c.actions.isEmpty()) Text("No veo nada que revisar ahora mismo.", fontSize = 16.sp, color = P.muted, modifier = Modifier.padding(16.dp))
            c.actions.forEach { a ->
                val ok = a.key in reviewed
                Row(Modifier.fillMaxWidth().clickable { onToggle(a.key) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MerchantLogo(a.name, com.caudal.finanzas.domain.SpendTag.SUBSCRIPTIONS, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(a.text, Modifier.weight(1f), fontSize = 16.sp, color = if (ok) P.muted else P.ink)
                    Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (ok) "Revisada" else "Sin revisar", Modifier.size(24.dp), tint = if (ok) Color(0xFF2E6B3F) else P.muted)
                }
            }
        }
        val urgent = c.actions.any { it.key.startsWith("up-") && it.key !in reviewed }
        StatusLine("Estado", if (done == c.actions.size) "Hecho" else if (done == 0) "Pendiente" else "En curso", if (done == c.actions.size) Color(0xFF2E6B3F) else Color(0xFFB7791F))
        StatusLine("Prioridad", if (urgent) "Urgente" else if (done < c.actions.size) "Media" else "Baja", if (urgent) Color(0xFFC2415C) else P.muted)
        Text("Pagas ${money.format(c.monthlyTotal)} al mes en ${c.subs.size} suscripciones. Toca una acción para marcarla como revisada.", fontSize = 12.sp, color = P.muted)
    }
}

@Composable
private fun Segments(label: String, frac: Double, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 14.sp, color = P.muted, maxLines = 1)
            Text("${(frac * 100).toInt()} %", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        }
        Row(Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val n = 24
            repeat(n) { i -> Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(1.dp)).background(if (i < (frac * n).toInt()) color else P.chip)) }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = P.muted)
        Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

/** "Próximos recibos": lo que te van a cobrar en los próximos días y si el banco lo cubre. */
@Composable
fun UpcomingBillsCard(bills: List<com.caudal.finanzas.domain.UpcomingBill>, bank: Double, today: java.time.LocalDate) {
    val money = LocalMoney.current
    val total = bills.sumOf { it.amount }
    val ok = bank >= total
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Próximos recibos", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Box(Modifier.clip(CircleShape).background((if (ok) Color(0xFF2E6B3F) else Color(0xFFC2415C)).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (ok) "Cubierto" else "Falta ${money.format(total - bank)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (ok) Color(0xFF2E6B3F) else Color(0xFFC2415C))
            }
        }
        bills.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MerchantLogo(b.name, com.caudal.finanzas.domain.SpendTag.HOME, 40.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
                    Text(b.whenText(today).replaceFirstChar { it.uppercase() }, fontSize = 14.sp, color = P.muted)
                }
                Text("−${money.format(b.amount)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            }
        }
    }
}
