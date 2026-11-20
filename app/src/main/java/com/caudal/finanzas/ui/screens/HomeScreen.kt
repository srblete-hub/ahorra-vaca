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
            Box(Modifier.clip(CircleShape).background(P.glass).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(month.format(monthFmt).replaceFirstChar { it.titlecase(es) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
            }
        }
        if (month < YearMonth.now()) RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente", onClick = { onShift(1) })
        else Spacer(Modifier.size(40.dp))
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
                // El logo real del comercio (Alcampo, Zara…) y, si no se encuentra, el icono de su tipo.
                if (cat?.isSavings == true) IconTile(categoryIcon(cat), SeriesColors[1]) else com.caudal.finanzas.ui.MerchantLogo(e.note, t, 40.dp)
                if (e.fromCard) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(P.card).padding(2.dp).clip(CircleShape).background(P.brand),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CreditCard, null, Modifier.size(8.dp), tint = P.onBrand) }
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
    /** Desde un atajo del icono: "pepe" abre el chat, "subs" las suscripciones. */
    request: String? = null,
    onRequestDone: () -> Unit = {},
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
    var buyCheck by remember { mutableStateOf<com.caudal.finanzas.domain.BuyCheck?>(null) }
    var pepeAsk by remember { mutableStateOf<String?>(null) }
    var subsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshIfStale() }
    LaunchedEffect(request, planning != null) {
        if (planning == null) return@LaunchedEffect
        when (request) { "pepe" -> pepeAsk = ""; "subs" -> subsOpen = true; else -> return@LaunchedEffect }
        onRequestDone()
    }

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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(HomeScroll.index, HomeScroll.offset)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { HomeScroll.index = listState.firstVisibleItemIndex; HomeScroll.offset = listState.firstVisibleItemScrollOffset } }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = args.contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                args.headerActions()
            }
        }
        item {
            Column(pad.padding(top = 8.dp, bottom = 2.dp)) {
                Text(
                    if (settings.userName.isNotBlank()) "Hola, ${settings.userName}" else greeting,
                    fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, fontFamily = com.caudal.finanzas.ui.SerifDisplay, letterSpacing = (-1).sp, color = P.ink,
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
                com.caudal.finanzas.ui.SpottedCard {
                    Text("En el banco", fontSize = 18.sp, color = P.muted)
                    Spacer(Modifier.height(8.dp))
                    AnimatedAmount(wealth.available, 48.sp, P.ink)
                    // Lo que te queda hoy, en la píldora de pasto de la marca.
                    p?.let { pl -> com.caudal.finanzas.domain.TodayLeft.of(pl.data, com.caudal.finanzas.domain.CashPlanner.of(pl.data, settings.bankBuffer)) }?.let { (_, left) ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (left >= 0) "Hoy te quedan ${money.format(left)}" else "Hoy te has pasado ${money.format(-left)}",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (left >= 0) com.caudal.finanzas.ui.Vaca.PradoOscuro else com.caudal.finanzas.ui.Vaca.UbreFuerte,
                            modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (left >= 0) com.caudal.finanzas.ui.Vaca.Pasto else com.caudal.finanzas.ui.Vaca.UbreSuave)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    StackedBar(bankAccounts.mapIndexed { i, a -> Slice(a.name, a.balance, SeriesColors[i % SeriesColors.size]) }, height = 12.dp)
                    Spacer(Modifier.height(16.dp))
                    // Lo ahorrado e invertido se suma aparte: no es dinero del día a día.
                    AddOnRow("+ Ahorrado", wealth.savings, SavingsColor) { onOpenTab(Tab.WEALTH) }
                    AddOnRow("+ Invertido", wealth.invested, InvestedColor) { onOpenTab(Tab.INVEST) }
                    if (wealth.debts > 0) AddOnRow("− Deudas", -wealth.debts, P.loss) { onOpenTab(Tab.WEALTH) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = P.line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Patrimonio total", Modifier.weight(1f), fontSize = 16.sp, color = P.muted)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money.format(wealth.total), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                            if (change != null && kotlin.math.abs(change) >= 0.01) Text(
                                (if (change >= 0) "↑ " else "↓ ") + money.signed(change) + " este mes",
                                fontSize = 14.sp, color = gainColor(change),
                            )
                        }
                    }
                }
            }

            // ---------- Modo viaje: lo que te queda del presupuesto del viaje ----------
            if (p != null) com.caudal.finanzas.domain.Trips.active(p.data.today)?.let { t ->
                item(key = "trip") { TripCard(com.caudal.finanzas.domain.Trips.status(p.data, t), onEnd = { vm.endTrip() }) }
            }
            // ---------- Últimos movimientos, de todas las cuentas ----------
            item {
                RecentMovesCard(
                    expenses = allExpensesList, incomes = allIncomes, transfers = transfers,
                    accounts = accounts, onEdit = args.onEdit, onOpenAccount = args.onOpenAccount,
                )
            }

            // ---------- Suscripciones, justo debajo de los movimientos ----------
            if (p != null) {
                val subs = com.caudal.finanzas.domain.BillReminders.upcoming(p.data, p.data.bills.filter(com.caudal.finanzas.domain.BillReminders::isSubscription))
                val pendingSubs = com.caudal.finanzas.domain.SubscriptionChecker.of(p.data)?.actions?.count { it.key !in settings.reviewedSubs } ?: 0
                item(key = "subs-check") { SubscriptionsCard(subs, p.data.today, pendingSubs) { subsOpen = true } }
            }

            // ---------- Revolut: ¿en qué te gastaste lo que pasaste? ----------
            if (p != null) com.caudal.finanzas.domain.RevolutAsk.pending(p.data).firstOrNull()?.let { e ->
                item(key = "revolut-${e.id}") {
                    RevolutAskCard(
                        e, onPick = { tag -> vm.assignRevolut(e, tag) },
                        onTransfer = { args.onEdit(Editing.ConvertTransfer(e)) },
                    )
                }
            }

            // ---------- Pepe ----------
            if (p != null) item {
                val pepeTip = remember(p, settings.bankBuffer) {
                    com.caudal.finanzas.domain.PepeBot.tips(
                        com.caudal.finanzas.domain.PepeBot.Ctx(p.data, com.caudal.finanzas.domain.CashPlanner.of(p.data, settings.bankBuffer), p.goals.firstOrNull(), settings.userName, money::format),
                    ).firstOrNull()
                }
                PepeCard(pepeTip) { q -> pepeAsk = q }
            }

            // ---------- Edad financiera, debajo de Pepe ----------
            item { FinancialAgeMini(p?.age) { args.onOpen(Overlay.AGE) } }

            // ---------- Panel: gasto de la semana y ritmo del mes ----------
            if (p != null) {
                val pulse = com.caudal.finanzas.domain.PulseCalculator.of(p.data, p.budget, p.bankPlan, money::format)
                val typical = com.caudal.finanzas.domain.TypicalSpend.of(p.data)
                item { PulseHeader(pulse, typical) { if (it == "rhythm") onOpenTab(Tab.BUDGET) else pulseInfo = it } }
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

            // ---------- Tus límites por categoría (ropa 100 €/mes…) ----------
            if (p != null) p.budget?.let { b ->
                item(key = "tag-limits") { TagLimitsCard(b.items, onEdit = { args.onEdit(Editing.TagLimitForm(it)) }) }
            }

            // ---------- Recibos que pasan en los próximos días ----------
            if (p != null) com.caudal.finanzas.domain.UpcomingBills.of(p.data).takeIf { it.isNotEmpty() }?.let { up ->
                val bank = p.data.accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK }.sumOf { it.balance }
                item(key = "upcoming-bills") { UpcomingBillsCard(up, bank, p.data.today) }
            }

            // Pepe te recomienda pasar lo que sobra por encima del colchón a tu ahorro.
            if (p != null) com.caudal.finanzas.domain.CashPlanner.of(p.data, settings.bankBuffer)?.let { c ->
                val tip = com.caudal.finanzas.domain.PepeSuggest.of(p.data, c, p.goals.firstOrNull(), money::format)
                if (tip != null && settings.weeklyTip && settings.pepeTipHiddenWeek != com.caudal.finanzas.domain.PepeSuggest.weekKey(LocalDate.now())) item(key = "pepe-tip") {
                    PepeTipCard(tip, onMove = { vm.moveMoney(tip.fromId, tip.toId, tip.amount, "Pepe: al ahorro") }, onLater = { vm.hidePepeTip() })
                }
            }
            // Lectura semanal: en qué se te fue el dinero sin darte cuenta.
            if (p != null) com.caudal.finanzas.domain.WeeklyReader.of(p.data, money::format)?.let { r ->
                item(key = "weekly-read") { WeeklyReadCard(r, onLimit = { args.onEdit(Editing.TagLimitForm(it)) }, onDetails = { args.onEdit(Editing.TagDetail(it)) }) }
            }
            // Hucha de redondeo: los céntimos de la semana pasada, al ahorro.
            if (p != null) {
                val week = com.caudal.finanzas.domain.RoundUp.lastWeek(p.data.today).toEpochDay()
                val round = com.caudal.finanzas.domain.RoundUp.of(p.data)
                val bankId = p.data.accounts.firstOrNull { it.type == com.caudal.finanzas.data.AccountType.BANK }?.id
                val saveId = p.data.accounts.filter { it.isSaving() }.maxByOrNull { it.balance }?.id
                if (round >= 0.5 && settings.roundUpWeek != week && bankId != null && saveId != null) item(key = "round-up") {
                    RoundUpCard(
                        round, transfers.filter { it.note == "Hucha de redondeo" }.sumOf { it.amount },
                        onMove = { vm.moveRoundUp(bankId, saveId, round, week) }, onSkip = { vm.skipRoundUp(week) },
                    )
                }
            }
            // Retos de la vaca.
            if (p != null) {
                val perDay = com.caudal.finanzas.domain.CashPlanner.of(p.data, settings.bankBuffer)?.perDayBefore ?: 0.0
                val active = settings.challengeRaw.split('|').takeIf { it.size == 2 }?.let { (id, day) ->
                    com.caudal.finanzas.domain.Challenges.byId(id)?.let { c ->
                        day.toLongOrNull()?.let { com.caudal.finanzas.domain.Challenges.status(p.data, c, LocalDate.ofEpochDay(it), perDay) }
                    }
                }
                val litres = settings.challengesDone.split(';').mapNotNull { com.caudal.finanzas.domain.Challenges.byId(it.substringBefore('|'))?.litres }.sum()
                item(key = "challenges") {
                    if (active?.done == true) LaunchedEffect(settings.challengeRaw) { vm.completeChallenge(active.challenge) }
                    ChallengeCard(
                        active?.takeIf { !it.done }, com.caudal.finanzas.domain.Challenges.suggest(p.data), litres,
                        onStart = { vm.startChallenge(it.id) }, onDrop = { vm.dropChallenge() },
                    )
                }
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

            // ---------- Atajos: ¿me lo compro? y análisis ----------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShortcutCard("¿Me lo compro?", "Mira cómo te afecta", Icons.Filled.ShoppingCart, SeriesColors[4], Modifier.weight(1f)) { args.onOpen(Overlay.PURCHASE) }
                    ShortcutCard("Análisis", "Salud, fugas, hábitos…", Icons.Filled.Insights, SeriesColors[1], Modifier.weight(1f)) { args.onOpen(Overlay.INSIGHTS) }
                }
            }

            // ---------- Tu futuro ----------
            if (p != null) item { FutureMiniCard(p.forecast.points.take(31), onOpenFuture) }
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
                    ) { Icon(Icons.Filled.CreditCard, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Activar", fontWeight = FontWeight.Bold) }
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
    }
    // La hoja de "¿Lo compro?" fuera de la lista: así se abre aunque la tarjeta quede abajo.
    buyCheck?.let { c -> BuyCheckSheet(c) { buyCheck = null } }
    pepeAsk?.let { q ->
        p?.let { pl ->
            PepeChat(
                com.caudal.finanzas.domain.PepeBot.Ctx(pl.data, com.caudal.finanzas.domain.CashPlanner.of(pl.data, settings.bankBuffer), pl.goals.firstOrNull(), settings.userName, money::format),
                q.takeIf { it.isNotBlank() },
            ) { pepeAsk = null }
        }
    }
    if (subsOpen) p?.data?.let { d ->
        val subs = com.caudal.finanzas.domain.BillReminders.upcoming(d, d.bills.filter(com.caudal.finanzas.domain.BillReminders::isSubscription))
        val known = d.bills.map { com.caudal.finanzas.domain.Classifier.normalize(it.name) }
        val detected = com.caudal.finanzas.domain.RecurringDetector.find(d).filter { !it.fromBills && it.kind == "Suscripción" && com.caudal.finanzas.domain.Classifier.normalize(it.name) !in known }
        val subsCategory = com.caudal.finanzas.domain.Classifier.bucketFor(com.caudal.finanzas.domain.SpendTag.SUBSCRIPTIONS, categories)
            ?: categories.firstOrNull { !it.isSavings }?.id ?: 0L
        val bankId = accounts.firstOrNull { it.id == settings.cardAccountId }?.id ?: accounts.firstOrNull { it.type == com.caudal.finanzas.data.AccountType.BANK }?.id
        SubscriptionsSheet(
            subs, detected, com.caudal.finanzas.domain.SubscriptionChecker.of(d), settings.reviewedSubs, settings.billRemindDays, d.today,
            onRemindDays = { vm.setBillRemindDays(it) },
            onEdit = { subsOpen = false; args.onEdit(Editing.BillForm(it)) },
            onFollow = { r ->
                vm.save(com.caudal.finanzas.data.RecurringBill(name = r.name, amount = r.last, day = r.lastDate.dayOfMonth, categoryId = subsCategory, accountId = bankId))
            },
            onAdd = { item ->
                subsOpen = false
                args.onEdit(Editing.BillForm(com.caudal.finanzas.data.RecurringBill(
                    name = item?.name ?: "", amount = item?.price ?: 0.0, day = d.today.dayOfMonth, categoryId = subsCategory, accountId = bankId,
                )))
            },
            onToggle = { vm.toggleReviewedSub(it) },
            onDismiss = { subsOpen = false },
        )
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
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Últimos movimientos", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("De todas tus cuentas", fontSize = 14.sp, color = P.muted)
            }
            if (moves.size > 6) Text(
                if (showAll) "Ver menos" else "Ver más", fontSize = 16.sp, color = P.gain, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).clickable { showAll = !showAll }.padding(8.dp),
            )
        }
        if (moves.isEmpty()) {
            Text("Aún no hay movimientos. Los pagos con tarjeta aparecen aquí solos; los demás, con el +.", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(16.dp))
        }
        moves.take(if (showAll) 25 else 6).forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 80.dp), color = P.line, thickness = 0.6.dp)
            when (m) {
                is Move.Out -> {
                    val t = m.e.spendTag()
                    val acc = name(m.e.accountId)
                    ListRow(
                        m.e.note.ifBlank { t.label },
                        subtitle = listOfNotNull(dayLabel(m.e.epochDay), acc?.let { "de $it" } ?: "sin cuenta", if (m.e.fromCard) "tarjeta" else null).joinToString(" · "),
                        onClick = { onEdit(Editing.ExpenseForm(m.e)) },
                        leading = { com.caudal.finanzas.ui.MerchantLogo(m.e.note, t, 40.dp) },
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
        Spacer(Modifier.height(8.dp))
    }
}

/** "+ Ahorrado 9.140 €": lo que se suma al dinero del banco. */
@Composable
private fun AddOnRow(label: String, value: Double, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = P.ink)
        Text(LocalMoney.current.format(kotlin.math.abs(value)), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = P.muted)
    }
}

