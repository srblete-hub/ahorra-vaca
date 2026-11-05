package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val ES = Locale.forLanguageTag("es-ES")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM", ES)
private fun Long.date(): LocalDate = LocalDate.ofEpochDay(this)
private val FEE_WORDS = listOf("comision", "comisiones", "cuota tarjeta", "mantenimiento", "descubierto", "cargo por")
private fun isFee(note: String) = Classifier.normalize(note).let { n -> FEE_WORDS.any { n.contains(it) } }

// ---------- Detector de gastos recurrentes ----------

data class Recurring(
    val name: String,
    /** Suscripción, recibo, cuota o comisión. */
    val kind: String,
    val monthly: Double,
    val last: Double,
    val lastDate: LocalDate,
    /** Cambio del último pago frente a los anteriores (0 = igual). */
    val change: Double,
    val times: Int,
    val fromBills: Boolean,
) {
    val annual get() = monthly * 12
}

object RecurringDetector {
    /** Pagos que se repiten cada mes (por comercio), más tus gastos fijos. Solo describe: no decide si sobran. */
    fun find(d: FinanceData): List<Recurring> {
        val since = d.today.minusDays(400).toEpochDay()
        val found = d.spending.filter { it.epochDay >= since }
            .groupBy { Classifier.normalize(it.note) }.filter { it.key.isNotEmpty() }
            .mapNotNull { (_, l) ->
                val sorted = l.sortedBy { it.epochDay }
                val months = sorted.map { YearMonth.from(it.epochDay.date()) }.distinct()
                if (months.size < 3 || sorted.size > months.size * 2) return@mapNotNull null
                val gaps = sorted.zipWithNext { a, b -> b.epochDay - a.epochDay }.sorted()
                val median = gaps[gaps.size / 2]
                if (median !in 24..38) return@mapNotNull null
                val last = sorted.last()
                if (last.epochDay < d.today.minusDays(45).toEpochDay()) return@mapNotNull null
                val before = sorted.dropLast(1).takeLast(3).map { it.amount }.average()
                Recurring(
                    last.note, kindOf(last), sorted.takeLast(3).map { it.amount }.average(), last.amount, last.epochDay.date(),
                    last.amount - before, sorted.size, fromBills = false,
                )
            }
        val names = found.map { Classifier.normalize(it.name) }.toSet()
        val bills = d.bills.filter { Classifier.normalize(it.name) !in names }.map { b ->
            val paid = d.expenses.filter { Budgeting.isBillPayment(it, listOf(b)) }.sortedBy { it.epochDay }
            val last = paid.lastOrNull()
            val before = paid.dropLast(1).takeLast(3).map { it.amount }.ifEmpty { listOf(b.amount) }.average()
            Recurring(
                b.name, if (Classifier.guess(b.name) == SpendTag.SUBSCRIPTIONS) "Suscripción" else if (b.yearly) "Pago anual" else "Recibo",
                Bills.monthly(b), last?.amount ?: b.amount, last?.epochDay?.date() ?: d.today, (last?.amount ?: b.amount) - before, paid.size, fromBills = true,
            )
        }
        return (found + bills).sortedByDescending { it.annual }
    }

    private fun kindOf(e: Expense) = when {
        isFee(e.note) -> "Comisión"
        e.spendTag() == SpendTag.SUBSCRIPTIONS -> "Suscripción"
        e.spendTag() == SpendTag.HOME -> "Recibo"
        else -> "Pago recurrente"
    }
}

// ---------- Dinero recuperable ----------

/** Una posible recuperación: cuánto, por qué lo pensamos (evidencia) y qué puedes hacer. Nunca afirma un error. */
data class Recoverable(val title: String, val amount: Double, val evidence: String, val nextStep: String, val expense: Expense? = null)

