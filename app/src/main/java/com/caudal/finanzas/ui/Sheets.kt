package com.caudal.finanzas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Group
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.domain.Classifier
import com.caudal.finanzas.domain.SpendTag
import com.caudal.finanzas.domain.spendTag
import com.caudal.finanzas.domain.isDebt
import com.caudal.finanzas.domain.isSaving
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.CoinResult
import com.caudal.finanzas.data.CryptoHolding
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.StockHolding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Currency
import kotlin.math.abs

/** Qué formulario está abierto. Lo gestiona MainScreen para que el + funcione desde cualquier pestaña. */
sealed interface Editing {
    data object Chooser : Editing
    data class ExpenseForm(val expense: Expense) : Editing
    data class AccountForm(val account: Account) : Editing
    data object CoinSearch : Editing
    data class CryptoForm(val holding: CryptoHolding) : Editing
    data class StockForm(val holding: StockHolding) : Editing
    data object Income : Editing
    data object Categories : Editing
    data class IncomeSourceForm(val source: com.caudal.finanzas.data.IncomeSource) : Editing
    data class IncomeForm(val income: com.caudal.finanzas.data.Income) : Editing
    data class BillForm(val bill: com.caudal.finanzas.data.RecurringBill) : Editing
    data class TransferForm(val fromAccountId: Long) : Editing
    data class BalanceForm(val account: Account) : Editing
    /** Escribir gastos como una frase. */
    data class QuickEntry(val text: String = "") : Editing
    data class SplitForm(val expense: Expense) : Editing
    data object BankPlanForm : Editing
    data object PlanForm : Editing
    data class GoalForm(val index: Int? = null) : Editing
    data object AgeForm : Editing
    data object ProfileForm : Editing
    /** Convertir un gasto en un traspaso entre tus cuentas. */
    data class ConvertTransfer(val expense: Expense) : Editing
    /** Desglose de los movimientos de un tipo de gasto en el mes elegido. */
    data class TagDetail(val tag: SpendTag) : Editing
    data class TagLimitForm(val tag: SpendTag) : Editing
    /** Me deben / debo; [total] es el gasto del que sale (para proponer la mitad). */
    data class DebtForm(val debt: com.caudal.finanzas.domain.Debt, val total: Double? = null) : Editing
    data class TricountForm(val person: String) : Editing
    data class ExpectedIncome(val source: com.caudal.finanzas.data.IncomeSource, val month: java.time.YearMonth) : Editing
}

// ---------- Escribir gastos como hablas ----------

