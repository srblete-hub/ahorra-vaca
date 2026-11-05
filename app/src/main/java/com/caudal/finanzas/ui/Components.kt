package com.caudal.finanzas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val Spanish: Locale = Locale.forLanguageTag("es-ES")

// ---------- Formato ----------

/** Moneda y "ocultar importes", disponibles en toda la interfaz. */
data class MoneyFormat(val currency: String = "EUR", val hidden: Boolean = false) {
    private val fmt = NumberFormat.getCurrencyInstance(Spanish).apply {
        currency = Currency.getInstance(this@MoneyFormat.currency)
    }

    fun format(value: Double): String = if (hidden) "•••••" else fmt.format(value)

    fun signed(value: Double): String = if (hidden) "•••••" else (if (value > 0) "+" else "") + fmt.format(value)
}

val LocalMoney = compositionLocalOf { MoneyFormat() }

fun pct(value: Double): String = String.format(Spanish, "%.1f %%", value)
fun signedPct(value: Double): String = (if (value > 0) "+" else "") + pct(value)

private val amountFormat = DecimalFormat("#,##0.########", DecimalFormatSymbols(Spanish))
fun quantity(value: Double): String = amountFormat.format(value)

/** Acepta "1.234,56", "1234.56" o "1234,56". */
fun String.toAmount(): Double? {
    val s = trim().replace(" ", "")
    if (s.isEmpty()) return null
    val normalized = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
    return normalized.toDoubleOrNull()
}

fun Double.editable(): String = if (this == 0.0) "" else amountFormat.format(this).replace(".", "")

@Composable
fun gainColor(value: Double): Color = when {
    value > 0 -> P.gain
    value < 0 -> P.loss
    else -> P.muted
}

// ---------- Texto ----------

/** "152.092,62 €" con los céntimos y el símbolo más pequeños, como en las apps de banca modernas. */
@Composable
fun Amount(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 40.sp,
    color: Color = P.ink,
    textAlign: TextAlign? = null,
) {
    val cut = text.lastIndexOf(',').takeIf { it > 0 } ?: text.indexOf(' ').takeIf { it > 0 } ?: text.length
    Text(
        buildAnnotatedString {
            append(text.substring(0, cut))
            withStyle(SpanStyle(fontSize = size * 0.55f)) { append(text.substring(cut)) }
        },
        modifier = modifier,
        color = color,
        maxLines = 1,
        textAlign = textAlign,
        style = TextStyle(fontSize = size, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04 * size.value).sp),
    )
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = P.muted) {
    Text(text.uppercase(Spanish), modifier = modifier, style = OverlineStyle, color = color)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = P.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = P.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = P.ink)
        action?.invoke()
    }
}

// ---------- Superficies ----------

private val CardShape = RoundedCornerShape(24.dp)

/** Tarjeta blanca con sombra suave. */
@Composable
fun MCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    color: Color = P.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = P
    Column(
        modifier
            .fillMaxWidth()
            .then(if (!p.isDark) Modifier.shadow(4.dp, CardShape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000)) else Modifier)
            .clip(CardShape)
            .background(color)
            .border(1.dp, p.cardBorder, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

private val HeroShape = RoundedCornerShape(30.dp)

/** Tarjeta principal: un poco más clara arriba, con borde fino. */
@Composable
fun MintCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = P
    Column(
        modifier
            .fillMaxWidth()
            .then(if (!p.isDark) Modifier.shadow(6.dp, HeroShape, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000)) else Modifier)
            .clip(HeroShape)
            .background(Brush.verticalGradient(listOf(p.mint, p.mintDeep)))
            .border(1.dp, p.cardBorder, HeroShape)
            .padding(22.dp),
        content = content,
    )
}

/** Fondo de la app: neutro con un brillo suave arriba a la izquierda. */
fun Modifier.appBackground(p: Palette): Modifier = this
    .background(p.bg)
    .background(Brush.radialGradient(listOf(p.glow.copy(alpha = if (p.isDark) 0.75f else 0.7f), Color.Transparent), center = Offset(0f, 0f), radius = 2200f))

/** Botones de la cabecera agrupados en una sola píldora de cristal. */
@Composable
fun HeaderPill(content: @Composable RowScope.() -> Unit) {
    val p = P
    Row(
        Modifier.clip(CircleShape).background(p.glass).border(1.dp, p.cardBorder, CircleShape).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun PillIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(22.dp), tint = P.ink)
    }
}

