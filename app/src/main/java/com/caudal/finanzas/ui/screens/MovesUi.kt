package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.RecurringBill
import com.caudal.finanzas.data.Transfer
import com.caudal.finanzas.domain.Budgeting
import com.caudal.finanzas.domain.Merchants
import com.caudal.finanzas.domain.SpendTag
import com.caudal.finanzas.domain.spendTag
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.color
import com.caudal.finanzas.ui.icon
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")

enum class MoveKind { EXPENSE, INCOME, TRANSFER }

/** Un movimiento para la lista y el calendario: gasto, ingreso o traspaso entre tus cuentas. */
data class MoveItem(
    val key: String, val day: LocalDate, val title: String, val subtitle: String, val amount: Double,
    val kind: MoveKind, val tag: SpendTag?, val fixed: Boolean, val createdAt: Long, val editing: Editing,
    /** Texto original del gasto, para buscar el logo del comercio. */
    val note: String = "",
)

fun buildMoves(month: YearMonth, expenses: List<Expense>, incomes: List<Income>, transfers: List<Transfer>, accounts: List<Account>, bills: List<RecurringBill>): List<MoveItem> {
    fun acc(id: Long?) = accounts.firstOrNull { it.id == id }?.name
    fun inMonth(d: Long) = YearMonth.from(LocalDate.ofEpochDay(d)) == month
    val e = expenses.filter { inMonth(it.epochDay) }.map { x ->
        val t = x.spendTag()
        MoveItem(
            "e${x.id}", LocalDate.ofEpochDay(x.epochDay), Merchants.clean(x.note).ifBlank { t.label },
            listOfNotNull(t.label, acc(x.accountId)).joinToString(" · "), -x.amount, MoveKind.EXPENSE, t,
            Budgeting.isFixed(x, bills), x.createdAt, Editing.ExpenseForm(x), x.note,
        )
    }
    val i = incomes.filter { inMonth(it.epochDay) }.map { x ->
        MoveItem("i${x.id}", LocalDate.ofEpochDay(x.epochDay), x.note.ifBlank { "Ingreso" }, listOfNotNull("Ingreso", acc(x.accountId)).joinToString(" · "), x.amount, MoveKind.INCOME, null, false, x.createdAt, Editing.IncomeForm(x))
    }
    val t = transfers.filter { inMonth(it.epochDay) }.map { x ->
        MoveItem("t${x.id}", LocalDate.ofEpochDay(x.epochDay), x.note.ifBlank { "Traspaso" }, "${acc(x.fromAccountId) ?: "?"} → ${acc(x.toAccountId) ?: "?"}", x.amount, MoveKind.TRANSFER, null, false, x.createdAt, Editing.TransferForm(x.fromAccountId))
    }
    return (e + i + t).sortedWith(compareByDescending<MoveItem> { it.day }.thenByDescending { it.createdAt })
}

private fun dayTitle(d: LocalDate) = d.dayOfWeek.getDisplayName(JTextStyle.FULL, es).replaceFirstChar { it.uppercase() } + ", ${d.dayOfMonth} de " + d.month.getDisplayName(JTextStyle.FULL, es)

/** Color del día en el calendario según lo que gastaste (sin alquiler ni fijos) frente a lo que te toca. */
private fun dayColor(spent: Double, allowance: Double): Color = when {
    spent <= 0 -> Color.Transparent
    allowance <= 0 -> Color(0xFFEAF2E4)
    spent <= allowance -> Color(0xFFDCEBD3)
    spent <= allowance * 1.5 -> Color(0xFFFBE3E8)
    else -> Color(0xFFF5A3B4)
}

@Composable
private fun MoveIcon(m: MoveItem, size: Int) {
    if (m.kind == MoveKind.EXPENSE) { com.caudal.finanzas.ui.MerchantLogo(m.note, m.tag ?: SpendTag.OTHER, size.dp); return }
    val (icon, color) = when (m.kind) {
        MoveKind.EXPENSE -> (m.tag ?: SpendTag.OTHER).let { it.icon() to it.color() }
        MoveKind.INCOME -> Icons.Filled.Payments to P.gain
        MoveKind.TRANSFER -> Icons.Filled.SwapHoriz to Color(0xFF8E8E93)
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.55f).dp), tint = color)
    }
}

