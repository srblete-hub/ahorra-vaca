package com.caudal.finanzas.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Un objetivo de ahorro: "10.000 € ahorrados en 5 años". */
data class Goal(val name: String, val target: Double, val start: LocalDate, val deadline: LocalDate)

data class GoalStatus(
    val goal: Goal,
    /** Lo que ya tienes para el objetivo: ahorro + inversiones. */
    val current: Double,
    val monthsLeft: Int,
    /** Rentabilidad que se supone (la mejor cuenta remunerada que tienes). */
    val rate: Double,
    /** Cuánto hay que meter cada mes desde hoy para llegar a tiempo. */
    val requiredMonthly: Double,
    /** Lo que estás ahorrando de verdad al mes (media reciente, o lo del reparto). */
    val pace: Double,
    /** Con lo que tienes y tu ritmo, cuánto tendrías en la fecha. */
    val projected: Double,
    /** Cuándo llegarías a tu ritmo (null si no llegarías nunca). */
    val eta: LocalDate?,
    val tips: List<String>,
    /** % de reparto que haría falta en la cuenta principal (null sin reparto). */
    val suggestedPct: Double?,
) {
    val progress get() = (current / goal.target).coerceIn(0.0, 1.0)
    val done get() = current >= goal.target
    val onTrack get() = done || projected >= goal.target * 0.98
    /** Meses de adelanto (+) o retraso (−) respecto a la fecha. */
    val monthsAhead get() = eta?.let { ChronoUnit.MONTHS.between(it, goal.deadline).toInt() }
}

object Goals {
    /** Cuota mensual para pasar de [pv] a [target] en [months] meses con interés compuesto. */
    fun requiredMonthly(pv: Double, target: Double, ratePct: Double, months: Int): Double {
        if (months <= 0) return max(0.0, target - pv)
        val r = InterestMath.monthlyRate(ratePct)
        if (r == 0.0) return max(0.0, (target - pv) / months)
        val g = (1 + r).pow(months)
        return max(0.0, (target - pv * g) * r / (g - 1))
    }

    /** Meses hasta llegar a [target] metiendo [monthly] al mes (null si no se llega en 100 años). */
    fun monthsTo(pv: Double, target: Double, ratePct: Double, monthly: Double): Int? {
        if (pv >= target) return 0
        val r = InterestMath.monthlyRate(ratePct)
        if (r == 0.0) return if (monthly <= 0) null else ceil((target - pv) / monthly).toInt()
        val num = target * r + monthly
        val den = pv * r + monthly
        if (den <= 0 || num <= 0) return null
        val n = ln(num / den) / ln(1 + r)
        return if (n.isNaN() || n > 1200) null else ceil(n).toInt()
    }

    fun rateOf(d: FinanceData) = d.accounts.filter { it.isSaving() }.maxOfOrNull { it.interestRate } ?: 0.0

    /** Ritmo real de ahorro: lo que te sobra de media en los meses cerrados; si no hay, lo que manda el reparto. */
    fun paceOf(d: FinanceData, plan: BankPlan?): Double {
        val closed = d.closedMonths(3).filter { d.receivedIn(it) > 0 }
        val netAvg = if (closed.isEmpty()) null else closed.sumOf { d.receivedIn(it) - d.spentIn(it) } / closed.size
        return max(0.0, netAvg ?: plan?.saveGoal ?: 0.0)
    }

    fun monthsLeft(d: FinanceData, goal: Goal) = max(0, ChronoUnit.MONTHS.between(d.today.withDayOfMonth(1), goal.deadline.withDayOfMonth(1)).toInt())

    fun status(goal: Goal, d: FinanceData, plan: BankPlan?, fmt: (Double) -> String): GoalStatus {
        val current = d.wealth.savings + d.wealth.invested
        val rate = rateOf(d)
        val pace = paceOf(d, plan)
        val months = monthsLeft(d, goal)
        val projected = InterestMath.project(current, rate, pace, months).finalBalance
        val eta = monthsTo(current, goal.target, rate, pace)?.let { d.today.plusMonths(it.toLong()) }
        return build(goal, d, plan, fmt, current, pace, eta, projected)
    }

