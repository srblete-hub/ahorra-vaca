package com.caudal.finanzas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caudal.finanzas.AppViewModel
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.ui.screens.BudgetScreen
import com.caudal.finanzas.ui.screens.HomeScreen
import com.caudal.finanzas.ui.screens.InvestKind
import com.caudal.finanzas.ui.screens.InvestmentsScreen
import com.caudal.finanzas.ui.screens.OnboardingScreen
import com.caudal.finanzas.domain.IncomeMath
import java.time.YearMonth
import com.caudal.finanzas.ui.screens.SettingsScreen
import com.caudal.finanzas.ui.screens.WealthScreen
import com.caudal.finanzas.ui.screens.FutureScreen
import com.caudal.finanzas.ui.screens.ProfileScreen
import com.caudal.finanzas.ui.screens.AccountScreen
import com.caudal.finanzas.ui.screens.InsightsScreen
import com.caudal.finanzas.ui.screens.ChatScreen
import com.caudal.finanzas.ui.screens.SimulatorScreen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Home),
    WEALTH("Patrimonio", Icons.Filled.DonutLarge),
    BUDGET("Presupuesto", Icons.Filled.PieChart),
    INVEST("Inversión", Icons.AutoMirrored.Filled.TrendingUp),
    PROFILE("Perfil", Icons.Filled.Person),
}
// "Presupuesto" no cabe con letra legible en la barra: allí se llama "Gastos".
private val Tab.barLabel get() = if (this == Tab.BUDGET) "Movimientos" else label

/** Pantallas que se abren por encima de las pestañas. */
enum class Overlay { INSIGHTS, CHAT, SIMULATOR, AGE, PURCHASE, WRAPPED, NEWS }

/** Lo común a todas las pestañas: márgenes, abrir formularios y los botones de la cabecera. */
data class ScreenArgs(
    val contentPadding: PaddingValues,
    val onEdit: (Editing) -> Unit,
    val headerActions: @Composable () -> Unit,
    /** Abre la pantalla de una cuenta (saldo, movimientos, transferir). */
    val onOpenAccount: (com.caudal.finanzas.data.Account) -> Unit = {},
    /** Abre el análisis, el chat o el simulador. */
    val onOpen: (Overlay) -> Unit = {},
)