/**
 * Selección para sumar: mantén pulsado un movimiento y toca otros; abajo sale la suma.
 * Vive mientras la app está abierta; se vacía al cambiar de mes o de vista.
 */
object MoveSelection {
    val picked = androidx.compose.runtime.mutableStateMapOf<String, MoveItem>()
    fun toggle(m: MoveItem) { if (picked.containsKey(m.key)) picked.remove(m.key) else picked[m.key] = m }
    fun clear() = picked.clear()
}

@Composable
private fun MoveRow(m: MoveItem, onEdit: (Editing) -> Unit) {
    val money = LocalMoney.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isPicked = MoveSelection.picked.containsKey(m.key)
    Row(
        Modifier.fillMaxWidth()
            .background(if (isPicked) P.gain.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(
                onClick = { if (MoveSelection.picked.isNotEmpty()) MoveSelection.toggle(m) else onEdit(m.editing) },
                onLongClick = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    MoveSelection.toggle(m)
                },
                onLongClickLabel = "Seleccionar para sumar",
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPicked) Box(Modifier.size(40.dp).clip(CircleShape).background(P.gain), contentAlignment = Alignment.Center) {
            Icon(androidx.compose.material.icons.Icons.Filled.Check, "Seleccionado", Modifier.size(24.dp), tint = Color.White)
        } else MoveIcon(m, 40)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
            Text(
                m.day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", es)).replace(".", "") + " · " + m.subtitle + if (m.fixed) " · fijo" else "",
                fontSize = 12.sp, color = P.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (m.kind) { MoveKind.INCOME -> "+" + money.format(m.amount); MoveKind.TRANSFER -> money.format(m.amount); else -> "−" + money.format(-m.amount) },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = when (m.kind) { MoveKind.INCOME -> P.gain; MoveKind.TRANSFER -> P.muted; else -> P.ink },
            )
            if (m.createdAt > 0) Text(
                java.time.Instant.ofEpochMilli(m.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 12.sp, color = P.muted,
            )
        }
    }
}

