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

    @Test fun rentIsMonthlyFixedAndStreakCountsDays() {
        val cats = listOf(Category(1, "Necesidades", 50.0))
        val m = YearMonth.from(today)
        val exp = listOf(
            Expense(1, 1, 700.0, "Alquiler noviembre", m.atDay(1).toEpochDay()),
            Expense(2, 1, 300.0, "Transferencia casero piso", m.atDay(2).toEpochDay(), tag = "HOME"),
            Expense(3, 1, 20.0, "Mercadona", today.minusDays(1).toEpochDay()),
            Expense(4, 1, 80.0, "Zara", today.minusDays(3).toEpochDay()),
            Expense(5, 1, 10.0, "Café", today.toEpochDay()),
        )
        val d = FinanceData(today, listOf(Account(1, "Santander", AccountType.BANK, 900.0)), emptyList(), emptyList(), cats, exp, emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(Budgeting.isFixed(exp[0], emptyList()))
        assertTrue(Budgeting.isFixed(exp[1], emptyList()))
        assertTrue(!Budgeting.isFixed(exp[2], emptyList()))
        // El resumen del mes (Wrapped) no cuenta el alquiler, pero lo dice aparte.
        val w = Wrapped.of(d, m)
        assertEquals(110.0, w.spent, 0.001)
        assertEquals(1000.0, w.fixed, 0.001)
        // Racha con 30 €/día: hoy (10) y ayer (20) bien, anteayer sin gastos, hace 3 días 80 € → se rompe.
        val s = Streaks.of(d, 30.0)
        assertEquals(3, s.current)
        assertEquals(DayMark.OK, s.today)
        assertEquals(7, s.nextGoal)
    }

    @Test fun merchantsAreCleanedAndDuplicatesFound() {
        assertEquals("Vinted", Merchants.clean("MPVinted*"))
        assertEquals("Steam", Merchants.clean("PAYPAL *STEAM GAMES"))
        assertEquals("Amazon", Merchants.clean("AMZN Mktp ES*2K4AB"))
        assertEquals("Bar Pepe", Merchants.clean("SUMUP *BAR PEPE"))
        assertEquals("Mercadona", Merchants.clean("MERCADONA 2345 MADRID"))
        assertEquals("Cafeteria Luna", Merchants.clean("CAFETERIA LUNA 23"))
        assertEquals(SpendTag.SHOPPING, Classifier.guess("MPVinted*"))
        assertEquals(SpendTag.TRANSPORT, Classifier.guess("TELPARK"))
        val t = 1_000_000L
        val a = Expense(1, 1, 12.5, "Bar", 100, createdAt = t)
        val b = Expense(2, 1, 12.5, "Bar", 100, createdAt = t + 30_000, fromCard = true)
        val c = Expense(3, 1, 12.5, "Bar", 100, createdAt = t + 5 * 60_000)
        assertEquals(listOf(b), Duplicates.extras(listOf(a, b, c)))
    }

    @Test fun statsCompareWithThePreviousPeriod() {
        val m = YearMonth.from(today)
        val exp = listOf(
            Expense(1, 1, 60.0, "Mercadona", m.atDay(3).toEpochDay()),
            Expense(2, 1, 40.0, "Starbucks", m.atDay(10).toEpochDay()),
            Expense(3, 1, 700.0, "Alquiler", m.atDay(1).toEpochDay()),
            Expense(4, 1, 80.0, "Mercadona", m.minusMonths(1).atDay(5).toEpochDay()),
        )
        val d = FinanceData(today, emptyList(), emptyList(), emptyList(), listOf(Category(1, "Necesidades", 50.0)), exp, emptyList(), emptyList(), emptyList(), emptyList())
        val s = Stats.of(d, 0)
        assertEquals(100.0, s.total, 0.001)
        assertEquals(80.0, s.prev, 0.001)
        assertEquals(25.0, s.changePct!!, 0.001)
        assertEquals(SpendTag.SUPER, s.byTag.first().first)
        assertEquals("https://www.google.com/s2/favicons?domain=starbucks.es&sz=128", Merchants.logoUrl("STARBUCKS 0123 MADRID"))
        assertNull(Merchants.logoUrl("Bar Pepe"))
    }

    @Test fun buyCheckCountsWorkHours() {
        val m = YearMonth.from(today)
        val d = FinanceData(
            today, listOf(Account(1, "Santander", AccountType.BANK, 900.0), Account(2, "Robinhood", AccountType.BANK_SAVINGS, 3000.0, interestRate = 5.0)),
            emptyList(), emptyList(), listOf(Category(1, "Necesidades", 50.0)),
            (1..4).map { Expense(it.toLong(), 1, 2.2, "Monster Energy", today.minusDays(it.toLong()).toEpochDay()) },
            listOf(Income(1, null, 1600.0, "Nómina", m.atDay(2).toEpochDay(), 1)), emptyList(), emptyList(), emptyList(),
        )
        val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }
        val c = BuyChecker.of("Camiseta Eme Studio", 25.0, d, null, null, null, fmt)
        assertEquals(SpendTag.SHOPPING, c.tag)
        // 1.600 € / 160 h = 10 €/h → 25 € son 2 h 30 min.
        assertEquals("2 h 30 min", c.facts.first { it.title == "Horas de trabajo" }.value)
        assertTrue(c.facts.any { it.value == "= 11 × Monster Energy" })
        assertEquals(25 * 1.05, c.invested.first().second, 0.001)
    }

    @Test fun cryptoMovesAlertOnBigChangesOnly() {
        val btc = com.caudal.finanzas.data.CryptoHolding(1, "bitcoin", "BTC", "Bitcoin", 0.5, 50000.0)
        val first = CryptoMoves.check(listOf(btc), mapOf("bitcoin" to 60000.0), emptyMap())
        assertTrue(first.first.isEmpty())                       // primera vez: solo guarda la referencia
        val small = CryptoMoves.check(listOf(btc), mapOf("bitcoin" to 61000.0), first.second)
        assertTrue(small.first.isEmpty())                       // +1,7 %: no avisa
        val big = CryptoMoves.check(listOf(btc), mapOf("bitcoin" to 54000.0), first.second)
        assertEquals(1, big.first.size)                         // −10 %: avisa
        assertEquals(-0.10, big.first[0].change, 0.0001)
        assertEquals(27000.0, big.first[0].value, 0.01)
        assertEquals(-3000.0, big.first[0].delta, 0.01)
        assertEquals(54000.0, big.second["bitcoin"]!!, 0.01)    // la referencia pasa al precio nuevo
    }

    @Test fun pepeBotAnswersInsideTheApp() {
        val m = YearMonth.from(today)
        val d = FinanceData(
            today, listOf(Account(1, "Santander", AccountType.BANK, 900.0), Account(2, "Robinhood", AccountType.BANK_SAVINGS, 3000.0, interestRate = 5.0)),
            emptyList(), emptyList(), listOf(Category(1, "Necesidades", 50.0)),
            listOf(Expense(1, 1, 60.0, "Mercadona", m.atDay(1).toEpochDay()), Expense(2, 1, 25.0, "Cines Yelmo", m.atDay(1).toEpochDay())),
            emptyList(), emptyList(), emptyList(), emptyList(),
        )
        val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }
        val ctx = PepeBot.Ctx(d, null, null, "Pablo", fmt)
        assertTrue(PepeBot.answer("hola", ctx).startsWith("¡Buenas, Pablo!"))
        assertTrue(PepeBot.answer("¿Cuánto tengo en el banco?", ctx).contains("900.00 €"))
        assertTrue(PepeBot.answer("¿cuánto gasto en ocio?", ctx).contains("25.00 €"))
        assertTrue(PepeBot.answer("¿Cómo va mi cartera?", ctx).startsWith("No tienes inversiones"))
        assertTrue(PepeBot.answer("¿qué tiempo hace?", ctx).startsWith("Esa no me la sé"))
    }

    @Test fun pepeBriefHasNumbersAndQuestion() {
        val m = YearMonth.from(today)
        val d = FinanceData(
            today, listOf(Account(1, "Santander", AccountType.BANK, 900.0), Account(2, "Robinhood", AccountType.BANK_SAVINGS, 3000.0, interestRate = 5.0)),
            emptyList(), emptyList(), listOf(Category(1, "Necesidades", 50.0)),
            listOf(Expense(1, 1, 60.0, "Mercadona", m.atDay(3).toEpochDay()), Expense(2, 1, 700.0, "Alquiler", m.atDay(1).toEpochDay())),
            emptyList(), emptyList(), emptyList(), emptyList(),
        )
        val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }
        val t = Pepe.brief("¿En qué puedo recortar?", "Pablo", d, null, null, null, listOf("YouTube ~290.00 € el día 22"), fmt)
        assertTrue(t.startsWith("Eres Pepe, un pastor"))
        assertTrue(t.contains("Santander 900.00 €"))
        assertTrue(t.contains("Robinhood 3000.00 € al 5,0 % TAE"))
        assertTrue(t.contains("Por llegar: YouTube ~290.00 € el día 22"))
        assertTrue(t.contains("sin alquiler ni fijos): 60.00 €. Alquiler y fijos: 700.00 €"))
        assertTrue(t.endsWith("Mi pregunta: ¿En qué puedo recortar?"))
    }

    @Test fun moreCategoriesAndBizum() {
        assertEquals(SpendTag.LEISURE, Classifier.guess("CINES YELMO PLAZA"))
        assertEquals(SpendTag.LEISURE, Classifier.guess("Cine con Juanpa"))
        assertEquals(SpendTag.SPORT, Classifier.guess("Futbol 7 jueves"))
        assertEquals(SpendTag.SPORT, Classifier.guess("PLAYTOMIC*CLUB PADEL"))
        assertEquals(SpendTag.SPORT, Classifier.guess("Pista futbol sala"))
        val inc = com.caudal.finanzas.payments.BizumParser.parse("Bizum recibido", "Has recibido un Bizum de 8,00 € de JUAN PABLO GARCIA. Concepto: pádel")!!
        assertTrue(inc.incoming)
        assertEquals(8.0, inc.amount, 0.001)
        assertEquals("Juan Pablo Garcia", inc.person)
        assertEquals("pádel", inc.concept)
        val out = com.caudal.finanzas.payments.BizumParser.parse("Bizum", "Has enviado 12,50 € a MARIA LOPEZ por Bizum")!!
        assertTrue(!out.incoming)
        assertEquals("Maria Lopez", out.person)
        assertNull(com.caudal.finanzas.payments.BizumParser.parse("Bizum", "MARIA te pide 10,00 € por Bizum"))
    }
}
