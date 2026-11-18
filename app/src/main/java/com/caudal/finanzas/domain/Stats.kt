package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import java.time.LocalDate
import java.time.YearMonth

/** Estadísticas de gasto de un periodo: total, el periodo anterior, por categorías, por meses y lo último. */
data class SpendStats(
    val total: Double,
    /** Lo gastado en el mismo tramo del periodo anterior (0 si no hay datos). */
    val prev: Double,
    val byTag: List<Pair<SpendTag, Double>>,
    val bars: List<Pair<YearMonth, Double>>,
    val recent: List<Expense>,
    val prevLabel: String,
) {
    /** Cambio frente al periodo anterior en %, solo si hay con qué comparar. */
    val changePct get() = if (prev > 0) (total - prev) / prev * 100 else null
}

/** Una barra del gráfico: etiqueta, gasto y si es el periodo actual. */
data class Bar(val label: String, val value: Double, val current: Boolean)

/** Analítica de Movimientos: total gastado del periodo, el anterior y las barras (días, meses). */
data class Analytics(val total: Double, val prev: Double, val bars: List<Bar>, val title: String, val prevLabel: String) {
    val changePct get() = if (prev > 0) (total - prev) / prev * 100 else null
}

/** Periodos: 0 = este mes, 1 = últimos 3 meses, 2 = este año. Sin alquiler ni fijos (son de una vez al mes). */
object Stats {
    private val ES = java.util.Locale.forLanguageTag("es-ES")
    private fun monthShort(ym: YearMonth) = ym.month.getDisplayName(java.time.format.TextStyle.SHORT, ES).trimEnd('.').replaceFirstChar { it.uppercase() }

    /** 0 = semana (lunes a domingo), 1 = mes (últimos 6 meses en barras), 2 = año (meses del año). Sin alquiler ni fijos. */
    fun analytics(d: FinanceData, kind: Int): Analytics {
        val today = d.today
        val m = YearMonth.from(today)
        val variable = d.spending.filter { !Budgeting.isFixed(it, d.bills) }
        fun sum(a: LocalDate, b: LocalDate) = variable.filter { it.epochDay in a.toEpochDay()..b.toEpochDay() }.sumOf { it.amount }
        fun has(a: LocalDate) = (d.expenses.minOfOrNull { it.epochDay } ?: Long.MAX_VALUE) <= a.toEpochDay()
        return when (kind) {
            0 -> {
                val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val days = (0..6).map { monday.plusDays(it.toLong()) }
                val names = listOf("L", "M", "X", "J", "V", "S", "D")
                Analytics(
                    sum(monday, today), if (has(monday.minusWeeks(1))) sum(monday.minusWeeks(1), today.minusWeeks(1)) else 0.0,
                    days.mapIndexed { i, day -> Bar(names[i], if (day.isAfter(today)) 0.0 else sum(day, day), day == today) },
                    "Esta semana", "vs semana pasada",
                )
            }
            1 -> {
                val prevM = m.minusMonths(1)
                Analytics(
                    sum(m.atDay(1), today), if (has(prevM.atDay(1))) sum(prevM.atDay(1), prevM.atDay(minOf(today.dayOfMonth, prevM.lengthOfMonth()))) else 0.0,
                    (5 downTo 0).map { m.minusMonths(it.toLong()) }.map { ym -> Bar(monthShort(ym), sum(ym.atDay(1), ym.atEndOfMonth()), ym == m) },
                    m.month.getDisplayName(java.time.format.TextStyle.FULL, ES).replaceFirstChar { it.uppercase() } + " ${m.year}", "vs mes anterior",
                )
            }
            else -> {
                val start = today.withDayOfYear(1)
                Analytics(
                    sum(start, today), if (has(start.minusYears(1))) sum(start.minusYears(1), today.minusYears(1)) else 0.0,
                    (1..12).map { YearMonth.of(m.year, it) }.map { ym -> Bar(monthShort(ym).take(1), if (ym.isAfter(m)) 0.0 else sum(ym.atDay(1), ym.atEndOfMonth()), ym == m) },
                    "Año ${m.year}", "vs año pasado",
                )
            }
        }
    }

    fun of(d: FinanceData, period: Int): SpendStats {
        val today = d.today
        val m = YearMonth.from(today)
        fun day(ym: YearMonth, dom: Int) = ym.atDay(minOf(dom, ym.lengthOfMonth()))
        val (from, prevFrom, prevTo) = when (period) {
            0 -> Triple(m.atDay(1), m.minusMonths(1).atDay(1), day(m.minusMonths(1), today.dayOfMonth))
            1 -> Triple(m.minusMonths(2).atDay(1), m.minusMonths(5).atDay(1), day(m.minusMonths(3), today.dayOfMonth))
            else -> Triple(today.withDayOfYear(1), today.minusYears(1).withDayOfYear(1), today.minusYears(1))
        }
        val variable = d.spending.filter { !Budgeting.isFixed(it, d.bills) }
        fun inRange(e: Expense, a: LocalDate, b: LocalDate) = e.epochDay in a.toEpochDay()..b.toEpochDay()
        val now = variable.filter { inRange(it, from, today) }
        val before = variable.filter { inRange(it, prevFrom, prevTo) }
        val hasPrev = d.expenses.any { it.epochDay <= prevTo.toEpochDay() && it.epochDay >= prevFrom.toEpochDay() }
        val months = if (period == 2) (1..m.monthValue).map { YearMonth.of(m.year, it) } else (5 downTo 0).map { m.minusMonths(it.toLong()) }
        val bars = months.map { ym -> ym to variable.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == ym }.sumOf { it.amount } }
        return SpendStats(
            total = now.sumOf { it.amount },
            prev = if (hasPrev) before.sumOf { it.amount } else 0.0,
            byTag = now.groupBy { it.spendTag() }.map { (t, l) -> t to l.sumOf { it.amount } }.sortedByDescending { it.second },
            bars = bars,
            recent = now.sortedWith(compareByDescending<Expense> { it.epochDay }.thenByDescending { it.createdAt }).take(8),
            prevLabel = when (period) { 0 -> "vs mes anterior"; 1 -> "vs 3 meses anteriores"; else -> "vs año pasado" },
        )
    }
}
