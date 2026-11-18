package com.caudal.finanzas.domain

import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Lo que Pepe propone esta semana: pasar una cantidad segura a tu ahorro sin bajar del colchón antes de cobrar. */
data class PepeTip(val amount: Double, val text: String, val fromId: Long, val toId: Long, val toName: String)

/**
 * "Pepe te recomienda": reglas fijas, sin IA. Mira el punto más bajo de tu saldo previsto hasta la nómina
 * (gastando lo de cada día) y propone pasar lo que sobra por encima del colchón, con un margen, a tu ahorro.
 */
object PepeSuggest {
    /** Margen de seguridad por encima del colchón. */
    const val MARGIN = 25.0

    fun weekKey(d: LocalDate) = "${d.get(IsoFields.WEEK_BASED_YEAR)}-${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    fun of(d: FinanceData, plan: CashPlan?, goal: GoalStatus?, fmt: (Double) -> String): PepeTip? {
        plan ?: return null
        if (plan.perDayBefore < 0) return null
        val main = d.accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK }.maxByOrNull { it.balance } ?: return null
        val target = d.accounts.filter { it.isSaving() }.maxByOrNull { it.interestRate * 1_000_000 + it.balance } ?: return null
        val low = plan.series.drop(1).minOfOrNull { it.second } ?: return null
        var amount = floor((low - plan.cushion - MARGIN) / 10) * 10
        if (goal != null) amount = min(amount, max(0.0, goal.goal.target - goal.current))
        if (amount < 10) return null
        val where = goal?.goal?.name?.takeIf { it.isNotBlank() }?.let { "«$it»" } ?: target.name
        return PepeTip(
            amount,
            "Pasa ${fmt(amount)} a $where esta semana. Seguirás llegando a la nómina con ${fmt(plan.cushion)} de colchón.",
            main.id, target.id, target.name,
        )
    }

    /** Lo que has ido metiendo en el ahorro las últimas [weeks] semanas (de la más antigua a la actual). */
    fun weekly(d: FinanceData, transfers: List<com.caudal.finanzas.data.Transfer>, weeks: Int = 5): List<Pair<String, Double>> {
        val saving = d.accounts.filter { it.isSaving() }.map { it.id }.toSet()
        val monday = d.today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        return (weeks - 1 downTo 0).map { w ->
            val from = monday.minusWeeks(w.toLong())
            val to = from.plusDays(6)
            val sum = transfers.filter { it.toAccountId in saving && it.fromAccountId !in saving && it.epochDay in from.toEpochDay()..to.toEpochDay() }.sumOf { it.amount }
            (if (w == 0) "Esta" else "S${weeks - w}") to sum
        }
    }
}
