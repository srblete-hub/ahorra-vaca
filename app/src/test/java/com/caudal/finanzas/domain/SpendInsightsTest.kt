package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.RecurringBill
import com.caudal.finanzas.data.Split
import com.caudal.finanzas.data.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpendInsightsTest {
    private val cats = listOf(
        Category(1, "Necesidades", 50.0), Category(2, "Ocio y caprichos", 30.0), Category(3, "Ahorro", 20.0, isSavings = true),
    )
    private val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }

    @Test fun classifiesMerchants() {
        assertEquals(SpendTag.SUPER, Classifier.classify("MERCADONA VALENCIA"))
        assertEquals(SpendTag.FOOD, Classifier.classify("Uber Eats"))
        assertEquals(SpendTag.TRANSPORT, Classifier.classify("Uber *Trip"))
        assertEquals(SpendTag.SUBSCRIPTIONS, Classifier.classify("Netflix.com"))
        assertEquals(SpendTag.TRANSPORT, Classifier.classify("Gasolinera Repsol"))
        assertEquals(SpendTag.FOOD, Classifier.classify("Cena con amigos"))
        assertEquals(SpendTag.OTHER, Classifier.classify("Bizum Pepe"))
        // Aprende de cómo lo clasificaste tú.
        val history = listOf(Expense(categoryId = 2, amount = 5.0, note = "Bizum Pepe", epochDay = 0, tag = SpendTag.LEISURE.name))
        assertEquals(SpendTag.LEISURE, Classifier.classify("bizum pepe", history))
        assertEquals(1L, Classifier.bucketFor(SpendTag.SUPER, cats))
        assertEquals(2L, Classifier.bucketFor(SpendTag.FOOD, cats))
    }

    @Test fun parsesSentences() {
        val list = NaturalEntry.parse("He pagado 18 € en una cena con amigos y 12,50 € en gasolina")
        assertEquals(2, list.size)
        assertEquals(18.0, list[0].amount, 0.001)
        assertEquals("Cena con amigos", list[0].note)
        assertEquals(SpendTag.FOOD, list[0].tag)
        assertEquals(12.5, list[1].amount, 0.001)
        assertEquals("Gasolina", list[1].note)
        assertEquals(SpendTag.TRANSPORT, list[1].tag)
        val y = NaturalEntry.parse("ayer 30 euros de cine")
        assertEquals(1, y.single().daysAgo)
        assertEquals(SpendTag.LEISURE, y.single().tag)
    }

    @Test fun bankPlanRentLateSalaryBufferThenSave() {
        // 2 de noviembre: 2.000 € en Santander, alquiler de 700 € el día 3 y 250 € del sueldo que llegan el 20.
        val today = LocalDate.of(2026, 11, 2)
        val santander = Account(1, "Santander", AccountType.BANK, 2000.0)
        val robinhood = Account(2, "Robinhood", AccountType.BANK_SAVINGS, 500.0, interestRate = 5.0)
        val rent = RecurringBill(1, "Alquiler", 700.0, day = 3, categoryId = 1, accountId = 1)
        val late = com.caudal.finanzas.data.IncomeSource(1, "Resto de nómina", false, 250.0, 20, 25, accountId = 1)
        // Gastas 20 € al día y quieres llegar a fin de mes con 75 € de sobra.
        val plan = BankPlanning.plan(today, santander, robinhood, 75.0, listOf(rent), emptyList(), emptyList(), listOf(late), emptyList(), 20.0, 0.0)
        assertEquals(29, plan.days)
        assertEquals(1550.0, plan.monthMoney, 0.001)
        assertEquals(580.0, plan.expectedSpend, 0.001)
        assertEquals(895.0, plan.saveGoal, 0.001)
        // Lo del sueldo que aún no ha llegado se mueve cuando llegue.
        assertEquals(645.0, plan.moveNow, 0.001)
        assertEquals(250.0, plan.moveLater, 0.001)
        assertEquals(580.0, plan.spendable, 0.001)
        // Tras mover 645 €, ya no pide mover más ahora y lo gastable no cambia.
        val after = BankPlanning.plan(
            today, santander.copy(balance = 1355.0), robinhood, 75.0, listOf(rent), emptyList(),
            listOf(Transfer(1, 1, 2, 645.0, epochDay = today.toEpochDay())), listOf(late), emptyList(), 20.0, 0.0,
        )
        assertEquals(0.0, after.moveNow, 0.001)
        assertEquals(250.0, after.moveLater, 0.001)
        assertEquals(580.0, after.spendable, 0.001)
        // Cuando llega el cobro, deja de contar como pendiente.
        val paid = BankPlanning.plan(
            today, santander.copy(balance = 1605.0), robinhood, 75.0, listOf(rent), emptyList(),
            listOf(Transfer(1, 1, 2, 645.0, epochDay = today.toEpochDay())), listOf(late),
            listOf(com.caudal.finanzas.data.Income(1, 1, 250.0, "Resto", today.toEpochDay(), 1)), 20.0, 0.0,
        )
        assertEquals(250.0, paid.moveNow, 0.001)
        assertEquals(robinhood, BankPlanning.defaultTarget(listOf(santander, robinhood)))
        // Con 700 € en el banco no se puede proponer ahorrar 670 €: se reserva lo necesario para vivir
        // (según tu perfil) y el ahorro no pasa de lo que te propones al mes.
        val tight = BankPlanning.plan(
            today, santander.copy(balance = 700.0), robinhood, 75.0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            dailyHabit = 2.0, spentToday = 0.0, targetSaving = 330.0, floorDaily = 20.0,
        )
        assertEquals(580.0, tight.expectedSpend, 0.001)
        assertEquals(45.0, tight.saveGoal, 0.001)
        assertTrue(tight.spendable >= tight.expectedSpend)
        val rich = BankPlanning.plan(
            today, santander.copy(balance = 5000.0), robinhood, 75.0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            dailyHabit = 20.0, spentToday = 0.0, targetSaving = 330.0, floorDaily = 20.0,
        )
        assertEquals(330.0, rich.saveGoal, 0.001)
        assertEquals(santander, BankPlanning.defaultMain(listOf(santander, robinhood), -1))
    }

    private fun data(today: LocalDate): FinanceData {
        val exp = mutableListOf<Expense>()
        var id = 1L
        // Tres meses: restaurantes van subiendo, supermercado estable.
        for (back in 1..3) {
            val m = today.withDayOfMonth(1).minusMonths(back.toLong())
            exp += Expense(id++, 1, 200.0, "Mercadona", m.plusDays(4).toEpochDay())
            exp += Expense(id++, 2, 40.0 * (4 - back), "Restaurante", m.plusDays(5).toEpochDay())
            exp += Expense(id++, 1, 650.0, "Alquiler", m.plusDays(2).toEpochDay(), billId = 1)
        }
        exp += Expense(id++, 1, 120.0, "Mercadona", today.minusDays(1).toEpochDay())
        exp += Expense(id++, 2, 90.0, "Cena con amigos", today.toEpochDay())
        val inc = (0..3).map { back -> Income(back + 1L, 1, 1800.0, "Nómina", today.withDayOfMonth(1).minusMonths(back.toLong()).toEpochDay(), null) }
        return FinanceData(
            today, listOf(Account(1, "Santander", AccountType.BANK, 1500.0), Account(2, "Robinhood", AccountType.BANK_SAVINGS, 4000.0, interestRate = 5.0)),
            emptyList(), emptyList(), cats, exp, inc, emptyList(),
            listOf(RecurringBill(1, "Alquiler", 650.0, 3, categoryId = 1), RecurringBill(2, "Netflix", 13.99, 15, categoryId = 2)),
            emptyList(), listOf(Split(exp.last().id, 3, 1)),
        )
    }

    @Test fun healthCostAndShare() {
        val d = data(LocalDate.of(2026, 11, 20))
        val h = Insights.health(d, fmt)
        assertEquals(5, h.indicators.size)
        assertTrue(h.nextStep.isNotBlank())
        val cost = Insights.costOfLiving(d)
        assertEquals(663.99, cost.fixed, 0.01)
        assertTrue(cost.variable > 0)
        // 90 € entre 3, uno ya ha pagado: te deben 30 €.
        assertEquals(30.0, Insights.owed(d.splits, d.expenses), 0.001)
        assertTrue(Insights.describe(Insights.monthStory(d), fmt).isNotBlank())
        assertTrue(Insights.timeTravel(d, 3).then.spent > 0)
    }

    @Test fun goalAndFinancialAge() {
        // 10.000 € en 5 años sin intereses: 166,67 € al mes.
        assertEquals(166.67, Goals.requiredMonthly(0.0, 10000.0, 0.0, 60), 0.01)
        // Con 2.000 € ya y un 5 %, hace falta menos.
        assertTrue(Goals.requiredMonthly(2000.0, 10000.0, 5.0, 60) < 120.0)
        assertEquals(60, Goals.monthsTo(0.0, 10000.0, 0.0, 10000.0 / 60))
        val d = data(LocalDate.of(2026, 11, 20))
        val g = Goals.status(Goal("Colchón", 10000.0, d.today, d.today.plusYears(5)), d, null, fmt)
        assertTrue(g.tips.isNotEmpty())
        assertEquals(4000.0, g.current, 0.001)
        val age = FinancialAging.evaluate(30, d, g)
        assertTrue(age.factors.size >= 6)
        assertTrue(age.pace in -1.0..3.0)
        assertEquals(30.0 + age.factors.sumOf { it.years }, age.age, 0.001)
    }

    @Test fun budgetWheelAndSavingsAdvice() {
        val d = data(LocalDate.of(2026, 11, 20))
        val limits = Budgeting.parseLimits(null)
        assertEquals(30.0, limits.getValue(SpendTag.TRANSPORT).amount, 0.001)
        val b = Budgeting.plan(d, saving = 300.0, limits = limits)
        // 1.800 − 663,99 de fijos − 300 de ahorro.
        assertEquals(836.01, b.base, 0.01)
        val gas = b.items.first { it.tag == SpendTag.TRANSPORT }
        assertEquals(130.0, gas.monthly, 0.01)
        assertEquals(b.base, b.items.sumOf { it.monthly }, 0.01)
        assertEquals(100.0, b.items.sumOf { it.pct }, 0.01)
        assertEquals(limits, Budgeting.parseLimits(Budgeting.formatLimits(limits)))
        val advice = SavingsAdvisor.advise(d, null, fmt)!!
        assertTrue(advice.recommended > 0 && advice.recommended <= advice.capacity + 5)
        val today = Advisor.today(d, null, b, null, fmt)
        assertTrue(today.headline.isNotBlank())
    }

    @Test fun leaksAnomaliesAndGoalPriorities() {
        val today = LocalDate.of(2026, 11, 20)
        val base = data(today)
        val extra = mutableListOf<Expense>()
        var id = 500L
        for (back in 1..3) {
            val m = today.withDayOfMonth(1).minusMonths(back.toLong())
            extra += Expense(id++, 1, 4.5, "Comisión mantenimiento", m.toEpochDay())
            extra += Expense(id++, 2, 10.99, "Spotify", m.plusDays(7).toEpochDay())
        }
        extra += Expense(id++, 2, 12.99, "Spotify", today.minusDays(2).toEpochDay())
        extra += Expense(id++, 2, 29.99, "Amazon", today.minusDays(1).toEpochDay())
        extra += Expense(id++, 2, 29.99, "Amazon", today.toEpochDay())
        val d = base.copy(expenses = base.expenses + extra)
        val leaks = InvisibleMoney.find(d, fmt)
        assertTrue(leaks.any { it.kind == LeakKind.FEE })
        // Spotify sale una sola vez: suscripción que además ha subido de precio.
        assertEquals(1, leaks.count { it.name == "Spotify" })
        assertTrue(leaks.first { it.name == "Spotify" }.detail.contains("Ha subido"))
        assertTrue(leaks.any { it.kind == LeakKind.SUBSCRIPTION && it.name == "Netflix" })
        assertTrue(Anomalies.find(d, fmt).any { it.title.contains("duplicado") })

        val goals = listOf(Goal("Colchón", 10000.0, today, today.plusYears(5)), Goal("Viaje", 2000.0, today, today.plusYears(1)))
        assertEquals(goals, GoalList.parse(GoalList.format(goals)))
        val plan = GoalList.plan(goals, d, null, fmt)
        // El ahorro actual (4.000 €) va primero al de más prioridad.
        assertEquals(4000.0, plan[0].current, 0.001)
        assertEquals(0.0, plan[1].current, 0.001)
        assertTrue(GoalList.swapImpact(goals, d, null, fmt)!!.contains("Viaje"))
        val options = PurchaseSimulator.compare(180.0, 120.0, d, null, plan, fmt)
        assertEquals(3, options.size)
        assertTrue((options[0].goalDelayDays ?: 0) > (options[2].goalDelayDays ?: 0))
    }

    @Test fun assistantAnswers() {
        val d = data(LocalDate.of(2026, 11, 20))
        val safe = SafeToSpend(available = 1500.0, pendingBills = 0.0, savingsReserve = 200.0, nextPayday = LocalDate.of(2026, 12, 1), daysToPayday = 11)
        val yes = Assistant.answer("¿Puedo gastarme 250 € este fin de semana?", d, safe, emptyList(), fmt)
        assertTrue(yes.text, yes.text.startsWith("Sí"))
        assertTrue(yes.sources.isNotEmpty())
        val no = Assistant.answer("¿me puedo permitir 9000 €?", d, safe, emptyList(), fmt)
        assertTrue(no.text, no.text.startsWith("No"))
        val save = Assistant.answer("¿Qué tres cambios me permitirían ahorrar 100 € más?", d, safe, emptyList(), fmt)
        assertTrue(save.text, save.text.contains("1."))
        val rec = Assistant.answer("He pagado 18 € en una cena y 12 € en gasolina", d, safe, emptyList(), fmt)
        assertEquals(2, rec.expenses.size)
    }
}
