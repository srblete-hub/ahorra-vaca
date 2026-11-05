package com.caudal.finanzas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val ES = Locale.forLanguageTag("es-ES")
private const val WEEKS_PER_MONTH = 52.0 / 12

/** Lo que fijas tú para un tipo de gasto: [amount] por semana o por mes. */
data class TagLimit(val amount: Double, val weekly: Boolean) {
    val monthly get() = if (weekly) amount * WEEKS_PER_MONTH else amount
}

/** Cuánto te toca gastar en un tipo de gasto este mes y cuánto llevas. */
data class TagBudget(val tag: SpendTag, val monthly: Double, val pct: Double, val fixedByYou: TagLimit?, val spent: Double) {
    val weekly get() = monthly / WEEKS_PER_MONTH
    val left get() = monthly - spent
}

data class SpendingBudget(
    /** Lo que hay para gastar este mes en total (lo que puedes gastar ahora + lo ya gastado). */
    val base: Double,
    /** Sale del reparto de tu cuenta principal (si no, de ingresos − fijos − ahorro). */
    val fromBank: Boolean,
    val income: Double,
    val fixed: Double,
    val saving: Double,
    val items: List<TagBudget>,
)

object Budgeting {
    /** Reparto de partida si aún no hay historial. */
    private val DEFAULT_WEIGHTS = mapOf(
        SpendTag.SUPER to 35.0, SpendTag.FOOD to 15.0, SpendTag.TRANSPORT to 12.0, SpendTag.LEISURE to 10.0,
        SpendTag.SHOPPING to 10.0, SpendTag.HEALTH to 5.0, SpendTag.SUBSCRIPTIONS to 3.0, SpendTag.OTHER to 10.0,
    )

    /** "TRANSPORT:30:W;SUPER:250:M" ↔ límites. Sin nada guardado: gasolina 30 € a la semana. */
    fun parseLimits(raw: String?): Map<SpendTag, TagLimit> {
        if (raw == null) return mapOf(SpendTag.TRANSPORT to TagLimit(30.0, weekly = true))
        return raw.split(';').mapNotNull { part ->
            val p = part.split(':')
            if (p.size != 3) return@mapNotNull null
            val tag = SpendTag.of(p[0]) ?: return@mapNotNull null
            val amount = p[1].toDoubleOrNull() ?: return@mapNotNull null
            tag to TagLimit(amount, p[2] == "W")
        }.toMap()
    }

    fun formatLimits(limits: Map<SpendTag, TagLimit>): String =
        limits.entries.joinToString(";") { (t, l) -> "${t.name}:${l.amount}:${if (l.weekly) "W" else "M"}" }

    /** Pago de un gasto fijo: enlazado, o con su nombre e importe parecido (ya cuenta en los fijos). */
    fun isBillPayment(e: com.caudal.finanzas.data.Expense, bills: List<com.caudal.finanzas.data.RecurringBill>): Boolean =
        e.billId != null || bills.any { b -> e.note.contains(b.name, ignoreCase = true) && kotlin.math.abs(e.amount - b.amount) <= b.amount * 0.1 }

    /** Gasto variable (sin fijos ni ahorro) de un mes. */
    fun variable(d: FinanceData, m: YearMonth) = d.spendingIn(m).filter { !isBillPayment(it, d.bills) }