object RecoverableMoney {
    fun find(d: FinanceData, refunds: Set<Long>, fmt: (Double) -> String): List<Recoverable> {
        val out = mutableListOf<Recoverable>()
        // Posibles duplicados: mismo comercio e importe en menos de 3 días.
        val recent = d.expenses.filter { it.epochDay >= d.today.minusDays(60).toEpochDay() }.sortedBy { it.epochDay }
        recent.forEachIndexed { i, a ->
            recent.drop(i + 1).firstOrNull { b ->
                b.epochDay - a.epochDay <= 2 && abs(b.amount - a.amount) <= max(0.5, a.amount * 0.01) && a.amount >= 5 &&
                    Classifier.normalize(a.note) == Classifier.normalize(b.note)
            }?.let { b ->
                out += Recoverable(
                    "Posible cargo duplicado · ${b.note.ifBlank { "Gasto" }}", b.amount,
                    "Dos cargos de ${fmt(b.amount)} el ${a.epochDay.date().format(dayFmt)} y el ${b.epochDay.date().format(dayFmt)}.",
                    "Comprueba en la app de tu banco si son dos cargos reales. Si es así, pide la devolución al comercio; si fue una compra doble a propósito, ignóralo.", b,
                )
            }
        }
        // Subidas de precio en pagos recurrentes.
        RecurringDetector.find(d).filter { it.change >= 1 && it.change >= it.last * 0.04 }.forEach { r ->
            out += Recoverable(
                "${r.name} ha subido", r.change * 12,
                "Último pago ${fmt(r.last)}; antes pagabas unos ${fmt(r.last - r.change)} (+${fmt(r.change)} al mes).",
                "Pregunta si hay una tarifa o promoción más barata, o compara con otra compañía. Son ${fmt(r.change * 12)} al año.",
            )
        }
        // Comisiones: casi siempre se pueden negociar o evitar.
        d.spending.filter { it.epochDay >= d.today.minusDays(90).toEpochDay() && isFee(it.note) }
            .groupBy { Classifier.normalize(it.note) }.values.forEach { l ->
                val total = l.sumOf { it.amount }
                out += Recoverable(
                    "Comisiones · ${l.first().note}", total,
                    "${l.size} ${if (l.size == 1) "cargo" else "cargos"} en los últimos 3 meses (${fmt(total)}).",
                    "Pide a tu banco que la anule o qué condiciones evitan cobrarla (nómina domiciliada, recibos…).", l.maxBy { it.epochDay },
                )
            }
        // Suscripciones a revisar (sin dar por hecho que no se usan).
        RecurringDetector.find(d).filter { it.kind == "Suscripción" }.forEach { r ->
            out += Recoverable(
                "Revisa ${r.name}", r.annual,
                "${fmt(r.monthly)} al mes · ${fmt(r.annual)} al año.",
                "Si la usas, perfecto. Si la usas poco, mira un plan más barato o compartirla.",
            )
        }
        // Reembolsos que tú has marcado como pendientes.
        d.expenses.filter { it.id in refunds }.forEach { e ->
            val days = (d.today.toEpochDay() - e.epochDay).toInt()
            out += Recoverable(
                "Reembolso pendiente · ${e.note.ifBlank { "Gasto" }}", e.amount,
                "Lo marcaste como pendiente; pagado hace $days ${if (days == 1) "día" else "días"}.",
                if (days > 14) "Han pasado más de dos semanas: reclámalo al comercio o a la persona." else "Aún está en plazo; si llega, márcalo como cobrado.", e,
            )
        }
        return out.sortedByDescending { it.amount }
    }
}

// ---------- Aviso semanal por comercio ----------

object WeeklyNudge {
    /** "Pablo, esta semana has gastado 12 € en Monster Energy…": el capricho que más se repite esta semana. */
    fun find(d: FinanceData, name: String, fmt: (Double) -> String): String? {
        val monday = d.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        val week = d.spending.filter { it.epochDay >= monday && !Budgeting.isBillPayment(it, d.bills) && !it.spendTag().need && it.amount < 25 }
        val top = week.groupBy { Classifier.normalize(it.note) }.filter { it.key.isNotEmpty() && it.value.size >= 2 }
            .maxByOrNull { it.value.sumOf { e -> e.amount } }?.value ?: return null
        val spent = top.sumOf { it.amount }
        val monthly = spent * 52 / 12
        val who = if (name.isNotBlank()) "$name, esta" else "Esta"
        return "$who semana has gastado ${fmt(spent)} en ${top.first().note} (${top.size} veces). Si gastas la mitad, ahorrarías unos ${fmt(monthly / 2)} al mes."
    }
}