@Composable
fun QuickEntrySheet(
    initialText: String,
    history: List<Expense>,
    accounts: List<Account>,
    defaultAccountId: Long?,
    onSave: (List<com.caudal.finanzas.domain.ParsedExpense>, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var accountId by remember { mutableStateOf(defaultAccountId) }
    val parsed = remember(text) { com.caudal.finanzas.domain.NaturalEntry.parse(text, history) }
    val money = LocalMoney.current
    FormSheet(
        "Escribe tus gastos", onDismiss,
        primary = if (parsed.size > 1) "Apuntar ${parsed.size} gastos" else "Apuntar",
        onPrimary = { onSave(parsed, accountId) }, primaryEnabled = parsed.isNotEmpty(),
    ) {
        Text("Como se lo contarías a alguien. Ahorra, vaca saca los importes y los clasifica solo.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
        TextInput(text, { text = it }, "He pagado 18 € en una cena con amigos y 12 € en gasolina")
        parsed.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(p.tag.icon(), p.tag.color(), 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.note, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                    Text(p.tag.label + if (p.daysAgo == 1) " · ayer" else " · hoy", style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
                Text("−" + money.format(p.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
            }
        }
        FieldLabel("Sale de")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("No descontar", accountId == null, { accountId = null })
            accounts.filter { !it.isDebt() }.forEach { a -> Choice(a.name, a.id == accountId, { accountId = a.id }, a.type.icon()) }
        }
    }
}

// ---------- Repartir un gasto ----------

@Composable
fun SplitSheet(expense: Expense, current: com.caudal.finanzas.data.Split?, onSave: (com.caudal.finanzas.data.Split) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var people by remember { mutableStateOf(current?.people ?: 2) }
    var settled by remember { mutableStateOf(current?.settled ?: 0) }
    var note by remember { mutableStateOf(current?.note ?: "") }
    val money = LocalMoney.current
    val share = expense.amount / people
    FormSheet(
        "Repartir gasto", onDismiss,
        onPrimary = { onSave(com.caudal.finanzas.data.Split(expense.id, people, settled.coerceAtMost(people - 1), note.trim())) },
        onDelete = onDelete,
    ) {
        Text("${expense.note.ifBlank { "Gasto" }} · ${money.format(expense.amount)}", style = MaterialTheme.typography.titleMedium, color = P.ink)
        FieldLabel("¿Entre cuántas personas? (tú incluido)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..8).forEach { n -> Choice("$n", n == people, { people = n; settled = settled.coerceAtMost(n - 1) }) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.mint).padding(16.dp),
        ) {
            Text("A cada uno le toca", style = MaterialTheme.typography.bodySmall, color = P.muted)
            Text(money.format(share), style = MaterialTheme.typography.headlineSmall, color = P.ink)
            Text("Te deben ${money.format(share * (people - 1 - settled))}", style = MaterialTheme.typography.titleSmall, color = if (people - 1 - settled > 0) SeriesColors[1] else P.gain)
        }
        FieldLabel("¿Cuántos te han pagado ya su parte?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until people).forEach { n -> Choice(if (n == 0) "Nadie" else "$n", n == settled, { settled = n }) }
        }
        TextInput(note, { note = it }, "Con quién (Laura, Marcos…)")
    }
}

// ---------- Reparto del saldo ----------

@Composable
fun BankPlanSheet(accounts: List<Account>, mainId: Long?, targetId: Long?, savePct: Double, onSave: (Long, Long, Double) -> Unit, onDismiss: () -> Unit) {
    var main by remember { mutableStateOf(mainId) }
    var target by remember { mutableStateOf(targetId) }
    var percent by remember { mutableStateOf(savePct.toInt().toString()) }
    val pctValue = percent.toIntOrNull()?.takeIf { it in 0..100 }
    FormSheet(
        "Tu reparto", onDismiss,
        onPrimary = { onSave(main!!, target ?: Settings.NONE, pctValue!!.toDouble()) },
        primaryEnabled = main != null && pctValue != null,
    ) {
        Text("Del dinero de tu cuenta principal, Ahorra, vaca descuenta los gastos fijos que faltan (el alquiler…) y te propone mandar un % al ahorro. Lo demás es lo que puedes gastar.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
        FieldLabel("Cuenta principal (donde cobras y pagas)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.filter { !(it.isSaving() || it.isDebt()) }.forEach { a -> Choice(a.name, a.id == main, { main = a.id; if (target == a.id) target = null }, a.type.icon()) }
        }
        FieldLabel("¿Adónde va tu ahorro?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.filter { it.id != main && !it.isDebt() }.forEach { a ->
                Choice(a.name + if (a.interestRate > 0) " · ${pct(a.interestRate)}" else "", a.id == target, { target = a.id }, a.type.icon())
            }
        }
        TextInput(percent, { percent = it.filter(Char::isDigit).take(3) }, "% de lo que te queda que va al ahorro", keyboardType = KeyboardType.Number)
    }
}

/** Apartar dinero para un plan: no se cuenta como gastable hasta que lo quites. */
@Composable
fun PlanSheet(name: String, amount: Double, currency: String, onSave: (String, Double) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var a by remember { mutableStateOf(amount.editable()) }
    val value = a.toAmount() ?: if (a.isBlank()) 0.0 else null
    FormSheet("Apartar para un plan", onDismiss, onPrimary = { onSave(n.trim(), value!!) }, primaryEnabled = value != null) {
        Text("Una escapada, un regalo, un concierto… Lo que apartes deja de contar como dinero para gastar hasta tu próximo cobro. Pon 0 para quitarlo.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
        BigAmountInput(a, { a = it }, currencySymbol(currency))
        TextInput(n, { n = it }, "Para qué (Escapada a Lisboa…)")
    }
}

/** Mover dinero entre tus cuentas: de la nómina al ahorro, a la hucha… */
@Composable
fun TransferSheet(fromId: Long, accounts: List<Account>, currency: String, onSave: (com.caudal.finanzas.data.Transfer) -> Unit, onDismiss: () -> Unit) {
    var from by remember { mutableStateOf(fromId) }
    var to by remember { mutableStateOf(accounts.firstOrNull { it.id != fromId }?.id) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val value = amount.toAmount()
    FormSheet(
        "Transferir", onDismiss, primary = "Transferir",
        onPrimary = { onSave(com.caudal.finanzas.data.Transfer(fromAccountId = from, toAccountId = to!!, amount = value!!, note = note.trim(), epochDay = LocalDate.now().toEpochDay())) },
        primaryEnabled = value != null && value > 0 && to != null && to != from,
    ) {
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        FieldLabel("Desde")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a -> Choice(a.name, a.id == from, { from = a.id; if (to == a.id) to = null }, a.type.icon()) }
        }
        FieldLabel("Hacia")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.filter { it.id != from }.forEach { a -> Choice(a.name, a.id == to, { to = a.id }, a.type.icon()) }
        }
        TextInput(note, { note = it }, "Concepto (opcional)")
        Text("Una transferencia no es un gasto: tu patrimonio no cambia, solo dónde está el dinero.", style = MaterialTheme.typography.bodySmall, color = P.muted)
    }
}

/** Poner el saldo que dice el banco, sin tocar nada más. */
@Composable
fun BalanceSheet(account: Account, currency: String, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(account.balance.editable()) }
    val value = amount.toAmount() ?: if (amount.isBlank()) 0.0 else null
    FormSheet("Ajustar saldo", onDismiss, onPrimary = { onSave(value!!) }, primaryEnabled = value != null) {
        Text("Pon el saldo que te dice el banco ahora mismo en ${account.name}.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        if (value != null && value != account.balance) {
            val diff = value - account.balance
            Text(
                "Diferencia: ${LocalMoney.current.signed(diff)}",
                style = MaterialTheme.typography.titleSmall, color = if (diff >= 0) P.gain else P.loss,
            )
        }
    }
}

fun newBill(categories: List<Category>, accountId: Long?) = com.caudal.finanzas.data.RecurringBill(
    name = "", amount = 0.0, day = 1,
    categoryId = categories.firstOrNull { !it.isSavings }?.id ?: 0, accountId = accountId,
)

@Composable
fun BillSheet(
    initial: com.caudal.finanzas.data.RecurringBill,
    categories: List<Category>,
    accounts: List<Account>,
    currency: String,
    onSave: (com.caudal.finanzas.data.RecurringBill) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var amount by remember { mutableStateOf(initial.amount.editable()) }
    var day by remember { mutableStateOf(initial.day.toString()) }
    var yearly by remember { mutableStateOf(initial.yearly) }
    var month by remember { mutableStateOf(initial.month) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    val value = amount.toAmount()
    val dayNum = day.toIntOrNull()?.takeIf { it in 1..31 }
    val es = java.util.Locale.forLanguageTag("es-ES")

    FormSheet(
        if (initial.id == 0L) "Nuevo gasto fijo" else initial.name, onDismiss,
        onPrimary = { onSave(initial.copy(name = name.trim(), amount = value!!, day = dayNum!!, yearly = yearly, month = month, categoryId = categoryId, accountId = accountId)) },
        primaryEnabled = name.isNotBlank() && value != null && value > 0 && dayNum != null && categories.any { it.id == categoryId },
        onDelete = onDelete,
    ) {
        Segmented(listOf(false, true), yearly, { if (it) "Cada año" else "Cada mes" }, { yearly = it })
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        TextInput(name, { name = it }, "Qué es (Alquiler, Netflix, Seguro coche…)")
        TextInput(day, { day = it.filter(Char::isDigit).take(2) }, "Día en que se cobra", keyboardType = KeyboardType.Number)
        if (yearly) {
            FieldLabel("Mes")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..12).forEach { m ->
                    Choice(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, es).trimEnd('.'), m == month, { month = m })
                }
            }
        }
        FieldLabel("Categoría")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c -> Choice(c.name, c.id == categoryId, { categoryId = c.id }, categoryIcon(c)) }
        }
        FieldLabel("Sale de")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Ninguna", accountId == null, { accountId = null })
            accounts.forEach { a -> Choice(a.name, a.id == accountId, { accountId = a.id }, a.type.icon()) }
        }
    }
}

