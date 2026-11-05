package com.caudal.finanzas.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

data class CoinResult(val id: String, val symbol: String, val name: String)

/** Estadísticas de una cripto según CoinGecko (gratis). Lo que no venga queda en null. */
/** Una cripto del mercado: precio, cambio en 24 h y su gráfico de las últimas 24 h. */
data class MarketCoin(val id: String, val symbol: String, val name: String, val price: Double, val change24h: Double?, val spark: List<Double>)

/** Un sitio donde se negocia una cripto (de CoinGecko): nombre, par, enlace y confianza. */
data class CoinMarket(val exchange: String, val pair: String, val url: String?, val trusted: Boolean)

data class CoinStats(
    val rank: Int?, val marketCap: Double?, val volume24h: Double?, val high24h: Double?, val low24h: Double?,
    val ath: Double?, val athChangePct: Double?, val change7d: Double?, val change30d: Double?, val change1y: Double?,
    val circulating: Double?, val maxSupply: Double?,
)

/** Una moneda en tendencia en CoinGecko (lo más buscado). */
data class TrendingCoin(val id: String, val symbol: String, val name: String, val rank: Int?, val change24h: Double?)

/** Un hilo de Reddit: título, votos, comentarios y enlace. */
data class RedditPost(val title: String, val score: Int, val comments: Int, val url: String)

/** Una vela: apertura, máximo, mínimo y cierre de un tramo. */
data class Candle(val open: Double, val high: Double, val low: Double, val close: Double)

/**
 * Precios de mercado. Cripto: API pública de CoinGecko (sin clave).
 * Acciones: endpoint público de Yahoo Finance; no es oficial y puede fallar, por eso el
 * precio de una acción también se puede escribir a mano.
 */
object PriceService {

