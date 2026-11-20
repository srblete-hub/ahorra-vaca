package com.caudal.finanzas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil

/** Un hallazgo de la semana: título, explicación, comprobaciones y la categoría a la que afecta. */
data class WeeklyInsight(val tag: SpendTag, val title: String, val detail: String, val checks: List<String>, val suggestedWeekly: Double?, val up: Boolean)

/** "Lectura semanal": en qué se te fue el dinero esta semana frente a lo normal. Sin IA. */
data class WeeklyRead(val from: LocalDate, val to: LocalDate, val insights: List<WeeklyInsight>)

object WeeklyReader {
    /**
     * Gastos que van "a golpes": no se gastan cada semana (la gasolina, la ropa, un viaje…). Compararlos semana a
     * semana engaña: una semana sin repostar y otra con 30 € darían "+100 %". Estos se miran por meses.
     */
    val LUMPY = setOf(
        SpendTag.TRANSPORT, SpendTag.CLOTHES, SpendTag.SHOPPING, SpendTag.TRAVEL, SpendTag.HEALTH,
        SpendTag.SPORT, SpendTag.GIFTS, SpendTag.EDUCATION, SpendTag.PETS, SpendTag.HOME, SpendTag.SUBSCRIPTIONS,
    )

    private fun median(v: List<Double>): Double = v.sorted().let { s -> if (s.isEmpty()) 0.0 else if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

    fun of(d: FinanceData, fmt: (Double) -> String): WeeklyRead? {
        val es = java.util.Locale.forLanguageTag("es-ES")
        val today = d.today
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val variable = d.spending.filter { !Budgeting.isFixed(it, d.bills) }
        val first = d.expenses.minOfOrNull { it.epochDay } ?: return null
        fun sumIn(tag: SpendTag, from: LocalDate, to: LocalDate) =
            variable.filter { it.spendTag() == tag && it.epochDay in from.toEpochDay()..to.toEpochDay() }

        // Semanas anteriores completas que ya existían en la app.
        val weeks = (1..8).map { monday.minusWeeks(it.toLong()) }.filter { it.toEpochDay() >= first }
        val partOfWeek = today.dayOfWeek.value / 7.0

        val insights = SpendTag.entries.filter { it != SpendTag.OTHER }.mapNotNull { tag ->
            if (tag in LUMPY) {
                // Últimos 30 días frente a la media de los tres periodos de 30 días anteriores.
                val periods = (1..3).map { today.minusDays(30L * it) }.filter { it.minusDays(29).toEpochDay() >= first }
                if (periods.size < 2) return@mapNotNull null
                val now = sumIn(tag, today.minusDays(29), today)
                val spent = now.sumOf { it.amount }
                val avg = periods.sumOf { end -> sumIn(tag, end.minusDays(29), end).sumOf { it.amount } } / periods.size
                val diff = spent - avg
                if (abs(diff) < 25) return@mapNotNull null
                // Que caigan dos repostajes en un mes es normal: solo cuenta si un pago es mucho mayor de lo habitual
                // o si hay bastantes más pagos de los que sueles hacer.
                val before = variable.filter { it.spendTag() == tag && it.epochDay in today.minusDays(30L * periods.size + 29).toEpochDay() until today.minusDays(29).toEpochDay() }
                val usualTicket = median(before.map { it.amount })
                val usualCount = before.size.toDouble() / periods.size
                val bigTicket = now.any { usualTicket > 0 && it.amount > usualTicket * 2 && it.amount - usualTicket >= 25 }
                val manyMore = now.size >= usualCount + 2
                val muchLess = diff < 0 && avg > 0 && -diff / avg >= 0.5
                if (!bigTicket && !manyMore && !muchLess) return@mapNotNull null
                val up = diff > 0
                val top = now.maxByOrNull { it.amount }
                WeeklyInsight(
                    tag,
                    if (up) "${tag.label}: ${fmt(diff)} más este mes" else "${tag.label}: ${fmt(-diff)} menos este mes",
                    "En los últimos 30 días llevas ${fmt(spent)} y lo normal son unos ${fmt(avg)}" +
                        (top?.takeIf { up && it.amount >= diff * 0.6 }?.let { ". Casi todo es ${Merchants.clean(it.note).ifBlank { "un pago" }} (${fmt(it.amount)})" } ?: "") + ".",
                    listOf("Se mide por meses: es un gasto que no se hace cada semana", "Media de ${periods.size} meses"),
                    if (up) ceil(avg / 4.33 / 5) * 5 else null, up,
                )
            } else {
                // Gasto de diario: esta semana frente a la mediana de tus semanas (un mes raro no la tuerce).
                if (weeks.size < 3) return@mapNotNull null
                val perWeek = weeks.map { w -> sumIn(tag, w, w.plusDays(6)).sumOf { it.amount } }
                // Solo si es un gasto habitual: aparece en al menos la mitad de las semanas.
                if (perWeek.count { it > 0 } * 2 < perWeek.size) return@mapNotNull null
                val now = sumIn(tag, monday, today)
                val spent = now.sumOf { it.amount }
                val usual = median(perWeek)
                val expected = usual * partOfWeek
                val diff = spent - expected
                if (abs(diff) < 15) return@mapNotNull null
                val pct = if (expected >= 20) diff / expected * 100 else null
                if (pct != null && abs(pct) < 30) return@mapNotNull null
                val up = diff > 0
                val night = now.count { java.time.Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).hour.let { h -> h >= 22 || h < 5 } }
                val topPlace = now.groupBy { Merchants.clean(it.note) }.maxByOrNull { it.value.size }
                val checks = mutableListOf<String>()
                if (up && night * 2 > now.size && now.size >= 3) checks += "Sobre todo de noche"
                checks += "Frente a tu semana habitual (${weeks.size} semanas)"
                val limit = if (up) ceil(usual / 5) * 5 else null
                limit?.takeIf { it > 0 }?.let { checks += "Límite sugerido: ${fmt(it)}/semana" }
                WeeklyInsight(
                    tag,
                    when {
                        pct == null && up -> "${tag.label}: ${fmt(diff)} más esta semana"
                        pct == null -> "${tag.label}: ${fmt(-diff)} menos esta semana"
                        up -> "${tag.label} sube un ${String.format(es, "%.0f", pct)} % esta semana"
                        else -> "${tag.label} baja un ${String.format(es, "%.0f", abs(pct))} % esta semana"
                    },
                    "${now.size} ${if (now.size == 1) "pago" else "pagos"}" +
                        (topPlace?.takeIf { it.value.size >= 2 }?.let { ", sobre todo en ${it.key}" } ?: "") +
                        (if (up) ". Son ${fmt(diff)} más que en tu semana habitual a estas alturas." else ". Llevas ${fmt(-diff)} menos que en una semana habitual."),
                    checks, limit, up,
                )
            }
        }.sortedByDescending { it.up }
        if (insights.isEmpty()) return null
        return WeeklyRead(monday, today, insights.take(5))
    }
}
