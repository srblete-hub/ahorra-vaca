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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.domain.Anomaly
import com.caudal.finanzas.domain.GoalStatus
import com.caudal.finanzas.domain.Leak
import com.caudal.finanzas.domain.LeakKind
import com.caudal.finanzas.domain.PurchaseSimulator
import com.caudal.finanzas.ui.BigAmountInput
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.AmountField
import com.caudal.finanzas.ui.currencySymbol
import com.caudal.finanzas.ui.toAmount

private val Orange = Color(0xFFFF9F0A)

/** "Estos 5 gastos suman 276 € al año": las fugas pequeñas con su coste acumulado. */
@Composable
fun LeaksCard(leaks: List<Leak>, onReview: (Leak) -> Unit) {
    val money = LocalMoney.current
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Visibility, SeriesColors[6], 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Dinero invisible", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("Pequeñas fugas que, juntas, pesan", fontSize = 14.sp, color = P.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (leaks.isEmpty()) {
            Text("No veo fugas: ni comisiones, ni compras pequeñas que se repitan, ni subidas de precio.", fontSize = 14.sp, color = P.muted)
            return@MCard
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SeriesColors[6].copy(alpha = 0.10f)).padding(14.dp)) {
            Text(
                "${if (leaks.size == 1) "Este gasto suma" else "Estos ${leaks.size} gastos suman"} ${money.format(leaks.sumOf { it.yearly })} al año",
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink,
            )
        }
        leaks.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(color = P.line, thickness = 0.6.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
                    Text("${l.kind.label} · ${l.detail}", fontSize = 13.sp, color = P.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money.format(l.yearly), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (l.kind == LeakKind.PRICE_UP || l.kind == LeakKind.FEE) P.loss else P.ink)
                    Text("al año", fontSize = 12.sp, color = P.muted)
                }
                Spacer(Modifier.width(8.dp))
                Text("Revisar", fontSize = 14.sp, color = P.gain, modifier = Modifier.clip(CircleShape).clickable { onReview(l) }.padding(6.dp))
            }
        }
    }
}

/** Cambios respecto a tu forma normal de gastar. */
@Composable
fun AnomaliesCard(list: List<Anomaly>, onOpen: (Anomaly) -> Unit) {
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.ReportProblem, Orange, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Algo distinto a lo normal", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("Comparado contigo mismo, no con otros", fontSize = 14.sp, color = P.muted)
            }
        }
        if (list.isEmpty()) Text("Todo dentro de lo habitual este mes.", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(top = 10.dp))
        list.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = a.expense != null) { onOpen(a) }) {
                Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(if (a.severe) P.loss else Orange))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(a.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                    Text(a.detail, fontSize = 14.sp, color = P.muted)
                }
            }
        }
    }
}

/** Varios objetivos por prioridad: fechas realistas y cómo acelerar uno retrasa otro. */
@Composable
fun GoalsSection(
    goals: List<GoalStatus>, swapText: String?,
    onAdd: () -> Unit, onEdit: (Int) -> Unit, onMove: (Int, Int) -> Unit, onApplyPct: ((Double) -> Unit)?,
) {
    if (goals.isEmpty()) { GoalCard(null, onEdit = onAdd, onApplyPct = null); return }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (goals.size == 1) "Tu objetivo" else "Tus objetivos, por prioridad", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            TextButton(onClick = onAdd) { Icon(Icons.Filled.Add, null, tint = P.gain); Text(" Añadir", color = P.gain) }
        }
        goals.forEachIndexed { i, g ->
            if (goals.size > 1) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                Text("Prioridad ${i + 1}", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = P.muted)
                if (i > 0) RoundIconButton(Icons.Filled.KeyboardArrowUp, "Subir prioridad de ${g.goal.name}", onClick = { onMove(i, -1) })
                if (i < goals.lastIndex) { Spacer(Modifier.width(6.dp)); RoundIconButton(Icons.Filled.KeyboardArrowDown, "Bajar prioridad de ${g.goal.name}", onClick = { onMove(i, 1) }) }
            }
            GoalCard(g, onEdit = { onEdit(i) }, onApplyPct = if (i == 0) onApplyPct else null)
        }
        if (swapText != null) Text(swapText, fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/** "¿Me lo compro?": cómo afecta una compra a tu mes y a tus objetivos, con tres opciones. */
@Composable
fun PurchaseScreen(vm: FinanceViewModel, onBack: () -> Unit) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val p = planning ?: return
    val money = LocalMoney.current
    var price by rememberSaveable { mutableStateOf("") }
    var cheaper by rememberSaveable { mutableStateOf("") }
    val value = price.toAmount()
    val alt = cheaper.toAmount() ?: value?.let { it * 0.7 }
    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val goalName = p.goals.firstOrNull { !it.done }?.goal?.name
    val options = remember(value, alt, p) { if (value != null && value > 0) PurchaseSimulator.compare(value, alt ?: value, p.data, p.bankPlan, p.goals, money::format) else emptyList() }

    LazyColumn(
        Modifier.fillMaxSize().background(P.bg),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = nav + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("¿Me lo compro?", style = MaterialTheme.typography.headlineMedium, color = P.ink)
                    Text("No te digo que no: te enseño qué pasa en cada caso", style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }
        item {
            MCard {
                Text("¿Cuánto cuesta?", fontSize = 15.sp, color = P.muted)
                BigAmountInput(price, { price = it }, currencySymbol(money.currency))
                Spacer(Modifier.height(6.dp))
                AmountField(cheaper, { cheaper = it }, "Alternativa más barata (vacío = un 30 % menos)", suffix = "€")
            }
        }
        if (value != null && value > 0) item {
            RealPriceCard(remember(value, p) { com.caudal.finanzas.domain.RealPriceCalculator.of(value, p.data, p.goals.firstOrNull { !it.done }, money::format) }, goalName)
        }
        options.forEachIndexed { i, o ->
            item {
                val content: @Composable () -> Unit = {
                    Overline(o.title)
                    Spacer(Modifier.height(4.dp))
                    Text(money.format(o.cost), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(PurchaseSimulator.goalDelayText(o.goalDelayDays, goalName), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if ((o.goalDelayDays ?: 0) > 0) Orange else P.gain)
                    if (o.note.isNotBlank()) Text(o.note, fontSize = 14.sp, color = P.muted)
                }
                if (i == 0) MintCard { content() } else MCard { content() }
            }
        }
        if (options.isEmpty()) item {
            Text("Pon el precio y verás cuánto te quedaría al día este mes y cuánto se retrasa tu objetivo en cada opción.", fontSize = 14.sp, color = P.muted)
        }
    }
}
