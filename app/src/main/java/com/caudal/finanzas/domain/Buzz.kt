package com.caudal.finanzas.domain

import com.caudal.finanzas.data.RedditPost
import com.caudal.finanzas.data.TrendingCoin
import kotlin.math.ln

/** Una moneda de la que se habla: menciones, votos, tono y si está en tendencia. */
data class BuzzItem(
    val symbol: String,
    val name: String,
    val coinId: String?,
    val mentions: Int,
    val votes: Int,
    /** Tono de los hilos que la mencionan: 0 (todo negativo) a 1 (todo positivo); 0,5 = neutro. */
    val positivity: Double,
    val trending: Boolean,
    val change24h: Double?,
    val posts: List<RedditPost>,
) {
    /** Ruido positivo: más menciones y votos pesan, pero con rendimiento decreciente; el tono lo multiplica. */
    val score get() = (ln(1.0 + mentions) * 2 + ln(1.0 + votes) / 2 + (if (trending) 2 else 0)) * (positivity - 0.3).coerceAtLeast(0.0)
    val tone get() = when { positivity >= 0.6 -> "positivo"; positivity <= 0.4 -> "negativo"; else -> "neutro" }
}

data class Buzz(val items: List<BuzzItem>, val headlines: List<RedditPost>) {
    /** La moneda con más ruido positivo (solo si de verdad hay tono positivo y se habla de ella). */
    val featured get() = items.filter { it.positivity >= 0.6 && (it.mentions >= 2 || it.trending) }.maxByOrNull { it.score }
}

/**
 * "¿De qué se está hablando?": cuenta en qué hilos populares de Reddit sale cada moneda (por nombre o
 * símbolo), suma sus votos y mira si las palabras de esos títulos son positivas o negativas. Le añade lo
 * más buscado en CoinGecko. No predice precios: solo resume la conversación.
 */
object BuzzAnalyzer {
    private val POSITIVE = listOf(
        "bull", "bullish", "moon", "rally", "surge", "soar", "pump", "breakout", "ath", "all-time high", "record", "adoption",
        "partnership", "approve", "approved", "approval", "etf", "upgrade", "launch", "gains", "green", "buy", "accumulat", "growth", "rise", "up ",
        "sube", "subida", "alcista", "récord", "compra",
    )
    private val NEGATIVE = listOf(
        "bear", "bearish", "crash", "dump", "scam", "hack", "hacked", "exploit", "lawsuit", "sec ", "sued", "sell", "selling", "fall", "drop",
        "plunge", "red", "rug", "ban", "fraud", "fud", "liquidat", "down ", "lose", "loss", "warning",
        "baja", "caída", "bajista", "estafa", "hackeo",
    )

    /** Monedas conocidas para reconocerlas en los títulos (además de las que tengas y las de tendencia). */
    private val KNOWN = listOf(
        Triple("bitcoin", "BTC", "Bitcoin"), Triple("ethereum", "ETH", "Ethereum"), Triple("solana", "SOL", "Solana"),
        Triple("ripple", "XRP", "XRP"), Triple("cardano", "ADA", "Cardano"), Triple("dogecoin", "DOGE", "Dogecoin"),
        Triple("chainlink", "LINK", "Chainlink"), Triple("avalanche-2", "AVAX", "Avalanche"), Triple("polkadot", "DOT", "Polkadot"),
        Triple("binancecoin", "BNB", "BNB"), Triple("litecoin", "LTC", "Litecoin"), Triple("tron", "TRX", "TRON"),
        Triple("shiba-inu", "SHIB", "Shiba Inu"), Triple("pepe", "PEPE", "Pepe"), Triple("sui", "SUI", "Sui"),
    )

    private fun tone(text: String): Double? {
        val t = " " + text.lowercase() + " "
        val pos = POSITIVE.count { t.contains(it) }
        val neg = NEGATIVE.count { t.contains(it) }
        return if (pos + neg == 0) null else pos.toDouble() / (pos + neg)
    }

    fun analyze(posts: List<RedditPost>, trending: List<TrendingCoin>, mine: List<Triple<String, String, String>> = emptyList()): Buzz {
        val coins = (mine + trending.map { Triple(it.id, it.symbol, it.name) } + KNOWN).distinctBy { it.second.uppercase() }
        val items = coins.mapNotNull { (id, symbol, name) ->
            val sym = Regex("(^|[^A-Za-z])\\$?${Regex.escape(symbol)}([^A-Za-z]|$)")
            val nm = name.lowercase()
            val hits = posts.filter { p -> (symbol.length >= 3 && sym.containsMatchIn(p.title)) || (nm.length >= 3 && p.title.lowercase().contains(nm)) }
            val tr = trending.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
            if (hits.isEmpty() && tr == null) return@mapNotNull null
            // Tono medio ponderado por votos; los títulos sin palabras claras cuentan como neutros.
            val weights = hits.map { (it.score + it.comments).coerceAtLeast(1).toDouble() }
            val tones = hits.map { tone(it.title) ?: 0.5 }
            val positivity = if (hits.isEmpty()) 0.5 else tones.zip(weights).sumOf { it.first * it.second } / weights.sum()
            BuzzItem(symbol.uppercase(), name, id, hits.size, hits.sumOf { it.score }, positivity, tr != null, tr?.change24h, hits.sortedByDescending { it.score }.take(3))
        }
        return Buzz(items.sortedByDescending { it.score + it.mentions * 0.01 }, posts.sortedByDescending { it.score }.take(6))
    }
}
