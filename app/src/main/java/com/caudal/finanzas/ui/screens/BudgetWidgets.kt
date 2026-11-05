package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.DailyAdvice
import com.caudal.finanzas.domain.SavingsAdvice
import com.caudal.finanzas.domain.SpendTag
import com.caudal.finanzas.domain.SpendingBudget
import com.caudal.finanzas.domain.TagLimit
import com.caudal.finanzas.ui.AnimatedAmount
import com.caudal.finanzas.ui.BigAmountInput
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.Donut
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.ProgressLine
import com.caudal.finanzas.ui.Segmented
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.Slice
import com.caudal.finanzas.ui.color
import com.caudal.finanzas.ui.currencySymbol
import com.caudal.finanzas.ui.editable
import com.caudal.finanzas.ui.icon
import com.caudal.finanzas.ui.toAmount

private val Amber = Color(0xFFFFB020)

/** Consejo de hoy: qué día es, cuánto falta para cobrar, cuánto tienes al día y qué hacer. */
@Composable
fun DailyAdviceCard(a: DailyAdvice) {
    val money = LocalMoney.current
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.WbSunny, Amber, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Consejo de hoy", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text(a.headline, fontSize = 14.sp, color = P.muted)
            }
            if (a.perDay > 0) Column(horizontalAlignment = Alignment.End) {
                Text(money.format(a.perDay), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.gain)
                Text("para hoy", fontSize = 12.sp, color = P.muted)
            }
        }
        if (a.tips.isNotEmpty()) Spacer(Modifier.height(10.dp))
        a.tips.forEach { t ->
            Row(Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Lightbulb, null, Modifier.size(16.dp).padding(top = 2.dp), tint = Amber)
                Spacer(Modifier.width(8.dp))
                Text(t, fontSize = 15.sp, lineHeight = 21.sp, color = P.ink)
            }
        }
    }
}

