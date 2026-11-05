package com.caudal.finanzas.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.domain.Achievements
import com.caudal.finanzas.domain.isSaving
import com.caudal.finanzas.domain.isDebt
import com.caudal.finanzas.domain.signedBalance
import com.caudal.finanzas.ui.BankPlanCard
import com.caudal.finanzas.ui.color
import com.caudal.finanzas.ui.icon
import com.caudal.finanzas.domain.spendTag
import com.caudal.finanzas.ui.Overlay
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.caudal.finanzas.ui.AccountLogo
import com.caudal.finanzas.ui.AmountTrailing
import com.caudal.finanzas.ui.AnimatedAmount
import com.caudal.finanzas.ui.BrandCard
import com.caudal.finanzas.ui.CardList
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.EmptyState
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.Initials
import com.caudal.finanzas.ui.LineChart
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.ProgressLine
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.ScreenArgs
import com.caudal.finanzas.ui.SectionTitle
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.Slice
import com.caudal.finanzas.ui.StackedBar
import com.caudal.finanzas.ui.Tab
import com.caudal.finanzas.ui.categoryColor
import com.caudal.finanzas.ui.categoryIcon
import com.caudal.finanzas.ui.gainColor
import com.caudal.finanzas.ui.pct
import com.caudal.finanzas.ui.signedPct
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")
private val monthFmt = DateTimeFormatter.ofPattern("LLLL 'de' yyyy", es)
private val shortDayFmt = DateTimeFormatter.ofPattern("EEE d MMM", es)

fun dayLabel(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (d) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> d.format(shortDayFmt)
    }
}

@Composable
fun MonthSelector(month: YearMonth, onShift: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior", onClick = { onShift(-1) })
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.clip(CircleShape).background(P.glass).padding(horizontal = 18.dp, vertical = 9.dp)) {
                Text(month.format(monthFmt).replaceFirstChar { it.titlecase(es) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
            }
        }
        if (month < YearMonth.now()) RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente", onClick = { onShift(1) })
        else Spacer(Modifier.size(42.dp))
    }
}

/** Fila de un pago: icono de categoría, comercio, cuándo y de dónde salió. */
@Composable
fun ExpenseRow(e: Expense, categories: List<Category>, accounts: List<Account>, onClick: () -> Unit) {
    val cat = categories.firstOrNull { it.id == e.categoryId }
    val account = accounts.firstOrNull { it.id == e.accountId }
    ListRow(
        title = e.note.ifBlank { cat?.name ?: "Gasto" },
        subtitle = listOfNotNull(dayLabel(e.epochDay), e.spendTag().label, if (e.fromCard) "Tarjeta" else null, if (e.billId != null) "Gasto fijo" else null).joinToString(" · "),
        onClick = onClick,
        leading = {
            Box {
                val t = e.spendTag()
                if (cat?.isSavings == true) IconTile(categoryIcon(cat), SeriesColors[1]) else IconTile(t.icon(), t.color())
                if (e.fromCard) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(P.card).padding(2.dp).clip(CircleShape).background(P.brand),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CreditCard, null, Modifier.size(10.dp), tint = P.onBrand) }
                }
            }
        },
        trailing = { AmountTrailing("-" + LocalMoney.current.format(e.amount), account?.name) },
    )
}

@Composable
fun IncomeRow(i: com.caudal.finanzas.data.Income, accounts: List<Account>, onClick: () -> Unit) {
    val interest = i.sourceId == null
    ListRow(
        title = i.note.ifBlank { "Ingreso" },
        subtitle = listOf(dayLabel(i.epochDay), if (interest) "Intereses" else "Ingreso").joinToString(" · "),
        onClick = onClick,
        leading = { IconTile(if (interest) Icons.Filled.Savings else Icons.Filled.Work, P.gain) },
        trailing = { AmountTrailing("+" + LocalMoney.current.format(i.amount), accounts.firstOrNull { it.id == i.accountId }?.name, color = P.gain) },
    )
}

