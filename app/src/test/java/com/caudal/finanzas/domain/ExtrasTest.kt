package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.RecurringBill
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExtrasTest {
    private val today = LocalDate.of(2026, 11, 20) // viernes
    private val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }
    private var id = 1L
    private fun e(amount: Double, note: String, day: LocalDate, tag: String = "") = Expense(id++, 2, amount, note, day.toEpochDay(), tag = tag)

    private fun data(exp: List<Expense>, bills: List<RecurringBill> = emptyList()) = FinanceData(
        today, listOf(Account(1, "Santander", AccountType.BANK, 800.0)), emptyList(), emptyList(),
        listOf(Category(1, "Necesidades", 50.0), Category(2, "Ocio", 30.0)), exp, emptyList(), emptyList(), bills, emptyList(),
    )

    @After fun clearTrips() { Trips.all = emptyList() }

    @Test fun nightSummaryTellsTodayAndTomorrowBills() {
        val d = data(listOf(e(12.0, "Bar Pepe", today), e(3.0, "Café", today)), listOf(RecurringBill(1, "Netflix", 13.99, day = 21, categoryId = 2)))
        val r = NightSummary.of(d, 20.0, fmt)
        assertEquals("Hoy has gastado 15.00 € de 20.00 €", r.title)
        assertTrue(r.text.contains("Te sobran 5.00 €"))
        assertTrue(r.text.contains("Mañana te cobran Netflix (13.99 €)"))
        assertEquals("Hoy te has pasado 5.00 €", NightSummary.of(d, 10.0, fmt).title)
        assertEquals("Hoy no has gastado nada", NightSummary.of(data(emptyList()), 20.0, fmt).title)
    }

    @Test fun tripSpendingGoesApartFromTheDayToDay() {
        val trip = Trip("Lisboa", 300.0, today.minusDays(1).toEpochDay(), today.plusDays(2).toEpochDay())
        Trips.all = listOf(trip)
        val inTrip = e(40.0, "Restaurante", today)
        val before = e(10.0, "Café", today.minusDays(3))
        val d = data(listOf(inTrip, before))
        assertTrue(Budgeting.isFixed(inTrip, emptyList()))
        assertFalse(Budgeting.isFixed(before, emptyList()))
        assertEquals(0.0, TodayLeft.spentToday(d), 0.001)
        val s = Trips.status(d, trip)
        assertEquals(260.0, s.left, 0.001)
        assertEquals(3, s.daysLeft)
        assertEquals(Trip.parseAll(Trip.encodeAll(listOf(trip))), listOf(trip))
    }

    @Test fun challengesProgressFailAndFinish() {
        val c = Challenges.byId("bares")!!
        val start = today.minusDays(2)
        val ok = Challenges.status(data(listOf(e(30.0, "Mercadona", today))), c, start, 20.0)
        assertNull(ok.failedOn); assertFalse(ok.done); assertEquals(3, ok.day)
        val broken = Challenges.status(data(listOf(e(8.0, "Cervecería La Plaza", today.minusDays(1)))), c, start, 20.0)
        assertEquals(today.minusDays(1), broken.failedOn)
        val finished = Challenges.status(data(emptyList()), c, today.minusDays(7), 20.0)
        assertTrue(finished.done)
        val zero = Challenges.status(data(listOf(e(1.0, "Chicle", today.minusDays(1)))), Challenges.byId("cero")!!, today.minusDays(1), 20.0)
        assertNotNull(zero.failedOn)
        assertEquals(3, Challenges.suggest(data(emptyList())).size)
    }

    @Test fun roundUpUsesLastClosedWeek() {
        // Semana pasada: lunes 9 a domingo 15.
        val d = data(listOf(e(3.40, "Café", LocalDate.of(2026, 11, 10)), e(9.99, "Libro", LocalDate.of(2026, 11, 15)), e(2.0, "Pan", LocalDate.of(2026, 11, 12)), e(7.30, "Hoy", today)))
        assertEquals(LocalDate.of(2026, 11, 9), RoundUp.lastWeek(today))
        assertEquals(0.61, RoundUp.of(d), 0.001)
    }

    @Test fun compareSameDaysOfLastMonth() {
        val d = data(listOf(
            e(50.0, "Mercadona", today.minusDays(2)), e(20.0, "Bar", today.minusDays(1)),
            e(30.0, "Mercadona", LocalDate.of(2026, 10, 5)), e(40.0, "Bar", LocalDate.of(2026, 10, 10)),
            e(500.0, "Zara", LocalDate.of(2026, 10, 28)), // después del día 20: no cuenta
        ))
        val c = MonthComparer.of(d, YearMonth.of(2026, 11))!!
        assertEquals(20, c.days)
        assertEquals(70.0, c.now, 0.001); assertEquals(70.0, c.before, 0.001)
        assertEquals(2, c.rows.size)
    }
}
