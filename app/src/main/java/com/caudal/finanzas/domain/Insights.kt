package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.CryptoHolding
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.NetWorthSnapshot
import com.caudal.finanzas.data.RecurringBill
import com.caudal.finanzas.data.Split
import com.caudal.finanzas.data.StockHolding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val ES = Locale.forLanguageTag("es-ES")
fun YearMonth.monthName(): String = month.getDisplayName(TextStyle.FULL, ES)
private fun Long.date(): LocalDate = LocalDate.ofEpochDay(this)

/** Todo lo que necesitan los análisis, ya cargado. */
data class FinanceData(
    val today: LocalDate,
    val accounts: List<Account>,
    val crypto: List<CryptoHolding>,
    val stocks: List<StockHolding>,
    val categories: List<Category>,
    val expenses: List<Expense>,
    val incomes: List<Income>,
    val sources: List<IncomeSource>,
    val bills: List<RecurringBill>,
    val snapshots: List<NetWorthSnapshot>,
    val splits: List<Split> = emptyList(),
    /** Dinero apartado para un plan (no cuenta como disponible para gastar). */
    val planReserve: Double = 0.0,
) {
    val wealth: Wealth by lazy { Wealth.of(accounts, crypto, stocks) }
    val savingIds: Set<Long> by lazy { categories.filter { it.isSavings }.map { it.id }.toSet() }

    /** Gastos de verdad: sin lo que se aparta para ahorrar. */
    val spending: List<Expense> by lazy { expenses.filter { it.categoryId !in savingIds } }
    val thisMonth: YearMonth get() = YearMonth.from(today)

    fun inMonth(day: Long, m: YearMonth) = YearMonth.from(day.date()) == m
    fun spendingIn(m: YearMonth) = spending.filter { inMonth(it.epochDay, m) }
    fun spentIn(m: YearMonth) = spendingIn(m).sumOf { it.amount }
    fun receivedIn(m: YearMonth) = incomes.filter { inMonth(it.epochDay, m) }.sumOf { it.amount }

    val firstMonth: YearMonth? by lazy {
        (expenses.map { it.epochDay } + incomes.map { it.epochDay }).minOrNull()?.let { YearMonth.from(it.date()) }
    }

    /** Meses ya cerrados con datos, del más reciente al más antiguo (como mucho [n]). */
    fun closedMonths(n: Int): List<YearMonth> {
        val first = firstMonth ?: return emptyList()
        return (1..n.toLong()).map { thisMonth.minusMonths(it) }.filter { !it.isBefore(first) }
    }

    /** Ingreso mensual de referencia: la media cobrada en los últimos meses cerrados, o lo previsto. */
    val monthlyIncome: Double by lazy {
        val closed = closedMonths(3).map { receivedIn(it) }.filter { it > 0 }
        when {
            closed.isNotEmpty() -> closed.average()
            sources.isNotEmpty() -> IncomeMath.forMonth(sources, incomes, thisMonth).total
            else -> receivedIn(thisMonth)
        }
    }
}

fun byTag(list: List<Expense>): List<Pair<SpendTag, Double>> =
    list.groupBy { it.spendTag() }.map { (t, l) -> t to l.sumOf { it.amount } }.sortedByDescending { it.second }

private fun List<Pair<SpendTag, Double>>.amountOf(t: SpendTag) = firstOrNull { it.first == t }?.second ?: 0.0
private fun pctText(v: Double) = "${v.roundToInt()} %"
private fun joinEs(items: List<String>) = when (items.size) {
    0 -> ""; 1 -> items[0]; else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}

// ---------- 7. ¿Dónde se ha ido mi dinero? ----------

data class MonthStory(
    /** "este mes" o "en octubre". */
    val label: String,
    val incomeNow: Double,
    val incomePrev: Double,
    val spentNow: Double,
    val spentPrev: Double,
    /** Diferencia por tipo de gasto (positivo = más que antes), de mayor a menor. */
    val changes: List<Pair<SpendTag, Double>>,
)

