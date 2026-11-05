package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.domain.isSaving
import com.caudal.finanzas.domain.isDebt
import androidx.compose.material.icons.filled.Savings
import com.caudal.finanzas.ui.Amount
import com.caudal.finanzas.ui.AmountTrailing
import com.caudal.finanzas.ui.CardList
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.EmptyState
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LineChart
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.Pill
import com.caudal.finanzas.ui.ProgressLine
import com.caudal.finanzas.ui.ScreenArgs
import com.caudal.finanzas.ui.ScreenTitle
import com.caudal.finanzas.ui.SectionTitle
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.gainColor
import com.caudal.finanzas.ui.icon
import com.caudal.finanzas.ui.pct
import com.caudal.finanzas.ui.signedPct
import java.time.LocalDate

enum class Period(val label: String, val days: Long?) {
    WEEK("Semana", 7), MONTH("Mes", 30), M3("3M", 90), M6("6M", 180), Y1("1A", 365), ALL("Todo", null)
}

private data class Bucket(val label: String, val sub: String, val value: Double, val color: androidx.compose.ui.graphics.Color, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: (() -> Unit)?)

@Composable
fun WealthScreen(vm: FinanceViewModel, settings: Settings, args: ScreenArgs, onOpenInvest: (InvestKind) -> Unit, onStartSetup: () -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val crypto by vm.crypto.collectAsStateWithLifecycle()
    val stocks by vm.stocks.collectAsStateWithLifecycle()
    val snapshots by vm.snapshots.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }

    val availableAccounts = accounts.filter { !it.isSaving() && !it.isDebt() }
    val debtAccounts = accounts.filter { it.isDebt() }
    val savingAccounts = accounts.filter { it.isSaving() }
    val available = availableAccounts.sumOf { it.balance }
    val saved = savingAccounts.sumOf { it.balance }
    val cryptoValue = crypto.sumOf { it.amount * it.price }
    val stockValue = stocks.sumOf { it.shares * it.price }
    val total = available + saved + cryptoValue + stockValue - debtAccounts.sumOf { it.balance }
    val incomesAll by vm.incomes.collectAsStateWithLifecycle()
    val transfersAll by vm.transfers.collectAsStateWithLifecycle()
    val expensesAll by vm.allExpenses.collectAsStateWithLifecycle()
    val since = listOfNotNull(incomesAll.minOfOrNull { it.epochDay }, expensesAll.minOfOrNull { it.epochDay }, snapshots.minOfOrNull { it.epochDay })
        .minOrNull()?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
    val earnedBy = accounts.filter { it.interestRate > 0 }.associate { it.id to com.caudal.finanzas.domain.InterestEarned.of(it, incomesAll, transfersAll, LocalDate.now(), since) }
    val yearlyInterest = earnedBy.values.sumOf { it.earned }
    var showWhy by rememberSaveable { mutableStateOf(false) }
    val hasData = accounts.isNotEmpty() || crypto.isNotEmpty() || stocks.isNotEmpty()

    // Una foto diaria del patrimonio alimenta el gráfico.
    LaunchedEffect(total, hasData) { if (hasData) vm.recordNetWorth(total) }
    LaunchedEffect(Unit) { vm.refreshIfStale() }

    val today = LocalDate.now().toEpochDay()
    val series = snapshots
        .filter { s -> period.days == null || s.epochDay >= today - period.days!! }
        .filter { it.epochDay != today }
        .map { it.total } + total
    val change = total - series.first()
    val changeBase = series.first()
    val fromDay = snapshots.filter { s -> period.days == null || s.epochDay >= today - period.days!! }.filter { it.epochDay != today }.firstOrNull()?.epochDay ?: today
    val planning by vm.planning.collectAsStateWithLifecycle()
    val why = planning?.data?.let { com.caudal.finanzas.domain.WealthExplainer.explain(it, fromDay, change, settings.dayChanges, money::format) }

    val buckets = listOf(
        Bucket("Disponible", "${availableAccounts.size} cuentas, efectivo y monederos", available, AvailableColor, Icons.Filled.AccountBalance, null),
        Bucket(
            "Ahorro", "${savingAccounts.size} sitios" + if (yearlyInterest > 0) " · +${money.format(yearlyInterest)} de intereses ganados" else "",
            saved, SavingsColor, Icons.Filled.Savings, null,
        ),
        Bucket("Cripto", "${crypto.size} monedas", cryptoValue, SeriesColors[2], Icons.Filled.CurrencyBitcoin) { onOpenInvest(InvestKind.CRYPTO) },
        Bucket("Acciones", "${stocks.size} posiciones", stockValue, InvestedColor, Icons.AutoMirrored.Filled.TrendingUp) { onOpenInvest(InvestKind.STOCKS) },
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = args.contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("Patrimonio") { args.headerActions() } }

        item {
            MintCard {
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(P.ink.copy(alpha = 0.05f)).padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) { Period.entries.forEach { p -> Pill(p.label, p == period, { period = p }) } }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Overline("Patrimonio neto")
                    Spacer(Modifier.height(4.dp))
                    Amount(money.format(total), size = 40.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (change >= 0) "↑ " else "↓ ") + money.signed(change) +
                            (if (changeBase > 0) " (${signedPct(change / changeBase * 100)})" else "") + " · ${period.label.lowercase()}  ›",
                        style = MaterialTheme.typography.labelLarge, color = gainColor(change), textAlign = TextAlign.Center,
                        modifier = Modifier.clip(CircleShape).clickable { showWhy = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    if (why != null) Text(why, fontSize = 13.sp, color = P.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                LineChart(series, P.gain, Modifier.fillMaxWidth().height(110.dp))
            }
        }

        item {
            CardList(buckets) { b ->
                ListRow(
                    title = b.label, subtitle = b.sub, onClick = b.onClick,
                    leading = { IconTile(b.icon, b.color) },
                    trailing = { AmountTrailing(money.format(b.value)) },
                    chevron = b.onClick != null,
                    below = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressLine(if (total > 0) b.value / total else 0.0, b.color, Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            Text(if (total > 0) pct(b.value / total * 100) else "—", style = MaterialTheme.typography.labelSmall, color = P.muted)
                        }
                    },
                )
            }
        }

        item {
            SectionTitle("Tus cuentas") {
                TextButton(onClick = { args.onEdit(Editing.AccountForm(Account(name = "", type = com.caudal.finanzas.data.AccountType.BANK, balance = 0.0))) }) {
                    Icon(Icons.Filled.Add, null, tint = P.gain); Spacer(Modifier.width(4.dp)); Text("Añadir", color = P.gain)
                }
            }
        }
        if (accounts.isEmpty()) {
            item { SetupCard(args.onEdit, onStepByStep = onStartSetup) }
        }
        listOf("Disponible" to availableAccounts, "Ahorro" to savingAccounts, "Deudas" to debtAccounts).filter { it.second.isNotEmpty() }.forEach { (label, list) ->
            item(key = "h-$label") {
                Overline("$label · ${money.format(list.sumOf { it.balance })}", Modifier.padding(start = 6.dp, top = 4.dp))
            }
            item(key = "l-$label") {
                CardList(list, key = { it.id }) { a ->
                    ListRow(
                        title = a.name,
                        subtitle = listOfNotNull(
                            a.type.label,
                            if (a.id == settings.cardAccountId && settings.cardPayments) "Pagos con tarjeta" else null,
                            a.note.ifBlank { null },
                        ).joinToString(" · "),
                        onClick = { args.onOpenAccount(a) },
                        leading = { IconTile(a.type.icon(), if (a.isSaving()) SavingsColor else AvailableColor) },
                        trailing = {
                            if (a.interestRate > 0) AmountTrailing(money.format(a.balance), "${pct(a.interestRate)} · ${if (earnedBy[a.id]?.estimated == true) "≈" else ""}+${money.format(earnedBy[a.id]?.earned ?: 0.0)} ganados", subColor = P.gain)
                            else AmountTrailing(money.format(a.balance))
                        },
                    )
                }
            }
        }
    }
    if (showWhy) planning?.data?.let { d ->
        WealthChangeSheet(d, fromDay, change, period.label.lowercase(), settings.dayChanges) { showWhy = false }
    }
}

