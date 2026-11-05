package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.RecurringBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlanningTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val rent = RecurringBill(id = 1, name = "Alquiler", amount = 650.0, day = 8, categoryId = 1)
    private val netflix = RecurringBill(id = 2, name = "Netflix", amount = 14.0, day = 20, categoryId = 2)
    private val salary = IncomeSource(id = 1, name = "Nómina", variable = false, amount = 1800.0, dayFrom = 28, dayTo = 28)

    @Test fun billPaidByLinkOrByNameAndAmount() {
        val nov = YearMonth.of(2026, 11)
        assertFalse(Bills.isPaid(rent, nov, emptyList()))
        assertTrue(Bills.isPaid(rent, nov, listOf(Expense(categoryId = 1, amount = 1.0, epochDay = today.toEpochDay(), billId = 1))))
        assertTrue(Bills.isPaid(rent, nov, listOf(Expense(categoryId = 1, amount = 640.0, note = "alquiler noviembre", epochDay = today.toEpochDay()))))
        assertFalse(Bills.isPaid(rent, nov, listOf(Expense(categoryId = 1, amount = 300.0, note = "Alquiler", epochDay = today.toEpochDay()))))
    }

    @Test fun yearlyBillOnlyInItsMonth() {
        val insurance = RecurringBill(name = "Seguro", amount = 420.0, day = 10, yearly = true, month = 3, categoryId = 1)
        assertEquals(null, Bills.dueDate(insurance, YearMonth.of(2026, 11)))
        assertEquals(LocalDate.of(2027, 3, 10), Bills.dueDate(insurance, YearMonth.of(2027, 3)))
        assertEquals(35.0, Bills.monthly(insurance), 0.001)
    }

    @Test fun forecastWarnsBeforeRunningShort() {
        // 500 € hoy, alquiler el 8 y nómina el 28: entre medias te quedas en negativo.
        val f = Forecasting.forecast(today, 500.0, 30, listOf(salary), emptyList(), listOf(rent, netflix), emptyList(), emptySet())
        val negative = f.firstNegative
        assertNotNull(negative)
        assertEquals(LocalDate.of(2026, 11, 8), negative!!.first)
        assertEquals(500.0 - 650.0 - 14.0 + 1800.0, f.points.first { it.first == LocalDate.of(2026, 11, 28) }.second, 0.001)
    }

    @Test fun safeToSpendSubtractsBillsBeforePaydayAndSavings() {
        val events = Forecasting.events(today, today.plusDays(60), listOf(salary), emptyList(), listOf(rent, netflix), emptyList())
        val s = Spending.safeToSpend(today, 1200.0, events, plannedSavings = 200.0, savedThisMonth = 0.0)
        assertEquals(LocalDate.of(2026, 11, 28), s.nextPayday)
        assertEquals(664.0, s.pendingBills, 0.001)
        assertEquals(1200.0 - 664.0 - 200.0, s.safe, 0.001)
        assertEquals(24, s.daysToPayday)
    }

    @Test fun levelsGrowWithPoints() {
        assertEquals(1, Achievements.level(0).number)
        assertEquals("Ahorrador", Achievements.level(80).name)
        assertTrue(Achievements.level(10_000).to == null)
    }
}
