package com.caudal.finanzas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private val ES = Locale.forLanguageTag("es-ES")
private fun f1(v: Double) = String.format(ES, "%.1f", v)
private fun pc(v: Double) = String.format(ES, "%.0f %%", v * 100)

/**
 * Una variable de un círculo. [score] va de 0 a 100 por una curva continua (nunca un umbral);
 * [score] null = no hay datos y no cuenta (y baja la confianza del círculo).
 */
data class CircleFactor(
    val name: String,
    val score: Double?,
    val weight: Double,
    val detail: String,
    val advice: String,
    /** La fórmula, para que se vea de dónde sale el número. */
    val formula: String = "",
) {
    val provisional get() = score == null
}

/**
 * Un círculo: media geométrica ponderada de sus variables (una muy floja hunde el total y no se
 * compensa con otra muy buena), encogida hacia 50 según la confianza de los datos.
 */
data class FinanceCircle(val key: String, val title: String, val question: String, val factors: List<CircleFactor>, val confidence: Double) {
    /** Puntuación sin corregir por confianza. */
    val raw: Double get() {
        val known = factors.filter { it.score != null }
        if (known.isEmpty()) return 50.0
        val w = known.sumOf { it.weight }
        return exp(known.sumOf { it.weight / w * ln(it.score!!.coerceIn(1.0, 100.0)) })
    }
    /** Con pocos datos la nota se acerca a 50: no se puede sacar 100 con una semana de historial. */
    val score get() = 50 + (raw - 50) * confidence
    val provisional get() = confidence < 0.75
    /** La acción más útil: la variable que más puntos le quita al círculo (por su peso). */
    val nextStep get() = factors.filter { it.score != null }.maxByOrNull { (100 - it.score!!) * it.weight }?.advice ?: factors.first().advice
}

/**
 * Los tres círculos de tu salud financiera, con curvas continuas que se acercan a 100 sin tocarlo:
 * - satura(x, τ) = 100·(1 − e^(−x/τ)): rendimientos decrecientes (cada mes de colchón suma menos que el anterior).
 * - decae(x, k) = 100·e^(−k·x): para lo que es malo cuanto más hay (volatilidad, deudas, desvíos).
 * - logística(x, x0, k) = 100 / (1 + e^(−k(x−x0))): para ratios con un punto de equilibrio claro.
 */
object FinancialCircles {
    fun saturate(x: Double, tau: Double) = 100 * (1 - exp(-max(0.0, x) / tau))
    fun decay(x: Double, k: Double) = 100 * exp(-k * max(0.0, x))
    fun logistic(x: Double, x0: Double, k: Double) = 100 / (1 + exp(-k * (x - x0)))

    private fun cv(values: List<Double>): Double? {
        if (values.size < 3) return null
        val m = values.average()
        if (m <= 0) return null
        return sqrt(values.sumOf { (it - m) * (it - m) } / values.size) / m
    }