object Insights {
    /**
     * Este mes hasta hoy frente al mismo tramo del anterior. En los primeros días del mes,
     * cuenta mejor el último mes completo frente al anterior.
     */
    fun monthStory(d: FinanceData): MonthStory {
        val earlyInMonth = d.today.dayOfMonth <= 7 && d.spentIn(d.thisMonth.minusMonths(1)) > 0
        val (nowFrom, nowTo, prevFrom, prevTo, label) = if (earlyInMonth) {
            val last = d.thisMonth.minusMonths(1); val before = last.minusMonths(1)
            Window(last.atDay(1), last.atEndOfMonth(), before.atDay(1), before.atEndOfMonth(), "en ${last.monthName()}")
        } else {
            val prev = d.thisMonth.minusMonths(1)
            Window(d.thisMonth.atDay(1), d.today, prev.atDay(1), prev.atDay(minOf(d.today.dayOfMonth, prev.lengthOfMonth())), "este mes")
        }
        fun inRange(day: Long, a: LocalDate, b: LocalDate) = day.date().let { !it.isBefore(a) && !it.isAfter(b) }
        val now = d.spending.filter { inRange(it.epochDay, nowFrom, nowTo) }
        val prev = d.spending.filter { inRange(it.epochDay, prevFrom, prevTo) }
        val nowTags = byTag(now); val prevTags = byTag(prev)
        val changes = SpendTag.entries.map { t -> t to (nowTags.amountOf(t) - prevTags.amountOf(t)) }
            .filter { abs(it.second) >= 1 }.sortedByDescending { it.second }
        return MonthStory(
            label,
            d.incomes.filter { inRange(it.epochDay, nowFrom, nowTo) }.sumOf { it.amount },
            d.incomes.filter { inRange(it.epochDay, prevFrom, prevTo) }.sumOf { it.amount },
            now.sumOf { it.amount }, prev.sumOf { it.amount }, changes,
        )
    }

    private data class Window(val a: LocalDate, val b: LocalDate, val c: LocalDate, val d: LocalDate, val label: String)

    /** "Has cobrado lo mismo que el mes pasado, pero has gastado 184 € más: 72 € en restaurantes…" */
    fun describe(s: MonthStory, fmt: (Double) -> String): String {
        val incomeDiff = s.incomeNow - s.incomePrev
        val spentDiff = s.spentNow - s.spentPrev
        val subject = s.label.replaceFirstChar { it.titlecase(ES) }
        val income = when {
            s.incomeNow <= 0 && s.incomePrev <= 0 -> "$subject no hay cobros apuntados"
            abs(incomeDiff) < max(10.0, s.incomePrev * 0.03) -> "$subject has cobrado lo mismo que el mes anterior"
            incomeDiff > 0 -> "$subject has cobrado ${fmt(incomeDiff)} más que el mes anterior"
            else -> "$subject has cobrado ${fmt(-incomeDiff)} menos que el mes anterior"
        }
        val ups = s.changes.filter { it.second > 0 }.take(3).map { "${fmt(it.second)} en ${it.first.label.lowercase()}" }
        val downs = s.changes.filter { it.second < 0 }.sortedBy { it.second }.take(2).map { "${fmt(-it.second)} en ${it.first.label.lowercase()}" }
        val spent = when {
            abs(spentDiff) < 5 -> " y has gastado prácticamente lo mismo."
            spentDiff > 0 -> (if (incomeDiff >= 0) ", pero" else " y") + " has gastado ${fmt(spentDiff)} más" +
                (if (ups.isNotEmpty()) ": ${joinEs(ups)}." else ".")
            else -> " y has gastado ${fmt(-spentDiff)} menos" + (if (downs.isNotEmpty()) ", sobre todo ${joinEs(downs)}." else ".") + " ¡Bien!"
        }
        return income + spent
    }

    // ---------- 8. Máquina del tiempo ----------

