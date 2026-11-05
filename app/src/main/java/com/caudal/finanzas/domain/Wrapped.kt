package com.caudal.finanzas.domain

import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Transfer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** El "Wrapped" de un mes: tus números contados como tarjetas. Todo sale de tus datos, sin IA. */
data class MonthWrapped(
    val month: YearMonth,
    val income: Double,
    val spent: Double,
    /** Gasto de cada día del mes (los días que aún no han llegado, 0). */
    val daily: List<Double>,
    /** Días del mes que ya han pasado (todo el mes si está cerrado). */
    val daysElapsed: Int,
    val payments: Int,
    val topTag: Triple<SpendTag, Double, Double>?,
    /** Comercio donde más has gastado: nombre, total y veces. */
    val topPlace: Triple<String, Double, Int>?,
    val biggest: Expense?,
    val priciestWeekday: Pair<DayOfWeek, Double>?,
    val noSpendDays: Int,
    val topDay: Pair<LocalDate, Double>?,
    /** Gasto del mes anterior en los mismos días (null si no hay datos). */
    val prevSpent: Double?,
    val saved: Double,
    /** Saldo de tus cuentas corrientes ahora (tu liquidez; sin ahorros ni inversiones). */
    val bank: Double = 0.0,
) {
    val left get() = income - spent
    val perDay get() = if (daysElapsed > 0) spent / daysElapsed else 0.0
    val isCurrent get() = month == YearMonth.now()
    /** Cambio frente al mes anterior en %, solo si hay base. */
    val vsPrevPct get() = prevSpent?.takeIf { it > 0 }?.let { (spent - it) / it * 100 }
}

object Wrapped {
    fun of(d: FinanceData, month: YearMonth, transfers: List<Transfer> = emptyList()): MonthWrapped {
        val today = d.today
        val len = month.lengthOfMonth()
        val elapsed = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> len
        }
        val spending = d.spendingIn(month)
        val spent = spending.sumOf { it.amount }
        val daily = (1..len).map { day -> spending.filter { LocalDate.ofEpochDay(it.epochDay).dayOfMonth == day }.sumOf { it.amount } }
        val tags = spending.groupBy { it.spendTag() }.mapValues { (_, l) -> l.sumOf { it.amount } }.maxByOrNull { it.value }
        val place = spending.filter { it.note.isNotBlank() }.groupBy { it.note.trim().lowercase() }
            .map { (_, l) -> Triple(l.first().note.trim(), l.sumOf { it.amount }, l.size) }
            .maxByOrNull { it.second }
        val weekday = spending.groupBy { LocalDate.ofEpochDay(it.epochDay).dayOfWeek }.mapValues { (_, l) -> l.sumOf { it.amount } }.maxByOrNull { it.value }
        val topDayIdx = daily.indices.maxByOrNull { daily[it] }?.takeIf { daily[it] > 0 }
        val prevMonth = month.minusMonths(1)
        val prevList = d.spendingIn(prevMonth).filter { LocalDate.ofEpochDay(it.epochDay).dayOfMonth <= elapsed }
        val hasPrev = d.firstMonth?.let { !prevMonth.isBefore(it) } == true
        val savingAccounts = d.accounts.filter { it.type in setOf(AccountType.BANK_SAVINGS, AccountType.HOME_SAVINGS, AccountType.FUND) }.map { it.id }.toSet()
        val savedByTransfer = transfers.filter { it.toAccountId in savingAccounts && it.fromAccountId !in savingAccounts && d.inMonth(it.epochDay, month) }.sumOf { it.amount }
        val savedByCategory = d.expenses.filter { it.categoryId in d.savingIds && d.inMonth(it.epochDay, month) }.sumOf { it.amount }
        return MonthWrapped(
            month = month,
            income = d.receivedIn(month),
            spent = spent,
            daily = daily,
            daysElapsed = elapsed,
            payments = spending.size,
            topTag = tags?.let { Triple(it.key, it.value, if (spent > 0) it.value / spent * 100 else 0.0) },
            topPlace = place,
            biggest = spending.maxByOrNull { it.amount },
            priciestWeekday = weekday?.let { it.key to it.value },
            noSpendDays = daily.take(elapsed).count { it <= 0.0 },
            topDay = topDayIdx?.let { month.atDay(it + 1) to daily[it] },
            prevSpent = if (hasPrev) prevList.sumOf { it.amount } else null,
            saved = savedByTransfer + savedByCategory,
            bank = d.accounts.filter { it.type == AccountType.BANK }.sumOf { it.balance },
        )
    }
}