    fun of(d: FinanceData, plan: BankPlan?, forecast: Forecast?, goals: List<GoalStatus>, margin: Double, fmt: (Double) -> String): List<FinanceCircle> {
        val today = d.today
        val closed3 = d.closedMonths(3)
        val closed6 = d.closedMonths(6)
        val bills = d.bills.sumOf { Bills.monthly(it) }
        // Gasto esencial mensual: fijos + lo necesario variable (súper, transporte, salud…) de los meses cerrados.
        val needsVar = if (closed3.isEmpty()) null else closed3.sumOf { m -> Budgeting.variable(d, m).filter { it.spendTag().need }.sumOf { it.amount } } / closed3.size
        val essentials = bills + (needsVar ?: 0.0)
        val income = d.monthlyIncome
        val monthsOfData = d.firstMonth?.let { ((today.year - it.year) * 12 + today.monthValue - it.monthValue).coerceAtLeast(0) } ?: 0
        val dataConfidence = min(1.0, monthsOfData / 6.0)

        // ================= ESTABILIDAD =================
        val liquid = d.wealth.savings + max(0.0, d.wealth.available - essentials)
        val months = if (essentials > 0) liquid / essentials else null
        val e1 = CircleFactor(
            "Colchón de emergencia", months?.let { saturate(it, 2.5) }, 0.35,
            months?.let { "Tu ahorro líquido (${fmt(liquid)}) cubre ${f1(it)} meses de gastos esenciales (${fmt(essentials)}/mes). 3 meses ≈ 70; 6 ≈ 91; 12 ≈ 99." } ?: "Apunta tus gastos fijos para calcularlo.",
            "Aparta cada mes en la cuenta remunerada hasta cubrir 6 meses de gastos esenciales.", "100·(1 − e^(−meses/2,5))",
        )
        // Próximos 30 días: lo que tienes y entra frente a lo que sale (fijos + gasto esencial previsto).
        val in30 = forecast?.events?.filter { it.isIncome && !it.date.isAfter(today.plusDays(30)) }?.sumOf { it.amount } ?: 0.0
        val out30 = (forecast?.events?.filter { !it.isIncome && !it.date.isAfter(today.plusDays(30)) }?.sumOf { -it.amount } ?: bills) + (needsVar ?: 0.0)
        val coverage = if (out30 > 0) (d.wealth.available + in30) / out30 else null
        val e2 = CircleFactor(
            "Cobertura de pagos (30 días)", coverage?.let { logistic(it, 1.15, 4.0) }, 0.25,
            coverage?.let { "Disponible + lo que va a entrar (${fmt(d.wealth.available + in30)}) frente a lo que va a salir (${fmt(out30)}): ${f1(it)}×. 1× ≈ 35; 1,5× ≈ 80; 2× ≈ 97." } ?: "Sin pagos previstos.",
            "Ten en cuenta los recibos del mes antes de gastar: deja en la cuenta al menos 1,5 veces lo que va a salir.", "100 / (1 + e^(−4·(cobertura − 1,15)))",
        )
        val incomes6 = closed6.map { d.receivedIn(it) }.filter { it > 0 }
        val incomeCv = cv(incomes6)
        val e3 = CircleFactor(
            "Estabilidad de ingresos", incomeCv?.let { decay(it, 3.5) }, 0.15,
            incomeCv?.let { "Lo que cobras varía un ${pc(it)} entre meses (coeficiente de variación de ${incomes6.size} meses). 5 % ≈ 84; 20 % ≈ 50." } ?: "Hacen falta 3 meses cerrados con cobros.",
            "Si tus ingresos varían, presupuesta con el mes más flojo y trata lo demás como extra.", "100·e^(−3,5·CV)",
        )
        val debtRatio = if (income > 0) d.wealth.debts / (income * 12) else null
        val e4 = CircleFactor(
            "Carga de deuda", if (d.wealth.debts <= 0) 100.0 else debtRatio?.let { decay(it, 2.0) }, 0.15,
            if (d.wealth.debts <= 0) "Sin deudas apuntadas." else "Debes ${fmt(d.wealth.debts)}: ${debtRatio?.let { pc(it) } ?: "?"} de tus ingresos de un año.",
            "Paga primero la deuda con más interés.", "100·e^(−2·deuda/ingresos anuales)",
        )
        val rigidity = if (income > 0) bills / income else null
        val e5 = CircleFactor(
            "Rigidez de gastos fijos", rigidity?.let { 100 - logistic(it, 0.5, 10.0) }, 0.10,
            rigidity?.let { "Tus fijos (${fmt(bills)}/mes) son el ${pc(it)} de lo que ganas. 30 % ≈ 88; 50 % ≈ 50; 60 % ≈ 27." } ?: "Apunta tus ingresos.",
            "Revisa tarifas y suscripciones: cada euro fijo que bajas te da margen todos los meses.", "100 − 100/(1 + e^(−10·(fijos/ingresos − 0,5)))",
        )

        // ================= CONTROL =================
        val spare = if (income > 0) (income - essentials) / income else null
        val c1 = CircleFactor(
            "Margen sostenible", spare?.let { if (it <= 0) 0.0 else saturate(it, 0.15) }, 0.30,
            spare?.let { "Tras fijos y necesidades te queda el ${pc(it)} de lo que ganas (${fmt(income - essentials)}/mes). 10 % ≈ 49; 20 % ≈ 74; 30 % ≈ 86." } ?: "Apunta tus ingresos.",
            "Gana margen recortando lo necesario más caro (súper, transporte) o subiendo ingresos.", "100·(1 − e^(−margen/0,15))",
        )
        // Presupuesto: cuánto te pasaste (en %) cada mes cerrado y en lo que va de este, con más peso lo reciente.
        val spendableShare = d.categories.filter { !it.isSavings }.sumOf { it.percent }.takeIf { it > 0 }?.div(100) ?: 0.8
        val overs = closed3.mapIndexedNotNull { i, m ->
            val received = d.receivedIn(m).takeIf { it > 0 } ?: return@mapIndexedNotNull null
            val limit = received * spendableShare
            val spent = d.spentIn(m)
            (max(0.0, spent / limit - 1)) to (3.0 - i)
        }
        val pressureNow = (100 - margin) / 100 * 1.2 // margen 100 → 0; 50 → 0,6; 0 → 1,2 (escala del día)
        val overAvg = if (overs.isEmpty()) null else overs.sumOf { it.first * it.second } / overs.sumOf { it.second }
        val c2 = CircleFactor(
            "Cumplimiento del presupuesto", overAvg?.let { decay(it, 3.0) * (0.7 + 0.3 * decay(max(0.0, pressureNow - 0.6), 2.0) / 100) }
                ?: decay(max(0.0, pressureNow - 0.6), 2.0), 0.25,
            (overAvg?.let { "En los últimos ${overs.size} meses te pasaste de media un ${pc(it)} de lo que podías gastar. " } ?: "") +
                "Este mes vas al ${margin.toInt()} % de margen.",
            "Usa la cifra de \"Puedes gastar\" como tope diario el resto del mes.", "100·e^(−3·exceso medio) · ajuste por el ritmo de este mes",
        )
        val low = forecast?.points?.take(31)?.minOfOrNull { it.second }
        val lowRel = if (low != null && essentials > 0) low / essentials else null
        val c3 = CircleFactor(
            "Previsibilidad del saldo", lowRel?.let { if (it <= 0) 0.0 else saturate(it, 0.25) }, 0.20,
            low?.let { "Tu saldo más bajo previsto en 30 días es ${fmt(it)}" + (lowRel?.let { r -> " (${f1(r)} meses de gastos esenciales). 0,25 ≈ 63; 0,5 ≈ 86." } ?: ".") } ?: "Sin previsión.",
            "Si el saldo previsto baja mucho antes de cobrar, reserva antes los pagos grandes.", "100·(1 − e^(−(saldo mínimo/gastos esenciales)/0,25))",
        )
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val variable = d.spending.filter { !Budgeting.isBillPayment(it, d.bills) }
        val weeks = (1..8).map { w ->
            val s = monday.minusWeeks(w.toLong()).toEpochDay()
            variable.filter { it.epochDay in s..(s + 6) }.sumOf { it.amount }
        }
        val weeksWithData = variable.minOfOrNull { it.epochDay }?.let { first -> weeks.indices.count { monday.minusWeeks(it + 1L).toEpochDay() >= first } } ?: 0
        val weeklyCv = if (weeksWithData >= 4) cv(weeks.take(weeksWithData)) else null
        val c4 = CircleFactor(
            "Regularidad del gasto", weeklyCv?.let { decay(it, 1.5) }, 0.15,
            weeklyCv?.let { "Tu gasto semanal varía un ${pc(it)} (${weeksWithData} semanas). 30 % ≈ 64; 60 % ≈ 41." } ?: "Hacen falta 4 semanas de gastos.",
            "Reparte los gastos grandes: ir semana a semana evita sustos a fin de mes.", "100·e^(−1,5·CV semanal)",
        )
        val last90 = variable.filter { it.epochDay > today.minusDays(90).toEpochDay() }
        val wantsShare = last90.sumOf { it.amount }.takeIf { it > 0 }?.let { tot -> last90.filter { !it.spendTag().need }.sumOf { it.amount } / tot }
        val wantsTarget = d.categories.filter { !it.isSavings && !Classifier.normalize(it.name).contains("necesidad") }.sumOf { it.percent } /
            d.categories.filter { !it.isSavings }.sumOf { it.percent }.coerceAtLeast(1.0)
        val c5 = CircleFactor(
            "Caprichos frente a tu plan", wantsShare?.let { decay(it - wantsTarget, 3.0) }, 0.10,
            wantsShare?.let { "El ${pc(it)} de tu gasto variable es prescindible; tu plan permite ${pc(wantsTarget)}." } ?: "Sin gastos en 90 días.",
            "Pon un límite semanal a tus caprichos principales (restaurantes, compras).", "100·e^(−3·exceso sobre tu plan)",
        )

        // ================= PROGRESO =================
        val inc3 = closed3.sumOf { d.receivedIn(it) }
        val rate = if (inc3 > 0) (inc3 - closed3.sumOf { d.spentIn(it) }) / inc3 else null
        val p1 = CircleFactor(
            "Tasa de ahorro", rate?.let { if (it <= 0) 0.0 else saturate(it, 0.12) }, 0.35,
            rate?.let { "Ahorras el ${pc(it)} de lo que cobras (${closed3.size} meses). 10 % ≈ 56; 20 % ≈ 81; 30 % ≈ 92." } ?: "Hace falta un mes cerrado con cobros.",
            "Automatiza el ahorro el día de cobro: lo que no ves, no lo gastas.", "100·(1 − e^(−tasa/0,12))",
        )
        val nets = closed6.filter { d.receivedIn(it) > 0 }.map { d.receivedIn(it) - d.spentIn(it) }
        val p2 = CircleFactor(
            "Constancia", if (nets.size >= 2) 100 * (nets.count { it > 0 }.toDouble() / nets.size).pow(1.5) else null, 0.20,
            if (nets.size >= 2) "${nets.count { it > 0 }} de ${nets.size} meses cerraste ahorrando." else "Hacen falta 2 meses cerrados.",
            "Aunque sea poco, cierra cada mes en positivo: la constancia pesa más que un mes bueno.", "100·(meses en positivo/meses)^1,5",
        )
        val avgNet = if (nets.isNotEmpty()) nets.average() else null
        val buildYears = if (avgNet != null && essentials > 0) 12 * avgNet / essentials else null
        val p3 = CircleFactor(
            "Patrimonio construido por ti", buildYears?.let { if (it <= 0) 0.0 else saturate(it, 2.0) }, 0.25,
            buildYears?.let { "A tu ritmo sumas ${f1(it)} meses de gastos esenciales al año a tu patrimonio, sin contar subidas de bolsa. 2 ≈ 63; 4 ≈ 86." } ?: "Hacen falta meses cerrados.",
            "Cada mes que ahorras compra tiempo: apunta a sumar 4 meses de gastos al año.", "100·(1 − e^(−meses de gastos ahorrados al año/2))",
        )
        val g = goals.firstOrNull { !it.done } ?: goals.firstOrNull()
        val goalRatio = g?.let { if (it.done) 1.0 else (it.projected / it.goal.target) }
        val p4 = CircleFactor(
            "Camino a tus objetivos", goalRatio?.let { 100 * min(1.0, max(0.0, it)).pow(1.5) * (if (it >= 1) 0.97 else 1.0) }, 0.20,
            g?.let { "\"${it.goal.name}\": a tu ritmo tendrías el ${pc(min(1.5, goalRatio ?: 0.0))} en la fecha." } ?: "No tienes objetivos con importe y fecha.",
            "Define un objetivo con importe y fecha y aparta cada mes lo que te dice la app.", "100·(previsto en la fecha/objetivo)^1,5",
        )

        fun conf(factors: List<CircleFactor>) = dataConfidence * (factors.filter { it.score != null }.sumOf { it.weight } / factors.sumOf { it.weight })
        val est = listOf(e1, e2, e3, e4, e5)
        val con = listOf(c1, c2, c3, c4, c5)
        val pro = listOf(p1, p2, p3, p4)
        return listOf(
            FinanceCircle("stability", "Estabilidad", "¿Qué pasaría si mañana tuvieses un imprevisto?", est, conf(est)),
            FinanceCircle("control", "Control", "¿Puedes mantener tu estilo de vida sin comprometer tus pagos?", con, conf(con)),
            FinanceCircle("progress", "Progreso", "¿Estás avanzando hacia la vida financiera que quieres?", pro, conf(pro)),
        )
    }

