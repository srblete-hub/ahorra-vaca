package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.BuyCheck
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.TextInput
import com.caudal.finanzas.ui.color
import com.caudal.finanzas.ui.icon
import com.caudal.finanzas.ui.toAmount

/** Tarjeta de Inicio: escribes qué quieres comprar y cuánto cuesta, y ves si te lo puedes permitir. */
@Composable
fun BuyCheckCard(onCheck: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    val value = price.toAmount()
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFF9F0A).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ShoppingBag, null, Modifier.size(24.dp), tint = Color(0xFFFF9F0A))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("¿Lo compro o no?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("Mira lo que te cuesta de verdad antes de pagar", fontSize = 14.sp, color = P.muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        TextInput(name, { name = it }, "Qué es (ej.: camiseta Eme Studio)")
        Spacer(Modifier.height(8.dp))
        TextInput(price, { price = it }, "Cuánto cuesta (€)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(16.dp))
        BrandButton("Simular", { value?.let { onCheck(name.ifBlank { "Esta compra" }, it) } }, Modifier.fillMaxWidth(), enabled = value != null && value > 0)
    }
}


/** El resultado: veredicto, horas de trabajo, cómo queda tu mes, tu límite, tu objetivo y si lo invirtieras. */
@Composable
fun BuyCheckSheet(c: BuyCheck, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val (vColor, vTitle) = when (c.verdict) {
        0 -> P.gain to "Puedes"
        1 -> Color(0xFFFF9F0A) to "Piénsalo"
        else -> P.loss to "Mejor no ahora"
    }
    FormSheet("¿Lo compro?", onDismiss, primary = "Entendido", onPrimary = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.tag.color().copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(c.tag.icon(), null, Modifier.size(24.dp), tint = c.tag.color())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 2)
                Text(c.tag.label, fontSize = 14.sp, color = P.muted)
            }
            Text(money.format(c.price), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = P.ink)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vColor.copy(alpha = 0.14f)).padding(16.dp)) {
            Text(vTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = vColor)
            Text(c.verdictText, fontSize = 16.sp, color = P.ink, modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.chip).padding(vertical = 8.dp)) {
            c.facts.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = P.line, thickness = 0.6.dp)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.title, Modifier.weight(1f), fontSize = 14.sp, color = P.muted)
                        Text(f.value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    }
                    Text(f.detail, fontSize = 12.sp, color = P.muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.chip).padding(16.dp)) {
            Text("Si en vez de comprarlo lo invirtieras al ${String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", c.rate).replace(",0", "")} %", fontSize = 14.sp, color = P.muted)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                c.invested.forEach { (y, v) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(money.format(v), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.gain)
                        Text(if (y == 1) "en 1 año" else "en $y años", fontSize = 12.sp, color = P.muted)
                    }
                }
            }
        }
        Text("Cálculo con tus datos y reglas fijas (sin IA). La decisión es tuya.", fontSize = 12.sp, color = P.muted)
    }
}
