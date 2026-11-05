package com.caudal.finanzas.domain

import java.text.Normalizer

/** Cómo te toca una noticia: un titular corto y las cuentas hechas con tus datos. */
data class NewsImpact(val relevant: Boolean, val headline: String, val lines: List<String>, val tags: List<String>)

/**
 * "¿Cómo puede afectarme?": relaciona una noticia con lo que tienes en la app con reglas fijas (sin IA).
 * Busca en el titular tus criptos y acciones, tus bancos, los tipos de interés, la inflación, la gasolina,
 * la luz y la vivienda, y hace la cuenta con tus saldos y tus gastos reales. Nunca dice qué comprar o vender.
 */
object NewsImpactAnalyzer {
    private val DOWN = listOf("cae", "caen", "caida", "baja", "bajan", "bajada", "desploma", "desplome", "hunde", "hundimiento", "pierde", "pierden", "recorta", "retrocede", "crash", "plunge", "drop", "falls", "se deja", "minimos", "abarata", "recorte", "rebaja")
    private val UP = listOf("sube", "suben", "subida", "dispara", "se dispara", "gana", "ganan", "record", "maximos", "rally", "repunta", "avanza", "escala", "surge", "rises", "encarece", "alza", "revaloriza")
    private val CRYPTO_NAMES = mapOf(
        "BTC" to listOf("bitcoin", "btc"), "ETH" to listOf("ethereum", "ether", "eth"), "SOL" to listOf("solana"),
        "XRP" to listOf("xrp", "ripple"), "BNB" to listOf("bnb", "binance coin"), "DOGE" to listOf("dogecoin", "doge"),
        "ADA" to listOf("cardano"), "DOT" to listOf("polkadot"), "LTC" to listOf("litecoin"), "AVAX" to listOf("avalanche"),
        "LINK" to listOf("chainlink"), "SHIB" to listOf("shiba"), "PEPE" to listOf("pepe"), "SUI" to listOf("sui"), "TRX" to listOf("tron"),
    )

    /** Símbolos de las criptos que nombra un texto (para ponerle su gráfico a la noticia). */
    fun coinsIn(text: String): List<String> = norm(text).let { t -> CRYPTO_NAMES.filter { (_, names) -> names.any { has(t, it) } }.keys.toList() }

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    private fun has(text: String, word: String) = Regex("(^|[^a-z0-9])${Regex.escape(word)}([^a-z0-9]|$)").containsMatchIn(text)

    /** Primer porcentaje del titular ("cae un 7,5 %" → 7,5). */
    fun percentIn(text: String): Double? = Regex("(\\d{1,3}(?:[.,]\\d+)?)\\s?%").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    /** −1 si la noticia habla de bajadas, +1 de subidas, 0 si no está claro. */
    fun direction(text: String): Int {
        val d = DOWN.count { has(text, it) }; val u = UP.count { has(text, it) }
        return when { d > u -> -1; u > d -> 1; else -> 0 }
    }

