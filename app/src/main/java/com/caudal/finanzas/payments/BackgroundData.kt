package com.caudal.finanzas.payments

import android.content.Context
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.domain.CashPlanner
import com.caudal.finanzas.domain.FinanceData
import com.caudal.finanzas.domain.IncomeMath
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** Los datos de la app leídos fuera de ella (avisos, widget), igual que los calcula la app. */
object BackgroundData {
    suspend fun load(context: Context): FinanceData {
        val s = Prefs.get(context).settings.value
        IncomeMath.confirmed = s.expectedIncome
        val dao = FinanceDatabase.get(context).dao()
        return FinanceData(
            LocalDate.now(), dao.accounts().first(), dao.crypto().first(), dao.stocks().first(), dao.categories().first(),
            dao.allExpenses().first(), dao.incomes().first(), dao.incomeSources().first(), dao.bills().first(), emptyList(),
            dao.splits().first(), s.planReserve,
        )
    }

    /** Lo que toca gastar al día (sin fijos), como en "Hoy te quedan". */
    fun perDay(context: Context, d: FinanceData): Double? =
        CashPlanner.of(d, Prefs.get(context).settings.value.bankBuffer)?.perDayBefore?.takeIf { it > 0 }

    fun money(context: Context): (Double) -> String {
        val f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).apply { currency = Currency.getInstance(Prefs.get(context).settings.value.currency) }
        return { f.format(it) }
    }
}
