package com.caudal.finanzas.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

/**
 * Este mes frente a tu mes típico: lo que llevas gastado día a día (acumulado) y la línea gris de lo que sueles
 * llevar a cada día del mes (media de tus meses anteriores). Sin alquiler ni fijos.
 */
data class TypicalMonth(
    val month: YearMonth,
    /** Gastado acumulado de este mes, día 1..hoy. */
    val now: List<Double>,
    /** Lo típico acumulado, día 1..fin de mes. */
    val typical: List<Double>,
    val monthsUsed: Int,
) {
    val today get() = now.size
    val spent get() = now.lastOrNull() ?: 0.0
    val typicalToday get() = typical.getOrElse(today - 1) { 0.0 }
    val typicalTotal get() = typical.lastOrNull() ?: 0.0
    val diff get() = spent - typicalToday
}

object TypicalSpend {
    fun of(d: FinanceData, maxMonths: Int = 6): TypicalMonth? {
        val m = YearMonth.from(d.today)
        val variable = d.spending.filter { !Budgeting.isFixed(it, d.bills) }
        val first = d.expenses.minOfOrNull { it.epochDay }?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: return null
        val past = (1..maxMonths).map { m.minusMonths(it.toLong()) }.filter { !it.isBefore(first) }
        if (past.isEmpty()) return null
        fun cumulative(ym: YearMonth, days: Int): List<Double> {
            val byDay = variable.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == ym }
                .groupBy { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }.mapValues { (_, l) -> l.sumOf { it.amount } }
            var acc = 0.0
            return (1..days).map { day -> acc += byDay[day] ?: 0.0; acc }
        }
        val len = m.lengthOfMonth()
        // Cada mes pasado se lleva a la longitud de este (por proporción del día del mes).
        val curves = past.map { ym ->
            val c = cumulative(ym, ym.lengthOfMonth())
            (1..len).map { day -> c[(day * ym.lengthOfMonth() / len - 1).coerceIn(0, c.size - 1)] }
        }
        val typical = (0 until len).map { i -> curves.sumOf { it[i] } / curves.size }
        return TypicalMonth(m, cumulative(m, d.today.dayOfMonth), typical, past.size)
    }
}

/** Una acción para una suscripción: revisar una subida, pasarla a pago anual o ver si sobra alguna parecida. */
data class SubAction(val key: String, val name: String, val text: String, val saving: Double)

/** Revisión de suscripciones: las que tienes, qué conviene revisar y cuánto podrías ahorrar. Sin IA. */
data class SubscriptionCheck(val subs: List<Recurring>, val actions: List<SubAction>, val monthlyTotal: Double) {
    val maxSaving get() = actions.sumOf { it.saving }
}

object SubscriptionChecker {
    private val VIDEO = listOf("netflix", "hbo", "max", "disney", "prime video", "amazon prime", "filmin", "skyshowtime", "movistar plus", "dazn", "apple tv", "crunchyroll")
    private val MUSIC = listOf("spotify", "apple music", "youtube music", "tidal", "deezer", "amazon music")

    fun of(d: FinanceData): SubscriptionCheck? {
        val subs = RecurringDetector.find(d).filter { it.kind == "Suscripción" }
        if (subs.isEmpty()) return null
        val es = java.util.Locale.forLanguageTag("es-ES")
        fun e(v: Double) = String.format(es, "%.2f €", v)
        val actions = mutableListOf<SubAction>()
        subs.forEach { s ->
            if (s.change > 0.4) actions += SubAction("up-${s.name}", s.name, "Revisa la subida de ${s.name} (+${e(s.change)} al mes)", s.change)
            if (s.monthly >= 8) actions += SubAction("annual-${s.name}", s.name, "Mira si ${s.name} tiene pago anual: suele salir un 15 % más barato", s.monthly * 0.15)
        }
        fun group(words: List<String>, label: String) {
            val g = subs.filter { s -> val n = Classifier.normalize(s.name); words.any { it in n } }
            if (g.size >= 2) {
                val cheapest = g.minByOrNull { it.monthly }!!
                actions += SubAction("dup-$label", cheapest.name, "Tienes ${g.size} de $label (${g.joinToString(", ") { it.name }}): ¿usas todas?", cheapest.monthly)
            }
        }
        group(VIDEO, "vídeo"); group(MUSIC, "música")
        return SubscriptionCheck(subs, actions.distinctBy { it.key }, subs.sumOf { it.monthly })
    }

    /** Salud del ahorro (0–100): lo que te queda de cada euro que entra, sobre un 30 % como ideal. */
    fun savingsHealth(d: FinanceData): Int {
        val m = YearMonth.from(d.today).minusMonths(1)
        val inc = d.receivedIn(m)
        if (inc <= 0) return 0
        val rate = (inc - d.spentIn(m)) / inc
        return (max(0.0, rate) / 0.30 * 100).coerceAtMost(100.0).toInt()
    }
}
