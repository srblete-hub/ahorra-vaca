package com.caudal.finanzas.ui.auth

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.caudal.finanzas.AppViewModel
import com.caudal.finanzas.security.Biometrics
import com.caudal.finanzas.ui.Initials
import com.caudal.finanzas.ui.P

const val PIN_LENGTH = 6

private val Forest = Color(0xFF0B2E1D)
private val Neon = Color(0xFF34C77B)

@Composable
private fun LightLayout(title: String, subtitle: String, top: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(P.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        top()
        Text(title, style = MaterialTheme.typography.headlineMedium, color = P.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = P.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

/** Teclado numérico con puntos. [leftKey] suele ser el botón de huella. */
@Composable
fun PinPad(
    error: String?,
    enabled: Boolean = true,
    onComplete: (String) -> Unit,
    leftKey: (@Composable () -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    val p = P
    fun press(d: String) {
        if (!enabled || pin.length >= PIN_LENGTH) return
        pin += d
        if (pin.length == PIN_LENGTH) { onComplete(pin); pin = "" }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(PIN_LENGTH) { i ->
                val filled = i < pin.length
                val size by animateDpAsState(if (filled) 16.dp else 12.dp, label = "dot")
                Box(Modifier.size(size).clip(CircleShape).background(if (filled) p.gain else p.ink.copy(alpha = 0.15f)))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(error ?: " ", color = p.loss, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { d -> PinKey(onClick = { press(d.toString()) }) { Text(d.toString(), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = p.ink) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) { leftKey?.invoke() }
            PinKey(onClick = { press("0") }) { Text("0", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = p.ink) }
            Box(Modifier.size(74.dp).clip(CircleShape).clickable { pin = pin.dropLast(1) }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Backspace, "Borrar", tint = p.ink)
            }
        }
    }
}

@Composable
private fun PinKey(onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = P
    Box(
        Modifier.size(74.dp)
            .then(if (!p.isDark) Modifier.shadow(4.dp, CircleShape, spotColor = Color(0x22000000)) else Modifier)
            .clip(CircleShape).background(p.card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Primera vez: bienvenida → PIN → repetir PIN → activar huella. */
@Composable
fun SetupScreen(vm: AppViewModel, activity: FragmentActivity) {
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var firstPin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    when (step) {
        0 -> Welcome(name, { name = it }, onContinue = { step = 1 })
        1 -> LightLayout("Crea tu PIN", "$PIN_LENGTH cifras. Lo usarás si la huella no funciona.") {
            PinPad(error = error, onComplete = { firstPin = it; error = null; step = 2 })
        }
        2 -> LightLayout("Repite el PIN", "Para comprobar que lo has escrito bien.") {
            PinPad(error = error, onComplete = { pin ->
                if (pin != firstPin) {
                    error = "No coincide. Vuelve a crearlo."
                    firstPin = ""
                    step = 1
                } else if (Biometrics.isAvailable(context)) {
                    step = 3
                } else {
                    vm.createAccount(name, pin)
                }
            })
        }
        3 -> LightLayout(
            "Entra con tu huella", "La próxima vez solo tendrás que poner el dedo.",
            top = {
                Box(Modifier.size(120.dp).clip(CircleShape).background(P.mint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Fingerprint, null, Modifier.size(64.dp), tint = P.gain)
                }
                Spacer(Modifier.height(24.dp))
            },
        ) {
            Text(error ?: " ", color = P.loss, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    Biometrics.prompt(
                        activity, "Activar huella",
                        onSuccess = { vm.setBiometric(true); vm.createAccount(name, firstPin) },
                        onError = { error = it },
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = P.brand, contentColor = P.onBrand),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Activar huella", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { vm.createAccount(name, firstPin) }) { Text("Ahora no", color = P.ink) }
        }
    }
}

/** Portada en verde bosque con titular grande, como la de las apps de patrimonio modernas. */
@Composable
private fun Welcome(name: String, onName: (String) -> Unit, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Forest).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 20.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Neon), contentAlignment = Alignment.Center) {
            Text("C", color = Forest, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(36.dp))
        Text(
            buildAnnotatedString {
                append("¿Sabes cuánto tienes\n")
                withStyle(SpanStyle(color = Neon)) { append("ahora mismo?") }
            },
            color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Banco, ahorros en casa, cripto y acciones en un solo número. Y tus pagos con tarjeta se apuntan solos.",
            color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(40.dp))
        TextField(
            name, onName,
            placeholder = { Text("¿Cómo te llamas?") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.1f), unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.5f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Neon,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            enabled = name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Neon, contentColor = Forest,
                disabledContainerColor = Color.White.copy(alpha = 0.12f), disabledContentColor = Color.White.copy(alpha = 0.4f),
            ),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Empezar", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        Spacer(Modifier.height(14.dp))
        Text(
            "Tus datos se guardan solo en este móvil.",
            color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LockScreen(vm: AppViewModel, activity: FragmentActivity, userName: String, biometricEnabled: Boolean) {
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val canUseBiometric = biometricEnabled && Biometrics.isAvailable(context)

    val askFingerprint = {
        Biometrics.prompt(
            activity, "Desbloquear Ahorra, vaca",
            onSuccess = { vm.unlockWithBiometric() },
            onError = { error = it },
        )
    }
    LaunchedEffect(Unit) { if (canUseBiometric) askFingerprint() }

    LightLayout(
        if (userName.isNotBlank()) "Hola, $userName" else "Hola",
        if (canUseBiometric) "Pon el dedo en el lector o escribe tu PIN" else "Escribe tu PIN",
        top = {
            Initials(userName.ifBlank { "?" }.take(1), P.gain, 64.dp)
            Spacer(Modifier.height(18.dp))
        },
    ) {
        PinPad(
            error = error,
            onComplete = { pin ->
                error = when (val r = vm.unlockWithPin(pin)) {
                    AppViewModel.PinResult.Ok -> null
                    is AppViewModel.PinResult.Wrong -> "PIN incorrecto. Te quedan ${r.attemptsLeft} intentos."
                    is AppViewModel.PinResult.LockedOut -> "Demasiados intentos. Espera ${r.seconds} s."
                }
            },
            leftKey = if (canUseBiometric) {
                {
                    Box(Modifier.size(74.dp).clip(CircleShape).background(P.mint).clickable { askFingerprint() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Fingerprint, "Usar huella", Modifier.size(34.dp), tint = P.gain)
                    }
                }
            } else null,
        )
    }
}
