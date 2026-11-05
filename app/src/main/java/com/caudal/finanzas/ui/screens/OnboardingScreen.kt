package com.caudal.finanzas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.StockHolding
import com.caudal.finanzas.domain.Allocation
import com.caudal.finanzas.domain.InterestMath
import com.caudal.finanzas.ui.Amount
import com.caudal.finanzas.ui.AmountTrailing
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.CardList
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.Initials
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.icon
import com.caudal.finanzas.ui.pct
import java.time.YearMonth

private enum class Step(val title: String, val subtitle: String) {
    BANK("Tu dinero en el banco", "Añade tus cuentas con el saldo que tienen hoy. Después se mantienen al día solas con tus pagos y cobros."),
    SAVINGS("Lo que tienes guardado fuera", "Efectivo, la hucha, PayPal, un fondo, un depósito… Si te da intereses, márcalo y verás cuánto te paga al mes."),
    INVEST("Tus inversiones", "Cripto y acciones o ETF. Los precios se actualizan solos."),
    INCOME("Lo que cobras", "Tu nómina fija y lo variable (autónomo, YouTube…) con su día de cobro. Lo variable se estima con tu media."),
    SPLIT("¿Cómo quieres repartirlo?", "Elige cuánto quieres ahorrar. Lo puedes cambiar cuando quieras en Presupuesto."),
    DONE("¡Listo!", "Esto es lo que tienes de verdad, todo junto."),
}