@Composable
fun MainScreen(appVm: AppViewModel, activity: FragmentActivity) {
    val vm: FinanceViewModel = viewModel()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val incomeSources by vm.incomeSources.collectAsStateWithLifecycle()
    val incomes by vm.incomes.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var investKind by rememberSaveable { mutableStateOf(InvestKind.CRYPTO) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showFuture by rememberSaveable { mutableStateOf(false) }
    var openAccount by rememberSaveable { mutableStateOf<Long?>(null) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var openHolding by rememberSaveable { mutableStateOf<String?>(null) }
    val allExpenses by vm.allExpenses.collectAsStateWithLifecycle()
    val splits by vm.splits.collectAsStateWithLifecycle()
    val planning by vm.planning.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var celebrate by remember { mutableStateOf<List<com.caudal.finanzas.domain.Achievement>>(emptyList()) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // Permiso para avisarte cuando te acercas a un límite (Android 13+), una vez por sesión.
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(settings.onboardingDone, settings.savingStyle) {
        if (settings.onboardingDone && settings.savingStyle >= 0 && android.os.Build.VERSION.SDK_INT >= 33 && !com.caudal.finanzas.payments.Notifier.canNotify(context)) {
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Al principio, una vez: objetivos y cuán ahorrador quieres ser (fija los % de cada tipo de gasto).
    LaunchedEffect(settings.onboardingDone, settings.savingStyle) {
        if (settings.onboardingDone && settings.savingStyle < 0 && editing == null) editing = Editing.ProfileForm
    }
    LaunchedEffect(Unit) { vm.newAchievements.collect { celebrate = celebrate + it } }

    CompositionLocalProvider(LocalMoney provides MoneyFormat(settings.currency, settings.hideAmounts)) {
        Box(Modifier.fillMaxSize().appBackground(P)) {
            if (!settings.onboardingDone) {
                OnboardingScreen(vm, onEdit = { editing = it }, onFinish = { appVm.setOnboardingDone(true); tab = Tab.HOME })
            } else if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(appVm, vm, activity, onBack = { showSettings = false }, onRepeatSetup = { showSettings = false; appVm.setOnboardingDone(false) })
            } else if (showFuture) {
                BackHandler { showFuture = false }
                FutureScreen(vm, onEdit = { editing = it }, onBack = { showFuture = false })
            } else if (openHolding != null) {
                BackHandler { openHolding = null }
                com.caudal.finanzas.ui.screens.HoldingDetailScreen(vm, openHolding!!, settings.dayChanges, onEdit = { editing = it }, onBack = { openHolding = null })
            } else if (overlay != null) {
                BackHandler { overlay = null }
                val onEdit: (Editing) -> Unit = { editing = it }
                when (overlay!!) {
                    Overlay.INSIGHTS -> InsightsScreen(vm, settings, onEdit, onOpen = { overlay = it }, onBack = { overlay = null })
                    Overlay.CHAT -> ChatScreen(vm, onEdit, onBack = { overlay = null })
                    Overlay.SIMULATOR -> SimulatorScreen(vm, onBack = { overlay = Overlay.INSIGHTS })
                    Overlay.PURCHASE -> com.caudal.finanzas.ui.screens.PurchaseScreen(vm, onBack = { overlay = null })
                    Overlay.AGE -> com.caudal.finanzas.ui.screens.FinancialAgeScreen(vm, onEdit, onBack = { overlay = null })
                Overlay.WRAPPED -> com.caudal.finanzas.ui.screens.WrappedScreen(vm, settings.userName, onBack = { overlay = null })
                Overlay.NEWS -> com.caudal.finanzas.ui.screens.NewsScreen(vm, onBack = { overlay = null })
                }
            } else if (openAccount != null) {
                BackHandler { openAccount = null }
                AccountScreen(vm, openAccount!!, onEdit = { editing = it }, onBack = { openAccount = null })
            } else {
                if (tab != Tab.HOME) BackHandler { tab = Tab.HOME }

                val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val args = ScreenArgs(
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = nav + 104.dp),
                    onEdit = { editing = it },
                    headerActions = {
                        HeaderPill {
                            PillIcon(
                                if (settings.hideAmounts) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                if (settings.hideAmounts) "Mostrar importes" else "Ocultar importes",
                                onClick = { appVm.setHideAmounts(!settings.hideAmounts) },
                            )
                            PillIcon(Icons.Filled.Add, "Añadir", onClick = { editing = Editing.Chooser })
                            PillIcon(Icons.Filled.Newspaper, "Noticias", onClick = { overlay = Overlay.NEWS })
                            PillIcon(Icons.Filled.Settings, "Ajustes", onClick = { showSettings = true })
                        }
                    },
                    onOpenAccount = { openAccount = it.id },
                    onOpen = { overlay = it },
                )

                when (tab) {
                    Tab.HOME -> HomeScreen(
                        vm, settings, args,
                        onOpenSettings = { showSettings = true }, onStartSetup = { appVm.setOnboardingDone(false) },
                        onOpenFuture = { showFuture = true }, onOpenTab = { tab = it }, onOpenInvest = { investKind = it; tab = Tab.INVEST },
                    )
                    Tab.WEALTH -> WealthScreen(vm, settings, args, onOpenInvest = { investKind = it; tab = Tab.INVEST }, onStartSetup = { appVm.setOnboardingDone(false) })
                    Tab.BUDGET -> BudgetScreen(vm, settings, args, onOpenFuture = { showFuture = true })
                    Tab.INVEST -> InvestmentsScreen(vm, args, investKind, settings.dayChanges, onKind = { investKind = it }, onOpenHolding = { openHolding = it })
                    Tab.PROFILE -> ProfileScreen(vm, appVm, settings, args)
                }

                BottomBar(tab, settings, onTab = { tab = it }, onAdd = { editing = Editing.Chooser }, modifier = Modifier.align(Alignment.BottomCenter))
            }
            // Tapa la barra de estado de Android: al hacer scroll nada se ve por detrás de la hora y los iconos.
            // (en el Wrapped no: su degradado llega hasta arriba del todo)
            if (overlay != Overlay.WRAPPED && overlay != Overlay.NEWS) Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .height(WindowInsets.displayCutout.asPaddingValues().calculateTopPadding())
                    .background(if (overlay == Overlay.AGE) Color(0xFF050505) else P.bg),
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        }

        val close = { editing = null }
        when (val e = editing) {
            null -> {}
            Editing.Chooser -> AddChooserSheet(onPick = { editing = it }, onDismiss = close, categories, accounts, settings.cardAccountId)
            is Editing.ExpenseForm -> ExpenseSheet(
                e.expense, categories, accounts, settings.currency,
                onSave = { vm.save(it); close() },
                onDelete = if (e.expense.id != 0L) ({ confirm = "este gasto" to { vm.delete(e.expense); close() } }) else null,
                onDismiss = close,
                refundPending = e.expense.id in settings.refundIds,
                onRefund = { vm.setRefund(e.expense.id, it) },
                onNotPayment = if (e.expense.fromCard && e.expense.id != 0L) ({ vm.notAPayment(e.expense); close() }) else null,
                onIsTransfer = { editing = Editing.ConvertTransfer(e.expense) },
                onOwed = {
                    val known = com.caudal.finanzas.domain.Debts.people(com.caudal.finanzas.domain.Debts.parse(settings.debtsRaw), com.caudal.finanzas.domain.Debts.parseTricount(settings.tricountRaw))
                    editing = Editing.DebtForm(
                        com.caudal.finanzas.domain.Debt(0, known.firstOrNull() ?: "", e.expense.amount / 2, e.expense.note, e.expense.epochDay, e.expense.id),
                        e.expense.amount,
                    )
                },
            )
            is Editing.AccountForm -> AccountSheet(
                e.account, settings.currency,
                onSave = { vm.save(it); close() },
                onDelete = if (e.account.id != 0L) ({ confirm = "\"${e.account.name}\"" to { vm.delete(e.account); close() } }) else null,
                onDismiss = close,
            )
            Editing.CoinSearch -> CoinSearchSheet(vm::searchCoins, onPick = { c ->
                editing = Editing.CryptoForm(com.caudal.finanzas.data.CryptoHolding(coinId = c.id, symbol = c.symbol, name = c.name, amount = 0.0, avgBuyPrice = 0.0))
            }, onDismiss = close)
            is Editing.CryptoForm -> CryptoSheet(
                e.holding, settings.currency, vm::cryptoPrice,
                onSave = { vm.save(it); close() },
                onDelete = if (e.holding.id != 0L) ({ confirm = e.holding.name to { vm.delete(e.holding); close() } }) else null,
                onDismiss = close,
            )
            is Editing.StockForm -> StockSheet(
                e.holding, settings.currency,
                onSave = { s, fetch -> vm.save(s, fetch); close() },
                onDelete = if (e.holding.id != 0L) ({ confirm = e.holding.ticker to { vm.delete(e.holding); close() } }) else null,
                onDismiss = close,
            )
            Editing.Income -> MonthlyIncomeSheet(settings.monthlyIncome, settings.currency, onSave = { appVm.setIncome(it); close() }, onDismiss = close)
            Editing.Categories -> CategoriesSheet(categories, onSave = { vm.saveCategories(it); close() }, onDismiss = close)
            is Editing.TransferForm -> TransferSheet(e.fromAccountId, accounts, settings.currency, onSave = { vm.save(it); close() }, onDismiss = close)
            is Editing.QuickEntry -> QuickEntrySheet(
                e.text, allExpenses, accounts,
                defaultAccountId = accounts.firstOrNull { it.id == settings.cardAccountId }?.id ?: planning?.bankPlan?.main?.id,
                onSave = { list, acc -> vm.saveParsed(list, acc); close() }, onDismiss = close,
            )
            is Editing.SplitForm -> {
                val current = splits.firstOrNull { it.expenseId == e.expense.id }
                SplitSheet(
                    e.expense, current, onSave = { vm.save(it); close() },
                    onDelete = current?.let { c -> { vm.delete(c); close() } }, onDismiss = close,
                )
            }
            Editing.BankPlanForm -> BankPlanSheet(
                accounts, planning?.bankPlan?.main?.id, planning?.bankPlan?.target?.id, settings.bankSavePct,
                onSave = { m, t, pc -> vm.setBankPlan(m, t, pc); close() }, onDismiss = close,
            )
            is Editing.GoalForm -> {
                val g = e.index?.let { planning?.goalList?.getOrNull(it) }
                com.caudal.finanzas.ui.screens.GoalSheet(
                    g?.name ?: "", g?.target ?: 0.0, g?.deadline, settings.currency,
                    onSave = { n, t, dl -> vm.setGoal(e.index, n, t, dl); close() },
                    onDelete = if (g != null) ({ vm.deleteGoal(e.index!!); close() }) else null,
                    onDismiss = close,
                )
            }
            is Editing.ConvertTransfer -> ConvertTransferSheet(e.expense, accounts, onConvert = { to -> vm.convertToTransfer(e.expense, to); close() }, onDismiss = close)
            is Editing.DebtForm -> com.caudal.finanzas.ui.screens.DebtSheet(
                e.debt, e.total,
                com.caudal.finanzas.domain.Debts.people(com.caudal.finanzas.domain.Debts.parse(settings.debtsRaw), com.caudal.finanzas.domain.Debts.parseTricount(settings.tricountRaw)),
                settings.currency,
                onSave = { vm.saveDebt(it); close() },
                onDelete = if (e.debt.id != 0L) ({ vm.deleteDebt(e.debt.id); close() }) else null,
                onDismiss = close,
            )
            is Editing.TricountForm -> com.caudal.finanzas.ui.screens.TricountSheet(
                e.person, com.caudal.finanzas.domain.Debts.parseTricount(settings.tricountRaw).entries.firstOrNull { it.key.equals(e.person, true) }?.value,
                settings.currency, onSave = { p, a -> vm.setTricount(p, a); close() }, onDismiss = close,
            )
            is Editing.ExpectedIncome -> com.caudal.finanzas.ui.screens.ExpectedIncomeSheet(
                e.source, e.month, settings.expectedIncome[IncomeMath.key(e.source.id, e.month)],
                IncomeMath.variableAverage(e.source, incomes, e.month), settings.currency,
                onSave = { vm.setExpectedIncome(e.source, e.month, it); close() }, onDismiss = close,
            )
            is Editing.TagDetail -> {
                val month by vm.month.collectAsStateWithLifecycle()
                TagDetailSheet(
                    e.tag, month, allExpenses.filter { x -> categories.none { it.id == x.categoryId && it.isSavings } }, accounts,
                    planning?.budget?.items?.firstOrNull { it.tag == e.tag }?.monthly,
                    onOpenExpense = { editing = Editing.ExpenseForm(it) },
                    onSetLimit = { editing = Editing.TagLimitForm(e.tag) },
                    onDismiss = close,
                )
            }
            is Editing.TagLimitForm -> com.caudal.finanzas.ui.screens.TagLimitSheet(
                e.tag, planning?.limits?.get(e.tag), settings.currency,
                onSave = { vm.setTagLimit(e.tag, it); close() },
                onAuto = { vm.setTagLimit(e.tag, null); close() }, onDismiss = close,
            )
            Editing.ProfileForm -> com.caudal.finanzas.ui.screens.SavingProfileSheet(
                settings.savingStyle, settings.lifeGoals, settings.bankBuffer, onSave = { st, g, b -> vm.setSavingProfile(st, g); vm.setBankBuffer(b); close() }, onDismiss = close,
            )
            Editing.AgeForm -> com.caudal.finanzas.ui.screens.AgeSheet(settings.birthYear, onSave = { vm.setBirthYear(it); close() }, onDismiss = close)
            Editing.PlanForm -> PlanSheet(settings.planName, settings.planReserve, settings.currency, onSave = { n, a -> vm.setPlan(n, a); close() }, onDismiss = close)
            is Editing.BalanceForm -> BalanceSheet(e.account, settings.currency, onSave = { vm.setBalance(e.account, it); close() }, onDismiss = close)
            is Editing.BillForm -> BillSheet(
                e.bill, categories, accounts, settings.currency,
                onSave = { vm.save(it); close() },
                onDelete = if (e.bill.id != 0L) ({ confirm = "\"${e.bill.name}\"" to { vm.delete(e.bill); close() } }) else null,
                onDismiss = close,
            )
            is Editing.IncomeSourceForm -> IncomeSourceSheet(
                e.source, accounts,
                average = if (e.source.id != 0L) IncomeMath.variableAverage(e.source, incomes, YearMonth.now()) else null,
                currency = settings.currency,
                onSave = { vm.save(it); close() },
                onDelete = if (e.source.id != 0L) ({ confirm = "\"${e.source.name}\"" to { vm.delete(e.source); close() } }) else null,
                onDismiss = close,
            )
            is Editing.IncomeForm -> IncomeSheet(
                e.income, incomeSources.firstOrNull { it.id == e.income.sourceId }, accounts, settings.currency,
                onSave = { vm.save(it); close() },
                onDelete = if (e.income.id != 0L) ({ confirm = "este cobro" to { vm.delete(e.income); close() } }) else null,
                onDismiss = close,
            )
        }
        confirm?.let { (what, action) -> ConfirmDeleteDialog(what, onConfirm = action, onDismiss = { confirm = null }) }
        if (celebrate.isNotEmpty()) AchievementDialog(celebrate, onDismiss = { celebrate = emptyList() })
    }
}

/** Barra inferior flotante de cristal: pestañas con icono y texto, y el + aparte. */
@Composable
private fun BottomBar(tab: Tab, settings: com.caudal.finanzas.data.Settings, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val p = P
    val shape = RoundedCornerShape(38.dp)
    // Barra de cristal a todo el ancho: icono grande encima y texto debajo, la activa en una cápsula.
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(22.dp, shape, ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
            .clip(shape).background(p.glass).border(1.dp, p.cardBorder, shape)
            .height(74.dp).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t -> BarItem(t, t == tab, settings, Modifier.weight(1f)) { onTab(t) } }
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, settings: com.caudal.finanzas.data.Settings, modifier: Modifier, onClick: () -> Unit) {
    val p = P
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(if (selected) p.chip else Color.Transparent).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (tab == Tab.PROFILE) Avatar(settings.userName, settings.avatarVersion, 27.dp, ring = if (selected) p.ink else null)
        else Icon(tab.icon, null, Modifier.size(27.dp), tint = p.ink.copy(alpha = if (selected) 1f else 0.85f))
        Spacer(Modifier.height(2.dp))
        Text(
            tab.barLabel, fontSize = 11.sp, letterSpacing = (-0.2).sp, maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = p.ink.copy(alpha = if (selected) 1f else 0.85f),
            modifier = Modifier.semantics { contentDescription = tab.barLabel },
        )
    }
}

/** Celebración cuando se consigue un logro nuevo. */
@Composable
private fun AchievementDialog(list: List<com.caudal.finanzas.domain.Achievement>, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.card,
        icon = { com.caudal.finanzas.ui.screens.AchievementBadge(list.first(), 72.dp) },
        title = { Text(if (list.size == 1) "¡Nuevo logro!" else "¡${list.size} logros nuevos!", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                list.take(4).forEach { a ->
                    Text(a.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = P.ink)
                    Text("${a.description} · +${a.points} pts", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = P.muted)
                    Spacer(Modifier.height(8.dp))
                }
                if (list.size > 4) Text("y ${list.size - 4} más en tu perfil", color = P.muted)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("¡Genial!", color = P.gain, fontWeight = FontWeight.Bold) } },
    )
}
