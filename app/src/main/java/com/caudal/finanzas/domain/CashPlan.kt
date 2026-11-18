package com.caudal.finanzas.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** Una entrada (+) o salida (−) de dinero en el banco antes de la nómina. */
data class CashEvent(val date: LocalDate, val label: String, val amount: Double, val estimated: Boolean = false)

/**
 * Tu dinero del banco hasta la próxima nómina: cuánto puedes gastar al día para llegar con el colchón
 * que elijas, contando los recibos que vienen (alquiler…) y lo que entra antes (YouTube el 22…).
 * Si hasta que llega el ingreso grande vas más justo, se divide en dos tramos.
 */
data class CashPlan(
    val today: LocalDate,
    val payday: LocalDate,
    val paydayName: String,
    val balance: Double,
    val cushion: Double,
    val events: List<CashEvent>,
    /** Gasto diario permitido en el primer tramo (o en todo el periodo si no hay dos). */
    val perDayBefore: Double,
    /** Gasto diario del segundo tramo (desde [splitDate]); null si es un solo tramo. */
    val perDayAfter: Double?,
    val splitDate: LocalDate?,
    /** Saldo previsto cada día (gastando lo permitido). */
    val series: List<Pair<LocalDate, Double>>,
) {
    val days get() = ChronoUnit.DAYS.between(today, payday).toInt().coerceAtLeast(1)
    /** Si no se llega con el colchón: cuánto falta. */
    val shortfall get() = if (perDayBefore < 0) -perDayBefore * days else 0.0
}

object CashPlanner {
    fun of(d: FinanceData, cushion: Double): CashPlan? {
        val today = d.today
        val bank = d.accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK }
        if (bank.isEmpty()) return null
        val bankIds = bank.map { it.id }.toSet()
        val balance = bank.sumOf { it.balance }

        // La nómina: el ingreso fijo más grande. Próximo cobro que aún no ha llegado.
        val salary = d.sources.filter { !it.variable }.maxByOrNull { it.amount } ?: return null
        val m = YearMonth.from(today)
        fun received(sourceId: Long, ym: YearMonth) = d.incomes.any { it.sourceId == sourceId && YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == ym }
        val thisPay = m.atDay(min(salary.dayFrom, m.lengthOfMonth()))
        val payday = if (!received(salary.id, m) && !thisPay.isBefore(today)) thisPay
            else m.plusMonths(1).let { it.atDay(min(salary.dayFrom, it.lengthOfMonth())) }
        if (!payday.isAfter(today)) return null

        val events = mutableListOf<CashEvent>()
        // Otros cobros antes de la nómina (YouTube el 22, el resto de la nómina…).
        IncomeMath.forMonth(d.sources, d.incomes, m).sources.filter { it.source.id != salary.id && !it.isReceived && it.amount > 0 }.forEach { sm ->
            val s = sm.source
            if (s.accountId != null && s.accountId !in bankIds) return@forEach
            val date = m.atDay(min(s.dayFrom, m.lengthOfMonth())).let { if (it.isBefore(today)) today else it }
            if (date.isBefore(payday)) {
                val amount = if (s.variable && sm.confirmed == null) Math.floor(sm.amount * 0.7 / 5) * 5 else sm.amount
                events += CashEvent(date, s.name, amount, estimated = s.variable && sm.confirmed == null)
            }
        }
        // Recibos que se pagan antes de la nómina y aún no están pagados.
        d.bills.filter { it.accountId == null || it.accountId in bankIds }.forEach { b ->
            var ym = m
            repeat(2) {
                val due = ym.atDay(min(b.day, ym.lengthOfMonth()))
                val applies = !b.yearly || b.month == ym.monthValue
                val paid = d.expenses.any { e -> YearMonth.from(LocalDate.ofEpochDay(e.epochDay)) == ym && Budgeting.isBillPayment(e, listOf(b)) }
                if (applies && !paid && due.isBefore(payday)) {
                    events += CashEvent(if (due.isBefore(today)) today else due, b.name, -b.amount)
                }
                ym = ym.plusMonths(1)
            }
        }
        events.sortBy { it.date }

        val days = ChronoUnit.DAYS.between(today, payday).toInt()
        fun dayIndex(x: LocalDate) = ChronoUnit.DAYS.between(today, x).toInt()
        fun net(upTo: Int) = events.filter { dayIndex(it.date) <= upTo }.sumOf { it.amount }
        val total = balance + events.sumOf { it.amount } - cushion

        // Un solo ritmo: lo que sobra repartido entre los días. Hay que comprobar que antes del ingreso grande no se baja del colchón.
        val uniform = total / days
        val big = events.filter { it.amount > 0 }.maxByOrNull { it.amount }
        var before = uniform
        var after: Double? = null
        var split: LocalDate? = null
        val firstOk = (0 until days).all { i -> balance + net(i) - uniform * (i + 1) >= cushion - 0.01 }
        if (!firstOk && big != null && dayIndex(big.date) in 1 until days) {
            val k = dayIndex(big.date)
            before = minOf((0 until k).minOf { i -> (balance + net(i) - cushion) / (i + 1) }, uniform)
            val rest = total - before * k
            after = rest / (days - k)
            split = big.date
        } else if (!firstOk) {
            before = (0 until days).minOf { i -> (balance + net(i) - cushion) / (i + 1) }
        }
        val rate = { i: Int -> if (split != null && i >= dayIndex(split)) after!! else before }
        var bal = balance
        val series = (0..days).map { i ->
            if (i > 0) bal -= max(0.0, rate(i - 1))
            bal += events.filter { dayIndex(it.date) == i }.sumOf { it.amount }
            today.plusDays(i.toLong()) to bal
        }
        return CashPlan(today, payday, salary.name, balance, cushion, events, before, after, split, series)
    }
}
