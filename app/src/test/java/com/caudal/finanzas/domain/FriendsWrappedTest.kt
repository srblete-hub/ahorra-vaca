package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FriendsWrappedTest {
    private val today = LocalDate.of(2026, 11, 20)

    @Test fun tricountBalanceReplacesWhatIsAlreadyInside() {
        val d1 = Debt(1, "Juanpa", 8.0, "Pádel", today.minusDays(5).toEpochDay(), 10, inTricount = true)
        val d2 = Debt(2, "Juanpa", 5.0, "Café", today.minusDays(4).toEpochDay())
        val d3 = Debt(3, "juanpa", 6.0, "Cena", today.toEpochDay(), inTricount = true)
        val debts = listOf(d1, d2, d3)
        assertEquals(debts, Debts.parse(Debts.format(debts)))
        // Sin Tricount: suma todo.
        assertEquals(19.0, Debts.balances(debts, emptyMap()).single().net, 0.001)
        // Tricount dice que le debo 20 € (a 2 días de hoy): el pádel ya está dentro; el café y la cena (posterior) se suman.
        val tri = mapOf("Juanpa" to TricountBalance(-20.0, today.minusDays(2).toEpochDay()))
        assertEquals(tri, Debts.parseTricount(Debts.formatTricount(tri)))
        val b = Debts.balances(debts, tri).single()
        assertEquals(-20.0 + 5.0 + 6.0, b.net, 0.001)
        assertEquals(listOf("juanpa"), Debts.people(debts, tri).map { it.lowercase() }.distinct())
    }

    @Test fun confirmedVariableIncomeIsUsedAsIs() {
        val yt = IncomeSource(2, "YouTube", true, 200.0, 22, 22, accountId = 1)
        val m = YearMonth.of(2026, 11)
        val past = (1..3).map { Income(it.toLong(), 2, 400.0, "YouTube", m.minusMonths(it.toLong()).atDay(22).toEpochDay(), 1) }
        IncomeMath.confirmed = emptyMap()
        assertEquals(400.0, IncomeMath.forMonth(listOf(yt), past, m).total, 0.001)
        IncomeMath.confirmed = mapOf(IncomeMath.key(2, m) to 290.0)
        val mi = IncomeMath.forMonth(listOf(yt), past, m)
        assertEquals(290.0, mi.total, 0.001)
        assertEquals(290.0, mi.sources.single().confirmed!!, 0.001)
        IncomeMath.confirmed = emptyMap()
    }

    @Test fun compoundInterestCountsEachDepositFromItsDate() {
        val acc = Account(2, "Robinhood", AccountType.BANK_SAVINGS, 450.0, interestRate = 5.0)
        val start = today.minusDays(61)
        val transfers = listOf(
            Transfer(1, 1, 2, 200.0, "", start.toEpochDay()),
            Transfer(2, 1, 2, 250.0, "", today.minusDays(30).toEpochDay()),
        )
        val e = InterestEarned.of(acc, emptyList(), transfers, today, start)
        val r = InterestMath.monthlyRate(5.0)
        val expected = 200 * (Math.pow(1 + r, 61 / 30.4375) - 1) + 250 * (Math.pow(1 + r, 30 / 30.4375) - 1)
        assertEquals(expected, e.earned, 0.001)
        assertTrue(e.estimated)
        // Mucho menos que "saldo × 5 %/año".
        assertTrue(e.earned < 450 * 0.05 / 2)
    }

    @Test fun wrappedSummarisesTheMonth() {
        val cats = listOf(Category(1, "Necesidades", 50.0), Category(3, "Ahorro", 20.0, isSavings = true))
        val m = YearMonth.from(today)
        val exp = listOf(
            Expense(1, 1, 16.0, "Pádel", m.atDay(3).toEpochDay()),
            Expense(2, 1, 16.0, "Pádel", m.atDay(10).toEpochDay()),
            Expense(3, 1, 60.0, "Mercadona", m.atDay(12).toEpochDay()),
            Expense(4, 1, 30.0, "Mercadona", m.minusMonths(1).atDay(5).toEpochDay()),
        )
        val d = FinanceData(
            today, listOf(Account(1, "Santander", AccountType.BANK, 700.0), Account(2, "Robinhood", AccountType.BANK_SAVINGS, 450.0)),
            emptyList(), emptyList(), cats, exp, listOf(Income(1, null, 1500.0, "Nómina", m.atDay(2).toEpochDay(), 1)), emptyList(), emptyList(), emptyList(),
        )
        val w = Wrapped.of(d, m, listOf(Transfer(1, 1, 2, 250.0, "", m.atDay(4).toEpochDay())))
        assertEquals(92.0, w.spent, 0.001)
        assertEquals(1500.0 - 92.0, w.left, 0.001)
        assertEquals("Mercadona", w.topPlace!!.first)
        assertEquals(60.0, w.biggest!!.amount, 0.001)
        assertEquals(250.0, w.saved, 0.001)
        assertEquals(700.0, w.bank, 0.001)
        assertEquals(20, w.daysElapsed)
        assertEquals(17, w.noSpendDays)
        assertEquals(30.0, w.prevSpent!!, 0.001)
        assertNull(Wrapped.of(d, m.minusMonths(1)).prevSpent)
    }

    @Test fun newsImpactUsesYourNumbers() {
        val d = FinanceData(
            today,
            listOf(Account(1, "Santander", AccountType.BANK, 700.0), Account(2, "Robinhood", AccountType.BANK_SAVINGS, 3000.0, interestRate = 5.0)),
            listOf(com.caudal.finanzas.data.CryptoHolding(1, "ethereum", "ETH", "Ethereum", 1.0, 2000.0, 2500.0)),
            emptyList(), listOf(Category(1, "Necesidades", 50.0)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        )
        val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }
        val eth = NewsImpactAnalyzer.analyze("Ethereum cae un 7 % tras la salida de fondos", "", d, fmt)
        assertTrue(eth.relevant)
        assertEquals("Puede restarte dinero", eth.headline)
        assertTrue(eth.lines.toString(), eth.lines.any { it.contains("perderías unos 175.00 €") && it.contains("6025.00 €") })
        val bce = NewsImpactAnalyzer.analyze("El BCE baja los tipos de interés", "", d, fmt)
        assertTrue(bce.lines.toString(), bce.lines.any { it.startsWith("Robinhood") && it.contains("12.50 €") && it.contains("0,25 puntos menos") })
        val other = NewsImpactAnalyzer.analyze("Apple presenta un nuevo iPhone", "", d, fmt)
        assertTrue(!other.relevant)
    }

    @Test fun newsWithoutAdsAndWithPhotos() {
        val n = { t: String, u: String -> com.caudal.finanzas.data.NewsItem(t, "X", u, 0, "", "Economía") }
        val svc = com.caudal.finanzas.data.NewsService
        assertTrue(svc.isAd(n("Las mejores ofertas del Black Friday en móviles", "https://elpais.com/a")))
        assertTrue(svc.isAd(n("Así ahorra una familia con su nuevo seguro", "https://elpais.com/escaparate/x")))
        assertTrue(svc.isAd(n("Bitcoin y el futuro de los pagos", "https://es.beincrypto.com/x"), " Patrocinado "))
        assertTrue(!svc.isAd(n("El Ibex 35 cae un 1 % lastrado por la banca", "https://expansion.com/mercados/x")))
    }
}
