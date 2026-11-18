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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.caudal.finanzas.R
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
    bg = Color(0xFFF6F1E6),        // Leche
    card = Color(0xFFFFFDF8),
    ink = Color(0xFF17150F),       // Tinta
    muted = Color(0xFF7C7566),
    line = Color(0xFFE7DFCF),
    chip = Color(0xFFEDE6D6),
    brand = Color(0xFF2E6B3F),     // Prado
    onBrand = Color(0xFFFFFDF8),
    accent = Color(0xFF2E6B3F),
    mint = Color(0xFFFFFDF8),
    mintDeep = Color(0xFFF1EADC),
    gain = Color(0xFF2E6B3F),
    loss = Color(0xFFC2415C),      // Ubre fuerte
    debt = Color(0xFFB7791F),      // Heno oscuro
    isDark = false,
    cardBorder = Color(0x0F17150F),
    glass = Color(0xEBFFFDF8),
    glow = Color(0xFFDCEBD3),      // Pasto
)

val DarkPalette = Palette(
    bg = Color(0xFF12110D),
    card = Color(0xFF1E1C17),
    ink = Color(0xFFF6F1E6),
    muted = Color(0xFFA39C8C),
    line = Color(0xFF34302A),
    chip = Color(0xFF2B2822),
    brand = Color(0xFF8FC79A),
    onBrand = Color(0xFF12110D),
    accent = Color(0xFF8FC79A),
    mint = Color(0xFF1E1C17),
    mintDeep = Color(0xFF191712),
    gain = Color(0xFF8FC79A),
    loss = Color(0xFFF08AA0),
    debt = Color(0xFFE9B949),
    isDark = true,
    cardBorder = Color(0x24F6F1E6),
    glass = Color(0xE01E1C17),
    glow = Color(0xFF2A3A2C),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Atajo: `P.card`, `P.ink`… dentro de cualquier composable. */
val P: Palette
    @Composable get() = LocalPalette.current

/** Colores fijos por tipo de patrimonio y por categoría. */
val SeriesColors = listOf(
    Color(0xFF2E6B3F), Color(0xFFE0607A), Color(0xFFE9B949), Color(0xFF6E9C7A),
    Color(0xFFA07A52), Color(0xFFF2A9B6), Color(0xFFB7791F), Color(0xFF9FC9A8),
)

// Compatibilidad con código que usa estos nombres.
val Gain: Color @Composable get() = P.gain
val Loss: Color @Composable get() = P.loss

/** La letra de la marca: Bricolage Grotesque (variable), la única de toda la app. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun bricolage(w: Int) = Font(R.font.bricolage_variable, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val BrandFamily = FontFamily(bricolage(400), bricolage(500), bricolage(600), bricolage(700), bricolage(800))
/** Nombre antiguo, por compatibilidad: ya es la letra de la marca. */
val InterFamily = BrandFamily

/** Una sola letra en toda la app: los titulares también van en Inter. */
val SerifDisplay = BrandFamily

private val AppTypography = Typography().run {
    // Inter sin el espaciado extra de Material (las referencias llevan la letra más junta).
    fun TextStyle.sans() = copy(fontFamily = InterFamily, letterSpacing = (-0.1).sp)
    fun TextStyle.serif() = copy(fontFamily = BrandFamily, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    copy(
        displayLarge = displayLarge.serif(),
        displayMedium = displayMedium.serif(),
        displaySmall = displaySmall.serif(),
        headlineLarge = headlineLarge.serif(),
        headlineMedium = headlineMedium.serif().copy(fontSize = 40.sp, lineHeight = 44.sp),
        headlineSmall = headlineSmall.sans().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.sans().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = titleMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.sans(),
        bodyMedium = bodyMedium.sans(),
        bodySmall = bodySmall.sans(),
        labelLarge = labelLarge.sans().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.sans(),
        labelSmall = labelSmall.sans(),
    )
}

/** Etiqueta pequeña en mayúsculas y espaciada ("PATRIMONIO NETO"). */
val OverlineStyle = TextStyle(fontFamily = InterFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)

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
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
