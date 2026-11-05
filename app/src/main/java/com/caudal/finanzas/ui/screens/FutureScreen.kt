package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.RecurringBill
import com.caudal.finanzas.domain.Bills
import com.caudal.finanzas.domain.FlowEvent
import com.caudal.finanzas.ui.Amount
import com.caudal.finanzas.ui.AmountTrailing
import com.caudal.finanzas.ui.CardList
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.EmptyState
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LineChart
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.SectionTitle
import com.caudal.finanzas.ui.Segmented
import com.caudal.finanzas.ui.categoryColor
import com.caudal.finanzas.ui.categoryIcon
import com.caudal.finanzas.ui.newBill
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")
private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", es)

fun prettyDate(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> d.format(dayMonth)
    }
}

/** Lista de gastos fijos con su estado de este mes y un botón para apuntar el pago. */
@Composable
fun BillsList(bills: List<RecurringBill>, expenses: List<Expense>, categories: List<Category>, accounts: List<Account>, onEdit: (Editing) -> Unit, onPay: (RecurringBill) -> Unit) {
    val money = LocalMoney.current
    val month = YearMonth.now()
    CardList(bills, key = { it.id }) { b ->
        val due = Bills.dueDate(b, month)
        val paid = due != null && Bills.isPaid(b, month, expenses)
        ListRow(
            title = b.name,
            subtitle = listOfNotNull(
                if (b.yearly) "Cada año · ${java.time.Month.of(b.month).getDisplayName(java.time.format.TextStyle.FULL, es)} ${b.day}" else "Día ${b.day} de cada mes",
                accounts.firstOrNull { it.id == b.accountId }?.name,
            ).joinToString(" · "),
            onClick = { onEdit(Editing.BillForm(b)) },
            leading = { IconTile(categoryIcon(categories.firstOrNull { it.id == b.categoryId }), categoryColor(categories, b.categoryId)) },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Text("-" + money.format(b.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                    when {
                        due == null -> Text("No toca este mes", style = MaterialTheme.typography.labelSmall, color = P.muted)
                        paid -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(12.dp), tint = P.gain)
                            Text(" Pagado", style = MaterialTheme.typography.labelSmall, color = P.gain)
                        }
                        else -> Box(
                            Modifier.padding(top = 2.dp).clip(CircleShape).background(P.brand).clickable { onPay(b) }.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("Pagar", style = MaterialTheme.typography.labelSmall, color = P.onBrand, fontWeight = FontWeight.Bold) }
                    }
                }
            },
        )
    }
}

@Composable
fun EventRow(e: FlowEvent) {
    val money = LocalMoney.current
    ListRow(
        title = e.label,
        subtitle = prettyDate(e.date) + if (e.overdue) " · debería haber llegado ya" else "",
        leading = { IconTile(if (e.isIncome) Icons.Filled.Work else Icons.Filled.CalendarMonth, if (e.isIncome) P.gain else P.loss) },
        trailing = { AmountTrailing(money.signed(e.amount), color = if (e.isIncome) P.gain else P.ink) },
    )
}

/** "Tu futuro": saldo previsto día a día con cobros, gastos fijos y tu gasto habitual. */
@Composable
fun FutureScreen(vm: FinanceViewModel, onEdit: (Editing) -> Unit, onBack: () -> Unit) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val expenses by vm.allExpenses.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    var days by rememberSaveable { mutableStateOf(30) }
    val p = planning ?: return

    val today = LocalDate.now()
    val points = p.forecast.points.take(days + 1)
    val events = p.forecast.events.filter { !it.date.isAfter(today.plusDays(days.toLong())) }
    val end = points.last()
    val lowest = points.minBy { it.second }
    val negative = points.firstOrNull { it.second < 0 }
    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize().background(P.bg),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = nav + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Tu futuro", style = MaterialTheme.typography.headlineMedium, color = P.ink)
                    Text("Cuánto tendrás disponible cada día", style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }
        item { Segmented(listOf(30, 60, 90), days, { "$it días" }, { days = it }) }

        if (negative != null) {
            item {
                MCard(color = P.loss.copy(alpha = 0.12f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.Warning, P.loss)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Te quedarías corto", style = MaterialTheme.typography.titleMedium, color = P.ink)
                            Text(
                                "${prettyDate(negative.first)} tu saldo disponible bajaría a ${money.format(negative.second)}. " +
                                    "Tienes tiempo para mover dinero del ahorro o recortar gastos antes.",
                                style = MaterialTheme.typography.bodySmall, color = P.ink,
                            )
                        }
                    }
                }
            }
        }

        item {
            MintCard {
                Overline("Disponible el ${end.first.format(DateTimeFormatter.ofPattern("d 'de' MMMM", es))}")
                Amount(money.format(end.second), size = 38.sp, color = if (end.second < 0) P.loss else P.ink)
                Text(
                    "Punto más bajo: ${money.format(lowest.second)} · ${prettyDate(lowest.first)}",
                    style = MaterialTheme.typography.labelLarge, color = if (lowest.second < 0) P.loss else P.muted,
                )
                Spacer(Modifier.height(12.dp))
                LineChart(points.map { it.second }, if (negative != null) P.loss else P.gain, Modifier.fillMaxWidth().height(130.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "Cuenta tus cobros, tus gastos fijos y tu gasto habitual de ${money.format(p.forecast.dailySpend)} al día (media de los últimos 60 días). Solo el dinero disponible: el ahorro no se toca.",
                    style = MaterialTheme.typography.bodySmall, color = P.muted,
                )
            }
        }

        item { SectionTitle("Lo que viene") }
        item {
            if (events.isEmpty()) EmptyState(Icons.Filled.CalendarMonth, "Nada previsto", "Añade tus ingresos y tus gastos fijos para ver aquí lo que va a pasar en tu cuenta.")
            else CardList(events) { e -> EventRow(e) }
        }

        item {
            SectionTitle("Gastos fijos") {
                TextButton(onClick = { onEdit(Editing.BillForm(newBill(categories, accounts.firstOrNull()?.id))) }) {
                    Icon(Icons.Filled.Add, null, tint = P.gain); Spacer(Modifier.width(4.dp)); Text("Añadir", color = P.gain)
                }
            }
        }
        item {
            if (bills.isEmpty()) EmptyState(Icons.Filled.CalendarMonth, "Sin gastos fijos", "Alquiler, luz, suscripciones, seguros… Apúntalos una vez y Ahorra, vaca los tendrá en cuenta cada mes.")
            else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillsList(bills, expenses, categories, accounts, onEdit, vm::payBill)
                    Text(
                        "Total: ${money.format(bills.sumOf { Bills.monthly(it) })} al mes (los anuales, repartidos)",
                        style = MaterialTheme.typography.bodySmall, color = P.muted, modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