fun AccountType.icon(): ImageVector = when (this) {
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.BANK_SAVINGS -> Icons.Filled.Savings
    AccountType.CASH -> Icons.Filled.Payments
    AccountType.HOME_SAVINGS -> Icons.Filled.Home
    AccountType.WALLET -> Icons.Filled.Wallet
    AccountType.FUND -> Icons.Filled.Spa
    AccountType.OTHER -> Icons.Filled.AccountBalanceWallet
    AccountType.DEBT -> Icons.Filled.CreditCard
}

fun categoryColor(categories: List<Category>, id: Long): Color =
    SeriesColors[categories.indexOfFirst { it.id == id }.coerceAtLeast(0) % SeriesColors.size]

fun categoryIcon(category: Category?): ImageVector = when {
    category == null -> Icons.AutoMirrored.Filled.ReceiptLong
    category.isSavings -> Icons.Filled.Savings
    category.name.contains("necesidad", ignoreCase = true) -> Icons.Filled.Home
    else -> Icons.Filled.ShoppingBag
}

fun currencySymbol(code: String): String = runCatching { Currency.getInstance(code).getSymbol(java.util.Locale.forLanguageTag("es-ES")) }.getOrDefault(code)

/** Opción de formulario: verde oscuro si está elegida, gris si no. */
@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val p = P
    Row(
        Modifier.clip(CircleShape)
            .background(if (selected) p.brand else p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (selected) p.onBrand else p.ink); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) p.onBrand else p.ink)
    }
}

// ---------- Elegir qué añadir ----------

@Composable
fun AddChooserSheet(onPick: (Editing) -> Unit, onDismiss: () -> Unit, categories: List<Category>, accounts: List<Account>, cardAccountId: Long) {
    val defaultAccount = accounts.firstOrNull { it.id == cardAccountId }?.id ?: accounts.firstOrNull { it.type == AccountType.BANK }?.id
    FormSheet("¿Qué quieres añadir?", onDismiss, primary = null) {
        val options = listOf(
            Triple(Icons.Filled.SwapHoriz, "Transferencia entre mis cuentas", "De Santander a Revolut, a Robinhood… no es un gasto"),
            Triple(Icons.Filled.Group, "Me deben o debo", "Pagaste por un amigo o él por ti (o el saldo de Tricount)"),
            Triple(Icons.AutoMirrored.Filled.ReceiptLong, "Gasto", "Apunta un pago y descuéntalo de una cuenta"),
            Triple(Icons.Filled.Savings, "Ingreso", "Bizum, venta, devolución, un extra… y súmalo a una cuenta"),
            Triple(Icons.Filled.AccountBalance, "Cuenta o ahorro", "Banco, efectivo, hucha, PayPal…"),
            Triple(Icons.Filled.CurrencyBitcoin, "Criptomoneda", "Con su precio en vivo"),
            Triple(Icons.AutoMirrored.Filled.TrendingUp, "Acción o ETF", "Con su ticker de bolsa"),
            Triple(Icons.Filled.Home, "Gasto fijo", "Alquiler, suscripciones, seguros… cada mes o cada año"),
        )
        options.forEachIndexed { i, (icon, title, sub) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.chip).clickable {
                    onPick(
                        when (i - 2) {
                            -2 -> Editing.TransferForm(defaultAccount ?: accounts.firstOrNull()?.id ?: 0L)
                            -1 -> Editing.DebtForm(com.caudal.finanzas.domain.Debt(0, "", 0.0, "", LocalDate.now().toEpochDay()))
                            0 -> Editing.ExpenseForm(
                                Expense(
                                    categoryId = categories.firstOrNull { !it.isSavings }?.id ?: 0,
                                    amount = 0.0, epochDay = LocalDate.now().toEpochDay(), accountId = defaultAccount,
                                ),
                            )
                            1 -> Editing.IncomeForm(com.caudal.finanzas.data.Income(sourceId = null, amount = 0.0, note = "", epochDay = LocalDate.now().toEpochDay(), accountId = defaultAccount))
                            2 -> Editing.AccountForm(Account(name = "", type = AccountType.BANK, balance = 0.0))
                            3 -> Editing.CoinSearch
                            4 -> Editing.StockForm(StockHolding(ticker = "", name = "", shares = 0.0, avgBuyPrice = 0.0))
                            else -> Editing.BillForm(newBill(categories, defaultAccount))
                        },
                    )
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(icon, SeriesColors[i % SeriesColors.size])
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }
    }
}

// ---------- Gasto ----------