/** Cuánto meter al mes en tu cuenta de ahorro, con el porqué y un botón para aplicarlo al reparto. */
@Composable
fun SavingsAdviceCard(a: SavingsAdvice, targetName: String?, currentGoal: Double?, onApply: (() -> Unit)?) {
    val money = LocalMoney.current
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Savings, SeriesColors[1], 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("¿Cuánto meter en ${targetName ?: "tu ahorro"}?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("Mi recomendación para cada mes", fontSize = 14.sp, color = P.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedAmount(a.recommended, 34.sp, SeriesColors[1])
            Text("  al mes · ${a.pctOfIncome.toInt()} % de lo que entra", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            "En un año: ${money.format(a.inOneYear)}" + if (a.interestOneYear > 1) " (${money.format(a.interestOneYear)} de intereses)" else "",
            fontSize = 14.sp, color = P.gain,
        )
        Spacer(Modifier.height(8.dp))
        a.reasons.forEach { Text("• $it", fontSize = 14.sp, color = P.ink, modifier = Modifier.padding(vertical = 2.dp)) }
        if (currentGoal != null) {
            val diff = currentGoal - a.recommended
            Text(
                if (kotlin.math.abs(diff) < 10) "Tu reparto actual (${money.format(currentGoal)}) ya está en línea."
                else "Tu reparto actual manda ${money.format(currentGoal)}: ${if (diff > 0) "algo más" else "algo menos"} de lo que te recomiendo.",
                fontSize = 13.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (onApply != null) {
            Spacer(Modifier.height(10.dp))
            BrandButton("Usar ${money.format(a.recommended)} en mi reparto", onApply, Modifier.fillMaxWidth())
        }
    }
}

/** La rueda: qué % de lo que tienes para gastar va a cada cosa, en euros al mes y a la semana. */
@Composable
fun BudgetWheelCard(b: SpendingBudget, onProfile: (() -> Unit)? = null, onEditTag: (SpendTag) -> Unit) {
    val money = LocalMoney.current
    MCard {
        Text("En qué puedes gastar", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        Text(
            if (b.fromBank) "Lo que te queda este mes después del alquiler, los fijos y el ahorro, repartido por tipo de gasto"
            else "Ingresos ${money.format(b.income)} − fijos ${money.format(b.fixed)} − ahorro ${money.format(b.saving)}",
            fontSize = 13.sp, color = P.muted,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Donut(b.items.filter { it.monthly >= 1 || it.spent > 0 }.map { Slice(it.tag.label, it.monthly, it.tag.color()) }, size = 190.dp) {
                Text(money.format(b.base), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("este mes para gastar", fontSize = 12.sp, color = P.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Toca una para fijar cuánto quieres gastar (por semana o por mes); el resto se reparte según tu perfil de ahorrador.", fontSize = 13.sp, color = P.muted)
        if (onProfile != null) Text(
            "Cambiar mi perfil de ahorrador", fontSize = 15.sp, color = P.gain, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onProfile).padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        b.items.filter { it.monthly >= 1 || it.spent > 0 }.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = P.line, thickness = 0.6.dp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEditTag(t.tag) }.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(t.tag.icon(), t.tag.color(), 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.tag.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                        Text(
                            "${money.format(t.monthly)}/mes · ${money.format(t.weekly)}/semana" + if (t.fixedByYou != null) " · fijado por ti" else "",
                            fontSize = 13.sp, color = P.muted,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${t.pct.toInt()} %", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = t.tag.color())
                }
                Row(Modifier.padding(start = 48.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(if (t.monthly > 0) t.spent / t.monthly else 0.0, t.tag.color(), Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (t.left >= 0) "quedan ${money.format(t.left)}" else "te pasas ${money.format(-t.left)}",
                        fontSize = 12.sp, color = if (t.left >= 0) P.muted else P.loss,
                    )
                }
            }
        }
    }
}

@Composable
fun TagLimitSheet(tag: SpendTag, current: TagLimit?, currency: String, onSave: (TagLimit) -> Unit, onAuto: () -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(current?.amount?.editable() ?: "") }
    var weekly by remember { mutableStateOf(current?.weekly ?: true) }
    val value = amount.toAmount()
    FormSheet(tag.label, onDismiss, onPrimary = { onSave(TagLimit(value!!, weekly)) }, primaryEnabled = value != null && value >= 0) {
        Text("¿Cuánto sueles (o quieres) gastar en ${tag.label.lowercase()}? Lo descontamos primero y el resto se reparte entre lo demás.", fontSize = 14.sp, color = P.muted)
        Segmented(listOf(true, false), weekly, { if (it) "Por semana" else "Por mes" }, { weekly = it })
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        value?.let { v ->
            Text(
                if (weekly) "≈ ${LocalMoney.current.format(v * 52 / 12)} al mes" else "≈ ${LocalMoney.current.format(v * 12 / 52)} a la semana",
                fontSize = 14.sp, color = P.gain,
            )
        }
        if (current != null) Text(
            "Volver a automático", fontSize = 15.sp, color = P.gain,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAuto).padding(8.dp),
        )
    }
}

/** Al principio: qué objetivos tienes y cuán ahorrador quieres ser. Con eso la app fija los % de cada tipo de gasto. */
@Composable
fun SavingProfileSheet(style: Int, goals: Set<String>, buffer: Double, onSave: (Int, Set<String>, Double) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(if (style < 0) 1 else style) }
    var picked by remember { mutableStateOf(goals) }
    var cushion by remember { mutableStateOf(buffer) }
    FormSheet("Tu forma de ahorrar", onDismiss, primary = "Fijar mis porcentajes", onPrimary = { onSave(chosen, picked, cushion) }) {
        Text("Con esto fijo cuánto puedes gastar en cada cosa y cuánto mandar al ahorro. Lo puedes cambiar cuando quieras.", fontSize = 14.sp, color = P.muted)
        Text("¿Qué objetivos tienes?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            com.caudal.finanzas.domain.LifeGoal.entries.forEach { g ->
                val on = g.name in picked
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) P.brand else P.chip)
                        .clickable { picked = if (on) picked - g.name else picked + g.name }.padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text(g.label, fontSize = 14.sp, color = if (on) P.onBrand else P.ink) }
            }
        }
        Text("¿Cuán ahorrador quieres ser?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        com.caudal.finanzas.domain.SavingProfile.STYLES.forEachIndexed { i, (name, sub) ->
            val on = i == chosen
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) P.brand else P.chip).clickable { chosen = i }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) P.onBrand else P.ink)
                    Text(sub, fontSize = 13.sp, color = if (on) P.onBrand.copy(alpha = 0.8f) else P.muted)
                }
            }
        }
        Text("¿Con cuánto quieres llegar a fin de mes?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        Text("Lo que te sobra a final de mes para no quedarte en negativo. \"Puedes gastar\" ya lo descuenta.", fontSize = 13.sp, color = P.muted)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            listOf(50.0, 100.0, 150.0, 200.0).forEach { b ->
                val on = kotlin.math.abs(b - cushion) < 0.5
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) P.brand else P.chip).clickable { cushion = b }.padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text("+${b.toInt()} €", fontSize = 14.sp, color = if (on) P.onBrand else P.ink) }
            }
        }
        val (n, o, a) = com.caudal.finanzas.domain.SavingProfile.split(chosen)
        Text("Tu reparto del sueldo: ${n.toInt()} % necesidades · ${o.toInt()} % ocio · ${a.toInt()} % ahorro", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = P.ink)
        val w = com.caudal.finanzas.domain.SavingProfile.weights(chosen, picked).orEmpty()
        val total = w.values.sum().takeIf { it > 0 } ?: 1.0
        Text("Así quedaría lo que puedes gastar:", fontSize = 14.sp, color = P.muted)
        Text(
            w.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key.label} ${(it.value / total * 100).toInt()} %" },
            fontSize = 14.sp, color = P.ink,
        )
    }
}
