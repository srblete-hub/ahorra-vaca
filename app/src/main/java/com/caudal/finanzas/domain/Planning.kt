package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.RecurringBill
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min

private fun Long.date(): LocalDate = LocalDate.ofEpochDay(this)
private fun YearMonth.day(d: Int): LocalDate = atDay(min(d, lengthOfMonth()))

object Bills {
    /** Cuándo toca pagarlo en [month], o null si es anual y no le toca ese mes. */
    fun dueDate(bill: RecurringBill, month: YearMonth): LocalDate? =
        if (bill.yearly && month.monthValue != bill.month) null else month.day(bill.day)

    /** Pagado ese mes: hay un gasto enlazado, o uno con su nombre e importe parecido (±10 %). */
    fun isPaid(bill: RecurringBill, month: YearMonth, expenses: List<Expense>): Boolean = expenses.any { e ->
        YearMonth.from(e.epochDay.date()) == month && (
            e.billId == bill.id ||
                (e.note.contains(bill.name, ignoreCase = true) && kotlin.math.abs(e.amount - bill.amount) <= bill.amount * 0.1)
            )
    }

    /** Lo que cuesta al mes (los anuales, repartidos en 12). */
    fun monthly(bill: RecurringBill): Double = if (bill.yearly) bill.amount / 12 else bill.amount
}

/** Algo que va a pasar en tu cuenta: un cobro (+) o un gasto fijo (−). */
data class FlowEvent(val date: LocalDate, val label: String, val amount: Double, val isIncome: Boolean, val overdue: Boolean = false)

data class Forecast(
    val points: List<Pair<LocalDate, Double>>,
    val events: List<FlowEvent>,
    val dailySpend: Double,
) {
    val lowest get() = points.minBy { it.second }
    val end get() = points.last()
    /** Primer día en que el saldo previsto baja de cero. */
    val firstNegative get() = points.firstOrNull { it.second < 0 }
}

object Forecasting {
    /** Gasto variable medio por día de los últimos [window] días (sin gastos fijos ni aportaciones al ahorro). */
    fun dailyVariableSpend(expenses: List<Expense>, today: LocalDate, savingCategoryIds: Set<Long>, window: Int = 60): Double {
        val from = today.minusDays(window.toLong())
        val relevant = expenses.filter { it.billId == null && it.categoryId !in savingCategoryIds && it.epochDay.date() > from && it.epochDay.date() <= today }
        if (relevant.isEmpty()) return 0.0
        // Si llevas poco tiempo usando la app, se divide entre los días que hay datos (mínimo 14).
        val firstDay = relevant.minOf { it.epochDay }.date()
        val days = max(14, (today.toEpochDay() - firstDay.toEpochDay() + 1).toInt()).coerceAtMost(window)
        return relevant.sumOf { it.amount } / days
    }

    /** Lo que se espera cobrar de [source] en [month]: lo fijo, o la media de lo variable. */
    private fun expected(source: IncomeSource, incomes: List<Income>, month: YearMonth): Double =
        if (source.variable) IncomeMath.confirmedFor(source, YearMonth.now()) ?: IncomeMath.variableAverage(source, incomes, YearMonth.now()) ?: source.amount else source.amount

    /** Cobros y gastos fijos previstos entre hoy y [until]; lo que ya debería haber pasado y no ha pasado va a hoy. */
    fun events(
        today: LocalDate, until: LocalDate,
        sources: List<IncomeSource>, incomes: List<Income>, bills: List<RecurringBill>, expenses: List<Expense>,
    ): List<FlowEvent> {
        val out = mutableListOf<FlowEvent>()
        var m = YearMonth.from(today)
        val current = m
        while (!m.atDay(1).isAfter(until)) {
            for (s in sources) {
                val due = m.day(s.dayFrom)
                if (m == current) {
                    val received = incomes.any { it.sourceId == s.id && YearMonth.from(it.epochDay.date()) == m }
                    if (received) continue
                }
                val date = if (due.isBefore(today)) today else due
                if (!date.isAfter(until)) out += FlowEvent(date, s.name, expected(s, incomes, m), isIncome = true, overdue = due.isBefore(today))
            }
            for (b in bills) {
                val due = Bills.dueDate(b, m) ?: continue
                if (m == current && Bills.isPaid(b, m, expenses)) continue
                val date = if (due.isBefore(today)) today else due
                if (!date.isAfter(until)) out += FlowEvent(date, b.name, -b.amount, isIncome = false, overdue = due.isBefore(today))
            }
            m = m.plusMonths(1)
        }
        return out.sortedWith(compareBy({ it.date }, { !it.isIncome }))
    }