@Composable
fun ExpenseSheet(
    initial: Expense,
    categories: List<Category>,
    accounts: List<Account>,
    currency: String,
    onSave: (Expense) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    refundPending: Boolean = false,
    onRefund: ((Boolean) -> Unit)? = null,
    onNotPayment: (() -> Unit)? = null,
    onIsTransfer: (() -> Unit)? = null,
    onOwed: (() -> Unit)? = null,
) {
    var amount by remember { mutableStateOf(initial.amount.editable()) }
    var note by remember { mutableStateOf(initial.note) }
    var refund by remember { mutableStateOf(refundPending) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(initial.epochDay)) }
    var tag by remember { mutableStateOf(initial.spendTag()) }
    // Mientras no elijas tú el tipo, se deduce de la descripción al escribir.
    var tagChosen by remember { mutableStateOf(initial.tag.isNotBlank()) }
    var categoryChosen by remember { mutableStateOf(initial.id != 0L) }
    var otherDay by remember { mutableStateOf(false) }
    var dayText by remember { mutableStateOf(date.dayOfMonth.toString()) }
    val today = LocalDate.now()
    val value = amount.toAmount()

    FormSheet(
        if (initial.id == 0L) "Nuevo gasto" else if (initial.fromCard) "Gasto detectado" else "Editar gasto",
        onDismiss,
        onPrimary = { onSave(initial.copy(amount = value!!, note = note.trim(), categoryId = categoryId, accountId = accountId, epochDay = date.toEpochDay(), tag = tag.name)) },
        primaryEnabled = value != null && value > 0 && categories.any { it.id == categoryId },
        onDelete = onDelete,
    ) {
        if (initial.fromCard) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.mint).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CreditCard, null, tint = P.gain)
                Spacer(Modifier.width(10.dp))
                Text("Apuntado solo con tu tarjeta · ${initial.sourceApp}", style = MaterialTheme.typography.bodySmall, color = P.ink)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).padding(12.dp)) {
                Text("Notificación que leí", style = MaterialTheme.typography.labelMedium, color = P.muted)
                Text(
                    initial.rawText.ifBlank { "No guardada (este pago se apuntó con una versión anterior de la app)." },
                    style = MaterialTheme.typography.bodySmall, color = P.ink,
                )
            }
            if (onNotPayment != null) TextButton(onClick = onNotPayment) {
                Text("No era un pago: borrarlo y no volver a apuntar avisos así", color = P.loss)
            }
        }
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        TextInput(note, { t ->
            note = t
            if (!tagChosen) {
                tag = Classifier.guess(t) ?: SpendTag.OTHER
                if (!categoryChosen) Classifier.bucketFor(tag, categories)?.let { categoryId = it }
            }
        }, "Descripción (Mercadona, cena…)")

        FieldLabel(if (tagChosen) "Tipo de gasto" else "Tipo de gasto · deducido solo")
        TagPicker(tag) { t ->
            tag = t; tagChosen = true
            if (!categoryChosen) Classifier.bucketFor(t, categories)?.let { categoryId = it }
        }

        FieldLabel("Parte del reparto")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c -> Choice(c.name, c.id == categoryId, { categoryId = c.id; categoryChosen = true }, categoryIcon(c)) }
        }

        FieldLabel("Sale de")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("No descontar", accountId == null, { accountId = null })
            accounts.forEach { a -> Choice(a.name, a.id == accountId, { accountId = a.id }, a.type.icon()) }
        }

        if (onRefund != null && initial.id != 0L) {
            SwitchRow("Espero que me lo devuelvan", "Te lo recordaré en Dinero recuperable hasta que llegue", refund) { refund = it; onRefund(it) }
        }

        if (onOwed != null && initial.id != 0L) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).clickable(onClick = onOwed).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Group, null, tint = SeriesColors[1])
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Alguien me debe parte", style = MaterialTheme.typography.titleSmall, color = P.ink)
                    Text("Juanpa me debe la mitad, por ejemplo", style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }
        if (onIsTransfer != null && initial.id != 0L && initial.accountId != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).clickable(onClick = onIsTransfer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SwapHoriz, null, tint = P.gain)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Era un traspaso a mis cuentas", style = MaterialTheme.typography.titleSmall, color = P.ink)
                    Text("Lo convierto en transferencia (no cuenta como gasto)", style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }

        FieldLabel("Fecha")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Hoy", !otherDay && date == today, { otherDay = false; date = today })
            Choice("Ayer", !otherDay && date == today.minusDays(1), { otherDay = false; date = today.minusDays(1) })
            Choice("Otro día", otherDay || (date != today && date != today.minusDays(1)), { otherDay = true })
        }
        if (otherDay || (date != today && date != today.minusDays(1))) {
            TextInput(dayText, { t ->
                dayText = t.filter(Char::isDigit).take(2)
                dayText.toIntOrNull()?.takeIf { it in 1..date.lengthOfMonth() }?.let { date = date.withDayOfMonth(it) }
            }, "Día de ${date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-ES"))}", keyboardType = KeyboardType.Number)
        }
    }
}

// ---------- Cuenta ----------