/** Asistente de la primera vez: va pidiendo cada cosa que tienes, con la opción de saltarla. */
@Composable
fun OnboardingScreen(vm: FinanceViewModel, onEdit: (Editing) -> Unit, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(Step.BANK) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val crypto by vm.crypto.collectAsStateWithLifecycle()
    val stocks by vm.stocks.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val sources by vm.incomeSources.collectAsStateWithLifecycle()
    val monthIncome by vm.monthIncome.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val next = { if (step == Step.DONE) onFinish() else step = Step.entries[step.ordinal + 1] }

    if (step.ordinal > 0) BackHandler { step = Step.entries[step.ordinal - 1] }

    Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding().navigationBarsPadding()) {
        // Progreso + saltar todo
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Step.entries.forEach { s ->
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (s.ordinal <= step.ordinal) P.gain else P.line))
                }
            }
            if (step != Step.DONE) TextButton(onClick = onFinish) { Text("Saltar todo", color = P.muted) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Overline("Paso ${step.ordinal + 1} de ${Step.entries.size}")
            Text(step.title, style = MaterialTheme.typography.headlineMedium, color = P.ink)
            Text(step.subtitle, style = MaterialTheme.typography.bodyMedium, color = P.muted)

            when (step) {
                Step.BANK -> {
                    val list = accounts.filter { it.type.inBank }
                    AccountList(list) { onEdit(Editing.AccountForm(it)) }
                    AddButton("Añadir cuenta del banco", Icons.Filled.AccountBalance) {
                        onEdit(Editing.AccountForm(Account(name = "", type = AccountType.BANK, balance = 0.0)))
                    }
                }
                Step.SAVINGS -> {
                    val list = accounts.filter { !it.type.inBank }
                    AccountList(list) { onEdit(Editing.AccountForm(it)) }
                    AddButton("Añadir ahorro", Icons.Filled.Savings) {
                        onEdit(Editing.AccountForm(Account(name = "", type = AccountType.HOME_SAVINGS, balance = 0.0)))
                    }
                }
                Step.INVEST -> {
                    val rows = crypto.map { Triple(it.symbol, it.name, it.amount * it.price) } + stocks.map { Triple(it.ticker, it.name.ifBlank { it.ticker }, it.shares * it.price) }
                    if (rows.isNotEmpty()) CardList(rows) { (sym, name, value) ->
                        ListRow(name, leading = { Initials(sym, SeriesColors[(sym.hashCode() and 7)]) }, trailing = { AmountTrailing(money.format(value)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AddButton("Cripto", Icons.Filled.CurrencyBitcoin, Modifier.weight(1f)) { onEdit(Editing.CoinSearch) }
                        AddButton("Acción o ETF", Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f)) {
                            onEdit(Editing.StockForm(StockHolding(ticker = "", name = "", shares = 0.0, avgBuyPrice = 0.0)))
                        }
                    }
                }
                Step.INCOME -> {
                    if (monthIncome.sources.isNotEmpty()) {
                        CardList(monthIncome.sources, key = { it.source.id }) { sm -> IncomeSourceRow(sm, YearMonth.now(), accounts, onEdit) }
                        Text("Total previsto este mes: ${money.format(monthIncome.total)}", style = MaterialTheme.typography.titleSmall, color = P.gain)
                    }
                    AddButton(if (sources.isEmpty()) "Añadir nómina u otro ingreso" else "Añadir otro ingreso", Icons.Filled.Add) {
                        onEdit(Editing.IncomeSourceForm(newIncomeSource()))
                    }
                }
                Step.SPLIT -> {
                    var draft by remember(categories) { mutableStateOf(categories) }
                    val savings = draft.firstOrNull { it.isSavings }
                    Allocation.Preset.entries.forEach { p ->
                        val selected = draft.filter { it.isSavings }.sumOf { it.percent } == p.savings
                        MCard(onClick = { draft = Allocation.applyPreset(draft, p); vm.saveCategories(draft) }, color = if (selected) P.mint else P.card) {
                            Text(p.label, style = MaterialTheme.typography.titleMedium, color = P.ink)
                            Text(
                                "${p.needs.toInt()} % necesidades · ${p.wants.toInt()} % ocio · ${p.savings.toInt()} % ahorro" +
                                    if (monthIncome.total > 0) " → ahorras ${money.format(monthIncome.total * p.savings / 100)}/mes" else "",
                                style = MaterialTheme.typography.bodySmall, color = P.muted,
                            )
                        }
                    }
                    if (savings != null) {
                        MCard {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("O elige tú cuánto ahorrar", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = P.ink)
                                Text(pct(savings.percent), style = MaterialTheme.typography.headlineSmall, color = SeriesColors[1])
                            }
                            Slider(
                                value = savings.percent.toFloat(),
                                onValueChange = { draft = Allocation.rebalance(draft, savings.id, Math.round(it).toDouble()) },
                                onValueChangeFinished = { vm.saveCategories(draft) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(thumbColor = SeriesColors[1], activeTrackColor = SeriesColors[1], inactiveTrackColor = P.chip),
                            )
                            Text(draft.joinToString(" · ") { "${it.name} ${pct(it.percent)}" }, style = MaterialTheme.typography.bodySmall, color = P.muted)
                        }
                    }
                }
                Step.DONE -> {
                    val total = accounts.sumOf { it.balance } + crypto.sumOf { it.amount * it.price } + stocks.sumOf { it.shares * it.price }
                    val interest = accounts.filter { it.interestRate > 0 }.sumOf { InterestMath.monthlyInterest(it.balance, it.interestRate) }
                    MintCard {
                        Overline("Tu patrimonio")
                        Amount(money.format(total), size = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("${accounts.size} cuentas · ${crypto.size + stocks.size} inversiones · ${sources.size} ingresos", style = MaterialTheme.typography.bodyMedium, color = P.muted)
                        if (interest > 0) Text("Tus ahorros te dan ${money.format(interest)} al mes de intereses", style = MaterialTheme.typography.titleSmall, color = P.gain)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step != Step.DONE) TextButton(onClick = next) { Text("Saltar paso", color = P.muted) }
            Spacer(Modifier.weight(1f))
            BrandButton(if (step == Step.DONE) "¡A ahorrar!" else "Siguiente", next, Modifier.width(180.dp))
        }
    }
}

@Composable
private fun AccountList(list: List<Account>, onClick: (Account) -> Unit) {
    if (list.isEmpty()) return
    val money = LocalMoney.current
    CardList(list, key = { it.id }) { a ->
        ListRow(
            a.name,
            subtitle = if (a.interestRate > 0) "${pct(a.interestRate)} TAE · +${money.format(InterestMath.monthlyInterest(a.balance, a.interestRate))}/mes" else a.type.label,
            onClick = { onClick(a) },
            leading = { IconTile(a.type.icon(), if (a.type.inBank) SeriesColors[0] else SeriesColors[1]) },
            trailing = { AmountTrailing(money.format(a.balance)) },
        )
    }
}

@Composable
private fun AddButton(text: String, icon: ImageVector, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, P.gain.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = P.gain),
    ) {
        Icon(icon, null, Modifier.padding(end = 8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}
