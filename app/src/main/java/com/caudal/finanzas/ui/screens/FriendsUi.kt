package com.caudal.finanzas.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
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
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.domain.Debt
import com.caudal.finanzas.domain.FriendBalance
import com.caudal.finanzas.domain.TricountBalance
import com.caudal.finanzas.ui.BigAmountInput
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.FieldLabel
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.Segmented
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.TextInput
import com.caudal.finanzas.ui.currencySymbol
import com.caudal.finanzas.ui.editable
import com.caudal.finanzas.ui.toAmount
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("es-ES"))
private fun dayText(epochDay: Long) = LocalDate.ofEpochDay(epochDay).format(dayFmt)

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) P.brand else P.chip).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 14.sp, color = if (selected) P.onBrand else P.ink, fontWeight = FontWeight.Medium) }
}

/** Cuentas con amigos: quién te debe y a quién debes, con el saldo de Tricount si lo usas. */
@Composable
fun FriendsCard(
    friends: List<FriendBalance>,
    onAdd: () -> Unit,
    onEdit: (Debt) -> Unit,
    onTricount: (String) -> Unit,
    onSettle: (String) -> Unit,
    onSettleOne: (Long) -> Unit,
) {
    val money = LocalMoney.current
    var open by remember { mutableStateOf<String?>(null) }
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cuentas con amigos", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text("+ Añadir", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.gain, modifier = Modifier.clip(CircleShape).clickable(onClick = onAdd).padding(8.dp))
        }
        if (friends.isEmpty()) {
            Text(
                "¿Pagaste el pádel y la mitad era de Juanpa? Ábrelo y toca «Alguien me debe parte», o añade aquí lo que debes o te deben. Si usáis Tricount, puedes poner su saldo.",
                fontSize = 14.sp, color = P.muted,
            )
            return@MCard
        }
        val owed = friends.filter { it.net > 0 }.sumOf { it.net }
        val owe = -friends.filter { it.net < 0 }.sumOf { it.net }
        Row(Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Te deben", fontSize = 13.sp, color = P.muted)
                Text(money.format(owed), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = P.gain)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Debes", fontSize = 13.sp, color = P.muted)
                Text(money.format(owe), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (owe > 0) P.loss else P.ink)
            }
        }
        friends.forEach { f ->
            val expanded = open == f.person
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(P.chip).padding(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable { open = if (expanded) null else f.person }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(SeriesColors[abs(f.person.hashCode()) % SeriesColors.size].copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text(f.person.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.person, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink)
                        Text(
                            when {
                                f.net > 0.005 -> "Te debe ${money.format(f.net)}"
                                f.net < -0.005 -> "Le debes ${money.format(-f.net)}"
                                else -> "En paz"
                            } + "  ${if (expanded) "▴" else "▾"}",
                            fontSize = 14.sp, color = when { f.net > 0.005 -> P.gain; f.net < -0.005 -> P.loss; else -> P.muted },
                        )
                    }
                    Box(
                        Modifier.clip(CircleShape).background(P.brand).clickable { onSettle(f.person) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    ) { Text("Se ha pagado", fontSize = 13.sp, color = P.onBrand, fontWeight = FontWeight.Bold) }
                }
                Text(
                    f.tricount?.let { t ->
                        "Tricount: " + (if (t.amount >= 0) "te debe ${money.format(t.amount)}" else "le debes ${money.format(-t.amount)}") + " (al ${dayText(t.epochDay)}) · cambiar"
                    } ?: "¿Lo lleváis en Tricount? Pon su saldo",
                    fontSize = 13.sp, color = SeriesColors[1],
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { onTricount(f.person) }.padding(vertical = 2.dp),
                )
                if (expanded) f.open.forEach { d ->
                    val inside = d.inTricount && f.tricount != null && d.epochDay <= f.tricount.epochDay
                    Row(Modifier.fillMaxWidth().clickable { onEdit(d) }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.note.ifBlank { "Sin concepto" }, fontSize = 14.sp, color = P.ink)
                            Text(dayText(d.epochDay) + if (d.inTricount) (if (inside) " · ya incluido en Tricount" else " · en Tricount") else "", fontSize = 12.sp, color = P.muted)
                        }
                        Text(money.signed(d.amount), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (inside) P.muted else if (d.amount >= 0) P.gain else P.loss)
                        Spacer(Modifier.width(8.dp))
                        Text("Pagado", fontSize = 12.sp, color = P.gain, modifier = Modifier.clip(CircleShape).clickable { onSettleOne(d.id) }.padding(6.dp))
                    }
                }
            }
        }
        Text("«Se ha pagado» deja a esa persona en paz (también el saldo de Tricount).", fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Apuntar que alguien te debe (o que debes): desde un gasto se propone la mitad. */
@Composable
fun DebtSheet(initial: Debt, total: Double?, people: List<String>, currency: String, onSave: (Debt) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    var person by remember { mutableStateOf(initial.person) }
    var owesMe by remember { mutableStateOf(initial.amount >= 0) }
    var amount by remember { mutableStateOf(abs(initial.amount).editable()) }
    var note by remember { mutableStateOf(initial.note) }
    var tricount by remember { mutableStateOf(initial.inTricount) }
    val v = amount.toAmount()
    FormSheet(
        if (initial.id == 0L) "Cuenta con un amigo" else "Editar cuenta", onDismiss,
        onPrimary = { onSave(initial.copy(person = person.trim(), amount = if (owesMe) v!! else -v!!, note = note.trim(), inTricount = tricount)) },
        primaryEnabled = v != null && v > 0 && person.isNotBlank(),
        onDelete = onDelete,
    ) {
        if (total != null) Text("${initial.note.ifBlank { "Gasto" }} · pagaste ${money.format(total)}", fontSize = 15.sp, color = P.muted)
        Segmented(listOf(true, false), owesMe, { if (it) "Me debe" else "Le debo" }, { owesMe = it })
        TextInput(person, { person = it }, "¿Quién? (Juanpa…)")
        if (people.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            people.take(6).forEach { p -> Pill(p, p.equals(person.trim(), true)) { person = p } }
        }
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        if (total != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("La mitad", v == total / 2, { amount = (total / 2).editable() })
            Pill("Un tercio", false, { amount = (Math.round(total / 3 * 100) / 100.0).editable() })
            Pill("Todo", v == total, { amount = total.editable() })
        }
        TextInput(note, { note = it }, "Concepto (pádel, cena…)")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).clickable { tricount = !tricount }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (tricount) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null, tint = if (tricount) P.gain else P.muted)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("También está en Tricount", fontSize = 15.sp, color = P.ink)
                Text("Cuando pongas el saldo de Tricount no se contará dos veces.", fontSize = 12.sp, color = P.muted)
            }
        }
        if (v != null && v > 0 && person.isNotBlank()) Text(
            if (owesMe) "${person.trim()} te debe ${money.format(v)}" else "Le debes ${money.format(v)} a ${person.trim()}",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (owesMe) P.gain else P.loss,
        )
    }
}

