package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Candle
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.EmptyState
import com.caudal.finanzas.ui.Initials
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.ScreenArgs
import com.caudal.finanzas.ui.ScreenTitle
import com.caudal.finanzas.ui.Segmented
import com.caudal.finanzas.ui.gainColor
import com.caudal.finanzas.ui.quantity
import com.caudal.finanzas.ui.signedPct
import java.util.Locale
import kotlinx.coroutines.launch

enum class InvestKind(val label: String) { CRYPTO("Cripto"), STOCKS("Acciones") }

/** Periodos de los gráficos. */
private enum class Range(val label: String, val days: Int) { DAY("24 h", 1), WEEK("7 días", 7), MONTH("1 mes", 30), QUARTER("3 meses", 90), YEAR("1 año", 365) }

private val es = Locale.forLanguageTag("es-ES")
/** Tonos pastel de las tarjetas (como la referencia: lila, menta, melocotón…), con texto oscuro. */
private val Pastels = listOf(Color(0xFFB9B1F6), Color(0xFFA9DCC7), Color(0xFFF6CBA8), Color(0xFFB4D5F6), Color(0xFFF4B9CB))
private val OnPastel = Color(0xFF15151C)

/** Colores de marca conocidos; el resto, un tono estable por símbolo. */
fun symbolColor(symbol: String): Color = when (symbol.uppercase()) {
    "BTC" -> Color(0xFFF7931A)
    "ETH" -> Color(0xFF627EEA)
    "SOL" -> Color(0xFF9945FF)
    "ADA" -> Color(0xFF0033AD)
    "XRP" -> Color(0xFF23292F)
    "DOGE" -> Color(0xFFC2A633)
    "USDT", "USDC" -> Color(0xFF26A17B)
    "AAPL" -> Color(0xFF1C1C1E)
    else -> listOf(Color(0xFF1E9D57), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEF6C4A), Color(0xFF14B8A6), Color(0xFFE5578A))[Math.floorMod(symbol.hashCode(), 6)]
}

/** Acciones conocidas para mirar precios aunque no tengas ninguna. Solo para ver: no se compra nada. */
val MARKET = listOf(
    "AAPL" to "Apple", "MSFT" to "Microsoft", "NVDA" to "Nvidia", "AMZN" to "Amazon", "GOOGL" to "Alphabet (Google)",
    "META" to "Meta", "TSLA" to "Tesla", "NFLX" to "Netflix", "MCD" to "McDonald's", "DIS" to "Walt Disney",
    "KO" to "Coca-Cola", "SAN.MC" to "Banco Santander", "ITX.MC" to "Inditex", "IBE.MC" to "Iberdrola", "BBVA.MC" to "BBVA",
)

/** Criptos conocidas (id de CoinGecko, símbolo, nombre) para mirar aunque no tengas. */
val CRYPTO_MARKET = listOf(
    Triple("bitcoin", "BTC", "Bitcoin"), Triple("ethereum", "ETH", "Ethereum"), Triple("solana", "SOL", "Solana"),
    Triple("ripple", "XRP", "XRP"), Triple("binancecoin", "BNB", "BNB"), Triple("dogecoin", "DOGE", "Dogecoin"),
    Triple("cardano", "ADA", "Cardano"), Triple("tron", "TRX", "TRON"), Triple("chainlink", "LINK", "Chainlink"),
    Triple("avalanche-2", "AVAX", "Avalanche"), Triple("polkadot", "DOT", "Polkadot"), Triple("litecoin", "LTC", "Litecoin"),
    Triple("sui", "SUI", "Sui"), Triple("shiba-inu", "SHIB", "Shiba Inu"), Triple("pepe", "PEPE", "Pepe"),
)

/** Precio de una cripto en dólares (así se cotizan): "$61.234,50", con más decimales si vale menos de 1 $. */
fun usd(v: Double): String = "$" + when {
    v >= 1 -> String.format(Locale.forLanguageTag("es-ES"), "%,.2f", v)
    v >= 0.01 -> String.format(Locale.forLanguageTag("es-ES"), "%.4f", v)
    else -> String.format(Locale.forLanguageTag("es-ES"), "%.8f", v).trimEnd('0')
}
fun usdSigned(v: Double) = (if (v >= 0) "+" else "−") + usd(kotlin.math.abs(v))

/** Una fila del mercado: id (ticker o id de CoinGecko), etiqueta corta, nombre, precio, cambio y minigráfico. */
private data class MarketRow(val id: String, val label: String, val name: String, val price: Double?, val change: Double?, val spark: List<Double>)