    /** Saldo previsto cada día: hoy, más cobros, menos gastos fijos y menos el gasto diario habitual. */
    fun forecast(
        today: LocalDate, startBalance: Double, days: Int,
        sources: List<IncomeSource>, incomes: List<Income>, bills: List<RecurringBill>, expenses: List<Expense>,
        savingCategoryIds: Set<Long>,
    ): Forecast {
        val until = today.plusDays(days.toLong())
        val events = events(today, until, sources, incomes, bills, expenses)
        val daily = dailyVariableSpend(expenses, today, savingCategoryIds)
        val byDate = events.groupBy { it.date }
        var balance = startBalance
        val points = (0..days).map { i ->
            val d = today.plusDays(i.toLong())
            if (i > 0) balance -= daily
            balance += byDate[d]?.sumOf { it.amount } ?: 0.0
            d to balance
        }
        return Forecast(points, events, daily)
    }
}

/** Cuánto puedes gastar de verdad sin tocar lo que ya está comprometido hasta el próximo cobro. */
data class SafeToSpend(
    val available: Double,
    val pendingBills: Double,
    val savingsReserve: Double,
    val nextPayday: LocalDate?,
    val daysToPayday: Int,
    /** Apartado para un plan (una escapada, un regalo…): no se cuenta como gastable. */
    val plans: Double = 0.0,
) {
    val safe get() = available - pendingBills - savingsReserve - plans
    val perDay get() = if (safe <= 0) 0.0 else safe / max(1, daysToPayday)
}

object Spending {
    fun safeToSpend(
        today: LocalDate, available: Double, events: List<FlowEvent>,
        plannedSavings: Double, savedThisMonth: Double, plans: Double = 0.0,
    ): SafeToSpend {
        val payday = events.firstOrNull { it.isIncome && it.date.isAfter(today) }?.date
        val horizon = payday ?: YearMonth.from(today).atEndOfMonth().plusDays(1)
        val pending = events.filter { !it.isIncome && it.date.isBefore(horizon) }.sumOf { -it.amount }
        val reserve = max(0.0, plannedSavings - savedThisMonth)
        val days = (horizon.toEpochDay() - today.toEpochDay()).toInt()
        return SafeToSpend(available, pending, reserve, payday, max(1, days), plans)
    }
}

/**
 * El mes de tu cuenta principal: el saldo, menos los gastos fijos que aún salen (alquiler…), más lo
 * que aún te tiene que llegar (por ejemplo, la parte del sueldo que cobras más tarde). De ahí se
 * reserva lo que sueles gastar hasta fin de mes y un margen para llegar sobrado; lo que sobra va
 * al ahorro (Robinhood…). Lo que aún no ha llegado se mueve cuando llegue.
 */