/** El saldo que marca Tricount con una persona: manda sobre lo apuntado "en Tricount" hasta hoy. */
@Composable
fun TricountSheet(person: String, current: TricountBalance?, currency: String, onSave: (String, Double) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(person) }
    var owesMe by remember { mutableStateOf((current?.amount ?: 0.0) >= 0) }
    var amount by remember { mutableStateOf(abs(current?.amount ?: 0.0).editable()) }
    val v = if (amount.isBlank()) 0.0 else amount.toAmount()
    FormSheet("Saldo de Tricount", onDismiss, onPrimary = { onSave(name.trim(), if (owesMe) v!! else -v!!) }, primaryEnabled = v != null && name.isNotBlank()) {
        Text("Pon lo que te dice Tricount ahora mismo. Lo apuntado «en Tricount» hasta hoy ya va dentro; lo que no esté en Tricount (o lo que apuntes después) se suma o resta encima.", fontSize = 14.sp, color = P.muted)
        TextInput(name, { name = it }, "Con quién")
        Segmented(listOf(true, false), owesMe, { if (it) "Me debe" else "Le debo" }, { owesMe = it })
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        Text("0 = estáis en paz en Tricount.", fontSize = 12.sp, color = P.muted)
    }
}

/** Lo que te pagará este mes un ingreso variable (YouTube…), cuando ya lo sabes. */
@Composable
fun ExpectedIncomeSheet(source: IncomeSource, month: YearMonth, current: Double?, average: Double?, currency: String, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    var amount by remember { mutableStateOf(current?.editable() ?: "") }
    val v = amount.toAmount()
    val es = java.util.Locale.forLanguageTag("es-ES")
    FormSheet("${source.name} en ${month.month.getDisplayName(java.time.format.TextStyle.FULL, es)}", onDismiss, onPrimary = { onSave(v) }, primaryEnabled = v != null && v >= 0) {
        Text(
            "¿Cuánto te pagará el día ${source.dayFrom}? Con la cantidad confirmada, el consejo de hoy y el reparto la usan tal cual en vez de estimar.",
            fontSize = 14.sp, color = P.muted,
        )
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        if (average != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Media: ${money.format(average)}", false) { amount = (Math.round(average)).toDouble().editable() }
        }
        Text(
            if (current == null) "Ahora se estima con el 70 % de tu media${average?.let { " (${money.format(it * 0.7)})" } ?: ""}, por prudencia." else "Confirmado: ${money.format(current)}.",
            fontSize = 13.sp, color = P.muted,
        )
        if (current != null) Text(
            "Volver a la estimación", fontSize = 15.sp, color = P.gain,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSave(null) }.padding(8.dp),
        )
    }
}

/** Aviso en Inicio: a principios de mes ya sabes lo que te pagará YouTube; ponlo con un toque. */
@Composable
fun ExpectedIncomeNudge(source: IncomeSource, onSet: () -> Unit) {
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Bolt, SeriesColors[2])
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("¿Ya sabes cuánto te paga ${source.name} este mes?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("Cobras el día ${source.dayFrom}. Ponlo y el consejo dejará de estimar.", fontSize = 13.sp, color = P.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        BrandButton("Poner importe", onSet, Modifier.fillMaxWidth())
    }
}

@Suppress("unused") private val keepColor = Color.Unspecified
