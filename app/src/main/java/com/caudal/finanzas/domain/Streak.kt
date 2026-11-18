package com.caudal.finanzas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Cómo fue un día para la racha. */
enum class DayMark { NO_SPEND, OK, OVER, FUTURE }

/**
 * Racha: días seguidos gastando lo que te toca al día o menos (sin contar alquiler ni fijos, que son de una
 * vez al mes). Un día sin gastar nada también suma. Hoy cuenta si de momento no te has pasado.
 */
data class SpendStreak(
    val current: Int,
    val record: Int,
    /** De lunes a domingo de esta semana. */
    val week: List<Pair<LocalDate, DayMark>>,
    val today: DayMark,
    val spentToday: Double,
    val allowance: Double,
    /** Días sin gastar nada en lo que va de mes. */
    val noSpendThisMonth: Int,
    /** Próxima meta de racha (7, 14, 30…) o null si ya pasaste la última. */
    val nextGoal: Int?,
)

object Streaks {
    val GOALS = listOf(7, 14, 30, 60, 100, 180, 365)

    fun of(d: FinanceData, dailyAllowance: Double): SpendStreak {
        val today = d.today
        val allowance = dailyAllowance.coerceAtLeast(1.0)
        val spentBy = d.spending.filter { !Budgeting.isFixed(it, d.bills) }.groupBy { it.epochDay }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val first = (d.expenses.minOfOrNull { it.epochDay } ?: today.toEpochDay()).coerceAtLeast(today.toEpochDay() - 400)
        fun mark(day: Long): DayMark {
            if (day > today.toEpochDay()) return DayMark.FUTURE
            val s = spentBy[day] ?: 0.0
            return when { s <= 0.0 -> DayMark.NO_SPEND; s <= allowance -> DayMark.OK; else -> DayMark.OVER }
        }
        // Racha actual: hacia atrás desde hoy mientras no te pases.
        var current = 0
        var day = today.toEpochDay()
        while (day >= first && mark(day) != DayMark.OVER) { current++; day-- }
        // Récord: la racha más larga desde que hay datos.
        var record = 0; var run = 0
        for (x in first..today.toEpochDay()) { if (mark(x) == DayMark.OVER) run = 0 else { run++; record = maxOf(record, run) } }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = (0..6).map { monday.plusDays(it.toLong()) }.map { it to mark(it.toEpochDay()) }
        val monthStart = today.withDayOfMonth(1).toEpochDay()
        val noSpend = (maxOf(monthStart, first)..today.toEpochDay()).count { mark(it) == DayMark.NO_SPEND }
        return SpendStreak(
            current, maxOf(record, current), week, mark(today.toEpochDay()), spentBy[today.toEpochDay()] ?: 0.0, allowance,
            noSpend, GOALS.firstOrNull { it > current },
        )
    }
}
