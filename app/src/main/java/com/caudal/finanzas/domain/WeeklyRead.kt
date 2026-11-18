package com.caudal.finanzas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil

/** Un hallazgo de la semana: título, explicación, comprobaciones y la categoría a la que afecta. */
data class WeeklyInsight(val tag: SpendTag, val title: String, val detail: String, val checks: List<String>, val suggestedWeekly: Double?, val up: Boolean)

/** "Lectura semanal": en qué se te fue el dinero esta semana frente a tu semana normal (media de 8 semanas). Sin IA. */
data class WeeklyRead(val from: LocalDate, val to: LocalDate, val insights: List<WeeklyInsight>)

object WeeklyReader {
    fun of(d: FinanceData, fmt: (Double) -> String): WeeklyRead? {
        val es = java.util.Locale.forLanguageTag("es-ES")
        val today = d.today
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val variable = d.spending.filter { !Budgeting.isFixed(it, d.bills) }
        val week = variable.filter { it.epochDay in monday.toEpochDay()..today.toEpochDay() }
        if (week.isEmpty()) return null
        // Media de las 8 semanas anteriores, contando solo las que ya existían en la app.
        val first = d.expenses.minOfOrNull { it.epochDay } ?: return null
        val weeks = (1..8).map { monday.minusWeeks(it.toLong()) }.filter { it.plusDays(6).toEpochDay() >= first }
        if (weeks.isEmpty()) return null
        // La semana en curso aún no ha terminado: se compara con la misma parte de la semana normal.
        val partOfWeek = today.dayOfWeek.value / 7.0
        fun avgOf(tag: SpendTag) = weeks.sumOf { w -> variable.filter { it.spendTag() == tag && it.epochDay in w.toEpochDay()..w.plusDays(6).toEpochDay() }.sumOf { it.amount } } / weeks.size

        val insights = SpendTag.entries.mapNotNull { tag ->
            val now = week.filter { it.spendTag() == tag }
            val spent = now.sumOf { it.amount }
            val avgWeek = avgOf(tag)
            val expected = avgWeek * partOfWeek
            val diff = spent - expected
            if (abs(diff) < 10 || (spent < 5 && expected < 5)) return@mapNotNull null
            val pct = if (expected > 0) diff / expected * 100 else 100.0
            if (abs(pct) < 25) return@mapNotNull null
            val up = diff > 0
            val night = now.count { java.time.Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).hour.let { h -> h >= 22 || h < 5 } }
            val top = now.groupBy { Merchants.clean(it.note) }.maxByOrNull { it.value.size }
            val checks = mutableListOf<String>()
            if (up && night * 2 > now.size && now.size >= 3) checks += "Sobre todo de noche"
            checks += "Comparado con tu media de ${weeks.size} ${if (weeks.size == 1) "semana" else "semanas"}"
            val limit = if (up) ceil(avgWeek / 5) * 5 else null
            limit?.takeIf { it > 0 }?.let { checks += "Límite sugerido: ${fmt(it)}/semana" }
            WeeklyInsight(
                tag,
                if (up && pct > 300) "${tag.label} se dispara esta semana"
                else if (up) "${tag.label} sube un ${String.format(es, "%.0f", pct)} % esta semana"
                else "${tag.label} baja un ${String.format(es, "%.0f", abs(pct))} % esta semana",
                "${now.size} ${if (now.size == 1) "pago" else "pagos"}" +
                    (top?.takeIf { it.value.size >= 2 }?.let { ", sobre todo en ${it.key}" } ?: "") +
                    (if (up) ". Son ${fmt(diff)} más que en tu semana normal a estas alturas." else ". Llevas ${fmt(-diff)} menos que en una semana normal."),
                checks, limit, up,
            )
        }.sortedByDescending { it.up }
        if (insights.isEmpty()) return null
        return WeeklyRead(monday, today, insights.take(5))
    }
}