    fun build(goal: Goal, d: FinanceData, plan: BankPlan?, fmt: (Double) -> String, current: Double, pace: Double, eta: LocalDate?, projected: Double): GoalStatus {
        val months = monthsLeft(d, goal)
        val rate = rateOf(d)
        val required = requiredMonthly(current, goal.target, rate, months)
        val suggestedPct: Double? = null

        val tips = mutableListOf<String>()
        val gap = required - pace
        if (current >= goal.target) tips += "¡Objetivo cumplido! Ponte uno nuevo para seguir avanzando."
        else if (gap <= 0) {
            tips += "Vas bien: a tu ritmo (${fmt(pace)} al mes) llegarías" + (eta?.let { " en ${it.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-ES"))} de ${it.year}" } ?: "") + "."
            tips += "Automatiza: manda ${fmt(required)} al ahorro el día que cobras y olvídate."
        } else {
            tips += "Te faltan ${fmt(gap)} al mes para llegar a tiempo."
            if (plan?.target != null) tips += "Este mes tu reparto manda ${fmt(plan.saveGoal)} a ${plan.target.name}; para llegar harían falta ${fmt(required)}. Baja el margen de fin de mes o tu gasto habitual."
            val wants = d.closedMonths(3).flatMap { d.spendingIn(it) }.let { byTag(it) }
                .filter { !it.first.need && it.first != SpendTag.OTHER }.map { it.first to it.second / max(1, d.closedMonths(3).size) }
            wants.firstOrNull()?.let { (t, avg) ->
                val cut = minOf(gap, avg * 0.3)
                if (cut >= 5) tips += "Recorta ${t.label.lowercase()} un ${(cut / avg * 100).toInt()} % (${fmt(cut)} al mes): es tu gasto prescindible más alto."
            }
            d.bills.filter { !it.yearly && Classifier.guess(it.name) == SpendTag.SUBSCRIPTIONS }.maxByOrNull { it.amount }?.let {
                tips += "Cancelar o compartir ${it.name} te da ${fmt(it.amount)} al mes más."
            }
            val longer = monthsTo(current, goal.target, rate, pace)
            if (longer != null && pace > 0) tips += "Si no cambias nada, llegarías en ${longer / 12} años y ${longer % 12} meses."
        }
        if (rate < 2 && current > 500) tips += "Tu ahorro no da intereses: en una cuenta al 2–5 % llegarías antes sin poner más."
        if (rate >= 2 && !goal.deadline.isBefore(d.today)) {
            val interest = InterestMath.project(current, rate, required, months).interest
            if (interest > 1) tips += "Los intereses (${String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", rate)} %) pondrán unos ${fmt(interest)} por ti."
        }
        return GoalStatus(goal, current, months, rate, required, pace, projected, eta, tips, suggestedPct)
    }
}

// ---------- Edad financiera (inspirada en la "WHOOP Age") ----------

/** Un factor que suma o resta años a tu edad financiera. [years] positivo = te da madurez (bueno). */
data class AgeFactor(val name: String, val value: String, val years: Double, val explanation: String)

data class FinancialAge(
    val realAge: Int,
    val factors: List<AgeFactor>,
    /** Velocidad a la que maduran tus finanzas: 1x normal, >1 más rápido (bueno), <1 te quedas atrás (−1x…3x). */
    val pace: Double,
    /** Con pocos meses de datos la cifra aún se mueve mucho. */
    val calibrating: Boolean,
) {
    /** Madurez financiera: cuanto mayor, mejor (tener la edad financiera de un adolescente es malo). */
    val age get() = (realAge + factors.sumOf { it.years }).coerceAtLeast(14.0)
    val difference get() = age - realAge
}

object FinancialAging {
    /** Patrimonio que se espera para tu edad, en años de ingresos (≈1x a los 30, 3x a los 38, 6x a los 50). */
    fun expectedMultiple(age: Int) = max(0.25, (age - 26) * 0.25)