    suspend fun cryptoPrices(ids: List<String>, currency: String): Map<String, Double> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val cur = currency.lowercase()
        val json = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=${enc(ids.joinToString(","))}&vs_currencies=$cur"))
        ids.mapNotNull { id ->
            json.optJSONObject(id)?.takeIf { it.has(cur) }?.let { id to it.getDouble(cur) }
        }.toMap()
    }

    /** Precio y cambio en 24 h (%) de cada cripto. */
    suspend fun cryptoQuotes(ids: List<String>, currency: String): Map<String, Pair<Double, Double?>> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val cur = currency.lowercase()
        val json = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=${enc(ids.joinToString(","))}&vs_currencies=$cur&include_24hr_change=true"))
        ids.mapNotNull { id ->
            json.optJSONObject(id)?.takeIf { it.has(cur) }?.let { o ->
                id to (o.getDouble(cur) to o.optDouble("${cur}_24h_change").takeIf { !it.isNaN() })
            }
        }.toMap()
    }

    /** Precio de [ticker] en [currency] y su cambio (%) frente al cierre anterior. */
    suspend fun stockQuote(ticker: String, currency: String): Pair<Double, Double?> = withContext(Dispatchers.IO) {
        val meta = yahooMeta(ticker)
        val prev = meta.optDouble("chartPreviousClose").takeIf { !it.isNaN() && it > 0 }
        val raw = meta.getDouble("regularMarketPrice")
        stockPrice(ticker, currency) to prev?.let { (raw - it) / it * 100 }
    }

    private fun yahooMeta(symbol: String): JSONObject {
        val body = get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(symbol)}?range=5d&interval=1d")
        val result = JSONObject(body).getJSONObject("chart").optJSONArray("result") ?: throw IOException("No encontrado: $symbol")
        return result.getJSONObject(0).getJSONObject("meta")
    }

    /** Para pruebas sin internet: devuelve un historial inventado. */
    @Volatile var historyOverride: ((String, Int) -> List<Double>)? = null
    @Volatile var marketsOverride: (() -> List<MarketCoin>)? = null
    @Volatile var exchangesOverride: ((String) -> List<CoinMarket>)? = null

    /** Dónde se puede comprar una cripto: los exchanges con más volumen (gratis, CoinGecko). */
    suspend fun coinExchanges(id: String): List<CoinMarket> = withContext(Dispatchers.IO) {
        exchangesOverride?.let { return@withContext it(id) }
        val arr = JSONObject(get("https://api.coingecko.com/api/v3/coins/${enc(id)}/tickers?order=volume_desc&depth=false")).getJSONArray("tickers")
        (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { t ->
            val name = t.optJSONObject("market")?.optString("name").orEmpty()
            if (name.isBlank() || t.optBoolean("is_stale") || t.optBoolean("is_anomaly")) null
            else CoinMarket(name, "${t.optString("base")}/${t.optString("target")}", t.optString("trade_url").takeIf { it.startsWith("https://") }, t.optString("trust_score") == "green")
        }.distinctBy { it.exchange }.take(8)
    }

    /** Precios de varias cripto en una sola llamada gratuita a CoinGecko (con minigráfico). */
    suspend fun cryptoMarkets(ids: List<String>, currency: String): List<MarketCoin> = withContext(Dispatchers.IO) {
        marketsOverride?.let { return@withContext it() }
        val arr = org.json.JSONArray(get("https://api.coingecko.com/api/v3/coins/markets?vs_currency=${currency.lowercase()}&ids=${enc(ids.joinToString(","))}&sparkline=true&price_change_percentage=24h"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sp = o.optJSONObject("sparkline_in_7d")?.optJSONArray("price")
            MarketCoin(
                o.getString("id"), o.getString("symbol").uppercase(), o.getString("name"), o.getDouble("current_price"),
                o.optDouble("price_change_percentage_24h").takeIf { !it.isNaN() },
                sp?.let { a -> (0 until a.length()).map { a.getDouble(it) } }.orEmpty().takeLast(24),
            )
        }
    }

    /** En pruebas: reparte los puntos inventados a lo largo del periodo. */
    private fun fake(values: List<Double>, days: Int): List<Pair<Long, Double>> {
        val now = System.currentTimeMillis(); val span = days * 86_400_000L
        return values.mapIndexed { i, v -> (now - span + span * i / (values.size - 1).coerceAtLeast(1)) to v }
    }

    /** Precios (hora en ms, precio) de los últimos [days] días de una cripto (unos 60 puntos). */
    suspend fun cryptoHistory(id: String, currency: String, days: Int): List<Pair<Long, Double>> = withContext(Dispatchers.IO) {
        historyOverride?.let { return@withContext fake(it(id, days), days) }
        val arr = JSONObject(get("https://api.coingecko.com/api/v3/coins/${enc(id)}/market_chart?vs_currency=${currency.lowercase()}&days=$days")).getJSONArray("prices")
        samplePoints((0 until arr.length()).map { arr.getJSONArray(it).let { p -> p.getLong(0) to p.getDouble(1) } })
    }

    /** Cierres (hora en ms, precio) de los últimos [days] días de una acción, en [currency]. */
    suspend fun stockHistory(ticker: String, currency: String, days: Int): List<Pair<Long, Double>> = withContext(Dispatchers.IO) {
        historyOverride?.let { return@withContext fake(it(ticker, days), days) }
        val (range, interval) = when { days <= 1 -> "1d" to "5m"; days <= 7 -> "5d" to "1h"; days <= 31 -> "1mo" to "1d"; days <= 92 -> "3mo" to "1d"; else -> "1y" to "1d" }
        val body = get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(ticker)}?range=$range&interval=$interval")
        val r = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val times = r.optJSONArray("timestamp")
        val closes = r.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
        val raw = (0 until closes.length()).mapNotNull { i -> closes.optDouble(i).takeIf { v -> !v.isNaN() }?.let { (times?.optLong(i) ?: 0L) * 1000 to it } }
        val last = r.getJSONObject("meta").getDouble("regularMarketPrice")
        // Misma conversión de moneda que el precio actual.
        val factor = if (last > 0) stockPrice(ticker, currency) / last else 1.0
        samplePoints(raw.map { it.first to it.second * factor })
    }

    private fun samplePoints(list: List<Pair<Long, Double>>, max: Int = 80): List<Pair<Long, Double>> =
        if (list.size <= max) list else (0 until max).map { list[it * (list.size - 1) / (max - 1)] }

    /** Estadísticas de mercado de una cripto (CoinGecko, gratis). */
    suspend fun coinStats(id: String, currency: String): CoinStats = withContext(Dispatchers.IO) {
        statsOverride?.let { return@withContext it(id) }
        val md = JSONObject(get("https://api.coingecko.com/api/v3/coins/${enc(id)}?localization=false&tickers=false&community_data=false&developer_data=false&sparkline=false"))
        val rank = md.optInt("market_cap_rank").takeIf { it > 0 }
        val m = md.getJSONObject("market_data")
        val cur = currency.lowercase()
        fun byCur(k: String) = m.optJSONObject(k)?.optDouble(cur)?.takeIf { !it.isNaN() }
        fun num(k: String) = m.optDouble(k).takeIf { !it.isNaN() }
        CoinStats(
            rank, byCur("market_cap"), byCur("total_volume"), byCur("high_24h"), byCur("low_24h"), byCur("ath"), byCur("ath_change_percentage"),
            num("price_change_percentage_7d"), num("price_change_percentage_30d"), num("price_change_percentage_1y"),
            num("circulating_supply"), num("max_supply"),
        )
    }
    @Volatile var statsOverride: ((String) -> CoinStats)? = null

    /** Lo más buscado en CoinGecko ahora mismo (gratis). */
    suspend fun trending(): List<TrendingCoin> = withContext(Dispatchers.IO) {
        trendingOverride?.let { return@withContext it() }
        val coins = JSONObject(get("https://api.coingecko.com/api/v3/search/trending")).getJSONArray("coins")
        (0 until coins.length()).map { i ->
            val it = coins.getJSONObject(i).getJSONObject("item")
            val change = it.optJSONObject("data")?.optJSONObject("price_change_percentage_24h")?.optDouble("eur")?.takeIf { v -> !v.isNaN() }
            TrendingCoin(it.getString("id"), it.getString("symbol").uppercase(), it.getString("name"), it.optInt("market_cap_rank").takeIf { r -> r > 0 }, change)
        }
    }
    @Volatile var trendingOverride: (() -> List<TrendingCoin>)? = null

    /** Hilos más populares de un subreddit (JSON público de Reddit, gratis y sin cuenta). */
    suspend fun redditHot(sub: String, limit: Int = 60): List<RedditPost> = withContext(Dispatchers.IO) {
        redditOverride?.let { return@withContext it(sub) }
        val children = JSONObject(get("https://www.reddit.com/r/${enc(sub)}/hot.json?limit=$limit&raw_json=1")).getJSONObject("data").getJSONArray("children")
        (0 until children.length()).map { i ->
            val d = children.getJSONObject(i).getJSONObject("data")
            RedditPost(d.optString("title"), d.optInt("score"), d.optInt("num_comments"), "https://www.reddit.com" + d.optString("permalink"))
        }.filter { it.title.isNotBlank() }
    }
    @Volatile var redditOverride: ((String) -> List<RedditPost>)? = null

    /** Para pruebas sin internet: velas inventadas. */
    @Volatile var ohlcOverride: ((String, Int) -> List<Candle>)? = null

    /** Velas de los últimos [days] días de una cripto (CoinGecko, gratis). */
    suspend fun cryptoOhlc(id: String, currency: String, days: Int): List<Candle> = withContext(Dispatchers.IO) {
        ohlcOverride?.let { return@withContext it(id, days) }
        val d = listOf(7, 14, 30, 90, 180, 365).firstOrNull { it >= days } ?: 365
        val arr = org.json.JSONArray(get("https://api.coingecko.com/api/v3/coins/${enc(id)}/ohlc?vs_currency=${currency.lowercase()}&days=$d"))
        sampleCandles((0 until arr.length()).map { i -> arr.getJSONArray(i).let { Candle(it.getDouble(1), it.getDouble(2), it.getDouble(3), it.getDouble(4)) } })
    }

    /** Velas diarias de una acción (Yahoo, gratis), en [currency]. */
    suspend fun stockOhlc(ticker: String, currency: String, days: Int): List<Candle> = withContext(Dispatchers.IO) {
        ohlcOverride?.let { return@withContext it(ticker, days) }
        val range = when { days <= 7 -> "5d"; days <= 31 -> "1mo"; days <= 92 -> "3mo"; else -> "1y" }
        val r = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(ticker)}?range=$range&interval=1d"))
            .getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val q = r.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val last = r.getJSONObject("meta").getDouble("regularMarketPrice")
        val factor = if (last > 0) stockPrice(ticker, currency) / last else 1.0
        val o = q.getJSONArray("open"); val h = q.getJSONArray("high"); val l = q.getJSONArray("low"); val c = q.getJSONArray("close")
        sampleCandles((0 until c.length()).mapNotNull { i ->
            listOf(o.optDouble(i), h.optDouble(i), l.optDouble(i), c.optDouble(i)).takeIf { v -> v.none { it.isNaN() } }
                ?.let { (a, b, cc, d) -> Candle(a * factor, b * factor, cc * factor, d * factor) }
        })
    }

    /** Junta velas hasta dejar como mucho [max] (la primera apertura, máximos y mínimos del grupo y el último cierre). */
    private fun sampleCandles(list: List<Candle>, max: Int = 40): List<Candle> {
        if (list.size <= max) return list
        val size = (list.size + max - 1) / max
        return list.chunked(size).map { g -> Candle(g.first().open, g.maxOf { it.high }, g.minOf { it.low }, g.last().close) }
    }

    private fun sample(list: List<Double>, max: Int = 60): List<Double> =
        if (list.size <= max) list else (0 until max).map { list[it * (list.size - 1) / (max - 1)] }

    suspend fun searchCoins(query: String): List<CoinResult> = withContext(Dispatchers.IO) {
        val coins = JSONObject(get("https://api.coingecko.com/api/v3/search?query=${enc(query)}")).getJSONArray("coins")
        (0 until minOf(coins.length(), 8)).map { i ->
            val c = coins.getJSONObject(i)
            CoinResult(c.getString("id"), c.getString("symbol").uppercase(), c.getString("name"))
        }
    }

    /** Precio de [ticker] convertido a [currency]. */
    suspend fun stockPrice(ticker: String, currency: String): Double = withContext(Dispatchers.IO) {
        val (price, quoteCurrency) = yahooQuote(ticker)
        // La Bolsa de Londres cotiza en peniques (GBp).
        val (value, cur) = if (quoteCurrency == "GBp") price / 100 to "GBP" else price to quoteCurrency
        if (cur.equals(currency, ignoreCase = true)) value
        else value * yahooQuote("${cur.uppercase()}${currency.uppercase()}=X").first
    }

    private fun yahooQuote(symbol: String): Pair<Double, String> {
        val body = get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(symbol)}?range=1d&interval=1d")
        val result = JSONObject(body).getJSONObject("chart").optJSONArray("result")
            ?: throw IOException("No encontrado: $symbol")
        val meta = result.getJSONObject(0).getJSONObject("meta")
        return meta.getDouble("regularMarketPrice") to meta.optString("currency", "USD")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** CoinGecko gratis limita las consultas por minuto: aviso propio para tratarlo con calma. */
    class RateLimited : IOException("CoinGecko limita las consultas")

    private val cgLock = Any()
    @Volatile private var cgLast = 0L
    private val cgCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    private fun get(url: String): String {
        if (!url.contains("coingecko.com")) return fetch(url)
        cgCache[url]?.let { (t, body) -> if (System.currentTimeMillis() - t < 60_000) return body }
        synchronized(cgLock) {
            cgCache[url]?.let { (t, body) -> if (System.currentTimeMillis() - t < 60_000) return body }
            repeat(2) { attempt ->
                val wait = cgLast + 2_500 - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                cgLast = System.currentTimeMillis()
                try {
                    return fetch(url).also { cgCache[url] = System.currentTimeMillis() to it }
                } catch (e: RateLimited) {
                    if (attempt == 1) throw e
                    Thread.sleep(15_000)
                }
            }
            throw RateLimited()
        }
    }

    private fun fetch(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Caudal/0.1")
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode == 429) throw RateLimited()
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
