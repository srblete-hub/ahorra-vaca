package com.caudal.finanzas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.BankPlan
import com.caudal.finanzas.domain.InterestMath
import com.caudal.finanzas.domain.SpendTag

fun SpendTag.color(): Color = when (this) {
    SpendTag.SUPER -> Color(0xFF34C759)
    SpendTag.FOOD -> Color(0xFFFF9F0A)
    SpendTag.TRANSPORT -> Color(0xFF0A84FF)
    SpendTag.HOME -> Color(0xFF5E5CE6)
    SpendTag.SUBSCRIPTIONS -> Color(0xFFFF375F)
    SpendTag.SHOPPING -> Color(0xFFBF5AF2)
    SpendTag.HEALTH -> Color(0xFF30B0C7)
    SpendTag.LEISURE -> Color(0xFFFFD60A)
    SpendTag.TRAVEL -> Color(0xFF64D2FF)
    SpendTag.GIFTS -> Color(0xFFFF6B3D)
    SpendTag.EDUCATION -> Color(0xFFAC8E68)
    SpendTag.PETS -> Color(0xFFA2845E)
    SpendTag.OTHER -> Color(0xFF8E8E93)
}

fun SpendTag.icon(): ImageVector = when (this) {
    SpendTag.SUPER -> Icons.Filled.LocalGroceryStore
    SpendTag.FOOD -> Icons.Filled.Restaurant
    SpendTag.TRANSPORT -> Icons.Filled.DirectionsCar
    SpendTag.HOME -> Icons.Filled.Home
    SpendTag.SUBSCRIPTIONS -> Icons.Filled.MusicNote
    SpendTag.SHOPPING -> Icons.Filled.ShoppingBag
    SpendTag.HEALTH -> Icons.Filled.FavoriteBorder
    SpendTag.LEISURE -> Icons.Filled.SportsEsports
    SpendTag.TRAVEL -> Icons.Filled.FlightTakeoff
    SpendTag.GIFTS -> Icons.Filled.Redeem
    SpendTag.EDUCATION -> Icons.Filled.School
    SpendTag.PETS -> Icons.Filled.Pets
    SpendTag.OTHER -> Icons.AutoMirrored.Filled.ReceiptLong
}

/** Anillo con un trozo por tipo de gasto y el total en el centro. */
@Composable
fun Donut(slices: List<Slice>, size: Dp = 170.dp, stroke: Dp = 22.dp, center: @Composable () -> Unit = {}) {
    val track = P.chip
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            val total = slices.sumOf { it.value }.takeIf { it > 0 }
            if (total == null) {
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
                return@Canvas
            }
            val gap = if (slices.size > 1) 2.5f else 0f
            var start = -90f
            slices.forEach { s ->
                val sweep = (s.value / total * 360).toFloat()
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

/** Botón de elegir tipo de gasto. */
@Composable
fun TagChip(tag: SpendTag, selected: Boolean, onClick: () -> Unit) {
    val p = P
    Row(
        Modifier.clip(CircleShape).background(if (selected) tag.color() else p.chip).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tag.icon(), null, Modifier.size(16.dp), tint = if (selected) Color.White else tag.color())
        Spacer(Modifier.width(6.dp))
        Text(tag.label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else p.ink)
    }
}

@Composable
fun TagPicker(selected: SpendTag, onSelect: (SpendTag) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpendTag.entries.forEach { t -> TagChip(t, t == selected) { onSelect(t) } }
    }
}

/**
 * El mes de tu cuenta principal: saldo − fijos + lo que aún te llega = dinero del mes. De ahí, tu
 * gasto habitual y el margen de fin de mes; lo que sobra va al ahorro (ahora, o cuando llegue el cobro).
 */
