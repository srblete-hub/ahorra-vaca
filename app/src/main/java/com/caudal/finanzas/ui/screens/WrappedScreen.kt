package com.caudal.finanzas.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.domain.MonthWrapped
import com.caudal.finanzas.domain.Wrapped
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.icon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")
private val NeonGreen = Color(0xFF4ADE80)
private fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, es)
private fun shortDay(d: LocalDate) = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.')}"

private data class Story(val colors: List<Color>, val overline: String, val content: @Composable () -> Unit)

/** El Wrapped del mes: tarjetas a pantalla completa que se deslizan y, al final, la ficha para compartir. */
@Composable
fun WrappedScreen(vm: FinanceViewModel, userName: String, onBack: () -> Unit) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val d = planning?.data ?: return
    var month by remember { mutableStateOf(YearMonth.now()) }
    val w = remember(d, transfers, month) { Wrapped.of(d, month, transfers) }
    val money = LocalMoney.current
    val name = userName.ifBlank { "tú" }
    val mName = monthName(month)

    val stories = buildList {
        add(Story(listOf(Color(0xFF1DB954), Color(0xFF0B3D20)), "Tu ${mName} en Ahorra, vaca") {
            Big("Hola, $name.")
            Body(if (w.isCurrent) "Así va tu ${mName} hasta hoy (día ${w.daysElapsed}). Desliza →" else "Así fue tu ${mName}. Desliza →")
        })
        add(Story(listOf(Color(0xFF5E5CE6), Color(0xFF1C1A5E)), "Entró y salió") {
            Small("Ha entrado")
            Big(money.format(w.income), NeonGreen)
            Spacer(Modifier.height(18.dp))
            Small("Has gastado")
            Big(money.format(w.spent))
            Body("${w.payments} pagos · ${money.format(w.perDay)} al día de media")
        })
        w.topTag?.let { (tag, amount, share) ->
            add(Story(listOf(Color(0xFFFF6B3D), Color(0xFF5A1A06)), "Tu categoría estrella") {
                Icon(tag.icon(), null, Modifier.size(64.dp), tint = Color.White)
                Big(tag.label)
                Body("${money.format(amount)} · el ${share.toInt()} % de todo lo que gastaste")
            })
        }
        w.topPlace?.let { (place, total, times) ->
            add(Story(listOf(Color(0xFFE91E63), Color(0xFF4A0820)), "Tu sitio favorito") {
                Big(place)
                Body("Fuiste $times ${if (times == 1) "vez" else "veces"} y te dejaste ${money.format(total)}.")
            })
        }
        w.biggest?.let { e ->
            add(Story(listOf(Color(0xFFFFB300), Color(0xFF5C3A00)), "Tu mayor compra") {
                Big(money.format(e.amount))
                Body("${e.note.ifBlank { "Sin descripción" }} · ${shortDay(LocalDate.ofEpochDay(e.epochDay))}")
            })
        }
        if (w.priciestWeekday != null || w.noSpendDays > 0) add(Story(listOf(Color(0xFF00BCD4), Color(0xFF00363D)), "Tus días") {
            w.priciestWeekday?.let { (day, v) ->
                Small("El día que más gastas")
                Big(day.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() })
                Body("${money.format(v)} en total los ${day.getDisplayName(TextStyle.FULL, es)}")
                Spacer(Modifier.height(18.dp))
            }
            Small("Días sin gastar nada")
            Big("${w.noSpendDays}", NeonGreen)
            w.topDay?.let { (day, v) -> Body("Tu día más caro: ${shortDay(day)}, ${money.format(v)}.") }
        })
        w.vsPrevPct?.let { pct ->
            add(Story(listOf(if (pct <= 0) Color(0xFF16A34A) else Color(0xFFDC2626), Color(0xFF111111)), "Frente al mes pasado") {
                Big((if (pct > 0) "+" else "") + "${pct.toInt()} %", if (pct <= 0) NeonGreen else Color(0xFFFF8A8A))
                Body(
                    (if (pct <= 0) "Has gastado menos" else "Has gastado más") +
                        " que en los mismos días del mes anterior (${money.format(w.prevSpent ?: 0.0)})." +
                        if (pct <= 0) " Bien hecho." else "",
                )
            })
        }
        if (w.saved > 0) add(Story(listOf(Color(0xFF0E9F6E), Color(0xFF032B1D)), "Tu ahorro") {
            Big(money.format(w.saved), NeonGreen)
            Body("apartado para tu futuro este mes. La vaca está orgullosa. 🐄")
        })
    }

    val pager = rememberPagerState { stories.size + 1 }
    val scope = rememberCoroutineScope()
    val top = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page < stories.size) {
                val s = stories[page]
                Column(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(s.colors))
                        // Como en las historias: toque en el tercio izquierdo = atrás; en el resto = siguiente.
                        .pointerInput(page, stories.size) {
                            detectTapGestures { pos ->
                                val target = if (pos.x < size.width / 3f) page - 1 else page + 1
                                scope.launch { pager.animateScrollToPage(target.coerceIn(0, stories.size)) }
                            }
                        }
                        .padding(top = top + 70.dp, start = 28.dp, end = 28.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(s.overline.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(20.dp))
                    s.content()
                }
            } else {
                ShareStory(w, top)
            }
        }
        // Barras de historia y controles.
        Column(Modifier.fillMaxWidth().padding(top = top + 10.dp, start = 14.dp, end = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(stories.size + 1) { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 26.sp, color = Color.White, modifier = Modifier.clip(CircleShape).clickable { month = month.minusMonths(1); scope.launch { pager.scrollToPage(0) } }.padding(horizontal = 10.dp))
                Text(mName.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White)
                Text(
                    "›", fontSize = 26.sp, color = Color.White.copy(alpha = if (month < YearMonth.now()) 1f else 0.3f),
                    modifier = Modifier.clip(CircleShape).clickable(enabled = month < YearMonth.now()) { month = month.plusMonths(1); scope.launch { pager.scrollToPage(0) } }.padding(horizontal = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Cerrar", tint = Color.White)
                }
            }
        }
    }
}