    fun evaluate(realAge: Int, d: FinanceData, goal: GoalStatus?): FinancialAge {
        val f = mutableListOf<AgeFactor>()
        val annual = d.monthlyIncome * 12
        val w = d.wealth

        val multiple = if (annual > 0) w.total / annual else null
        val expected = expectedMultiple(realAge)
        f += if (multiple == null) AgeFactor("Patrimonio para tu edad", "Sin ingresos", 0.0, "Apunta tus ingresos para compararlo.")
        else AgeFactor(
            "Patrimonio para tu edad", "%.1fx tu sueldo anual".format(multiple),
            ((expected - multiple) * 1.2).coerceIn(-4.0, 4.0),
            "Referencia habitual a tu edad: unas ${"%.1f".format(expected)} veces lo que ganas en un año (1× a los 30, 3,5× a los 40, 6× a los 50). Orientativo.",
        )

        val closed = d.closedMonths(3).filter { d.receivedIn(it) > 0 }
        val inc = closed.sumOf { d.receivedIn(it) }
        val rate = if (inc > 0) (inc - closed.sumOf { d.spentIn(it) }) / inc * 100 else null
        f += if (rate == null) AgeFactor("Capacidad de ahorro", "Calibrando", 0.0, "Necesito al menos un mes cerrado con cobros.")
        else AgeFactor(
            "Capacidad de ahorro", "%.0f %%".format(rate),
            when { rate >= 30 -> -2.5; rate >= 20 -> -1.5; rate >= 10 -> -0.5; rate >= 0 -> 1.0; else -> 3.0 },
            "Lo que te queda de cada euro que entra. Lo ideal, un 20 % o más.",
        )

        val cost = Insights.costOfLiving(d).monthly
        val cushion = if (cost > 0) w.savings / cost else null
        f += if (cushion == null) AgeFactor("Colchón de emergencia", "Calibrando", 0.0, "Apunta gastos para calcularlo.")
        else AgeFactor(
            "Colchón de emergencia", "%.1f meses".format(cushion),
            when { cushion >= 6 -> -2.0; cushion >= 3 -> -1.0; cushion >= 1 -> 0.5; else -> 2.0 },
            "Meses que aguantarías sin ingresos con tu coste de vida real.",
        )

        val longTerm = w.savings + w.invested
        val investedShare = if (longTerm > 0) w.invested / longTerm * 100 else 0.0
        f += AgeFactor(
            "Dinero invertido", "%.0f %% de tu ahorro".format(investedShare),
            when { investedShare >= 30 -> -1.5; investedShare > 0 -> -0.5; else -> 1.0 },
            "Invertir a largo plazo hace que tu dinero crezca más que la inflación.",
        )

        val debtRatio = if (annual > 0) w.debts / annual * 100 else if (w.debts > 0) 100.0 else 0.0
        f += AgeFactor(
            "Deudas", if (w.debts <= 0) "Ninguna" else "%.0f %% de un año de sueldo".format(debtRatio),
            when { w.debts <= 0 -> -0.5; debtRatio <= 35 -> 1.0; else -> 3.0 },
            "Las deudas caras son lo que más envejece tus finanzas.",
        )

        val fixedPct = if (d.monthlyIncome > 0) d.bills.sumOf { Bills.monthly(it) } / d.monthlyIncome * 100 else null
        if (fixedPct != null) f += AgeFactor(
            "Gastos fijos", "%.0f %% de lo que ganas".format(fixedPct),
            when { fixedPct <= 35 -> -0.5; fixedPct <= 50 -> 0.5; else -> 1.5 },
            "Cuanto menos se va en fijos, más margen tienes cada mes.",
        )

        val recent = d.spending.filter { it.epochDay > d.today.toEpochDay() - 90 && it.billId == null }
        val total = recent.sumOf { it.amount }
        if (total > 0) {
            val wants = recent.filter { !it.spendTag().need }.sumOf { it.amount } / total * 100
            f += AgeFactor(
                "Hábitos de gasto", "%.0f %% prescindible".format(wants),
                when { wants <= 30 -> -0.5; wants <= 50 -> 0.0; else -> 1.0 },
                "Parte de tu gasto variable que va a caprichos, en los últimos 90 días.",
            )
        }

        f += when {
            goal == null -> AgeFactor("Objetivo", "Sin objetivo", 0.5, "Tener una meta concreta ayuda a ahorrar más.")
            goal.onTrack -> AgeFactor("Objetivo", "Vas a tiempo", -1.0, "${goal.goal.name}: a tu ritmo llegas.")
            else -> AgeFactor("Objetivo", "Vas con retraso", 0.5, "${goal.goal.name}: te faltan ${"%.0f".format(goal.requiredMonthly - goal.pace)} € al mes.")
        }

        val pace = when (rate) {
            null -> 1.0
            else -> (1.0 + (rate - 10) / 15).coerceIn(-1.0, 3.0)
        }
        // Más madurez = mejor: lo que antes "rejuvenecía" ahora suma años y viceversa.
        val maturity = f.map { it.copy(years = -it.years) }.sortedByDescending { it.years }
        return FinancialAge(realAge, maturity, pace, calibrating = d.closedMonths(3).size < 2 || d.monthlyIncome <= 0)
    }
}

