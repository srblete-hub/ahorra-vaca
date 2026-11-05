package com.caudal.finanzas.payments

import java.util.Locale

data class DetectedPayment(val amount: Double, val currency: String, val merchant: String?, val raw: String = "")

/**
 * Saca importe y comercio del texto de una notificación de pago (Google Wallet o la app del banco).
 * Es Kotlin puro para poder probarlo en el PC.
 */
object PaymentParser {

    /** Carteras: su notificación es "Comercio" + "12,50 € con Visa ••1234", sin la palabra "pago". */
    val WALLET_APPS = mapOf(
        "com.google.android.apps.walletnfcrel" to "Google Wallet",
        "com.samsung.android.spay" to "Samsung Wallet",
        "com.samsung.android.samsungpay.gear" to "Samsung Wallet",
    )

    val BANK_APPS = mapOf(
        "com.bbva.bbvacontigo" to "BBVA",
        "es.lacaixa.mobile.android.newwapicon" to "CaixaBank",
        "com.imaginbank.app" to "imagin",
        "es.bancosantander.apps" to "Santander",
        "es.openbank.mobile" to "Openbank",
        "www.ingdirect.nativeframe" to "ING",
        "net.inverline.bancosabadell.officelocator.android" to "Sabadell",
        "com.kutxabank.android" to "Kutxabank",
        "es.unicajabanco.app" to "Unicaja",
        "com.abanca.bm.pt" to "ABANCA",
        "es.ibercaja.ibercajaapp" to "Ibercaja",
        "com.bankinter.launcher" to "Bankinter",
        "es.evobanco.bancamovil" to "EVO",
        "com.revolut.revolut" to "Revolut",
        "de.number26.android" to "N26",
        "com.bunq.android" to "bunq",
        "com.wise.android" to "Wise",
        "com.paypal.android.p2pmobile" to "PayPal",
    )

    private val EXCLUDE = listOf(
        "devoluci", "reembols", "ingreso", "recibid", "abono", "te han enviado", "te ha enviado",
        "rechazad", "denegad", "no se ha podido", "fallid", "cancelad", "anulad",
        "refund", "received", "declined", "failed", "cancelled", "canceled",
        // Verificaciones de compra online (aún no es un pago: llegará otro aviso si se completa).
        "código", "codigo", "confirma", "autoriza", "verifica", "clave", "otp", "contraseña", "seguridad",
        // Avisos de cargos futuros o pendientes.
        "se cargará", "se cargara", "se realizará", "se realizara", "programad", "pendiente", "próxim", "proxim",
        "mañana", "recordatorio", "recuerda", "vence", "vencimiento", "aviso de recibo", "previsto",
        // Publicidad, saldos y límites.
        "descuento", "consigue", "gana ", "oferta", "promoci", "cashback", "regalo", "sorteo", "invita",
        "tu saldo es", "tu saldo disponible", "tu saldo actual", "límite", "limite", "resumen", "extracto",
        "verification", "confirm", "upcoming", "scheduled", "reminder", "offer", "balance",
    )

    private val PAYMENT_WORDS = listOf(
        "pago", "pagado", "compra", "cargo", "has gastado", "gasto con", "operación con tarjeta",
        "operacion con tarjeta", "con tu tarjeta", "con tarjeta",
        "payment", "paid", "purchase", "spent", "card transaction",
    )

    private const val CUR = "(€|EUR|\\$|USD|£|GBP)"
    // Miles con punto, coma o espacio ("1.234,56", "1,234.56", "1 234"), o número simple ("23,45").
    private const val NUM = "(\\d{1,3}(?:[.,\\s\\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"
    private val AMOUNT_AFTER = Regex("$NUM\\s?$CUR", RegexOption.IGNORE_CASE)
    private val AMOUNT_BEFORE = Regex("$CUR\\s?$NUM", RegexOption.IGNORE_CASE)
    private val MERCHANT = Regex(
        "\\b(?:en|at|in)\\s+(.+?)(?=\\s+(?:por|con|de|el|on|for|with|using)\\b|[.,;:·]|\\s+\\d|$)",
        RegexOption.IGNORE_CASE,
    )

    fun parse(title: String?, text: String?, isWallet: Boolean): DetectedPayment? {
        val full = listOfNotNull(title, text).joinToString("\n").trim()
        if (full.isEmpty()) return null
        val lower = full.lowercase(Locale.ROOT)
        if (EXCLUDE.any { it in lower }) return null
        if (!isWallet && PAYMENT_WORDS.none { it in lower }) return null

        val (rawNumber, rawCurrency) = AMOUNT_AFTER.find(full)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: AMOUNT_BEFORE.find(full)?.let { it.groupValues[2] to it.groupValues[1] }
            ?: return null
        val amount = parseNumber(rawNumber) ?: return null
        if (amount <= 0) return null

        val merchant = text?.let { MERCHANT.find(it)?.groupValues?.get(1) }
            ?: title?.let { MERCHANT.find(it)?.groupValues?.get(1) }
            ?: title?.takeIf { isWallet && AMOUNT_AFTER.find(it) == null && AMOUNT_BEFORE.find(it) == null }
        return DetectedPayment(amount, currencyCode(rawCurrency), merchant?.let(::cleanMerchant), full)
    }

    /**
     * Firma de una notificación sin números (importes, fechas, tarjetas): si marcas un aviso como
     * "no era un pago", los que tengan la misma firma se ignoran.
     */
    fun signature(raw: String): String =
        raw.lowercase(Locale.ROOT).replace(Regex("[0-9]+"), "#").replace(Regex("[^a-záéíóúñü#]+"), " ").trim().take(120)

    /** "1.234,56", "1,234.56", "12,5", "1 234" → número. */
    fun parseNumber(raw: String): Double? {
        val s = raw.replace(Regex("[\\s\\u00A0\\u202F]"), "")
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val decimalSep = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> if (s.length - lastComma - 1 in 1..2) ',' else null
            lastDot >= 0 -> if (s.length - lastDot - 1 in 1..2) '.' else null
            else -> null
        }
        val normalized = if (decimalSep == null) s.replace(",", "").replace(".", "")
        else s.replace(if (decimalSep == ',') "." else ",", "").replace(decimalSep, '.')
        return normalized.toDoubleOrNull()
    }

    private fun currencyCode(raw: String) = when (raw.uppercase(Locale.ROOT)) {
        "€", "EUR" -> "EUR"
        "$", "USD" -> "USD"
        "£", "GBP" -> "GBP"
        else -> raw.uppercase(Locale.ROOT)
    }

    /** "MERCADONA S.A." → "Mercadona S.A."; corta comercios muy largos. */
    private fun cleanMerchant(raw: String): String? {
        val m = raw.trim().trim('"', '\'', '*').take(40).trim()
        if (m.length < 2) return null
        return if (m == m.uppercase(Locale.ROOT) && m.any { it.isLetter() }) {
            m.lowercase(Locale.ROOT).split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        } else m
    }
}