/** Logo redondo de una cuenta: colores de los bancos conocidos, o un color estable por nombre. */
@Composable
fun AccountLogo(name: String, size: Dp = 44.dp) {
    val n = name.lowercase(Spanish)
    val (bg, fg, text) = when {
        "bbva" in n -> Triple(Color(0xFF004481), Color.White, "BBVA")
        "santander" in n -> Triple(Color(0xFFEC0000), Color.White, "S")
        "ing" in n.split(" ") || n.startsWith("ing") -> Triple(Color(0xFFFF6200), Color.White, "ING")
        "caixa" in n -> Triple(Color(0xFF007EAE), Color.White, "CX")
        "imagin" in n -> Triple(Color(0xFF00C3A5), Color.White, "im")
        "sabadell" in n -> Triple(Color(0xFF006DFF), Color.White, "S")
        "openbank" in n -> Triple(Color(0xFFE30613), Color.White, "OB")
        "revolut" in n -> Triple(Color(0xFF0B0B0C), Color.White, "R")
        "n26" in n -> Triple(Color(0xFF36A18B), Color.White, "N26")
        "wise" in n -> Triple(Color(0xFF9FE870), Color(0xFF163300), "W")
        "paypal" in n -> Triple(Color(0xFF003087), Color.White, "P")
        "trade republic" in n -> Triple(Color(0xFF0B0B0C), Color.White, "TR")
        "myinvestor" in n -> Triple(Color(0xFF1D1D1B), Color(0xFFFFD100), "MI")
        "robinhood" in n -> Triple(Color(0xFFCCFF00), Color(0xFF0B0B0C), "R")
        "binance" in n -> Triple(Color(0xFF181A20), Color(0xFFF0B90B), "B")
        "indexa" in n -> Triple(Color(0xFF00A1E0), Color.White, "IX")
        else -> {
            val c = SeriesColors[(name.hashCode() and 0x7fffffff) % SeriesColors.size]
            Triple(c, Color.White, name.trim().take(1).uppercase(Spanish).ifBlank { "?" })
        }
    }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = (size.value * if (text.length == 1) 0.42f else 0.27f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Tarjeta oscura de marca, para avisos destacados. */
@Composable
fun BrandCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFF0F3D27)).padding(22.dp),
        content = content,
    )
}

// ---------- Botones y pastillas ----------

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, badge: Boolean = false) {
    val p = P
    Box(
        Modifier.size(42.dp)
            .clip(CircleShape).background(p.glass).border(1.dp, p.cardBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = p.ink)
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(p.loss))
    }
}

@Composable
fun BrandButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = P.brand, contentColor = P.onBrand),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Pastilla seleccionable (filtros de periodo, monedas…). */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = P
    Box(
        modifier.clip(CircleShape)
            .background(if (selected) p.card else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) p.ink else p.muted,
        )
    }
}

/** Control segmentado tipo "Gasto | Ingreso": fondo gris y la opción activa en blanco. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val p = P
    Row(modifier.fillMaxWidth().clip(CircleShape).background(p.chip).padding(4.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).clip(CircleShape)
                    .background(if (on) p.card else Color.Transparent)
                    .clickable { onSelect(o) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (on) p.ink else p.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Etiqueta suave con fondo tintado ("88 % de tus ingresos"). */
@Composable
fun SoftTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Iconos ----------