    fun plan(d: FinanceData, saving: Double, limits: Map<SpendTag, TagLimit>, plan: BankPlan? = null, profile: Map<SpendTag, Double>? = null): SpendingBudget {
        val income = d.monthlyIncome
        val fixed = d.bills.sumOf { Bills.monthly(it) }
        val spentNow = variable(d, d.thisMonth).sumOf { it.amount }
        // Con reparto, la rueda parte de lo mismo que "Puedes gastar": lo que te queda + lo que ya llevas.
        val fromBank = plan?.target != null
        val base = if (fromBank) max(0.0, plan!!.spendable + spentNow) else max(0.0, income - fixed - saving)
        val fixedTotal = limits.values.sumOf { it.monthly }
        val rest = max(0.0, base - fixedTotal)

        // Pesos: tu gasto medio por tipo (sin gastos fijos) en los meses cerrados; sin historial, uno estándar.
        val closed = d.closedMonths(3)
        val history = byTag(closed.flatMap { m -> variable(d, m) }).toMap()
        // Con perfil de ahorrador, los % los fija el perfil; si no, tus hábitos.
        val weights = (profile ?: if (history.values.sum() > 0) history else DEFAULT_WEIGHTS)
            .filterKeys { it !in limits }.filterValues { it > 0 }
        val weightSum = weights.values.sum()

        val month = d.thisMonth
        val spent = byTag(variable(d, month)).toMap()
        val items = (limits.keys + weights.keys).distinct().map { t ->
            val monthly = limits[t]?.monthly ?: if (weightSum > 0) rest * weights.getValue(t) / weightSum else 0.0
            TagBudget(t, monthly, if (base > 0) monthly / base * 100 else 0.0, limits[t], spent[t] ?: 0.0)
        }.sortedByDescending { it.monthly }
        return SpendingBudget(base, fromBank, income, fixed, saving, items)
    }
}

/** Cuánto meter en tu cuenta de ahorro (Robinhood…) al mes, con el porqué. */
data class SavingsAdvice(
    val recommended: Double,
    /** % de lo que entra cada mes. */
    val pctOfIncome: Double,
    /** Lo máximo razonable sin ahogarte: ingresos − fijos − lo básico − un mínimo para ti. */
    val capacity: Double,
    val reasons: List<String>,
    val inOneYear: Double,
    val interestOneYear: Double,
)

object SavingsAdvisor {
    fun advise(d: FinanceData, goal: GoalStatus?, fmt: (Double) -> String, plan: BankPlan? = null): SavingsAdvice? {
        val income = d.monthlyIncome
        if (income <= 0) return null
        val fixed = d.bills.sumOf { Bills.monthly(it) }
        // Lo básico (súper, transporte, salud…) según tu media; si no hay datos, un 25 % de lo que entra.
        val closed = d.closedMonths(3)
        val needs = if (closed.isEmpty()) income * 0.25
        else closed.sumOf { m -> Budgeting.variable(d, m).filter { it.spendTag().need }.sumOf { it.amount } } / closed.size
        val capacity = max(0.0, income - fixed - needs - income * 0.10)

        val reasons = mutableListOf<String>()
        val candidates = mutableListOf(income * 0.20 to "La regla clásica: un 20 % de lo que entra (${fmt(income * 0.20)}).")
        val cost = Insights.costOfLiving(d).monthly
        val cushionGap = cost * 3 - d.wealth.savings
        if (cushionGap > 0) candidates += cushionGap / 12 to "Te faltan ${fmt(cushionGap)} para tener 3 meses de colchón: en un año serían ${fmt(cushionGap / 12)} al mes."
        if (goal != null && !goal.done) candidates += goal.requiredMonthly to "Para \"${goal.goal.name}\" necesitas ${fmt(goal.requiredMonthly)} al mes."
        val (want, why) = candidates.maxBy { it.first }
        reasons += why
        val recommended = (min(want, capacity) / 5).roundToInt() * 5.0
        if (want > capacity) reasons += "No te recomiendo más de ${fmt(capacity)}: después de fijos y lo básico necesitas margen para vivir."
        else reasons += "Te deja ${fmt(capacity - recommended)} de margen sobre lo que puedes permitirte."
        // Dinero arrastrado de meses anteriores en la cuenta principal: se puede mover de una vez.
        if (plan?.target != null) {
            val carried = plan.monthMoney - max(0.0, income - fixed)
            if (carried > 100) reasons += "Además arrastras unos ${fmt(carried)} de meses anteriores en ${plan.main.name}: si no los necesitas, muévelos de una vez a ${plan.target.name}."
        }
        val rate = d.accounts.filter { it.isSaving() }.maxOfOrNull { it.interestRate } ?: 0.0
        val year = InterestMath.project(0.0, rate, recommended, 12)
        return SavingsAdvice(recommended, recommended / income * 100, capacity, reasons, year.finalBalance, year.interest)
    }
}

