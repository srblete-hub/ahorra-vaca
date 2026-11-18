package com.caudal.finanzas.domain

import java.time.YearMonth
import kotlin.math.max
import kotlin.math.pow

/** Una línea del análisis: título corto, el dato y una explicación. */
data class BuyFact(val title: String, val value: String, val detail: String)

/** El resultado de "¿Lo compro?": veredicto (0 sí, 1 piénsalo, 2 mejor no) y los datos que lo explican. */
data class BuyCheck(
    val name: String,
    val price: Double,
    val tag: SpendTag,
    val verdict: Int,
    val verdictText: String,
    val facts: List<BuyFact>,
    /** Lo que valdría ese dinero invertido a tu mejor TAE en 1, 5 y 10 años. */
    val invested: List<Pair<Int, Double>>,
    val rate: Double,
)

/**
 * "¿Lo compro o no?": con reglas fijas y tus datos reales (sin IA). Mira cuántas horas de trabajo cuesta según
 * lo que te ha entrado este mes, cómo deja tu mes y tu banco, tu límite en ese tipo de gasto, cuánto retrasa tu
 * objetivo de ahorro y lo que valdría ese dinero si lo invirtieras.
 */
object BuyChecker {
    /** Horas de trabajo al mes que se suponen (jornada completa). */
    const val HOURS_PER_MONTH = 160.0

    fun of(
        name: String, price: Double, d: FinanceData, plan: BankPlan?, budget: SpendingBudget?, goal: GoalStatus?, fmt: (Double) -> String,
    ): BuyCheck {
        val es = java.util.Locale.forLanguageTag("es-ES")
        val tag = Classifier.guess(name) ?: SpendTag.SHOPPING
        val facts = mutableListOf<BuyFact>()
        val m = YearMonth.from(d.today)

        // 1. Horas de trabajo, con lo que ha entrado este mes (o lo previsto si aún no has cobrado).
        val received = d.receivedIn(m)
        val income = if (received > 0) received else d.monthlyIncome
        if (income > 0) {
            val perHour = income / HOURS_PER_MONTH
            val hours = price / perHour
            val h = hours.toInt(); val min = Math.round((hours - h) * 60).toInt()
            facts += BuyFact(
                "Horas de trabajo",
                if (h == 0) "$min min" else if (min == 0) "$h h" else "$h h $min min",
                "Este mes te han entrado ${fmt(income)}: unos ${fmt(perHour)} por hora (contando ${HOURS_PER_MONTH.toInt()} h al mes).",
            )
        }

        // 2. Tu gasto del día a día y lo que te queda este mes.
        var verdict = 0
        val reasons = mutableListOf<String>()
        if (plan != null) {
            val perDay = plan.perDay
            if (perDay > 0) facts += BuyFact(
                "Días de tu gasto diario",
                String.format(es, "%.1f días", price / perDay),
                "Hoy te tocan ${fmt(perDay)} al día hasta el cobro.",
            )
            val spendable = plan.spendable
            val after = spendable - price
            val afterPerDay = after / max(1, plan.days)
            facts += BuyFact(
                "Cómo queda tu mes",
                if (after >= 0) "${fmt(afterPerDay)}/día" else "Te pasas ${fmt(-after)}",
                if (after >= 0) "Te quedarían ${fmt(after)} para gastar en ${plan.days} días (${String.format(es, "%.0f", price / max(1.0, spendable) * 100)} % de lo que te queda)."
                else "Ahora mismo solo te quedan ${fmt(max(0.0, spendable))} para gastar hasta el cobro.",
            )
            facts += BuyFact("En el banco después", fmt(plan.main.balance - price), "${plan.main.name} pasaría de ${fmt(plan.main.balance)} a ${fmt(plan.main.balance - price)}.")
            when {
                after < 0 -> { verdict = 2; reasons += "te pasarías de lo que puedes gastar este mes" }
                price > spendable * 0.25 -> { verdict = max(verdict, 1); reasons += "se lleva más de un cuarto de lo que te queda" }
                price > perDay * 2 -> { verdict = max(verdict, 1); reasons += "son más de dos días de tu gasto diario" }
            }
        }

        // 3. Tu límite en ese tipo de gasto.
        budget?.items?.firstOrNull { it.tag == tag }?.let { b ->
            val total = b.spent + price
            facts += BuyFact(
                "En ${tag.label.lowercase()}",
                "${fmt(total)} de ${fmt(b.monthly)}",
                if (total <= b.monthly) "Llevas ${fmt(b.spent)} este mes; con esto te quedarían ${fmt(b.monthly - total)}."
                else "Llevas ${fmt(b.spent)} este mes; con esto te pasarías ${fmt(total - b.monthly)} de lo que te toca.",
            )
            if (total > b.monthly) { verdict = max(verdict, if (total > b.monthly * 1.5) 2 else 1); reasons += "te pasas en ${tag.label.lowercase()}" }
        }

        // 4. Tu objetivo de ahorro.
        if (goal != null && goal.pace > 0) {
            val days = (price / goal.pace * 30.4).toInt()
            facts += BuyFact(
                "Tu objetivo «${goal.goal.name.ifBlank { "de ahorro" }}»",
                if (days <= 0) "Sin retraso" else "+$days ${if (days == 1) "día" else "días"}",
                "Ahorras unos ${fmt(goal.pace)} al mes: ese dinero tardarías ${if (days <= 0) "menos de un día" else if (days == 1) "1 día" else "$days días"} en volver a ahorrarlo.",
            )
        }

        // 5. En tus propios gastos: "= 11 Monster Energy".
        val since = d.today.minusDays(90).toEpochDay()
        d.spending.filter { it.epochDay >= since && it.amount in 1.0..(price / 2) && it.note.isNotBlank() }
            .groupBy { Merchants.clean(it.note) }
            .filter { it.value.size >= 3 }
            .maxByOrNull { it.value.size }
            ?.let { (shop, list) ->
                val avg = list.sumOf { it.amount } / list.size
                facts += BuyFact("En lo que sueles comprar", "= ${Math.round(price / avg)} × $shop", "Pagas unos ${fmt(avg)} cada vez que vas a $shop.")
            }

        // 6. Si lo invirtieras.
        val rate = d.accounts.maxOfOrNull { it.interestRate }?.takeIf { it > 0 } ?: 5.0
        val invested = listOf(1, 5, 10).map { y -> y to price * (1 + rate / 100).pow(y) }

        if (price >= max(100.0, d.monthlyIncome * 0.1)) { verdict = max(verdict, 1); reasons += "es una compra grande: espera 48 h antes de decidir" }
        val text = when (verdict) {
            0 -> "Puedes permitírtelo sin descolocar tu mes."
            else -> reasons.distinct().joinToString(", ").replaceFirstChar { it.uppercase() } + "."
        }
        return BuyCheck(name.trim(), price, tag, verdict, text, facts, invested, rate)
    }
}