@Composable
fun AccountSheet(
    initial: Account,
    currency: String,
    onSave: (Account) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    title: String? = null,
    types: List<AccountType> = AccountType.entries,
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var balance by remember { mutableStateOf(initial.balance.editable()) }
    var note by remember { mutableStateOf(initial.note) }
    var paysInterest by remember { mutableStateOf(initial.interestRate > 0) }
    var rate by remember { mutableStateOf(initial.interestRate.editable()) }
    var autoInterest by remember { mutableStateOf(initial.autoInterest || initial.id == 0L) }
    val value = balance.toAmount() ?: if (balance.isBlank()) 0.0 else null
    val rateValue = rate.toAmount()

    FormSheet(
        title ?: if (initial.id == 0L) "Nueva cuenta" else "Editar cuenta", onDismiss,
        onPrimary = {
            val r = if (paysInterest) rateValue ?: 0.0 else 0.0
            onSave(
                initial.copy(
                    name = name.trim(), type = type, balance = value!!, note = note.trim(),
                    interestRate = r, autoInterest = r > 0 && autoInterest,
                    // Si se apaga el abono automático, al volver a encenderlo empieza de cero.
                    interestFromMonth = if (r > 0 && autoInterest) initial.interestFromMonth else 0,
                ),
            )
        },
        primaryEnabled = name.isNotBlank() && value != null && (!paysInterest || (rateValue != null && rateValue > 0)),
        onDelete = onDelete,
    ) {
        BigAmountInput(balance, { balance = it }, currencySymbol(currency))
        TextInput(name, { name = it }, "Nombre (BBVA nómina, Hucha, MyInvestor…)")
        if (types.size > 1) {
            FieldLabel("¿Dónde está?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t -> Choice(t.label, t == type, { type = t }, t.icon()) }
            }
        }

        SwitchRow("Da intereses", "Cuenta remunerada, depósito, fondo monetario…", paysInterest) { paysInterest = it }
        if (paysInterest) {
            AmountField(rate, { rate = it }, "Rentabilidad anual (TAE)", suffix = "%")
            SwitchRow("Sumar los intereses cada mes", "Se añaden solos al saldo al empezar cada mes", autoInterest) { autoInterest = it }
            if (value != null && rateValue != null && rateValue > 0) InterestCalculator(value, rateValue)
        }
        TextInput(note, { note = it }, "Nota (opcional)")
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = P.muted)
        }
        androidx.compose.material3.Switch(
            checked, onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = P.gain, checkedThumbColor = P.card, uncheckedTrackColor = P.card, uncheckedBorderColor = P.line),
        )
    }
}

/**
 * Cuánto da una cuenta remunerada: dejándola quieta, metiendo una cantidad fija al mes,
 * y con una parte variable (entre un mínimo y un máximo).
 */
@Composable
fun InterestCalculator(balance: Double, ratePct: Double) {
    val money = LocalMoney.current
    var fixed by remember { mutableStateOf("") }
    var varMin by remember { mutableStateOf("") }
    var varMax by remember { mutableStateOf("") }
    var years by remember { mutableStateOf(5) }
    val f = fixed.toAmount() ?: 0.0
    val vMin = varMin.toAmount() ?: 0.0
    val vMax = (varMax.toAmount() ?: vMin).coerceAtLeast(vMin)
    val months = years * 12

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.mint).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Overline("Si lo dejas como está")
        Row {
            Column(Modifier.weight(1f)) {
                Text(money.format(com.caudal.finanzas.domain.InterestMath.monthlyInterest(balance, ratePct)), style = MaterialTheme.typography.titleLarge, color = P.gain)
                Text("al mes", style = MaterialTheme.typography.labelSmall, color = P.muted)
            }
            Column(Modifier.weight(1f)) {
                Text(money.format(balance * ratePct / 100), style = MaterialTheme.typography.titleLarge, color = P.gain)
                Text("al año", style = MaterialTheme.typography.labelSmall, color = P.muted)
            }
        }

        Overline("Si vas metiendo dinero")
        AmountField(fixed, { fixed = it }, "Cantidad fija al mes", suffix = "€")
        Text("Y si además metes algo variable cada mes (opcional):", style = MaterialTheme.typography.bodySmall, color = P.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountField(varMin, { varMin = it }, "Mínimo", Modifier.weight(1f), suffix = "€")
            AmountField(varMax, { varMax = it }, "Máximo", Modifier.weight(1f), suffix = "€")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 5, 10, 20).forEach { y -> Choice("$y ${if (y == 1) "año" else "años"}", y == years, { years = y }) }
        }

        val low = com.caudal.finanzas.domain.InterestMath.project(balance, ratePct, f + vMin, months)
        val high = com.caudal.finanzas.domain.InterestMath.project(balance, ratePct, f + vMax, months)
        val ranged = vMax > vMin
        Text(
            if (ranged) "${money.format(low.finalBalance)} – ${money.format(high.finalBalance)}" else money.format(low.finalBalance),
            style = MaterialTheme.typography.headlineSmall, color = P.ink,
        )
        Text(
            "Tendrías esto en $years ${if (years == 1) "año" else "años"}: " +
                "${money.format(low.contributed + balance)}${if (ranged) "–${money.format(high.contributed + balance)}" else ""} puesto por ti y " +
                "${money.format(low.interest)}${if (ranged) "–${money.format(high.interest)}" else ""} de intereses.",
            style = MaterialTheme.typography.bodySmall, color = P.muted,
        )
        Text(
            "Entonces te daría ${money.format(low.monthlyInterestAtEnd(ratePct))}${if (ranged) "–${money.format(high.monthlyInterestAtEnd(ratePct))}" else ""} al mes.",
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = P.gain,
        )
    }
}

// ---------- Ingresos ----------

@Composable
fun IncomeSourceSheet(
    initial: IncomeSource,
    accounts: List<Account>,
    average: Double?,
    currency: String,
    onSave: (IncomeSource) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var variable by remember { mutableStateOf(initial.variable) }
    var amount by remember { mutableStateOf(initial.amount.editable()) }
    var dayFrom by remember { mutableStateOf(initial.dayFrom.toString()) }
    var dayTo by remember { mutableStateOf(initial.dayTo.toString()) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    val value = amount.toAmount()
    val from = dayFrom.toIntOrNull()?.takeIf { it in 1..31 }
    val to = (dayTo.toIntOrNull() ?: from)?.takeIf { it in 1..31 }

    FormSheet(
        if (initial.id == 0L) "Nuevo ingreso" else initial.name, onDismiss,
        onPrimary = { onSave(initial.copy(name = name.trim(), variable = variable, amount = value!!, dayFrom = from!!, dayTo = maxOf(from, to!!), accountId = accountId)) },
        primaryEnabled = name.isNotBlank() && value != null && value >= 0 && from != null && to != null,
        onDelete = onDelete,
    ) {
        Segmented(listOf(false, true), variable, { if (it) "Variable" else "Fijo" }, { variable = it })
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        Text(
            if (variable) {
                if (average != null) "Tu media de los últimos meses es ${LocalMoney.current.format(average)}: es la que se usa para el presupuesto. Esta cantidad solo cuenta hasta que haya historial."
                else "Pon una estimación de lo que sueles cobrar. Cuando registres cobros, se usará la media de los últimos 3 meses."
            } else "Lo que cobras cada mes.",
            style = MaterialTheme.typography.bodySmall, color = P.muted,
        )
        TextInput(name, { name = it }, "Nombre (Nómina, Autónomo, YouTube…)")
        FieldLabel("¿Qué días cobras?")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextInput(dayFrom, { dayFrom = it.filter(Char::isDigit).take(2) }, "Del día", Modifier.weight(1f), KeyboardType.Number)
            TextInput(dayTo, { dayTo = it.filter(Char::isDigit).take(2) }, "Al día", Modifier.weight(1f), KeyboardType.Number)
        }
        FieldLabel("¿A qué cuenta llega?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Ninguna", accountId == null, { accountId = null })
            accounts.forEach { a -> Choice(a.name, a.id == accountId, { accountId = a.id }, a.type.icon()) }
        }
    }
}

