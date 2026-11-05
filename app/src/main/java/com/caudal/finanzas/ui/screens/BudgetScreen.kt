package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.domain.Allocation
import com.caudal.finanzas.domain.IncomeMath
import com.caudal.finanzas.ui.Amount
import com.caudal.finanzas.ui.AmountTrailing
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.CardList
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.EmptyState
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.ProgressLine
import com.caudal.finanzas.ui.ScreenArgs
import com.caudal.finanzas.ui.ScreenTitle
import com.caudal.finanzas.ui.SectionTitle
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.SoftTag
import com.caudal.finanzas.ui.categoryIcon
import com.caudal.finanzas.ui.pct
import com.caudal.finanzas.ui.Donut
import com.caudal.finanzas.ui.Slice
import com.caudal.finanzas.ui.StackedBar
import com.caudal.finanzas.ui.color
import com.caudal.finanzas.ui.icon
import com.caudal.finanzas.domain.SpendTag
import com.caudal.finanzas.domain.Insights
import com.caudal.finanzas.domain.byTag
import com.caudal.finanzas.domain.spendTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Insights
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

fun newIncomeSource() = IncomeSource(name = "", variable = false, amount = 0.0, dayFrom = 1, dayTo = 5)

/** Cobro nuevo de [source] en [month], con lo esperado como importe de partida. */
fun newIncomeFor(source: IncomeSource, expected: Double, month: YearMonth): Income {
    val today = LocalDate.now()
    val day = if (YearMonth.from(today) == month) today.dayOfMonth else source.dayFrom.coerceAtMost(month.lengthOfMonth())
    return Income(sourceId = source.id, amount = expected, note = source.name, epochDay = month.atDay(day).toEpochDay(), accountId = source.accountId)
}