/** Por qué ha cambiado tu patrimonio: cobros, gastos e inversiones del periodo, con cada movimiento. */
@Composable
private fun WealthChangeSheet(d: com.caudal.finanzas.domain.FinanceData, fromDay: Long, change: Double, periodLabel: String, dayChanges: Map<String, Double>, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val incomes = d.incomes.filter { it.epochDay > fromDay && it.accountId != null }.sortedByDescending { it.epochDay }
    val spent = d.expenses.filter { it.epochDay > fromDay && it.accountId != null }.sortedByDescending { it.epochDay }
    val inTotal = incomes.sumOf { it.amount }
    val outTotal = spent.sumOf { it.amount }
    val market = change - inTotal + outTotal
    val moves = (d.crypto.map { Triple(it.name, it.symbol, it.amount * it.price) } + d.stocks.map { Triple(it.name.ifBlank { it.ticker }, it.ticker, it.shares * it.price) })
        .mapNotNull { (n, s, v) -> dayChanges[s]?.let { pct -> Triple("$n ($s)", pct, v - v / (1 + pct / 100)) } }
        .sortedByDescending { kotlin.math.abs(it.third) }
    com.caudal.finanzas.ui.FormSheet("Por qué ha cambiado", onDismiss, primary = "Entendido", onPrimary = onDismiss) {
        Text("${money.signed(change)} · $periodLabel", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = gainColor(change))
        WhyLine("Lo que ha entrado en tus cuentas", inTotal, P.gain)
        WhyLine("Lo que has gastado", -outTotal, P.loss)
        WhyLine("Inversiones y ajustes de saldo", market, gainColor(market))
        Text("Las transferencias entre tus cuentas no cambian el total. Si corriges a mano el saldo de una cuenta, la diferencia sale en «ajustes de saldo».",fontSize = 12.sp, color = P.muted)
        if (moves.isNotEmpty()) {
            Text("Tus inversiones hoy (24 h)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            moves.forEach { (n, pct, v) -> MoveLine(n, "${signedPct(pct)} en 24 h", v) }
        }
        if (incomes.isNotEmpty()) {
            Text("Entradas", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            incomes.take(15).forEach { MoveLine(it.note.ifBlank { "Ingreso" }, dayLabel(it.epochDay) + " · " + (d.accounts.firstOrNull { a -> a.id == it.accountId }?.name ?: ""), it.amount) }
        }
        if (spent.isNotEmpty()) {
            Text("Salidas (las más grandes)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            spent.sortedByDescending { it.amount }.take(15).forEach { MoveLine(it.note.ifBlank { "Gasto" }, dayLabel(it.epochDay) + " · " + (d.accounts.firstOrNull { a -> a.id == it.accountId }?.name ?: ""), -it.amount) }
        }
    }
}

@Composable
private fun WhyLine(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = P.ink)
        Text(LocalMoney.current.signed(value), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun MoveLine(title: String, sub: String, value: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = P.ink, maxLines = 1)
            Text(sub, fontSize = 12.sp, color = P.muted)
        }
        Text(LocalMoney.current.signed(value), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = gainColor(value))
    }
}