/** Registrar un cobro: suma el importe a la cuenta y alimenta la media de los variables. */
@Composable
fun IncomeSheet(
    initial: Income,
    source: IncomeSource?,
    accounts: List<Account>,
    currency: String,
    onSave: (Income) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(initial.amount.editable()) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    var day by remember { mutableStateOf(LocalDate.ofEpochDay(initial.epochDay).dayOfMonth.toString()) }
    var note by remember { mutableStateOf(initial.note) }
    val date = LocalDate.ofEpochDay(initial.epochDay)
    val loose = source == null
    val value = amount.toAmount()
    val dayNum = day.toIntOrNull()?.takeIf { it in 1..date.lengthOfMonth() }

    FormSheet(
        if (initial.id == 0L) (if (loose) "Nuevo ingreso" else "Cobro de ${source?.name}") else initial.note.ifBlank { "Ingreso" }, onDismiss,
        primary = if (initial.id == 0L && !loose) "Marcar como cobrado" else "Guardar",
        onPrimary = { onSave(initial.copy(amount = value!!, accountId = accountId, epochDay = date.withDayOfMonth(dayNum!!).toEpochDay(), note = if (loose) note.trim().ifBlank { "Ingreso" } else initial.note)) },
        primaryEnabled = value != null && value > 0 && dayNum != null,
        onDelete = onDelete,
    ) {
        BigAmountInput(amount, { amount = it }, currencySymbol(currency))
        if (loose) TextInput(note, { note = it }, "Concepto (Bizum de Juanpa, venta Wallapop…)")
        if (source?.variable == true) Text("Pon lo que has cobrado de verdad: así la media de los próximos meses será más exacta.", style = MaterialTheme.typography.bodySmall, color = P.muted)
        FieldLabel("Entra en")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("No sumar a ninguna", accountId == null, { accountId = null })
            accounts.forEach { a -> Choice(a.name, a.id == accountId, { accountId = a.id }, a.type.icon()) }
        }
        TextInput(day, { day = it.filter(Char::isDigit).take(2) }, "Día del mes", keyboardType = KeyboardType.Number)
    }
}

// ---------- Cripto ----------

@Composable
fun CoinSearchSheet(search: suspend (String) -> List<CoinResult>, onPick: (CoinResult) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<CoinResult>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val run: () -> Unit = {
        scope.launch {
            loading = true; error = null
            try {
                results = search(query.trim())
                if (results.isEmpty()) error = "No se ha encontrado nada."
            } catch (e: Exception) {
                error = "Sin conexión con CoinGecko."
            }
            loading = false
        }
    }
    FormSheet("Añadir cripto", onDismiss, primary = null) {
        TextField(
            query, { query = it },
            placeholder = { Text("Bitcoin, ETH, Solana…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else IconButton(onClick = run, enabled = query.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.TrendingUp, "Buscar") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) run() }),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = P.chip, unfocusedContainerColor = P.chip,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = P.loss, style = MaterialTheme.typography.bodySmall) }
        results.forEachIndexed { i, c ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(c) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Initials(c.symbol, SeriesColors[(i + 2) % SeriesColors.size], 38.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = P.ink)
                    Text(c.symbol, style = MaterialTheme.typography.bodySmall, color = P.muted)
                }
            }
        }
    }
}

/**
 * Cripto por dinero: pones cuánto tienes en euros y Caudal calcula la cantidad con el precio de
 * ahora. Opcionalmente, lo que metiste, para ver si ganas o pierdes.
 */
@Composable
fun CryptoSheet(
    initial: CryptoHolding,
    currency: String,
    priceOf: suspend (String) -> Double?,
    onSave: (CryptoHolding) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    var price by remember { mutableStateOf(initial.price.takeIf { it > 0 }) }
    var loading by remember { mutableStateOf(initial.price <= 0) }
    androidx.compose.runtime.LaunchedEffect(initial.coinId) {
        priceOf(initial.coinId)?.let { price = it }
        loading = false
    }
    var byQuantity by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf((initial.amount * initial.price).editable()) }
    var invested by remember { mutableStateOf((initial.amount * initial.avgBuyPrice).editable()) }
    var qtyText by remember { mutableStateOf(initial.amount.editable()) }
    val pr = price
    val v = value.toAmount()
    val inv = invested.toAmount() ?: if (invested.isBlank()) 0.0 else null
    val qty = if (byQuantity || pr == null) qtyText.toAmount() else v?.let { it / pr }
    val avg = if (qty != null && qty > 0 && inv != null && inv > 0) inv / qty else 0.0

    FormSheet(
        "${initial.name} · ${initial.symbol}", onDismiss,
        onPrimary = { onSave(initial.copy(amount = qty!!, avgBuyPrice = avg, price = pr ?: initial.price, updatedAt = if (pr != null) System.currentTimeMillis() else initial.updatedAt)) },
        primaryEnabled = qty != null && qty > 0 && inv != null,
        onDelete = onDelete,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.chip).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Initials(initial.symbol, com.caudal.finanzas.ui.screens.symbolColor(initial.symbol), 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                when { pr != null -> "1 ${initial.symbol} = ${money.format(pr)} ahora"; loading -> "Buscando el precio…"; else -> "Sin conexión: pon la cantidad a mano" },
                style = MaterialTheme.typography.titleSmall, color = P.ink, modifier = Modifier.weight(1f),
            )
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (pr != null && !byQuantity) {
            FieldLabel("¿Cuánto dinero tienes en ${initial.name}?")
            BigAmountInput(value, { value = it }, currencySymbol(currency))
            if (qty != null && qty > 0) Text("Son ${quantity(qty)} ${initial.symbol}", style = MaterialTheme.typography.titleSmall, color = P.gain)
        } else {
            AmountField(qtyText, { qtyText = it }, "Cantidad que tienes", suffix = initial.symbol)
        }
        AmountField(invested, { invested = it }, "¿Cuánto metiste en total? (opcional)", suffix = currencySymbol(currency))
        if (qty != null && qty > 0 && inv != null && inv > 0 && pr != null) {
            val pnl = qty * pr - inv
            Text(
                (if (pnl >= 0) "Ganas " else "Pierdes ") + money.format(kotlin.math.abs(pnl)) + " (${signedPct(pnl / inv * 100)})",
                style = MaterialTheme.typography.titleSmall, color = gainColor(pnl),
            )
        } else Text("Si pones lo que metiste, verás cuánto ganas o pierdes.", style = MaterialTheme.typography.bodySmall, color = P.muted)
        if (pr != null) TextButton(onClick = { byQuantity = !byQuantity; if (byQuantity && qty != null) qtyText = qty.editable() }) {
            Text(if (byQuantity) "Prefiero poner el dinero que tengo" else "Prefiero poner la cantidad exacta de ${initial.symbol}", color = P.gain)
        }
    }
}

