package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

// ---------- Modo viaje ----------

/** Un viaje: lo que gastes entre [start] y [end] (epochDay) va aparte y no cuenta en la media del día a día. */
data class Trip(val name: String, val budget: Double, val start: Long, val end: Long) {
    fun covers(day: Long) = day in start..end
    fun isActive(today: LocalDate) = covers(today.toEpochDay())
    val days get() = (end - start + 1).toInt()

    companion object {
        fun parseAll(raw: String): List<Trip> = raw.split(';').mapNotNull { t ->
            val p = t.split('|')
            if (p.size != 4) null else runCatching { Trip(p[0], p[1].toDouble(), p[2].toLong(), p[3].toLong()) }.getOrNull()
        }
        fun encodeAll(trips: List<Trip>) = trips.joinToString(";") { "${it.name.replace("|", " ").replace(";", " ")}|${it.budget}|${it.start}|${it.end}" }
    }
}

/** Los viajes guardados. Lo rellena [com.caudal.finanzas.data.Prefs] al leer los ajustes. */
object Trips {
    @Volatile var all: List<Trip> = emptyList()

    fun active(today: LocalDate = LocalDate.now()) = all.firstOrNull { it.isActive(today) }
    /** Un pago es del viaje si cae en sus días (y no es un recibo fijo). */
    fun covers(e: Expense) = e.billId == null && all.any { it.covers(e.epochDay) }

    data class Status(val trip: Trip, val spent: Double, val left: Double, val daysLeft: Int, val perDayLeft: Double)

    fun status(d: FinanceData, trip: Trip): Status {
        val spent = d.spending.filter { trip.covers(it.epochDay) && it.billId == null }.sumOf { it.amount }
        val daysLeft = (trip.end - d.today.toEpochDay() + 1).toInt().coerceIn(0, trip.days)
        val left = trip.budget - spent
        return Status(trip, spent, left, daysLeft, if (daysLeft > 0) left / daysLeft else 0.0)
    }
}

// ---------- Resumen de la noche ----------

object NightSummary {
    /** El aviso de las 21:00: lo gastado hoy frente a lo que tocaba, si mañana te cobran algo y cómo va el viaje. */
    fun of(d: FinanceData, perDay: Double?, fmt: (Double) -> String): Reminder {
        val spent = TodayLeft.spentToday(d)
        val parts = mutableListOf<String>()
        val title = when {
            spent <= 0.0 -> "Hoy no has gastado nada"
            perDay == null || perDay <= 0 -> "Hoy has gastado ${fmt(spent)}"
            spent <= perDay -> "Hoy has gastado ${fmt(spent)} de ${fmt(perDay)}"
            else -> "Hoy te has pasado ${fmt(spent - perDay)}"
        }
        if (perDay != null && perDay > 0) parts += when {
            spent <= 0.0 -> "Día sin gastar: los ${fmt(perDay)} de hoy se reparten en los próximos días."
            spent <= perDay -> "Te sobran ${fmt(perDay - spent)}: mañana tendrás un poco más."
            else -> "Tocaba ${fmt(perDay)}: lo que te pasaste se descuenta poco a poco de los próximos días."
        }
        val tomorrow = BillReminders.upcoming(d).filter { it.daysFrom(d.today) == 1 && (it.bill.trialEnds == null || it.bill.trialEnds < d.today.toEpochDay()) }
        if (tomorrow.isNotEmpty()) parts += "Mañana te cobran " + tomorrow.joinToString(" y ") { "${it.bill.name} (${fmt(it.bill.amount)})" } + "."
        Trips.active(d.today)?.let { t ->
            val s = Trips.status(d, t)
            parts += if (s.left >= 0) "${t.name}: te quedan ${fmt(s.left)} del viaje." else "${t.name}: llevas ${fmt(-s.left)} por encima del presupuesto."
        }
        return Reminder("night-${d.today.toEpochDay()}", title, parts.joinToString(" ").ifBlank { "Mañana más." })
    }
}

// ---------- Retos de ahorro ----------

/** Un reto: aguantar [days] días sin un tipo de gasto, por debajo de lo que toca o sin gastar nada. */
data class Challenge(val id: String, val title: String, val hint: String, val days: Int, val kind: Kind, val tag: SpendTag? = null, val words: List<String> = emptyList()) {
    enum class Kind { AVOID, UNDER_DAILY, NO_SPEND }
    /** Litros de leche que da la vaca al cumplirlo. */
    val litres get() = days
}

data class ChallengeStatus(val challenge: Challenge, val start: LocalDate, val day: Int, val failedOn: LocalDate?, val done: Boolean) {
    val end: LocalDate get() = start.plusDays(challenge.days - 1L)
    val progress get() = if (done) 1.0 else (day - 1).coerceAtLeast(0).toDouble() / challenge.days
}

object Challenges {
    private val DELIVERY = listOf("glovo", "just eat", "uber eats", "deliveroo", "telepizza", "dominos", "domino s")

