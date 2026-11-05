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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.HorizontalDivider
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
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.domain.Insights
import com.caudal.finanzas.domain.monthName
import com.caudal.finanzas.domain.spendTag
import com.caudal.finanzas.ui.AnimatedAmount
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LineChart
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overlay
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.Segmented
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.color
import com.caudal.finanzas.ui.icon
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")

/** Todos los análisis en una pantalla: cada tarjeta responde a una pregunta. */
@Composable
fun InsightsScreen(vm: FinanceViewModel, settings: Settings, onEdit: (Editing) -> Unit, onOpen: (Overlay) -> Unit, onBack: () -> Unit) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val p = planning ?: return
    val d = p.data
    val money = LocalMoney.current
    val fmt: (Double) -> String = money::format
    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val health = remember(d, money) { Insights.health(d, fmt) }
    val cost = remember(d) { Insights.costOfLiving(d) }
    val radar = remember(d) { Insights.radar(d) }
    val habits = remember(d, money) { Insights.habits(d, fmt) }
    val share = remember(d) { Insights.shareable(d) }
    val owed = Insights.owed(d.splits, d.expenses)
    val story = remember(d) { Insights.monthStory(d) }

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
                    Text("Análisis", style = MaterialTheme.typography.headlineMedium, color = P.ink)
                    Text("Tus números, explicados", style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }

        item { FinancialAgeMini(p.age) { onOpen(Overlay.AGE) } }

        // ---------- 3. Dinero invisible y 5. anomalías ----------
        item {
            val rec = remember(d, money, settings.refundIds) { com.caudal.finanzas.domain.RecoverableMoney.find(d, settings.refundIds, fmt) }
            RecoverableCard(rec) { r -> r.expense?.let { onEdit(Editing.ExpenseForm(it)) } }
        }
        item { RecurringCard(remember(d) { com.caudal.finanzas.domain.RecurringDetector.find(d) }) }
        item { TrendsCard(remember(d, money) { com.caudal.finanzas.domain.TrendDetector.find(d, fmt) }) }
        com.caudal.finanzas.domain.PaymentOrganizer.analyze(d, p.bankPlan, fmt)?.let { c -> item { BillCalendarCard(c) } }
        com.caudal.finanzas.domain.PaycheckDistributor.split(d, p.goal, fmt)?.let { s -> item { PaycheckSplitCard(s) } }
        item {
            val anomalies = remember(d, money) { com.caudal.finanzas.domain.Anomalies.find(d, fmt) }
            AnomaliesCard(anomalies) { a -> a.expense?.let { onEdit(Editing.ExpenseForm(it)) } }
        }
        item { ShortcutCard("¿Me lo compro?", "Antes de comprar algo, mira cómo afecta a tu mes y a tus objetivos", Icons.Filled.ShoppingCart, SeriesColors[4]) { onOpen(Overlay.PURCHASE) } }

        // ---------- 20. Salud financiera ----------
        item {
            MintCard {
                Overline("Salud financiera")
                Spacer(Modifier.height(4.dp))
                Text("${health.good} de ${health.indicators.size} en buen estado", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                health.trend?.let { Text(it, fontSize = 14.sp, color = P.muted) }
                Spacer(Modifier.height(12.dp))
                health.indicators.forEach { i ->
                    val c = when (i.status) { Insights.Status.GOOD -> P.gain; Insights.Status.OK -> Color(0xFFFF9F0A); Insights.Status.BAD -> P.loss }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(i.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                                Text(i.value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c)
                            }
                            Text(i.explanation, fontSize = 13.sp, color = P.muted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).padding(14.dp)) {
                    Column {
                        Text("El siguiente paso más útil", fontSize = 13.sp, color = P.muted)
                        Text(health.nextStep, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink)
                    }
                }
            }
        }

        // ---------- 9. Hasta la próxima nómina ----------
        item {
            val s = p.safe
            MCard {
                Overline("Hasta la próxima nómina")
                Spacer(Modifier.height(4.dp))
                AnimatedAmount(maxOf(0.0, s.perDay), 34.sp, if (s.safe > 0) P.gain else P.loss)
                Text(
                    "al día durante ${s.daysToPayday} ${if (s.daysToPayday == 1) "día" else "días"}" +
                        (s.nextPayday?.let { " · cobras el ${it.format(DateTimeFormatter.ofPattern("EEEE d", es))}" } ?: " · hasta fin de mes"),
                    fontSize = 14.sp, color = P.muted,
                )
                val today = d.spending.filter { it.epochDay == d.today.toEpochDay() }.sumOf { it.amount }
                if (today > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Hoy llevas ${fmt(today)}" + if (today > s.perDay && s.perDay > 0) ": te has pasado ${fmt(today - s.perDay)}, así que los próximos días tocan a menos." else ".",
                        fontSize = 14.sp, color = if (today > s.perDay) P.loss else P.ink,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (s.plans > 0) Text("Apartado para ${settings.planName.ifBlank { "tu plan" }}: ${fmt(s.plans)} (no cuenta como gastable)", fontSize = 14.sp, color = SeriesColors[1])
                TextButton(onClick = { onEdit(Editing.PlanForm) }) {
                    Text(if (s.plans > 0) "Cambiar lo apartado" else "Apartar dinero para un plan", color = P.gain)
                }
            }
        }

        // ---------- 7. ¿Dónde se ha ido mi dinero? ----------
        if (story.spentNow > 0 || story.spentPrev > 0) item {
            MCard {
                Overline("¿Dónde se ha ido tu dinero?")
                Spacer(Modifier.height(6.dp))
                Text(Insights.describe(story, fmt), fontSize = 16.sp, lineHeight = 22.sp, color = P.ink)
            }
        }

        // ---------- 12. Coste de vida real ----------
        item {
            MCard {
                Overline("Tu coste de vida real")
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(cost.monthly), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text(" al mes · ${fmt(cost.yearly)} al año", fontSize = 14.sp, color = P.muted, modifier = Modifier.padding(bottom = 5.dp))
                }
                Spacer(Modifier.height(10.dp))
                CostLine("Gastos fijos cada mes", cost.fixed)
                CostLine("Gastos anuales repartidos (seguros…)", cost.yearlySpread)
                CostLine("Tu gasto variable habitual", cost.variable)
                if (cost.forgotten > 0) CostLine("Pagos de una vez al año que sueles olvidar", cost.forgotten)
                cost.thisMonthPace?.let { pace ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (pace < cost.monthly * 0.9) "Este mes vas camino de ${fmt(pace)}: es un mes barato, pero tu estilo de vida real cuesta ${fmt(cost.monthly)}."
                        else if (pace > cost.monthly * 1.1) "Este mes vas camino de ${fmt(pace)}: por encima de lo que cuesta tu vida normal."
                        else "Este mes vas en línea con tu coste de vida normal.",
                        fontSize = 14.sp, color = P.ink,
                    )
                }
            }
        }

        // ---------- 17. Radar de gastos olvidados ----------
        item {
            MCard {
                Overline("Radar de gastos olvidados")
                Spacer(Modifier.height(6.dp))
                if (radar.isEmpty()) Text("Nada a la vista en los próximos 4 meses. Apunta tus gastos anuales (seguros, ITV…) como gasto fijo y te avisaré.", fontSize = 14.sp, color = P.muted)
                radar.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.AutoAwesome, if (r.fromHistory) SeriesColors[3] else SeriesColors[4], 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                            Text(
                                prettyDate(r.due) + if (r.fromHistory) " · lo pagaste hace un año" else " · gasto anual",
                                fontSize = 13.sp, color = P.muted,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt(r.amount), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                            Text("aparta ${fmt(r.monthlyReserve(d.today))}/mes", fontSize = 12.sp, color = SeriesColors[1])
                        }
                    }
                }
            }
        }

        // ---------- 18. Mapa de hábitos ----------
        habits?.let { h ->
            item {
                MCard {
                    Overline("Tus hábitos de gasto")
                    Spacer(Modifier.height(10.dp))
                    val max = (h.byWeekday.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
                    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, label ->
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                val f = (h.byWeekday[i] / max).toFloat().coerceIn(0.04f, 1f)
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight(f * 0.82f).clip(RoundedCornerShape(8.dp))
                                        .background(if (i >= 5) SeriesColors[2] else SeriesColors[1]),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(label, fontSize = 12.sp, color = P.muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    h.notes.forEach { Text("• $it", fontSize = 14.sp, color = P.ink, modifier = Modifier.padding(vertical = 2.dp)) }
                    Text("Últimos ${h.days} días, sin contar gastos fijos.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // ---------- 8. Máquina del tiempo + 16. patrimonio mes a mes ----------
        item { TimeMachineCard(d, fmt) }

        // ---------- 11. Gastos compartibles ----------
        item {
            MCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Gastos que puedes compartir", Modifier.weight(1f))
                    if (owed > 0) Text("Te deben ${fmt(owed)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SeriesColors[1])
                }
                Spacer(Modifier.height(6.dp))
                d.splits.forEach { s ->
                    val e = d.expenses.firstOrNull { it.id == s.expenseId } ?: return@forEach
                    val pendingPeople = (s.people - 1 - s.settled).coerceAtLeast(0)
                    SplitRow(e.note, "Entre ${s.people}" + (if (s.note.isNotBlank()) " · ${s.note}" else "") + if (pendingPeople == 0) " · saldado" else " · faltan $pendingPeople",
                        fmt(e.amount / s.people * pendingPeople), if (pendingPeople == 0) P.gain else SeriesColors[1]) { onEdit(Editing.SplitForm(e)) }
                }
                if (share.isEmpty() && d.splits.isEmpty()) Text("No veo gastos recientes que suelan compartirse (facturas de casa, plataformas, viajes, cenas en grupo).", fontSize = 14.sp, color = P.muted)
                share.forEach { c ->
                    SplitRow(c.expense.note.ifBlank { c.expense.spendTag().label }, c.reason, fmt(c.expense.amount), P.ink) { onEdit(Editing.SplitForm(c.expense)) }
                }
            }
        }

        // ---------- 19. Contrafactual ----------
        item { WhatIfCard(d, fmt) }

        // ---------- 13 y 10 ----------
        item {
            ShortcutCard("Simulador de decisiones", "Mudarte, cambiar de trabajo, comprar un coche…", Icons.Filled.Psychology, SeriesColors[4]) { onOpen(Overlay.SIMULATOR) }
        }
    }
}

@Composable
private fun CostLine(label: String, value: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = P.muted)
        Text(LocalMoney.current.format(value), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = P.ink)
    }
}

@Composable
private fun SplitRow(title: String, sub: String, value: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Filled.Groups, SeriesColors[5], 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, maxLines = 1)
            Text(sub, fontSize = 13.sp, color = P.muted, maxLines = 2)
        }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color)
    }
}