@Composable
private fun DayCard(moves: List<MoveItem>, onEdit: (Editing) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card)) {
        moves.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = P.line, thickness = 0.6.dp)
            MoveRow(m, onEdit)
        }
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, dark: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(if (dark) Color(0xFF151515) else P.card).padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = if (dark) Color.White.copy(alpha = 0.7f) else P.muted)
        androidx.compose.foundation.text.BasicText(
            value, maxLines = 1,
            style = TextStyle(fontWeight = FontWeight.Bold, color = if (dark) Color.White else color),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 20.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Movimientos en calendario (cada día con el color de lo que gastaste) o en lista por días con buscador.
 * [allowance] = lo que te toca gastar al día; el alquiler y los fijos no colorean el día.
 */
fun LazyListScope.movesSection(
    calendar: Boolean,
    month: YearMonth,
    moves: List<MoveItem>,
    allowance: Double,
    pending: Double,
    query: String,
    onQuery: (String) -> Unit,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onEdit: (Editing) -> Unit,
    /** Cobros que aún esperas este mes (día → nombre e importe, "~" si es estimado). */
    expected: Map<LocalDate, List<Pair<String, String>>> = emptyMap(),
    /** Cabecera de la lista: total, cambio y barras (semana/mes/año). */
    analytics: com.caudal.finanzas.domain.Analytics? = null,
    period: Int = 1,
    onPeriod: (Int) -> Unit = {},
) {
    if (calendar) {
        item {
            val money = LocalMoney.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("INGRESADO", money.format(moves.filter { it.kind == MoveKind.INCOME }.sumOf { it.amount }), P.gain, false, Modifier.weight(1f))
                Tile("GASTADO", money.format(moves.filter { it.kind == MoveKind.EXPENSE && !it.fixed }.sumOf { -it.amount }), P.loss, false, Modifier.weight(1f))
                Tile("POR PAGAR", money.format(pending), P.ink, true, Modifier.weight(1f))
            }
        }
        item {
            val byDay = moves.groupBy { it.day }
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            val today = LocalDate.now()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), fontSize = 14.sp, color = P.muted, textAlign = TextAlign.Center) }
                }
                Spacer(Modifier.height(8.dp))
                (0 until (cells + 6) / 7).forEach { w ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..6).forEach { c ->
                            val idx = w * 7 + c - offset + 1
                            if (idx < 1 || idx > month.lengthOfMonth()) Spacer(Modifier.weight(1f))
                            else {
                                val day = month.atDay(idx)
                                val list = byDay[day].orEmpty()
                                val spent = list.filter { it.kind == MoveKind.EXPENSE && !it.fixed }.sumOf { -it.amount }
                                val income = list.filter { it.kind == MoveKind.INCOME }.sumOf { it.amount }
                                val isToday = day == today
                                val waiting = expected[day]
                                val bg = if (isToday) Color(0xFF2E6B3F) else dayColor(spent, allowance)
                                // Sobre un color, texto siempre oscuro (también en modo oscuro); sin color, el de la app.
                                val ink = when { isToday -> Color.White; bg != Color.Transparent -> Color(0xFF1C1C1E); else -> P.ink }
                                val soft = when { isToday -> Color.White.copy(alpha = 0.85f); bg != Color.Transparent -> Color(0xFF3A3A3C); else -> P.muted }
                                Column(
                                    Modifier.weight(1f).aspectRatio(0.8f).padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                                        .border(
                                            2.dp,
                                            when { day == selected && !isToday -> P.ink; waiting != null -> P.gain; else -> Color.Transparent },
                                            RoundedCornerShape(16.dp),
                                        )
                                        .clickable { onSelect(day) }.padding(top = 8.dp, bottom = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("$idx", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (list.isEmpty() && waiting == null && !isToday) P.muted else ink)
                                    Spacer(Modifier.weight(1f))
                                    when {
                                        spent > 0 -> Text("−" + Math.round(spent), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = soft, maxLines = 1)
                                        income > 0 -> Text("+" + Math.round(income), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isToday) Color.White else P.gain, maxLines = 1)
                                        waiting != null -> Text(waiting.first().second, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isToday) Color.White else P.gain, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                // Leyenda: qué significa cada color y el borde verde.
                Column(Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cada día pone lo que gastaste (sin alquiler ni fijos).", fontSize = 12.sp, color = P.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(Color(0xFFDCEBD3) to "Dentro de lo tuyo", Color(0xFFFBE3E8) to "Algo de más", Color(0xFFF5A3B4) to "Mucho de más").forEach { (c, t) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(c))
                                Spacer(Modifier.width(4.dp))
                                Text(t, fontSize = 12.sp, color = P.muted)
                            }
                        }
                    }
                    if (expected.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).border(2.dp, P.gain, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("Día en que esperas cobrar", fontSize = 12.sp, color = P.muted)
                    }
                }
            }
        }
        val day = selected ?: LocalDate.now().takeIf { YearMonth.from(it) == month } ?: moves.firstOrNull()?.day
        val list = moves.filter { it.day == day }
        if (day != null) item {
            val money = LocalMoney.current
            val net = list.filter { it.kind != MoveKind.TRANSFER }.sumOf { it.amount }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayTitle(day), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
                if (list.isNotEmpty()) Text((if (net >= 0) "+" else "−") + money.format(kotlin.math.abs(net)), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (net >= 0) P.gain else P.ink)
            }
        }
        expected[day]?.let { waits -> item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(2.dp, P.gain, RoundedCornerShape(24.dp)).padding(16.dp)) {
                waits.forEach { (name, amount) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(P.gain.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Payments, null, Modifier.size(24.dp), tint = P.gain)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Esperas cobrar $name", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                            Text(if (amount.startsWith("+~")) "Importe estimado; confírmalo en Resumen → Tus ingresos" else "Aún no ha llegado", fontSize = 12.sp, color = P.muted)
                        }
                        Text(amount.replace("+~", "~"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.gain)
                    }
                }
            }
        } }
        if (day != null) item {
            if (list.isEmpty()) Text("Sin movimientos este día.", fontSize = 16.sp, color = P.muted, modifier = Modifier.padding(horizontal = 8.dp))
            else DayCard(list, onEdit)
        }
    } else {
        // ---------- Cabecera tipo "Analytics": Semana / Mes / Año, total, cambio y barras ----------
        if (analytics != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Semana", "Mes", "Año").forEachIndexed { i, t ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (i == period) P.brand else P.chip).clickable { onPeriod(i) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(t, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (i == period) P.onBrand else P.ink) }
                    }
                }
            }
            item { AnalyticsHeader(analytics) }
            item { Text("Transacciones", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(P.card).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = P.muted)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar movimientos", fontSize = 16.sp, color = P.muted)
                    BasicTextField(query, onQuery, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = P.ink), cursorBrush = SolidColor(P.ink), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { Text("Mantén pulsado un movimiento y toca otros para sumarlos.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(horizontal = 8.dp)) }
        val q = com.caudal.finanzas.domain.Classifier.normalize(query)
        val shown = if (q.isBlank()) moves else moves.filter { com.caudal.finanzas.domain.Classifier.normalize(it.title + " " + it.subtitle).contains(q) }
        if (shown.isEmpty()) item { Text(if (q.isBlank()) "No hay movimientos este mes." else "Nada coincide con «$query».", fontSize = 16.sp, color = P.muted, modifier = Modifier.padding(8.dp)) }
        shown.groupBy { it.day }.forEach { (day, list) ->
            item(key = "h$day") { Text(dayTitle(day), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.muted, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
            item(key = "d$day") { DayCard(list, onEdit) }
        }
    }
}

/** Gráficas del gasto: donut por categorías con el total y el cambio, barras por mes y los últimos gastos. */
fun LazyListScope.statsSection(
    stats: com.caudal.finanzas.domain.SpendStats,
    period: Int,
    onPeriod: (Int) -> Unit,
    onTag: (SpendTag) -> Unit,
    onEdit: (Editing) -> Unit,
) {
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Este mes", "3 meses", "Este año").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (i == period) P.brand else P.chip).clickable { onPeriod(i) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (i == period) P.onBrand else P.ink) }
            }
        }
    }
    item {
        val money = LocalMoney.current
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.caudal.finanzas.ui.Donut(
                stats.byTag.map { (t, v) -> com.caudal.finanzas.ui.Slice(t.label, v, t.color()) }.ifEmpty { listOf(com.caudal.finanzas.ui.Slice("", 1.0, P.chip)) },
                size = 230.dp, stroke = 32.dp,
            ) {
                Text("Total gastado", fontSize = 14.sp, color = P.muted)
                Text(money.format(stats.total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = P.ink)
                stats.changePct?.let { ch ->
                    val good = ch <= 0
                    Box(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background((if (good) P.gain else P.loss).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text((if (ch >= 0) "↑ " else "↓ ") + String.format(es, "%.0f %%", kotlin.math.abs(ch)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (good) P.gain else P.loss)
                    }
                    Text(stats.prevLabel, fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text("Sin alquiler ni fijos", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (stats.byTag.isNotEmpty()) item {
        val money = LocalMoney.current
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(vertical = 8.dp)) {
            stats.byTag.forEachIndexed { i, (t, v) ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = P.line, thickness = 0.6.dp)
                Row(Modifier.fillMaxWidth().clickable { onTag(t) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(t.color().copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(t.icon(), null, Modifier.size(24.dp), tint = t.color()) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Text(money.format(v), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                        }
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.caudal.finanzas.ui.ProgressLine(if (stats.total > 0) v / stats.total else 0.0, t.color(), Modifier.weight(1f), height = 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(String.format(es, "%.0f %%", if (stats.total > 0) v / stats.total * 100 else 0.0), fontSize = 14.sp, color = P.muted)
                        }
                    }
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, androidx.compose.ui.Modifier.size(24.dp), tint = P.muted)
                }
            }
        }
    }
    item {
        val money = LocalMoney.current
        val max = stats.bars.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
        val current = YearMonth.now()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(16.dp)) {
            Text("Por meses", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text("Gasto del día a día de cada mes", fontSize = 14.sp, color = P.muted)
            Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                stats.bars.forEach { (ym, v) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (ym == current) Text(money.format(v).substringBefore(','), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 1)
                        Box(
                            Modifier.fillMaxWidth().height((120 * v / max).coerceAtLeast(4.0).dp).clip(RoundedCornerShape(8.dp))
                                .background(if (ym == current) P.gain else P.chip),
                        )
                        Text(ym.month.getDisplayName(JTextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
    if (stats.recent.isNotEmpty()) item {
        val money = LocalMoney.current
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(vertical = 8.dp)) {
            Text("Últimos gastos", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            stats.recent.forEach { e ->
                val t = e.spendTag()
                Row(Modifier.fillMaxWidth().clickable { onEdit(Editing.ExpenseForm(e)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.caudal.finanzas.ui.MerchantLogo(e.note, t, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Merchants.clean(e.note).ifBlank { t.label }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
                        Text(LocalDate.ofEpochDay(e.epochDay).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", es)) + " · " + t.label, fontSize = 14.sp, color = P.muted, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("−" + money.format(e.amount), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                        Text(java.time.Instant.ofEpochMilli(e.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), fontSize = 12.sp, color = P.muted)
                    }
                }
            }
        }
    }
}

/** Barra con la suma de los movimientos seleccionados (gastos, ingresos y el neto). */
@Composable
fun MoveSelectionBar(modifier: Modifier = Modifier) {
    val picked = MoveSelection.picked.values.toList()
    if (picked.isEmpty()) return
    val money = LocalMoney.current
    val out = picked.filter { it.kind == MoveKind.EXPENSE }.sumOf { -it.amount }
    val inc = picked.filter { it.kind == MoveKind.INCOME }.sumOf { it.amount }
    val transfers = picked.count { it.kind == MoveKind.TRANSFER }
    val net = inc - out
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF151515)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${picked.size} ${if (picked.size == 1) "movimiento" else "movimientos"}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Text((if (net >= 0) "+" else "−") + money.format(kotlin.math.abs(net)), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (net >= 0) Color(0xFF4ADE80) else Color.White)
            Text(
                listOfNotNull(
                    if (out > 0) "Gastos −${money.format(out)}" else null,
                    if (inc > 0) "Ingresos +${money.format(inc)}" else null,
                    if (transfers > 0) "$transfers ${if (transfers == 1) "traspaso" else "traspasos"} (no suma)" else null,
                ).joinToString(" · "),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(CircleShape).background(Color.White).clickable { MoveSelection.clear() }.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Listo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF151515)) }
    }
}

/** Cabecera de Movimientos: total gastado grande, el cambio frente al periodo anterior y las barras con el actual resaltado. */
@Composable
private fun AnalyticsHeader(a: com.caudal.finanzas.domain.Analytics) {
    val money = LocalMoney.current
    val lime = Color(0xFF2E6B3F)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).padding(24.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicText(
                    money.format(a.total), maxLines = 1,
                    style = TextStyle(fontWeight = FontWeight.Bold, color = P.ink),
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 32.sp),
                )
                Text("Total gastado · ${a.title}", fontSize = 14.sp, color = P.muted)
            }
            a.changePct?.let { ch ->
                val good = ch <= 0
                Column(horizontalAlignment = Alignment.End) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background((if (good) P.gain else P.loss).copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text((if (ch >= 0) "↑ " else "↓ ") + String.format(es, "%.0f %%", kotlin.math.abs(ch)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (good) P.gain else P.loss)
                    }
                    Text(a.prevLabel, fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        val max = a.bars.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            // Eje: 0, la mitad y el máximo.
            Column(Modifier.height(160.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(max, max / 2, 0.0).forEach { Text(compactEuros(it), fontSize = 11.sp, color = P.muted) }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).height(184.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                a.bars.forEach { b ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height((152 * b.value / max).coerceAtLeast(4.0).dp).clip(RoundedCornerShape(8.dp))
                                .background(if (b.current) lime else P.chip),
                        )
                        Text(b.label, fontSize = 12.sp, fontWeight = if (b.current) FontWeight.SemiBold else FontWeight.Normal, color = if (b.current) P.ink else P.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

private fun compactEuros(v: Double): String = when {
    v >= 1000 -> String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1fK", v / 1000).replace(",0K", "K")
    else -> Math.round(v).toString()
}
