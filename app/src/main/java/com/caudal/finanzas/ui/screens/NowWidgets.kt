package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.NetWorthSnapshot
import com.caudal.finanzas.domain.InterestMath
import com.caudal.finanzas.domain.Wealth
import com.caudal.finanzas.domain.isSaving
import com.caudal.finanzas.ui.Amount
import com.caudal.finanzas.ui.BrandCard
import com.caudal.finanzas.ui.Dot
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.ProgressLine
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.Slice
import com.caudal.finanzas.ui.StackedBar
import com.caudal.finanzas.ui.gainColor
import com.caudal.finanzas.ui.pct
import com.caudal.finanzas.ui.signedPct
import java.time.LocalDate
import kotlin.math.ceil

val AvailableColor = SeriesColors[0]
val SavingsColor = SeriesColors[1]
val InvestedColor = SeriesColors[3]

/** Patrimonio de hoy comparado con la foto más antigua de este mes (o la última del mes pasado). */
fun changeThisMonth(snapshots: List<NetWorthSnapshot>, total: Double): Double? {
    val firstOfMonth = LocalDate.now().withDayOfMonth(1).toEpochDay()
    val today = LocalDate.now().toEpochDay()
    val base = snapshots.lastOrNull { it.epochDay < firstOfMonth }
        ?: snapshots.firstOrNull { it.epochDay in firstOfMonth until today }
        ?: return null
    return total - base.total
}

/** La tarjeta principal: todo lo que tienes ahora mismo, separado en disponible, ahorro e invertido. */
@Composable
fun NowCard(w: Wealth, change: Double?) {
    val money = LocalMoney.current
    MintCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Overline("Tienes ahora mismo")
            Spacer(Modifier.height(4.dp))
            Amount(money.format(w.total), size = 44.sp)
            if (change != null && change != 0.0) {
                val base = w.total - change
                Text(
                    (if (change >= 0) "↑ " else "↓ ") + money.signed(change) + (if (base > 0) " (${signedPct(change / base * 100)})" else "") + " este mes",
                    style = MaterialTheme.typography.labelLarge, color = gainColor(change),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        StackedBar(
            listOf(Slice("Disponible", w.available, AvailableColor), Slice("Ahorro", w.savings, SavingsColor), Slice("Invertido", w.invested, InvestedColor)),
            height = 10.dp,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(
                Triple("Disponible", w.available, AvailableColor),
                Triple("Ahorro", w.savings, SavingsColor),
                Triple("Invertido", w.invested, InvestedColor),
            ).forEach { (label, value, color) ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(color); Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = P.muted)
                    }
                    Text(money.format(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = P.ink, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Es la suma real de tus cuentas, ahorros e inversiones, incluido lo que ya tenías de antes.",
            style = MaterialTheme.typography.bodySmall, color = P.muted,
        )
    }
}

/** Cuando aún no hay nada: pide el saldo de hoy de cada sitio, con atajos claros. */
@Composable
fun SetupCard(onEdit: (Editing) -> Unit, onStepByStep: () -> Unit) {
    MintCard {
        Overline("Empieza aquí")
        Spacer(Modifier.height(6.dp))
        Text("¿Cuánto tienes ahora mismo?", style = MaterialTheme.typography.headlineSmall, color = P.ink)
        Text(
            "Pon el saldo que tiene hoy cada sitio donde guardas dinero. Lo que arrastras de meses anteriores ya va incluido ahí.",
            style = MaterialTheme.typography.bodyMedium, color = P.muted,
        )
        Spacer(Modifier.height(14.dp))
        SetupButton("Cuenta del banco", "Nómina, cuenta corriente…", Icons.Filled.AccountBalance, AvailableColor) {
            onEdit(Editing.AccountForm(Account(name = "", type = AccountType.BANK, balance = 0.0)))
        }
        Spacer(Modifier.height(8.dp))
        SetupButton("Ahorro", "Cuenta de ahorro, hucha, fondo, con o sin intereses", Icons.Filled.Savings, SavingsColor) {
            onEdit(Editing.AccountForm(Account(name = "", type = AccountType.BANK_SAVINGS, balance = 0.0)))
        }
        Spacer(Modifier.height(8.dp))
        SetupButton("Inversión", "Cripto, acciones o ETF", Icons.AutoMirrored.Filled.TrendingUp, InvestedColor) { onEdit(Editing.Chooser) }
        TextButton(onClick = onStepByStep, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Hacerlo paso a paso", color = P.gain) }
    }
}

@Composable
private fun SetupButton(title: String, sub: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = P.muted)
        }
        Text("+", style = MaterialTheme.typography.headlineSmall, color = P.gain)
    }
}

/**
 * "Tu dinero trabajando": lo que dan tus ahorros y cálculos de "¿sabías que…?" con tus propios
 * números. Son cuentas, no consejos: usan tus tipos y tus cantidades.
 */
