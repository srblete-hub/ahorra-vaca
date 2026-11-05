package com.caudal.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.Transfer
import com.caudal.finanzas.domain.InterestMath
import com.caudal.finanzas.ui.AccountLogo
import com.caudal.finanzas.ui.AmountTrailing
import com.caudal.finanzas.ui.AnimatedAmount
import com.caudal.finanzas.ui.CardList
import com.caudal.finanzas.ui.Editing
import com.caudal.finanzas.ui.EmptyState
import com.caudal.finanzas.ui.IconTile
import com.caudal.finanzas.ui.ListRow
import com.caudal.finanzas.ui.LocalMoney
import com.caudal.finanzas.ui.MCard
import com.caudal.finanzas.ui.P
import com.caudal.finanzas.ui.RoundIconButton
import com.caudal.finanzas.ui.SectionTitle
import com.caudal.finanzas.ui.SeriesColors
import com.caudal.finanzas.ui.pct

/** Una cuenta por dentro: su saldo, lo que ha entrado y salido, y acciones rápidas. */
@Composable
fun AccountScreen(vm: FinanceViewModel, accountId: Long, onEdit: (Editing) -> Unit, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenses by vm.allExpenses.collectAsStateWithLifecycle()
    val incomes by vm.incomes.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val account = accounts.firstOrNull { it.id == accountId }
    if (account == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }

    // Todo lo que ha tocado esta cuenta, de lo más nuevo a lo más viejo.
    data class Move(val day: Long, val created: Long, val item: Any)
    val moves = (
        expenses.filter { it.accountId == accountId }.map { Move(it.epochDay, it.createdAt, it) } +
            incomes.filter { it.accountId == accountId }.map { Move(it.epochDay, it.createdAt, it) } +
            transfers.filter { it.fromAccountId == accountId || it.toAccountId == accountId }.map { Move(it.epochDay, it.createdAt, it) }
        ).sortedWith(compareByDescending<Move> { it.day }.thenByDescending { it.created })

    val status = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = status + 8.dp, bottom = nav + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AccountLogo(account.name, 72.dp)
                Spacer(Modifier.height(12.dp))
                Text(account.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = P.ink)
                Text(account.type.label, style = MaterialTheme.typography.bodyMedium, color = P.muted)
                Spacer(Modifier.height(12.dp))
                AnimatedAmount(account.balance, 42.sp, P.ink)
                if (account.interestRate > 0) {
                    Text(
                        run {
                            val since = listOfNotNull(
                                incomes.minOfOrNull { it.epochDay }, expenses.minOfOrNull { it.epochDay }, transfers.minOfOrNull { it.epochDay },
                            ).minOrNull()?.let { java.time.LocalDate.ofEpochDay(it) } ?: java.time.LocalDate.now()
                            val e = com.caudal.finanzas.domain.InterestEarned.of(account, incomes, transfers, java.time.LocalDate.now(), since)
                            "${pct(account.interestRate)} TAE · ${if (e.estimated) "≈ " else ""}+${money.format(e.earned)} ganados (interés compuesto) · ahora +${money.format(e.monthlyNow)}/mes"
                        },
                        style = MaterialTheme.typography.labelLarge, color = P.gain,
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.Filled.SwapHoriz, "Transferir") { onEdit(Editing.TransferForm(account.id)) }
                ActionButton(Icons.Filled.Tune, "Ajustar saldo") { onEdit(Editing.BalanceForm(account)) }
                ActionButton(Icons.Filled.Edit, "Editar") { onEdit(Editing.AccountForm(account)) }
            }
        }
        item { SectionTitle("Movimientos") }
        item {
            if (moves.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Sin movimientos", "Los gastos, cobros y transferencias de esta cuenta aparecerán aquí.")
            } else {
                CardList(moves, key = { "${it.item::class.simpleName}-${it.item.hashCode()}" }) { m ->
                    when (val x = m.item) {
                        is Expense -> ExpenseRow(x, categories, accounts, onClick = { onEdit(Editing.ExpenseForm(x)) })
                        is Income -> IncomeRow(x, accounts, onClick = { onEdit(Editing.IncomeForm(x)) })
                        is Transfer -> {
                            val incoming = x.toAccountId == accountId
                            val other = accounts.firstOrNull { it.id == if (incoming) x.fromAccountId else x.toAccountId }?.name ?: "otra cuenta"
                            ListRow(
                                title = if (incoming) "Desde $other" else "A $other",
                                subtitle = listOfNotNull(dayLabel(x.epochDay), "Transferencia", x.note.ifBlank { null }).joinToString(" · "),
                                leading = { IconTile(Icons.Filled.SwapHoriz, SeriesColors[1]) },
                                trailing = { AmountTrailing((if (incoming) "+" else "-") + money.format(x.amount), color = if (incoming) P.gain else P.ink) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(8.dp)) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(P.card).border(1.dp, P.cardBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(24.dp), tint = P.ink) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = P.ink)
    }
}
