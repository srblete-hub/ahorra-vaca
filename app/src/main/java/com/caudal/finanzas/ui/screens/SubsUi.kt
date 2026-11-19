package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.data.RecurringBill
import com.caudal.finanzas.domain.Bills
import com.caudal.finanzas.domain.NextCharge
import com.caudal.finanzas.domain.Recurring
import com.caudal.finanzas.domain.SpendTag
import com.caudal.finanzas.domain.SubscriptionCatalog
import com.caudal.finanzas.domain.SubscriptionCheck
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MerchantLogo
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.Vaca
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")

/** "hoy", "mañana", "en 3 días", "el lunes 24". */
private fun whenText(today: LocalDate, date: LocalDate): String = when (val n = ChronoUnit.DAYS.between(today, date).toInt()) {
    0 -> "hoy"
    1 -> "mañana"
    in 2..6 -> "en $n días"
    else -> "el " + date.dayOfWeek.getDisplayName(TextStyle.FULL, ES) + " ${date.dayOfMonth}"
}

/** La línea de debajo del nombre: prueba gratis o cuándo se cobra. */
private fun subline(c: NextCharge, today: LocalDate): Pair<String, Boolean> {
    c.bill.trialEnds?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isBefore(today) }?.let { end ->
        return "Prueba gratis · acaba ${whenText(today, end)}" to (ChronoUnit.DAYS.between(today, end) <= 2)
    }
    return "Se cobra ${whenText(today, c.date)}" to (c.daysFrom(today) <= 1)
}

/** Tarjeta de Inicio: cuánto pagas en suscripciones y cuál es el próximo cobro. */
@Composable
fun SubscriptionsCard(subs: List<NextCharge>, today: LocalDate, pending: Int, onOpen: () -> Unit) {
    val money = LocalMoney.current
    val monthly = subs.sumOf { Bills.monthly(it.bill) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(P.card).clickable(onClick = onOpen).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tus suscripciones", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = P.ink)
                Text(
                    if (subs.isEmpty()) "Apúntalas y te aviso antes de cada cobro" else "${money.format(monthly)} al mes · ${money.format(monthly * 12)} al año",
                    fontSize = 14.sp, color = P.muted,
                )
            }
            // Logos apilados.
            if (subs.isNotEmpty()) Box(Modifier.size(width = (40 + 24 * (minOf(subs.size, 3) - 1)).dp, height = 40.dp)) {
                subs.take(3).forEachIndexed { i, s ->
                    Box(Modifier.offset(x = (i * 24).dp).size(40.dp).clip(CircleShape).background(Color.White).border(2.dp, P.card, CircleShape), contentAlignment = Alignment.Center) {
                        MerchantLogo(s.bill.name, SpendTag.SUBSCRIPTIONS, 36.dp)
                    }
                }
            } else Box(Modifier.size(40.dp).clip(CircleShape).background(Vaca.Pasto), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null, Modifier.size(24.dp), tint = Vaca.Prado)
            }
        }
        val next = subs.firstOrNull()
        if (next != null) {
            val (line, urgent) = subline(next, today)
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(if (urgent) Vaca.UbreSuave else P.chip).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(16.dp), tint = if (urgent) Vaca.UbreFuerte else P.muted)
                Spacer(Modifier.width(8.dp))
                Text("${next.bill.name} · ${line.replaceFirstChar { it.lowercase() }}", Modifier.weight(1f), fontSize = 14.sp, color = if (urgent) Vaca.Tinta else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(money.format(next.bill.amount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (urgent) Vaca.Tinta else P.ink)
            }
        }
        if (pending > 0) Text(
            "$pending ${if (pending == 1) "idea" else "ideas"} para pagar menos", Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Vaca.Prado,
        )
    }
}

