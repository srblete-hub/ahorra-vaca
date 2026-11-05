package com.caudal.finanzas.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.AppViewModel
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.domain.Achievement
import com.caudal.finanzas.ui.Avatar
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.MintCard
import com.caudal.finanzas.ui.Overline
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.ProgressLine
import com.caudal.finanzas.ui.ScreenArgs
import com.caudal.finanzas.ui.SectionTitle
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.TextInput

private fun iconFor(key: String): Pair<ImageVector, Color> = when (key) {
    "flag" -> Icons.Filled.Flag to SeriesColors[1]
    "card" -> Icons.Filled.CreditCard to SeriesColors[5]
    "calendar" -> Icons.Filled.CalendarMonth to SeriesColors[4]
    "savings" -> Icons.Filled.Savings to SeriesColors[0]
    "trending" -> Icons.AutoMirrored.Filled.TrendingUp to SeriesColors[3]
    "shield" -> Icons.Filled.Shield to SeriesColors[1]
    "leaf" -> Icons.Filled.Eco to SeriesColors[0]
    "fire" -> Icons.Filled.LocalFireDepartment to SeriesColors[4]
    "target" -> Icons.Filled.TrackChanges to SeriesColors[6]
    "piggy" -> Icons.Filled.Savings to SeriesColors[6]
    "crown" -> Icons.Filled.EmojiEvents to SeriesColors[7]
    "people" -> Icons.Filled.Groups to SeriesColors[5]
    "heart" -> Icons.Filled.Favorite to SeriesColors[6]
    else -> Icons.Filled.EmojiEvents to SeriesColors[2]
}

/** Medalla de un logro: a color si está conseguido, gris con su progreso si no. */
@Composable
fun AchievementBadge(a: Achievement, size: Dp = 64.dp) {
    val (icon, color) = iconFor(a.icon)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (!a.unlocked) {
            CircularProgressIndicator(
                progress = { a.progress.toFloat() }, modifier = Modifier.size(size),
                color = color, trackColor = P.chip, strokeWidth = 3.dp,
            )
        }
        Box(
            Modifier.size(size * 0.82f).clip(CircleShape)
                .background(if (a.unlocked) color else P.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (a.unlocked) icon else Icons.Filled.Lock, a.title, Modifier.size(size * 0.38f), tint = if (a.unlocked) Color.White else P.muted)
        }
    }
}

