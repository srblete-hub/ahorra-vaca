package com.caudal.finanzas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.caudal.finanzas.domain.Merchants
import com.caudal.finanzas.domain.SpendTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = android.util.LruCache<String, ImageBitmap>(80)
private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

/** Descarga y reduce una imagen; null si no existe, no es una imagen o es demasiado pequeña para verse bien. */
private suspend fun load(url: String, maxWidth: Int, minWidth: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val c = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 6_000; c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AhorraVaca")
        val bytes = c.inputStream.use { it.readBytes() }.also { c.disconnect() }
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?.takeIf { it.width >= minWidth }?.asImageBitmap()
    }.getOrNull()
}

/** Descarga una imagen pequeña (logo) y la guarda en memoria. Null mientras carga o si falla. */
@Composable
fun rememberNetImage(url: String?, maxWidth: Int = 256): ImageBitmap? = rememberFirstNetImage(listOfNotNull(url), maxWidth)

/** Prueba varias direcciones de imagen, una tras otra, y se queda con la primera que funcione. */
@Composable
fun rememberFirstNetImage(urls: List<String>, maxWidth: Int = 256): ImageBitmap? {
    val state = produceState(urls.firstNotNullOfOrNull { cache.get(it) }, urls) {
        if (value != null) return@produceState
        for (u in urls) {
            if (u in failed) continue
            val img = load(u, maxWidth, minWidth = 24)
            if (img != null) { cache.put(u, img); value = img; break } else failed += u
        }
    }
    return state.value
}

/** Icono de un gasto: el logo del comercio si es conocido (gratis, de internet); si no, el de su categoría. */
@Composable
fun MerchantLogo(note: String, tag: SpendTag, size: Dp) {
    val logo = rememberFirstNetImage(Merchants.logoUrls(note))
    if (logo != null) {
        Box(Modifier.size(size).clip(CircleShape).background(Color.White).border(1.dp(), Color(0x14000000), CircleShape), contentAlignment = Alignment.Center) {
            Image(logo, null, Modifier.size(size * 0.62f).clip(CircleShape), contentScale = ContentScale.Fit)
        }
    } else {
        val c = tag.color()
        Box(Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(tag.icon(), null, Modifier.size(size * 0.55f), tint = c)
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
