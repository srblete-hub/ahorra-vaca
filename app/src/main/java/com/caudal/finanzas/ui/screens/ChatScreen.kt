package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.domain.Answer
import com.caudal.finanzas.domain.Assistant
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.SeriesColors

private data class Message(val fromUser: Boolean, val text: String, val answer: Answer? = null)

/** Chat con tus finanzas: preguntas en lenguaje normal, respuestas con tus números y de dónde salen. */
@Composable
fun ChatScreen(vm: FinanceViewModel, onEdit: (Editing) -> Unit, onBack: () -> Unit) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val messages = remember {
        mutableStateListOf(
            Message(false, "Hola. Pregúntame lo que quieras sobre tu dinero: respondo con tus movimientos, tus gastos fijos y lo que viene, y te digo de dónde sale cada cosa. Todo se calcula en tu móvil."),
        )
    }
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()

    fun ask(q: String) {
        val p = planning ?: return
        if (q.isBlank()) return
        messages += Message(true, q.trim())
        val a = Assistant.answer(q, p.data, p.safe, p.forecast.events, money::format)
        messages += Message(false, a.text, a)
        input = ""
    }
    LaunchedEffect(messages.size) { list.animateScrollToItem(messages.size) }

    Column(Modifier.fillMaxSize().background(P.bg).imePadding()) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Pregúntale a Ahorra, vaca", style = MaterialTheme.typography.headlineSmall, color = P.ink)
                Text("Sin enviar tus datos a nadie", style = MaterialTheme.typography.bodySmall, color = P.muted)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages.size) { i -> Bubble(messages[i], onRecord = { exp -> vm.saveParsed(exp, planning?.bankPlan?.main?.id); messages += Message(false, "Hecho: ${if (exp.size == 1) "gasto apuntado" else "${exp.size} gastos apuntados"}.") }) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Assistant.SUGGESTIONS) { s ->
                        Box(Modifier.clip(CircleShape).background(P.chip).clickable { ask(s) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                            Text(s, fontSize = 14.sp, color = P.ink)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                input, { input = it }, Modifier.weight(1f),
                placeholder = { Text("Escribe tu pregunta o tus gastos…") },
                shape = RoundedCornerShape(26.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { ask(input) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = P.card, unfocusedContainerColor = P.card,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(if (input.isBlank()) P.chip else P.brand).clickable(enabled = input.isNotBlank()) { ask(input) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (input.isBlank()) P.muted else P.onBrand) }
        }
    }
}

@Composable
private fun Bubble(m: Message, onRecord: (List<com.caudal.finanzas.domain.ParsedExpense>) -> Unit) {
    var recorded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (m.fromUser) 20.dp else 6.dp, bottomEnd = if (m.fromUser) 6.dp else 20.dp))
                .background(if (m.fromUser) P.brand else P.card).padding(14.dp),
        ) {
            Text(m.text, fontSize = 16.sp, lineHeight = 22.sp, color = if (m.fromUser) P.onBrand else P.ink)
            val a = m.answer
            if (a != null && a.sources.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text("De dónde sale", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SeriesColors[1])
                a.sources.forEach { Text("· $it", fontSize = 13.sp, color = P.muted) }
            }
            if (a != null && a.expenses.isNotEmpty() && !recorded) {
                Spacer(Modifier.size(10.dp))
                BrandButton(if (a.expenses.size == 1) "Apuntar" else "Apuntar los ${a.expenses.size}", { recorded = true; onRecord(a.expenses) })
            }
        }
    }
}
