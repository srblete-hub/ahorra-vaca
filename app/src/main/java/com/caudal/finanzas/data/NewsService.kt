package com.caudal.finanzas.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Una noticia: titular, medio, enlace, hora (ms) y un resumen corto si el medio lo da. */
data class NewsItem(val title: String, val source: String, val url: String, val epochMs: Long, val summary: String, val topic: String, val imageUrl: String? = null)

/**
 * Noticias financieras de hoy, gratis y sin clave: los feeds RSS públicos de Google Noticias en español
 * (economía, bolsa y cripto). Nada de lo que tienes en la app se envía: solo se descargan titulares.
 */
object NewsService {
    @Volatile var override: (() -> List<NewsItem>)? = null

    private val FEEDS = listOf(
        // Medios con foto en su RSS (gratis).
        "Empresas" to "https://feeds.elpais.com/mrss-s/pages/ep/site/cincodias.elpais.com/portada",
        "Economía" to "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/section/economia/portada",
        "Bolsa" to "https://e00-expansion.uecdn.es/rss/mercados.xml",
        "Economía" to "https://e00-expansion.uecdn.es/rss/portada.xml",
        "Cripto" to "https://es.beincrypto.com/feed/",
        // Google Noticias para completar (sin foto: se busca la de la página o se pone un gráfico).
        "Economía" to "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=es&gl=ES&ceid=ES:es",
        "Bolsa" to "https://news.google.com/rss/search?q=bolsa%20OR%20Ibex%20OR%20%22Wall%20Street%22%20OR%20acciones%20when:1d&hl=es&gl=ES&ceid=ES:es",
        "Cripto" to "https://news.google.com/rss/search?q=bitcoin%20OR%20ethereum%20OR%20criptomonedas%20when:1d&hl=es&gl=ES&ceid=ES:es",
        "Empresas" to "https://news.google.com/rss/search?q=empresas%20resultados%20OR%20BCE%20OR%20inflaci%C3%B3n%20when:1d&hl=es&gl=ES&ceid=ES:es",
    )

    suspend fun today(): List<NewsItem> = withContext(Dispatchers.IO) {
        override?.let { return@withContext it() }
        val all = coroutineScope { FEEDS.map { (topic, url) -> async { runCatching { parse(get(url), topic) }.getOrDefault(emptyList()) } }.awaitAll() }.flatten()
        val since = System.currentTimeMillis() - 26 * 3600_000L
        all.filter { it.epochMs == 0L || it.epochMs >= since }
            .filter { !isAd(it) }
            .distinctBy { it.title.lowercase().filter(Char::isLetterOrDigit).take(60) }
            .sortedByDescending { it.epochMs }
            .take(60)
    }

    /** Fuera anuncios: contenido patrocinado, publirreportajes, ofertas y escaparates de compras. */
    private val AD_WORDS = listOf(
        "patrocinado", "patrocinada", "publirreportaje", "publicidad", "contenido de marca", "branded", "sponsored", "partner content",
        "ofrecido por", "en colaboración con", "en colaboracion con", "oferta", "ofertas", "descuento", "chollo", "chollos", "cupón", "cupon",
        "código promocional", "codigo promocional", "black friday", "cyber monday", "rebajas", "sorteo", "promoción", "promocion", "compra ya",
        "mejores precios", "amazon prime day", "bono de bienvenida", "regístrate", "registrate", "airdrop gratis", "escaparate", "showroom",
    )
    private val AD_URL = listOf("/escaparate/", "/branded", "/patrocinad", "/publicidad", "/promocion", "/ofertas", "/compras/", "/brand-studio", "/sponsored", "/partner", "/press-release", "/comunicado")

    fun isAd(n: NewsItem, categories: String = ""): Boolean {
        val t = "${n.title} ${n.summary} $categories".lowercase()
        return AD_WORDS.any { w -> Regex("(^|[^a-záéíóúñ])${Regex.escape(w)}([^a-záéíóúñ]|$)").containsMatchIn(t) } || AD_URL.any { n.url.lowercase().contains(it) }
    }

    private val ogCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Foto de la propia noticia (la que la web pone para compartir), si la noticia no trae. */
    suspend fun pageImage(url: String): String? = withContext(Dispatchers.IO) {
        if (url.contains("news.google.com") || url.startsWith("https://example.com")) return@withContext null
        ogCache[url]?.let { return@withContext it.ifBlank { null } }
        val img = runCatching {
            val html = get(url).take(300_000)
            Regex("<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]*content=[\"']([^\"']+)").find(html)?.groupValues?.get(1)
                ?: Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image)").find(html)?.groupValues?.get(1)
        }.getOrNull()?.replace("&amp;", "&")?.takeIf { it.startsWith("https://") }
        ogCache[url] = img ?: ""
        img
    }

    private fun get(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AhorraVaca")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    /** Lee un RSS 2.0. Los titulares de Google Noticias acaban en " - Medio": se separa. */
    fun parse(xml: String, topic: String): List<NewsItem> {
        val p = android.util.Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        val out = mutableListOf<NewsItem>()
        var inItem = false
        val f = mutableMapOf<String, String>()
        var tag = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") { inItem = true; f.clear() }
                    else if (inItem && tag in setOf("media:content", "media:thumbnail", "enclosure")) {
                        val u = p.getAttributeValue(null, "url")
                        val type = p.getAttributeValue(null, "type").orEmpty() + p.getAttributeValue(null, "medium").orEmpty()
                        val isImage = type.isBlank() && tag != "enclosure" || "image" in type
                        // Mejor la foto grande (media:content) que la miniatura.
                        if (u != null && u.startsWith("http") && isImage && (f["image"] == null || tag == "media:content" && f["imageTag"] != "media:content")) {
                            f["image"] = u; f["imageTag"] = tag
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem && tag in setOf("title", "link", "pubDate", "description", "source", "category")) f[tag] = (f[tag] ?: "") + " " + p.text
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        inItem = false
                        val rawTitle = f["title"]?.trim().orEmpty().replace(Regex("\\s+"), " ")
                        val source = f["source"]?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() } ?: rawTitle.substringAfterLast(" - ", "").trim()
                        val title = if (source.isNotBlank() && rawTitle.endsWith(" - $source")) rawTitle.removeSuffix(" - $source").trim() else rawTitle
                        val ms = runCatching { ZonedDateTime.parse(f["pubDate"]?.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L)
                        val desc = f["description"].orEmpty()
                        val image = f["image"] ?: Regex("<img[^>]+src=[\"']([^\"']+)").find(desc)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
                        val summary = desc.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()
                            .takeIf { it.isNotBlank() && !it.startsWith(title.take(30)) }.orEmpty()
                        val item = NewsItem(title, source.ifBlank { sourceOf(f["link"].orEmpty()) }, f["link"].orEmpty().trim(), ms, summary.take(280), topic, image)
                        if (title.isNotBlank() && f["link"] != null && !isAd(item, f["category"].orEmpty())) out += item
                    }
                    tag = ""
                }
            }
        }
        return out
    }

    /** Nombre del medio a partir del enlace, para los feeds que no lo dicen. */
    private fun sourceOf(link: String): String = when {
        "cincodias" in link -> "Cinco Días"
        "elpais" in link -> "El País"
        "expansion" in link -> "Expansión"
        "beincrypto" in link -> "BeInCrypto"
        else -> "Google Noticias"
    }
}
