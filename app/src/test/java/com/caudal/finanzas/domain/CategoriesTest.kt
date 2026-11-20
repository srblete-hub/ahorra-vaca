package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CategoriesTest {
    @Test fun brandsAndGluedWords() {
        assertEquals(SpendTag.LEISURE, Classifier.guess("NEOCINE ALMERIA"))
        assertEquals(SpendTag.LEISURE, Classifier.guess("Cinesa Diagonal"))
        assertEquals(SpendTag.CLOTHES, Classifier.guess("SCUFFERS SL"))
        assertEquals(SpendTag.CLOTHES, Classifier.guess("Eme Studios"))
        assertEquals(SpendTag.CLOTHES, Classifier.guess("TWOJEYS"))
        assertEquals(SpendTag.CLOTHES, Classifier.guess("Zara"))
        assertEquals(SpendTag.SUPER, Classifier.guess("ALCAMPO SA"))
        assertEquals(SpendTag.TRANSPORT, Classifier.guess("Ballenoil Getafe"))
        assertEquals(SpendTag.FOOD, Classifier.guess("BurgerLab Malasaña"))
        assertEquals(SpendTag.SPORT, Classifier.guess("Basic-Fit"))
        // Lo que no se reconoce no se inventa.
        assertNull(Classifier.guess("Transferencia a Juan"))
    }

    private val today = LocalDate.of(2026, 11, 20)
    private val cats = listOf(Category(1, "Necesidades", 50.0), Category(2, "Ocio", 30.0))
    private fun data(e: List<Expense>) = FinanceData(today, listOf(Account(1, "Santander", AccountType.BANK, 900.0)), emptyList(), emptyList(), cats, e, emptyList(), emptyList(), emptyList(), emptyList())

    @Test fun oneRefuelIsNotASpike() {
        // Repostar cada 3-4 semanas: la semana que toca no es "transporte +100 %".
        var id = 1L
        val e = (0..5).map { k -> Expense(id++, 1, 30.0, "Repsol", today.minusDays(25L * k).toEpochDay()) } +
            (1..60).map { k -> Expense(id++, 2, 6.0, "Bar Pepe", today.minusDays(k.toLong()).toEpochDay()) }
        val r = WeeklyReader.of(data(e)) { "%.0f €".format(it) }
        assertTrue(r == null || r.insights.none { it.tag == SpendTag.TRANSPORT })
    }

    @Test fun revolutTopUpAsksWhereItWent() {
        val e = Expense(1, 1, 30.0, "Revolut", today.minusDays(1).toEpochDay())
        assertEquals(1, RevolutAsk.pending(data(listOf(e))).size)
        val done = e.copy(note = "Revolut · Ocio", tag = SpendTag.LEISURE.name)
        assertTrue(RevolutAsk.pending(data(listOf(done))).isEmpty())
    }
}
