package com.caudal.finanzas.social

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caudal.finanzas.domain.Achievement
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.CardList
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.TextInput
import com.caudal.finanzas.ui.screens.AchievementBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

val VerifiedBlue = Color(0xFF1D9BF0)

/** El verificado azul. */
@Composable
fun VerifiedBadge(size: Dp = 20.dp) {
    Icon(Icons.Filled.Verified, "Verificado", Modifier.size(size), tint = VerifiedBlue)
}

/** Nombre con su verificado al lado si lo tiene. */
@Composable
fun NameWithBadge(name: String, verified: Boolean, fontSize: Int = 16, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, color = P.ink, maxLines = 1)
        if (verified) { Spacer(Modifier.width(4.dp)); VerifiedBadge((fontSize * 1.05).dp) }
    }
}

private val avatarCache = mutableStateMapOf<String, ImageBitmap>()

/** Foto de un perfil remoto (con caché en memoria) o su inicial. */
@Composable
fun RemoteAvatar(url: String?, name: String, size: Dp) {
    val bitmap by produceState(url?.let { avatarCache[it] }, url) {
        if (url != null && avatarCache[url] == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }?.also { avatarCache[url] = it }
        }
    }
    val b = bitmap
    if (b != null) Image(b, name, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size).clip(CircleShape).background(SeriesColors[(name.hashCode() and 0x7fffffff) % SeriesColors.size]), contentAlignment = Alignment.Center) {
        Text(name.ifBlank { "?" }.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ProfileRow(p: RemoteProfile, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteAvatar(p.avatarUrl, p.name.ifBlank { p.handle }, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NameWithBadge(p.name.ifBlank { p.handle }, p.verified)
            Text("@${p.handle} · Nivel ${p.level} ${p.levelName}", style = MaterialTheme.typography.bodySmall, color = P.muted, maxLines = 1)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SmallButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (filled) P.brand else P.chip).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (filled) P.onBrand else P.ink) }
}

/** Sección "Amigos" del perfil: crear cuenta, solicitudes, amigos y buscar. */
@Composable
fun FriendsSection(state: SocialViewModel.State, hasHandle: Boolean, vm: SocialViewModel, onEditProfile: () -> Unit) {
    var showAuth by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var openFriend by remember { mutableStateOf<RemoteProfile?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.error?.let { msg ->
            MCard(color = P.loss.copy(alpha = 0.12f), padding = 14.dp, onClick = vm::clearError) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = P.ink)
            }
        }
        when {
            !state.configured -> MCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(SeriesColors[1].copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Group, null, tint = SeriesColors[1]) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Muy pronto", style = MaterialTheme.typography.titleMedium, color = P.ink)
                        Text("Busca a tus amigos por su @, mándales solicitud y celebra sus logros. Tus importes nunca se ven.", style = MaterialTheme.typography.bodySmall, color = P.muted)
                    }
                }
            }
            !state.signedIn -> MCard {
                Text("Conecta con tus amigos", style = MaterialTheme.typography.titleMedium, color = P.ink)
                Text(
                    "Crea tu cuenta de Ahorra, vaca con tu email (te mandamos un código, sin contraseñas). Tus amigos verán tu @, tu foto, tu nivel y los logros que elijas. Nunca tus cuentas ni tus importes.",
                    style = MaterialTheme.typography.bodySmall, color = P.muted,
                )
                Spacer(Modifier.height(12.dp))
                BrandButton("Crear cuenta o entrar", { showAuth = true }, Modifier.fillMaxWidth())
            }
            !hasHandle -> MCard {
                Text("Elige tu @", style = MaterialTheme.typography.titleMedium, color = P.ink)
                Text("Es como te encontrarán tus amigos.", style = MaterialTheme.typography.bodySmall, color = P.muted)
                Spacer(Modifier.height(10.dp))
                BrandButton("Elegir mi @", onEditProfile, Modifier.fillMaxWidth())
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandButton("Buscar amigos", { showSearch = true }, Modifier.weight(1f), icon = Icons.Filled.Search)
                }
                if (state.incoming.isNotEmpty()) {
                    Overline("Solicitudes (${state.incoming.size})", Modifier.padding(start = 6.dp))
                    CardList(state.incoming, key = { it.id }) { p ->
                        ProfileRow(p) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallButton("Aceptar", true) { vm.accept(p) }
                                SmallButton("No", false) { vm.remove(p) }
                            }
                        }
                    }
                }
                if (state.friends.isEmpty()) {
                    MCard {
                        Text("Aún no tienes amigos en Ahorra, vaca", style = MaterialTheme.typography.titleMedium, color = P.ink)
                        Text("Búscalos por su @ y mándales una solicitud.", style = MaterialTheme.typography.bodySmall, color = P.muted)
                    }
                } else {
                    Overline("Tus amigos (${state.friends.size})", Modifier.padding(start = 6.dp))
                    CardList(state.friends.sortedByDescending { it.points }, key = { it.id }) { p -> ProfileRow(p, onClick = { openFriend = p }) }
                }
                if (state.outgoing.isNotEmpty()) {
                    Overline("Enviadas", Modifier.padding(start = 6.dp))
                    CardList(state.outgoing, key = { it.id }) { p ->
                        ProfileRow(p) { SmallButton("Cancelar", false) { vm.remove(p) } }
                    }
                }
            }
        }
    }

    if (showAuth) AuthSheet(state, vm, onDone = { showAuth = false })
    if (showSearch) SearchSheet(state, vm, onDone = { showSearch = false })
    openFriend?.let { FriendSheet(it, vm, onDone = { openFriend = null }) }
}

