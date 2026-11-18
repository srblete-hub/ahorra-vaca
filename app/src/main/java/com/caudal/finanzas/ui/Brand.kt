package com.caudal.finanzas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Colores de la marca "Ahorra, vaca" que no dependen del tema. */
object Vaca {
    val Leche = Color(0xFFF6F1E6)
    val Tinta = Color(0xFF17150F)
    val Prado = Color(0xFF2E6B3F)
    val PradoOscuro = Color(0xFF1F4D2C)
    val Pasto = Color(0xFFDCEBD3)
    val Ubre = Color(0xFFE0607A)
    val UbreFuerte = Color(0xFFC2415C)
    val UbreSuave = Color(0xFFFBE3E8)
    val Heno = Color(0xFFE9B949)
    val Mancha = Color(0xFF2B2820)
}

// Las manchas de vaca (mismas formas que la presentación de la marca), en una caja de 320 × 200.
private val SPOTS = listOf(
    "M-10 18c22-14 64-12 78 8 12 18-4 40-26 46-20 6-30 22-50 18-20-4-24-58-2-72Z",
    "M206 -14c30-6 66 6 72 30 6 22-18 32-12 52 6 18-20 30-40 22-22-8-14-30-34-42-22-14-22-56 14-62Z",
    "M118 120c20-10 48-4 56 14 8 16-6 30-24 34-18 4-40-2-44-18-4-12 2-24 12-30Z",
    "M268 148c18-8 44 0 50 18 6 16-10 32-30 32s-36-10-36-24c0-12 6-20 16-26Z",
    "M30 168c10-6 26-4 30 6s-4 20-16 22-24-4-24-12 2-12 10-16Z",
)

/** Las manchas, escaladas para cubrir la caja (como `slice` en SVG). */
@Composable
fun Spots(modifier: Modifier = Modifier, tone: Color = Vaca.Mancha) {
    val paths = remember { SPOTS.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier) {
        val k = maxOf(size.width / 320f, size.height / 200f)
        translate((size.width - 320f * k) / 2, (size.height - 200f * k) / 2) {
            scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) { paths.forEach { drawPath(it, tone) } }
        }
    }
}

/**
 * La tarjeta del banco de la marca: tinta con manchas. Dentro, los colores del tema se invierten
 * (texto leche) para que cualquier contenido se lea sin tocarlo.
 */
@Composable
fun SpottedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val onInk = P.copy(
        ink = Vaca.Leche, muted = Vaca.Leche.copy(alpha = 0.7f), line = Vaca.Leche.copy(alpha = 0.14f),
        chip = Color(0xFF2B2820), card = Vaca.Tinta, mint = Vaca.Tinta, gain = Color(0xFF8FC79A), loss = Color(0xFFF08AA0),
    )
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Vaca.Tinta)) {
        Spots(Modifier.matchParentSize())
        CompositionLocalProvider(LocalPalette provides onInk) {
            Column(Modifier.fillMaxWidth().padding(24.dp), content = content)
        }
    }
}
