package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.domain.Insights
import com.caudal.finanzas.ui.AmountField
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.Segmented
import com.caudal.finanzas.ui.editable
import com.caudal.finanzas.ui.toAmount

/** Cada decisión es una lista de campos y una fórmula que da (cambio de ingreso, cambio de gasto, pago inicial). */
private enum class Decision(val label: String, val intro: String, val fields: List<String>) {
    MOVE("Mudarme", "Cambiar de casa: nuevo alquiler y lo que cuesta la mudanza.", listOf("Nuevo alquiler al mes", "Fianza y mudanza")),
    JOB("Otro trabajo", "Cambiar de trabajo: sueldo neto nuevo y lo que cambia ir y volver.", listOf("Nuevo sueldo neto al mes", "Transporte extra al mes")),
    CAR("Coche", "Comprar un coche: entrada, financiación, seguro y gasolina.", listOf("Precio del coche", "Entrada", "Meses de financiación", "Seguro al año", "Gasolina al mes")),
    ALONE("Vivir solo", "Irte a vivir solo: alquiler, facturas y compra, menos lo que pagas ahora en casa.", listOf("Alquiler", "Luz, agua e internet", "Compra extra al mes", "Lo que pagas ahora en casa")),
}

/** Simulador de decisiones vitales: compara tu situación actual con un escenario paralelo. */
@Composable
fun SimulatorScreen(vm: FinanceViewModel, onBack: () -> Unit) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val p = planning ?: return
    val base = remember(p.data) { Insights.simBase(p.data) }
    val money = LocalMoney.current
    var decision by rememberSaveable { mutableStateOf(Decision.MOVE) }
    val values = remember { mutableStateMapOf<String, String>() }
    fun v(k: String) = values["${decision.name}:$k"]?.toAmount() ?: 0.0
    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val (incomeDelta, costDelta, upfront) = when (decision) {
        Decision.MOVE -> Triple(0.0, if (v("Nuevo alquiler al mes") > 0) v("Nuevo alquiler al mes") - base.rent else 0.0, v("Fianza y mudanza"))
        Decision.JOB -> Triple(if (v("Nuevo sueldo neto al mes") > 0) v("Nuevo sueldo neto al mes") - base.fixedIncome else 0.0, v("Transporte extra al mes"), 0.0)
        Decision.CAR -> {
            val months = v("Meses de financiación").takeIf { it > 0 } ?: 1.0
            val financed = (v("Precio del coche") - v("Entrada")).coerceAtLeast(0.0)
            val monthly = if (v("Precio del coche") > 0) financed / months + v("Seguro al año") / 12 + v("Gasolina al mes") + 30 else v("Seguro al año") / 12 + v("Gasolina al mes")
            Triple(0.0, monthly, v("Entrada"))
        }
        Decision.ALONE -> Triple(0.0, v("Alquiler") + v("Luz, agua e internet") + v("Compra extra al mes") - v("Lo que pagas ahora en casa"), v("Alquiler") * 2)
    }
    val r = Insights.simulate(base, incomeDelta, costDelta, upfront)

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
                    Text("¿Qué pasaría si…?", style = MaterialTheme.typography.headlineMedium, color = P.ink)
                    Text("Tu vida ahora frente a un escenario paralelo", style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }
        item { Segmented(Decision.entries, decision, { it.label }, { decision = it }) }
        if (decision == Decision.MOVE) item { MoveComparison(base) }
        if (decision != Decision.MOVE) item {
            MCard {
                Text(decision.intro, fontSize = 15.sp, color = P.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ahora: ingresas ${money.format(base.income)} y tu vida cuesta ${money.format(base.cost)} al mes" +
                        if (decision == Decision.MOVE && base.rent > 0) " (alquiler ${money.format(base.rent)})." else ".",
                    fontSize = 13.sp, color = P.muted,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    decision.fields.forEach { f ->
                        val key = "${decision.name}:$f"
                        AmountField(values[key] ?: "", { values[key] = it }, f, suffix = if (f.startsWith("Meses")) null else "€")
                    }
                }
                if (decision == Decision.ALONE) Text("Contamos dos meses de alquiler de fianza como pago inicial.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (decision != Decision.MOVE) item {
            MintCard {
                Overline("Resultado")
                Spacer(Modifier.height(8.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Ahora", fontSize = 14.sp, color = P.muted)
                        Text(money.signed(r.savingNow), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (r.savingNow >= 0) P.gain else P.loss)
                        Text("ahorras al mes", fontSize = 12.sp, color = P.muted)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Con el cambio", fontSize = 14.sp, color = P.muted)
                        Text(money.signed(r.savingAfter), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (r.savingAfter >= 0) P.gain else P.loss)
                        Text("ahorrarías al mes", fontSize = 12.sp, color = P.muted)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = P.line)
                Compare("Dinero en 1 año", money.format(r.in1yNow), money.format(r.in1yAfter))
                Compare("Dinero en 5 años", money.format(r.in5yNow), money.format(r.in5yAfter))
                Compare("Colchón (meses de vida)", "%.1f".format(r.emergencyNow), "%.1f".format(r.emergencyAfter))
                if (upfront > 0) Compare("Pago inicial", "—", money.format(upfront))
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        r.runwayMonths != null -> "Con este cambio perderías ${money.format(-r.savingAfter)} al mes: tus ahorros durarían unos ${r.runwayMonths.toInt()} meses."
                        r.savingAfter < r.savingNow * 0.5 && r.savingNow > 0 -> "Es posible, pero ahorrarías menos de la mitad que ahora. Mira si puedes compensarlo en otra parte."
                        r.savingAfter >= r.savingNow -> "Saldrías ganando: ahorrarías ${money.format(r.savingAfter - r.savingNow)} más al mes."
                        else -> "Asumible: ahorrarías ${money.format(r.savingNow - r.savingAfter)} menos al mes."
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.ink,
                )
                Text("Estimación con tus ingresos y tu coste de vida reales; no incluye intereses ni subidas de precios.", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun Compare(label: String, now: String, after: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = P.muted)
        Text(now, Modifier.width(96.dp), fontSize = 14.sp, color = P.ink)
        Text(after, Modifier.width(96.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Una forma de vivir: lo que cuesta al mes y lo que hay que pagar de golpe al empezar. */
private data class Housing(val name: String, val monthly: Double, val upfront: Double, val detail: String)

/**
 * Mudarse, comparado de verdad: seguir donde estás, el piso nuevo, compartir o uno más barato, con el
 * coste total (fianza, mudanza, suministros y transporte), no solo el alquiler anunciado.
 */
@Composable
private fun MoveComparison(base: Insights.SimBase) {
    val money = LocalMoney.current
    val f = remember { mutableStateMapOf<String, String>() }
    fun v(k: String, def: Double) = f[k]?.toAmount() ?: def
    val rentNow = v("Tu alquiler ahora", base.rent)
    val newRent = v("Alquiler del piso nuevo", 750.0)
    val depositMonths = v("Meses de fianza", 2.0)
    val moving = v("Mudanza y muebles", 300.0)
    val utilities = v("Suministros extra al mes (luz, agua, internet)", 0.0)
    val transport = v("Transporte extra al mes", 0.0)
    val shared = v("Tu parte si compartes piso", 400.0)
    val cheaper = v("Alquiler de un piso más barato", 600.0)

    val options = listOf(
        Housing("Seguir donde estás", rentNow, 0.0, "Sin cambios"),
        Housing("Mudarte a ese piso", newRent + utilities + transport, newRent * depositMonths + moving, "Fianza ${money.format(newRent * depositMonths)} + mudanza ${money.format(moving)}"),
        Housing("Compartir vivienda", shared + utilities / 2 + transport, shared * depositMonths + moving / 2, "Fianza de tu parte + media mudanza"),
        Housing("Un piso más barato", cheaper + utilities + transport, cheaper * depositMonths + moving, "Fianza ${money.format(cheaper * depositMonths)} + mudanza ${money.format(moving)}"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MCard {
            Text("Cuéntame el piso que estás mirando", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text("Rellena lo que sepas; lo demás lleva un valor de ejemplo.", fontSize = 13.sp, color = P.muted)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Tu alquiler ahora" to base.rent, "Alquiler del piso nuevo" to 750.0, "Meses de fianza" to 2.0, "Mudanza y muebles" to 300.0,
                    "Suministros extra al mes (luz, agua, internet)" to 0.0, "Transporte extra al mes" to 0.0,
                    "Tu parte si compartes piso" to 400.0, "Alquiler de un piso más barato" to 600.0,
                ).forEach { (k, def) ->
                    AmountField(f[k] ?: def.editable(), { f[k] = it }, k, suffix = if (k.startsWith("Meses")) null else "€")
                }
            }
        }
        options.forEach { o ->
            val costAfter = base.cost - rentNow + o.monthly
            val saving = base.income - costAfter
            val start = base.savings - o.upfront
            MCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.name, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text(money.signed(saving) + "/mes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (saving >= 0) P.gain else P.loss)
                }
                Text(o.detail, fontSize = 13.sp, color = P.muted)
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = P.line)
                Compare("Vivienda al mes (todo incluido)", "", money.format(o.monthly))
                Compare("Pago al empezar", "", money.format(o.upfront))
                Compare("Gastos fijos del mes", "", money.format(costAfter))
                Compare("Tu dinero en 6 meses", "", money.format(start + saving * 6))
                Compare("Tu dinero en 12 meses", "", money.format(start + saving * 12))
                Compare("Colchón (meses de vida)", "", if (costAfter > 0) "%.1f".format(start / costAfter) else "—")
            }
        }
        Text("Estimación con tus ingresos y tu coste de vida reales; no incluye subidas de precio.", fontSize = 12.sp, color = P.muted)
    }
}
