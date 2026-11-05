package com.caudal.finanzas.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.AppViewModel
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.payments.PaymentListenerService
import com.caudal.finanzas.security.Biometrics
import com.caudal.finanzas.ui.BrandButton
import com.caudal.finanzas.ui.FormSheet
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.Pill
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.SectionTitle
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.TextInput
import com.caudal.finanzas.ui.auth.PIN_LENGTH
import com.caudal.finanzas.ui.auth.PinPad
import androidx.compose.material3.HorizontalDivider

private val currencies = listOf("EUR", "USD", "GBP", "CHF", "MXN")

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    MCard(padding = 0.dp) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 72.dp), color = P.line)

@Composable
private fun AppSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, enabled = enabled, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = P.gain, checkedThumbColor = P.card, uncheckedTrackColor = P.chip, uncheckedBorderColor = P.line),
    )
}

@Composable
fun SettingsScreen(appVm: AppViewModel, vm: FinanceViewModel, activity: FragmentActivity, onBack: () -> Unit, onRepeatSetup: () -> Unit) {
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val socialVm: com.caudal.finanzas.social.SocialViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val social by socialVm.state.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember { Biometrics.isAvailable(context) }
    var hasAccess by remember { mutableStateOf(PaymentListenerService.hasAccess(context)) }
    var editName by remember { mutableStateOf(false) }
    var changePin by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    // Al volver de los ajustes de Android, comprobamos si ya se ha dado el permiso.
    LifecycleResumeEffect(Unit) {
        hasAccess = PaymentListenerService.hasAccess(context)
        onPauseOrDispose {}
    }

    val openListenerSettings = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, PaymentListenerService::class.java).flattenToString(),
            )
        } else Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        Unit
    }
    val openAppInfo = {
        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cardAccount = accounts.firstOrNull { it.id == settings.cardAccountId }
    val cardCategory = categories.firstOrNull { it.id == settings.cardCategoryId } ?: categories.firstOrNull { !it.isSavings }

    LazyColumn(
        Modifier.fillMaxSize().background(P.bg),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = nav + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Text("Ajustes", style = MaterialTheme.typography.headlineMedium, color = P.ink)
            }
        }

        // ---------- Pagos con tarjeta ----------
        item { SectionTitle("Pagos con tarjeta") }
        item {
            SettingsGroup {
                ListRow(
                    "Apuntar pagos automáticamente",
                    subtitle = "Lee el aviso de Google Wallet o de tu banco, lo añade a movimientos y lo descuenta de tu cuenta",
                    leading = { IconTile(Icons.Filled.CreditCard, SeriesColors[0]) },
                    trailing = {
                        AppSwitch(settings.cardPayments) { on ->
                            appVm.setCardPayments(on)
                            if (on && cardAccount == null) {
                                (accounts.firstOrNull { it.type == AccountType.BANK } ?: accounts.firstOrNull())?.let { appVm.setCardAccount(it.id) }
                            }
                            if (on && !hasAccess) openListenerSettings()
                        }
                    },
                )
                if (settings.cardPayments) {
                    Divider()
                    Box {
                        ListRow(
                            "Descontar de",
                            subtitle = cardAccount?.name ?: "Ninguna cuenta: solo se apuntan",
                            onClick = { accountMenu = true },
                            leading = { IconTile(Icons.Filled.AccountBalance, SeriesColors[1]) },
                            chevron = true,
                        )
                        DropdownMenu(accountMenu, { accountMenu = false }, containerColor = P.card) {
                            DropdownMenuItem({ Text("No descontar de ninguna") }, onClick = { appVm.setCardAccount(Settings.NONE); accountMenu = false })
                            accounts.forEach { a -> DropdownMenuItem({ Text(a.name) }, onClick = { appVm.setCardAccount(a.id); accountMenu = false }) }
                        }
                    }
                    Divider()
                    Box {
                        ListRow(
                            "Categoría por defecto",
                            subtitle = cardCategory?.name ?: "—",
                            onClick = { categoryMenu = true },
                            leading = { IconTile(Icons.Filled.Category, SeriesColors[2]) },
                            chevron = true,
                        )
                        DropdownMenu(categoryMenu, { categoryMenu = false }, containerColor = P.card) {
                            categories.forEach { c -> DropdownMenuItem({ Text(c.name) }, onClick = { appVm.setCardCategory(c.id); categoryMenu = false }) }
                        }
                    }
                    Divider()
                    ListRow(
                        "Mirar cualquier app",
                        subtitle = "Además de Google Wallet, Samsung Wallet y los bancos conocidos (BBVA, Santander, CaixaBank, ING, Revolut, N26…)",
                        leading = { IconTile(Icons.Filled.Apps, SeriesColors[3]) },
                        trailing = { AppSwitch(settings.cardAnyApp) { appVm.setCardAnyApp(it) } },
                    )
                }
            }
        }
        if (settings.cardPayments && !hasAccess) {
            item {
                MCard(color = P.debt.copy(alpha = 0.12f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.Warning, P.debt)
                        Spacer(Modifier.width(12.dp))
                        Text("Falta un permiso", style = MaterialTheme.typography.titleMedium, color = P.ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android tiene que dejar que Ahorra, vaca lea tus notificaciones. Pulsa el botón y activa \"Ahorra, vaca · pagos con tarjeta\".",
                        style = MaterialTheme.typography.bodyMedium, color = P.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Si sale gris o dice \"ajuste restringido\": abre Información de la app → menú ⋮ → \"Permitir ajustes restringidos\" y vuelve a intentarlo.",
                        style = MaterialTheme.typography.bodySmall, color = P.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    BrandButton("Dar permiso", openListenerSettings, Modifier.fillMaxWidth())
                    TextButton(onClick = openAppInfo, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Información de la app", color = P.ink) }
                }
            }
        }

        // ---------- Seguridad ----------
        item { SectionTitle("Seguridad") }
        item {
            SettingsGroup {
                ListRow(
                    "Entrar con huella",
                    subtitle = if (biometricAvailable) "Se pide al abrir y tras 30 s fuera de la app" else "Este móvil no tiene huella configurada",
                    leading = { IconTile(Icons.Filled.Fingerprint, SeriesColors[0]) },
                    trailing = {
                        AppSwitch(settings.biometricEnabled && biometricAvailable, enabled = biometricAvailable) { on ->
                            if (on) Biometrics.prompt(activity, "Activar huella", onSuccess = { appVm.setBiometric(true) }, onError = { info = it })
                            else appVm.setBiometric(false)
                        }
                    },
                )
                Divider()
                ListRow(
                    "Bloquear capturas de pantalla",
                    subtitle = "Si lo activas, no se podrán hacer capturas y la app se verá en blanco en recientes",
                    leading = { IconTile(Icons.Filled.Shield, SeriesColors[4]) },
                    trailing = { AppSwitch(settings.blockScreenshots) { appVm.setBlockScreenshots(it) } },
                )
                Divider()
                ListRow("Cambiar PIN", onClick = { changePin = true }, leading = { IconTile(Icons.Filled.Password, SeriesColors[1]) }, chevron = true)
                Divider()
                ListRow(
                    "Bloquear ahora", subtitle = "Pide huella o PIN para volver a entrar",
                    onClick = { appVm.lock() }, leading = { IconTile(Icons.Filled.Shield, SeriesColors[3]) },
                )
            }
        }

        // ---------- Perfil ----------
        item { SectionTitle("Perfil") }
        item {
            SettingsGroup {
                ListRow("Nombre", subtitle = settings.userName, onClick = { editName = true }, leading = { IconTile(Icons.Filled.Person, SeriesColors[3]) }, chevron = true)
                Divider()
                ListRow(
                    "Repetir configuración inicial", subtitle = "Vuelve a pasar por cuentas, ahorro, inversiones e ingresos",
                    onClick = onRepeatSetup, leading = { IconTile(Icons.Filled.Apps, SeriesColors[0]) }, chevron = true,
                )
                Divider()
                ListRow(
                    "Moneda", subtitle = "Los precios de cripto y acciones se convierten; los saldos de cuentas no",
                    leading = { IconTile(Icons.Filled.Euro, SeriesColors[2]) },
                    below = {
                        FlowRow(
                            Modifier.clip(CircleShape).background(P.chip).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            currencies.forEach { c ->
                                Pill(c, settings.currency == c, {
                                    if (c != settings.currency) {
                                        appVm.setCurrency(c)
                                        vm.refreshCrypto(quiet = true)
                                        vm.refreshStocks(quiet = true)
                                    }
                                })
                            }
                        }
                    },
                )
            }
        }

        item { SectionTitle("Amigos y privacidad") }
        item {
            SettingsGroup {
                ListRow(
                    "Compartir logros de cantidades",
                    subtitle = "\"1.000 € ahorrados\", \"Patrimonio de 50.000 €\", \"Colchón\"… Dan pistas de lo que tienes, así que por defecto no se comparten",
                    leading = { IconTile(Icons.Filled.Shield, SeriesColors[1]) },
                    trailing = { AppSwitch(settings.shareMoneyAchievements) { Prefs.get(context).setShareMoneyAchievements(it) } },
                )
                if (social.signedIn) {
                    Divider()
                    ListRow(
                        "Cuenta de Ahorra, vaca", subtitle = social.email ?: "",
                        leading = { IconTile(Icons.Filled.Person, SeriesColors[3]) },
                        trailing = { TextButton(onClick = socialVm::signOut) { Text("Salir", color = P.loss) } },
                    )
                }
                Divider()
                ListRow(
                    "Lo que ven tus amigos",
                    subtitle = "Tu @, nombre, foto, nivel y los logros que compartes. Nunca tus cuentas, movimientos ni importes. " +
                        "Lo demás no sale del móvil: a internet solo se piden precios de cripto (CoinGecko) y acciones (Yahoo Finance). Capturas de pantalla bloqueadas.",
                    leading = { IconTile(Icons.Filled.Shield, SeriesColors[0]) },
                )
            }
        }
    }

    if (editName) {
        var name by remember { mutableStateOf(settings.userName) }
        FormSheet("Tu nombre", { editName = false }, onPrimary = { appVm.setName(name); editName = false }, primaryEnabled = name.isNotBlank()) {
            TextInput(name, { name = it }, "Nombre")
        }
    }
    if (changePin) ChangePinDialog(appVm, onDone = { changePin = false; info = it })
    info?.let {
        AlertDialog(onDismissRequest = { info = null }, containerColor = P.card, text = { Text(it) }, confirmButton = { TextButton(onClick = { info = null }) { Text("Vale", color = P.ink) } })
    }
}

@Composable
private fun ChangePinDialog(appVm: AppViewModel, onDone: (String?) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    FormSheet(listOf("PIN actual", "PIN nuevo ($PIN_LENGTH cifras)", "Repite el PIN nuevo")[step], { onDone(null) }, primary = null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PinPad(error = error, onComplete = { pin ->
                when (step) {
                    0 -> { current = pin; error = null; step = 1 }
                    1 -> { fresh = pin; error = null; step = 2 }
                    else -> when {
                        pin != fresh -> { error = "No coincide"; step = 1 }
                        appVm.changePin(current, fresh) -> onDone("PIN cambiado")
                        else -> { error = "El PIN actual no es correcto"; step = 0 }
                    }
                }
            })
        }
    }
}