/** Consejo de hoy: según el día que es, lo que queda para cobrar y cómo vas. */
data class DailyAdvice(val headline: String, val perDay: Double, val spentToday: Double, val tips: List<String>)

object Advisor {
    /** Próximo cobro fijo (nómina): el día de inicio de la fuente fija que aún no ha llegado. */
    fun nextFixedPayday(d: FinanceData): LocalDate? = nextPayday(d, fixedOnly = true)

    /** Próximo cobro (fijo o variable, como YouTube el día 22) que aún no ha llegado. */
    fun nextPayday(d: FinanceData, fixedOnly: Boolean = false): LocalDate? {
        val fixed = d.sources.filter { !fixedOnly || !it.variable }
        if (fixed.isEmpty()) return null
        return fixed.mapNotNull { s ->
            val thisMonth = d.thisMonth
            val received = d.incomes.any { it.sourceId == s.id && d.inMonth(it.epochDay, thisMonth) }
            val day = thisMonth.atDay(min(s.dayFrom, thisMonth.lengthOfMonth()))
            if (!received && !day.isBefore(d.today)) day
            else thisMonth.plusMonths(1).let { it.atDay(min(s.dayFrom, it.lengthOfMonth())) }
        }.minOrNull()
    }

    fun today(d: FinanceData, plan: BankPlan?, budget: SpendingBudget?, habits: Insights.Habits?, fmt: (Double) -> String, userName: String = ""): DailyAdvice {
        val today = d.today
        val payday = nextPayday(d)
        val payName = payday?.let { p -> d.sources.firstOrNull { s -> p.dayOfMonth == min(s.dayFrom, YearMonth.from(p).lengthOfMonth()) }?.name }
        val daysToPay = payday?.let { (it.toEpochDay() - today.toEpochDay()).toInt() }
        val spendable = plan?.spendable
        val days = max(1, daysToPay ?: (YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1))
        val perDay = if (spendable != null && spendable > 0) spendable / days else 0.0
        val spentToday = d.spending.filter { it.epochDay == today.toEpochDay() }.sumOf { it.amount }
        val dayName = today.format(DateTimeFormatter.ofPattern("EEEE d", ES)).replaceFirstChar { it.titlecase(ES) }
        val headline = dayName + when {
            daysToPay == null -> ""
            daysToPay == 0 -> " · hoy cobras"
            daysToPay == 1 -> " · mañana cobras" + (payName?.let { " ($it)" } ?: "")
            else -> " · cobras en $daysToPay días" + (payName?.let { " ($it)" } ?: "")
        }

        val tips = mutableListOf<String>()
        // 1) Acabas de cobrar: primero el ahorro.
        val lastPay = d.incomes.filter { it.sourceId != null && d.sources.any { s -> s.id == it.sourceId && !s.variable } }.maxOfOrNull { it.epochDay }
        if (plan?.target != null && plan.moveNow > 1 && lastPay != null && today.toEpochDay() - lastPay in 0..4) {
            tips += "Acabas de cobrar: mueve ya ${fmt(plan.moveNow)} a ${plan.target.name} (tu ahorro del mes) antes de empezar a gastar."
        }
        plan?.incoming?.firstOrNull { it.third == today }?.let { (name, amount, _) ->
            tips += "Hoy debería llegarte $name (~${fmt(amount)}). Cuando llegue, márcalo como cobrado con el importe real" +
                (if (plan.moveLater > 0 && plan.target != null) " y mueve ${fmt(plan.moveLater)} a ${plan.target.name}." else ".")
        }
        if (plan != null && plan.buffer > 0 && today.dayOfMonth >= today.lengthOfMonth() - 3) {
            val endWith = plan.spendable + plan.buffer - plan.expectedSpend
            tips += if (endWith >= plan.buffer * 0.5) "Fin de mes a la vista: a tu ritmo llegas con ~${fmt(endWith)} de sobra." else "Fin de mes justo: a tu ritmo llegarías con ${fmt(endWith)}. Estos días, solo lo imprescindible."
        }
        // 2) Cuánto tienes al día y cómo vas hoy.
        if (plan?.target != null && plan.saveGoal < plan.targetSaving - 5 && plan.targetSaving < Double.MAX_VALUE / 2) {
            tips += if (plan.saveGoal <= 0) "Este mes no te llega para ahorrar sin quedarte justo: prioriza llegar a fin de mes con +${fmt(plan.buffer)}."
            else "Este mes solo puedes ahorrar ${fmt(plan.saveGoal)} de los ${fmt(plan.targetSaving)} que te propones: lo demás lo necesitas para vivir hasta fin de mes."
        }
        if (spendable != null) {
            when {
                spendable <= 0 -> tips += "Lo de este mes ya está comprometido: hasta cobrar, solo lo imprescindible."
                spentToday > perDay * 1.2 -> tips += "Hoy llevas ${fmt(spentToday)}, más de tus ${fmt(perDay)} diarios: mañana intenta quedarte en ${fmt(max(0.0, (spendable - (spentToday - perDay)) / days))}."
                spentToday > perDay -> tips += "Hoy llevas ${fmt(spentToday)}: justo en tu límite de ${fmt(perDay)}. Mejor no gastar más hoy."
                spentToday > 0 -> tips += "Hoy llevas ${fmt(spentToday)} de ${fmt(perDay)}: vas bien."
                else -> tips += "Tienes ${fmt(perDay)} para hoy. Si no los gastas, se reparten entre los días que quedan."
            }
        }
        // 3) Ayer.
        val yesterday = d.spending.filter { it.epochDay == today.toEpochDay() - 1 && it.billId == null }.sumOf { it.amount }
        if (perDay > 0 && yesterday > perDay * 1.5) tips += "Ayer gastaste ${fmt(yesterday)}, bastante más de lo previsto. Compénsalo con un par de días tranquilos."
        // 4) Final de mes / cerca del cobro.
        if (daysToPay != null && daysToPay in 1..3) tips += "Faltan $daysToPay ${if (daysToPay == 1) "día" else "días"} para cobrar: aguanta con lo que tengas en casa."
        else if (daysToPay != null && daysToPay > 20 && spendable != null && spendable > 0) tips += "Queda mucho mes por delante: no gastes de golpe, ve semana a semana (${fmt(perDay * 7)} por semana)."
        // 5) Fin de semana.
        val dow = today.dayOfWeek
        if (dow == DayOfWeek.THURSDAY || dow == DayOfWeek.FRIDAY) {
            val weekend = habits?.weekendAvg?.let { it * 2 }
            tips += "Llega el fin de semana" + (if (weekend != null && weekend > 0) ": sueles gastar unos ${fmt(weekend)}." else ".") +
                if (perDay > 0) " Para no pasarte, deja el finde en ${fmt(perDay * 2.5)}." else ""
        }
        // 6) Gastos fijos que salen pronto.
        d.bills.mapNotNull { b -> Bills.dueDate(b, d.thisMonth)?.takeIf { !it.isBefore(today) && it.toEpochDay() - today.toEpochDay() <= 3 && !Bills.isPaid(b, d.thisMonth, d.expenses) }?.let { b to it } }
            .forEach { (b, due) -> tips += "${if (due == today) "Hoy" else "El ${due.dayOfMonth}"} se cobra ${b.name} (${fmt(b.amount)}): que haya saldo en la cuenta." }
        // 7) Presupuesto por tipo: semana de gasolina y lo que va pasado de ritmo.
        budget?.items?.forEach { b ->
            val lim = b.fixedByYou
            if (lim != null && lim.weekly) {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
                val week = d.spending.filter { it.epochDay >= monday && it.spendTag() == b.tag }.sumOf { it.amount }
                tips += if (week >= lim.amount) "Esta semana ya has gastado ${fmt(week)} en ${b.tag.label.lowercase()} (tu límite: ${fmt(lim.amount)})."
                else "Te quedan ${fmt(lim.amount - week)} de ${b.tag.label.lowercase()} esta semana."
            } else if (b.monthly > 0) {
                val expected = b.monthly * today.dayOfMonth / YearMonth.from(today).lengthOfMonth()
                if (b.spent > expected * 1.3 && b.spent > 20) tips += "En ${b.tag.label.lowercase()} llevas ${fmt(b.spent)} de ${fmt(b.monthly)} y estamos a día ${today.dayOfMonth}: frena un poco."
            }
        }
        WeeklyNudge.find(d, userName, fmt)?.let { tips.add(0, it) }
        return DailyAdvice(headline, perDay, spentToday, tips.take(5))
    }
}

