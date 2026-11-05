package com.caudal.finanzas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colores de la app: fondo claro, tarjetas blancas, verde bosque para botones,
 * menta para la tarjeta principal y verde vivo para lo que sube.
 */
data class Palette(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val chip: Color,
    val brand: Color,
    val onBrand: Color,
    val accent: Color,
    val mint: Color,
    val mintDeep: Color,
    val gain: Color,
    val loss: Color,
    val debt: Color,
    val isDark: Boolean,
    /** Borde fino de las tarjetas, como el cristal de iOS. */
    val cardBorder: Color = Color.Transparent,
    /** Barra inferior y píldoras flotantes (algo translúcidas). */
    val glass: Color = Color.Transparent,
    /** Brillo suave arriba a la izquierda del fondo. */
    val glow: Color = Color.Transparent,
)

// Estilo neutro tipo iOS: grises limpios, tarjetas con borde fino y color solo en los datos.
val LightPalette = Palette(
    bg = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF0B0B0C),
    muted = Color(0xFF6E6E73),
    line = Color(0xFFE5E5EA),
    chip = Color(0xFFEDEDF0),
    brand = Color(0xFF0B0B0C),
    onBrand = Color(0xFFFFFFFF),
    accent = Color(0xFF34C759),
    mint = Color(0xFFFFFFFF),
    mintDeep = Color(0xFFF7F7FA),
    gain = Color(0xFF1FA34A),
    loss = Color(0xFFE5392F),
    debt = Color(0xFFF08A24),
    isDark = false,
    cardBorder = Color(0x0F000000),
    glass = Color(0xE6FFFFFF),
    glow = Color(0xFFFFFFFF),
)

val DarkPalette = Palette(
    bg = Color(0xFF000000),
    card = Color(0xFF232325),
    ink = Color(0xFFFFFFFF),
    muted = Color(0xFF98989F),
    line = Color(0xFF333336),
    chip = Color(0xFF3A3A3D),
    brand = Color(0xFFFFFFFF),
    onBrand = Color(0xFF000000),
    accent = Color(0xFF30D158),
    mint = Color(0xFF2C2C2F),
    mintDeep = Color(0xFF222224),
    gain = Color(0xFF30D158),
    loss = Color(0xFFFF453A),
    debt = Color(0xFFFF9F0A),
    isDark = true,
    cardBorder = Color(0x24FFFFFF),
    glass = Color(0xE02A2A2D),
    glow = Color(0xFF555558),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Atajo: `P.card`, `P.ink`… dentro de cualquier composable. */
val P: Palette
    @Composable get() = LocalPalette.current

/** Colores fijos por tipo de patrimonio y por categoría. */
val SeriesColors = listOf(
    Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFFBF5AF2),
    Color(0xFFFF6B3D), Color(0xFF64D2FF), Color(0xFFFF375F), Color(0xFFFFD60A),
)

// Compatibilidad con código que usa estos nombres.
val Gain: Color @Composable get() = P.gain
val Loss: Color @Composable get() = P.loss

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Etiqueta pequeña en mayúsculas y espaciada ("PATRIMONIO NETO"). */
val OverlineStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)

@Composable
fun CaudalTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val scheme = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.brand,
        onPrimary = p.onBrand,
        primaryContainer = p.mint,
        onPrimaryContainer = p.ink,
        secondary = p.gain,
        secondaryContainer = p.mintDeep,
        onSecondaryContainer = p.ink,
        background = p.bg,
        onBackground = p.ink,
        surface = p.bg,
        onSurface = p.ink,
        surfaceVariant = p.chip,
        onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.card,
        surfaceContainerLow = p.card,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.chip,
        outline = p.line,
        outlineVariant = p.line,
        error = p.loss,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
