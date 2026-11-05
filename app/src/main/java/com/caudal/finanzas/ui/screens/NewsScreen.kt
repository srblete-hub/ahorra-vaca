package com.caudal.finanzas.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.NewsItem
import com.caudal.finanzas.domain.NewsImpact
import com.caudal.finanzas.domain.NewsImpactAnalyzer
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.P

private val TOPIC_COLORS = mapOf(
    "Cripto" to listOf(Color(0xFF1E3A8A), Color(0xFF0B1022)),
    "Bolsa" to listOf(Color(0xFF0F3D2E), Color(0xFF07130F)),
    "Empresas" to listOf(Color(0xFF3B1F5C), Color(0xFF110A1C)),
    "Economía" to listOf(Color(0xFF1F2937), Color(0xFF0A0D12)),
)

private fun ago(ms: Long): String {
    if (ms <= 0) return "hoy"
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when { min < 1 -> "ahora"; min < 60 -> "hace $min min"; min < 24 * 60 -> "hace ${min / 60} h"; else -> "ayer" }
}

/** Noticias de hoy, una por pantalla, deslizando hacia arriba (como TikTok). Gratis: RSS de Google Noticias. */
@Composable
fun NewsScreen(vm: FinanceViewModel, onBack: () -> Unit) {
    val state by vm.news.collectAsStateWithLifecycle()
    val planning by vm.planning.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    LaunchedEffect(Unit) { vm.loadNews(); vm.loadMarkets(CRYPTO_MARKET.map { it.first }) }
    val markets by vm.cryptoMarkets.collectAsStateWithLifecycle()
    val (loading, all) = state
    // Las que ya viste (hoy o ayer) no se repiten al volver a entrar.
    val seenAtOpen = remember { vm.seenNews() }
    val items = all?.filter { it.url.hashCode().toString() !in seenAtOpen }
    val top = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var impactOf by remember { mutableStateOf<Pair<NewsItem, NewsImpact>?>(null) }
    val uri = LocalUriHandler.current
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0D12))) {
        when {
            items == null && loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Text("Buscando las noticias de hoy…", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 14.dp))
            }
            items == null -> EndPage("No he podido cargar noticias", "Comprueba la conexión y vuelve a intentarlo.")
            items.isEmpty() -> EndPage("Te has quedado sin noticias", if (all.isNotEmpty()) "Ya has visto todas las de hoy. Vuelve más tarde." else "Hoy aún no hay noticias.")
            else -> {
                val pager = rememberPagerState { items.size + 1 }
                LaunchedEffect(pager.currentPage) { items.getOrNull(pager.currentPage)?.let { vm.markNewsSeen(it.url) } }
                VerticalPager(pager, Modifier.fillMaxSize()) { page ->
                    if (page == items.size) EndPage("Te has quedado sin noticias", "Has visto todas las de hoy. Vuelve mañana.")
                    else {
                        val n = items[page]
                        val impact = remember(n, planning) { planning?.data?.let { NewsImpactAnalyzer.analyze(n.title, n.summary, it, money::format) } }
                        // Foto de la noticia; si no trae, la de su página; si tampoco, un gráfico.
                        val img by androidx.compose.runtime.produceState(n.imageUrl, n) { if (value == null) value = com.caudal.finanzas.data.NewsService.pageImage(n.url) }
                        val coin = remember(n) { NewsImpactAnalyzer.coinsIn(n.title + " " + n.summary).firstOrNull() }
                        val spark = coin?.let { c -> markets.firstOrNull { it.symbol.equals(c, true) } }
                        NewsPage(
                            n, impact, top, bottom, img, spark,
                            onImpact = { impact?.let { impactOf = n to it } },
                            onOpen = { runCatching { uri.openUri(n.url) } },
                            onShare = {
                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${n.title}\n${n.url}") }
                                context.startActivity(Intent.createChooser(send, "Compartir noticia"))
                            },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = top + 10.dp, start = 18.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Noticias de hoy", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Cerrar", tint = Color.White)
            }
        }
    }
    impactOf?.let { (n, i) -> ImpactSheet(n, i) { impactOf = null } }
}