/** Icono en un cuadrado redondeado con el color tintado. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 42.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = color)
    }
}

/** Círculo con iniciales (comercios, monedas, tickers). */
@Composable
fun Initials(text: String, color: Color, size: Dp = 42.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        val t = text.take(3).uppercase(Spanish)
        // Una letra se ve grande; tres (BTC, ETH) más pequeñas para que quepan.
        val fontSize = (size.value * if (t.length == 1) 0.42f else 0.26f).sp
        Text(t, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Foto de perfil (guardada en el móvil) o, si no hay, la inicial del nombre. */
@Composable
fun Avatar(name: String, version: Long, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap: androidx.compose.ui.graphics.ImageBitmap? = androidx.compose.runtime.remember(version) {
        if (version == 0L) null
        else runCatching {
            android.graphics.BitmapFactory.decodeFile(java.io.File(context.filesDir, AVATAR_FILE).path)?.asImageBitmap()
        }.getOrNull()
    }
    val base = modifier.size(size).then(if (ring != null) Modifier.background(ring, CircleShape).padding(3.dp) else Modifier).clip(CircleShape)
    if (bitmap != null) {
        androidx.compose.foundation.Image(bitmap = bitmap, contentDescription = "Foto de perfil", modifier = base, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(base.background(P.gain), contentAlignment = Alignment.Center) {
            Text(name.ifBlank { "?" }.take(1).uppercase(Spanish), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

const val AVATAR_FILE = "avatar.jpg"

/** Número que sube animado hasta [value] la primera vez que se ve. */
@Composable
fun AnimatedAmount(value: Double, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(value) {
        anim.animateTo(value.toFloat(), androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val money = LocalMoney.current
    Amount(money.format(if (money.hidden) value else anim.value.toDouble()), modifier, size, color)
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

// ---------- Listas ----------

/** Tarjeta blanca con filas separadas por líneas finas. */
@Composable
fun <T> CardList(items: List<T>, key: ((T) -> Any)? = null, row: @Composable (T) -> Unit) {
    MCard(padding = 0.dp) {
        items.forEachIndexed { i, item ->
            androidx.compose.runtime.key(key?.invoke(item) ?: i) {
                if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = P.line, thickness = 0.6.dp)
                row(item)
            }
        }
    }
}

@Composable
fun ListRow(
    title: String,
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val p = P
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
            if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = p.muted)
        }
        if (below != null) Column(Modifier.padding(start = if (leading != null) 56.dp else 0.dp, top = 8.dp), content = below)
    }
}

@Composable
fun AmountTrailing(text: String, sub: String? = null, color: Color = P.ink, subColor: Color = P.muted) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
        if (sub != null) Text(sub, fontSize = 14.sp, color = subColor, maxLines = 1)
    }
}

// ---------- Barras y gráficos ----------

/** Barra fina de progreso; roja al pasarse de 1. */
@Composable
fun ProgressLine(fraction: Double, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, track: Color = P.chip) {
    val f = fraction.coerceIn(0.0, 1.0).toFloat()
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(if (fraction > 1.0) P.loss else color))
    }
}

data class Slice(val label: String, val value: Double, val color: Color)

@Composable
fun StackedBar(slices: List<Slice>, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val positive = slices.filter { it.value > 0 }
    val total = positive.sumOf { it.value }
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (total <= 0) Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(P.chip))
        else positive.forEach { s ->
            Box(Modifier.weight((s.value / total).toFloat().coerceAtLeast(0.02f)).fillMaxHeight().clip(CircleShape).background(s.color))
        }
    }
}

/** Línea de evolución con relleno degradado y punto final. */
@Composable
fun LineChart(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val pts = if (values.size == 1) listOf(values[0], values[0]) else values
        val min = pts.min()
        val max = pts.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val top = 6.dp.toPx()
        val h = size.height - top - 2.dp.toPx()
        val stepX = size.width / (pts.size - 1)
        fun y(v: Double) = top + (h * (1 - (v - min) / range)).toFloat()
        val line = Path().apply {
            pts.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * stepX, y(v)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val end = Offset(size.width, y(pts.last()))
        drawCircle(color.copy(alpha = 0.25f), 7.dp.toPx(), end)
        drawCircle(color, 3.5.dp.toPx(), end)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    MCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(icon, P.gain, size = 60.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = P.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = P.muted, textAlign = TextAlign.Center)
        }
    }
}

// ---------- Formularios ----------

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = P.chip,
    unfocusedContainerColor = P.chip,
    disabledContainerColor = P.chip,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedLabelColor = P.muted,
    unfocusedLabelColor = P.muted,
    cursorColor = P.gain,
)

@Composable
fun AmountField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null) {
    TextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = suffix?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TextInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    TextField(
        value, onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Importe enorme y centrado, como en la pantalla de "Gasto detectado". */
@Composable
fun BigAmountInput(value: String, onValueChange: (String) -> Unit, currencySymbol: String) {
    val p = P
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, textAlign = TextAlign.End, letterSpacing = (-2).sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.gain),
            modifier = Modifier.width(IntrinsicWidthFor(value)),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text("0", style = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = p.muted.copy(alpha = 0.5f)))
                    inner()
                }
            },
        )
        Text(" $currencySymbol", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = p.muted), modifier = Modifier.padding(bottom = 8.dp))
    }
}

private fun IntrinsicWidthFor(text: String): Dp = (maxOf(1, text.length) * 30 + 8).dp

@Composable
fun ConfirmDeleteDialog(what: String, detail: String? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.card,
        title = { Text("¿Borrar $what?") },
        text = detail?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Borrar", color = P.loss) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = P.ink) } },
    )
}

/** Hoja inferior blanca con título, campos y botón principal ancho. */
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    primary: String? = "Guardar",
    onPrimary: () -> Unit = {},
    primaryEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = P.card,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = P.ink)
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Borrar", color = P.loss) }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(P.chip).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Cerrar", Modifier.size(18.dp), tint = P.ink) }
            }
            header?.invoke(this)
            content()
            if (primary != null) {
                BrandButton(primary, onPrimary, Modifier.fillMaxWidth().padding(top = 4.dp), enabled = primaryEnabled)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Etiqueta pequeña encima de un grupo de opciones en un formulario. */
@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = P.muted)
}