/** Objetivos que puedes marcar en la ventana inicial. */
enum class LifeGoal(val label: String) {
    CUSHION("Colchón de emergencia"), TRAVEL("Viajar"), HOME("Comprar casa o la entrada"), CAR("Coche"),
    INVEST("Invertir"), DEBTS("Pagar deudas"), TREATS("Darme caprichos sin culpa"),
}

/**
 * Cuán ahorrador quieres ser fija los % de cada tipo de gasto y cuánto se supone que gastarás
 * (más ahorrador = la app reserva menos para gastar y manda más al ahorro).
 */
object SavingProfile {
    val STYLES = listOf(
        "Relajado" to "Disfruto el día a día; ahorro lo que sobre",
        "Equilibrado" to "Ni me privo ni derrocho",
        "Ahorrador" to "Prefiero recortar caprichos para ahorrar más",
        "Muy ahorrador" to "Lo justo para vivir; el resto, a ahorrar",
    )

    /** Reparto del ingreso (necesidades / ocio / ahorro) según cuán ahorrador eres. */
    fun split(style: Int): Triple<Double, Double, Double> = when (style) {
        0 -> Triple(55.0, 30.0, 15.0)
        2 -> Triple(50.0, 20.0, 30.0)
        3 -> Triple(45.0, 10.0, 45.0)
        else -> Triple(50.0, 30.0, 20.0)
    }

