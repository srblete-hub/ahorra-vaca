package com.caudal.finanzas.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.domain.FinancialAge
import com.caudal.finanzas.domain.GoalStatus
import com.caudal.finanzas.ui.BigAmountInput
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.FieldLabel
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.ProgressLine
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.TextInput
import com.caudal.finanzas.ui.currencySymbol
import com.caudal.finanzas.ui.editable
import com.caudal.finanzas.ui.toAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val es = Locale.forLanguageTag("es-ES")
private val Amber = Color(0xFFFFB020)
private fun years(v: Double) = String.format(es, "%.1f", abs(v))

// ---------- Objetivo ----------

/** El objetivo: progreso, cuánto hace falta al mes, si llegas a tiempo, consejos y un botón para ajustar el reparto. */
@Composable
fun GoalCard(g: GoalStatus?, onEdit: () -> Unit, onApplyPct: ((Double) -> Unit)?) {
    val money = LocalMoney.current
    if (g == null) {
        MCard(onClick = onEdit, padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Flag, SeriesColors[0], 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ponte un objetivo", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text("Ej.: 10.000 € ahorrados en 5 años. Te digo cuánto apartar y cómo llegar.", fontSize = 14.sp, color = P.muted)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = P.muted)
            }
        }
        return
    }
    val color = if (g.onTrack) P.gain else Color(0xFFFF9F0A)
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Flag, color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.goal.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("${money.format(g.goal.target)} · para ${g.goal.deadline.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", es))}", fontSize = 13.sp, color = P.muted)
            }
            Text("Editar", fontSize = 14.sp, color = P.gain, modifier = Modifier.clip(CircleShape).clickable(onClick = onEdit).padding(8.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money.format(g.current), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text("  de ${money.format(g.goal.target)}", fontSize = 15.sp, color = P.muted, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            Text("${(g.progress * 100).toInt()} %", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
        Spacer(Modifier.height(8.dp))
        // Hitos del 25, 50 y 75 %.
        Box {
            ProgressLine(g.progress, color, height = 12.dp)
            Row(Modifier.fillMaxWidth().height(12.dp)) {
                listOf(0.25f, 0.25f, 0.25f, 0.25f).forEachIndexed { i, w ->
                    Box(Modifier.weight(w).fillMaxSize()) {
                        if (i > 0) Box(Modifier.width(2.dp).fillMaxSize().background(P.card))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row {
            GoalStat("Necesitas", money.format(g.requiredMonthly) + "/mes", Modifier.weight(1f))
            GoalStat("Tu ritmo", money.format(g.pace) + "/mes", Modifier.weight(1f))
            GoalStat(
                if (g.onTrack) "Llegas" else "Llegarías",
                g.eta?.format(DateTimeFormatter.ofPattern("MMM yyyy", es)) ?: "—", Modifier.weight(1f),
                color,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp)) {
            Text(
                when {
                    g.done -> "¡Objetivo cumplido!"
                    g.onTrack -> "Vas a tiempo" + (g.monthsAhead?.takeIf { it > 0 }?.let { ": $it meses de adelanto." } ?: ".")
                    else -> "Vas con retraso: a este ritmo tendrías ${money.format(g.projected)} en la fecha."
                },
                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Consejos para llegar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        g.tips.forEach { t ->
            Row(Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Lightbulb, null, Modifier.size(16.dp).padding(top = 2.dp), tint = Amber)
                Spacer(Modifier.width(8.dp))
                Text(t, fontSize = 14.sp, color = P.ink)
            }
        }
        if (onApplyPct != null && g.suggestedPct != null && !g.onTrack) {
            Spacer(Modifier.height(12.dp))
            BrandButton("Ajustar mi reparto al ${g.suggestedPct.toInt()} %", { onApplyPct(g.suggestedPct) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GoalStat(label: String, value: String, modifier: Modifier, color: Color = P.ink) {
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = P.muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
fun GoalSheet(name: String, target: Double, deadline: LocalDate?, currency: String, onSave: (String, Double, LocalDate) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var amount by remember { mutableStateOf(target.editable()) }
    val today = LocalDate.now()
    var years by remember { mutableStateOf(deadline?.let { ((it.year - today.year) + (it.monthValue - today.monthValue) / 12.0).toInt().coerceAtLeast(1) } ?: 5) }
    val value = amount.toAmount()
    FormSheet("Tu objetivo", onDismiss, onPrimary = { onSave(n.trim().ifBlank { "Mi objetivo" }, value!!, today.plusYears(years.toLong())) }, primaryEnabled = value != null && value > 0, onDelete = onDelete) {
        Text("Pon una cantidad y un plazo. Ahorra, vaca calcula cuánto apartar al mes (con los intereses de tu cuenta de ahorro), te dice si vas a tiempo y cómo llegar antes.", fontSize = 14.sp, color = P.muted)
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        TextInput(n, { n = it }, "Para qué (Entrada del piso, Colchón, Viaje…)")
        FieldLabel("¿En cuánto tiempo?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 5, 10, 15).forEach { y ->
                Box(
                    Modifier.clip(CircleShape).background(if (y == years) P.brand else P.chip).clickable { years = y }.padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text("$y ${if (y == 1) "año" else "años"}", color = if (y == years) P.onBrand else P.ink, style = MaterialTheme.typography.labelLarge) }
            }
        }
        value?.let { Text("≈ ${LocalMoney.current.format(it / (years * 12))} al mes sin contar intereses", fontSize = 14.sp, color = P.gain) }
    }
}

// ---------- Edad financiera ----------

@Composable
fun AgeSheet(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    val year = text.toIntOrNull()?.takeIf { it in 1920..LocalDate.now().year - 14 }
    FormSheet("Tu año de nacimiento", onDismiss, onPrimary = { onSave(year!!) }, primaryEnabled = year != null) {
        Text("Para calcular tu edad financiera la comparamos con tu edad real. Solo se guarda en tu móvil.", fontSize = 14.sp, color = P.muted)
        TextInput(text, { text = it.filter(Char::isDigit).take(4) }, "Año (por ejemplo 1996)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
    }
}

/** Orbe luminoso con chispas, como el de la WHOOP Age: verde si tus finanzas son más maduras que tú, dorado si van por detrás. */
@Composable
fun AgeOrb(age: FinancialAge, size: androidx.compose.ui.unit.Dp = 260.dp) {
    val mature = age.difference >= 0
    val base = if (mature) Color(0xFF34E08A) else Amber
    val t = rememberInfiniteTransition(label = "orb")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val sparks = remember { List(70) { i -> Triple(((i * 73) % 100) / 100f, ((i * 37 + 11) % 100) / 100f, ((i * 53) % 10) / 10f) } }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2 * 0.86f
            // Contorno blando que respira.
            val path = Path()
            for (i in 0..72) {
                val a = i / 72f * 2 * PI.toFloat()
                val wobble = 1 + 0.035f * sin(3 * a + phase) + 0.025f * cos(5 * a - phase)
                val p = Offset(c.x + cos(a) * r * wobble, c.y + sin(a) * r * wobble)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.35f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
            drawPath(path, Brush.radialGradient(listOf(Color.Black, Color(0xFF0E0A02), base.copy(alpha = 0.55f), base), c, r * 1.05f))
            sparks.forEach { (x, y, s) ->
                val a = x * 2 * PI.toFloat() + phase * 0.15f
                val d = (0.55f + 0.45f * y) * r
                drawCircle(Color(0xFFFFF4D6).copy(alpha = 0.35f + 0.6f * s), 1.2f + 2.4f * s, Offset(c.x + cos(a) * d, c.y + sin(a) * d))
            }
        }
        if (size >= 150.dp) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(es, "%.1f", age.age), fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-1).sp)
            Text("EDAD FINANCIERA", fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            Text(
                if (abs(age.difference) < 0.05) "igual que tu edad" else if (age.difference > 0) "${years(age.difference)} años más madura" else "${years(age.difference)} años por detrás",
                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = base,
            )
        }
    }
}

/** Ritmo de maduración de tus finanzas: de −1x (te quedas atrás) a 3x (maduras deprisa). */
@Composable
fun PaceOfAging(pace: Double) {
    Column(Modifier.fillMaxWidth()) {
        Text("RITMO DE MADURACIÓN", fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = P.muted)
        Spacer(Modifier.height(4.dp))
        Text(String.format(es, "%.1fx", pace), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P.ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth()) {
            Text("Lento", fontSize = 13.sp, color = P.muted); Spacer(Modifier.weight(1f)); Text("Rápido", fontSize = 13.sp, color = P.muted)
        }
        val ink = P.ink; val muted = P.muted
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val n = 60
            for (i in 0..n) {
                val x = size.width * i / n
                val major = i % 15 == 0
                drawLine(muted.copy(alpha = if (major) 0.9f else 0.45f), Offset(x, if (major) 4f else 10f), Offset(x, size.height - 6f), strokeWidth = if (major) 3f else 2f)
            }
            val f = ((pace + 1) / 4).toFloat().coerceIn(0f, 1f)
            drawLine(ink, Offset(size.width * f, 0f), Offset(size.width * f, size.height), strokeWidth = 6f)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("−1,0x", fontSize = 12.sp, color = P.muted); Spacer(Modifier.weight(1f))
            Text("1,0x", fontSize = 12.sp, color = P.muted); Spacer(Modifier.weight(1f))
            Text("3,0x", fontSize = 12.sp, color = P.muted)
        }
    }
}

/** Tarjeta pequeña para Inicio. */
@Composable
fun FinancialAgeMini(age: FinancialAge?, onClick: () -> Unit) {
    MCard(onClick = onClick, padding = 18.dp, color = Color(0xFF0B0B0C)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EDAD FINANCIERA", fontSize = 12.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.7f))
                if (age == null) {
                    Text("¿Cuántos años tiene tu dinero?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Descúbrelo según tus hábitos y tu ahorro", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                } else {
                    Text(String.format(es, "%.1f", age.age), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (age.difference >= 0) "${years(age.difference)} años por delante de tu edad" else "${years(age.difference)} años por detrás de tu edad",
                        fontSize = 14.sp, color = if (age.difference >= 0) Color(0xFF34E08A) else Amber,
                    )
                }
            }
            if (age != null) AgeOrb(age, 92.dp) else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
        }
    }
}

@Composable
fun FinancialAgeScreen(vm: FinanceViewModel, onEdit: (Editing) -> Unit, onBack: () -> Unit) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val p = planning ?: return
    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var hideCalibrating by remember { mutableStateOf(false) }
    val age = p.age
    val today = LocalDate.now()
    val nextUpdate = 7 - today.dayOfWeek.value % 7

    // Siempre en oscuro, como la pantalla de WHOOP.
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF050505)),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = nav + 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SALUD FINANCIERA", fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("PRÓXIMA ACTUALIZACIÓN EN $nextUpdate ${if (nextUpdate == 1) "DÍA" else "DÍAS"}", fontSize = 11.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.6f))
                }
                Spacer(Modifier.width(42.dp))
            }
        }
        if (age == null) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                    Text("Tu edad financiera", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Comparamos tus hábitos, tu ahorro y tu patrimonio con lo esperable para tu edad. Dinos en qué año naciste.", fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    BrandButton("Poner mi año de nacimiento", { onEdit(Editing.AgeForm) })
                }
            }
            return@LazyColumn
        }
        item { AgeOrb(age) }
        item {
            Text("Tu edad real: ${age.realAge} años", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        }
        if (age.calibrating && !hideCalibrating) item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0E2A3A)).padding(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, null, tint = Color(0xFF5AC8FA), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tu edad financiera se está calibrando", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5AC8FA))
                        Text("✕", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.clip(CircleShape).clickable { hideCalibrating = true }.padding(6.dp))
                    }
                    Text("Con pocos meses de datos la cifra puede moverse mucho. Según Ahorra, vaca vaya conociendo tus meses, se estabilizará.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        item {
            androidx.compose.runtime.CompositionLocalProvider(com.caudal.finanzas.ui.LocalPalette provides com.caudal.finanzas.ui.DarkPalette) {
                PaceOfAging(age.pace)
            }
        }
        item {
            val (title, body) = when {
                age.pace > 1.2 -> "Maduras deprisa" to "Ahorras lo suficiente para que tus finanzas ganen experiencia más rápido que tú. Sigue así."
                age.pace >= 0.8 -> "A ritmo normal" to "Tus finanzas maduran al mismo ritmo que tú. Ahorrar un poco más te haría ganar años."
                else -> "Te estás quedando atrás" to "Gastas casi todo lo que entra: tus finanzas no ganan experiencia. Mira qué te resta años abajo."
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF16181A)).padding(16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(body, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF16181A)).padding(vertical = 6.dp)) {
                Text("QUÉ TE SUMA Y QUÉ TE RESTA", fontSize = 12.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp, 10.dp))
                age.factors.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text(f.value, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                            Text(f.explanation, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
                        }
                        val c = when { f.years > 0.05 -> Color(0xFF34E08A); f.years < -0.05 -> Color(0xFFFF6B5A); else -> Color.White.copy(alpha = 0.6f) }
                        Text(
                            if (abs(f.years) < 0.05) "±0" else (if (f.years < 0) "−" else "+") + years(f.years) + " años",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c,
                        )
                    }
                }
            }
        }
        item {
            Text("Cambiar año de nacimiento", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clip(CircleShape).clickable { onEdit(Editing.AgeForm) }.padding(10.dp))
        }
    }
}
