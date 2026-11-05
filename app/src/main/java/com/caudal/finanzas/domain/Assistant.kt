package com.caudal.finanzas.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Respuesta del asistente: el texto, de dónde sale cada dato y, si escribiste gastos, cuáles. */
data class Answer(
    val text: String,
    val sources: List<String> = emptyList(),
    val expenses: List<ParsedExpense> = emptyList(),
)

/**
 * Chat con tus finanzas. Funciona en el móvil, sin enviar nada a internet: entiende las preguntas
 * más habituales y responde con tus movimientos, tus gastos fijos y lo que viene.
 */
object Assistant {
    private val ES = Locale.forLanguageTag("es-ES")
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)

    val SUGGESTIONS = listOf(
        "¿Puedo gastarme 250 € este fin de semana?",
        "¿Por qué he ahorrado menos este mes?",
        "¿Qué tres cambios me permitirían ahorrar 100 € más?",
        "¿Cuánto gasto en restaurantes?",
        "¿Cuándo cobro?",
        "He pagado 18 € en una cena y 12 € en gasolina",
    )

    private fun amountIn(q: String): Double? =
        Regex("(\\d+(?:[.,]\\d{1,2})?)").find(q.replace(".", ""))?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    fun answer(question: String, d: FinanceData, safe: SafeToSpend, events: List<FlowEvent>, fmt: (Double) -> String): Answer {
        val q = Classifier.normalize(question)
        fun has(vararg w: String) = w.any { q.contains(it) }

        // Gastos escritos como frase: "He pagado 18 € en…".
        if (Regex("^(hoy |ayer )?(me )?(he )?(pagado|gastado|gaste|pague|apunta|anade|compre)").containsMatchIn(q)) {
            val parsed = NaturalEntry.parse(question, d.expenses)
            if (parsed.isNotEmpty()) {
                return Answer(
                    "Lo apunto así:\n" + parsed.joinToString("\n") { "• ${it.note}: ${fmt(it.amount)} · ${it.tag.label}" + if (it.daysAgo == 1) " · ayer" else "" },
                    listOf("Tipo de gasto deducido de la descripción y de cómo clasificaste gastos parecidos."),
                    parsed,
                )
            }
        }

        return when {
            has("objetivo", "meta") -> Answer("Tu objetivo lo ves en Inicio: te digo cuánto apartar al mes, si vas a tiempo y qué cambiar para llegar antes.")
            has("puedo gastar", "me puedo permitir", "puedo permitirme", "puedo comprar", "me lo puedo", "puedo pagar") -> canSpend(amountIn(question), q, d, safe, fmt)
            has("ahorrado menos", "ahorro menos", "ahorre menos", "por que he ahorrado", "por que tengo menos", "por que he gastado") -> whySavedLess(d, fmt)
            has("ahorrar") && (amountIn(question) != null || has("cambios", "recortar", "como puedo")) -> howToSave(amountIn(question) ?: 100.0, d, fmt)
            has("suscripcion") -> subscriptions(d, fmt)
            has("cuando cobro", "proximo cobro", "cuando me pagan", "nomina", "cuando entra") -> nextIncome(events, fmt)
            has("salud", "como voy", "como estoy", "estoy bien", "voy bien") -> healthSummary(d, fmt)
            has("cuanto tengo", "patrimonio", "dinero tengo", "cuanto dinero") -> wealth(d, fmt)
            has("gasto en", "gastado en", "gasto de", "gastando en", "gasto al mes en") -> spendOn(q, d, fmt)
            has("cuanto he gastado", "cuanto llevo", "gastado este mes", "cuanto gasto") -> spentThisMonth(d, fmt)
            else -> Answer(
                "Puedo ayudarte con preguntas como estas:\n" + SUGGESTIONS.joinToString("\n") { "• $it" } +
                    "\n\nTambién puedes escribir tus gastos tal cual y los apunto.",
            )
        }
    }

    private fun canSpend(amount: Double?, q: String, d: FinanceData, s: SafeToSpend, fmt: (Double) -> String): Answer {
        val sources = mutableListOf(
            "Disponible en tu cuenta principal: ${fmt(s.available)}",
            "Gastos fijos que faltan: −${fmt(s.pendingBills)}",
            "Reservado para ahorrar este mes: −${fmt(s.savingsReserve)}",
        )
        if (s.plans > 0) sources += "Apartado para tus planes: −${fmt(s.plans)}"
        sources += s.nextPayday?.let { "Tu próximo cobro: ${it.format(dayFmt)} (faltan ${s.daysToPayday} días)" } ?: "Cuento hasta final de mes (quedan ${s.daysToPayday} días)"
        if (amount == null) return Answer("Ahora mismo puedes gastar ${fmt(max(0.0, s.safe))} sin tocar lo comprometido, unos ${fmt(s.perDay)} al día. Dime una cantidad y te digo si te lo puedes permitir.", sources)

        val weekend = if (q.contains("fin de semana")) Insights.habits(d, fmt)?.let { 2 * it.weekendAvg } else null
        if (weekend != null && weekend > 0) sources += "Un fin de semana normal gastas unos ${fmt(weekend)}"
        val left = s.safe - amount
        val text = when {
            s.safe <= 0 -> "Mejor no: todo lo que tienes disponible ya está comprometido hasta tu próximo cobro." +
                if (d.wealth.savings >= amount) " Podrías hacerlo tirando de tu ahorro (${fmt(d.wealth.savings)}), pero estarías gastando lo que habías guardado." else ""
            left >= 0 -> "Sí. Después de gastar ${fmt(amount)} te quedarían ${fmt(left)} para gastar" +
                (if (s.daysToPayday > 1) ", unos ${fmt(left / s.daysToPayday)} al día durante ${s.daysToPayday} días." else ".") +
                if (left / max(1, s.daysToPayday) < 10) " Vas a ir justo: ojo con los imprevistos." else ""
            d.wealth.savings >= -left -> "Te pasarías ${fmt(-left)} de lo que puedes gastar. Solo podrías tirando de tu ahorro; si es algo que quieres de verdad, plantéate apartarlo poco a poco."
            else -> "No: te pasarías ${fmt(-left)} y no tienes margen ni en el ahorro. Mejor espera a cobrar."
        }
        return Answer(text, sources)
    }

    private fun whySavedLess(d: FinanceData, fmt: (Double) -> String): Answer {
        val s = Insights.monthStory(d)
        val savedNow = s.incomeNow - s.spentNow
        val savedPrev = s.incomePrev - s.spentPrev
        val diff = savedNow - savedPrev
        val reasons = mutableListOf<String>()
        if (s.incomePrev - s.incomeNow > 10) reasons += "has cobrado ${fmt(s.incomePrev - s.incomeNow)} menos"
        s.changes.filter { it.second > 5 }.take(3).forEach { reasons += "has gastado ${fmt(it.second)} más en ${it.first.label.lowercase()}" }
        val text = when {
            diff >= 0 -> "En realidad ${s.label} has ahorrado ${fmt(diff)} más que en el mismo tramo del mes anterior (${fmt(savedNow)} frente a ${fmt(savedPrev)}). ¡Bien!"
            reasons.isEmpty() -> "${s.label.replaceFirstChar { it.titlecase(ES) }} has ahorrado ${fmt(-diff)} menos, repartido en muchos gastos pequeños."
            else -> "${s.label.replaceFirstChar { it.titlecase(ES) }} has ahorrado ${fmt(-diff)} menos porque " + reasons.joinToString(", ").replaceLast(", ", " y ") + "."
        }
        return Answer(
            text,
            listOf(
                "Cobrado: ${fmt(s.incomeNow)} ahora frente a ${fmt(s.incomePrev)} antes",
                "Gastado: ${fmt(s.spentNow)} ahora frente a ${fmt(s.spentPrev)} antes",
                "Comparo el mismo número de días de cada mes",
            ),
        )
    }

    private fun String.replaceLast(old: String, new: String): String {
        val i = lastIndexOf(old)
        return if (i < 0) this else substring(0, i) + new + substring(i + old.length)
    }

    private fun monthlyByTag(d: FinanceData): Pair<List<Pair<SpendTag, Double>>, String> {
        val closed = d.closedMonths(3)
        if (closed.isEmpty()) return byTag(d.spendingIn(d.thisMonth)) to "lo que llevas este mes"
        val totals = byTag(closed.flatMap { d.spendingIn(it) }).map { it.first to it.second / closed.size }
        return totals to "tu media de ${closed.size} ${if (closed.size == 1) "mes" else "meses"}"
    }

    private fun howToSave(target: Double, d: FinanceData, fmt: (Double) -> String): Answer {
        val (tags, basis) = monthlyByTag(d)
        val changes = mutableListOf<Pair<String, Double>>()
        var left = target
        // 1) Suscripciones (gastos fijos): quitar una es ahorro seguro todos los meses.
        val subs = d.bills.filter { !it.yearly && Classifier.guess(it.name) == SpendTag.SUBSCRIPTIONS }.sortedByDescending { it.amount }
        subs.firstOrNull()?.let { b -> changes += "Cancela o comparte ${b.name}" to b.amount; left -= b.amount }
        // 2) Recortar un 25 % lo prescindible que más pesa.
        for ((tag, avg) in tags.filter { !it.first.need && it.first != SpendTag.OTHER && it.first != SpendTag.SUBSCRIPTIONS }) {
            if (changes.size >= 3 || left <= 0) break
            val cut = min(left, ceil(avg * 0.25 / 5) * 5)
            if (cut >= 5) { changes += "Gasta un ${(cut / avg * 100).toInt()} % menos en ${tag.label.lowercase()} (de ${fmt(avg)} a ${fmt(avg - cut)})" to cut; left -= cut }
        }
        // 3) Si aún falta, lo necesario más flexible: supermercado y transporte.
        for ((tag, avg) in tags.filter { it.first == SpendTag.SUPER || it.first == SpendTag.TRANSPORT }) {
            if (changes.size >= 3 || left <= 0) break
            val cut = min(left, ceil(avg * 0.1 / 5) * 5)
            if (cut >= 5) { changes += "Ajusta ${tag.label.lowercase()} un 10 % (marca blanca, lista de la compra, compartir coche)" to cut; left -= cut }
        }
        if (changes.isEmpty()) return Answer("Aún no tengo suficientes gastos tuyos para proponerte cambios. Apunta unas semanas de gastos (o activa los pagos con tarjeta) y vuelve a preguntarme.")
        val total = changes.sumOf { it.second }
        return Answer(
            changes.mapIndexed { i, (t, v) -> "${i + 1}. $t: +${fmt(v)} al mes" }.joinToString("\n") +
                "\n\n" + if (total >= target) "Con eso ahorrarías ${fmt(total)} más al mes (${fmt(total * 12)} al año)." else "Con eso llegarías a ${fmt(total)} al mes; para el resto haría falta tocar gastos fijos.",
            listOf("Basado en $basis por tipo de gasto", "Suscripciones sacadas de tus gastos fijos"),
        )
    }

    private fun subscriptions(d: FinanceData, fmt: (Double) -> String): Answer {
        val subs = d.bills.filter { Classifier.guess(it.name) == SpendTag.SUBSCRIPTIONS }
        val seen = d.spending.filter { it.billId == null && it.spendTag() == SpendTag.SUBSCRIPTIONS && it.epochDay >= d.today.toEpochDay() - 40 }
            .distinctBy { Classifier.normalize(it.note) }.filter { e -> subs.none { Classifier.normalize(it.name) == Classifier.normalize(e.note) } }
        if (subs.isEmpty() && seen.isEmpty()) return Answer("No veo suscripciones entre tus gastos fijos ni en tus pagos recientes.")
        val monthly = subs.sumOf { Bills.monthly(it) } + seen.sumOf { it.amount }
        return Answer(
            (subs.map { "• ${it.name}: ${fmt(it.amount)}${if (it.yearly) " al año" else " al mes"}" } + seen.map { "• ${it.note}: ${fmt(it.amount)} (visto en tus pagos)" }).joinToString("\n") +
                "\n\nEn total ${fmt(monthly)} al mes, ${fmt(monthly * 12)} al año.",
            listOf("Gastos fijos y pagos de los últimos 40 días clasificados como suscripción"),
        )
    }

    private fun nextIncome(events: List<FlowEvent>, fmt: (Double) -> String): Answer {
        val next = events.filter { it.isIncome }.take(3)
        if (next.isEmpty()) return Answer("No tengo cobros previstos. Añade tus ingresos (nómina, autónomo…) en la pestaña Gastos.")
        return Answer(
            next.joinToString("\n") { "• ${it.label}: ${fmt(it.amount)} · ${it.date.format(dayFmt)}" + if (it.overdue) " (debería haber llegado ya)" else "" },
            listOf("Según tus fuentes de ingreso; lo variable, con la media de tus últimos 3 meses"),
        )
    }

    private fun healthSummary(d: FinanceData, fmt: (Double) -> String): Answer {
        val h = Insights.health(d, fmt)
        return Answer(
            "Tienes ${h.good} de ${h.indicators.size} indicadores en buen estado.\n" +
                h.indicators.joinToString("\n") { "• ${it.name}: ${it.value}" } + "\n\nLo más útil ahora: ${h.nextStep}",
            listOf("Informe de salud financiera (pestaña Gastos → Análisis)"),
        )
    }

    private fun wealth(d: FinanceData, fmt: (Double) -> String): Answer {
        val w = d.wealth
        return Answer(
            "Tu patrimonio neto es ${fmt(w.total)}.",
            listOfNotNull(
                "Disponible: ${fmt(w.available)}", "Ahorro: ${fmt(w.savings)}", "Invertido: ${fmt(w.invested)}",
                if (w.debts > 0) "Deudas: −${fmt(w.debts)}" else null,
            ),
        )
    }

    private fun spendOn(q: String, d: FinanceData, fmt: (Double) -> String): Answer {
        val rest = q.substringAfter(" en ", q.substringAfter(" de ", q))
        val tag = SpendTag.entries.firstOrNull { Classifier.normalize(it.label).split(" ").any { w -> w.length > 3 && rest.contains(w) } } ?: Classifier.guess(rest)
        if (tag == null || tag == SpendTag.OTHER) return spentThisMonth(d, fmt)
        val now = d.spendingIn(d.thisMonth).filter { it.spendTag() == tag }.sumOf { it.amount }
        val closed = d.closedMonths(3)
        val avg = if (closed.isEmpty()) null else closed.sumOf { m -> d.spendingIn(m).filter { it.spendTag() == tag }.sumOf { it.amount } } / closed.size
        return Answer(
            "En ${tag.label.lowercase()} llevas ${fmt(now)} este mes" + (avg?.let { ". Tu media es de ${fmt(it)} al mes (${fmt(it * 12)} al año)." } ?: "."),
            listOf("Gastos clasificados como \"${tag.label}\"") + (if (avg != null) listOf("Media de los últimos ${closed.size} meses cerrados") else emptyList()),
        )
    }

    private fun spentThisMonth(d: FinanceData, fmt: (Double) -> String): Answer {
        val list = d.spendingIn(d.thisMonth)
        val tags = byTag(list).take(3)
        return Answer(
            "Este mes llevas ${fmt(list.sumOf { it.amount })} gastados" +
                if (tags.isNotEmpty()) ", sobre todo en " + tags.joinToString(", ") { "${it.first.label.lowercase()} (${fmt(it.second)})" } + "." else ".",
            listOf("${list.size} pagos este mes, sin contar lo que apartas para ahorrar"),
        )
    }
}