    /** Cuánto de tu gasto habitual cuenta el reparto: el ahorrador asume que gastarás menos. */
    fun spendFactor(style: Int) = when (style) { 0 -> 1.1; 2 -> 0.9; 3 -> 0.8; else -> 1.0 }

    fun weights(style: Int, goals: Set<String>): Map<SpendTag, Double>? {
        if (style < 0) return null
        val w = when (style) {
            0 -> mutableMapOf(SpendTag.SUPER to 30.0, SpendTag.FOOD to 18.0, SpendTag.TRANSPORT to 12.0, SpendTag.LEISURE to 14.0, SpendTag.SHOPPING to 12.0, SpendTag.HEALTH to 5.0, SpendTag.SUBSCRIPTIONS to 4.0, SpendTag.OTHER to 5.0)
            1 -> mutableMapOf(SpendTag.SUPER to 35.0, SpendTag.FOOD to 14.0, SpendTag.TRANSPORT to 12.0, SpendTag.LEISURE to 11.0, SpendTag.SHOPPING to 10.0, SpendTag.HEALTH to 6.0, SpendTag.SUBSCRIPTIONS to 4.0, SpendTag.OTHER to 8.0)
            2 -> mutableMapOf(SpendTag.SUPER to 42.0, SpendTag.FOOD to 10.0, SpendTag.TRANSPORT to 13.0, SpendTag.LEISURE to 8.0, SpendTag.SHOPPING to 7.0, SpendTag.HEALTH to 7.0, SpendTag.SUBSCRIPTIONS to 3.0, SpendTag.OTHER to 10.0)
            else -> mutableMapOf(SpendTag.SUPER to 48.0, SpendTag.FOOD to 6.0, SpendTag.TRANSPORT to 14.0, SpendTag.LEISURE to 5.0, SpendTag.SHOPPING to 5.0, SpendTag.HEALTH to 8.0, SpendTag.SUBSCRIPTIONS to 2.0, SpendTag.OTHER to 12.0)
        }
        fun move(from: SpendTag, to: SpendTag, pts: Double) { val take = minOf(pts, w[from] ?: 0.0); w[from] = (w[from] ?: 0.0) - take; w[to] = (w[to] ?: 0.0) + take }
        if (LifeGoal.TRAVEL.name in goals) { move(SpendTag.SHOPPING, SpendTag.TRAVEL, 3.0); move(SpendTag.LEISURE, SpendTag.TRAVEL, 2.0) }
        if (LifeGoal.TREATS.name in goals) { move(SpendTag.OTHER, SpendTag.LEISURE, 3.0); move(SpendTag.SUPER, SpendTag.SHOPPING, 2.0) }
        if (LifeGoal.HOME.name in goals || LifeGoal.DEBTS.name in goals) { move(SpendTag.FOOD, SpendTag.OTHER, 2.0); move(SpendTag.SHOPPING, SpendTag.OTHER, 2.0) }
        return w.filterValues { it > 0 }
    }
}