    data class TimePoint(val month: YearMonth, val netWorth: Double?, val spent: Double, val received: Double, val byTag: List<Pair<SpendTag, Double>>) {
        val wants get() = byTag.filter { !it.first.need }.sumOf { it.second }
    }

    data class TimeTravel(val then: TimePoint, val now: TimePoint, val netWorthNow: Double) {
        val netWorthChange get() = then.netWorth?.let { netWorthNow - it }
    }

    /** Patrimonio al final de cada mes (la última foto de ese mes); el mes actual, el de hoy. */
    fun netWorthByMonth(d: FinanceData, months: Int = 12): List<Pair<YearMonth, Double>> {
        val out = (months - 1 downTo 1).mapNotNull { back ->
            val m = d.thisMonth.minusMonths(back.toLong())
            d.snapshots.lastOrNull { YearMonth.from(it.epochDay.date()) == m }?.let { m to it.total }
        }
        return out + (d.thisMonth to d.wealth.total)
    }

    /** Cómo estabas hace [monthsBack] meses frente a ahora (último mes cerrado para los gastos). */
    fun timeTravel(d: FinanceData, monthsBack: Int): TimeTravel {
        val thenMonth = d.thisMonth.minusMonths(monthsBack.toLong())
        val thenEnd = if (monthsBack == 0) d.today else thenMonth.atEndOfMonth()
        val snapshot = d.snapshots.lastOrNull { !it.epochDay.date().isAfter(thenEnd) && !it.epochDay.date().isBefore(thenEnd.minusDays(45)) }
        val nowMonth = d.closedMonths(1).firstOrNull() ?: d.thisMonth
        fun point(m: YearMonth, nw: Double?) = TimePoint(m, nw, d.spentIn(m), d.receivedIn(m), byTag(d.spendingIn(m)))
        return TimeTravel(point(thenMonth, snapshot?.total), point(nowMonth, d.wealth.total), d.wealth.total)
    }

    /** Meses que se puede viajar atrás: desde la primera foto de patrimonio o el primer movimiento. */
    fun earliestMonthsBack(d: FinanceData): Int {
        val first = listOfNotNull(d.snapshots.firstOrNull()?.epochDay?.let { YearMonth.from(it.date()) }, d.firstMonth).minOrNull() ?: return 0
        return ((d.thisMonth.year - first.year) * 12 + d.thisMonth.monthValue - first.monthValue).coerceAtLeast(0)
    }

    fun describe(t: TimeTravel, monthsBack: Int, fmt: (Double) -> String): List<String> {
        val out = mutableListOf<String>()
        val when_ = if (monthsBack == 1) "Hace un mes" else if (monthsBack % 12 == 0) "Hace ${monthsBack / 12} año${if (monthsBack > 12) "s" else ""}" else "Hace $monthsBack meses"
        val change = t.netWorthChange
        if (t.then.netWorth != null && change != null) {
            out += "$when_ tenías ${fmt(t.then.netWorth)}; hoy tienes ${fmt(t.netWorthNow)}" + when {
                change > 1 -> " (${fmt(change)} más). Estás mejor que entonces."
                change < -1 -> " (${fmt(-change)} menos). Has ido a menos."
                else -> ". Estás igual."
            }
        } else out += "No hay foto de tu patrimonio de ${t.then.month.monthName()} ${t.then.month.year}: Ahorra, vaca la guarda cada día que abres la app."
        if (t.then.spent > 0 && t.now.spent > 0) {
            val diff = t.now.spent - t.then.spent
            out += "En ${t.then.month.monthName()} gastaste ${fmt(t.then.spent)}; en ${t.now.month.monthName()}, ${fmt(t.now.spent)}" +
                (if (abs(diff) < 10) "." else if (diff > 0) " (${fmt(diff)} más)." else " (${fmt(-diff)} menos).")
            val wantsDiff = t.now.wants - t.then.wants
            if (t.then.wants > 0 || t.now.wants > 0) {
                out += "En ocio y caprichos pasaste de ${fmt(t.then.wants)} a ${fmt(t.now.wants)} al mes" +
                    (if (wantsDiff > 10) "." else if (wantsDiff < -10) ": has recortado." else ": igual que entonces.")
            }
        }
        if (t.then.received > 0 && t.now.received > 0) {
            val r = (t.now.received - t.then.received) / t.then.received * 100
            if (abs(r) >= 3) out += "Cobras un ${pctText(abs(r))} ${if (r > 0) "más" else "menos"} que entonces."
        }
        return out
    }

