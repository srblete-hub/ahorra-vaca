package com.caudal.finanzas.domain

import com.caudal.finanzas.payments.BizumParser
import com.caudal.finanzas.payments.PaymentParser
import java.time.LocalDate
import java.util.Locale

/** Lo que se ha sacado de un texto compartido (o del texto de la foto de un ticket). */
data class SharedEntry(
    val amount: Double?,
    val note: String,
    val day: LocalDate,
    val income: Boolean = false,
    /** De dónde viene, para el aviso: "ticket", "texto". */
    val from: String = "texto",
)

/**
 * Lee lo que compartes con la app (un aviso del banco, un gasto de Tricount, el texto de un ticket)
 * y saca importe, comercio y fecha. Solo reglas: sin IA.
 */
object SharedReader {
    private const val NUM = "(\\d{1,3}(?:[.\\s\\u00A0]\\d{3})+(?:[.,]\\d{2})|\\d+[.,]\\d{2}|\\d+)"
    private val WITH_CUR = Regex("(?:$NUM\\s?(?:€|eur\\b|euros?\\b))|(?:€\\s?$NUM)", RegexOption.IGNORE_CASE)
    private val DECIMAL = Regex("(?<![\\d.,])(\\d{1,3}(?:[.\\s]\\d{3})*[.,]\\d{2})(?![\\d.,])")
    private val TOTAL_LINE = Regex("\\b(total|importe|a pagar|pagado|tu parte|cargo de|pago de|has pagado|has gastado)\\b", RegexOption.IGNORE_CASE)
    private val NOT_TOTAL = Regex("\\b(subtotal|sub-total|base imponible|iva|cuota|entregado|cambio|efectivo entregado|descuento|ahorro)\\b", RegexOption.IGNORE_CASE)
    private val DATE = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b")
    private val MERCHANT_EN = Regex("\\b(?:en|at)\\s+([A-Za-zÁÉÍÓÚÑáéíóúñ][^\\n,.;:·]{1,38}?)(?=\\s+(?:por|con|de|el|on|for)\\b|[\\n,.;:·]|\\s+\\d|$)", RegexOption.IGNORE_CASE)
    private val INCOME = listOf("has recibido", "te ha enviado", "te han enviado", "ingreso", "abono", "te devuelve", "devolución", "devolucion", "reembolso")
    private val NOISE = listOf(
        "ticket", "factura", "simplificada", "fecha", "hora", "nif", "cif", "tel", "telf", "teléfono", "http", "www", "@", "gracias",
        "total", "importe", "iva", "caja", "cajero", "le atendió", "c/", "calle", "avda", "avenida", "s.l", "tarjeta", "visa", "mastercard",
        "operación", "operacion", "pedido", "nº", "num", "hola", "compartido", "tricount",
    )

    fun read(text: String, today: LocalDate = LocalDate.now(), from: String = "texto"): SharedEntry? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val lower = t.lowercase(Locale.ROOT)

        // Un Bizum compartido desde el banco: ya sabemos leerlo.
        BizumParser.parse(null, t)?.let { b ->
            val who = b.person ?: "alguien"
            return SharedEntry(b.amount, (if (b.incoming) "Bizum de $who" else "Bizum a $who") + (b.concept?.let { " · $it" } ?: ""), today, b.incoming, from)
        }

        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val amount = totalFromLines(lines) ?: amountWithCurrency(t) ?: if (from == "ticket") largestDecimal(t) else null
        return SharedEntry(
            amount = amount,
            note = merchant(t, lines) ?: if (from == "ticket") "Ticket" else "",
            day = date(t, today) ?: today,
            income = INCOME.any { it in lower },
            from = from,
        )
    }

    /** La línea "TOTAL 23,45" de un ticket (la última, que es la buena), o "Tu parte: 12 €" de Tricount. */
    private fun totalFromLines(lines: List<String>): Double? {
        lines.indices.reversed().forEach { i ->
            val l = lines[i]
            if (!TOTAL_LINE.containsMatchIn(l) || NOT_TOTAL.containsMatchIn(l)) return@forEach
            val v = (DECIMAL.findAll(l) + WITH_CUR.findAll(l)).mapNotNull { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.let(PaymentParser::parseNumber) }.lastOrNull()
                // En muchos tickets el importe va en la línea de abajo.
                ?: lines.getOrNull(i + 1)?.let { n -> DECIMAL.find(n)?.groupValues?.get(1)?.let(PaymentParser::parseNumber) }
            if (v != null && v > 0) return v
        }
        return null
    }

    private fun amountWithCurrency(t: String): Double? =
        WITH_CUR.findAll(t).mapNotNull { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.let(PaymentParser::parseNumber) }.firstOrNull { it > 0 }

    private fun largestDecimal(t: String): Double? =
        DECIMAL.findAll(t).mapNotNull { PaymentParser.parseNumber(it.groupValues[1]) }.filter { it in 0.01..5000.0 }.maxOrNull()

    private fun merchant(t: String, lines: List<String>): String? {
        MERCHANT_EN.find(t)?.groupValues?.get(1)?.let { return tidy(it) }
        // En un ticket, el comercio es la primera línea con letras que no es ruido.
        return lines.take(6).firstOrNull { l ->
            val ll = l.lowercase(Locale.ROOT)
            l.count { it.isLetter() } >= 3 && NOISE.none { it in ll } && DECIMAL.find(l) == null && !l.any { it.isDigit() && l.count(Char::isDigit) > 4 }
        }?.let(::tidy)
    }

    private fun tidy(raw: String): String? {
        val m = Merchants.clean(raw).trim('"', '\'', '*', '-', ' ').take(40).trim()
        if (m.length < 2) return null
        return if (m == m.uppercase(Locale.ROOT)) m.lowercase(Locale.ROOT).split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } } else m
    }

    /** Fecha del ticket si es de los últimos 60 días; si no, hoy. */
    private fun date(t: String, today: LocalDate): LocalDate? = DATE.findAll(t).firstNotNullOfOrNull { m ->
        val (d, mo, y) = m.destructured
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()?.takeIf { !it.isAfter(today) && !it.isBefore(today.minusDays(60)) }
    }
}