    fun analyze(title: String, summary: String, d: FinanceData, fmt: (Double) -> String): NewsImpact {
        val text = norm("$title $summary")
        val dir = direction(text)
        val pct = percentIn(text)
        val lines = mutableListOf<String>()
        val tags = mutableListOf<String>()
        val total = d.wealth.total
        fun share(v: Double) = if (total > 0) " (el ${(v / total * 100).toInt()} % de tu patrimonio)" else ""
        val es = java.util.Locale.forLanguageTag("es-ES")
        fun pc(v: Double) = String.format(es, "%.1f %%", v).replace(",0 %", " %")

        // ---------- Tus criptos ----------
        val cryptoHits = d.crypto.filter { h ->
            val names = CRYPTO_NAMES[h.symbol.uppercase()].orEmpty() + h.name.lowercase() + h.symbol.lowercase()
            names.any { n -> n.length >= 3 && has(text, norm(n)) }
        }
        cryptoHits.forEach { h ->
            val v = h.amount * h.price
            tags += h.symbol
            val scen = pct ?: 10.0
            lines += "Tienes ${fmt(v)} en ${h.name}${share(v)}."
            lines += when (dir) {
                -1 -> "Si baja un ${pc(scen)}${if (pct == null) " (ejemplo)" else ""}, perderías unos ${fmt(v * scen / 100)}: tu patrimonio pasaría de ${fmt(total)} a ${fmt(total - v * scen / 100)}."
                1 -> "Si sube un ${pc(scen)}${if (pct == null) " (ejemplo)" else ""}, ganarías unos ${fmt(v * scen / 100)}: tu patrimonio pasaría de ${fmt(total)} a ${fmt(total + v * scen / 100)}."
                else -> "Cada 10 % que se mueva son unos ${fmt(v * 0.1)} arriba o abajo en tu patrimonio."
            }
            if (h.avgBuyPrice > 0) {
                val pnl = (h.price - h.avgBuyPrice) * h.amount
                lines += if (pnl >= 0) "Ahora mismo vas ganando ${fmt(pnl)} con ${h.symbol} desde que compraste." else "Ahora mismo vas perdiendo ${fmt(-pnl)} con ${h.symbol} desde que compraste."
            }
        }
        val mentionedCoins = CRYPTO_NAMES.filter { (sym, names) -> names.any { has(text, it) } && cryptoHits.none { it.symbol.equals(sym, true) } }.keys
        if (mentionedCoins.isNotEmpty()) {
            tags += mentionedCoins
            lines += "Habla de ${mentionedCoins.joinToString(", ")}, que no tienes: no te afecta directamente" +
                if (d.crypto.isNotEmpty() && (has(text, "cripto") || has(text, "criptomonedas") || has(text, "bitcoin"))) ", aunque cuando el mercado cripto se mueve, tus criptos (${fmt(d.crypto.sumOf { it.amount * it.price })}) suelen moverse con él." else "."
        }

        // ---------- Tus acciones ----------
        d.stocks.filter { s ->
            has(text, norm(s.ticker.substringBefore('.'))) || (s.name.isNotBlank() && has(text, norm(s.name.split(' ').first())) && s.name.split(' ').first().length >= 4)
        }.forEach { s ->
            val v = s.shares * s.price
            tags += s.ticker
            val scen = pct ?: 5.0
            lines += "Tienes ${fmt(v)} en acciones de ${s.name.ifBlank { s.ticker }}${share(v)}."
            lines += when (dir) {
                -1 -> "Una bajada del ${pc(scen)}${if (pct == null) " (ejemplo)" else ""} serían unos ${fmt(v * scen / 100)} menos."
                1 -> "Una subida del ${pc(scen)}${if (pct == null) " (ejemplo)" else ""} serían unos ${fmt(v * scen / 100)} más."
                else -> "Cada 5 % que se mueva son unos ${fmt(v * 0.05)}."
            }
        }

        // ---------- Tus bancos ----------
        d.accounts.filter { a -> a.name.length >= 4 && has(text, norm(a.name.split(' ').first())) }.forEach { a ->
            tags += a.name
            lines += "Tienes ${fmt(a.balance)} en ${a.name}." + if (a.type.inBank) " En bancos de la zona euro, los depósitos están cubiertos por el fondo de garantía hasta 100.000 € por persona y banco." else ""
        }

        // ---------- Tipos de interés ----------
        if (listOf("tipos de interes", "bce", "euribor", "reserva federal", "fed", "lagarde", "powell", "tipos").any { has(text, it) }) {
            tags += "Tipos de interés"
            val paying = d.accounts.filter { it.interestRate > 0 && it.balance > 0 }
            if (paying.isEmpty()) lines += "Los tipos de interés mueven lo que pagan las cuentas remuneradas y las hipotecas. No tienes ninguna cuenta con intereses apuntada en la app."
            else paying.forEach { a ->
                val now = a.balance * a.interestRate / 100 / 12
                val delta = a.balance * 0.25 / 100 / 12
                lines += "${a.name} te da un ${pc(a.interestRate)} TAE: unos ${fmt(now)} al mes con ${fmt(a.balance)}." +
                    when (dir) {
                        -1 -> " Si los tipos bajan, esa TAE suele bajar detrás: con 0,25 puntos menos ganarías ~${fmt(delta)} menos al mes."
                        1 -> " Si los tipos suben, podría pagar algo más: 0,25 puntos más serían ~${fmt(delta)} más al mes."
                        else -> " Cada 0,25 puntos de TAE son ~${fmt(delta)} al mes."
                    }
            }
        }

        // ---------- Inflación ----------
        val monthly = monthlySpend(d)
        if (listOf("inflacion", "ipc", "precios", "carestia").any { has(text, it) }) {
            tags += "Inflación"
            val inf = pct?.takeIf { it < 20 } ?: 3.0
            if (monthly > 0) lines += "Gastas unos ${fmt(monthly)} al mes en el día a día. Con una inflación del ${pc(inf)}${if (pct == null) " (ejemplo)" else ""}, lo mismo te costaría ~${fmt(monthly * inf / 100)} más al mes (${fmt(monthly * 12 * inf / 100)} al año)."
            d.accounts.filter { it.interestRate > 0 }.maxByOrNull { it.balance }?.let { a ->
                val real = a.interestRate - inf
                lines += "Tu ${a.name} da un ${pc(a.interestRate)}: " + if (real >= 0) "por encima de esa inflación (ganas ~${pc(real)} de verdad)." else "por debajo de esa inflación, así que tu ahorro pierde algo de poder de compra."
            }
        }

        // ---------- Gasolina, luz y vivienda ----------
        fun tagMonthly(t: SpendTag) = d.expenses.filter { it.epochDay >= d.today.minusDays(90).toEpochDay() && it.categoryId !in d.savingIds && it.spendTag() == t }.sumOf { it.amount } / 3
        if (listOf("gasolina", "gasoleo", "diesel", "carburantes", "petroleo", "crudo", "brent", "combustible").any { has(text, it) }) {
            tags += "Gasolina"
            val t = tagMonthly(SpendTag.TRANSPORT)
            val scen = pct?.takeIf { it < 50 } ?: 5.0
            lines += if (t > 0) "Te gastas unos ${fmt(t)} al mes en transporte. Un ${pc(scen)} ${if (dir < 0) "menos" else "más"} serían ~${fmt(t * scen / 100)} ${if (dir < 0) "de ahorro" else "de más"} al mes." else "No tienes gastos de transporte apuntados últimamente: apenas te afecta."
        }
        if (listOf("luz", "electricidad", "factura electrica", "gas natural", "tarifa").any { has(text, it) }) {
            tags += "Luz y gas"
            val h = tagMonthly(SpendTag.HOME)
            if (h > 0) lines += "En casa y facturas gastas unos ${fmt(h)} al mes. Un ${pc(pct?.takeIf { it < 50 } ?: 10.0)} ${if (dir < 0) "menos" else "más"} serían ~${fmt(h * (pct?.takeIf { it < 50 } ?: 10.0) / 100)} al mes."
        }
        if (listOf("alquiler", "alquileres", "vivienda", "hipoteca", "hipotecas", "pisos").any { has(text, it) }) {
            tags += "Vivienda"
            val rent = d.bills.filter { norm(it.name).let { n -> "alquiler" in n || "hipoteca" in n } }.sumOf { it.amount }
            lines += if (rent > 0) "Tu alquiler/hipoteca es de ${fmt(rent)} al mes. Las subidas suelen llegar al renovar el contrato; un 5 % serían ${fmt(rent * 0.05)} más al mes." else "No tienes un alquiler o hipoteca apuntado como gasto fijo."
        }
        if (listOf("paro", "despidos", "ere", "empleo", "recesion").any { has(text, it) } && monthly > 0) {
            tags += "Empleo"
            val cushion = d.wealth.savings + d.wealth.available
            lines += "Si un mes no entrara dinero, tu banco y tus ahorros (${fmt(cushion)}) cubrirían unos ${String.format(es, "%.1f", cushion / monthly)} meses de tu gasto actual."
        }

        val relevant = lines.isNotEmpty() && (cryptoHits.isNotEmpty() || tags.any { it !in mentionedCoins })
        val headline = when {
            !relevant && lines.isEmpty() -> "No toca nada de lo que tienes"
            !relevant -> "Te afecta poco"
            cryptoHits.isNotEmpty() || d.stocks.any { it.ticker in tags } -> if (dir < 0) "Puede restarte dinero" else if (dir > 0) "Puede sumarte dinero" else "Toca tus inversiones"
            else -> "Te afecta en el día a día"
        }
        if (lines.isEmpty()) lines += "Esta noticia no habla de tus cuentas, tus inversiones ni de tus gastos principales. Bueno para estar al día, pero no cambia tus números."
        return NewsImpact(relevant, headline, lines.distinct(), tags.distinct())
    }

    /** Gasto mensual del día a día (sin ahorro), media de los últimos 90 días. */
    fun monthlySpend(d: FinanceData): Double {
        val since = d.today.minusDays(90).toEpochDay()
        val first = d.expenses.minOfOrNull { it.epochDay } ?: return 0.0
        val days = (d.today.toEpochDay() - maxOf(since, first) + 1).coerceAtLeast(30)
        return d.spending.filter { it.epochDay >= since }.sumOf { it.amount } / days * 30.4
    }
}
