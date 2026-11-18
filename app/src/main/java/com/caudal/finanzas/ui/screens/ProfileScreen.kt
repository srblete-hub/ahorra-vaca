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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
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
                color = color, trackColor = P.chip, strokeWidth = 4.dp,
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = args.contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Perfil", Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, fontFamily = com.caudal.finanzas.ui.SerifDisplay, letterSpacing = (-1).sp, color = P.ink)
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
                        Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape).background(P.ink).border(4.dp, P.bg, CircleShape)
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CameraAlt, "Cambiar foto", Modifier.size(16.dp), tint = P.bg) }
                }
                Spacer(Modifier.height(12.dp))
                // Tu propio perfil lleva el tick azul de verificado; el lápiz edita nombre y @.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.caudal.finanzas.social.NameWithBadge(settings.userName.ifBlank { "Tu nombre" }, true, fontSize = 30)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(P.gain.copy(alpha = 0.14f)).clickable { editing = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Edit, "Editar perfil", Modifier.size(16.dp), tint = P.gain) }
                }
                Text(
                    if (settings.handle.isNotBlank()) "@${settings.handle}" else "Elige tu @",
                    fontSize = 16.sp,
                    color = if (settings.handle.isNotBlank()) P.muted else SeriesColors[1],
                    modifier = Modifier.clickable { editing = true },
                )
            }
        }

        // ---------- Tarjeta de miembro ----------
        item { MemberCard(level.number, level.name, level.to?.let { it - p.points }, p.data.firstMonth) }

        // ---------- Racha ----------
        item {
            val len = java.time.YearMonth.now().lengthOfMonth()
            val allowance = p.budget?.base?.takeIf { it > 0 }?.let { it / len } ?: p.safe.perDay
            val streak = remember(p.data, allowance) { com.caudal.finanzas.domain.Streaks.of(p.data, allowance) }
            StreakCard(streak)
        }

        // ---------- Tu actividad ----------
        item {
            val friends = remember(settings.debtsRaw, settings.tricountRaw) {
                com.caudal.finanzas.domain.Debts.people(com.caudal.finanzas.domain.Debts.parse(settings.debtsRaw), com.caudal.finanzas.domain.Debts.parseTricount(settings.tricountRaw)).size
            }
            val months = remember(p.data) { p.data.expenses.map { java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(it.epochDay)) }.distinct().size }
            Column {
                Text("TU ACTIVIDAD", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = P.muted, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivityTile(Icons.Filled.Groups, "$friends", "Amigos", Modifier.weight(1f))
                    ActivityTile(Icons.Filled.EmojiEvents, "${unlocked.size}", "Logros", Modifier.weight(1f))
                    ActivityTile(Icons.Filled.AutoAwesome, "$months", "Wrappeds", Modifier.weight(1f)) { args.onOpen(com.caudal.finanzas.ui.Overlay.WRAPPED) }
                }
            }
        }

        // ---------- Wrapped del mes ----------
        item {
            val mName = java.time.YearMonth.now().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-ES"))
            Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF0B1E4F))))
                    .clickable { args.onOpen(com.caudal.finanzas.ui.Overlay.WRAPPED) }.padding(24.dp),
            ) {
                Text("TU WRAPPED", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.8f))
                Text("Tu $mName, en tarjetas", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Tus números del mes, al día, y una ficha para compartir →", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }

        // ---------- Estadísticas ----------
        item {
            MCard(padding = 16.dp) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Patrimonio", money.format(p.wealth.total), Modifier.weight(1f))
                    Stat("Ahorrado", money.format(p.wealth.savings + p.wealth.invested), Modifier.weight(1f))
                    Stat("Logros", "${unlocked.size}/${p.achievements.size}", Modifier.weight(1f))
                }
            }
        }


        // ---------- Logros ----------
        item { SectionTitle("Logros") }
        item {
            MCard(padding = 16.dp) {
                (unlocked + locked).chunked(3).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { a ->
                            Column(
                                Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { selected = a }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AchievementBadge(a)
                                Spacer(Modifier.height(8.dp))
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

/** Tarjeta de miembro: tu nivel en Ahorra, vaca, desde cuándo ahorras y cuánto falta para el siguiente. */
@Composable
private fun MemberCard(level: Int, name: String, missing: Int?, since: java.time.YearMonth?) {
    Box(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1E4D33), Color(0xFF3F8A5F)))),
    ) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 0.dp).size(260.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.caudal.finanzas.R.drawable.ic_launcher_foreground), null,
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).size(128.dp),
        )
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xFF0B2E1D)), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.caudal.finanzas.R.drawable.ic_launcher_foreground), null, Modifier.size(48.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ahorra, vaca", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("MIEMBRO", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, color = Color.White.copy(alpha = 0.75f))
                }
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                    Spacer(Modifier.width(8.dp))
                    Text("Activo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, null, Modifier.size(32.dp), tint = Color(0xFFB7E4C7))
                Spacer(Modifier.width(8.dp))
                // A la derecha va la vaca: el texto deja su hueco.
                Column(Modifier.padding(end = 112.dp)) {
                    Text("NIVEL $level", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                    since?.let { Text("Ahorrando desde " + String.format("%02d/%d", it.monthValue, it.year), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("SIGUIENTE NIVEL", fontSize = 12.sp, letterSpacing = 3.sp, color = Color.White.copy(alpha = 0.7f))
            Text(missing?.let { "Faltan $it pts" } ?: "Nivel máximo", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** Racha: días seguidos gastando lo que te toca o menos (sin contar alquiler ni fijos). */
@Composable
private fun StreakCard(s: com.caudal.finanzas.domain.SpendStreak) {
    val money = LocalMoney.current
    Box(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFEFA25C), Color(0xFFE0703A)))),
    ) {
        Box(Modifier.align(Alignment.TopEnd).size(220.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("RACHA ACTUAL", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.85f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(48.dp).padding(bottom = 8.dp), tint = Color.White)
                        Text("${s.current}", fontSize = 72.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(if (s.current == 1) " día" else " días", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("Récord ${s.record}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Row(
                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Eco, null, Modifier.size(16.dp), tint = Color.White)
                        Text(" ×${s.noSpendThisMonth}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val names = listOf("L", "M", "X", "J", "V", "S", "D")
                s.week.forEachIndexed { i, (_, m) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(when (m) { com.caudal.finanzas.domain.DayMark.FUTURE -> Color.Transparent; com.caudal.finanzas.domain.DayMark.OVER -> Color.White.copy(alpha = 0.25f); else -> Color.White })
                                .border(2.dp, Color.White.copy(alpha = if (m == com.caudal.finanzas.domain.DayMark.FUTURE) 0.45f else 0f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (m) {
                                com.caudal.finanzas.domain.DayMark.OK -> Icon(Icons.Filled.Check, null, Modifier.size(24.dp), tint = Color(0xFFE0703A))
                                com.caudal.finanzas.domain.DayMark.NO_SPEND -> Icon(Icons.Filled.Eco, null, Modifier.size(24.dp), tint = Color(0xFFE0703A))
                                com.caudal.finanzas.domain.DayMark.OVER -> Icon(Icons.Filled.Close, null, Modifier.size(16.dp), tint = Color.White)
                                else -> {}
                            }
                        }
                        Text(names[i], fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when (s.today) {
                    com.caudal.finanzas.domain.DayMark.NO_SPEND -> "Hoy: día sin gastos (por ahora)"
                    com.caudal.finanzas.domain.DayMark.OK -> "Hoy: ${money.format(s.spentToday)} de ${money.format(s.allowance)}, dentro de lo tuyo"
                    else -> "Hoy te has pasado: la racha vuelve a empezar mañana"
                },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            )
            s.nextGoal?.let { g ->
                Text(
                    "${g - s.current} ${if (g - s.current == 1) "día" else "días"} para tu racha de ${if (g == 30) "1 mes" else "$g días"}.",
                    fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text("Cuenta los días que gastas lo que te toca o menos; el alquiler y los fijos no cuentan.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ActivityTile(icon: ImageVector, value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(P.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(P.gain.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = P.gain)
            }
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = P.ink)
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.ink, modifier = Modifier.padding(top = 8.dp))
    }
}