    // ---------- 12. Tu coste de vida real ----------

    data class CostOfLiving(val fixed: Double, val yearlySpread: Double, val variable: Double, val forgotten: Double, val thisMonthPace: Double?) {
        val monthly get() = fixed + yearlySpread + variable + forgotten
        val yearly get() = monthly * 12
    }

    fun costOfLiving(d: FinanceData): CostOfLiving {
        val fixed = d.bills.filter { !it.yearly }.sumOf { it.amount }
        val yearly = d.bills.filter { it.yearly }.sumOf { it.amount } / 12
        // Lo variable: gastos que no son fijos, de media en los meses cerrados (máx. 3).
        val closed = d.closedMonths(3).map { m -> d.spendingIn(m).filter { it.billId == null }.sumOf { it.amount } }.filter { it > 0 }
        val currentVar = d.spendingIn(d.thisMonth).filter { it.billId == null }.sumOf { it.amount }
        val pace = if (d.today.dayOfMonth >= 7) currentVar / d.today.dayOfMonth * d.thisMonth.lengthOfMonth() else null
        val variable = when {
            closed.isNotEmpty() -> closed.average()
            pace != null -> pace
            else -> Forecasting.dailyVariableSpend(d.expenses, d.today, d.savingIds) * 30
        }
        val forgotten = radar(d, horizonDays = 365).filter { it.fromHistory }.sumOf { it.amount } / 12
        val thisMonthPace = if (d.today.dayOfMonth >= 7) d.spentIn(d.thisMonth) / d.today.dayOfMonth * d.thisMonth.lengthOfMonth() else null
        return CostOfLiving(fixed, yearly, variable, forgotten, thisMonthPace)
    }

    // ---------- 17. Radar de gastos olvidados ----------

    data class RadarItem(val name: String, val amount: Double, val due: LocalDate, val fromHistory: Boolean) {
        fun monthsLeft(today: LocalDate) = max(1, ((due.toEpochDay() - today.toEpochDay()) / 30.4).toInt())
        fun monthlyReserve(today: LocalDate) = amount / monthsLeft(today)
    }

    private val YEARLY_TAGS = setOf(SpendTag.HOME, SpendTag.TRANSPORT, SpendTag.GIFTS, SpendTag.TRAVEL, SpendTag.EDUCATION, SpendTag.HEALTH, SpendTag.PETS, SpendTag.SUBSCRIPTIONS, SpendTag.OTHER)

    /** Pagos poco frecuentes que vienen: gastos fijos anuales y lo que pagaste hace casi un año. */
    fun radar(d: FinanceData, horizonDays: Int = 120): List<RadarItem> {
        val until = d.today.plusDays(horizonDays.toLong())
        val fromBills = d.bills.filter { it.yearly }.mapNotNull { b ->
            var due = Bills.dueDate(b, YearMonth.of(d.today.year, b.month))!!
            if (due.isBefore(d.today) || Bills.isPaid(b, YearMonth.from(due), d.expenses)) due = Bills.dueDate(b, YearMonth.of(d.today.year + 1, b.month))!!
            if (due.isAfter(until)) null else RadarItem(b.name, b.amount, due, fromHistory = false)
        }
        val billNames = d.bills.map { Classifier.normalize(it.name) }.toSet()
        val fromHistory = d.spending.asSequence()
            .filter { it.billId == null && it.amount >= 40 && it.spendTag() in YEARLY_TAGS }
            .filter { Classifier.normalize(it.note).let { n -> n.isNotEmpty() && n !in billNames } }
            .mapNotNull { e ->
                val next = e.epochDay.date().plusYears(1)
                if (next.isBefore(d.today) || next.isAfter(until)) return@mapNotNull null
                val n = Classifier.normalize(e.note)
                // Si lo pagas a menudo (cada mes), no es un gasto olvidado.
                val repeatedSince = d.spending.any { o -> o.id != e.id && Classifier.normalize(o.note) == n && o.epochDay > e.epochDay + 20 }
                if (repeatedSince) null else RadarItem(e.note, e.amount, next, fromHistory = true)
            }
            .distinctBy { Classifier.normalize(it.name) }
            .toList()
        return (fromBills + fromHistory).sortedBy { it.due }
    }

