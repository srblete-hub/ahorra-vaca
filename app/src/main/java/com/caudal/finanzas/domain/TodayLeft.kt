package com.caudal.finanzas.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")

/** Lo que te queda por gastar hoy: lo que toca al día menos lo gastado hoy (sin alquiler ni fijos). */
object TodayLeft {
    fun spentToday(d: FinanceData): Double =
        d.spending.filter { it.epochDay == d.today.toEpochDay() && !Budgeting.isFixed(it, d.bills) }.sumOf { it.amount }

    fun of(d: FinanceData, cash: CashPlan?): Pair<Double, Double>? {
        val perDay = cash?.perDayBefore?.takeIf { it > 0 } ?: return null
        return perDay to (perDay - spentToday(d))
    }

    /** Texto de la notificación tras un pago apuntado solo: (título, texto). */
    fun receipt(note: String, amount: Double, tag: SpendTag?, spentToday: Double, perDay: Double, fmt: (Double) -> String): Pair<String, String> {
        val left = perDay - spentToday
        val title = "Apuntado: $note · ${fmt(amount)}"
        val text = (tag?.takeIf { it != SpendTag.OTHER }?.let { "${it.label}. " } ?: "") +
            if (left >= 0) "Hoy te quedan ${fmt(left)} de ${fmt(perDay)}." else "Hoy te has pasado ${fmt(-left)}: se descuenta de los próximos días."
        return title to text
    }
}

/** Un recibo que pasa en los próximos días. */
data class UpcomingBill(val name: String, val amount: Double, val date: LocalDate) {
    fun whenText(today: LocalDate): String = when (java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()) {
        0 -> "hoy"
        1 -> "mañana"
        else -> "el " + date.dayOfWeek.getDisplayName(TextStyle.FULL, ES) + " ${date.dayOfMonth}"
    }
}

/** Recibos fijos que se cobran en los próximos [days] días y que aún no has pagado este mes. */
object UpcomingBills {
    fun of(d: FinanceData, days: Int = 3): List<UpcomingBill> {
        val today = d.today
        val m = java.time.YearMonth.from(today)
        val paid = d.spendingIn(m).map { Classifier.normalize(it.note) }
        return d.bills.mapNotNull { b ->
            val date = if (b.yearly) {
                runCatching { LocalDate.of(today.year, b.month, minOf(b.day, java.time.YearMonth.of(today.year, b.month).lengthOfMonth())) }.getOrNull()
            } else today.withDayOfMonth(minOf(b.day, m.lengthOfMonth()))
            val n = Classifier.normalize(b.name)
            when {
                date == null || date.isBefore(today) || date.isAfter(today.plusDays(days.toLong())) -> null
                paid.any { n.isNotBlank() && (it.contains(n) || n.contains(it.takeIf { p -> p.length >= 4 } ?: "\u0000")) } -> null
                else -> UpcomingBill(b.name, b.amount, date)
            }
        }.sortedBy { it.date }
    }
}