// ---------- Reorganizador de recibos ----------

data class BillCalendar(
    val payday: LocalDate?,
    /** Recibos de aquí al próximo cobro: (nombre, importe, fecha). */
    val beforePayday: List<Triple<String, Double, LocalDate>>,
    /** Totales por semana en las próximas 5 semanas. */
    val weeks: List<Pair<String, Double>>,
    val crunch: Boolean,
    val suggestions: List<String>,
)

object PaymentOrganizer {
    fun analyze(d: FinanceData, plan: BankPlan?, fmt: (Double) -> String): BillCalendar? {
        if (d.bills.isEmpty()) return null
        val payday = Advisor.nextFixedPayday(d)
        val until = d.today.plusDays(35)
        val due = mutableListOf<Triple<String, Double, LocalDate>>()
        var m = YearMonth.from(d.today)
        while (!m.atDay(1).isAfter(until)) {
            d.bills.forEach { b ->
                val date = Bills.dueDate(b, m) ?: return@forEach
                if (!date.isBefore(d.today) && !date.isAfter(until) && !(m == YearMonth.from(d.today) && Bills.isPaid(b, m, d.expenses))) due += Triple(b.name, b.amount, date)
            }
            m = m.plusMonths(1)
        }
        due.sortBy { it.third }
        val weeks = (0 until 5).map { w ->
            val start = d.today.plusDays(w * 7L)
            val end = start.plusDays(6)
            "${start.format(dayFmt)}–${end.format(dayFmt)}" to due.filter { !it.third.isBefore(start) && !it.third.isAfter(end) }.sumOf { it.second }
        }
        val before = if (payday == null) due.filter { YearMonth.from(it.third) == YearMonth.from(d.today) } else due.filter { it.third.isBefore(payday) }
        val beforeTotal = before.sumOf { it.second }
        val available = plan?.let { it.main.balance - it.buffer } ?: d.wealth.available
        val monthBills = d.bills.sumOf { Bills.monthly(it) }
        val peak = weeks.maxByOrNull { it.second }
        val crunch = beforeTotal > available * 0.8 || (peak != null && monthBills > 0 && peak.second > monthBills * 0.6 && weeks.count { it.second > 0 } > 1)
        val suggestions = mutableListOf<String>()
        if (crunch && payday != null) {
            before.filter { it.second >= 30 }.sortedByDescending { it.second }.take(2).forEach { (name, amount, date) ->
                suggestions += "Pide cambiar $name (${fmt(amount)}, el ${date.format(dayFmt)}) al ${payday.plusDays(1).dayOfMonth} de cada mes: no tendrías que pagarlo antes de cobrar. Se suele pedir en el área de cliente o llamando al proveedor."
            }
            suggestions += "O reserva ${fmt(beforeTotal)} de cada nómina para los recibos que llegan antes de la siguiente."
        }
        if (peak != null && peak.second > 0 && monthBills > 0 && peak.second > monthBills * 0.6) suggestions += "La semana del ${peak.first} se concentra el ${(peak.second / monthBills * 100).toInt()} % de tus recibos del mes."
        if (suggestions.isEmpty()) suggestions += "Tus recibos están bien repartidos: no hay semanas apretadas antes de tu próximo cobro."
        return BillCalendar(payday, before, weeks, crunch, suggestions)
    }
}

// ---------- Distribuidor de la nómina ----------

data class PaycheckSplit(
    val income: Double, val label: String, val parts: List<Pair<String, Double>>, val notes: List<String>,
    /** Los recibos que caen entre esta nómina y la siguiente. */
    val bills: List<Pair<String, Double>> = emptyList(),
) {
    val free get() = parts.lastOrNull()?.second ?: 0.0
}