@Composable
fun IncomeSourceRow(sm: IncomeMath.SourceMonth, month: YearMonth, accounts: List<com.caudal.finanzas.data.Account>, onEdit: (Editing) -> Unit) {
    val money = LocalMoney.current
    val s = sm.source
    val days = if (s.dayFrom == s.dayTo) "día ${s.dayFrom}" else "días ${s.dayFrom}–${s.dayTo}"
    val account = accounts.firstOrNull { it.id == s.accountId }?.name
    ListRow(
        title = s.name,
        subtitle = listOfNotNull(
            if (s.variable) (if (sm.confirmed != null) "Variable · confirmado este mes" else if (sm.average != null) "Variable · media de 3 meses" else "Variable · estimado") else "Fijo",
            days, account,
        ).joinToString(" · "),
        onClick = { onEdit(Editing.IncomeSourceForm(s)) },
        leading = { IconTile(if (s.variable) Icons.Filled.Bolt else Icons.Filled.Work, if (s.variable) SeriesColors[2] else SeriesColors[0]) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text((if (sm.isEstimate) "~" else "") + money.format(sm.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                if (sm.isReceived) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(12.dp), tint = P.gain)
                        Text(" Cobrado", style = MaterialTheme.typography.labelSmall, color = P.gain)
                    }
                } else {
                    Box(
                        Modifier.padding(top = 2.dp).clip(CircleShape).background(P.brand)
                            .clickable { onEdit(Editing.IncomeForm(newIncomeFor(s, sm.amount, month))) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("Cobrado", style = MaterialTheme.typography.labelSmall, color = P.onBrand, fontWeight = FontWeight.Bold) }
                    if (s.variable) Text(
                        if (sm.confirmed != null) "Cambiar importe" else "¿Cuánto este mes?",
                        style = MaterialTheme.typography.labelSmall, color = P.gain, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp).clip(CircleShape).clickable { onEdit(Editing.ExpectedIncome(s, month)) }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun BudgetScreen(vm: FinanceViewModel, settings: Settings, args: ScreenArgs, onOpenFuture: () -> Unit) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val allExpenses by vm.allExpenses.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val expenses by vm.monthExpenses.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val monthIncome by vm.monthIncome.collectAsStateWithLifecycle()
    val sources by vm.incomeSources.collectAsStateWithLifecycle()
    val money = LocalMoney.current

    // Copia local del reparto: los deslizadores la cambian al momento y se guarda al soltar.
    var draft by remember(categories) { mutableStateOf(categories) }
    val save: () -> Unit = { vm.saveCategories(draft) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    val income = monthIncome.total
    val spentBy = expenses.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val spendablePct = draft.filter { !it.isSavings }.sumOf { it.percent }
    val savingsPct = draft.filter { it.isSavings }.sumOf { it.percent }
    val totalPct = draft.sumOf { it.percent }
    val mainSavings = draft.firstOrNull { it.isSavings }
    val planning by vm.planning.collectAsStateWithLifecycle()
    var tagFilter by remember { mutableStateOf<SpendTag?>(null) }

    val savingIds = categories.filter { it.isSavings }.map { it.id }.toSet()
    val spendingList = expenses.filter { it.categoryId !in savingIds }
    val spentTotal = spendingList.sumOf { it.amount }
    val setAside = expenses.filter { it.categoryId in savingIds }.sumOf { it.amount }
    val received = monthIncome.received
    val tags = byTag(spendingList)
    val isCurrent = month == YearMonth.now()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = args.contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("Movimientos") { args.headerActions() } }
        item { MonthSelector(month, vm::shiftMonth) }

        // ---------- Entró vs salió ----------
        item {
            MintCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Ha entrado", fontSize = 15.sp, color = P.muted)
                        Text(money.format(received), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = P.gain, letterSpacing = (-0.5).sp)
                        if (income > received + 1) Text("de ~${money.format(income)} previstos", fontSize = 13.sp, color = P.muted)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Has gastado", fontSize = 15.sp, color = P.muted)
                        Text(money.format(spentTotal), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = P.ink, letterSpacing = (-0.5).sp)
                        Text("${spendingList.size} pagos", fontSize = 13.sp, color = P.muted)
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Barra: lo gastado, lo apartado para ahorrar y lo que queda, sobre lo que ha entrado.
                val base = maxOf(received, spentTotal + setAside).takeIf { it > 0 } ?: 1.0
                StackedBar(
                    listOf(
                        Slice("Sin gastar", (received - spentTotal - setAside).coerceAtLeast(0.0), P.gain),
                        Slice("Ahorro", setAside, SeriesColors[1]),
                        Slice("Gastado", spentTotal, P.loss),
                    ),
                    height = 12.dp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend("Sin gastar", P.gain); Legend("Apartado", SeriesColors[1]); Legend("Gastado", P.loss)
                }
                Spacer(Modifier.height(12.dp))
                // Tu liquidez es solo la cuenta del banco: los ahorros no se tocan.
                val liquid = accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK }.sumOf { it.balance }
                Text(
                    when {
                        received <= 0 && spentTotal <= 0 -> "Aún no hay movimientos este mes."
                        received <= 0 -> "Aún no has cobrado este mes: llevas ${money.format(spentTotal)} gastados."
                        else -> "Has gastado el ${(spentTotal / received * 100).toInt()} % de lo que ha entrado" +
                            (if (setAside > 0) " y has apartado ${money.format(setAside)}" else "") + "." +
                            if (isCurrent) " En el banco tienes ${money.format(liquid)}: tu dinero disponible (los ahorros no cuentan)." else ""
                    },
                    fontSize = 15.sp, color = P.ink,
                )
            }
        }

        // ---------- La rueda: en qué puedes gastar ----------
        planning?.budget?.takeIf { it.base > 0 && isCurrent }?.let { b ->
            item { BudgetWheelCard(b, onProfile = { args.onEdit(Editing.ProfileForm) }) { args.onEdit(Editing.TagDetail(it)) } }
        }

        // ---------- ¿En qué se ha ido? ----------
        if (tags.isNotEmpty()) {
            item {
                MCard {
                    Text("¿En qué se ha ido?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text("Toca una sección para ver en qué se ha ido, pago a pago", fontSize = 13.sp, color = P.muted)
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(tags.map { Slice(it.first.label, it.second, it.first.color()) }) {
                            Text(money.format(spentTotal), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                            Text("gastado", fontSize = 13.sp, color = P.muted)
                            val liquid = accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK }.sumOf { it.balance }
                            if (isCurrent && liquid > 0) Text("de ${money.format(liquid)}", fontSize = 12.sp, color = P.muted)
                            if (isCurrent && liquid > 0) Text("disponibles en el banco", fontSize = 10.sp, color = P.muted)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    tags.forEach { (t, v) ->
                        val count = spendingList.count { it.spendTag() == t }
                        val selected = tagFilter == t
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (selected) t.color().copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { args.onEdit(Editing.TagDetail(t)) }.padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(t.icon(), t.color(), 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                                Text("$count ${if (count == 1) "pago" else "pagos"}" + if (t.need) " · necesario" else " · prescindible", fontSize = 13.sp, color = P.muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money.format(v), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                                Text(pct(v / spentTotal * 100), fontSize = 13.sp, color = t.color())
                            }
                            Text("  ›", fontSize = 20.sp, color = P.muted)
                        }
                    }
                    val needs = tags.filter { it.first.need }.sumOf { it.second }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Necesario ${money.format(needs)} (${pct(needs / spentTotal * 100)}) · prescindible ${money.format(spentTotal - needs)} (${pct((spentTotal - needs) / spentTotal * 100)})",
                        fontSize = 13.sp, color = P.muted,
                    )
                }
            }
        }

        // ---------- Cuentas con amigos (y Tricount) ----------
        item {
            val debts = remember(settings.debtsRaw) { com.caudal.finanzas.domain.Debts.parse(settings.debtsRaw) }
            val tri = remember(settings.tricountRaw) { com.caudal.finanzas.domain.Debts.parseTricount(settings.tricountRaw) }
            FriendsCard(
                com.caudal.finanzas.domain.Debts.balances(debts, tri),
                onAdd = { args.onEdit(Editing.DebtForm(com.caudal.finanzas.domain.Debt(0, "", 0.0, "", LocalDate.now().toEpochDay()))) },
                onEdit = { args.onEdit(Editing.DebtForm(it)) },
                onTricount = { args.onEdit(Editing.TricountForm(it)) },
                onSettle = vm::settlePerson,
                onSettleOne = vm::settleDebt,
            )
        }

        // ---------- ¿Dónde se ha ido mi dinero? ----------
        planning?.data?.let { d ->
            if (isCurrent) item {
                val story = Insights.monthStory(d)
                if (story.spentPrev > 0 || story.incomePrev > 0) {
                    MCard {
                        Overline("¿Dónde se ha ido tu dinero?")
                        Spacer(Modifier.height(6.dp))
                        Text(Insights.describe(story, money::format), fontSize = 16.sp, color = P.ink, lineHeight = 22.sp)
                    }
                }
            }
        }
        item {
            ShortcutCard("Análisis completo", "Salud financiera, hábitos, radar, máquina del tiempo…", Icons.Filled.Insights, SeriesColors[1]) { args.onOpen(com.caudal.finanzas.ui.Overlay.INSIGHTS) }
        }

        item {
            SectionTitle("Tus ingresos") {
                TextButton(onClick = { args.onEdit(Editing.IncomeSourceForm(newIncomeSource())) }) {
                    Icon(Icons.Filled.Add, null, tint = P.gain); Spacer(Modifier.width(4.dp)); Text("Añadir", color = P.gain)
                }
            }
        }
        item {
            if (sources.isEmpty()) {
                MCard {
                    Text("Añade de dónde te entra el dinero", style = MaterialTheme.typography.titleMedium, color = P.ink)
                    Text(
                        "Nómina fija, autónomo, YouTube… Con su día de cobro. Lo variable se calcula con la media de tus últimos 3 meses.",
                        style = MaterialTheme.typography.bodyMedium, color = P.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    BrandButton("Añadir ingreso", { args.onEdit(Editing.IncomeSourceForm(newIncomeSource())) }, icon = Icons.Filled.Add)
                }
            } else {
                CardList(monthIncome.sources, key = { it.source.id }) { sm -> IncomeSourceRow(sm, month, accounts, args.onEdit) }
            }
        }

        // ---------- Gastos fijos ----------
        item {
            SectionTitle("Gastos fijos") {
                TextButton(onClick = { args.onEdit(Editing.BillForm(com.caudal.finanzas.ui.newBill(categories, settings.cardAccountId.takeIf { it > 0 }))) }) {
                    Icon(Icons.Filled.Add, null, tint = P.gain); Spacer(Modifier.width(4.dp)); Text("Añadir", color = P.gain)
                }
            }
        }
        item {
            if (bills.isEmpty()) {
                MCard {
                    Text("Alquiler, luz, suscripciones, seguros…", style = MaterialTheme.typography.titleMedium, color = P.ink)
                    Text("Apúntalos una vez: así Ahorra, vaca sabe cuánto dinero tienes de verdad para gastar y te avisa antes de quedarte corto.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillsList(bills, allExpenses, categories, accounts, args.onEdit, vm::payBill)
                    TextButton(onClick = onOpenFuture) { Text("Ver tu futuro día a día →", color = P.gain) }
                }
            }
        }

        // ---------- Reparto ----------
        item {
            SectionTitle("Reparto") {
                TextButton(onClick = { args.onEdit(Editing.Categories) }) { Text("Editar nombres", color = P.gain) }
            }
        }
        if (mainSavings != null) {
            item {
                MCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("¿Cuánto quieres ahorrar?", style = MaterialTheme.typography.titleMedium, color = P.ink)
                            Text("El resto se reparte solo entre lo demás", style = MaterialTheme.typography.bodySmall, color = P.muted)
                        }
                        Text(pct(mainSavings.percent), style = MaterialTheme.typography.headlineSmall, color = SeriesColors[1])
                    }
                    Slider(
                        value = mainSavings.percent.toFloat(),
                        onValueChange = { draft = Allocation.rebalance(draft, mainSavings.id, Math.round(it).toDouble()) },
                        onValueChangeFinished = save,
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = SeriesColors[1], activeTrackColor = SeriesColors[1], inactiveTrackColor = P.chip),
                    )
                    Text(
                        "${money.format(income * mainSavings.percent / 100)} al mes · ${money.format(income * mainSavings.percent / 100 * 12)} al año",
                        style = MaterialTheme.typography.bodySmall, color = P.muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Atajos (necesidades / ocio / ahorro):", style = MaterialTheme.typography.labelMedium, color = P.muted)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Allocation.Preset.entries.forEach { p ->
                            Box(
                                Modifier.clip(CircleShape).background(P.chip)
                                    .clickable { draft = Allocation.applyPreset(draft, p); save() }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text("${p.needs.toInt()}/${p.wants.toInt()}/${p.savings.toInt()}", style = MaterialTheme.typography.labelLarge, color = P.ink, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (abs(totalPct - 100) > 0.01) {
            item { SoftTag("El reparto suma ${pct(totalPct)}, no 100 %", P.loss) }
        }
        item {
            Text("Toca una categoría para ajustar su %. Con el candado no se mueve.", style = MaterialTheme.typography.bodySmall, color = P.muted, modifier = Modifier.padding(start = 6.dp))
        }
        item {
            CardList(draft, key = { it.id }) { c ->
                CategoryRow(c, draft.indexOf(c), income, spentBy[c.id] ?: 0.0,
                    expanded = expandedId == c.id,
                    onToggle = { expandedId = if (expandedId == c.id) null else c.id },
                    onPercent = { v -> draft = Allocation.rebalance(draft, c.id, v) },
                    onFinished = save,
                    onLock = { draft = draft.map { if (it.id == c.id) it.copy(locked = !it.locked) else it }; save() },
                )
            }
        }

        item {
            SectionTitle(tagFilter?.let { "Pagos · ${it.label}" } ?: "Pagos del mes") {
                if (tagFilter != null) TextButton(onClick = { tagFilter = null }) { Text("Ver todos", color = P.gain) }
            }
        }
        item {
            val shown = tagFilter?.let { t -> expenses.filter { it.spendTag() == t } } ?: expenses
            if (shown.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Sin pagos este mes", "Los pagos con tarjeta aparecen aquí solos; los demás, apúntalos con el +.")
            } else {
                CardList(shown, key = { it.id }) { e ->
                    ExpenseRow(e, categories, accounts, onClick = { args.onEdit(Editing.ExpenseForm(e)) })
                }
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = P.muted)
    }
}

@Composable
private fun CategoryRow(
    c: Category, index: Int, income: Double, used: Double,
    expanded: Boolean, onToggle: () -> Unit,
    onPercent: (Double) -> Unit, onFinished: () -> Unit, onLock: () -> Unit,
) {
    val money = LocalMoney.current
    val color = SeriesColors[index % SeriesColors.size]
    val budget = income * c.percent / 100
    ListRow(
        title = c.name,
        subtitle = if (income > 0) "${money.format(used)} de ${money.format(budget)}" else money.format(used),
        onClick = onToggle,
        leading = { IconTile(categoryIcon(c), color) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountTrailing(pct(c.percent), if (budget > 0) "usado ${pct(used / budget * 100)}" else null,
                    subColor = if (budget > 0 && used > budget && !c.isSavings) P.loss else P.muted)
                Box(
                    Modifier.padding(start = 6.dp).size(34.dp).clip(CircleShape).background(if (c.locked) P.ink else P.chip).clickable(onClick = onLock),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (c.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        if (c.locked) "Desbloquear ${c.name}" else "Bloquear ${c.name}",
                        Modifier.size(16.dp), tint = if (c.locked) P.card else P.muted,
                    )
                }
            }
        },
        below = {
            ProgressLine(if (budget > 0) used / budget else 0.0, color)
            if (expanded) Slider(
                value = c.percent.toFloat(),
                onValueChange = { onPercent(Math.round(it).toDouble()) },
                onValueChangeFinished = onFinished,
                enabled = !c.locked,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(alpha = 0.5f), inactiveTrackColor = P.chip, disabledThumbColor = P.muted, disabledActiveTrackColor = P.line),
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
        },
    )
}
