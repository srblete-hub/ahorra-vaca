package com.caudal.finanzas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.CoinResult
import com.caudal.finanzas.data.CryptoHolding
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.NetWorthSnapshot
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.data.PriceService
import com.caudal.finanzas.data.StockHolding
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.RecurringBill
import com.caudal.finanzas.domain.Achievement
import com.caudal.finanzas.domain.AchievementInput
import com.caudal.finanzas.domain.Achievements
import com.caudal.finanzas.domain.Bills
import com.caudal.finanzas.domain.Comparison
import com.caudal.finanzas.domain.Forecast
import com.caudal.finanzas.domain.Forecasting
import com.caudal.finanzas.domain.IncomeMath
import com.caudal.finanzas.domain.InterestMath
import com.caudal.finanzas.domain.MonthComparison
import com.caudal.finanzas.domain.SafeToSpend
import com.caudal.finanzas.domain.Spending
import com.caudal.finanzas.domain.Wealth
import com.caudal.finanzas.domain.Classifier
import com.caudal.finanzas.domain.BankPlan
import com.caudal.finanzas.domain.BankPlanning
import com.caudal.finanzas.domain.FinanceData
import com.caudal.finanzas.domain.Insights
import com.caudal.finanzas.domain.ParsedExpense
import com.caudal.finanzas.data.Split
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Mes como número correlativo (año·12 + mes − 1), para contar meses de intereses. */
fun monthIndex(m: YearMonth) = m.year * 12 + m.monthValue - 1

class FinanceViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = FinanceDatabase.get(app).dao()
    private val prefs = Prefs.get(app)
    private val currency get() = prefs.settings.value.currency

    private fun <T> Flow<List<T>>.state() = stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> = dao.accounts().state()
    val categories: StateFlow<List<Category>> = dao.categories().state()
    val crypto: StateFlow<List<CryptoHolding>> = dao.crypto().state()
    val stocks: StateFlow<List<StockHolding>> = dao.stocks().state()

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthExpenses: StateFlow<List<Expense>> = _month
        .flatMapLatest { dao.expenses(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
        .state()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun shiftMonth(by: Long) { _month.value = _month.value.plusMonths(by) }
    fun showCurrentMonth() { _month.value = YearMonth.now() }

    /** Al activar el abono automático, los intereses empiezan a contar desde este mes. */
    fun save(account: Account) = viewModelScope.launch {
        val start = account.autoInterest && account.interestRate > 0 && account.interestFromMonth == 0
        dao.upsertAccount(if (start) account.copy(interestFromMonth = monthIndex(YearMonth.now())) else account)
    }
    fun delete(account: Account) = viewModelScope.launch { dao.deleteAccount(account) }

    // ---------- Ingresos ----------

    val incomeSources: StateFlow<List<IncomeSource>> = dao.incomeSources().state()
    val incomes: StateFlow<List<Income>> = dao.incomes().state()

    /** Ingreso del mes elegido: lo fijo más la media de lo variable, corregido con lo ya cobrado. */
    val monthIncome: StateFlow<IncomeMath.MonthIncome> =
        combine(_month, incomeSources, incomes, prefs.settings) { m, sources, list, s ->
            if (sources.isEmpty()) {
                // Sin fuentes definidas, el ingreso único de antes.
                IncomeMath.MonthIncome(s.monthlyIncome, 0.0, emptyList(), 0.0)
            } else {
                IncomeMath.confirmed = s.expectedIncome
                IncomeMath.forMonth(sources, list, m)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, IncomeMath.MonthIncome(0.0, 0.0, emptyList(), 0.0))

    // ---------- Gastos fijos, futuro, disponible real y logros ----------

    val bills: StateFlow<List<RecurringBill>> = dao.bills().state()
    val allExpenses: StateFlow<List<Expense>> = dao.allExpenses().state()

    val transfers: StateFlow<List<com.caudal.finanzas.data.Transfer>> = dao.transfers().state()
    fun save(t: com.caudal.finanzas.data.Transfer) = viewModelScope.launch { dao.saveTransfer(t) }
    fun delete(t: com.caudal.finanzas.data.Transfer) = viewModelScope.launch { dao.removeTransfer(t) }

    /** Cambia el saldo a mano (por ejemplo, para cuadrarlo con lo que dice el banco). */
    fun setBalance(account: Account, balance: Double) = viewModelScope.launch { dao.upsertAccount(account.copy(balance = balance)) }

    fun save(bill: RecurringBill) = viewModelScope.launch { dao.upsertBill(bill) }
    fun delete(bill: RecurringBill) = viewModelScope.launch { dao.deleteBill(bill) }

    /** Apunta el pago de este mes de un gasto fijo (y lo descuenta de su cuenta). */
    fun payBill(bill: RecurringBill) = viewModelScope.launch {
        dao.saveExpense(
            Expense(
                categoryId = bill.categoryId, amount = bill.amount, note = bill.name,
                epochDay = java.time.LocalDate.now().toEpochDay(), accountId = bill.accountId, billId = bill.id,
            ),
        )
    }

    data class Planning(
        val wealth: Wealth,
        val forecast: Forecast,
        val safe: SafeToSpend,
        val comparison: MonthComparison,
        val achievements: List<Achievement>,
        /** Todo cargado, para los análisis y el chat. */
        val data: FinanceData,
        /** Reparto del saldo de la cuenta principal (null si no hay cuenta del día a día). */
        val bankPlan: BankPlan?,
        val goal: com.caudal.finanzas.domain.GoalStatus? = null,
        /** Todos los objetivos por prioridad (el primero es [goal]). */
        val goals: List<com.caudal.finanzas.domain.GoalStatus> = emptyList(),
        val goalList: List<com.caudal.finanzas.domain.Goal> = emptyList(),
        /** Edad financiera (null sin año de nacimiento). */
        val age: com.caudal.finanzas.domain.FinancialAge? = null,
        /** Rueda: cuánto puedes gastar en cada tipo de gasto al mes. */
        val budget: com.caudal.finanzas.domain.SpendingBudget? = null,
        val limits: Map<com.caudal.finanzas.domain.SpendTag, com.caudal.finanzas.domain.TagLimit> = emptyMap(),
    ) {
        val points get() = achievements.filter { it.unlocked }.sumOf { it.points }
        val level get() = Achievements.level(points)
    }

    val splits: StateFlow<List<Split>> = dao.splits().state()
    fun save(split: Split) = viewModelScope.launch { dao.upsertSplit(split) }
    fun delete(split: Split) = viewModelScope.launch { dao.deleteSplit(split) }

    /** Apunta los gastos escritos como frase, ya clasificados, saliendo de [accountId]. */
    fun saveParsed(list: List<ParsedExpense>, accountId: Long?) = viewModelScope.launch {
        val cats = categories.value
        val fallback = cats.firstOrNull { !it.isSavings }?.id ?: return@launch
        val today = java.time.LocalDate.now()
        list.forEach { p ->
            dao.saveExpense(
                Expense(
                    categoryId = Classifier.bucketFor(p.tag, cats) ?: fallback, amount = p.amount, note = p.note,
                    epochDay = p.date(today).toEpochDay(), accountId = accountId, tag = p.tag.name,
                ),
            )
        }
        _messages.emit(if (list.size == 1) "Gasto apuntado" else "${list.size} gastos apuntados")
    }

    fun setPlan(name: String, amount: Double) = prefs.setPlan(name, amount)
    fun setBankPlan(mainId: Long, targetId: Long, pct: Double) = prefs.setBankPlan(mainId, targetId, pct)
    fun setBankBuffer(value: Double) = prefs.setBankBuffer(value)
    fun setRefund(expenseId: Long, pending: Boolean) = prefs.setRefund(expenseId, pending)
    fun setSavingProfile(style: Int, goals: Set<String>) {
        prefs.setSavingProfile(style, goals)
        val (needs, wants, savings) = com.caudal.finanzas.domain.SavingProfile.split(style)
        val cats = categories.value
        val saving = cats.filter { it.isSavings }
        val needCat = cats.firstOrNull { !it.isSavings && Classifier.normalize(it.name).contains("necesidad") } ?: cats.firstOrNull { !it.isSavings }
        val others = cats.filter { !it.isSavings && it.id != needCat?.id }
        val updated = cats.map { c ->
            when {
                c.id == saving.firstOrNull()?.id -> c.copy(percent = savings)
                c.isSavings -> c.copy(percent = 0.0)
                c.id == needCat?.id -> c.copy(percent = if (others.isEmpty()) needs + wants else needs)
                else -> c.copy(percent = Math.round(wants / others.size * 10) / 10.0)
            }
        }
        if (updated != cats) saveCategories(updated)
    }

    /** Historial de precios por "símbolo:días", para los gráficos de Inversión. */
    private val _history = MutableStateFlow<Map<String, List<Double>>>(emptyMap())
    val history: StateFlow<Map<String, List<Double>>> = _history.asStateFlow()

    private val _ohlc = MutableStateFlow<Map<String, List<com.caudal.finanzas.data.Candle>>>(emptyMap())
    val ohlc: StateFlow<Map<String, List<com.caudal.finanzas.data.Candle>>> = _ohlc.asStateFlow()

    /** Velas para la ficha de una inversión, por "símbolo:días". */
    fun loadOhlc(key: String, isCrypto: Boolean, id: String, days: Int, cur: String? = null) {
        val k = if (cur == null) "$key:$days" else "$key:$days:$cur"
        val c = cur ?: currency
        if (k in _ohlc.value) return
        viewModelScope.launch {
            val list = runCatching {
                if (isCrypto) PriceService.cryptoOhlc(id, c, days) else PriceService.stockOhlc(id, c, days)
            }.getOrDefault(emptyList())
            _ohlc.value = _ohlc.value + (k to list.filter { listOf(it.open, it.high, it.low, it.close).all { v -> v.isFinite() && v > 0 } })
        }
    }

    /** Hora (ms) de cada punto del historial, para saber el precio de un momento al mantener pulsado. */
    private val _historyTimes = MutableStateFlow<Map<String, List<Long>>>(emptyMap())
    val historyTimes: StateFlow<Map<String, List<Long>>> = _historyTimes.asStateFlow()

    fun loadHistory(key: String, isCrypto: Boolean, id: String, days: Int, cur: String? = null) {
        val k = if (cur == null) "$key:$days" else "$key:$days:$cur"
        val c = cur ?: currency
        if (k in _history.value) return
        viewModelScope.launch {
            val list = runCatching {
                if (isCrypto) PriceService.cryptoHistory(id, c, days) else PriceService.stockHistory(id, c, days)
            }.getOrDefault(emptyList()).filter { it.second.isFinite() && it.second > 0 }
            _historyTimes.value = _historyTimes.value + (k to list.map { it.first })
            _history.value = _history.value + (k to list.map { it.second })
        }
    }

    private val _stats = MutableStateFlow<Map<String, com.caudal.finanzas.data.CoinStats?>>(emptyMap())
    val stats: StateFlow<Map<String, com.caudal.finanzas.data.CoinStats?>> = _stats.asStateFlow()

    /** Estadísticas de CoinGecko de una cripto (una vez por sesión). */
    fun loadStats(coinId: String, cur: String? = null) {
        val k = if (cur == null) coinId else "$coinId:$cur"
        if (k in _stats.value) return
        _stats.value = _stats.value + (k to null)
        viewModelScope.launch {
            runCatching { PriceService.coinStats(coinId, cur ?: currency) }.getOrNull()?.let { _stats.value = _stats.value + (k to it) }
        }
    }

    /** "¿De qué se está hablando?": estado de la carga y resultado (se refresca como mucho cada 30 min). */
    private val _buzz = MutableStateFlow<Pair<Boolean, com.caudal.finanzas.domain.Buzz?>>(false to null)
    val buzz: StateFlow<Pair<Boolean, com.caudal.finanzas.domain.Buzz?>> = _buzz.asStateFlow()
    private var buzzAt = 0L

    fun loadBuzz(force: Boolean = false) {
        if (_buzz.value.first || (!force && _buzz.value.second != null && System.currentTimeMillis() - buzzAt < 30 * 60_000)) return
        _buzz.value = true to _buzz.value.second
        viewModelScope.launch {
            val trending = runCatching { PriceService.trending() }.getOrDefault(emptyList())
            val posts = listOf("CryptoCurrency", "CryptoMarkets").flatMap { runCatching { PriceService.redditHot(it) }.getOrDefault(emptyList()) }
                .distinctBy { it.url }
            val mine = crypto.value.map { Triple(it.coinId, it.symbol, it.name) }
            val result = if (trending.isEmpty() && posts.isEmpty()) null else com.caudal.finanzas.domain.BuzzAnalyzer.analyze(posts, trending, mine)
            if (result != null) buzzAt = System.currentTimeMillis()
            _buzz.value = false to (result ?: _buzz.value.second)
            if (result == null) _messages.emit("Sin conexión: no he podido ver de qué se habla ahora mismo.")
        }
    }
    private fun goals() = com.caudal.finanzas.domain.GoalList.parse(prefs.settings.value.goalsRaw)
    private fun saveGoals(list: List<com.caudal.finanzas.domain.Goal>) = prefs.setGoals(com.caudal.finanzas.domain.GoalList.format(list))

    /** Crea ([index] null) o cambia un objetivo. */
    fun setGoal(index: Int?, name: String, target: Double, deadline: java.time.LocalDate) {
        val list = goals().toMutableList()
        val start = index?.let { list.getOrNull(it)?.start } ?: java.time.LocalDate.now()
        val g = com.caudal.finanzas.domain.Goal(name, target, start, deadline)
        if (index != null && index in list.indices) list[index] = g else list += g
        saveGoals(list)
    }
    fun deleteGoal(index: Int) = saveGoals(goals().filterIndexed { i, _ -> i != index })
    fun moveGoal(index: Int, by: Int) {
        val list = goals().toMutableList()
        val to = index + by
        if (index !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(index))
        saveGoals(list)
    }
    fun setBirthYear(year: Int) = prefs.setBirthYear(year)

    /** Fija (o con null, vuelve a automático) lo que quieres gastar en un tipo de gasto. */
    fun setTagLimit(tag: com.caudal.finanzas.domain.SpendTag, limit: com.caudal.finanzas.domain.TagLimit?) {
        val current = com.caudal.finanzas.domain.Budgeting.parseLimits(prefs.settings.value.tagLimitsRaw)
        val next = if (limit == null) current - tag else current + (tag to limit)
        prefs.setTagLimits(com.caudal.finanzas.domain.Budgeting.formatLimits(next))
    }

    /** Mueve el ahorro del reparto de la cuenta principal a la de ahorro. */
    fun moveToSavings(plan: BankPlan) = viewModelScope.launch {
        val target = plan.target ?: return@launch
        if (plan.moveNow <= 0) return@launch
        val amount = kotlin.math.round(plan.moveNow * 100) / 100
        dao.saveTransfer(com.caudal.finanzas.data.Transfer(fromAccountId = plan.main.id, toAccountId = target.id, amount = amount, note = "Ahorro del mes", epochDay = java.time.LocalDate.now().toEpochDay()))
        _messages.emit("Movido a ${target.name}")
    }

    /** Precio actual de una cripto en la moneda de la app, o null sin conexión. */
    suspend fun cryptoPrice(coinId: String): Double? = runCatching { PriceService.cryptoPrices(listOf(coinId), currency)[coinId] }.getOrNull()

    private data class Holdings(val accounts: List<Account>, val crypto: List<CryptoHolding>, val stocks: List<StockHolding>, val categories: List<Category>, val snapshots: List<NetWorthSnapshot>)
    private data class Flows(val bills: List<RecurringBill>, val expenses: List<Expense>, val sources: List<IncomeSource>, val incomes: List<Income>, val splits: List<Split>)

    val planning: StateFlow<Planning?> = combine(
        combine(accounts, crypto, stocks, categories, dao.snapshots()) { a, c, s, cat, snap -> Holdings(a, c, s, cat, snap) },
        combine(bills, allExpenses, incomeSources, incomes, splits) { b, e, src, inc, sp -> Flows(b, e, src, inc, sp) },
        prefs.settings,
        transfers,
    ) { h, f, s, t -> computePlanning(h, f, s, t) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun computePlanning(h: Holdings, f: Flows, s: com.caudal.finanzas.data.Settings, transfers: List<com.caudal.finanzas.data.Transfer>): Planning {
        IncomeMath.confirmed = s.expectedIncome
        val today = java.time.LocalDate.now()
        val thisMonth = YearMonth.from(today)
        val savingIds = h.categories.filter { it.isSavings }.map { it.id }.toSet()
        val savingsPct = h.categories.filter { it.isSavings }.sumOf { it.percent }
        val spendablePct = h.categories.filter { !it.isSavings }.sumOf { it.percent }
        val wealth = Wealth.of(h.accounts, h.crypto, h.stocks)
        val forecast = Forecasting.forecast(today, wealth.available, 90, f.sources, f.incomes, f.bills, f.expenses, savingIds)
        val income = if (f.sources.isEmpty()) s.monthlyIncome else IncomeMath.forMonth(f.sources, f.incomes, thisMonth).total
        fun inMonth(day: Long, m: YearMonth) = YearMonth.from(java.time.LocalDate.ofEpochDay(day)) == m
        val savedThisMonth = f.expenses.filter { it.categoryId in savingIds && inMonth(it.epochDay, thisMonth) }.sumOf { it.amount }
        val main = h.accounts.firstOrNull { it.id == s.bankMainId } ?: BankPlanning.defaultMain(h.accounts, s.cardAccountId)
        val target = h.accounts.firstOrNull { it.id == s.bankTargetId && it.id != main?.id } ?: BankPlanning.defaultTarget(h.accounts)
        val data = FinanceData(today, h.accounts, h.crypto, h.stocks, h.categories, f.expenses, f.incomes, f.sources, f.bills, h.snapshots, f.splits, s.planReserve)
        val spentToday = data.spending.filter { it.epochDay == today.toEpochDay() && !com.caudal.finanzas.domain.Budgeting.isBillPayment(it, f.bills) }.sumOf { it.amount }
        // Cuánto ahorrar al mes: lo que pide tu perfil (o el reparto) y, si es más, tu primer objetivo.
        val monthlyIncome = data.monthlyIncome
        val billsMonthly = f.bills.sumOf { Bills.monthly(it) }
        val profilePct = if (s.savingStyle >= 0) com.caudal.finanzas.domain.SavingProfile.split(s.savingStyle).third else savingsPct
        val goalNeed = com.caudal.finanzas.domain.GoalList.plan(com.caudal.finanzas.domain.GoalList.parse(s.goalsRaw), data, null, { it.toString() })
            .firstOrNull { !it.done }?.requiredMonthly ?: 0.0
        val targetSaving = max(monthlyIncome * profilePct / 100, goalNeed).takeIf { monthlyIncome > 0 } ?: Double.MAX_VALUE
        // Lo mínimo para vivir al día: lo que te deja tu perfil tras fijos y ahorro (nunca se manda eso al ahorro).
        val floorDaily = if (monthlyIncome > 0) max(0.0, monthlyIncome - billsMonthly - monthlyIncome * profilePct / 100) / thisMonth.lengthOfMonth() else 0.0
        val bankPlan = main?.let {
            BankPlanning.plan(
                today, it, target, s.bankBuffer, f.bills, f.expenses, transfers, f.sources, f.incomes,
                BankPlanning.dailyHabit(data) * com.caudal.finanzas.domain.SavingProfile.spendFactor(s.savingStyle), spentToday,
                targetSaving, floorDaily * com.caudal.finanzas.domain.SavingProfile.spendFactor(s.savingStyle),
            )
        }
        // Con reparto, "puedes gastar" sale de la cuenta principal, igual que en Inicio (el margen de fin de mes cuenta como apartado).
        val safe = if (bankPlan != null && target != null) com.caudal.finanzas.domain.SafeToSpend(
            bankPlan.main.balance + bankPlan.incomingTotal, bankPlan.pendingTotal, bankPlan.toSave, bankPlan.nextPayday, bankPlan.days, s.planReserve + bankPlan.buffer,
        )
        else Spending.safeToSpend(today, wealth.available, forecast.events, income * savingsPct / 100, savedThisMonth, s.planReserve)
        val health = Insights.health(data) { "%.2f".format(it) }
        val fmt: (Double) -> String = { String.format(java.util.Locale.forLanguageTag("es-ES"), "%,.0f €", it) }
        val goalList = com.caudal.finanzas.domain.GoalList.parse(s.goalsRaw)
        val goals = com.caudal.finanzas.domain.GoalList.plan(goalList, data, bankPlan, fmt)
        val goal = goals.firstOrNull()
        val limits = com.caudal.finanzas.domain.Budgeting.parseLimits(s.tagLimitsRaw)
        val budget = com.caudal.finanzas.domain.Budgeting.plan(data, bankPlan?.saveGoal ?: (income * savingsPct / 100), limits, bankPlan, com.caudal.finanzas.domain.SavingProfile.weights(s.savingStyle, s.lifeGoals))
        val age = if (s.birthYear > 1900) com.caudal.finanzas.domain.FinancialAging.evaluate(today.year - s.birthYear, data, goal) else null

        // Meses cerrados desde que hay datos, para los logros de constancia.
        val firstDay = (f.expenses.map { it.epochDay } + f.incomes.map { it.epochDay }).minOrNull()
        val closed = if (firstDay == null) emptyList() else {
            val first = YearMonth.from(java.time.LocalDate.ofEpochDay(firstDay))
            (1..24L).map { thisMonth.minusMonths(it) }.filter { !it.isBefore(first) }.reversed().map { m ->
                val received = f.incomes.filter { inMonth(it.epochDay, m) }.sumOf { it.amount }
                Triple(received, f.expenses.filter { inMonth(it.epochDay, m) }.sumOf { it.amount }, received * spendablePct / 100)
            }
        }
        val achievements = Achievements.evaluate(
            AchievementInput(
                wealth = wealth, accounts = h.accounts.size, investments = h.crypto.size + h.stocks.size,
                hasInterest = h.accounts.any { it.interestRate > 0 }, cardPayments = s.cardPayments,
                monthlyEssentials = f.bills.sumOf { Bills.monthly(it) } + forecast.dailySpend * 30,
                closedMonths = closed, bills = f.bills.size,
                splits = f.splits.size, healthGood = health.good,
            ),
        )
        return Planning(wealth, forecast, safe, Comparison.compare(today, f.expenses, f.incomes, h.categories), achievements, data, bankPlan, goal, goals, goalList, age, budget, limits)
    }

    /** Logros recién conseguidos, para celebrarlos. */
    private val _newAchievements = MutableSharedFlow<List<Achievement>>(extraBufferCapacity = 4)
    val newAchievements: SharedFlow<List<Achievement>> = _newAchievements

    init {
        viewModelScope.launch {
            planning.collect { p ->
                if (p == null) return@collect
                // Guarda los límites para poder avisar tras un pago con tarjeta aunque la app esté cerrada.
                p.budget?.let { b ->
                    val len = java.time.YearMonth.now().lengthOfMonth()
                    prefs.setLimitSnapshot(
                        com.caudal.finanzas.domain.LimitSnapshot(
                            p.bankPlan?.perDay ?: p.safe.perDay, b.base / len * 7,
                            b.items.filter { it.monthly >= 20 }.associate { it.tag to it.monthly },
                        ).encode(),
                    )
                }
                // Guarda la puntuación de hoy de los tres círculos (para "qué ha cambiado desde la semana pasada").
                runCatching {
                    val fmt: (Double) -> String = { "%.0f €".format(it) }
                    val pulse = com.caudal.finanzas.domain.PulseCalculator.of(p.data, p.budget, p.bankPlan, fmt)
                    val circles = com.caudal.finanzas.domain.FinancialCircles.of(p.data, p.bankPlan, p.forecast, p.goals, pulse.margin, fmt)
                    val history = com.caudal.finanzas.domain.FinancialCircles.decodeHistory(prefs.settings.value.circleHistory) +
                        (java.time.LocalDate.now().toEpochDay() to circles.map { it.score })
                    prefs.setCircleHistory(com.caudal.finanzas.domain.FinancialCircles.encodeHistory(history))
                }
                val known = prefs.settings.value.achievements
                val fresh = p.achievements.filter { it.unlocked && it.id !in known }
                if (fresh.isNotEmpty()) {
                    prefs.unlock(fresh.map { it.id })
                    _newAchievements.emit(fresh)
                }
            }
        }
    }

    fun save(source: IncomeSource) = viewModelScope.launch { dao.upsertIncomeSource(source) }
    fun delete(source: IncomeSource) = viewModelScope.launch { dao.deleteIncomeSource(source) }
    fun save(income: Income) = viewModelScope.launch { dao.saveIncome(income) }
    fun delete(income: Income) = viewModelScope.launch { dao.removeIncome(income) }

    /** Suma a cada cuenta remunerada los intereses de los meses cerrados que falten. */
    private suspend fun applyMonthlyInterest() {
        val current = monthIndex(YearMonth.now())
        for (account in dao.interestAccounts()) {
            var balance = account.balance
            var last = account.interestFromMonth
            if (last == 0) { dao.upsertAccount(account.copy(interestFromMonth = current)); continue }
            while (last < current) {
                last++
                val interest = InterestMath.monthlyInterest(balance, account.interestRate)
                if (interest <= 0) continue
                val month = YearMonth.of(last / 12, last % 12 + 1)
                dao.upsertIncome(
                    Income(
                        sourceId = null, amount = interest, note = "Intereses · ${account.name}",
                        epochDay = month.atDay(1).toEpochDay(), accountId = account.id,
                    ),
                )
                balance += interest
            }
            dao.upsertAccount(account.copy(balance = balance, interestFromMonth = last))
        }
    }

    init {
        viewModelScope.launch { applyMonthlyInterest() }
        viewModelScope.launch { markPaidSalary() }
    }

    /**
     * Las nóminas fijas cuyo día ya ha pasado este mes se apuntan como cobradas sin sumar al saldo
     * (el saldo que pusiste ya las incluye). Se hace una vez por mes, al abrir la app.
     */
    private suspend fun markPaidSalary() {
        val now = java.time.LocalDate.now()
        val month = YearMonth.from(now)
        val idx = monthIndex(month)
        if (prefs.settings.value.autoPaidMonth == idx) return
        val sources = dao.incomeSources().first()
        val incomes = dao.incomes().first()
        sources.filter { !it.variable && it.dayFrom <= 5 && it.dayFrom <= now.dayOfMonth }.forEach { s ->
            val got = incomes.any { it.sourceId == s.id && YearMonth.from(java.time.LocalDate.ofEpochDay(it.epochDay)) == month }
            if (!got) dao.upsertIncome(Income(sourceId = s.id, amount = s.amount, note = s.name, epochDay = month.atDay(s.dayFrom).toEpochDay(), accountId = null))
        }
        prefs.setAutoPaidMonth(idx)
    }

    val recentExpenses: StateFlow<List<Expense>> = dao.recentExpenses(5).state()
    val snapshots: StateFlow<List<NetWorthSnapshot>> = dao.snapshots().state()

    /** Guarda el patrimonio de hoy (una foto por día, la última gana). */
    fun recordNetWorth(total: Double) = viewModelScope.launch {
        dao.upsertSnapshot(NetWorthSnapshot(java.time.LocalDate.now().toEpochDay(), total))
    }

    fun save(expense: Expense) = viewModelScope.launch { dao.saveExpense(expense) }
    fun delete(expense: Expense) = viewModelScope.launch { dao.removeExpense(expense) }

    /** Deslizar para refrescar en la ficha: vuelve a pedir precio, gráfico y estadísticas (gratis). */
    fun reloadAsset(symbol: String, id: String, isCrypto: Boolean, days: Int, candles: Boolean, cur: String?) {
        val pre = "$symbol:"
        _history.value = _history.value.filterKeys { !it.startsWith(pre) }
        _historyTimes.value = _historyTimes.value.filterKeys { !it.startsWith(pre) }
        _ohlc.value = _ohlc.value.filterKeys { !it.startsWith(pre) }
        _stats.value = _stats.value.filterKeys { it != id && !it.startsWith("$id:") }
        loadHistory(symbol, isCrypto, id, 1)
        if (cur != null) loadHistory(symbol, isCrypto, id, 1, cur)
        if (candles) loadOhlc(symbol, isCrypto, id, days, cur) else loadHistory(symbol, isCrypto, id, days, cur)
        if (isCrypto) { loadStats(id, cur); refreshCrypto(quiet = true) } else refreshStocks()
    }

    // ---------- Noticias de hoy (RSS gratis) ----------
    fun seenNews(): Set<String> = prefs.seenNews()
    fun markNewsSeen(url: String) = prefs.markNewsSeen(url.hashCode().toString())
    private val _news = MutableStateFlow<Pair<Boolean, List<com.caudal.finanzas.data.NewsItem>?>>(false to null)
    val news: StateFlow<Pair<Boolean, List<com.caudal.finanzas.data.NewsItem>?>> = _news.asStateFlow()
    private var newsAt = 0L
    fun loadNews(force: Boolean = false) {
        if (_news.value.first) return
        if (!force && _news.value.second != null && System.currentTimeMillis() - newsAt < 15 * 60_000) return
        _news.value = true to _news.value.second
        viewModelScope.launch {
            val list = runCatching { com.caudal.finanzas.data.NewsService.today() }.getOrNull()
            if (list != null) newsAt = System.currentTimeMillis()
            _news.value = false to (list ?: _news.value.second)
        }
    }

    // ---------- Dónde comprar una cripto ----------
    private val _exchanges = MutableStateFlow<Map<String, List<com.caudal.finanzas.data.CoinMarket>?>>(emptyMap())
    val exchanges: StateFlow<Map<String, List<com.caudal.finanzas.data.CoinMarket>?>> = _exchanges.asStateFlow()
    fun loadExchanges(coinId: String) {
        if (coinId in _exchanges.value) return
        _exchanges.value = _exchanges.value + (coinId to null)
        viewModelScope.launch {
            val list = runCatching { PriceService.coinExchanges(coinId) }.getOrDefault(emptyList())
            _exchanges.value = _exchanges.value + (coinId to list)
        }
    }

    // ---------- Criptos del mercado (aunque no tengas) ----------
    private val _markets = MutableStateFlow<List<com.caudal.finanzas.data.MarketCoin>>(emptyList())
    val cryptoMarkets: StateFlow<List<com.caudal.finanzas.data.MarketCoin>> = _markets.asStateFlow()
    private var marketsAt = 0L
    fun loadMarkets(ids: List<String>) {
        if (_markets.value.isNotEmpty() && System.currentTimeMillis() - marketsAt < 120_000) return
        marketsAt = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching { PriceService.cryptoMarkets(ids, "usd") }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                _markets.value = list
                val sparks = list.filter { it.spark.size >= 2 }
                _history.value = _history.value + sparks.associate { "${it.symbol}:1:usd" to it.spark } +
                    sparks.filter { "${it.symbol}:1" !in _history.value }.associate { "${it.symbol}:1" to it.spark }
            }
        }
    }

    // ---------- Acciones vistas hace poco ----------
    private val _recentTickers = MutableStateFlow(prefs.recentTickers())
    val recentTickers: StateFlow<List<String>> = _recentTickers.asStateFlow()
    fun markViewed(ticker: String) {
        val list = (listOf(ticker) + _recentTickers.value.filter { it != ticker }).take(8)
        _recentTickers.value = list
        prefs.setRecentTickers(list)
    }

    // ---------- Ingresos variables confirmados y cuentas con amigos ----------
    fun setExpectedIncome(source: IncomeSource, month: java.time.YearMonth, amount: Double?) =
        prefs.setExpectedIncome(IncomeMath.key(source.id, month), amount)

    private fun debts() = com.caudal.finanzas.domain.Debts.parse(prefs.settings.value.debtsRaw)
    private fun tricount() = com.caudal.finanzas.domain.Debts.parseTricount(prefs.settings.value.tricountRaw)
    private fun putDebts(list: List<com.caudal.finanzas.domain.Debt>) = prefs.setDebts(com.caudal.finanzas.domain.Debts.format(list))

    fun saveDebt(d: com.caudal.finanzas.domain.Debt) {
        val item = if (d.id == 0L) d.copy(id = System.currentTimeMillis()) else d
        putDebts(debts().filter { it.id != item.id } + item)
    }
    fun deleteDebt(id: Long) = putDebts(debts().filter { it.id != id })
    fun settleDebt(id: Long) = putDebts(debts().map { if (it.id == id) it.copy(settled = true) else it })
    /** "Se ha pagado": todo lo pendiente con esa persona queda saldado, también en Tricount. */
    fun settlePerson(person: String) {
        putDebts(debts().map { if (it.person.equals(person, true)) it.copy(settled = true) else it })
        prefs.setTricount(com.caudal.finanzas.domain.Debts.formatTricount(tricount().filterKeys { !it.equals(person, true) }))
    }
    fun setTricount(person: String, amount: Double) {
        val m = tricount().filterKeys { !it.equals(person, true) } + (person to com.caudal.finanzas.domain.TricountBalance(amount, java.time.LocalDate.now().toEpochDay()))
        prefs.setTricount(com.caudal.finanzas.domain.Debts.formatTricount(m))
    }

    /** Un gasto que era un traspaso: se quita el gasto (devolviendo el dinero) y se apunta la transferencia. */
    fun convertToTransfer(expense: Expense, toAccountId: Long) = viewModelScope.launch {
        val from = expense.accountId ?: return@launch
        dao.removeExpense(expense)
        dao.saveTransfer(com.caudal.finanzas.data.Transfer(fromAccountId = from, toAccountId = toAccountId, amount = expense.amount, note = expense.note.ifBlank { "Traspaso" }, epochDay = expense.epochDay))
        _messages.emit("Convertido en transferencia")
    }

    /** "No era un pago": se borra (devolviendo el dinero a su cuenta) y no se vuelven a apuntar avisos iguales. */
    fun notAPayment(expense: Expense) = viewModelScope.launch {
        if (expense.rawText.isNotBlank()) prefs.ignorePayment(com.caudal.finanzas.payments.PaymentParser.signature(expense.rawText))
        dao.removeExpense(expense)
        _messages.emit("Borrado. No volveré a apuntar avisos como ese.")
    }

    /** Guarda el reparto completo; las categorías que ya no están se borran junto con sus gastos. */
    fun saveCategories(edited: List<Category>) = viewModelScope.launch {
        val keptIds = edited.map { it.id }.toSet()
        categories.value.filter { it.id !in keptIds }.forEach {
            dao.deleteExpensesOf(it.id)
            dao.deleteCategory(it)
        }
        edited.forEach { dao.upsertCategory(it) }
    }

    fun save(holding: CryptoHolding) = viewModelScope.launch {
        dao.upsertCrypto(holding)
        refreshCrypto(quiet = true)
    }
    fun delete(holding: CryptoHolding) = viewModelScope.launch { dao.deleteCrypto(holding) }

    fun save(holding: StockHolding, fetchPrice: Boolean) = viewModelScope.launch {
        dao.upsertStock(holding)
        if (fetchPrice) refreshStocks(quiet = true)
    }
    fun delete(holding: StockHolding) = viewModelScope.launch { dao.deleteStock(holding) }

    suspend fun searchCoins(query: String): List<CoinResult> = PriceService.searchCoins(query)

    fun refreshCrypto(quiet: Boolean = false): kotlinx.coroutines.Job = viewModelScope.launch {
        val list = crypto.value.ifEmpty { return@launch }
        _refreshing.value = true
        try {
            val quotes = PriceService.cryptoQuotes(list.map { it.coinId }.distinct(), currency)
            val now = System.currentTimeMillis()
            list.forEach { h -> quotes[h.coinId]?.let { (price, _) -> dao.upsertCrypto(h.copy(price = price, updatedAt = now)) } }
            prefs.setDayChanges(list.mapNotNull { h -> quotes[h.coinId]?.second?.let { h.symbol to it } }.toMap())
            if (!quiet) _messages.emit("Precios de cripto actualizados")
        } catch (e: PriceService.RateLimited) {
            // CoinGecko gratis: demasiadas consultas seguidas. Se reintenta solo, sin molestar.
            if (!quiet) _messages.emit("CoinGecko va saturado: reintento en un minuto con los precios de antes")
            if (!retryScheduled) {
                retryScheduled = true
                viewModelScope.launch { kotlinx.coroutines.delay(60_000); retryScheduled = false; refreshCrypto(quiet = true) }
            }
        } catch (e: Exception) {
            if (!quiet) _messages.emit("No se pudieron actualizar las cripto (sin conexión)")
        } finally {
            _refreshing.value = false
        }
    }
    @Volatile private var retryScheduled = false

    fun refreshStocks(quiet: Boolean = false) = viewModelScope.launch {
        val list = stocks.value.ifEmpty { return@launch }
        _refreshing.value = true
        val failed = mutableListOf<String>()
        val now = System.currentTimeMillis()
        for (h in list) {
            try {
                val (price, change) = PriceService.stockQuote(h.ticker, currency)
                dao.upsertStock(h.copy(price = price, updatedAt = now))
                change?.let { prefs.setDayChanges(mapOf(h.ticker to it)) }
            } catch (e: Exception) {
                failed += h.ticker
            }
        }
        _refreshing.value = false
        when {
            failed.isNotEmpty() -> _messages.emit("Sin precio para: ${failed.joinToString()}. Puedes ponerlo a mano.")
            !quiet -> _messages.emit("Precios de acciones actualizados")
        }
    }

    /** Si los precios tienen más de 10 minutos, se piden otra vez. */
    fun refreshIfStale() {
        val limit = System.currentTimeMillis() - 10 * 60_000
        if (crypto.value.any { it.updatedAt < limit }) refreshCrypto(quiet = true)
        if (stocks.value.any { it.updatedAt in 1 until limit }) refreshStocks(quiet = true)
    }
}
