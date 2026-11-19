package com.caudal.finanzas.domain

import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.RecurringBill
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Un aviso del móvil: [key] sirve para no repetirlo. */
data class Reminder(val key: String, val title: String, val text: String)

/** Una suscripción o recibo con su próximo cobro, para la lista de suscripciones. */
data class NextCharge(val bill: RecurringBill, val date: LocalDate) {
    fun daysFrom(today: LocalDate) = ChronoUnit.DAYS.between(today, date).toInt()
}

/**
 * Cuándo se cobra cada gasto fijo y qué avisos tocan: antes de cada cobro, cuando acaba una prueba
 * gratis y cuando en el banco queda poco para lo que te van a cobrar. Solo reglas.
 */
object BillReminders {
    /** ¿Ya está pagado el recibo [b] en el mes [m]? */
    fun paidIn(d: FinanceData, b: RecurringBill, m: YearMonth): Boolean = d.spendingIn(m).any { e ->
        e.billId == b.id || (b.name.isNotBlank() && e.note.contains(b.name, ignoreCase = true) && kotlin.math.abs(e.amount - b.amount) <= b.amount * 0.1)
    }

    private fun dateIn(b: RecurringBill, m: YearMonth) = m.atDay(minOf(b.day, m.lengthOfMonth()))

    /** Próximo cobro: este mes si aún no ha pasado ni está pagado; si no, el siguiente. */
    fun next(d: FinanceData, b: RecurringBill): LocalDate {
        val today = d.today
        if (b.yearly) {
            val thisYear = dateIn(b, YearMonth.of(today.year, b.month))
            return if (thisYear.isBefore(today) || paidIn(d, b, YearMonth.from(thisYear))) dateIn(b, YearMonth.of(today.year + 1, b.month)) else thisYear
        }
        val m = YearMonth.from(today)
        val here = dateIn(b, m)
        return if (here.isBefore(today) || paidIn(d, b, m)) dateIn(b, m.plusMonths(1)) else here
    }

    fun upcoming(d: FinanceData, bills: List<RecurringBill> = d.bills): List<NextCharge> =
        bills.map { NextCharge(it, next(d, it)) }.sortedBy { it.date }

    fun isSubscription(b: RecurringBill) = b.trialEnds != null || Classifier.guess(b.name) == SpendTag.SUBSCRIPTIONS

    private fun whenText(days: Int, date: LocalDate) = when (days) {
        0 -> "hoy"
        1 -> "mañana"
        else -> "el " + date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-ES")) + " ${date.dayOfMonth}"
    }

    /**
     * Avisos que tocan hoy. [daysBefore] = con cuántos días de antelación avisar de un cobro (0 = no avisar).
     * [done] = claves ya avisadas (cada cobro, prueba o aviso de saldo se avisa una sola vez).
     */
    fun due(d: FinanceData, daysBefore: Int, done: Set<String>, fmt: (Double) -> String): List<Reminder> {
        val today = d.today
        val out = mutableListOf<Reminder>()

        // 1. Pruebas gratis que se acaban: lo más urgente, para cancelar a tiempo.
        d.bills.filter { it.trialEnds != null }.forEach { b ->
            val end = LocalDate.ofEpochDay(b.trialEnds!!)
            val days = ChronoUnit.DAYS.between(today, end).toInt()
            val key = "trial-${b.id}-${end.toEpochDay()}"
            if (days in 0..2 && key !in done) out += Reminder(
                key, "Tu prueba de ${b.name} se acaba ${whenText(days, end)}",
                "Si no la vas a usar, cancélala antes. Si no, te cobrarán ${fmt(b.amount)} ${if (b.yearly) "al año" else "al mes"}.",
            )
        }

        // 2. Cobros que llegan (los que están en prueba gratis no se cobran aún).
        if (daysBefore > 0) {
            val soon = upcoming(d, d.bills.filter { it.remind && (it.trialEnds == null || LocalDate.ofEpochDay(it.trialEnds).isBefore(today)) })
                .filter { it.daysFrom(today) in 0..daysBefore && "bill-${it.bill.id}-${it.date.toEpochDay()}" !in done }
            if (soon.size == 1) {
                val c = soon.first()
                out += Reminder(
                    "bill-${c.bill.id}-${c.date.toEpochDay()}",
                    "${whenText(c.daysFrom(today), c.date).replaceFirstChar { it.uppercase() }} te cobran ${c.bill.name}",
                    "${fmt(c.bill.amount)}. " + if (isSubscription(c.bill)) "Si ya no la usas, aún estás a tiempo de darte de baja." else "Que no te pille sin saldo.",
                )
            } else if (soon.size > 1) {
                out += Reminder(
                    soon.joinToString(",") { "bill-${it.bill.id}-${it.date.toEpochDay()}" },
                    "Te van a cobrar ${soon.size} recibos · ${fmt(soon.sumOf { it.bill.amount })}",
                    soon.joinToString(" · ") { "${it.bill.name} ${whenText(it.daysFrom(today), it.date)} (${fmt(it.bill.amount)})" },
                )
            }
        }

        // 3. Queda poco en el banco para lo que te cobran en 7 días.
        val bank = d.accounts.filter { it.type == AccountType.BANK }.sumOf { it.balance }
        val week = upcoming(d, d.bills.filter { it.trialEnds == null || !LocalDate.ofEpochDay(it.trialEnds).isAfter(today) })
            .filter { it.daysFrom(today) in 0..7 }
        val due = week.sumOf { it.bill.amount }
        val first = week.firstOrNull()
        if (first != null && d.accounts.any { it.type == AccountType.BANK } && bank < due) {
            val key = "low-${first.date.toEpochDay()}"
            if (key !in done) out += Reminder(
                key, "Te queda poco en el banco",
                "Tienes ${fmt(bank)} y en los próximos 7 días te cobran ${fmt(due)} (${week.joinToString(", ") { it.bill.name }}). Te faltan ${fmt(due - bank)}.",
            )
        }
        return out
    }
}

/** Suscripciones típicas en España con su precio orientativo al mes (se puede cambiar al añadirla). */
object SubscriptionCatalog {
    data class Item(val name: String, val price: Double)

    val ITEMS = listOf(
        Item("Netflix", 13.99), Item("Spotify", 11.99), Item("HBO Max", 10.99), Item("Disney+", 9.99),
        Item("Amazon Prime", 4.99), Item("YouTube Premium", 13.99), Item("Apple Music", 10.99), Item("iCloud+", 0.99),
        Item("Google One", 1.99), Item("ChatGPT Plus", 23.00), Item("DAZN", 19.99), Item("Movistar Plus+", 9.99),
        Item("Filmin", 8.99), Item("Crunchyroll", 6.99), Item("Xbox Game Pass", 14.99), Item("PlayStation Plus", 8.99),
        Item("Gimnasio", 30.00),
    )
}