@Composable
private fun NewsPage(
    n: NewsItem, impact: NewsImpact?, top: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp,
    image: String?, coin: com.caudal.finanzas.data.MarketCoin?,
    onImpact: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit,
) {
    val colors = TOPIC_COLORS[n.topic] ?: TOPIC_COLORS.getValue("Economía")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        // ---------- Foto o gráfico arriba ----------
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.52f)) {
            val bmp = netImage(image)
            when {
                bmp != null -> androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                coin != null && coin.spark.size >= 2 -> CoinGraphic(coin, top)
                else -> TopicGraphic(n.topic, top)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, colors[0]), startY = 0f)))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 22.dp, end = 96.dp, bottom = bottom + 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(n.topic.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White)
                }
                if (impact?.relevant == true) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFD60A)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("TE AFECTA", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.Black)
                    }
                }
            }
            Text(n.title, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 14.dp))
            if (n.summary.isNotBlank()) Text(n.summary, fontSize = 16.sp, lineHeight = 22.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 5, modifier = Modifier.padding(top = 10.dp))
            Text("${n.source} · ${ago(n.epochMs)}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = 14.dp))
            Text("Desliza hacia arriba para la siguiente", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 18.dp))
        }
        // Botones a la derecha, donde TikTok pone el "me gusta".
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottom + 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SideButton(Icons.Filled.Person, "¿Cómo me\nafecta?", highlight = impact?.relevant == true, onClick = onImpact)
            SideButton(Icons.AutoMirrored.Filled.OpenInNew, "Leer", onClick = onOpen)
            SideButton(Icons.Filled.Share, "Compartir", onClick = onShare)
        }
    }
}

/** Gráfico de la cripto de la que habla la noticia (24 h, en dólares). */
@Composable
private fun CoinGraphic(c: com.caudal.finanzas.data.MarketCoin, top: androidx.compose.ui.unit.Dp) {
    val up = (c.change24h ?: 0.0) >= 0
    val line = if (up) Color(0xFF4ADE80) else Color(0xFFFF6B6B)
    Column(Modifier.fillMaxSize().padding(top = top + 70.dp, start = 22.dp, end = 22.dp, bottom = 20.dp)) {
        Text("${c.name} · ${c.symbol}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
        Text(usd(c.price) + (c.change24h?.let { "  " + (if (it >= 0) "▲ " else "▼ ") + String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f %%", kotlin.math.abs(it)) } ?: ""), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
            val v = c.spark; val min = v.min(); val max = v.max(); val r = (max - min).takeIf { it > 0 } ?: 1.0
            fun y(x: Double) = (size.height * (1 - (x - min) / r)).toFloat()
            val step = size.width / (v.size - 1)
            val path = androidx.compose.ui.graphics.Path().apply { v.forEachIndexed { i, x -> if (i == 0) moveTo(0f, y(x)) else lineTo(i * step, y(x)) } }
            drawPath(androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text("Últimas 24 h", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

/** Sin foto: un gráfico grande con el tema de la noticia. */
@Composable
private fun TopicGraphic(topic: String, top: androidx.compose.ui.unit.Dp) {
    val icon = when (topic) {
        "Cripto" -> Icons.Filled.CurrencyBitcoin
        "Bolsa" -> Icons.AutoMirrored.Filled.ShowChart
        "Empresas" -> Icons.Filled.Business
        else -> Icons.Filled.AccountBalance
    }
    Box(Modifier.fillMaxSize().padding(top = top), contentAlignment = Alignment.Center) {
        Box(Modifier.size(230.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)))
        Box(Modifier.size(160.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
        Icon(icon, null, Modifier.size(96.dp), tint = Color.White.copy(alpha = 0.85f))
    }
}

private val imageCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24)

/** Descarga y reduce una foto (sin librerías extra); se guarda en memoria mientras navegas. */
@Composable
private fun netImage(url: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val state = androidx.compose.runtime.produceState(url?.let { imageCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val c = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8_000; c.readTimeout = 12_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AhorraVaca")
                val bytes = c.inputStream.use { it.readBytes() }.also { c.disconnect() }
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var sample = 1
                while (o.outWidth / (sample * 2) >= 1080) sample *= 2
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.let { it.asImageBitmap() }
            }.getOrNull()
        }?.also { imageCache.put(url, it) }
    }
    return state.value
}

@Composable
private fun SideButton(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (highlight) Color(0xFFFFD60A) else Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(28.dp), tint = if (highlight) Color.Black else Color.White) }
        Text(label, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EndPage(title: String, sub: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🐄", fontSize = 56.sp)
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        Text(sub, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

/** La respuesta a "¿Cómo puede afectarme?": tus números al lado de la noticia. */
@Composable
private fun ImpactSheet(n: NewsItem, i: NewsImpact, onDismiss: () -> Unit) {
    FormSheet("¿Cómo puede afectarte?", onDismiss, primary = "Entendido", onPrimary = onDismiss) {
        Text(n.title, fontSize = 14.sp, color = P.muted, maxLines = 3)
        Text(i.headline, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (i.relevant) P.ink else P.muted)
        if (i.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            i.tags.forEach { t -> Box(Modifier.clip(RoundedCornerShape(50)).background(P.chip).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(t, fontSize = 13.sp, color = P.ink) } }
        }
        i.lines.forEach { l ->
            Row {
                Text("•", fontSize = 16.sp, color = P.gain, modifier = Modifier.width(16.dp))
                Text(l, fontSize = 16.sp, lineHeight = 22.sp, color = P.ink)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Cálculo con tus datos de la app y reglas fijas (sin IA). No es una recomendación de inversión.", fontSize = 12.sp, color = P.muted)
    }
}
