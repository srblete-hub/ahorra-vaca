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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.Challenge
import com.caudal.finanzas.domain.ChallengeStatus
import com.caudal.finanzas.domain.MonthCompare
import com.caudal.finanzas.domain.Trips
import com.caudal.finanzas.ui.BigAmountInput
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.TextInput
import com.caudal.finanzas.ui.Vaca
import com.caudal.finanzas.ui.color
import com.caudal.finanzas.ui.currencySymbol
import com.caudal.finanzas.ui.icon
import com.caudal.finanzas.ui.toAmount
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(P.card).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
}

@Composable
private fun Header(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, tint: Color, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = tint) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = P.ink)
            Text(sub, fontSize = 14.sp, color = P.muted)
        }
    }
}

@Composable
private fun Pill(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (primary) Vaca.Prado else P.chip).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Vaca.Leche else P.ink) }
}

/** Barra de progreso en segmentos (uno por día del reto o un 4 % del presupuesto). */
@Composable
private fun SegmentBar(filled: Int, total: Int, color: Color) {
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i -> Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(if (i < filled) color else P.chip)) }
    }
}

// ---------- Modo viaje ----------

@Composable
fun TripCard(s: Trips.Status, onEnd: () -> Unit) {
    val money = LocalMoney.current
    val over = s.left < 0
    CardBox {
        Header(Icons.Filled.Flight, Vaca.Pasto, Vaca.Prado, s.trip.name, "Modo viaje · ${if (s.daysLeft == 1) "último día" else "quedan ${s.daysLeft} días"}")
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(money.format(kotlin.math.abs(s.left)), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = if (over) Vaca.UbreFuerte else P.ink)
                Text(if (over) " por encima" else " te quedan", Modifier.padding(bottom = 4.dp), fontSize = 16.sp, color = P.muted)
            }
            Text("Llevas ${money.format(s.spent)} de ${money.format(s.trip.budget)}" + if (!over && s.daysLeft > 0) " · ${money.format(s.perDayLeft)} al día" else "", fontSize = 14.sp, color = P.muted)
        }
        SegmentBar(((s.spent / s.trip.budget.coerceAtLeast(1.0)) * 25).toInt().coerceIn(0, 25), 25, if (over) Vaca.Ubre else Vaca.Prado)
        Text("Lo que gastes estos días va aparte: no cuenta en tu media ni en lo que te queda cada día.", fontSize = 12.sp, color = P.muted)
        Row { Spacer(Modifier.weight(1f)); Pill("Terminar viaje", false, onEnd) }
    }
}

@Composable
fun TripSheet(currency: String, onStart: (String, Double, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("4") }
    val value = amount.toAmount()
    val dayNum = days.toIntOrNull()?.takeIf { it in 1..90 }
    FormSheet("Modo viaje", onDismiss, primary = "Empezar viaje", onPrimary = { onStart(name, value!!, dayNum!!) }, primaryEnabled = value != null && value > 0 && dayNum != null) {
        Text("Pon un presupuesto para el viaje. Lo que gastes estos días irá aparte y no te estropeará la media del mes.", fontSize = 14.sp, color = P.muted)
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        TextInput(name, { name = it }, "A dónde (Lisboa, el pueblo…)")
        TextInput(days, { days = it.filter(Char::isDigit).take(2) }, "Cuántos días, contando hoy", keyboardType = KeyboardType.Number)
        if (value != null && value > 0 && dayNum != null) Text("Unos ${LocalMoney.current.format(value / dayNum)} al día.", fontSize = 14.sp, color = P.ink)
    }
}

// ---------- Retos de la vaca ----------