@Composable
private fun TimeMachineCard(d: com.caudal.finanzas.domain.FinanceData, fmt: (Double) -> String) {
    val earliest = Insights.earliestMonthsBack(d)
    val options = listOf(1, 3, 6, 12).filter { it <= maxOf(1, earliest) } + if (earliest > 1 && earliest !in listOf(1, 3, 6, 12)) listOf(earliest) else emptyList()
    var back by rememberSaveable { mutableStateOf(options.lastOrNull { it <= 6 } ?: 1) }
    val t = remember(d, back) { Insights.timeTravel(d, back) }
    val series = remember(d) { Insights.netWorthByMonth(d) }
    MCard {
        Overline("Máquina del tiempo")
        Spacer(Modifier.height(10.dp))
        Segmented(options, back, { if (it == earliest && it !in listOf(1, 3, 6, 12)) "Inicio" else if (it == 12) "1 año" else "$it m" }, { back = it })
        Spacer(Modifier.height(12.dp))
        Insights.describe(t, back, fmt).forEach { Text(it, fontSize = 15.sp, color = P.ink, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        if (series.size >= 2) {
            Spacer(Modifier.height(12.dp))
            Text("Tu patrimonio neto, mes a mes", fontSize = 13.sp, color = P.muted)
            Spacer(Modifier.height(6.dp))
            val max = series.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
            Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                series.forEachIndexed { i, (m, v) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((v / max).toFloat().coerceIn(0.05f, 1f) * 0.8f).clip(RoundedCornerShape(6.dp))
                                .background(if (i == series.lastIndex) P.gain else P.gain.copy(alpha = 0.35f)),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(m.monthName().take(1).uppercase(), fontSize = 11.sp, color = P.muted)
                    }
                }
            }
            val w = d.wealth
            Spacer(Modifier.height(10.dp))
            Text(
                "Liquidez (lo del día a día): ${fmt(w.available)} · Riqueza construida (ahorro + inversión − deudas): ${fmt(w.built)}",
                fontSize = 13.sp, color = P.muted,
            )
        }
    }
}

@Composable
private fun WhatIfCard(d: com.caudal.finanzas.domain.FinanceData, fmt: (Double) -> String) {
    var monthly by rememberSaveable { mutableStateOf(25f) }
    val list = remember(d, monthly) { Insights.whatIfs(d, monthly.toDouble(), fmt) }
    if (list.isEmpty()) return
    MCard {
        Overline("¿Y si…?")
        Text("Simulaciones: no es dinero que hayas perdido, es lo que podrías conseguir.", fontSize = 13.sp, color = P.muted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reservar al mes", Modifier.weight(1f), fontSize = 14.sp, color = P.ink)
            Text(fmt(monthly.toDouble()), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SeriesColors[1])
        }
        Slider(
            monthly, { monthly = (Math.round(it / 5f) * 5f).coerceAtLeast(5f) }, valueRange = 5f..300f,
            colors = SliderDefaults.colors(thumbColor = SeriesColors[1], activeTrackColor = SeriesColors[1], inactiveTrackColor = P.chip),
        )
        list.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(color = P.line, modifier = Modifier.padding(vertical = 8.dp))
            Text(w.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink)
            Text(w.detail, fontSize = 14.sp, color = P.gain)
        }
    }
}