    // ---------- 18. Mapa de hábitos ----------

    data class Habits(
        /** Media por día de lunes a domingo. */
        val byWeekday: List<Double>,
        val weekdayAvg: Double,
        val weekendAvg: Double,
        val afterPaydayAvg: Double?,
        val otherDaysAvg: Double,
        /** Media por día a principios (1–10), mediados (11–20) y finales de mes. */
        val thirds: List<Double>,
        val days: Int,
        val notes: List<String>,
    )

    fun habits(d: FinanceData, fmt: (Double) -> String, window: Int = 90): Habits? {
        val variable = d.spending.filter { it.billId == null && it.epochDay <= d.today.toEpochDay() }
        val first = variable.minOfOrNull { it.epochDay } ?: return null
        val start = max(first, d.today.toEpochDay() - window + 1)
        val days = (d.today.toEpochDay() - start + 1).toInt()
        if (days < 14) return null
        val perDay = variable.filter { it.epochDay >= start }.groupBy { it.epochDay }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val range = (start..d.today.toEpochDay())
        fun avg(filter: (LocalDate) -> Boolean): Double {
            val ds = range.filter { filter(it.date()) }
            return if (ds.isEmpty()) 0.0 else ds.sumOf { perDay[it] ?: 0.0 } / ds.size
        }
        val byWeekday = DayOfWeek.entries.map { dow -> avg { it.dayOfWeek == dow } }
        val weekend = avg { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
        val weekday = avg { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        val paydays = d.incomes.filter { it.sourceId != null }.map { it.epochDay }
        fun afterPayday(day: LocalDate) = paydays.any { p -> day.toEpochDay() - p in 0..6 }
        val hasPaydays = range.any { afterPayday(it.date()) }
        val afterPay = if (hasPaydays) avg { afterPayday(it) } else null
        val other = avg { !afterPayday(it) }
        val thirds = listOf(avg { it.dayOfMonth <= 10 }, avg { it.dayOfMonth in 11..20 }, avg { it.dayOfMonth > 20 })

        val notes = mutableListOf<String>()
        if (weekday > 0 && weekend > weekday * 1.25) notes += "Los fines de semana gastas un ${pctText((weekend / weekday - 1) * 100)} más al día (${fmt(weekend)} frente a ${fmt(weekday)})."
        else if (weekend > 0 && weekday > weekend * 1.25) notes += "Entre semana gastas más que el fin de semana (${fmt(weekday)} al día frente a ${fmt(weekend)})."
        val top = byWeekday.withIndex().maxByOrNull { it.value }
        if (top != null && top.value > 0) notes += "Tu día más caro es el ${DayOfWeek.of(top.index + 1).getDisplayName(TextStyle.FULL, ES)}: ${fmt(top.value)} de media."
        if (afterPay != null && other > 0 && afterPay > other * 1.3) notes += "La semana después de cobrar gastas un ${pctText((afterPay / other - 1) * 100)} más al día. Aparta el ahorro nada más cobrar."
        val names = listOf("principios", "mediados", "finales")
        val maxThird = thirds.withIndex().maxBy { it.value }
        val minThird = thirds.withIndex().minBy { it.value }
        if (minThird.value > 0 && maxThird.value > minThird.value * 1.4) notes += "Gastas más a ${names[maxThird.index]} de mes y menos a ${names[minThird.index]}."
        if (notes.isEmpty()) notes += "Tu gasto es bastante regular: no hay días ni épocas que se disparen."
        return Habits(byWeekday, weekday, weekend, afterPay, other, thirds, days, notes)
    }

    // ---------- 19. El contrafactual de ahorro ----------

    data class WhatIf(val title: String, val amount: Double, val detail: String)

    fun whatIfs(d: FinanceData, monthly: Double, fmt: (Double) -> String): List<WhatIf> {
        val first = d.firstMonth ?: return emptyList()
        val months = max(1, (d.thisMonth.year - first.year) * 12 + d.thisMonth.monthValue - first.monthValue)
        val bestRate = d.accounts.maxOfOrNull { it.interestRate } ?: 0.0
        val past = InterestMath.project(0.0, bestRate, monthly, months)
        val out = mutableListOf(
            WhatIf(
                "Si hubieras reservado ${fmt(monthly)} cada mes desde ${first.monthName()}",
                past.finalBalance,
                "Tendrías ${fmt(past.finalBalance)} más" + if (bestRate > 0) ", con ${fmt(past.interest)} de intereses al ${"%.1f".format(ES, bestRate)} %." else ".",
            ),
        )
        val wants = byTag(d.spending.filter { !YearMonth.from(it.epochDay.date()).isBefore(first) }).filter { !it.first.need && it.first != SpendTag.OTHER }
        wants.firstOrNull()?.let { (tag, total) ->
            out += WhatIf("Si hubieras gastado un 20 % menos en ${tag.label.lowercase()}", total * 0.2, "Habrías guardado ${fmt(total * 0.2)} desde ${first.monthName()} sin dejar de hacerlo.")
        }
        val future = InterestMath.project(0.0, bestRate, monthly, 60)
        out += WhatIf("Si empiezas hoy con ${fmt(monthly)} al mes", future.finalBalance, "En 5 años tendrías ${fmt(future.finalBalance)}" + if (bestRate > 0) " (${fmt(future.interest)} de intereses)." else ".")
        return out
    }

    // ---------- 20. Informe de salud financiera ----------

    enum class Status { GOOD, OK, BAD }

    data class Indicator(val name: String, val value: String, val status: Status, val explanation: String, val advice: String)

    data class HealthReport(val indicators: List<Indicator>, val trend: String?, val nextStep: String) {
        val good get() = indicators.count { it.status == Status.GOOD }
    }

    fun health(d: FinanceData, fmt: (Double) -> String): HealthReport {
        val list = mutableListOf<Indicator>()
        val closed = d.closedMonths(3).filter { d.receivedIn(it) > 0 }
        fun rate(months: List<YearMonth>): Double? {
            val inc = months.sumOf { d.receivedIn(it) }
            return if (inc <= 0) null else (inc - months.sumOf { d.spentIn(it) }) / inc * 100
        }
        val savingRate = rate(closed) ?: rate(listOf(d.thisMonth))
        list += if (savingRate == null) Indicator("Capacidad de ahorro", "Sin datos", Status.OK, "Apunta tus cobros para saber qué parte de lo que entra te queda.", "Marca tus cobros como \"Cobrado\" en Gastos.")
        else Indicator(
            "Capacidad de ahorro", pctText(savingRate),
            when { savingRate >= 20 -> Status.GOOD; savingRate >= 5 -> Status.OK; else -> Status.BAD },
            "De lo que entra, te queda un ${pctText(savingRate)}" + if (closed.isNotEmpty()) " (media de ${closed.size} ${if (closed.size == 1) "mes" else "meses"})." else " este mes.",
            if (savingRate >= 20) "Vas bien: automatiza el ahorro el día que cobras." else "Intenta llegar al 20 %: empieza recortando tu tipo de gasto prescindible más alto.",
        )

        val prev = d.closedMonths(6).drop(3).filter { d.receivedIn(it) > 0 }
        val trend = rate(prev)?.let { before -> savingRate?.let { now ->
            when { now > before + 3 -> "Mejorando: ahorras un ${pctText(now - before)} más que hace unos meses."; now < before - 3 -> "Empeorando: ahorras un ${pctText(before - now)} menos que hace unos meses."; else -> "Estable respecto a hace unos meses." }
        } }

        val nets = d.closedMonths(6).map { d.receivedIn(it) - d.spentIn(it) }
        val red = nets.count { it < 0 }
        list += if (nets.isEmpty()) Indicator("Estabilidad", "Pronto", Status.OK, "Cuando cierres tu primer mes verás si entra más de lo que sale de forma regular.", "Sigue apuntando tus movimientos este mes.")
        else Indicator(
            "Estabilidad", "${nets.size - red} de ${nets.size} meses en verde",
            when (red) { 0 -> Status.GOOD; 1 -> Status.OK; else -> Status.BAD },
            if (red == 0) "Todos tus meses cierran con más entrada que salida." else "$red ${if (red == 1) "mes ha cerrado" else "meses han cerrado"} gastando más de lo que entró.",
            "Ten un pequeño colchón en la cuenta del día a día para los meses irregulares.",
        )

        val income = d.monthlyIncome
        val fixed = d.bills.sumOf { Bills.monthly(it) }
        val fixedPct = if (income > 0) fixed / income * 100 else null
        list += if (fixedPct == null) Indicator("Peso de los gastos fijos", fmt(fixed) + "/mes", Status.OK, "Apunta tus ingresos para saber cuánto de tu sueldo se va en fijos.", "Añade tus ingresos en Gastos.")
        else Indicator(
            "Peso de los gastos fijos", pctText(fixedPct),
            when { fixedPct <= 35 -> Status.GOOD; fixedPct <= 50 -> Status.OK; else -> Status.BAD },
            "Tus gastos fijos (${fmt(fixed)} al mes) se llevan un ${pctText(fixedPct)} de lo que ganas.",
            if (fixedPct <= 35) "Margen sano para imprevistos." else "Revisa suscripciones, tarifa de luz o móvil: cada fijo que bajas es ahorro todos los meses.",
        )

        val cost = costOfLiving(d).monthly
        val months = if (cost > 0) d.wealth.savings / cost else null
        list += if (months == null) Indicator("Colchón de emergencia", fmt(d.wealth.savings), Status.OK, "Cuando haya gastos apuntados calcularemos cuántos meses te cubre.", "Apunta tus gastos fijos.")
        else Indicator(
            "Colchón de emergencia", "%.1f meses".format(ES, months),
            when { months >= 3 -> Status.GOOD; months >= 1 -> Status.OK; else -> Status.BAD },
            "Tu ahorro (${fmt(d.wealth.savings)}) cubriría ${"%.1f".format(ES, months)} meses de tu coste de vida.",
            if (months >= 3) "Cubierto. Lo que pase de 6 meses puede trabajar en inversiones." else "Objetivo: 3 meses (${fmt(cost * 3)}). Aparta un poco cada mes en una cuenta remunerada.",
        )

        val debts = d.wealth.debts
        val debtRatio = if (income > 0) debts / (income * 12) * 100 else null
        list += Indicator(
            "Deudas", if (debts <= 0) "Sin deudas" else fmt(debts),
            when { debts <= 0 -> Status.GOOD; debtRatio != null && debtRatio <= 35 -> Status.OK; else -> Status.BAD },
            if (debts <= 0) "No tienes préstamos ni deudas apuntados." else "Debes ${fmt(debts)}" + (debtRatio?.let { ", un ${pctText(it)} de lo que ganas en un año." } ?: "."),
            if (debts <= 0) "Mantenlo así." else "Paga primero la deuda con el interés más alto.",
        )

        val worst = list.firstOrNull { it.status == Status.BAD } ?: list.firstOrNull { it.status == Status.OK }
        val next = worst?.let { "${it.name}: ${it.advice}" } ?: "Todo en orden. El siguiente paso: invertir lo que te sobre cada mes a largo plazo."
        return HealthReport(list, trend, next)
    }

    // ---------- 11. Gastos que se pueden compartir ----------

    data class ShareCandidate(val expense: Expense, val reason: String)

    private val SHARED_HOME = listOf("luz", "agua", "gas", "internet", "fibra", "alquiler", "comunidad", "limpieza")
    private val SHARED_SUBS = listOf("netflix", "spotify", "hbo", "disney", "prime", "youtube", "dazn", "filmin", "skyshowtime")

    fun shareable(d: FinanceData, days: Int = 60): List<ShareCandidate> {
        val since = d.today.toEpochDay() - days
        val split = d.splits.map { it.expenseId }.toSet()
        return d.spending.filter { it.epochDay >= since && it.id !in split }.mapNotNull { e ->
            val n = " ${Classifier.normalize(e.note)} "
            val reason = when (e.spendTag()) {
                SpendTag.HOME -> if (SHARED_HOME.any { n.contains(" $it ") }) "Factura de casa: ¿la compartes con quien vives?" else null
                SpendTag.SUBSCRIPTIONS -> if (SHARED_SUBS.any { n.contains(" $it ") }) "Plataforma que se puede compartir" else null
                SpendTag.TRAVEL -> "Viaje: ¿ibais varios?"
                SpendTag.FOOD -> if (e.amount >= 35 || n.contains(" amigos ")) "Comida o cena en grupo" else null
                SpendTag.SUPER -> if (e.amount >= 60) "Compra grande: ¿es para la casa?" else null
                SpendTag.LEISURE -> if (n.contains(" amigos ") || n.contains(" entradas ")) "Plan con amigos" else null
                else -> null
            }
            reason?.let { ShareCandidate(e, it) }
        }.sortedByDescending { it.expense.epochDay }.take(6)
    }

    /** Lo que te deben de gastos repartidos aún sin saldar. */
    fun owed(splits: List<Split>, expenses: List<Expense>): Double = splits.sumOf { s ->
        val e = expenses.firstOrNull { it.id == s.expenseId } ?: return@sumOf 0.0
        e.amount / s.people * (s.people - 1 - s.settled).coerceAtLeast(0)
    }

    // ---------- 13. Simulador de decisiones vitales ----------

    data class SimBase(val income: Double, val cost: Double, val savings: Double, val rent: Double, val fixedIncome: Double) {
        val monthlySaving get() = income - cost
    }

    data class SimResult(
        val savingNow: Double, val savingAfter: Double,
        val in1yNow: Double, val in1yAfter: Double,
        val in5yNow: Double, val in5yAfter: Double,
        val emergencyNow: Double, val emergencyAfter: Double,
        /** Meses hasta quedarte sin ahorro, si pasas a perder dinero cada mes. */
        val runwayMonths: Double?,
    )

    fun simBase(d: FinanceData): SimBase {
        val rent = d.bills.filter { b -> Classifier.normalize(b.name).let { it.contains("alquiler") || it.contains("hipoteca") } }.sumOf { Bills.monthly(it) }
        return SimBase(d.monthlyIncome, costOfLiving(d).monthly, d.wealth.savings + d.wealth.available, rent, d.sources.filter { !it.variable }.sumOf { it.amount })
    }

    fun simulate(base: SimBase, incomeDelta: Double, costDelta: Double, upfront: Double): SimResult {
        val now = base.monthlySaving
        val after = base.income + incomeDelta - (base.cost + costDelta)
        val start = base.savings - upfront
        val costAfter = base.cost + costDelta
        return SimResult(
            now, after,
            base.savings + now * 12, start + after * 12,
            base.savings + now * 60, start + after * 60,
            if (base.cost > 0) base.savings / base.cost else 0.0,
            if (costAfter > 0) start / costAfter else 0.0,
            if (after < 0 && start > 0) start / -after else null,
        )
    }
}