data class BankPlan(
    val main: com.caudal.finanzas.data.Account,
    val target: com.caudal.finanzas.data.Account?,
    /** Gastos fijos pendientes que salen de la cuenta principal: (nombre, importe). */
    val pending: List<Pair<String, Double>>,
    /** Cobros fijos que aún faltan este mes en esta cuenta: (nombre, importe, día). */
    val incoming: List<Triple<String, Double, LocalDate>>,
    /** Lo que sueles gastar al día (sin fijos ni ahorro). */
    val dailyHabit: Double,
    /** Lo que llevas gastado hoy (ya descontado del saldo). */
    val spentToday: Double,
    /** Con cuánto quieres llegar a fin de mes después de ahorrar. */
    val buffer: Double,
    /** Lo ya movido este mes de la principal al ahorro. */
    val movedThisMonth: Double,
    val nextPayday: LocalDate?,
    /** Días que quedan del mes, hoy incluido. */
    val days: Int,
    /** Lo que quieres ahorrar al mes según tus objetivos y tu perfil (tope del ahorro). */
    val targetSaving: Double = Double.MAX_VALUE,
    /** Lo mínimo para vivir al día según tu perfil: se reserva aunque tu gasto apuntado sea menor. */
    val floorDaily: Double = 0.0,
) {
    val pendingTotal get() = pending.sumOf { it.second }
    val incomingTotal get() = incoming.sumOf { it.second }
    /** Lo que queda en la cuenta tras los fijos. */
    val remainder get() = main.balance - pendingTotal
    /** Todo el dinero de lo que queda de mes: lo que hay tras los fijos más lo que va a llegar. */
    val monthMoney get() = remainder + incomingTotal
    /** Lo que sueles gastar de aquí a fin de mes. */
    val expectedSpend get() = max(0.0, max(dailyHabit, floorDaily) * days - spentToday)
    /** Ahorro de este mes (incluye lo ya movido): lo que sobra tras tu gasto habitual y el margen, en múltiplos de 5. */
    /** Lo que sobra tras vivir hasta fin de mes y el margen, sin pasar de lo que quieres ahorrar al mes. */
    val surplus get() = max(0.0, monthMoney + movedThisMonth - expectedSpend - buffer)
    val saveGoal get() = kotlin.math.floor(min(surplus, targetSaving) / 5) * 5
    val toSave get() = max(0.0, saveGoal - movedThisMonth)
    /** Lo que se puede mover ya: lo que aún no ha llegado se mueve cuando llegue. */
    /** Lo que se puede mover ya. Si ya metiste casi todo lo del mes (90 %), no se insiste. */
    val moveNow get() = if (saveGoal > 0 && movedThisMonth >= saveGoal * 0.9) 0.0 else kotlin.math.floor(max(0.0, toSave - incomingTotal) / 5) * 5
    val moveLater get() = max(0.0, toSave - moveNow)
    /** Lo que puedes gastar hasta fin de mes sin tocar el ahorro ni el margen. */
    val spendable get() = monthMoney - toSave - buffer
    val perDay get() = if (spendable <= 0) 0.0 else spendable / max(1, days)
}

object BankPlanning {
    /** Cuenta principal por defecto: la de los pagos con tarjeta, o la cuenta bancaria con más saldo. */
    fun defaultMain(accounts: List<com.caudal.finanzas.data.Account>, cardAccountId: Long): com.caudal.finanzas.data.Account? =
        accounts.firstOrNull { it.id == cardAccountId && !it.isSaving() && !it.isDebt() }
            ?: accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK && !it.isSaving() }.maxByOrNull { it.balance }
            ?: accounts.filter { !it.isSaving() && !it.isDebt() }.maxByOrNull { it.balance }

    /** Destino por defecto: la cuenta de ahorro que más interés da. */
    fun defaultTarget(accounts: List<com.caudal.finanzas.data.Account>): com.caudal.finanzas.data.Account? =
        accounts.filter { it.isSaving() }.maxWithOrNull(compareBy({ it.interestRate }, { it.balance }))

    fun plan(
        today: LocalDate,
        main: com.caudal.finanzas.data.Account,
        target: com.caudal.finanzas.data.Account?,
        buffer: Double,
        bills: List<RecurringBill>,
        expenses: List<Expense>,
        transfers: List<com.caudal.finanzas.data.Transfer>,
        sources: List<IncomeSource>,
        incomes: List<Income>,
        dailyHabit: Double,
        spentToday: Double,
        targetSaving: Double = Double.MAX_VALUE,
        floorDaily: Double = 0.0,
    ): BankPlan {
        val thisMonth = YearMonth.from(today)
        val pending = bills.filter { it.accountId == main.id || it.accountId == null }
            .filter { Bills.dueDate(it, thisMonth) != null && !Bills.isPaid(it, thisMonth, expenses) }
            .map { it.name to it.amount }
        // Solo cobros fijos (lo variable no es seguro) que llegan a esta cuenta y aún no han llegado este mes.
        val incoming = sources.filter { it.accountId == main.id || it.accountId == null }
            .filter { s -> incomes.none { it.sourceId == s.id && YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == thisMonth } }
            .map { s ->
                val conf = IncomeMath.confirmedFor(s, thisMonth)
                val amount = conf ?: if (s.variable) 0.7 * (IncomeMath.variableAverage(s, incomes, thisMonth) ?: s.amount) else s.amount
                Triple(if (conf != null) "${s.name} (confirmado)" else if (s.variable) "${s.name} (estimado)" else s.name, kotlin.math.floor(amount / 5) * 5, thisMonth.atDay(min(s.dayFrom, thisMonth.lengthOfMonth())).let { if (it.isBefore(today)) today else it })
            }
            .filter { it.second > 0 }
            .sortedBy { it.third }
        // Lo que ya entró este mes en la cuenta de ahorro, venga de donde venga (Santander → Revolut → Robinhood).
        val moved = if (target == null) 0.0 else transfers.filter {
            it.toAccountId == target.id && it.fromAccountId != target.id && YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == thisMonth
        }.sumOf { it.amount }
        val days = max(1, thisMonth.lengthOfMonth() - today.dayOfMonth + 1)
        return BankPlan(main, target, pending, incoming, dailyHabit, spentToday, buffer, moved, incoming.firstOrNull()?.third, days, targetSaving, floorDaily)
    }

    /** Gasto habitual por día: la media de los meses cerrados (sin fijos ni ahorro), o la de los últimos días. */
    fun dailyHabit(d: FinanceData): Double {
        val closed = d.closedMonths(3)
        if (closed.isNotEmpty()) return closed.sumOf { m -> Budgeting.variable(d, m).sumOf { it.amount } } / closed.sumOf { it.lengthOfMonth() }
        return Forecasting.dailyVariableSpend(d.expenses, d.today, d.savingIds)
    }
}

