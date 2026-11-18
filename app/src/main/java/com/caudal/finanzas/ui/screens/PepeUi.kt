package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.caudal.finanzas.R
import com.caudal.finanzas.domain.Pepe
import com.caudal.finanzas.domain.PepeBot
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.TextInput

/** Tarjeta de Inicio: Pepe, el pastor, te contesta aquí mismo con tus números (sin IA y sin salir de la app). */
@Composable
fun PepeCard(tip: String? = null, onAsk: (String) -> Unit) {
    var own by remember { mutableStateOf("") }
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_pepe), "Pepe, el pastor", Modifier.size(64.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Pregúntale a Pepe", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Column(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (tip != null) com.caudal.finanzas.ui.Vaca.Pasto else P.chip)
                        .clickable(enabled = tip != null) { onAsk("") }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (tip != null) Text("Pepe tiene algo que contarte", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.caudal.finanzas.ui.Vaca.PradoOscuro)
                    Text(
                        tip ?: "¿Te echo una mano con tus cuentas? Como con el rebaño: ni una oveja suelta.", fontSize = 14.sp,
                        color = if (tip != null) com.caudal.finanzas.ui.Vaca.Tinta else P.ink, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pepe.QUESTIONS.forEach { q ->
                Text(
                    q, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = P.ink,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(P.chip).clickable { onAsk(q) }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TextInput(own, { own = it }, "O pregúntale lo que quieras")
        Spacer(Modifier.height(8.dp))
        BrandButton("Preguntar a Pepe", { onAsk(own); own = "" }, Modifier.fillMaxWidth(), enabled = own.isNotBlank())
        Text("Pepe te contesta aquí, con tus números. Gratis y sin salir de la app.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Un mensaje del chat. */
data class PepeMsg(val fromPepe: Boolean, val text: String)

/** La conversación se guarda mientras la app esté abierta. */
object PepeChatState {
    val messages = mutableStateListOf<PepeMsg>()
    var typing by mutableStateOf(false)
    /** Consejos ya contados en esta sesión, para no repetirlos. */
    val toldTips = mutableSetOf<String>()
}

/** Chat con Pepe a pantalla completa. Las respuestas salen de reglas fijas con tus datos ([PepeBot]). */
@Composable
fun PepeChat(ctx: PepeBot.Ctx, first: String?, onDismiss: () -> Unit) {
    val msgs = PepeChatState.messages
    val scope = rememberCoroutineScope()
    // Pepe "escribe" antes de contestar: más tiempo cuanto más larga es la respuesta (entre 1,2 y 2,8 s).
    suspend fun say(text: String) {
        PepeChatState.typing = true
        delay((900L + text.length * 12L).coerceIn(1200L, 2800L))
        PepeChatState.typing = false
        msgs += PepeMsg(true, text)
    }
    fun ask(q: String) {
        if (q.isBlank() || PepeChatState.typing) return
        msgs += PepeMsg(false, q.trim())
        scope.launch { say(PepeBot.answer(q, ctx)) }
    }
    // Un consejo que Pepe manda por su cuenta, si tiene algo nuevo que contar.
    fun nextTip() = PepeBot.tips(ctx).firstOrNull { it !in PepeChatState.toldTips }?.also { PepeChatState.toldTips += it }
    LaunchedEffect(Unit) {
        if (msgs.isEmpty()) say(PepeBot.greeting(ctx.userName))
        if (first != null) {
            msgs += PepeMsg(false, first.trim())
            say(PepeBot.answer(first, ctx))
        } else nextTip()?.let { delay(600); say(it) }
    }
    // Si dejas el chat abierto un rato sin escribir, Pepe a veces te cuenta algo más.
    LaunchedEffect(msgs.size) {
        delay(20_000)
        if (!PepeChatState.typing && msgs.lastOrNull()?.fromPepe == true) nextTip()?.let { say(it) }
    }
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, PepeChatState.typing) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1 + if (PepeChatState.typing) 1 else 0) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(P.bg).padding(top = top).imePadding()) {
            // Cabecera.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_pepe), null, Modifier.size(48.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pepe", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text("Tu pastor de las cuentas", fontSize = 12.sp, color = P.muted)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(P.card).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Cerrar", Modifier.size(24.dp), tint = P.ink)
                }
            }
            // Mensajes.
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = list,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(msgs) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromPepe) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.Bottom) {
                        if (m.fromPepe) { Image(painterResource(R.drawable.ic_pepe), null, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp)) }
                        Text(
                            m.text, fontSize = 16.sp, lineHeight = 22.sp, color = if (m.fromPepe) P.ink else P.onBrand,
                            modifier = Modifier.widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = if (m.fromPepe) 8.dp else 24.dp, bottomEnd = if (m.fromPepe) 24.dp else 8.dp))
                                .background(if (m.fromPepe) P.card else P.brand).padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                // Pepe está escribiendo…
                if (PepeChatState.typing) item(key = "typing") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Image(painterResource(R.drawable.ic_pepe), null, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
                        TypingDots()
                    }
                }
            }
            // Preguntas rápidas.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (Pepe.QUESTIONS + listOf("¿Puedo gastarme 30 €?", "¿Cuánto gasto en comida?", "Mis suscripciones")).forEach { q ->
                    Text(q, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = P.ink,
                        modifier = Modifier.clip(CircleShape).background(P.card).clickable { ask(q) }.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            // Escribir.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    input, { input = it }, Modifier.weight(1f), placeholder = { Text("Escribe a Pepe…", color = P.muted) },
                    shape = RoundedCornerShape(24.dp), singleLine = false, maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = P.card, unfocusedContainerColor = P.card,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank()) P.chip else P.brand)
                        .clickable(enabled = input.isNotBlank()) { ask(input); input = "" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", Modifier.size(24.dp), tint = if (input.isBlank()) P.muted else P.onBrand) }
            }
        }
    }
}

/** Tres puntos que laten: Pepe está escribiendo. */
@Composable
private fun TypingDots() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)).background(P.card)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(500, delayMillis = i * 160), androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(P.muted.copy(alpha = a)))
        }
    }
}