@Composable
private fun AuthSheet(state: SocialViewModel.State, vm: SocialViewModel, onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val validEmail = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    FormSheet(
        if (sent) "Mira tu correo" else "Tu cuenta de Ahorra, vaca", onDone,
        primary = if (state.loading) "Un momento…" else if (sent) "Entrar" else "Enviarme el código",
        onPrimary = { if (sent) vm.verify(email, code, onDone) else vm.sendCode(email) { sent = true } },
        primaryEnabled = !state.loading && if (sent) code.trim().length >= 6 else validEmail,
    ) {
        if (!sent) {
            Text("Te mandamos un código de 6 cifras. Sin contraseñas.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
            TextInput(email, { email = it.trim() }, "Tu email", keyboardType = KeyboardType.Email)
        } else {
            Text("Escribe el código que te hemos enviado a $email.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
            TextInput(code, { code = it.filter(Char::isDigit).take(8) }, "Código", keyboardType = KeyboardType.Number)
            TextButton(onClick = { sent = false; code = "" }) { Text("Cambiar de email", color = P.muted) }
        }
        state.error?.let { Text(it, color = P.loss, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SearchSheet(state: SocialViewModel.State, vm: SocialViewModel, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<RemoteProfile>()) }
    var searching by remember { mutableStateOf(false) }
    // Busca mientras escribes (con una pequeña espera para no lanzar una búsqueda por tecla).
    LaunchedEffect(query) {
        if (query.trim().removePrefix("@").length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = vm.search(query)
        searching = false
    }
    FormSheet("Buscar amigos", onDone, primary = null) {
        TextInput(query, { query = it.replace(" ", "") }, "@ de tu amigo")
        if (searching) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = P.gain)
        if (!searching && query.length >= 2 && results.isEmpty()) Text("No hay nadie con ese @ todavía.", color = P.muted, style = MaterialTheme.typography.bodySmall)
        results.forEach { p ->
            val status = when {
                state.friends.any { it.id == p.id } -> "Amigos"
                state.outgoing.any { it.id == p.id } -> "Enviada"
                state.incoming.any { it.id == p.id } -> "Aceptar"
                else -> null
            }
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(P.chip)) {
                ProfileRow(p) {
                    when (status) {
                        null -> Box(
                            Modifier.size(40.dp).clip(CircleShape).background(P.brand).clickable { vm.sendRequest(p) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.PersonAdd, "Añadir a ${p.handle}", Modifier.size(20.dp), tint = P.onBrand) }
                        "Aceptar" -> SmallButton("Aceptar", true) { vm.accept(p) }
                        else -> Text(status, style = MaterialTheme.typography.labelLarge, color = P.muted)
                    }
                }
            }
        }
    }
}

/** Perfil de un amigo: su nivel y los logros que ha compartido. */
@Composable
private fun FriendSheet(p: RemoteProfile, vm: SocialViewModel, onDone: () -> Unit) {
    val achievements by produceState<List<SharedAchievement>?>(null, p.id) { value = vm.achievementsOf(p.id) }
    var confirmRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FormSheet(p.name.ifBlank { p.handle }, onDone, primary = null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteAvatar(p.avatarUrl, p.name.ifBlank { p.handle }, 96.dp)
            Spacer(Modifier.height(10.dp))
            NameWithBadge(p.name.ifBlank { p.handle }, p.verified, fontSize = 22)
            Text("@${p.handle}", color = P.muted)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.clip(CircleShape).background(SeriesColors[3].copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("Nivel ${p.level} · ${p.levelName} · ${p.points} pts", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SeriesColors[3])
            }
        }
        Overline("Sus logros")
        val list = achievements
        when {
            list == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = P.gain)
            list.isEmpty() -> Text("Todavía no ha compartido logros.", color = P.muted)
            else -> list.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { a ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            AchievementBadge(Achievement(a.id, a.title, "", a.icon, 0, 1.0), 56.dp)
                            Text(a.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = P.ink, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        TextButton(onClick = { confirmRemove = true }) { Text("Quitar de amigos", color = P.loss) }
        if (confirmRemove) {
            com.caudal.finanzas.ui.ConfirmDeleteDialog("a @${p.handle} de tus amigos", onConfirm = { scope.launch { vm.remove(p); onDone() } }, onDismiss = { confirmRemove = false })
        }
    }
}