/** Una acción ("w:TICKER") o cripto ("wc:id") que miras sin tenerla. */
private fun watchPosition(key: String): Position? {
    if (key.startsWith("wc:")) {
        val parts = key.removePrefix("wc:").split('|')
        val id = parts[0]
        val (_, sym, name) = CRYPTO_MARKET.firstOrNull { it.first == id }
            ?: Triple(id, parts.getOrNull(1) ?: id.uppercase(), parts.getOrNull(2) ?: id.replaceFirstChar { it.uppercase() })
        return Position(key, true, id, sym, name, 0.0, 0.0, 0.0, Editing.CryptoForm(com.caudal.finanzas.data.CryptoHolding(coinId = id, symbol = sym, name = name, amount = 0.0, avgBuyPrice = 0.0)))
    }
    if (!key.startsWith("w:")) return null
    val t = key.removePrefix("w:")
    val name = MARKET.firstOrNull { it.first == t }?.second ?: t
    return Position(key, false, t, t, name, 0.0, 0.0, 0.0, Editing.StockForm(com.caudal.finanzas.data.StockHolding(ticker = t, name = name, shares = 0.0, avgBuyPrice = 0.0)))
}

/** Una posición, cripto o acción, con lo necesario para pintarla. */
private data class Position(
    val key: String, val isCrypto: Boolean, val apiId: String, val symbol: String, val name: String,
    val qty: Double, val avg: Double, val price: Double, val editing: Editing,
) {
    val value get() = qty * price
    val cost get() = qty * avg
    val pnl get() = value - cost
}

@Composable
private fun positionsOf(vm: FinanceViewModel, kind: InvestKind?): List<Position> {
    val crypto by vm.crypto.collectAsStateWithLifecycle()
    val stocks by vm.stocks.collectAsStateWithLifecycle()
    val c = crypto.map { Position("c:${it.id}", true, it.coinId, it.symbol, it.name, it.amount, it.avgBuyPrice, it.price, Editing.CryptoForm(it)) }
    val s = stocks.map { Position("s:${it.id}", false, it.ticker, it.ticker, it.name.ifBlank { it.ticker }, it.shares, it.avgBuyPrice, it.price, Editing.StockForm(it)) }
    return when (kind) { InvestKind.CRYPTO -> c; InvestKind.STOCKS -> s; null -> c + s }.sortedByDescending { it.value }
}

/** Minigráfico sin ejes (para tarjetas y filas). */
@Composable
private fun Spark(values: List<Double>, color: Color, modifier: Modifier, fill: Boolean = false) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min(); val max = values.max(); val range = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - min) / range)).toFloat()
        val step = size.width / (values.size - 1)
        val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * step, y(v)) } }
        if (fill) drawPath(Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ChangeChip(pct: Double, dark: Boolean = false) {
    val up = pct >= 0
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (dark) OnPastel.copy(alpha = 0.10f) else gainColor(pct).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, Modifier.size(14.dp), tint = if (dark) OnPastel else gainColor(pct))
        Spacer(Modifier.width(4.dp))
        Text(signedPct(pct), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (dark) OnPastel else gainColor(pct))
    }
}