// ---------- Acción ----------

@Composable
fun StockSheet(initial: StockHolding, currency: String, onSave: (StockHolding, fetchPrice: Boolean) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var ticker by remember { mutableStateOf(initial.ticker) }
    var name by remember { mutableStateOf(initial.name) }
    var shares by remember { mutableStateOf(initial.shares.editable()) }
    var avg by remember { mutableStateOf(initial.avgBuyPrice.editable()) }
    var price by remember { mutableStateOf(initial.price.editable()) }
    val s = shares.toAmount()
    val a = avg.toAmount() ?: if (avg.isBlank()) 0.0 else null
    val manual = price.toAmount()

    FormSheet(
        if (initial.id == 0L) "Nueva acción" else initial.ticker, onDismiss,
        onPrimary = {
            val priceChangedByHand = manual != null && manual != initial.price
            val updated = initial.copy(
                ticker = ticker.trim().uppercase(), name = name.trim(), shares = s!!, avgBuyPrice = a!!,
                price = manual ?: initial.price,
                // updatedAt = 0 marca un precio puesto a mano: la recarga automática no lo pisa.
                updatedAt = if (priceChangedByHand) 0 else initial.updatedAt,
            )
            onSave(updated, !priceChangedByHand && (initial.id == 0L || updated.ticker != initial.ticker))
        },
        primaryEnabled = ticker.isNotBlank() && s != null && s > 0 && a != null && a >= 0,
        onDelete = onDelete,
    ) {
        TextInput(ticker, { ticker = it }, "Ticker (AAPL, SAN.MC, VWCE.DE…)")
        TextInput(name, { name = it }, "Nombre (opcional)")
        AmountField(shares, { shares = it }, "Número de acciones")
        AmountField(avg, { avg = it }, "Precio medio de compra", suffix = currency)
        AmountField(price, { price = it }, "Precio actual (vacío = automático)", suffix = currency)
    }
}

// ---------- Presupuesto ----------

@Composable
fun MonthlyIncomeSheet(current: Double, currency: String, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.editable()) }
    val value = text.toAmount()
    FormSheet("Ingreso mensual", onDismiss, onPrimary = { onSave(value ?: 0.0) }, primaryEnabled = value != null && value >= 0) {
        Text("Lo que entra cada mes (nómina, etc.). Los porcentajes del presupuesto se calculan sobre esto.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
        BigAmountInput(text, { text = it }, currencySymbol(currency))
    }
}