@Composable
fun MoneyWorkingSection(accounts: List<Account>, available: Double, suggestedMonthly: Double, onEdit: (Editing) -> Unit) {
    val money = LocalMoney.current
    val earning = accounts.filter { it.interestRate > 0 }.sortedByDescending { it.balance * it.interestRate }
    val savingNoRate = accounts.filter { it.isSaving() && it.interestRate <= 0 }
    if (earning.isEmpty() && savingNoRate.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        earning.forEach { a ->
            val year = a.balance * a.interestRate / 100
            MCard(onClick = { onEdit(Editing.AccountForm(a)) }, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Savings, SavingsColor)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                        Text("${money.format(a.balance)} al ${pct(a.interestRate)} TAE", style = MaterialTheme.typography.bodySmall, color = P.muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("+${money.format(year)}", style = MaterialTheme.typography.titleMedium, color = P.gain)
                        Text("estimado en 1 año", style = MaterialTheme.typography.labelSmall, color = P.muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Si no lo tocas, ≈ ${money.format(InterestMath.monthlyInterest(a.balance, a.interestRate))} al mes de intereses." +
                        if (a.autoInterest) " Se suman solos cada mes." else "",
                    style = MaterialTheme.typography.bodySmall, color = P.muted,
                )
            }
        }

        val best = earning.maxByOrNull { it.interestRate }
        if (best != null) {
            val monthly = suggestedMonthly
            val y1 = InterestMath.project(0.0, best.interestRate, monthly, 12)
            val y5 = InterestMath.project(best.balance, best.interestRate, monthly, 60)
            BrandCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, null, tint = Color(0xFF34C77B))
                    Spacer(Modifier.width(8.dp))
                    Overline("¿Sabías que…?", color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    buildAnnotatedString {
                        append("Si metes ${money.format(monthly)} todos los meses en ${best.name}, en un año ganarías ")
                        withStyle(SpanStyle(color = Color(0xFF34C77B), fontWeight = FontWeight.ExtraBold)) { append(money.format(y1.interest)) }
                        append(" solo de intereses.")
                    },
                    style = MaterialTheme.typography.titleMedium, color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Y en 5 años, con lo que ya tienes ahí, llegarías a ${money.format(y5.finalBalance)}: " +
                        "${money.format(y5.interest)} serían intereses y te daría ${money.format(y5.monthlyInterestAtEnd(best.interestRate))} al mes.",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f),
                )
            }

            if (available > 300) {
                MCard(padding = 18.dp) {
                    Text("Dinero sin dar intereses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tienes ${money.format(available)} en cuentas que no dan intereses. Al ${pct(best.interestRate)} de ${best.name} serían " +
                            "+${money.format(available * best.interestRate / 100)} al año.",
                        style = MaterialTheme.typography.bodyMedium, color = P.muted,
                    )
                }
            }
        }

        if (earning.isEmpty()) {
            val a = savingNoRate.first()
            MCard(onClick = { onEdit(Editing.AccountForm(a)) }, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Lightbulb, P.debt)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("¿Tu ahorro da intereses?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                        Text("Abre \"${a.name}\", marca \"Da intereses\" y pon su TAE: verás cuánto te paga al mes y al año.", style = MaterialTheme.typography.bodySmall, color = P.muted)
                    }
                }
            }
        }
    }
}

/** El mes con dinero real: lo cobrado, lo gastado y la diferencia; el presupuesto va como límite de gasto. */
@Composable
fun MonthFlowCard(received: Double, spent: Double, spendLimit: Double, estimated: Boolean) {
    val money = LocalMoney.current
    val net = received - spent
    MCard {
        Row(Modifier.fillMaxWidth()) {
            FlowStat("Cobrado", "+" + money.format(received), P.gain, Modifier.weight(1f))
            FlowStat("Gastado", "-" + money.format(spent), P.loss, Modifier.weight(1f))
            FlowStat("Balance", money.signed(net), gainColor(net), Modifier.weight(1f))
        }
        if (spendLimit > 0) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Límite de gasto", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = P.ink)
                Text("${money.format(spent)} de ${money.format(spendLimit)}", style = MaterialTheme.typography.labelLarge, color = if (spent > spendLimit) P.loss else P.muted)
            }
            Spacer(Modifier.height(6.dp))
            ProgressLine(spent / spendLimit, AvailableColor, height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Sale de tu reparto en Presupuesto" + if (estimated) ", con lo variable estimado por tu media." else ".",
                style = MaterialTheme.typography.labelSmall, color = P.muted,
            )
        }
    }
}

@Composable
private fun FlowStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = P.muted)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
    }
}

/** Cantidad redonda para los "¿sabías que…?": lo que te toca ahorrar al mes, o 200 € si aún no hay presupuesto. */
fun suggestedMonthly(income: Double, savingsPct: Double): Double {
    val v = income * savingsPct / 100
    return if (v >= 10) ceil(v / 10) * 10 else 200.0
}