/** Varios objetivos en orden de prioridad: el ahorro y lo que apartas cada mes van primero al de arriba. */
object GoalList {
    fun parse(raw: String): List<Goal> = raw.lines().mapNotNull { line ->
        val p = line.split('|')
        if (p.size != 4) return@mapNotNull null
        val target = p[1].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val deadline = p[3].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        Goal(p[0].ifBlank { "Mi objetivo" }, target, LocalDate.ofEpochDay(p[2].toLongOrNull() ?: deadline), LocalDate.ofEpochDay(deadline))
    }

    fun format(list: List<Goal>): String = list.joinToString("\n") {
        listOf(it.name.replace("|", " ").replace("\n", " "), it.target.toString(), it.start.toEpochDay().toString(), it.deadline.toEpochDay().toString()).joinToString("|")
    }

    /** Simula mes a mes: cada objetivo recibe primero lo que necesita, por orden; lo que sobra va al primero sin completar. */
    fun plan(goals: List<Goal>, d: FinanceData, plan: BankPlan?, fmt: (Double) -> String, extraNow: Double = 0.0): List<GoalStatus> {
        if (goals.isEmpty()) return emptyList()
        val rate = Goals.rateOf(d)
        val r = InterestMath.monthlyRate(rate)
        val pace = Goals.paceOf(d, plan)
        var left = max(0.0, d.wealth.savings + d.wealth.invested - extraNow)
        val current = goals.map { g -> min(g.target, left).also { left -= it } }
        val bal = current.toMutableList()
        val req = goals.mapIndexed { i, g -> Goals.requiredMonthly(current[i], g.target, rate, Goals.monthsLeft(d, g)) }
        val eta = arrayOfNulls<LocalDate>(goals.size)
        val projected = DoubleArray(goals.size) { current[it] }
        val firstGive = DoubleArray(goals.size)
        goals.indices.filter { bal[it] >= goals[it].target }.forEach { eta[it] = d.today }
        for (m in 1..600) {
            var avail = pace
            val open = goals.indices.filter { eta[it] == null }
            if (open.isEmpty()) break
            open.forEach { bal[it] *= 1 + r }
            for (i in open) { val give = min(avail, req[i]); bal[i] += give; avail -= give; if (m == 1) firstGive[i] += give }
            open.firstOrNull()?.let { bal[it] += avail; if (m == 1) firstGive[it] += avail }
            for (i in goals.indices) {
                if (eta[i] == null && bal[i] >= goals[i].target) eta[i] = d.today.plusMonths(m.toLong())
                if (m == Goals.monthsLeft(d, goals[i])) projected[i] = bal[i]
            }
        }
        return goals.mapIndexed { i, g ->
            val months = Goals.monthsLeft(d, g)
            val proj = if (months == 0) current[i] else if (eta[i] != null && eta[i]!! <= g.deadline) max(projected[i], g.target) else projected[i]
            Goals.build(g, d, plan, fmt, current[i], firstGive[i], eta[i], proj)
        }
    }

    /** "Si pones primero B…": cómo cambian las fechas al cambiar el orden de los dos primeros. */
    fun swapImpact(goals: List<Goal>, d: FinanceData, plan: BankPlan?, fmt: (Double) -> String): String? {
        if (goals.size < 2) return null
        val now = this.plan(goals, d, plan, fmt)
        val swapped = this.plan(listOf(goals[1], goals[0]) + goals.drop(2), d, plan, fmt)
        val fmtDate = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.forLanguageTag("es-ES"))
        fun e(s: GoalStatus) = s.eta?.format(fmtDate) ?: "nunca"
        return "Si pones primero \"${goals[1].name}\", llegaría en ${e(swapped[0])} (ahora ${e(now[1])}) y \"${goals[0].name}\" pasaría a ${e(swapped[1])} (ahora ${e(now[0])})."
    }
}