@Composable
fun ShortcutCard(title: String, sub: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    MCard(modifier, onClick = onClick, padding = 16.dp) {
        IconTile(icon, color, 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 1)
        Text(sub, fontSize = 14.sp, color = P.muted, maxLines = 1)
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
            leading = { Initials(h.symbol, symbolColor(h.symbol), 48.dp) },
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
        Spacer(Modifier.height(8.dp))
        AnimatedAmount(s.safe.coerceAtLeast(0.0), 36.sp, if (s.safe > 0) P.gain else P.loss)
        Text(
            if (s.safe <= 0) "Lo que tienes está comprometido hasta tu próximo cobro"
            else "≈ ${money.format(s.perDay)} al día" + (s.nextPayday?.let { " · faltan ${s.daysToPayday} días para tu cobro del ${prettyDate(it).lowercase()}" } ?: " hasta fin de mes"),
            style = MaterialTheme.typography.bodySmall, color = P.muted,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = P.line)
        Spacer(Modifier.height(8.dp))
        BreakdownRow("Disponible en cuentas", money.format(s.available), P.ink)
        BreakdownRow("Gastos fijos pendientes", (if (s.pendingBills > 0) "-" else "") + money.format(s.pendingBills), if (s.pendingBills > 0) P.loss else P.muted)
        BreakdownRow("Reservado para ahorrar", (if (s.savingsReserve > 0) "-" else "") + money.format(s.savingsReserve), if (s.savingsReserve > 0) SeriesColors[1] else P.muted)
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
        Spacer(Modifier.height(8.dp))
        LineChart(points.map { it.second }, if (negative != null) P.loss else P.gain, Modifier.fillMaxWidth().height(72.dp))
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

/** Dónde estabas en el Inicio, para volver al mismo sitio al cerrar otra pantalla (mientras la app está abierta). */
private object HomeScroll { var index = 0; var offset = 0 }