// ---------- Por qué sube o baja tu patrimonio ----------

object WealthExplainer {
    /**
     * "Debido a la bajada de ETH (−4,2 % en 24 h) y a tus gastos (−820 €); lo compensa en parte lo que has cobrado".
     * Separa lo que entra y sale de tus cuentas desde [fromDay] y atribuye el resto a tus inversiones.
     */
    fun explain(d: FinanceData, fromDay: Long, change: Double, dayChanges: Map<String, Double>, fmt: (Double) -> String): String? {
        if (kotlin.math.abs(change) < 1) return null
        val income = d.incomes.filter { it.epochDay > fromDay && it.accountId != null }.sumOf { it.amount }
        val spent = d.expenses.filter { it.epochDay > fromDay && it.accountId != null && it.categoryId !in d.savingIds }.sumOf { it.amount }
        val invest = change - income + spent
        val moves = (d.crypto.map { Triple(it.symbol, it.amount * it.price, dayChanges[it.symbol]) } + d.stocks.map { Triple(it.ticker, it.shares * it.price, dayChanges[it.ticker]) })
            .mapNotNull { (sym, value, pct) -> pct?.let { Triple(sym, it, value - value / (1 + it / 100)) } }
        val parts = mutableListOf<Pair<String, Double>>()
        if (kotlin.math.abs(invest) >= 1) {
            val drivers = moves.filter { kotlin.math.sign(it.third) == kotlin.math.sign(invest) && kotlin.math.abs(it.second) >= 0.5 }.sortedByDescending { kotlin.math.abs(it.third) }.take(2)
            val word = if (invest < 0) "la bajada" else "la subida"
            parts += (if (drivers.isNotEmpty()) "$word de " + drivers.joinToString(" y ") { "${it.first} (${"%+.1f".format(java.util.Locale.forLanguageTag("es-ES"), it.second)} % en 24 h)" }
            else "$word de tus inversiones (${(if (invest > 0) "+" else "−") + fmt(kotlin.math.abs(invest))})") to invest
        }
        if (income >= 1) parts += "lo que has cobrado (+${fmt(income)})" to income
        if (spent >= 1) parts += "tus gastos (−${fmt(spent)})" to -spent
        val pro = parts.filter { kotlin.math.sign(it.second) == kotlin.math.sign(change) }.sortedByDescending { kotlin.math.abs(it.second) }
        val contra = parts.filter { kotlin.math.sign(it.second) != kotlin.math.sign(change) }.sortedByDescending { kotlin.math.abs(it.second) }
        if (pro.isEmpty()) return null
        return "Debido a " + pro.take(2).joinToString(" y ") { it.first } + (contra.firstOrNull()?.let { (if (change > 0) "; frenado en parte por " else "; suavizado en parte por ") + it.first } ?: "") + "."
    }
}