object PaycheckDistributor {
    /** Propuesta para la próxima nómina (o la última): recibos que vienen, lo básico, colchón, objetivos y dinero libre. */
    fun split(d: FinanceData, goal: GoalStatus?, fmt: (Double) -> String): PaycheckSplit? {
        val source = d.sources.filter { !it.variable }.maxByOrNull { it.amount } ?: return null
        val income = source.amount
        val next = Advisor.nextFixedPayday(d) ?: return null
        val after = next.plusMonths(1)
        // Recibos entre esta nómina y la siguiente, incluidos los anuales que caen en ese tramo.
        val bills = mutableListOf<Pair<String, Double>>()
        listOf(YearMonth.from(next), YearMonth.from(after)).distinct().forEach { m ->
            d.bills.forEach { b -> Bills.dueDate(b, m)?.let { if (!it.isBefore(next) && it.isBefore(after)) bills += b.name to b.amount } }
        }
        val billsTotal = bills.sumOf { it.second }
        val closed = d.closedMonths(3)
        val essentials = if (closed.isEmpty()) income * 0.2 else closed.sumOf { m -> Budgeting.variable(d, m).filter { it.spendTag().need }.sumOf { it.amount } } / closed.size
        var left = income - billsTotal - essentials
        val cost = Insights.costOfLiving(d).monthly
        val cushionGap = max(0.0, cost * 3 - d.wealth.savings)
        val cushion = min(max(0.0, left) * 0.3, min(cushionGap, income * 0.1)).let { (it / 5).toInt() * 5.0 }
        left -= cushion
        val goals = min(max(0.0, left) * 0.6, goal?.requiredMonthly ?: max(0.0, left) * 0.3).let { (it / 5).toInt() * 5.0 }
        left -= goals
        val notes = mutableListOf<String>()
        val yearly = d.bills.filter { it.yearly }.filter { b -> bills.any { it.first == b.name } }
        yearly.forEach { notes += "Ojo: este tramo incluye ${it.name} (${fmt(it.amount)}), que solo pagas una vez al año. Por eso queda menos libre." }
        if (left < 0) notes += "No llega para todo: lo libre sale en negativo. Baja el colchón o el objetivo este mes."
        if (cushionGap <= 0) notes += "Tu colchón ya cubre 3 meses: no hace falta apartar para él."
        return PaycheckSplit(
            income, "${source.name} del ${next.format(dayFmt)}",
            listOf("Recibos hasta la siguiente" to billsTotal, "Lo básico (súper, transporte…)" to essentials, "Colchón de emergencia" to cushion, (goal?.goal?.name ?: "Objetivos") to goals, "Dinero libre" to left),
            notes, bills,
        )
    }
}

// ---------- Cambios en tu vida financiera ----------

data class Trend(val title: String, val explanation: String)