private enum class Filter(val label: String) { ALL("Todo"), AVAILABLE("Disponible"), SAVINGS("Ahorro"), INVESTED("Invertido") }

@Composable
fun HomeScreen(
    vm: FinanceViewModel,
    settings: Settings,
    args: ScreenArgs,
    onOpenSettings: () -> Unit,
    onStartSetup: () -> Unit,
    onOpenFuture: () -> Unit,
    onOpenTab: (Tab) -> Unit,
    onOpenInvest: (InvestKind) -> Unit,
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val expenses by vm.monthExpenses.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val monthIncome by vm.monthIncome.collectAsStateWithLifecycle()
    val allIncomes by vm.incomes.collectAsStateWithLifecycle()
    val crypto by vm.crypto.collectAsStateWithLifecycle()
    val stocks by vm.stocks.collectAsStateWithLifecycle()
    val snapshots by vm.snapshots.collectAsStateWithLifecycle()
    val planning by vm.planning.collectAsStateWithLifecycle()
    val allExpensesList by vm.allExpenses.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var filterMenu by remember { mutableStateOf(false) }
    var pulseInfo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshIfStale() }

    val hour = LocalTime.now().hour
    val greeting = when (hour) { in 6..13 -> "Buenos días"; in 14..20 -> "Buenas tardes"; else -> "Buenas noches" }
    val savingsIds = categories.filter { it.isSavings }.map { it.id }.toSet()
    val spent = expenses.filter { it.categoryId !in savingsIds }.sumOf { it.amount }
    val income = monthIncome.total
    val allowed = income * categories.filter { !it.isSavings }.sumOf { it.percent } / 100
    val isCurrent = month == YearMonth.now()
    val p = planning
    val wealth = p?.wealth ?: com.caudal.finanzas.domain.Wealth.of(accounts, crypto, stocks)
    val hasMoney = accounts.isNotEmpty() || crypto.isNotEmpty() || stocks.isNotEmpty()
    LaunchedEffect(wealth.total, hasMoney) { if (hasMoney) vm.recordNetWorth(wealth.total) }

    val monthIncomes = allIncomes.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
    val moves: List<Any> = (expenses.map { it as Any } + monthIncomes).sortedWith(
        compareByDescending<Any> { if (it is Expense) it.epochDay else (it as com.caudal.finanzas.data.Income).epochDay }
            .thenByDescending { if (it is Expense) it.createdAt else (it as com.caudal.finanzas.data.Income).createdAt },
    )
    val nextIncome = if (isCurrent) monthIncome.sources.filter { !it.isReceived }.minByOrNull { it.source.dayFrom } else null
    val detected = expenses.firstOrNull { it.fromCard && System.currentTimeMillis() - it.createdAt < 24 * 3600_000L }
    val pad = Modifier.padding(horizontal = 0.dp)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = args.contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                args.headerActions()
            }
        }
        item {
            Column(pad.padding(top = 10.dp, bottom = 2.dp)) {
                Text(
                    if (settings.userName.isNotBlank()) "Hola, ${settings.userName}" else greeting,
                    fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = P.ink,
                )
            }
        }

        if (!hasMoney) {
            item { SetupCard(args.onEdit, onStartSetup) }
        } else {
            // ---------- Patrimonio total ----------
            item {
                val bankAccounts = accounts.filter { !it.isSaving() && !it.isDebt() }
                val change = changeThisMonth(snapshots, wealth.total)
                MintCard {
                    Text("En el banco", fontSize = 18.sp, color = P.ink.copy(alpha = 0.85f))
                    Spacer(Modifier.height(6.dp))
                    AnimatedAmount(wealth.available, 44.sp, P.ink)
                    Spacer(Modifier.height(14.dp))
                    StackedBar(bankAccounts.mapIndexed { i, a -> Slice(a.name, a.balance, SeriesColors[i % SeriesColors.size]) }, height = 11.dp)
                    Spacer(Modifier.height(16.dp))
                    // Lo ahorrado e invertido se suma aparte: no es dinero del día a día.
                    AddOnRow("+ Ahorrado", wealth.savings, SavingsColor) { onOpenTab(Tab.WEALTH) }
                    AddOnRow("+ Invertido", wealth.invested, InvestedColor) { onOpenTab(Tab.INVEST) }
                    if (wealth.debts > 0) AddOnRow("− Deudas", -wealth.debts, P.loss) { onOpenTab(Tab.WEALTH) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = P.line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Patrimonio total", Modifier.weight(1f), fontSize = 16.sp, color = P.muted)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money.format(wealth.total), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                            if (change != null && kotlin.math.abs(change) >= 0.01) Text(
                                (if (change >= 0) "↑ " else "↓ ") + money.signed(change) + " este mes",
                                fontSize = 13.sp, color = gainColor(change),
                            )
                        }
                    }
                }
            }

            // ---------- Panel: colchón, margen y gasto de la semana ----------
            if (p != null) {
                val pulse = com.caudal.finanzas.domain.PulseCalculator.of(p.data, p.budget, p.bankPlan, money::format)
                item { PulseHeader(pulse) { pulseInfo = it } }
                pulseInfo?.let { key ->
                    item(key = "pulse-info") { PulseInfoSheet(key, pulse) { pulseInfo = null } }
                }
                // Avisos de límite (los mismos que llegan como notificación).
                val len = YearMonth.now().lengthOfMonth()
                val alerts = p.budget?.let { b ->
                    com.caudal.finanzas.domain.LimitAlerts.check(
                        LocalDate.now(), p.data.expenses, p.data.bills, p.data.savingIds,
                        com.caudal.finanzas.domain.LimitSnapshot(p.bankPlan?.perDay ?: p.safe.perDay, b.base / len * 7, b.items.filter { it.monthly >= 20 }.associate { it.tag to it.monthly }),
                        money::format,
                    )
                }.orEmpty()
                alerts.forEach { a ->
                    item(key = "alert-${a.key}") {
                        MCard(color = (if (a.severe) P.loss else Color(0xFFFF9F0A)).copy(alpha = 0.12f), padding = 16.dp) {
                            Text(a.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (a.severe) P.loss else P.ink)
                            Text(a.text, fontSize = 14.sp, color = P.ink)
                        }
                    }
                }
            }

            // ---------- Últimos movimientos, de todas las cuentas ----------
            item {
                RecentMovesCard(
                    expenses = allExpensesList, incomes = allIncomes, transfers = transfers,
                    accounts = accounts, onEdit = args.onEdit, onOpenAccount = args.onOpenAccount,
                )
            }

            // ---------- Ingresos variables: ¿ya sabes cuánto te pagan este mes? ----------
            if (p != null && LocalDate.now().dayOfMonth >= 3) {
                val m = java.time.YearMonth.now()
                p.data.sources.filter { s ->
                    s.variable && com.caudal.finanzas.domain.IncomeMath.confirmedFor(s, m) == null && LocalDate.now().dayOfMonth <= s.dayTo &&
                        allIncomes.none { it.sourceId == s.id && java.time.YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == m }
                }.forEach { s ->
                    item { ExpectedIncomeNudge(s) { args.onEdit(Editing.ExpectedIncome(s, m)) } }
                }
            }

            // ---------- Algo distinto a lo normal ----------
            if (p != null) {
                val anomalies = com.caudal.finanzas.domain.Anomalies.find(p.data, money::format)
                if (anomalies.isNotEmpty()) item { AnomaliesCard(anomalies) { a -> a.expense?.let { args.onEdit(Editing.ExpenseForm(it)) } } }
            }

            // ---------- Tu reparto: banco − alquiler → % al ahorro → para gastar ----------
            p?.bankPlan?.let { plan ->
                item {
                    BankPlanCard(
                        plan,
                        onBuffer = { vm.setBankBuffer(it) },
                        onMove = { vm.moveToSavings(plan) },
                        onSettings = { args.onEdit(Editing.BankPlanForm) },
                        onAddLateIncome = {
                            args.onEdit(
                                Editing.IncomeSourceForm(
                                    com.caudal.finanzas.data.IncomeSource(name = "Resto de nómina", variable = false, amount = 250.0, dayFrom = 20, dayTo = 25, accountId = plan.main.id),
                                ),
                            )
                        },
                    )
                }
            }

            // ---------- Cuánto meter en Robinhood y en qué gastar ----------
            if (p != null) {
                val sa = if (p.bankPlan?.target == null) com.caudal.finanzas.domain.SavingsAdvisor.advise(p.data, p.goal, money::format, p.bankPlan) else null
                if (sa != null) item {
                    val plan = p.bankPlan
                    SavingsAdviceCard(
                        sa, plan?.target?.name, plan?.saveGoal?.takeIf { plan.target != null },
                        onApply = if (plan?.target != null && plan.remainder + plan.movedThisMonth > 0) ({
                            val pct = Math.ceil(sa.recommended / (plan.remainder + plan.movedThisMonth) * 100).coerceIn(0.0, 100.0)
                            vm.setBankPlan(plan.main.id, plan.target.id, pct)
                        }) else null,
                    )
                }
                p.budget?.takeIf { it.base > 0 }?.let { b -> item { BudgetWheelCard(b, onProfile = { args.onEdit(Editing.ProfileForm) }) { args.onEdit(Editing.TagLimitForm(it)) } } }
            }

            // ---------- Nómina, recibos y cambios ----------
            if (p != null) {
                com.caudal.finanzas.domain.PaycheckDistributor.split(p.data, p.goal, money::format)?.let { s -> item { PaycheckSplitCard(s) } }
                com.caudal.finanzas.domain.PaymentOrganizer.analyze(p.data, p.bankPlan, money::format)?.takeIf { it.crunch }?.let { c -> item { BillCalendarCard(c) } }
                val trends = com.caudal.finanzas.domain.TrendDetector.find(p.data, money::format)
                if (trends.isNotEmpty()) item { TrendsCard(trends) }
            }

            // ---------- Objetivo y edad financiera ----------
            item {
                GoalsSection(
                    p?.goals ?: emptyList(),
                    swapText = p?.let { remember(it, money) { com.caudal.finanzas.domain.GoalList.swapImpact(it.goalList, it.data, it.bankPlan, money::format) } },
                    onAdd = { args.onEdit(Editing.GoalForm()) },
                    onEdit = { args.onEdit(Editing.GoalForm(it)) },
                    onMove = { i, by -> vm.moveGoal(i, by) },
                    onApplyPct = null,
                )
            }
            item { FinancialAgeMini(p?.age) { args.onOpen(Overlay.AGE) } }

            // ---------- Análisis y chat ----------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShortcutCard("¿Me lo compro?", "Mira cómo te afecta", Icons.Filled.ShoppingCart, SeriesColors[4], Modifier.weight(1f)) { args.onOpen(Overlay.PURCHASE) }
                    ShortcutCard("Análisis", "Salud, fugas, hábitos…", Icons.Filled.Insights, SeriesColors[1], Modifier.weight(1f)) { args.onOpen(Overlay.INSIGHTS) }
                }
            }

            // ---------- Cuentas ----------
            item { SectionTitle("Cuentas") }
            item {
                val total = wealth.total.takeIf { it > 0 } ?: 1.0
                val rows = accounts.map { a ->
                    Triple<@Composable () -> Unit, Pair<String, String>, Pair<Double, () -> Unit>>(
                        { AccountLogo(a.name, 46.dp) },
                        a.name to (if (a.interestRate > 0) "${pct(a.interestRate)} TAE · ahora +${money.format(com.caudal.finanzas.domain.InterestMath.monthlyInterest(a.balance, a.interestRate))}/mes" else a.type.label),
                        a.signedBalance() to { args.onOpenAccount(a) },
                    )
                } + listOfNotNull(
                    if (crypto.isNotEmpty()) Triple<@Composable () -> Unit, Pair<String, String>, Pair<Double, () -> Unit>>(
                        { Initials("₿", Color(0xFFF7931A), 44.dp) },
                        "Criptomonedas" to "${crypto.size} monedas",
                        crypto.sumOf { it.amount * it.price } to { onOpenInvest(InvestKind.CRYPTO) },
                    ) else null,
                    if (stocks.isNotEmpty()) Triple<@Composable () -> Unit, Pair<String, String>, Pair<Double, () -> Unit>>(
                        { Initials("↗", Color(0xFFBF5AF2), 44.dp) },
                        "Acciones y ETF" to "${stocks.size} posiciones",
                        stocks.sumOf { it.shares * it.price } to { onOpenInvest(InvestKind.STOCKS) },
                    ) else null,
                )
                CardList(rows) { (logo, texts, data) ->
                    ListRow(
                        texts.first, subtitle = texts.second, onClick = data.second, leading = logo,
                        trailing = { AmountTrailing(money.format(data.first), if (data.first >= 0) pct(data.first / total * 100) else "deuda", color = if (data.first < 0) P.loss else P.ink) },
                    )
                }
            }
            item {
                MCard(onClick = { onOpenTab(Tab.WEALTH) }, padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ver gráficos", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = P.ink)
                            Text("Por cuenta y evolución de tu patrimonio", fontSize = 14.sp, color = P.muted)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = P.muted)
                    }
                }
            }

            // ---------- Tus criptomonedas y acciones ----------
            if (crypto.isNotEmpty()) {
                item { SectionTitle("Tus criptomonedas") }
                item { HoldingsList(crypto.map { Holding(it.symbol, it.name, it.amount, it.avgBuyPrice, it.price) }) { onOpenInvest(InvestKind.CRYPTO) } }
            }
            if (stocks.isNotEmpty()) {
                item { SectionTitle("Tus acciones") }
                item { HoldingsList(stocks.map { Holding(it.ticker, it.name.ifBlank { it.ticker }, it.shares, it.avgBuyPrice, it.price) }) { onOpenInvest(InvestKind.STOCKS) } }
            }

            // ---------- Puedes gastar hoy + futuro ----------
            if (p != null) {
                if (p.bankPlan == null) item { SafeToSpendCard(p.safe) }
                item { FutureMiniCard(p.forecast.points.take(31), onOpenFuture) }
            }

            // ---------- Tus ahorros trabajando ----------
            item {
                MoneyWorkingSection(
                    accounts, wealth.available,
                    suggestedMonthly(income, categories.filter { it.isSavings }.sumOf { it.percent }),
                    args.onEdit,
                )
            }

            if (p != null) {
                item { WhereDidItGoCard(p.comparison) }
                item {
                    val level = p.level
                    val next = p.achievements.filter { !it.unlocked }.maxByOrNull { it.progress }
                    MCard(onClick = { onOpenTab(Tab.PROFILE) }, padding = 18.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(SeriesColors[3].copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Text("${level.number}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = SeriesColors[3])
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Nivel ${level.number} · ${level.name}", style = MaterialTheme.typography.titleMedium, color = P.ink)
                                Text(
                                    "${p.achievements.count { it.unlocked }} de ${p.achievements.size} logros" + (next?.let { " · siguiente: ${it.title}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = P.muted, maxLines = 1,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = P.muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        ProgressLine(level.progress(p.points), SeriesColors[3])
                    }
                }
            }
        }

        // ---------- Este mes ----------
        item { SectionTitle(if (isCurrent) "Este mes" else "Ese mes") }
        item { MonthSelector(month, vm::shiftMonth) }
        item {
            MonthFlowCard(
                received = monthIncome.received,
                spent = expenses.sumOf { it.amount },
                spendLimit = allowed.takeIf { income > 0 } ?: 0.0,
                estimated = monthIncome.sources.any { it.isEstimate },
            )
        }

        if (nextIncome != null) {
            item {
                MCard(padding = 0.dp) {
                    Overline("Próximo cobro", Modifier.padding(start = 16.dp, top = 14.dp))
                    IncomeSourceRow(nextIncome, month, accounts, args.onEdit)
                }
            }
        }

        if (!settings.cardPayments) {
            item {
                BrandCard {
                    Text(
                        buildAnnotatedString {
                            append("Pagas y se\n")
                            withStyle(SpanStyle(color = Color(0xFF34C77B))) { append("apunta solo.") }
                        },
                        style = MaterialTheme.typography.headlineMedium, color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Cada vez que pagues con tarjeta, Ahorra, vaca lee el aviso de Google Wallet o de tu banco, lo añade a tus movimientos y lo descuenta de tu cuenta.",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F3D27)),
                        shape = CircleShape,
                    ) { Icon(Icons.Filled.CreditCard, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Activar", fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (detected != null) {
            item {
                MCard(onClick = { args.onEdit(Editing.ExpenseForm(detected)) }, padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountLogo(detected.note, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Gasto detectado · ${detected.sourceApp}", style = MaterialTheme.typography.labelMedium, color = P.muted)
                            Text(
                                "${detected.note} · ${money.format(detected.amount)} · ${categories.firstOrNull { it.id == detected.categoryId }?.name ?: ""}",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink,
                            )
                        }
                        Text("Revisar", style = MaterialTheme.typography.labelLarge, color = P.gain)
                    }
                }
            }
        }

        item {
            SectionTitle("Movimientos") {
                if (moves.isNotEmpty()) Text("${moves.size}", style = MaterialTheme.typography.titleMedium, color = P.muted, modifier = Modifier.padding(end = 4.dp))
            }
        }
        item {
            if (moves.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Sin movimientos este mes", "Pulsa + para apuntar un gasto, o activa los pagos con tarjeta para que se apunten solos.")
            } else {
                CardList(moves, key = { if (it is Expense) "e${it.id}" else "i${(it as com.caudal.finanzas.data.Income).id}" }) { m ->
                    if (m is Expense) ExpenseRow(m, categories, accounts, onClick = { args.onEdit(Editing.ExpenseForm(m)) })
                    else IncomeRow(m as com.caudal.finanzas.data.Income, accounts, onClick = { args.onEdit(Editing.IncomeForm(m)) })
                }
            }
        }
    }
}

/** Un movimiento cualquiera: gasto, cobro o transferencia, con su fecha para ordenarlos. */
private sealed interface Move {
    val day: Long
    val created: Long
    data class Out(val e: Expense) : Move { override val day = e.epochDay; override val created = e.createdAt }
    data class In(val i: com.caudal.finanzas.data.Income) : Move { override val day = i.epochDay; override val created = i.createdAt }
    data class Moved(val t: com.caudal.finanzas.data.Transfer) : Move { override val day = t.epochDay; override val created = t.createdAt }
}

/** Nada más entrar: los últimos movimientos de todas tus cuentas y de dónde salen o adónde van. */
@Composable
private fun RecentMovesCard(
    expenses: List<Expense>,
    incomes: List<com.caudal.finanzas.data.Income>,
    transfers: List<com.caudal.finanzas.data.Transfer>,
    accounts: List<Account>,
    onEdit: (Editing) -> Unit,
    onOpenAccount: (Account) -> Unit,
) {
    val money = LocalMoney.current
    var showAll by remember { mutableStateOf(false) }
    val moves = remember(expenses, incomes, transfers) {
        (expenses.map { Move.Out(it) } + incomes.map { Move.In(it) } + transfers.map { Move.Moved(it) })
            .sortedWith(compareByDescending<Move> { it.day }.thenByDescending { it.created })
    }
    fun name(id: Long?) = accounts.firstOrNull { it.id == id }?.name
    MCard(padding = 0.dp) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Últimos movimientos", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("De todas tus cuentas", fontSize = 14.sp, color = P.muted)
            }
            if (moves.size > 6) Text(
                if (showAll) "Ver menos" else "Ver más", fontSize = 15.sp, color = P.gain, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).clickable { showAll = !showAll }.padding(8.dp),
            )
        }
        if (moves.isEmpty()) {
            Text("Aún no hay movimientos. Los pagos con tarjeta aparecen aquí solos; los demás, con el +.", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(18.dp))
        }
        moves.take(if (showAll) 25 else 6).forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = P.line, thickness = 0.6.dp)
            when (m) {
                is Move.Out -> {
                    val t = m.e.spendTag()
                    val acc = name(m.e.accountId)
                    ListRow(
                        m.e.note.ifBlank { t.label },
                        subtitle = listOfNotNull(dayLabel(m.e.epochDay), acc?.let { "de $it" } ?: "sin cuenta", if (m.e.fromCard) "tarjeta" else null).joinToString(" · "),
                        onClick = { onEdit(Editing.ExpenseForm(m.e)) },
                        leading = { IconTile(t.icon(), t.color()) },
                        trailing = { AmountTrailing("−" + money.format(m.e.amount), t.label) },
                    )
                }
                is Move.In -> {
                    val acc = name(m.i.accountId)
                    ListRow(
                        m.i.note.ifBlank { "Ingreso" },
                        subtitle = listOfNotNull(dayLabel(m.i.epochDay), acc?.let { "a $it" } ?: "ya estaba en el saldo").joinToString(" · "),
                        onClick = { onEdit(Editing.IncomeForm(m.i)) },
                        leading = { IconTile(Icons.Filled.Work, P.gain) },
                        trailing = { AmountTrailing("+" + money.format(m.i.amount), "Ingreso", color = P.gain) },
                    )
                }
                is Move.Moved -> {
                    val from = accounts.firstOrNull { it.id == m.t.fromAccountId }
                    ListRow(
                        m.t.note.ifBlank { "Transferencia" },
                        subtitle = "${dayLabel(m.t.epochDay)} · ${from?.name ?: "?"} → ${name(m.t.toAccountId) ?: "?"}",
                        onClick = { from?.let(onOpenAccount) },
                        leading = { IconTile(Icons.Filled.SwapHoriz, SeriesColors[1]) },
                        trailing = { AmountTrailing(money.format(m.t.amount), "Transferencia", color = SeriesColors[1]) },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** "+ Ahorrado 9.140 €": lo que se suma al dinero del banco. */
@Composable
private fun AddOnRow(label: String, value: Double, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = P.ink)
        Text(LocalMoney.current.format(kotlin.math.abs(value)), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = P.muted)
    }
}

@Composable
fun ShortcutCard(title: String, sub: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    MCard(modifier, onClick = onClick, padding = 16.dp) {
        IconTile(icon, color, 40.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 1)
        Text(sub, fontSize = 13.sp, color = P.muted, maxLines = 1)
    }
}

data class Holding(val symbol: String, val name: String, val qty: Double, val avg: Double, val price: Double) {
    val value get() = qty * price
    val pnlPct get() = if (avg > 0) (price - avg) / avg * 100 else null
}

/** Lista corta de posiciones: logo, nombre, cantidad, valor y cuánto ganas o pierdes. */
@Composable
fun HoldingsList(items: List<Holding>, onClick: () -> Unit) {
    val money = LocalMoney.current
    CardList(items.sortedByDescending { it.value }.take(5)) { h ->
        ListRow(
            h.name, subtitle = "${com.caudal.finanzas.ui.quantity(h.qty)} ${h.symbol}", onClick = onClick,
            leading = { Initials(h.symbol, symbolColor(h.symbol), 46.dp) },
            trailing = { AmountTrailing(money.format(h.value), h.pnlPct?.let { signedPct(it) }, subColor = gainColor(h.pnlPct ?: 0.0)) },
        )
    }
}

/** "Puedes gastar": lo disponible menos los gastos fijos que faltan y lo reservado para ahorrar. */
@Composable
fun SafeToSpendCard(s: com.caudal.finanzas.domain.SafeToSpend) {
    val money = LocalMoney.current
    MCard {
        Text("Puedes gastar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = P.muted)
        Spacer(Modifier.height(6.dp))
        AnimatedAmount(s.safe.coerceAtLeast(0.0), 36.sp, if (s.safe > 0) P.gain else P.loss)
        Text(
            if (s.safe <= 0) "Lo que tienes está comprometido hasta tu próximo cobro"
            else "≈ ${money.format(s.perDay)} al día" + (s.nextPayday?.let { " · faltan ${s.daysToPayday} días para tu cobro del ${prettyDate(it).lowercase()}" } ?: " hasta fin de mes"),
            style = MaterialTheme.typography.bodySmall, color = P.muted,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = P.line)
        Spacer(Modifier.height(10.dp))
        BreakdownRow("Disponible en cuentas", money.format(s.available), P.ink)
        BreakdownRow("Gastos fijos pendientes", (if (s.pendingBills > 0) "-" else "") + money.format(s.pendingBills), if (s.pendingBills > 0) P.loss else P.muted)
        BreakdownRow("Reservado para ahorrar", (if (s.savingsReserve > 0) "-" else "") + money.format(s.savingsReserve), if (s.savingsReserve > 0) SeriesColors[1] else P.muted)
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = P.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun FutureMiniCard(points: List<Pair<LocalDate, Double>>, onOpen: () -> Unit) {
    if (points.isEmpty()) return
    val money = LocalMoney.current
    val end = points.last()
    val negative = points.firstOrNull { it.second < 0 }
    MCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tu futuro", style = MaterialTheme.typography.titleMedium, color = P.ink)
                Text(
                    if (negative != null) "Ojo: ${prettyDate(negative.first).lowercase()} te quedarías en ${money.format(negative.second)}"
                    else "En 30 días tendrás ≈ ${money.format(end.second)} disponibles",
                    style = MaterialTheme.typography.bodySmall, color = if (negative != null) P.loss else P.muted,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = P.muted)
        }
        Spacer(Modifier.height(10.dp))
        LineChart(points.map { it.second }, if (negative != null) P.loss else P.gain, Modifier.fillMaxWidth().height(70.dp))
    }
}

/** Explica con palabras qué ha cambiado este mes frente al mismo tramo del anterior. */
@Composable
fun WhereDidItGoCard(c: com.caudal.finanzas.domain.MonthComparison) {
    if (c.spentPrev <= 0 && c.incomePrev <= 0) return
    val money = LocalMoney.current
    val diff = c.spentNow - c.spentPrev
    val ups = c.byCategory.filter { it.second > 1 }.take(3)
    val incomeDiff = c.incomeNow - c.incomePrev
    MCard {
        Overline("¿Dónde se ha ido tu dinero?")
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append(
                    when {
                        kotlin.math.abs(incomeDiff) < 1 -> "Has cobrado lo mismo que a estas alturas del mes pasado"
                        incomeDiff > 0 -> "Has cobrado ${money.format(incomeDiff)} más que a estas alturas del mes pasado"
                        else -> "Has cobrado ${money.format(-incomeDiff)} menos que a estas alturas del mes pasado"
                    },
                )
                append(
                    when {
                        kotlin.math.abs(diff) < 1 -> " y has gastado lo mismo."
                        diff > 0 -> ", pero has gastado ${money.format(diff)} más"
                        else -> " y has gastado ${money.format(-diff)} menos. ¡Bien!"
                    },
                )
                if (diff > 0 && ups.isNotEmpty()) append(": " + ups.joinToString(", ") { "${money.format(it.second)} en ${it.first.name.lowercase()}" } + ".")
                else if (diff > 0) append(".")
            },
            style = MaterialTheme.typography.bodyLarge, color = P.ink,
        )
    }
}