@Composable
fun CategoriesSheet(initial: List<Category>, onSave: (List<Category>) -> Unit, onDismiss: () -> Unit) {
    val rows = remember { mutableStateListOf<Triple<Category, String, String>>().apply { addAll(initial.map { Triple(it, it.name, it.percent.editable()) }) } }
    val total = rows.sumOf { it.third.toAmount() ?: 0.0 }
    val valid = rows.isNotEmpty() && rows.all { it.second.isNotBlank() && (it.third.toAmount() ?: -1.0) >= 0 }

    FormSheet(
        "Reparto del ingreso", onDismiss,
        onPrimary = { onSave(rows.map { (cat, name, percent) -> cat.copy(name = name.trim(), percent = percent.toAmount() ?: 0.0) }) },
        primaryEnabled = valid,
    ) {
        Text("Marca como ahorro lo que no es para gastar.", style = MaterialTheme.typography.bodySmall, color = P.muted)
        rows.forEachIndexed { i, (cat, name, percent) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.chip).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(SeriesColors[i % SeriesColors.size], 12.dp)
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        name, { rows[i] = Triple(cat, it, percent) }, singleLine = true,
                        textStyle = MaterialTheme.typography.titleSmall.copy(color = P.ink, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        percent, { v -> rows[i] = Triple(cat, name, v.filter { it.isDigit() || it == ',' || it == '.' }) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.titleSmall.copy(color = P.ink, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                        modifier = Modifier.width(48.dp),
                    )
                    Text(" %", color = P.muted)
                    IconButton(onClick = { rows.removeAt(i) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Quitar", tint = P.muted) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(cat.isSavings, { rows[i] = Triple(cat.copy(isSavings = it), name, percent) }, colors = CheckboxDefaults.colors(checkedColor = P.gain))
                    Text("Es ahorro", style = MaterialTheme.typography.bodySmall, color = P.ink)
                }
            }
        }
        TextButton(onClick = { rows.add(Triple(Category(name = "Nueva", percent = 0.0), "Nueva", "")) }) {
            Icon(Icons.Filled.Add, null, tint = P.gain); Spacer(Modifier.width(6.dp)); Text("Añadir categoría", color = P.gain)
        }
        Box(Modifier.fillMaxWidth()) {
            SoftTag("Total: ${pct(total)}", if (abs(total - 100) < 0.01) P.gain else P.loss)
        }
        if (initial.any { c -> rows.none { it.first.id == c.id } }) {
            Text("Al quitar una categoría se borran también sus gastos.", style = MaterialTheme.typography.bodySmall, color = P.loss)
        }
    }
}

/** Al tocar una categoría: todos sus movimientos del mes, el total y cómo va frente al mes anterior. */
@Composable
fun TagDetailSheet(
    tag: SpendTag,
    month: java.time.YearMonth,
    expenses: List<Expense>,
    accounts: List<Account>,
    limit: Double?,
    onOpenExpense: (Expense) -> Unit,
    onSetLimit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    fun inMonth(e: Expense, m: java.time.YearMonth) = java.time.YearMonth.from(LocalDate.ofEpochDay(e.epochDay)) == m
    val list = expenses.filter { inMonth(it, month) && it.spendTag() == tag }.sortedByDescending { it.epochDay }
    val prev = expenses.filter { inMonth(it, month.minusMonths(1)) && it.spendTag() == tag }.sumOf { it.amount }
    val total = list.sumOf { it.amount }
    val es = java.util.Locale.forLanguageTag("es-ES")
    FormSheet(tag.label, onDismiss, primary = "Fijar límite para ${tag.label.lowercase()}", onPrimary = onSetLimit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(tag.icon(), tag.color(), 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(money.format(total), style = MaterialTheme.typography.headlineSmall, color = P.ink)
                Text(
                    "${list.size} ${if (list.size == 1) "pago" else "pagos"} en ${month.month.getDisplayName(java.time.format.TextStyle.FULL, es)}" +
                        if (list.isNotEmpty()) " · media ${money.format(total / list.size)}" else "",
                    style = MaterialTheme.typography.bodySmall, color = P.muted,
                )
            }
        }
        Text(
            when {
                prev <= 0 && total <= 0 -> "Sin gastos en este tipo ni este mes ni el anterior."
                prev <= 0 -> "El mes anterior no gastaste nada en esto."
                total > prev -> "Llevas ${money.format(total - prev)} más que en todo el mes anterior (${money.format(prev)})."
                else -> "El mes anterior gastaste ${money.format(prev)} en total: llevas ${money.format(prev - total)} menos."
            },
            style = MaterialTheme.typography.bodyMedium, color = P.ink,
        )
        if (limit != null && limit > 0) {
            ProgressLine(total / limit, tag.color(), height = 8.dp)
            Text(
                if (total <= limit) "Te quedan ${money.format(limit - total)} de tu límite de ${money.format(limit)}." else "Te has pasado ${money.format(total - limit)} de tu límite de ${money.format(limit)}.",
                style = MaterialTheme.typography.bodySmall, color = if (total <= limit) P.muted else P.loss,
            )
        }
        if (list.isEmpty()) Text("No hay movimientos de este tipo este mes.", style = MaterialTheme.typography.bodyMedium, color = P.muted)
        list.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenExpense(e) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.note.ifBlank { tag.label }, style = MaterialTheme.typography.titleSmall, color = P.ink, maxLines = 1)
                    Text(
                        listOfNotNull(
                            LocalDate.ofEpochDay(e.epochDay).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", es)),
                            accounts.firstOrNull { it.id == e.accountId }?.name ?: "sin cuenta",
                            if (e.fromCard) "tarjeta" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = P.muted,
                    )
                }
                Text("−" + money.format(e.amount), style = MaterialTheme.typography.titleSmall, color = P.ink)
            }
        }
    }
}

/** Un gasto que en realidad fue un traspaso: eliges a qué cuenta tuya fue y se convierte en transferencia. */
@Composable
fun ConvertTransferSheet(expense: Expense, accounts: List<Account>, onConvert: (Long) -> Unit, onDismiss: () -> Unit) {
    val money = LocalMoney.current
    val from = accounts.firstOrNull { it.id == expense.accountId }
    val options = accounts.filter { it.id != expense.accountId && !it.isDebt() }
    val text = (expense.note + " " + expense.rawText).lowercase()
    var to by remember { mutableStateOf(options.firstOrNull { text.contains(it.name.lowercase()) }?.id ?: options.firstOrNull { it.isSaving() }?.id) }
    FormSheet("Era un traspaso", onDismiss, primary = "Convertir en transferencia", onPrimary = { to?.let(onConvert) }, primaryEnabled = to != null) {
        Text("${money.format(expense.amount)} desde ${from?.name ?: "tu cuenta"}. ¿A qué cuenta tuya fue?", style = MaterialTheme.typography.bodyMedium, color = P.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { a -> Choice(a.name, a.id == to, { to = a.id }, a.type.icon()) }
        }
        Text("El gasto desaparece de tus gastos y el dinero pasa de una cuenta a otra: tu patrimonio no cambia. Si fue a tu cuenta de ahorro, cuenta como el ahorro del mes.", style = MaterialTheme.typography.bodySmall, color = P.muted)
    }
}
