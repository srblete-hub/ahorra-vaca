package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class FinanceMathTest {

    // ---------- Disponible / ahorro / invertido ----------

    @Test fun wealthSplit() {
        val accounts = listOf(
            com.caudal.finanzas.data.Account(id = 1, name = "Nómina", type = com.caudal.finanzas.data.AccountType.BANK, balance = 1000.0),
            com.caudal.finanzas.data.Account(id = 2, name = "Cartera", type = com.caudal.finanzas.data.AccountType.CASH, balance = 50.0),
            com.caudal.finanzas.data.Account(id = 3, name = "Hucha", type = com.caudal.finanzas.data.AccountType.HOME_SAVINGS, balance = 300.0),
            // Una cuenta corriente que da intereses cuenta como ahorro.
            com.caudal.finanzas.data.Account(id = 4, name = "Remunerada", type = com.caudal.finanzas.data.AccountType.BANK, balance = 200.0, interestRate = 5.0),
        )
        val crypto = listOf(com.caudal.finanzas.data.CryptoHolding(coinId = "bitcoin", symbol = "BTC", name = "Bitcoin", amount = 0.01, avgBuyPrice = 0.0, price = 60000.0))
        val w = Wealth.of(accounts, crypto, emptyList())
        assertEquals(1050.0, w.available, 0.001)
        assertEquals(500.0, w.savings, 0.001)
        assertEquals(600.0, w.invested, 0.001)
        assertEquals(2150.0, w.total, 0.001)
    }

    // ---------- Intereses ----------

    @Test fun fivePercentOnTenThousand() {
        // 5 % TAE sobre 10.000 €: unos 40,74 € el primer mes y 500 € al año.
        assertEquals(40.74, InterestMath.monthlyInterest(10_000.0, 5.0), 0.01)
        val year = InterestMath.project(10_000.0, 5.0, 0.0, 12)
        assertEquals(10_500.0, year.finalBalance, 0.01)
        assertEquals(500.0, year.interest, 0.01)
    }

    @Test fun monthlyContributions() {
        val p = InterestMath.project(0.0, 5.0, 100.0, 12)
        assertEquals(1200.0, p.contributed, 0.001)
        assertTrue("Metiendo 100 €/mes se ganan intereses", p.interest in 20.0..30.0)
        val zero = InterestMath.project(1000.0, 0.0, 50.0, 10)
        assertEquals(1500.0, zero.finalBalance, 0.001)
    }

    // ---------- Ingresos ----------

    private val nov = YearMonth.of(2026, 11)
    private fun day(m: YearMonth, d: Int) = m.atDay(d).toEpochDay()
    private val salary = IncomeSource(id = 1, name = "Nómina", variable = false, amount = 1650.0, dayFrom = 1, dayTo = 5)
    private val youtube = IncomeSource(id = 2, name = "YouTube", variable = true, amount = 200.0, dayFrom = 22, dayTo = 22)

    @Test fun fixedPlusVariableAverage() {
        val history = listOf(
            Income(sourceId = 2, amount = 300.0, note = "", epochDay = day(nov.minusMonths(1), 22), accountId = null),
            Income(sourceId = 2, amount = 450.0, note = "", epochDay = day(nov.minusMonths(2), 22), accountId = null),
            Income(sourceId = 2, amount = 300.0, note = "", epochDay = day(nov.minusMonths(3), 22), accountId = null),
            Income(sourceId = 2, amount = 9999.0, note = "", epochDay = day(nov.minusMonths(4), 22), accountId = null), // fuera de la media
        )
        val m = IncomeMath.forMonth(listOf(salary, youtube), history, nov)
        assertEquals(1650.0 + 350.0, m.total, 0.001)
        val yt = m.sources.first { it.source.id == 2L }
        assertTrue(yt.isEstimate)
        assertEquals(350.0, yt.average!!, 0.001)
    }

    @Test fun variableWithoutHistoryUsesEstimate() {
        val m = IncomeMath.forMonth(listOf(youtube), emptyList(), nov)
        assertEquals(200.0, m.total, 0.001)
    }

    @Test fun receivedReplacesEstimate() {
        val incomes = listOf(
            Income(sourceId = 1, amount = 1700.0, note = "", epochDay = day(nov, 3), accountId = null),
            Income(sourceId = 2, amount = 512.0, note = "", epochDay = day(nov, 22), accountId = null),
            Income(sourceId = null, amount = 40.0, note = "Intereses", epochDay = day(nov, 1), accountId = null),
        )
        val m = IncomeMath.forMonth(listOf(salary, youtube), incomes, nov)
        assertEquals(1700.0 + 512.0, m.total, 0.001)
        assertEquals(1700.0 + 512.0 + 40.0, m.received, 0.001)
        assertFalse(m.sources.any { it.isEstimate })
    }

    // ---------- Reparto ----------

    private val base = listOf(
        Category(id = 1, name = "Necesidades", percent = 50.0),
        Category(id = 2, name = "Ocio", percent = 30.0),
        Category(id = 3, name = "Ahorro", percent = 20.0, isSavings = true),
    )

    @Test fun raisingSavingsShrinksTheRestProportionally() {
        val r = Allocation.rebalance(base, 3, 36.0)
        assertEquals(100.0, r.sumOf { it.percent }, 0.001)
        assertEquals(36.0, r.first { it.id == 3L }.percent, 0.001)
        assertEquals(40.0, r.first { it.id == 1L }.percent, 0.001)
        assertEquals(24.0, r.first { it.id == 2L }.percent, 0.001)
    }

    @Test fun lockedCategoryDoesNotMove() {
        val locked = base.map { if (it.id == 1L) it.copy(locked = true) else it }
        val r = Allocation.rebalance(locked, 3, 40.0)
        assertEquals(50.0, r.first { it.id == 1L }.percent, 0.001)
        assertEquals(10.0, r.first { it.id == 2L }.percent, 0.001)
        // No puede pasar del 50 % que queda libre.
        assertEquals(50.0, Allocation.rebalance(locked, 3, 90.0).first { it.id == 3L }.percent, 0.001)
    }

    @Test fun presetsAlwaysSumHundred() {
        Allocation.Preset.entries.forEach { p ->
            val r = Allocation.applyPreset(base, p)
            assertEquals(100.0, r.sumOf { it.percent }, 0.001)
            assertEquals(p.savings, r.first { it.isSavings }.percent, 0.001)
        }
    }
}