@Composable
fun ProfileScreen(vm: FinanceViewModel, appVm: AppViewModel, settings: Settings, args: ScreenArgs) {
    val planning by vm.planning.collectAsStateWithLifecycle()
    val socialVm: com.caudal.finanzas.social.SocialViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val social by socialVm.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Achievement?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(appVm::setAvatar) }
    val p = planning ?: return
    val level = p.level

    // Con sesión, el perfil público y los logros compartidos se mantienen al día solos.
    androidx.compose.runtime.LaunchedEffect(social.signedIn, p.points, settings.handle, settings.userName, settings.avatarVersion, settings.shareMoneyAchievements) {
        if (social.signedIn) socialVm.publish(p, settings)
    }
    val unlocked = p.achievements.filter { it.unlocked }
        .sortedByDescending { settings.achievements[it.id] ?: 0L }
    val locked = p.achievements.filter { !it.unlocked }.sortedByDescending { it.progress }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = args.contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Perfil", Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = P.ink)
                args.headerActions()
            }
        }

        // ---------- Cabecera ----------
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Avatar(settings.userName, settings.avatarVersion, 112.dp, ring = SeriesColors[3],
                        modifier = Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(P.ink).border(3.dp, P.bg, CircleShape)
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CameraAlt, "Cambiar foto", Modifier.size(18.dp), tint = P.bg) }
                }
                Spacer(Modifier.height(12.dp))
                // Tu propio perfil lleva el tick azul de verificado.
                com.caudal.finanzas.social.NameWithBadge(settings.userName.ifBlank { "Tu nombre" }, true, fontSize = 26)
                Text(
                    if (settings.handle.isNotBlank()) "@${settings.handle}" else "Elige tu @",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (settings.handle.isNotBlank()) P.muted else SeriesColors[1],
                    modifier = Modifier.clickable { editing = true },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(CircleShape).background(SeriesColors[3].copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text("Nivel ${level.number} · ${level.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SeriesColors[3])
                    }
                    Box(
                        Modifier.clip(CircleShape).background(P.chip).clickable { editing = true }.padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text("Editar perfil", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = P.ink) }
                }
            }
        }

        // ---------- Wrapped del mes ----------
        item {
            val mName = java.time.YearMonth.now().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-ES"))
            Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF0B1E4F))))
                    .clickable { args.onOpen(com.caudal.finanzas.ui.Overlay.WRAPPED) }.padding(22.dp),
            ) {
                Text("TU WRAPPED", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.8f))
                Text("Tu $mName, en tarjetas", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Tus números del mes, al día, y una ficha para compartir →", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }

        // ---------- Estadísticas ----------
        item {
            MCard(padding = 18.dp) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Patrimonio", money.format(p.wealth.total), Modifier.weight(1f))
                    Stat("Ahorrado", money.format(p.wealth.savings + p.wealth.invested), Modifier.weight(1f))
                    Stat("Logros", "${unlocked.size}/${p.achievements.size}", Modifier.weight(1f))
                }
            }
        }

        item {
            MintCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Overline("Tu nivel")
                        Text("${level.number} · ${level.name}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = P.ink)
                    }
                    Text("${p.points} pts", style = MaterialTheme.typography.titleMedium, color = SeriesColors[3])
                }
                Spacer(Modifier.height(12.dp))
                ProgressLine(level.progress(p.points), SeriesColors[3], height = 10.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    level.to?.let { "Te faltan ${it - p.points} pts para el siguiente nivel" } ?: "Has llegado al nivel máximo",
                    style = MaterialTheme.typography.bodySmall, color = P.muted,
                )
            }
        }

        // ---------- Logros ----------
        item { SectionTitle("Logros") }
        item {
            MCard(padding = 16.dp) {
                (unlocked + locked).chunked(3).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { a ->
                            Column(
                                Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { selected = a }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AchievementBadge(a)
                                Spacer(Modifier.height(6.dp))
                                Text(a.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (a.unlocked) P.ink else P.muted, textAlign = TextAlign.Center, maxLines = 2)
                                if (!a.unlocked) Text("${(a.progress * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = P.muted)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // ---------- Amigos ----------
        item {
            SectionTitle("Amigos") {
                if (social.signedIn) TextButton(onClick = socialVm::refresh) { Text("Actualizar", color = P.gain) }
            }
        }
        item {
            com.caudal.finanzas.social.FriendsSection(social, settings.handle.isNotBlank(), socialVm, onEditProfile = { editing = true })
        }
    }

    if (editing) EditProfileSheet(settings, appVm, onDone = { editing = false })
    selected?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = P.card,
            icon = { AchievementBadge(a, 72.dp) },
            title = { Text(a.title, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    a.description + "\n\n" + (if (a.unlocked) "Conseguido · +${a.points} pts" else "Llevas el ${(a.progress * 100).toInt()} % · vale ${a.points} pts"),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Vale", color = P.ink) } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = P.ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = P.muted)
    }
}

@Composable
private fun EditProfileSheet(settings: Settings, appVm: AppViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf(settings.userName) }
    var handle by remember { mutableStateOf(settings.handle) }
    val clean = handle.trim().removePrefix("@").lowercase()
    val valid = clean.isEmpty() || AppViewModel.HANDLE_REGEX.matches(clean)
    FormSheet(
        "Editar perfil", onDone,
        onPrimary = { appVm.setName(name); appVm.setHandle(clean); onDone() },
        primaryEnabled = name.isNotBlank() && valid,
        onDelete = if (settings.avatarVersion != 0L) ({ appVm.removeAvatar() }) else null,
    ) {
        TextInput(name, { name = it }, "Nombre")
        TextInput(handle, { handle = it.replace(" ", "") }, "Tu @ (por ejemplo: alex.ahorra)")
        Text(
            if (valid) "Así te encontrarán tus amigos: @${clean.ifBlank { "tu_nombre" }}"
            else "Entre 3 y 20 caracteres: letras minúsculas, números, punto o guion bajo.",
            style = MaterialTheme.typography.bodySmall, color = if (valid) P.muted else P.loss,
        )
        if (settings.avatarVersion != 0L) Text("\"Borrar\" quita tu foto de perfil.", style = MaterialTheme.typography.bodySmall, color = P.muted)
    }
}
