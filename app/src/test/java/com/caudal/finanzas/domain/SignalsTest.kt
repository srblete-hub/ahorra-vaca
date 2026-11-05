package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import com.caudal.finanzas.data.RecurringBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SignalsTest {
    private val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }
    private val cats = listOf(Category(1, "Necesidades", 50.0), Category(2, "Ocio y caprichos", 30.0), Category(3, "Ahorro", 20.0, isSavings = true))
    private val today = LocalDate.of(2026, 11, 20) // viernes

    private fun data(extra: List<Expense> = emptyList(), bills: List<RecurringBill> = emptyList()): FinanceData {
        var id = 1L
        val exp = mutableListOf<Expense>()
        for (back in 1..4) {
            val m = today.withDayOfMonth(1).minusMonths(back.toLong())
            exp += Expense(id++, 2, 10.99, "Spotify", m.plusDays(7).toEpochDay())
            exp += Expense(id++, 1, 4.5, "Comisión mantenimiento", m.toEpochDay())
        }
        exp += Expense(id++, 2, 12.99, "Spotify", today.minusDays(12).toEpochDay())
        exp += Expense(id++, 1, 4.5, "Comisión mantenimiento", today.withDayOfMonth(1).toEpochDay())
        val sources = listOf(IncomeSource(1, "Nómina", false, 1800.0, 28, 28, accountId = 1))
        val incomes = (1..4).map { Income(it.toLong(), 1, 1800.0, "Nómina", today.withDayOfMonth(28).minusMonths(it.toLong()).toEpochDay(), null) }
        return FinanceData(
            today, listOf(Account(1, "Santander", AccountType.BANK, 900.0), Account(2, "Robinhood", AccountType.BANK_SAVINGS, 3000.0, interestRate = 5.0)),
            emptyList(), emptyList(), cats, exp + extra.map { it.copy(id = id++) }, incomes, sources, bills, emptyList(),
        )
    }

    @Test fun recurringAndRecoverable() {
        val d = data()
        val rec = RecurringDetector.find(d)
        val spotify = rec.first { it.name == "Spotify" }
        assertEquals("Suscripción", spotify.kind)
        assertEquals(2.0, spotify.change, 0.01)
        assertTrue(rec.any { it.kind == "Comisión" })
        val refund = d.expenses.first { it.note == "Spotify" }
        val money = RecoverableMoney.find(d, setOf(refund.id), fmt)
        assertTrue(money.any { it.title.contains("Spotify ha subido") && it.nextStep.isNotBlank() })
        assertTrue(money.any { it.title.startsWith("Comisiones") })
        assertTrue(money.any { it.title.startsWith("Reembolso pendiente") })
        // Nunca afirma que algo esté olvidado o mal cobrado.
        assertTrue(money.none { it.title.contains("olvid") || it.evidence.contains("incorrect") })
    }

    @Test fun weeklyNudgeNamesTheMerchant() {
        val monday = today.minusDays(4)
        val d = data(extra = (0..2).map { Expense(0, 2, 2.2, "Monster Energy", monday.plusDays(it.toLong()).toEpochDay()) })
        val text = WeeklyNudge.find(d, "Pablo", fmt)!!
        assertTrue(text, text.startsWith("Pablo, esta semana has gastado 6.60 € en Monster Energy (3 veces)"))
    }

    @Test fun billCrunchBeforePaydaySuggestsMovingBills() {
        // Cobras el 28 y el alquiler, el seguro y la luz caen antes.
        val bills = listOf(
            RecurringBill(1, "Alquiler", 700.0, 22, categoryId = 1, accountId = 1),
            RecurringBill(2, "Seguro", 180.0, 24, categoryId = 1, accountId = 1),
            RecurringBill(3, "Luz", 60.0, 25, categoryId = 1, accountId = 1),
        )
        val c = PaymentOrganizer.analyze(data(bills = bills), null, fmt)!!
        assertTrue(c.crunch)
        assertTrue(c.suggestions.first().contains("Alquiler"))
        assertEquals(LocalDate.of(2026, 11, 28), c.payday)
    }

    @Test fun paycheckSplitAndRealPrice() {
        val d = data(bills = listOf(RecurringBill(1, "Alquiler", 700.0, 3, categoryId = 1, accountId = 1), RecurringBill(2, "Seguro coche", 420.0, 10, yearly = true, month = 12, categoryId = 1)))
        val s = PaycheckDistributor.split(d, null, fmt)
        assertNotNull(s)
        assertEquals(1800.0, s!!.income, 0.001)
        // El seguro anual de diciembre cae entre esta nómina y la siguiente: aparece y se avisa.
        assertTrue(s.notes.any { it.contains("Seguro coche") })
        assertEquals(s.income, s.parts.sumOf { it.second }, 0.01)
        val r = RealPriceCalculator.of(800.0, d, null, fmt)
        assertTrue(r.incomeDays > 10)
        assertEquals(3, r.months.size)
    }

    @Test fun wealthChangeIsExplainedAndProfilesSum() {
        val base = data()
        val d = base.copy(crypto = listOf(com.caudal.finanzas.data.CryptoHolding(1, "ethereum", "ETH", "Ethereum", 1.0, 3000.0, 2500.0)))
        // Baja 150 € sin gastos ni cobros en el periodo: lo explica la bajada de ETH.
        val why = WealthExplainer.explain(d, today.toEpochDay(), -150.0, mapOf("ETH" to -5.0), fmt)!!
        assertTrue(why, why.startsWith("Debido a la bajada de ETH"))
        (0..3).forEach { st ->
            val w = SavingProfile.weights(st, setOf(LifeGoal.TRAVEL.name))!!
            assertEquals(100.0, w.values.sum(), 0.001)
            assertTrue(w.getValue(SpendTag.TRAVEL) > 0)
        }
        // Más ahorrador = más peso al súper y menos a restaurantes.
        assertTrue(SavingProfile.weights(3, emptySet())!!.getValue(SpendTag.FOOD) < SavingProfile.weights(0, emptySet())!!.getValue(SpendTag.FOOD))
        assertTrue(SavingProfile.spendFactor(3) < SavingProfile.spendFactor(0))
    }

    @Test fun circlesAreWeightedAndMaturityRewardsGoodHabits() {
        val d = data()
        val circles = FinancialCircles.of(d, null, null, emptyList(), margin = 80.0, fmt = fmt)
        assertEquals(listOf("stability", "control", "progress"), circles.map { it.key })
        circles.forEach { c ->
            assertEquals(1.0, c.factors.sumOf { it.weight }, 0.001)
            assertTrue(c.score in 0.0..100.0)
            assertTrue(c.nextStep.isNotBlank())
        }
        // Sin objetivos, el progreso es provisional (no se inventa).
        assertTrue(circles.last().provisional)
        assertTrue(circles.last().factors.first { it.name == "Camino a tus objetivos" }.score == null)
        // Con 4 meses de historial la confianza es 4/6: ningún círculo puede llegar a 100.
        circles.forEach { assertTrue(it.score < 100.0 && it.confidence < 1.0) }
        // Curvas: el colchón tiene rendimientos decrecientes y nunca llega a 100.
        assertEquals(69.9, FinancialCircles.saturate(3.0, 2.5), 0.1)
        assertEquals(90.9, FinancialCircles.saturate(6.0, 2.5), 0.1)
        assertTrue(FinancialCircles.saturate(24.0, 2.5) < 100.0)
        assertEquals(50.0, FinancialCircles.logistic(1.15, 1.15, 4.0), 0.001)
        assertEquals("Ha subido 6 puntos desde la semana pasada.", FinancialCircles.changeText(70.0, 64.0))
        // Edad financiera: ahorrar mucho te hace "mayor" (más madura).
        val age = FinancialAging.evaluate(30, d, null)
        assertTrue(age.factors.first { it.name == "Capacidad de ahorro" }.years > 0)
        assertTrue(age.pace > 1.0)
    }

    @Test fun buzzCountsMentionsAndTone() {
        val posts = listOf(
            com.caudal.finanzas.data.RedditPost("Solana breakout: new ATH incoming, very bullish", 900, 120, "u1"),
            com.caudal.finanzas.data.RedditPost("SOL adoption keeps growing after the ETF approval", 400, 80, "u2"),
            com.caudal.finanzas.data.RedditPost("Dogecoin crash: another dump, bearish week", 300, 60, "u3"),
            com.caudal.finanzas.data.RedditPost("What wallet do you use?", 50, 10, "u4"),
        )
        val trending = listOf(com.caudal.finanzas.data.TrendingCoin("solana", "SOL", "Solana", 5, 6.2))
        val buzz = BuzzAnalyzer.analyze(posts, trending)
        val sol = buzz.items.first { it.symbol == "SOL" }
        assertEquals(2, sol.mentions)
        assertTrue(sol.positivity > 0.6)
        assertTrue(buzz.items.first { it.symbol == "DOGE" }.positivity < 0.4)
        assertEquals("SOL", buzz.featured?.symbol)
    }

    @Test fun persistentTrendIsDetected() {
        val monday = today.minusDays(4)
        // 8 semanas a 20 € de gasolina y las 4 últimas a 45 €.
        val gas = (1..12).flatMap { w ->
            val start = monday.minusWeeks(w.toLong())
            listOf(Expense(0, 1, if (w <= 4) 45.0 else 20.0, "Repsol", start.plusDays(2).toEpochDay()))
        }
        val trends = TrendDetector.find(data(extra = gas), fmt)
        assertTrue(trends.toString(), trends.any { it.title.startsWith("Transporte") && it.explanation.contains("Repsol") })
    }
}