    /** "Ha subido 6 puntos desde la semana pasada": compara con la puntuación guardada hace ≥7 días. */
    fun changeText(now: Double, weekAgo: Double?): String = when {
        weekAgo == null -> "Aún no hay datos de la semana pasada: en unos días verás cómo ha cambiado."
        now - weekAgo >= 3 -> "Ha subido ${(now - weekAgo).toInt()} puntos desde la semana pasada."
        weekAgo - now >= 3 -> "Ha bajado ${(weekAgo - now).toInt()} puntos desde la semana pasada."
        else -> "Igual que la semana pasada."
    }

    /** Historial "díaEpoch:est,con,pro;…" (como mucho 40 días). */
    fun encodeHistory(map: Map<Long, List<Double>>) = map.entries.sortedBy { it.key }.takeLast(40).joinToString(";") { "${it.key}:${it.value.joinToString(",") { v -> v.toInt().toString() }}" }
    fun decodeHistory(raw: String?): Map<Long, List<Double>> = raw.orEmpty().split(';').mapNotNull { e ->
        val (k, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        k.toLongOrNull()?.let { it to v.split(',').mapNotNull { x -> x.toDoubleOrNull() } }
    }.toMap()

    fun weekAgo(history: Map<Long, List<Double>>, today: LocalDate, index: Int): Double? =
        history.filterKeys { it <= today.toEpochDay() - 7 }.maxByOrNull { it.key }?.value?.getOrNull(index)
}