/**
 * Todas tus suscripciones: próximo cobro de cada una, pruebas gratis, las que se ven en tus pagos y no
 * sigues, añadir con un toque, cuándo avisarte y las ideas para pagar menos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionsSheet(
    subs: List<NextCharge>,
    detected: List<Recurring>,
    check: SubscriptionCheck?,
    reviewed: Set<String>,
    remindDays: Int,
    today: LocalDate,
    onRemindDays: (Int) -> Unit,
    onEdit: (RecurringBill) -> Unit,
    onFollow: (Recurring) -> Unit,
    onAdd: (SubscriptionCatalog.Item?) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val monthly = subs.sumOf { Bills.monthly(it.bill) }
    FormSheet("Tus suscripciones", onDismiss, primary = "Hecho", onPrimary = onDismiss) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money.format(monthly), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = P.ink)
            Text(" al mes", Modifier.padding(bottom = 8.dp), fontSize = 16.sp, color = P.muted)
        }
        Text("${money.format(monthly * 12)} al año en ${subs.size} ${if (subs.size == 1) "suscripción" else "suscripciones"}", fontSize = 14.sp, color = P.muted)

        // Cuándo avisar.
        Text("Avisarme antes de cada cobro", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "No", 1 to "1 día", 2 to "2 días", 3 to "3 días").forEach { (v, label) ->
                val on = v == remindDays
                Box(Modifier.clip(CircleShape).background(if (on) P.brand else P.chip).clickable { onRemindDays(v) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (on) P.onBrand else P.ink)
                }
            }
        }

        // La lista, por orden de cobro.
        if (subs.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.chip).padding(vertical = 8.dp)) {
            subs.forEach { c ->
                val (line, urgent) = subline(c, today)
                Row(Modifier.fillMaxWidth().clickable { onEdit(c.bill) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MerchantLogo(c.bill.name, SpendTag.SUBSCRIPTIONS, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.bill.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(line, fontSize = 14.sp, color = if (urgent) Vaca.UbreFuerte else P.muted, fontWeight = if (urgent) FontWeight.Medium else FontWeight.Normal)
                    }
                    if (!c.bill.remind) Icon(Icons.Filled.NotificationsOff, "Sin aviso", Modifier.padding(end = 8.dp).size(16.dp), tint = P.muted)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money.format(c.bill.amount), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                        Text(if (c.bill.yearly) "al año" else "al mes", fontSize = 12.sp, color = P.muted)
                    }
                }
            }
        }

        // Las que se ven en tus pagos pero no tienes apuntadas.
        if (detected.isNotEmpty()) {
            Text("Las veo en tus pagos", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            detected.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MerchantLogo(r.name, SpendTag.SUBSCRIPTIONS, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${money.format(r.last)} cada mes, hacia el día ${r.lastDate.dayOfMonth}", fontSize = 14.sp, color = P.muted)
                    }
                    Box(Modifier.clip(CircleShape).background(Vaca.Pasto).clickable { onFollow(r) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Seguir", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Vaca.PradoOscuro)
                    }
                }
            }
        }

        // Añadir con un toque.
        Text("Añadir", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        val have = subs.map { it.bill.name.lowercase(ES) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SubscriptionCatalog.ITEMS.filter { it.name.lowercase(ES) !in have }.forEach { item ->
                Row(Modifier.clip(CircleShape).background(P.chip).clickable { onAdd(item) }.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { MerchantLogo(item.name, SpendTag.SUBSCRIPTIONS, 24.dp) }
                    Spacer(Modifier.width(8.dp))
                    Text(item.name, fontSize = 14.sp, color = P.ink)
                }
            }
            Row(Modifier.clip(CircleShape).background(P.brand).clickable { onAdd(null) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = P.onBrand)
                Spacer(Modifier.width(4.dp))
                Text("Otra", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = P.onBrand)
            }
        }

        // Ideas para pagar menos (subidas, pago anual, repetidas).
        if (check != null && check.actions.isNotEmpty()) {
            Text("Para pagar menos", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.chip).padding(vertical = 8.dp)) {
                check.actions.forEach { a ->
                    val ok = a.key in reviewed
                    Row(Modifier.fillMaxWidth().clickable { onToggle(a.key) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.text, Modifier.weight(1f), fontSize = 14.sp, color = if (ok) P.muted else P.ink)
                        Spacer(Modifier.width(8.dp))
                        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (ok) "Hecho" else "Pendiente", Modifier.size(24.dp), tint = if (ok) Vaca.Prado else P.muted)
                    }
                }
            }
        }
        Text("Toca una suscripción para cambiar el precio, el día, marcarla como prueba gratis o quitar el aviso.", fontSize = 12.sp, color = P.muted)
    }
}