    val ALL = listOf(
        Challenge("delivery", "Una semana sin pedir a domicilio", "Ni Glovo, ni Just Eat, ni pizza a casa", 7, Challenge.Kind.AVOID, words = DELIVERY),
        Challenge("bares", "Una semana sin bares ni restaurantes", "Cafés y cañas, en casa", 7, Challenge.Kind.AVOID, tag = SpendTag.FOOD),
        Challenge("caprichos", "Una semana sin compras por capricho", "Ropa, Amazon, cositas… que esperen", 7, Challenge.Kind.AVOID, tag = SpendTag.SHOPPING),
        Challenge("tres-dias", "3 días por debajo de lo que toca", "Sin pasarte ningún día", 3, Challenge.Kind.UNDER_DAILY),
        Challenge("cero", "Un día sin gastar nada", "Ni un euro (los recibos no cuentan)", 1, Challenge.Kind.NO_SPEND),
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }

    private fun breaks(c: Challenge, e: Expense): Boolean = when (c.kind) {
        Challenge.Kind.AVOID -> (c.tag != null && e.spendTag() == c.tag) || (c.words.isNotEmpty() && Classifier.normalize(e.note).let { n -> c.words.any { it in n } })
        else -> false
    }

    fun status(d: FinanceData, c: Challenge, start: LocalDate, perDay: Double): ChallengeStatus {
        val today = d.today
        val end = start.plusDays(c.days - 1L)
        val variable = d.spending.filter { !Budgeting.isFixed(it, d.bills) && it.epochDay in start.toEpochDay()..minOf(today, end).toEpochDay() }
        val byDay = variable.groupBy { it.epochDay }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val failed: LocalDate? = when (c.kind) {
            Challenge.Kind.AVOID -> variable.filter { breaks(c, it) }.minOfOrNull { it.epochDay }?.let(LocalDate::ofEpochDay)
            Challenge.Kind.UNDER_DAILY -> byDay.filter { it.value > perDay.coerceAtLeast(1.0) }.keys.minOrNull()?.let(LocalDate::ofEpochDay)
            Challenge.Kind.NO_SPEND -> byDay.filter { it.value > 0 }.keys.minOrNull()?.let(LocalDate::ofEpochDay)
        }
        val day = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, c.days)
        return ChallengeStatus(c, start, day, failed, done = failed == null && today.isAfter(end))
    }

    /** Tres retos que te vienen bien: primero el del tipo de gasto en que más se te va. */
    fun suggest(d: FinanceData): List<Challenge> {
        val month = d.spending.filter { it.epochDay >= d.today.minusDays(30).toEpochDay() && !Budgeting.isFixed(it, d.bills) }
        val delivery = month.filter { e -> Classifier.normalize(e.note).let { n -> DELIVERY.any { it in n } } }.sumOf { it.amount }
        val ranked = listOf(
            byId("delivery")!! to delivery,
            byId("bares")!! to month.filter { it.spendTag() == SpendTag.FOOD }.sumOf { it.amount } - delivery,
            byId("caprichos")!! to month.filter { it.spendTag() == SpendTag.SHOPPING }.sumOf { it.amount },
        ).sortedByDescending { it.second }
        return listOf(ranked.first().first, byId("tres-dias")!!, byId("cero")!!)
    }
}

// ---------- Hucha de redondeo ----------

object RoundUp {
    /** Lunes de la semana pasada: la hucha se calcula con semanas cerradas, así el importe no cambia. */
    fun lastWeek(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

    /** Lo que suma redondear al euro cada gasto del día a día de esa semana (3,40 € → 0,60 €). */
    fun of(d: FinanceData, monday: LocalDate = lastWeek(d.today)): Double {
        val range = monday.toEpochDay()..monday.plusDays(6).toEpochDay()
        val cents = d.spending.filter { it.epochDay in range && !Budgeting.isFixed(it, d.bills) }
            .sumOf { e -> val c = Math.round(e.amount * 100); (ceil(c / 100.0) * 100).toLong() - c }
        return cents / 100.0
    }
}

// ---------- Comparar meses ----------

data class MonthCompare(val month: YearMonth, val days: Int?, val now: Double, val before: Double, val rows: List<Triple<SpendTag, Double, Double>>)

object MonthComparer {
    /**
     * Este mes frente al anterior, por tipo de gasto (sin fijos). Si es el mes en curso, compara los mismos
     * días (del 1 al de hoy), para que sea justo. Lo que más ha cambiado sale primero.
     */
    fun of(d: FinanceData, month: YearMonth): MonthCompare? {
        val prev = month.minusMonths(1)
        val days = if (month == YearMonth.from(d.today)) d.today.dayOfMonth else null
        fun spend(m: YearMonth) = Budgeting.variable(d, m).filter { days == null || LocalDate.ofEpochDay(it.epochDay).dayOfMonth <= days }
        val a = spend(month)
        val b = spend(prev)
        if (b.isEmpty()) return null
        val tags = (a + b).map { it.spendTag() }.distinct()
        val rows = tags.map { t -> Triple(t, a.filter { it.spendTag() == t }.sumOf { it.amount }, b.filter { it.spendTag() == t }.sumOf { it.amount }) }
            .filter { it.second + it.third >= 1 }
            .sortedByDescending { kotlin.math.abs(it.second - it.third) }
        return MonthCompare(month, days, a.sumOf { it.amount }, b.sumOf { it.amount }, rows)
    }
}
