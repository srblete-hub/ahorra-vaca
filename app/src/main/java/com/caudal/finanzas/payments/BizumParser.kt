package com.caudal.finanzas.payments

import java.util.Locale

/** Un Bizum leído de un aviso del banco: importe, si lo recibes o lo envías, con quién y el concepto. */
data class Bizum(val amount: Double, val incoming: Boolean, val person: String?, val concept: String?, val raw: String)

/**
 * Lee los avisos de Bizum de la app del banco (Santander, BBVA, CaixaBank…) sin conectar el banco:
 * "Has recibido un Bizum de 8,00 € de JUAN PABLO", "Has enviado 10,00 € a JUAN PABLO por Bizum. Concepto: pádel".
 */
object BizumParser {
    private val INCOMING = listOf("recibid", "te ha enviado", "te han enviado", "te ha pagado", "has cobrado", "te envía", "te envia", "received")
    private val OUTGOING = listOf("has enviado", "enviado a", "has pagado", "has realizado", "enviaste", "sent")
    private val IGNORE = listOf("solicitud", "te pide", "te ha pedido", "pendiente", "rechazad", "caducad", "cancelad", "devuelto", "no se ha", "regístrate", "registrate")
    private const val NUM = "(\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"
    private val AMOUNT = Regex("$NUM\\s?(?:€|EUR|euros?)", RegexOption.IGNORE_CASE)
    private val FROM = Regex("\\bde\\s+([A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ.]+){0,3})")
    private val TO = Regex("\\ba\\s+([A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ.]+){0,3})")
    private val WHO_SENT = Regex("^([A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ.]+){0,3})\\s+te\\s+ha", RegexOption.MULTILINE)
    private val CONCEPT = Regex("(?:concepto|motivo)\\s*:?\\s*[\"«']?([^\"»'\\n.]{1,60})", RegexOption.IGNORE_CASE)

    fun parse(title: String?, text: String?): Bizum? {
        val full = listOfNotNull(title, text).joinToString("\n").trim()
        val lower = full.lowercase(Locale.ROOT)
        if ("bizum" !in lower || IGNORE.any { it in lower }) return null
        val incoming = INCOMING.any { it in lower } && OUTGOING.none { it in lower && it != "enviado a" }
        val outgoing = OUTGOING.any { it in lower }
        if (!incoming && !outgoing) return null
        val raw = AMOUNT.find(full)?.groupValues?.get(1) ?: return null
        val amount = PaymentParser.parseNumber(raw) ?: return null
        if (amount <= 0) return null
        val person = (if (incoming) WHO_SENT.find(full)?.groupValues?.get(1) ?: FROM.findAll(full).map { it.groupValues[1] }.firstOrNull { it != "Bizum" }
            else TO.findAll(full).map { it.groupValues[1] }.firstOrNull { it != "Bizum" })
            ?.let(::pretty)
        val concept = CONCEPT.find(full)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        return Bizum(amount, incoming, person, concept, full)
    }

    /** "JUAN PABLO" → "Juan Pablo". */
    private fun pretty(s: String) = s.split(Regex("\\.\\s")).first().trim().trimEnd('.').lowercase(Locale.ROOT).split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
}
