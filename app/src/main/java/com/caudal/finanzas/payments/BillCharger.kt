package com.caudal.finanzas.payments

import android.content.Context
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.RecurringBill
import com.caudal.finanzas.domain.BillReminders
import com.caudal.finanzas.domain.SpendTag
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/**
 * Descuenta solo los gastos fijos (suscripciones, recibos…) el día que se cobran, de la cuenta que elegiste.
 * Los cobros de antes de apuntar el recibo no se tocan: tu saldo ya los incluía. Si el banco avisa del mismo
 * cargo, no se apunta dos veces ([PaymentRecorder] lo descarta).
 */
object BillCharger {
    private const val STORE = "bill_charger"

    /** Día (epochDay) desde el que se cobra solo cada recibo: el día en que la app lo vio por primera vez. */
    private fun firstSeen(context: Context, bill: RecurringBill, today: LocalDate): Long {
        val sp = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val key = "seen-${bill.id}"
        if (!sp.contains(key)) sp.edit().putLong(key, today.toEpochDay()).apply()
        return sp.getLong(key, today.toEpochDay())
    }

    /** Marca un recibo como "ya visto hoy" (al crearlo): los cobros desde hoy se descuentan solos. */
    fun markSeen(context: Context, billId: Long, day: LocalDate = LocalDate.now()) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putLong("seen-$billId", day.toEpochDay()).apply()
    }

    suspend fun chargeDue(context: Context, today: LocalDate = LocalDate.now()) {
        val dao = FinanceDatabase.get(context).dao()
        val bills = dao.bills().first().filter { it.accountId != null }
        if (bills.isEmpty()) return
        val d = BackgroundData.load(context)
        bills.forEach { b ->
            // Las pruebas gratis no se cobran hasta que acaban.
            if (b.trialEnds != null && b.trialEnds >= today.toEpochDay()) return@forEach
            val m = YearMonth.from(today)
            if (b.yearly && b.month != m.monthValue) return@forEach
            val due = m.atDay(minOf(b.day, m.lengthOfMonth()))
            if (due.isAfter(today)) return@forEach
            if (due.toEpochDay() < firstSeen(context, b, today)) return@forEach
            if (BillReminders.paidIn(d, b, m)) return@forEach
            dao.saveExpense(
                Expense(
                    categoryId = b.categoryId, amount = b.amount, note = b.name, epochDay = due.toEpochDay(), accountId = b.accountId,
                    billId = b.id, tag = if (BillReminders.isSubscription(b)) SpendTag.SUBSCRIPTIONS.name else "",
                ),
            )
        }
    }
}