@Composable
fun ChallengeCard(active: ChallengeStatus?, suggestions: List<Challenge>, litres: Int, onStart: (Challenge) -> Unit, onDrop: () -> Unit) {
    CardBox {
        Header(
            Icons.Filled.LocalDrink, Vaca.Pasto, Vaca.Prado, "Retos de la vaca",
            if (litres > 0) "La vaca te ha dado $litres ${if (litres == 1) "litro" else "litros"} de leche" else "Cumple uno y la vaca te da leche",
        )
        if (active != null) {
            val c = active.challenge
            Text(c.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            if (active.failedOn != null) {
                val dow = active.failedOn.dayOfWeek.getDisplayName(TextStyle.FULL, ES)
                Text("Se rompió el $dow ${active.failedOn.dayOfMonth}. No pasa nada: vuelve a intentarlo.", fontSize = 14.sp, color = Vaca.UbreFuerte)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Volver a empezar", true) { onStart(c) }
                    Pill("Elegir otro", false, onDrop)
                }
            } else {
                Text("Día ${active.day} de ${c.days} · ${c.hint}", fontSize = 14.sp, color = P.muted)
                SegmentBar(active.day - 1, c.days, Vaca.Prado)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Al acabar: ${c.litres} ${if (c.litres == 1) "litro" else "litros"} de leche", Modifier.weight(1f), fontSize = 14.sp, color = Vaca.Prado, fontWeight = FontWeight.Medium)
                    Pill("Dejarlo", false, onDrop)
                }
            }
        } else {
            suggestions.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                        Text("${c.hint} · ${c.litres} ${if (c.litres == 1) "litro" else "litros"}", fontSize = 14.sp, color = P.muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Pill("Empezar", true) { onStart(c) }
                }
            }
        }
    }
}

// ---------- Hucha de redondeo ----------

@Composable
fun RoundUpCard(amount: Double, saved: Double, onMove: () -> Unit, onSkip: () -> Unit) {
    val money = LocalMoney.current
    CardBox {
        Header(Icons.Filled.Savings, Vaca.UbreSuave, Vaca.UbreFuerte, "Hucha de redondeo", if (saved > 0) "Llevas ${money.format(saved)} en la hucha" else "Los céntimos también cuentan")
        Text(
            "Si redondeas al euro tus gastos de la semana pasada salen ${money.format(amount)}. Pásalos al ahorro y ni lo notarás.",
            fontSize = 16.sp, color = P.ink,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Pasar ${money.format(amount)}", true, onMove)
            Pill("Esta semana no", false, onSkip)
        }
    }
}

// ---------- Comparar meses ----------

@Composable
fun MonthCompareCard(c: MonthCompare) {
    val money = LocalMoney.current
    val prev = c.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, ES)
    val diff = c.now - c.before
    CardBox {
        Column {
            Text("Frente a $prev", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text(
                (if (c.days != null) "Del 1 al ${c.days} de cada mes, sin fijos. " else "Mes completo, sin fijos. ") +
                    when {
                        kotlin.math.abs(diff) < 1 -> "Vas igual."
                        diff > 0 -> "Llevas ${money.format(diff)} más."
                        else -> "Llevas ${money.format(-diff)} menos."
                    },
                fontSize = 14.sp, color = P.muted,
            )
        }
        c.rows.take(6).forEach { (tag, now, before) ->
            val d = now - before
            val up = d > 0.5
            val down = d < -0.5
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(tag.color().copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(tag.icon(), null, Modifier.size(24.dp), tint = tag.color())
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tag.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                    Text("${money.format(now)} · antes ${money.format(before)}", fontSize = 14.sp, color = P.muted)
                }
                if (up || down) Row(
                    Modifier.clip(CircleShape).background(if (up) Vaca.UbreSuave else Vaca.Pasto).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, Modifier.size(16.dp), tint = if (up) Vaca.UbreFuerte else Vaca.Prado)
                    Spacer(Modifier.width(4.dp))
                    Text((if (up) "+" else "−") + money.format(kotlin.math.abs(d)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (up) Vaca.UbreFuerte else Vaca.PradoOscuro)
                }
                else Text("Igual", fontSize = 14.sp, color = P.muted)
            }
        }
    }
}
