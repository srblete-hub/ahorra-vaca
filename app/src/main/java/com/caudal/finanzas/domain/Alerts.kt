package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.RecurringBill
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Los límites calculados la última vez que se abrió la app, para poder avisar en segundo plano. */
data class LimitSnapshot(val perDay: Double, val weekOptimal: Double, val tagMonthly: Map<SpendTag, Double>) {
    fun encode(): String = "$perDay|$weekOptimal|" + tagMonthly.entries.joinToString(";") { "${it.key.name}:${it.value}" }

    companion object {
        fun decode(raw: String?): LimitSnapshot? {
            val p = raw?.split('|') ?: return null
            if (p.size != 3) return null
            val tags = p[2].split(';').mapNotNull { e -> e.split(':').takeIf { it.size == 2 }?.let { (k, v) -> SpendTag.of(k)?.let { t -> v.toDoubleOrNull()?.let { t to it } } } }.toMap()
            return LimitSnapshot(p[0].toDoubleOrNull() ?: return null, p[1].toDoubleOrNull() ?: return null, tags)
        }
    }
}

/** Un aviso: [key] evita repetirlo el mismo día; [severe] = ya te has pasado. */
data class LimitAlert(val key: String, val title: String, val text: String, val severe: Boolean)

/** Avisos al acercarte (80 %) o pasarte (100 %) de lo de hoy, lo óptimo de la semana o un tipo de gasto. */
object LimitAlerts {
    fun check(today: LocalDate, expenses: List<Expense>, bills: List<RecurringBill>, savingIds: Set<Long>, snap: LimitSnapshot, fmt: (Double) -> String): List<LimitAlert> {
        fun isBill(e: Expense) = Budgeting.isBillPayment(e, bills)
        val variable = expenses.filter { it.categoryId !in savingIds && !isBill(it) }
        val out = mutableListOf<LimitAlert>()

        val todaySpent = variable.filter { it.epochDay == today.toEpochDay() }.sumOf { it.amount }
        if (snap.perDay > 0) when {
            todaySpent >= snap.perDay -> out += LimitAlert("day100", "Has llegado a tu gasto de hoy", "Llevas ${fmt(todaySpent)} y hoy tocaban ${fmt(snap.perDay)}. Lo que gastes de más se descuenta de los próximos días.", true)
            todaySpent >= snap.perDay * 0.8 -> out += LimitAlert("day80", "Casi en tu límite de hoy", "Llevas ${fmt(todaySpent)} de ${fmt(snap.perDay)}: te quedan ${fmt(snap.perDay - todaySpent)} para hoy.", false)
        }

        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        val week = variable.filter { it.epochDay in monday..today.toEpochDay() }.sumOf { it.amount }
        if (snap.weekOptimal > 0) when {
            week >= snap.weekOptimal -> out += LimitAlert("week100", "Has superado lo óptimo de la semana", "Llevas ${fmt(week)} desde el lunes; lo óptimo eran ${fmt(snap.weekOptimal)}.", true)
            week >= snap.weekOptimal * 0.85 -> out += LimitAlert("week85", "Cerca del límite de la semana", "Llevas ${fmt(week)} de ${fmt(snap.weekOptimal)} y aún queda semana.", false)
        }

        val month = YearMonth.from(today)
        val byTag = variable.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }.groupBy { it.spendTag() }.mapValues { (_, l) -> l.sumOf { it.amount } }
        snap.tagMonthly.forEach { (tag, limit) ->
            val spent = byTag[tag] ?: 0.0
            if (limit <= 0) return@forEach
            when {
                spent >= limit -> out += LimitAlert("tag100-${tag.name}", "Te has pasado en ${tag.label.lowercase()}", "Llevas ${fmt(spent)} este mes y tu límite era ${fmt(limit)}.", true)
                spent >= limit * 0.8 -> out += LimitAlert("tag80-${tag.name}", "Llevas el ${(spent / limit * 100).toInt()} % de ${tag.label.lowercase()}", "${fmt(spent)} de ${fmt(limit)} este mes: te quedan ${fmt(limit - spent)}.", false)
            }
        }
        return out
    }
}