@Composable
fun BankPlanCard(
    plan: BankPlan,
    onBuffer: (Double) -> Unit,
    onMove: () -> Unit,
    onSettings: () -> Unit,
    onAddLateIncome: () -> Unit,
) {
    val money = LocalMoney.current
    val p = P
    val target = plan.target
    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("es-ES"))
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tu reparto del mes", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                Text("Quedan ${plan.days} ${if (plan.days == 1) "día" else "días"} de mes", fontSize = 14.sp, color = p.muted)
            }
            RoundIconButton(Icons.Filled.Tune, "Cambiar reparto", onClick = onSettings)
        }
        Spacer(Modifier.height(14.dp))
        PlanLine({ AccountLogo(plan.main.name, 30.dp) }, "En ${plan.main.name}", money.format(plan.main.balance), p.ink, bold = true)
        plan.pending.forEach { (name, amount) ->
            PlanLine({ Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Home, null, Modifier.size(18.dp), tint = p.loss) } }, name, "−" + money.format(amount), p.loss)
        }
        plan.incoming.forEach { (name, amount, day) ->
            PlanLine(
                { Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp), tint = p.gain) } },
                "$name · llega el ${day.format(dayFmt)}", "+" + money.format(amount), p.gain,
            )
        }
        if (plan.incoming.isEmpty()) {
            Text(
                "+ ¿Te llega parte del sueldo más tarde? Añádela",
                fontSize = 14.sp, color = p.gain, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 4.dp).clip(CircleShape).clickable(onClick = onAddLateIncome),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.line)
        PlanLine(null, "Dinero del mes", money.format(plan.monthMoney), p.ink, bold = true)
        Text(
            "Sueles gastar ${money.format(plan.dailyHabit)} al día: unos ${money.format(plan.expectedSpend)} hasta fin de mes.",
            fontSize = 13.sp, color = p.muted,
        )
        Spacer(Modifier.height(12.dp))

        if (target == null) {
            Text(
                "Añade la cuenta donde ahorras (por ejemplo tu plan al 5 %) y marca que da intereses: Ahorra, vaca te dirá cuánto mandar cada mes.",
                fontSize = 14.sp, color = p.muted,
            )
        } else {
            Box(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(SeriesColors[1].copy(alpha = 0.10f)).padding(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountLogo(target.name, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("A ${target.name} este mes", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = p.ink)
                            if (target.interestRate > 0) Text("${pct(target.interestRate)} TAE", fontSize = 13.sp, color = p.gain)
                        }
                        Text(money.format(plan.saveGoal), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = SeriesColors[1])
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (plan.saveGoal > 0) "Lo que sobra después de tu gasto habitual y de dejar ${money.format(plan.buffer)} para llegar sobrado a fin de mes."
                        else "Este mes no sobra: con tu gasto habitual y ${money.format(plan.buffer)} de margen no queda nada para ahorrar.",
                        fontSize = 13.sp, color = p.muted,
                    )
                    if (plan.movedThisMonth > 0) Text("Ya has movido ${money.format(plan.movedThisMonth)} este mes.", fontSize = 13.sp, color = p.gain, fontWeight = FontWeight.Medium)
                    if (plan.moveNow > 0.5) {
                        Spacer(Modifier.height(10.dp))
                        BrandButton("Mover ahora ${money.format(plan.moveNow)}", onMove, Modifier.fillMaxWidth(), icon = Icons.Filled.SwapHoriz)
                    }
                    if (plan.moveLater > 0.5) {
                        Text(
                            "Y ${money.format(plan.moveLater)} cuando te llegue ${plan.incoming.firstOrNull()?.first?.lowercase() ?: "el cobro"}: no lo muevas antes.",
                            fontSize = 13.sp, color = p.ink, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (target.interestRate > 0 && plan.saveGoal > 0) {
                        val year = InterestMath.project(target.balance, target.interestRate, plan.saveGoal, 12)
                        Text(
                            "Si lo repites cada mes, en un año tendrás ${money.format(year.finalBalance)} en ${target.name} (${money.format(year.interest)} de intereses).",
                            fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Llegar a fin de mes con", fontSize = 14.sp, color = p.muted)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50.0, 100.0, 150.0, 200.0).forEach { b ->
                val sel = kotlin.math.abs(plan.buffer - b) < 0.5
                Box(
                    Modifier.clip(CircleShape).background(if (sel) p.brand else p.chip).clickable { onBuffer(b) }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("+${b.toInt()} €", color = if (sel) p.onBrand else p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Puedes gastar", fontSize = 16.sp, color = p.muted)
        AnimatedAmount(plan.spendable.coerceAtLeast(0.0), 38.sp, if (plan.spendable > 0) p.gain else p.loss)
        Text(
            if (plan.spendable <= 0) "No llegas: lo que tienes no cubre los fijos y el margen de fin de mes."
            else "≈ ${money.format(plan.perDay)} al día · llegas a fin de mes con +${money.format(plan.buffer)}",
            fontSize = 14.sp, color = p.muted,
        )
    }
}

@Composable
private fun PlanLine(leading: (@Composable () -> Unit)?, label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = P.ink, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)
        Text(value, fontSize = 16.sp, color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
    }
}