object TrendDetector {
    /** Solo cambios que se mantienen varias semanas o meses, con lo que los explica. */
    fun find(d: FinanceData, fmt: (Double) -> String): List<Trend> {
        val out = mutableListOf<Trend>()
        val monday = d.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStarts = (1..12).map { monday.minusWeeks(it.toLong()) } // semanas completas, de la más reciente a la más antigua
        val variable = d.spending.filter { !Budgeting.isBillPayment(it, d.bills) }
        if (variable.isNotEmpty() && variable.minOf { it.epochDay } <= weekStarts.last().toEpochDay()) {
            fun weekTotal(start: LocalDate, tag: SpendTag) = variable.filter { it.spendTag() == tag && it.epochDay in start.toEpochDay()..start.plusDays(6).toEpochDay() }.sumOf { it.amount }
            SpendTag.entries.forEach { tag ->
                val recent = weekStarts.take(4).map { weekTotal(it, tag) }
                val base = weekStarts.drop(4).map { weekTotal(it, tag) }.average()
                val above = recent.count { it > base * 1.25 && it - base >= 3 }
                val diff = recent.average() - base
                if (above >= 3 && diff >= 5) {
                    val cause = variable.filter { it.spendTag() == tag && it.epochDay >= weekStarts[3].toEpochDay() }
                        .groupBy { Classifier.normalize(it.note) }.maxByOrNull { it.value.sumOf { e -> e.amount } }?.value
                    out += Trend(
                        "${tag.label}: sube desde hace semanas",
                        "Llevas $above de las últimas 4 semanas por encima de lo normal: ${fmt(recent.average())} a la semana frente a ${fmt(base)} antes (unos ${fmt(diff * 52 / 12)} más al mes)." +
                            (cause?.let { " Lo explica sobre todo ${it.first().note} (${it.size} pagos)." } ?: ""),
                    )
                }
            }
        }
        // Ingresos más variables.
        val months = d.closedMonths(6).map { d.receivedIn(it) }
        if (months.size >= 6) {
            fun cv(l: List<Double>): Double { val m = l.average(); return if (m <= 0) 0.0 else sqrt(l.sumOf { (it - m) * (it - m) } / l.size) / m }
            val now = cv(months.take(3)); val before = cv(months.drop(3))
            if (now > before + 0.1 && now > 0.15) out += Trend("Tus ingresos son más variables", "En los últimos 3 meses lo que cobras cambia más de un mes a otro que antes. Ten algo más de colchón en la cuenta del día a día.")
        }
        // Margen mensual que se va estrechando.
        val nets = d.closedMonths(3).reversed().map { d.receivedIn(it) - d.spentIn(it) }
        if (nets.size == 3 && nets[0] > 0 && nets[1] < nets[0] && nets[2] < nets[1] && nets[2] < nets[0] * 0.7) {
            out += Trend("Tu margen se está estrechando", "Lo que te sobra cada mes ha bajado tres meses seguidos: ${nets.joinToString(" → ") { fmt(it) }}.")
        }
        return out
    }
}

// ---------- Precio real de una compra ----------

data class RealPrice(
    val price: Double,
    val incomeDays: Double,
    val goalDelayDays: Int?,
    val cushionMonthsBefore: Double?,
    val cushionMonthsAfter: Double?,
    /** Ahorro previsto en 1, 2 y 3 meses: (sin comprar, comprando). */
    val months: List<Triple<String, Double, Double>>,
    val waitNote: String,
)

object RealPriceCalculator {
    fun of(price: Double, d: FinanceData, goal: GoalStatus?, fmt: (Double) -> String): RealPrice {
        val dailyIncome = d.monthlyIncome / 30.4
        val pace = Goals.paceOf(d, null)
        val cost = Insights.costOfLiving(d).monthly
        val savings = d.wealth.savings
        val names = (1..3).map { d.today.plusMonths(it.toLong()).month.getDisplayName(java.time.format.TextStyle.SHORT, ES) }
        val months = names.mapIndexed { i, n -> Triple(n, savings + pace * (i + 1), savings + pace * (i + 1) - price) }
        val wait = if (pace > 0) {
            val m = (price / pace)
            if (m <= 3) "Si esperas 3 meses y lo apartas poco a poco (${fmt(price / 3)} al mes), no tocas tu ahorro." else "Aunque esperes 3 meses, a tu ritmo necesitarías ${"%.1f".format(ES, m)} meses de ahorro para pagarlo."
        } else "Ahora mismo no te sobra dinero al mes: esperar no lo hace más fácil, salvo que recortes algo."
        return RealPrice(
            price, if (dailyIncome > 0) price / dailyIncome else 0.0,
            goal?.takeIf { it.pace > 0 && !it.done }?.let { (price / it.pace * 30.4).toInt() },
            if (cost > 0) savings / cost else null, if (cost > 0) (savings - price) / cost else null, months, wait,
        )
    }
}

/** Días entre dos fechas (para mostrar "en 5 días"). */
fun daysUntil(from: LocalDate, to: LocalDate) = ChronoUnit.DAYS.between(from, to).toInt()