/** Pestaña Inversión: valor actual, favoritas en tarjetas de color y la lista de activos. Sin comprar ni vender. */
@Composable
fun InvestmentsScreen(vm: FinanceViewModel, args: ScreenArgs, kind: InvestKind, dayChanges: Map<String, Double>, onKind: (InvestKind) -> Unit, onOpenHolding: (String) -> Unit = {}) {
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val positions = positionsOf(vm, kind)
    LaunchedEffect(kind) { vm.refreshIfStale() }
    // Cripto: los minigráficos salen de una sola llamada al mercado (CoinGecko gratis limita las consultas).
    LaunchedEffect(kind, positions.map { it.key }) { positions.filter { !it.isCrypto }.forEach { vm.loadHistory(it.symbol, false, it.apiId, 1) } }
    LaunchedEffect(kind) {
        if (kind == InvestKind.STOCKS) MARKET.forEach { vm.loadHistory(it.first, false, it.first, 1) }
        else vm.loadMarkets((CRYPTO_MARKET.map { it.first } + positions.filter { it.isCrypto }.map { it.apiId }).distinct())
    }
    val recent by vm.recentTickers.collectAsStateWithLifecycle()
    val markets by vm.cryptoMarkets.collectAsStateWithLifecycle()
    val marketRows = if (kind == InvestKind.STOCKS) MARKET.map { (t, n) ->
        val h = history["$t:1"].orEmpty()
        MarketRow(t, t.substringBefore('.'), n, h.lastOrNull(), h.takeIf { it.size >= 2 }?.let { (it.last() / it.first() - 1) * 100 }, h)
    } else CRYPTO_MARKET.map { (id, sym, n) ->
        val m = markets.firstOrNull { it.id == id }
        MarketRow(id, sym, n, m?.price, m?.change24h, m?.spark.orEmpty())
    }
    val marketNote = if (kind == InvestKind.STOCKS) "Precios de Yahoo Finance, con retraso. Solo para mirar: aquí no se compra ni se vende."
        else "Precio de cada moneda en dólares (CoinGecko). Lo que tienes tú, en euros. Solo para mirar: aquí no se compra ni se vende."
    val marketFmt: (Double) -> String = if (kind == InvestKind.STOCKS) money::format else ::usd
    fun coinUsd(p: Position) = if (p.isCrypto) markets.firstOrNull { it.id == p.apiId }?.price else null
    val openMarket: (String) -> Unit = { id ->
        vm.markViewed(id)
        onOpenHolding(
            if (kind == InvestKind.STOCKS) positions.firstOrNull { !it.isCrypto && it.symbol.equals(id, true) }?.key ?: "w:$id"
            else positions.firstOrNull { it.isCrypto && it.apiId == id }?.key ?: "wc:$id",
        )
    }
    var buzzOpen by rememberSaveable { mutableStateOf(false) }

    val value = positions.sumOf { it.value }
    val cost = positions.sumOf { it.cost }
    val series = positions.map { p -> history["${p.symbol}:1"].orEmpty().map { it * p.qty } }
    val portfolio = if (series.isNotEmpty() && series.all { it.size >= 2 }) {
        val n = series.minOf { it.size }
        (0 until n).map { i -> series.sumOf { s -> s[s.size - n + i] } }
    } else emptyList()
    fun monthChange(p: Position) = history["${p.symbol}:1"]?.takeIf { it.size >= 2 }?.let { (it.last() / it.first() - 1) * 100 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = args.contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle("Inversiones") { args.headerActions() } }
        item { Segmented(InvestKind.entries, kind, { it.label }, onKind) }

        // ---------- Valor actual ----------
        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Valor actual", Modifier.weight(1f), fontSize = 20.sp, color = P.muted)
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = P.gain)
                    else RoundIconButton(Icons.Filled.Refresh, "Actualizar precios", onClick = { if (kind == InvestKind.CRYPTO) vm.refreshCrypto() else vm.refreshStocks() })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val text = money.format(value)
                    val cut = text.lastIndexOf(',')
                    Text(if (cut > 0) text.substring(0, cut) else text, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = P.ink, letterSpacing = (-1).sp)
                    if (cut > 0) Text(text.substring(cut), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = P.muted, letterSpacing = (-1).sp)
                    Spacer(Modifier.width(10.dp))
                    if (portfolio.size >= 2) Spark(portfolio, if (portfolio.last() >= portfolio.first()) P.gain else P.loss, Modifier.width(56.dp).height(28.dp))
                }
                if (cost > 0) Text(
                    "${money.signed(value - cost)} (${signedPct((value - cost) / cost * 100)}) desde que compraste",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = gainColor(value - cost),
                )
            }
        }

        if (positions.isEmpty()) {
            marketItems(marketRows, recent, openMarket, marketNote, marketFmt)
            return@LazyColumn
            item {
                if (kind == InvestKind.CRYPTO) EmptyState(Icons.Filled.CurrencyBitcoin, "Sin criptomonedas", "Pulsa + y pon cuánto dinero tienes en cada una: la cantidad se calcula sola con el precio en vivo.")
                else EmptyState(Icons.AutoMirrored.Filled.TrendingUp, "Sin acciones", "Pulsa + y añade tus acciones o ETF con su ticker (AAPL, SAN.MC, VWCE.DE…).")
            }
            return@LazyColumn
        }

        // ---------- ¿De qué se está hablando? (solo cripto) ----------
        if (kind == InvestKind.CRYPTO) item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(OnPastel).clickable { buzzOpen = true; vm.loadBuzz() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📰", fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("¿De qué se está hablando?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Tendencias de CoinGecko y lo más votado en Reddit", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                }
                Text("Ver", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Pastels[1])
            }
        }

        // ---------- Favoritas: tarjetas de color ----------
        item { Text("Favoritas", fontSize = 20.sp, color = P.muted, modifier = Modifier.padding(start = 4.dp)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                positions.take(5).forEachIndexed { i, p ->
                    val bg = Pastels[i % Pastels.size]
                    Column(Modifier.width(160.dp).clip(RoundedCornerShape(24.dp)).background(bg).clickable { onOpenHolding(p.key) }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Initials(p.symbol, symbolColor(p.symbol), 34.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnPastel, maxLines = 1)
                                Text(p.symbol, fontSize = 12.sp, color = OnPastel.copy(alpha = 0.6f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Spark(history["${p.symbol}:1"].orEmpty(), Color.White, Modifier.fillMaxWidth().height(56.dp))
                        Spacer(Modifier.height(8.dp))
                        if (p.isCrypto) Text("Lo tuyo", fontSize = 11.sp, color = OnPastel.copy(alpha = 0.6f))
                        Text(money.format(p.value), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnPastel)
                        coinUsd(p)?.let { Text("1 ${p.symbol} = ${usd(it)}", fontSize = 12.sp, color = OnPastel.copy(alpha = 0.7f), maxLines = 1) }
                        Spacer(Modifier.height(4.dp))
                        (dayChanges[p.symbol] ?: monthChange(p))?.let { ChangeChip(it, dark = true) }
                    }
                }
            }
        }

        // ---------- Activos ----------
        item { Text("Activos", fontSize = 20.sp, color = P.muted, modifier = Modifier.padding(start = 4.dp)) }
        positions.forEach { p ->
            item(key = p.key) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card).border(1.dp, P.cardBorder, RoundedCornerShape(20.dp))
                        .clickable { onOpenHolding(p.key) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Initials(p.symbol, symbolColor(p.symbol), 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 1)
                        Text(p.symbol + (coinUsd(p)?.let { " · precio ${usd(it)}" } ?: ""), fontSize = 13.sp, color = P.muted, maxLines = 1)
                    }
                    val h = history["${p.symbol}:1"].orEmpty()
                    if (h.size >= 2) Spark(h, if (h.last() >= h.first()) P.gain else P.loss, Modifier.width(64.dp).height(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        if (p.isCrypto) Text("lo tuyo", fontSize = 11.sp, color = P.muted)
                        Text(money.format(p.value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                        if (p.cost > 0) Text(signedPct(p.pnl / p.cost * 100), fontSize = 13.sp, color = gainColor(p.pnl))
                    }
                }
            }
        }
        marketItems(marketRows, recent, openMarket, marketNote, marketFmt)
    }
    // (las acciones del mercado van al final de la lista cuando ya tienes acciones)
    if (buzzOpen) BuzzSheet(vm, onOpenCoin = { b ->
        val id = b.coinId ?: CRYPTO_MARKET.firstOrNull { it.second.equals(b.symbol, true) }?.first
        if (id != null) {
            buzzOpen = false
            vm.markViewed(id)
            onOpenHolding(positions.firstOrNull { it.isCrypto && it.apiId == id }?.key ?: "wc:$id|${b.symbol}|${b.name}")
        }
    }) { buzzOpen = false }
}