/** "¿Dónde se ha ido mi dinero?": este mes hasta hoy frente al mismo tramo del mes pasado. */
data class MonthComparison(
    val incomeNow: Double,
    val incomePrev: Double,
    val spentNow: Double,
    val spentPrev: Double,
    /** Diferencia por categoría (positivo = gastas más), de mayor a menor. */
    val byCategory: List<Pair<Category, Double>>,
)

object Comparison {
    fun compare(today: LocalDate, expenses: List<Expense>, incomes: List<Income>, categories: List<Category>): MonthComparison {
        val thisStart = today.withDayOfMonth(1)
        val prevMonth = YearMonth.from(today).minusMonths(1)
        val prevStart = prevMonth.atDay(1)
        val prevEnd = prevMonth.day(today.dayOfMonth)
        fun inNow(d: Long) = d.date().let { !it.isBefore(thisStart) && !it.isAfter(today) }
        fun inPrev(d: Long) = d.date().let { !it.isBefore(prevStart) && !it.isAfter(prevEnd) }
        val diffs = categories.map { c ->
            val now = expenses.filter { it.categoryId == c.id && inNow(it.epochDay) }.sumOf { it.amount }
            val prev = expenses.filter { it.categoryId == c.id && inPrev(it.epochDay) }.sumOf { it.amount }
            c to (now - prev)
        }.sortedByDescending { it.second }
        return MonthComparison(
            incomeNow = incomes.filter { inNow(it.epochDay) }.sumOf { it.amount },
            incomePrev = incomes.filter { inPrev(it.epochDay) }.sumOf { it.amount },
            spentNow = expenses.filter { inNow(it.epochDay) }.sumOf { it.amount },
            spentPrev = expenses.filter { inPrev(it.epochDay) }.sumOf { it.amount },
            byCategory = diffs,
        )
    }
}

// ---------- Logros ----------

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val points: Int,
    val progress: Double,
) {
    val unlocked get() = progress >= 1.0
}

data class Level(val number: Int, val name: String, val from: Int, val to: Int?) {
    fun progress(points: Int) = if (to == null) 1.0 else (points - from).toDouble() / (to - from)
}

/** Lo que se necesita para calcular los logros, ya resumido. */
data class AchievementInput(
    val wealth: Wealth,
    val accounts: Int,
    val investments: Int,
    val hasInterest: Boolean,
    val cardPayments: Boolean,
    /** Gastos esenciales al mes (fijos + gasto habitual), para el fondo de emergencia. */
    val monthlyEssentials: Double,
    /** Meses ya cerrados, del más antiguo al más reciente: (cobrado, gastado, límite de gasto). */
    val closedMonths: List<Triple<Double, Double, Double>>,
    val bills: Int,
    /** Gastos repartidos con otros. */
    val splits: Int = 0,
    /** Indicadores del informe de salud en buen estado. */
    val healthGood: Int = 0,
)