@Composable private fun Big(text: String, color: Color = Color.White) =
    Text(text, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = color)

@Composable private fun Small(text: String) = Text(text, fontSize = 17.sp, color = Color.White.copy(alpha = 0.75f))

@Composable private fun Body(text: String) =
    Text(text, fontSize = 19.sp, lineHeight = 26.sp, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 10.dp))

/** Última tarjeta: la ficha del mes y el botón para compartirla como imagen. */
@Composable
private fun ShareStory(w: MonthWrapped, top: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101010), Color(0xFF050505))))
            .padding(top = top + 80.dp, start = 18.dp, end = 18.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TU FICHA DEL MES", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
            MonthShareCard(w)
        }
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.clip(CircleShape).background(Color.White).clickable {
                scope.launch {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                    val file = java.io.File(dir, "ahorra-vaca-${w.month}.png")
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Compartir mi mes"))
                }
            }.padding(horizontal = 26.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Share, null, tint = Color.Black)
            Spacer(Modifier.width(10.dp))
            Text("Compartir", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Text("Se comparte como imagen. Nada sale de tu móvil hasta que tú eliges dónde.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    }
}

/** La ficha: tu dinero en el banco (o el balance de un mes cerrado), una barra por día y lo que entró y salió. */
@Composable
fun MonthShareCard(w: MonthWrapped, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    val faded = Color.White.copy(alpha = 0.62f)
    val today = LocalDate.now()
    val len = w.month.lengthOfMonth()
    val shown = if (w.isCurrent) w.daysElapsed else len
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0E2A1B), Color(0xFF1E4D33))))
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹  ${monthName(w.month).uppercase()} ⌄  ›", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = faded)
            Text(if (w.isCurrent) "Hoy" else "Último día", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = faded)
        }
        Text(if (w.isCurrent) "En el banco" else "Balance del mes", fontSize = 17.sp, color = faded, modifier = Modifier.padding(top = 14.dp))
        Text(if (w.isCurrent) money.format(w.bank) else money.signed(w.left), fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, color = Color.White, maxLines = 1)
        val max = (w.daily.take(shown).maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth().height(78.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            (0 until len).forEach { i ->
                val v = w.daily[i]
                val last = i == shown - 1
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (last) Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    if (last) Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth().height(if (i < shown) (6 + 54 * (v / max)).dp else 4.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (last) Color.White else Color.White.copy(alpha = if (i < shown) 0.42f else 0.12f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(shortDay(w.month.atDay(1)), Modifier.weight(1f), fontSize = 13.sp, color = faded)
            if (w.isCurrent) Text("Hoy · ${today.dayOfMonth}   ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(shortDay(w.month.atEndOfMonth()), fontSize = 13.sp, color = faded)
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ingresos ", fontSize = 14.sp, color = faded)
            Text("+${money.format(w.income)}", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeonGreen, maxLines = 1)
            Text("Gastos ", fontSize = 14.sp, color = faded)
            Text("−${money.format(w.spent)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
        Text("Ahorra, vaca 🐄", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 12.dp))
    }
}