/** Ficha de una inversión: precio, gráfico de línea o velas, máximos y mínimos y tu posición. */
@Composable
fun HoldingDetailScreen(vm: FinanceViewModel, key: String, dayChanges: Map<String, Double>, onEdit: (Editing) -> Unit, onBack: () -> Unit) {
    val money = LocalMoney.current
    val history by vm.history.collectAsStateWithLifecycle()
    val p = (positionsOf(vm, null).firstOrNull { it.key == key } ?: watchPosition(key))?.let { q ->
        if (q.price > 0) q else q.copy(price = history["${q.symbol}:1"]?.lastOrNull() ?: history.entries.firstOrNull { it.key.startsWith(q.symbol + ":") }?.value?.lastOrNull() ?: 0.0)
    }
    if (p == null) { LaunchedEffect(Unit) { onBack() }; return }
    // Cripto: el precio de la moneda se ve en dólares; tu posición, en euros.
    val cur = if (p.isCrypto) "usd" else null
    val sfx = cur?.let { ":$it" } ?: ""
    val fmtPrice: (Double) -> String = if (p.isCrypto) ::usd else money::format
    val fmtSigned: (Double) -> String = if (p.isCrypto) ::usdSigned else money::signed
    LaunchedEffect(p.symbol) { vm.loadHistory(p.symbol, p.isCrypto, p.apiId, 1); if (cur != null) vm.loadHistory(p.symbol, true, p.apiId, 1, cur) }
    val times by vm.historyTimes.collectAsStateWithLifecycle()
    val ohlc by vm.ohlc.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(Range.MONTH) }
    // Punto que estás señalando al mantener pulsado el gráfico (null = el precio de ahora).
    var scrub by androidx.compose.runtime.remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(p.apiId) { if (p.isCrypto) vm.loadStats(p.apiId, cur) }
    var candles by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(range, candles) {
        if (candles) vm.loadOhlc(p.symbol, p.isCrypto, p.apiId, range.days, cur) else vm.loadHistory(p.symbol, p.isCrypto, p.apiId, range.days, cur)
    }
    val line = history["${p.symbol}:${range.days}$sfx"]
    val bars = ohlc["${p.symbol}:${range.days}$sfx"]
    val priceNow = if (p.isCrypto) history["${p.symbol}:1$sfx"]?.lastOrNull() ?: line?.lastOrNull() ?: 0.0 else p.price
    val values = if (candles) bars?.flatMap { listOf(it.high, it.low) } else line
    val change = line?.takeIf { it.size >= 2 }?.let { it.last() - it.first() } ?: bars?.takeIf { it.isNotEmpty() }?.let { it.last().close - it.first().open }
    val base = line?.firstOrNull() ?: bars?.firstOrNull()?.open
    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tint = Pastels[Math.floorMod(p.symbol.hashCode(), Pastels.size)]

    // Desliza hacia abajo desde arriba del todo para volver a cargar precio y gráfico.
    var reloading by androidx.compose.runtime.remember { mutableStateOf(false) }
    val reloadScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = reloading,
        onRefresh = {
            reloadScope.launch {
                reloading = true
                vm.reloadAsset(p.symbol, p.apiId, p.isCrypto, range.days, candles, cur)
                kotlinx.coroutines.delay(1200)
                reloading = false
            }
        },
        modifier = Modifier.fillMaxSize().background(P.bg),
    ) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = nav + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Initials(p.symbol, symbolColor(p.symbol), 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Text(p.symbol, fontSize = 13.sp, color = P.muted)
                }
                RoundIconButton(Icons.Filled.Edit, "Editar ${p.name}", onClick = { onEdit(p.editing) })
            }
        }
        // ---------- Precio y gráfico, sobre su color ----------
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(tint).padding(18.dp)) {
                val scrubPrice = scrub?.let { line?.getOrNull(it) }
                val scrubTime = scrub?.let { times["${p.symbol}:${range.days}$sfx"]?.getOrNull(it) }
                Text(
                    if (scrubPrice != null && scrubTime != null && scrubTime > 0) "Precio el " + java.time.Instant.ofEpochMilli(scrubTime).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern(if (range.days <= 7) "d MMM, HH:mm" else "d MMM yyyy", es))
                    else "Precio de ${p.name}",
                    fontSize = 15.sp, color = OnPastel.copy(alpha = 0.7f),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtPrice(scrubPrice ?: priceNow), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = OnPastel)
                    Spacer(Modifier.width(10.dp))
                    if (change != null && base != null && base > 0) {
                        Text(
                            "${if (change >= 0) "↗" else "↘"} ${fmtSigned(change)} (${signedPct(change / base * 100)})",
                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OnPastel, modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Toggle("Línea", !candles) { candles = false }
                    Toggle("Velas", candles) { candles = true }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    values == null -> Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = OnPastel) }
                    values.size < 2 -> Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) { Text("Sin datos ahora mismo (¿sin conexión?)", color = OnPastel.copy(alpha = 0.7f)) }
                    candles && bars != null -> PriceChart(values, priceNow, Modifier.fillMaxWidth().height(230.dp)) { area -> drawCandles(area, bars) }
                    else -> PriceChart(
                        values, priceNow,
                        Modifier.fillMaxWidth().height(230.dp).pointerInput(values) {
                            // Mantén pulsado y desliza: el precio de arriba pasa a ser el de ese momento.
                            fun index(x: Float) = ((x - 54.dp.toPx()) / (size.width - 54.dp.toPx()) * (values.size - 1)).toInt().coerceIn(0, values.size - 1)
                            detectDragGesturesAfterLongPress(
                                onDragStart = { scrub = index(it.x) },
                                onDrag = { change, _ -> scrub = index(change.position.x) },
                                onDragEnd = { scrub = null }, onDragCancel = { scrub = null },
                            )
                        },
                    ) { area ->
                        drawLine(area, values)
                        scrub?.let { i ->
                            val x = area.left + (area.right - area.left) * i / (values.size - 1)
                            drawLine(OnPastel.copy(alpha = 0.6f), Offset(x, area.top), Offset(x, area.bottom), 1.5.dp.toPx())
                            drawCircle(Color.White, 6.dp.toPx(), Offset(x, area.y(values[i])))
                            drawCircle(OnPastel, 3.dp.toPx(), Offset(x, area.y(values[i])))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.5f)).padding(3.dp)) {
                    Range.entries.forEach { r ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (r == range) OnPastel else Color.Transparent).clickable { range = r }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(r.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (r == range) Color.White else OnPastel) }
                    }
                }
            }
        }
        // ---------- Máximo, mínimo y cambio ----------
        item {
            val hi = values?.maxOrNull(); val lo = values?.minOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Máximo", hi?.let(fmtPrice) ?: "—", Modifier.weight(1f))
                Stat("Mínimo", lo?.let(fmtPrice) ?: "—", Modifier.weight(1f))
                Stat("24 h", dayChanges[p.symbol]?.let { signedPct(it) } ?: "—", Modifier.weight(1f), dayChanges[p.symbol]?.let { gainColor(it) })
            }
        }
        // ---------- Estadísticas de CoinGecko ----------
        if (p.isCrypto) item {
            val st = stats[p.apiId + sfx]
            MCard {
                Text("Estadísticas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("Datos de CoinGecko, en dólares", fontSize = 12.sp, color = P.muted)
                Spacer(Modifier.height(8.dp))
                if (st == null) Text("Cargando… (necesita conexión)", fontSize = 14.sp, color = P.muted)
                else {
                    fun big(v: Double?) = v?.let {
                        when { it >= 1e12 -> "$" + String.format(es, "%.2f bill.", it / 1e12); it >= 1e9 -> "$" + String.format(es, "%.2f mil M", it / 1e9); it >= 1e6 -> "$" + String.format(es, "%.1f M", it / 1e6); else -> usd(it) }
                    } ?: "—"
                    fun units(v: Double?) = v?.let { if (it >= 1e9) String.format(es, "%.2f mil M", it / 1e9) else if (it >= 1e6) String.format(es, "%.1f M", it / 1e6) else String.format(es, "%,.0f", it) } ?: "—"
                    PosLine("Puesto por capitalización", st.rank?.let { "#$it" } ?: "—")
                    PosLine("Capitalización", big(st.marketCap))
                    PosLine("Volumen 24 h", big(st.volume24h))
                    PosLine("Máximo / mínimo 24 h", "${st.high24h?.let(::usd) ?: "—"} / ${st.low24h?.let(::usd) ?: "—"}")
                    PosLine("Máximo histórico", (st.ath?.let(::usd) ?: "—") + (st.athChangePct?.let { " (${signedPct(it)})" } ?: ""))
                    PosLine("Cambio 7 días", st.change7d?.let { signedPct(it) } ?: "—", st.change7d?.let { gainColor(it) })
                    PosLine("Cambio 30 días", st.change30d?.let { signedPct(it) } ?: "—", st.change30d?.let { gainColor(it) })
                    PosLine("Cambio 1 año", st.change1y?.let { signedPct(it) } ?: "—", st.change1y?.let { gainColor(it) })
                    PosLine("En circulación", units(st.circulating) + (st.maxSupply?.let { " de ${units(it)}" } ?: ""))
                }
            }
        }

        // ---------- Por qué se habla de ella y dónde comprarla (cripto) ----------
        if (p.isCrypto) item {
            val buzzState by vm.buzz.collectAsStateWithLifecycle()
            val b = buzzState.second?.items?.firstOrNull { it.coinId == p.apiId || it.symbol.equals(p.symbol, true) }
            if (b != null) MCard {
                Text("Por qué se habla de ella", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val c = when (b.tone) { "positivo" -> P.gain; "negativo" -> P.loss; else -> P.muted }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("Tono ${b.tone}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c) }
                    if (b.trending) Box(Modifier.clip(RoundedCornerShape(50)).background(com.caudal.finanzas.ui.SeriesColors[1].copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("En tendencia", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = com.caudal.finanzas.ui.SeriesColors[1]) }
                }
                Text(reason(b), fontSize = 14.sp, color = P.ink, modifier = Modifier.padding(top = 8.dp))
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                b.posts.take(4).forEach { h ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { runCatching { uri.openUri(h.url) } }.padding(vertical = 6.dp)) {
                        Text("“${h.title}”", fontSize = 14.sp, color = P.ink, maxLines = 3)
                        Text("▲ ${h.score} · ${h.comments} comentarios · Reddit", fontSize = 12.sp, color = P.muted)
                    }
                }
                Text("Resume la conversación; no es una recomendación de inversión.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (p.isCrypto) item {
            LaunchedEffect(p.apiId) { vm.loadExchanges(p.apiId) }
            val ex by vm.exchanges.collectAsStateWithLifecycle()
            val list = ex[p.apiId]
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            MCard {
                Text("Dónde se puede comprar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("Exchanges con más volumen, según CoinGecko", fontSize = 12.sp, color = P.muted)
                Spacer(Modifier.height(6.dp))
                when {
                    list == null -> Text("Cargando… (necesita conexión)", fontSize = 14.sp, color = P.muted)
                    list.isEmpty() -> Text("No he encontrado dónde se negocia ahora mismo.", fontSize = 14.sp, color = P.muted)
                    else -> list.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = m.url != null) { m.url?.let { runCatching { uri.openUri(it) } } }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Initials(m.exchange.take(2), symbolColor(m.exchange), 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.exchange, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink)
                                Text(m.pair + if (m.trusted) " · confianza alta" else "", fontSize = 12.sp, color = if (m.trusted) P.gain else P.muted)
                            }
                            if (m.url != null) Text("Abrir ›", fontSize = 13.sp, color = P.gain)
                        }
                    }
                }
                Text(
                    "Antes de abrir cuenta, comprueba que el exchange puede operar en España (registro de la CNMV / MiCA). Esta app no compra ni vende nada.",
                    fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---------- Tu posición ----------
        if (p.qty <= 0.0) item {
            MCard {
                Text("No la tienes", fontSize = 14.sp, color = P.muted)
                Text("Solo estás mirando el precio. Si la compras en tu bróker, añádela para seguirla aquí.", fontSize = 15.sp, color = P.ink, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                com.caudal.finanzas.ui.BrandButton(if (p.isCrypto) "Añadir a mis criptos" else "Añadir a mis acciones", { onEdit(p.editing) }, Modifier.fillMaxWidth())
                Text("Desde aquí no se compra ni se vende.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (p.qty > 0) item {
            MCard {
                Text(if (p.isCrypto) "Lo tuyo (en euros)" else "Tu posición", fontSize = 14.sp, color = P.muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(money.format(p.value), Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    if (p.cost > 0) ChangeChip(p.pnl / p.cost * 100)
                }
                Spacer(Modifier.height(10.dp))
                PosLine("Cantidad", "${quantity(p.qty)} ${p.symbol}")
                if (p.cost > 0) {
                    PosLine("Invertido", money.format(p.cost))
                    PosLine("Precio medio de compra", money.format(p.avg))
                    PosLine(if (p.pnl >= 0) "Ganas" else "Pierdes", money.format(kotlin.math.abs(p.pnl)), gainColor(p.pnl))
                } else Text("Pon lo que metiste (Editar) para ver cuánto ganas o pierdes.", fontSize = 13.sp, color = P.muted, modifier = Modifier.padding(top = 4.dp))
                Text("Solo para seguir tu inversión: desde aquí no se compra ni se vende.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
    }
}

@Composable
private fun Toggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) OnPastel else Color.White.copy(alpha = 0.5f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) Color.White else OnPastel) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color? = null) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(P.card).border(1.dp, P.cardBorder, RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = P.muted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color ?: P.ink, maxLines = 1)
    }
}

@Composable
private fun PosLine(label: String, value: String, color: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = P.muted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color ?: P.ink)
    }
}

/** Área del gráfico ya escalada: mínimo, máximo y dónde pintar. */
private class ChartArea(val min: Double, val max: Double, val left: Float, val right: Float, val top: Float, val bottom: Float) {
    fun y(v: Double) = bottom - (bottom - top) * ((v - min) / (max - min)).toFloat()
}

/** Ejes con precios a la izquierda y una línea discontinua con el precio actual; el contenido lo pinta [content]. */
@Composable
private fun PriceChart(values: List<Double>, current: Double, modifier: Modifier, content: androidx.compose.ui.graphics.drawscope.DrawScope.(ChartArea) -> Unit) {
    val min0 = minOf(values.min(), current); val max0 = maxOf(values.max(), current)
    val pad = ((max0 - min0).takeIf { it > 0 } ?: max0 * 0.02) * 0.08
    val min = min0 - pad; val max = max0 + pad
    val labels = (0..4).map { min + (max - min) * it / 4 }
    Row(modifier) {
        Column(Modifier.width(54.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            labels.reversed().forEach { Text(compact(it), fontSize = 10.sp, color = OnPastel.copy(alpha = 0.7f)) }
        }
        Canvas(Modifier.weight(1f).fillMaxSize()) {
            val area = ChartArea(min, max, 0f, size.width, 6.dp.toPx(), size.height - 6.dp.toPx())
            labels.forEach { v -> drawLine(OnPastel.copy(alpha = 0.08f), Offset(0f, area.y(v)), Offset(size.width, area.y(v)), 1.dp.toPx()) }
            content(area)
            val yc = area.y(current)
            drawLine(OnPastel.copy(alpha = 0.7f), Offset(0f, yc), Offset(size.width, yc), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
    }
}

private fun compact(v: Double): String = when {
    v >= 10_000 -> String.format(es, "%,.0f", v)
    v >= 100 -> String.format(es, "%.0f", v)
    v >= 1 -> String.format(es, "%.2f", v)
    else -> String.format(es, "%.4f", v)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLine(area: ChartArea, values: List<Double>) {
    val step = (area.right - area.left) / (values.size - 1)
    val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(area.left, area.y(v)) else lineTo(area.left + i * step, area.y(v)) } }
    drawPath(Path().apply { addPath(path); lineTo(area.right, area.bottom); lineTo(area.left, area.bottom); close() }, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)))
    drawPath(path, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCandles(area: ChartArea, bars: List<Candle>) {
    val slot = (area.right - area.left) / bars.size
    val body = (slot * 0.6f).coerceAtLeast(2f)
    bars.forEachIndexed { i, c ->
        val x = area.left + slot * i + slot / 2
        val color = if (c.close >= c.open) Color(0xFF6F5BEA) else Color(0xFFD08A57)
        drawLine(color, Offset(x, area.y(c.high)), Offset(x, area.y(c.low)), 1.5.dp.toPx())
        val top = area.y(maxOf(c.open, c.close)); val bottom = area.y(minOf(c.open, c.close))
        drawRect(color, Offset(x - body / 2, top), Size(body, (bottom - top).coerceAtLeast(2f)))
    }
}

/** "¿De qué se está hablando?": monedas con más conversación y su tono, y los titulares más votados. */
@Composable
private fun BuzzSheet(vm: FinanceViewModel, onOpenCoin: (com.caudal.finanzas.domain.BuzzItem) -> Unit, onDismiss: () -> Unit) {
    val state by vm.buzz.collectAsStateWithLifecycle()
    val (loading, buzz) = state
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    com.caudal.finanzas.ui.FormSheet("¿De qué se está hablando?", onDismiss, primary = null) {
        Text("Lo más buscado en CoinGecko y los hilos más votados de r/CryptoCurrency y r/CryptoMarkets, ahora mismo.", fontSize = 14.sp, color = P.muted)
        when {
            loading && buzz == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = P.gain); Spacer(Modifier.width(10.dp))
                Text("Leyendo CoinGecko y Reddit…", fontSize = 14.sp, color = P.ink)
            }
            buzz == null -> Text("No he podido conectar. Inténtalo de nuevo con internet.", fontSize = 14.sp, color = P.muted)
            else -> {
                buzz.featured?.let { f ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pastels[1]).clickable { onOpenCoin(f) }.padding(16.dp)) {
                        Text("MÁS RUIDO POSITIVO", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = OnPastel.copy(alpha = 0.7f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Initials(f.symbol, symbolColor(f.symbol), 36.dp); Spacer(Modifier.width(10.dp))
                            Text("${f.name} (${f.symbol})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnPastel)
                        }
                        Text(reason(f), fontSize = 14.sp, color = OnPastel, modifier = Modifier.padding(top = 6.dp))
                        Text("Ver gráfico, por qué se habla y dónde comprarla ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnPastel, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Text("Monedas de las que se habla", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                buzz.items.take(8).forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenCoin(b) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Initials(b.symbol, symbolColor(b.symbol), 36.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${b.name} · ${b.symbol}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
                            Text(reason(b), fontSize = 12.sp, color = P.muted, maxLines = 2)
                        }
                        val c = when (b.tone) { "positivo" -> P.gain; "negativo" -> P.loss; else -> P.muted }
                        Text(b.tone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c)
                    }
                }
                if (buzz.headlines.isNotEmpty()) {
                    Text("Titulares más votados", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    buzz.headlines.forEach { h ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { runCatching { uri.openUri(h.url) } }.padding(vertical = 6.dp)) {
                            Text(h.title, fontSize = 14.sp, color = P.ink, maxLines = 3)
                            Text("▲ ${h.score} · ${h.comments} comentarios · Reddit", fontSize = 12.sp, color = P.muted)
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = { vm.loadBuzz(force = true) }) { Text(if (loading) "Actualizando…" else "Actualizar", color = P.gain) }
            }
        }
        Text(
            "Esto resume de qué se habla; no es una recomendación de inversión. Que una moneda tenga buen ambiente no significa que vaya a subir: el hype también hunde precios.",
            fontSize = 12.sp, color = P.muted,
        )
    }
}

private fun reason(b: com.caudal.finanzas.domain.BuzzItem): String = listOfNotNull(
    if (b.mentions > 0) "${b.mentions} ${if (b.mentions == 1) "hilo" else "hilos"} en Reddit (${b.votes} votos), tono ${b.tone}" else null,
    if (b.trending) "en tendencia en CoinGecko" else null,
    b.change24h?.let { "${signedPct(it)} en 24 h" },
).joinToString(" · ")

/** Vistas recientemente (tarjetas) y las más populares (lista), con precio y cambio del día. */
private fun androidx.compose.foundation.lazy.LazyListScope.marketItems(
    rows: List<MarketRow>,
    recent: List<String>,
    onOpen: (String) -> Unit,
    note: String,
    fmt: (Double) -> String,
) {
    val seen = recent.filter { r -> rows.any { it.id == r } }
    val cards = (seen + rows.map { it.id }).distinct().take(6).mapNotNull { id -> rows.firstOrNull { it.id == id } }
    val up = Color(0xFF3B82F6)
    item { Text(if (seen.isEmpty()) "Para echar un vistazo" else "Vistas recientemente", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.forEach { r ->
                Column(
                    Modifier.width(158.dp).clip(RoundedCornerShape(24.dp)).background(P.card).border(1.dp, P.cardBorder, RoundedCornerShape(24.dp))
                        .clickable { onOpen(r.id) }.padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Initials(r.label, symbolColor(r.label), 38.dp)
                        Spacer(Modifier.width(8.dp))
                        if (r.spark.size >= 2) Spark(r.spark, if (r.change != null && r.change < 0) P.loss else up, Modifier.weight(1f).height(30.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(r.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Text(r.name, fontSize = 13.sp, color = P.muted, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.price?.let(fmt) ?: "…", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 1)
                        r.change?.let { Text((if (it >= 0) "▲ " else "▼ ") + signedPct(kotlin.math.abs(it)).removePrefix("+"), fontSize = 12.sp, color = if (it >= 0) up else P.loss) }
                    }
                }
            }
        }
    }
    item { Text("Las más populares", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
    item {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.card).border(1.dp, P.cardBorder, RoundedCornerShape(24.dp)).padding(vertical = 6.dp)) {
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Initials(r.label, symbolColor(r.label), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 1)
                        Text(r.label, fontSize = 13.sp, color = P.muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(r.price?.let(fmt) ?: "…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                        r.change?.let { Text((if (it >= 0) "▲ " else "▼ ") + signedPct(kotlin.math.abs(it)).removePrefix("+"), fontSize = 13.sp, color = if (it >= 0) up else P.loss) }
                    }
                }
            }
        }
    }
    item { Text(note, fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(horizontal = 4.dp)) }
}