object Achievements {
    private val LEVELS = listOf(
        0 to "Semilla", 30 to "Brote", 80 to "Ahorrador", 150 to "Constante",
        250 to "Estratega", 400 to "Inversor", 600 to "Maestro", 900 to "Leyenda",
    )

    fun level(points: Int): Level {
        val i = LEVELS.indexOfLast { points >= it.first }
        return Level(i + 1, LEVELS[i].second, LEVELS[i].first, LEVELS.getOrNull(i + 1)?.first)
    }

    private fun ratio(value: Double, target: Double) = if (target <= 0) 0.0 else (value / target).coerceIn(0.0, 1.0)

    /** Racha más larga de meses cerrados con más cobrado que gastado. */
    private fun bestGreenStreak(months: List<Triple<Double, Double, Double>>): Int {
        var best = 0; var run = 0
        for ((inc, spent, _) in months) { if (inc > spent && inc > 0) { run++; best = max(best, run) } else run = 0 }
        return best
    }

    fun evaluate(i: AchievementInput): List<Achievement> {
        val saved = i.wealth.savings + i.wealth.invested
        val green = bestGreenStreak(i.closedMonths)
        val withinLimit = i.closedMonths.any { (inc, spent, limit) -> inc > 0 && limit > 0 && spent <= limit }
        val emergency = if (i.monthlyEssentials > 0) i.monthlyEssentials * 3 else 0.0
        val list = mutableListOf(
            Achievement("primer-paso", "Primer paso", "Pon el dinero que tienes ahora", "flag", 10, if (i.accounts > 0) 1.0 else 0.0),
            Achievement("piloto", "Piloto automático", "Activa los pagos con tarjeta automáticos", "card", 15, if (i.cardPayments) 1.0 else 0.0),
            Achievement("fijos", "Sin sorpresas", "Apunta tus gastos fijos", "calendar", 10, if (i.bills > 0) 1.0 else 0.0),
            Achievement("intereses", "Dinero trabajando", "Ten una cuenta que te dé intereses", "savings", 20, if (i.hasInterest) 1.0 else 0.0),
            Achievement("inversor", "Inversor", "Añade tu primera inversión", "trending", 20, if (i.investments > 0) 1.0 else 0.0),
            Achievement("colchon", "Colchón", "Ahorra 3 meses de gastos esenciales" + if (emergency > 0) "" else " (apunta gastos para calcularlo)", "shield", 60, ratio(i.wealth.savings, emergency)),
            Achievement("mes-verde", "Mes en verde", "Cierra un mes cobrando más de lo que gastas", "leaf", 25, if (green >= 1) 1.0 else 0.0),
            Achievement("racha-3", "Racha de 3", "Tres meses seguidos en verde", "fire", 50, ratio(green.toDouble(), 3.0)),
            Achievement("limite", "Dentro del límite", "Cierra un mes sin pasarte del límite de gasto", "target", 25, if (withinLimit) 1.0 else 0.0),
            Achievement("compartir", "Cuentas claras", "Reparte un gasto con otras personas", "people", 15, if (i.splits > 0) 1.0 else 0.0),
            Achievement("salud", "Salud de hierro", "Ten 4 de 5 indicadores de salud financiera en verde", "heart", 40, ratio(i.healthGood.toDouble(), 4.0)),
        )
        listOf(500, 1_000, 5_000, 10_000, 25_000, 50_000, 100_000).forEachIndexed { n, target ->
            list += Achievement("ahorro-$target", "${fmtK(target)} ahorrados", "Junta ${fmtK(target)} entre ahorro e inversiones", "piggy", 15 + n * 15, ratio(saved, target.toDouble()))
        }
        listOf(10_000, 50_000, 100_000, 250_000).forEachIndexed { n, target ->
            list += Achievement("patrimonio-$target", "Patrimonio de ${fmtK(target)}", "Llega a ${fmtK(target)} de patrimonio total", "crown", 40 + n * 40, ratio(i.wealth.total, target.toDouble()))
        }
        return list
    }

    private fun fmtK(v: Int) = if (v >= 1000) "${v / 1000}.${"%03d".format(v % 1000)} €" else "$v €"
}
