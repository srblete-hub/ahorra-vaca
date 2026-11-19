package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.RecurringBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SharedAndRemindersTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }

    // ---------- Compartir con la app ----------

    @Test fun ticketTakesTheTotalNotTheSubtotalNorTheCash() {
        val ticket = """
            MERCADONA S.A.
            C/ Mayor 12 - 28013 Madrid
            NIF A-46103834
            FACTURA SIMPLIFICADA 18/11/2026 19:42
            LECHE ENTERA 6 x 0,95     5,70
            PAN BARRA                 0,65
            TOMATE                    2,13
            SUBTOTAL                  7,80
            IVA 10%                   0,68
            TOTAL (€)                 8,48
            EFECTIVO ENTREGADO       10,00
            CAMBIO                    1,52
        """.trimIndent()
        val e = SharedReader.read(ticket, today, from = "ticket")!!
        assertEquals(8.48, e.amount!!, 0.001)
        assertEquals("Mercadona", e.note)
        assertEquals(LocalDate.of(2026, 11, 18), e.day)
        assertFalse(e.income)
    }

    @Test fun totalOnTheNextLine() {
        val e = SharedReader.read("BAR PEPE\nCAÑA 2,00\nTOTAL\n12,40\nGRACIAS", today, from = "ticket")!!
        assertEquals(12.40, e.amount!!, 0.001)
        assertEquals("Bar Pepe", e.note)
    }

    @Test fun bankTextAndTricount() {
        val bank = SharedReader.read("Compra con tarjeta de 23,45 € en ZARA ESPAÑA el 19/11", today)!!
        assertEquals(23.45, bank.amount!!, 0.001)
        assertTrue(bank.note.startsWith("Zara"))
        val tri = SharedReader.read("Cena del sábado\nPagado por Juanpa: 60,00 €\nTu parte: 30,00 €", today)!!
        assertEquals(30.0, tri.amount!!, 0.001)
        assertEquals("Cena del sábado", tri.note)
        val refund = SharedReader.read("Has recibido una devolución de 15,00 € de Amazon", today)!!
        assertTrue(refund.income)
    }

    @Test fun nothingToReadGivesNoAmount() {
        assertNull(SharedReader.read("   ", today))
        assertNull(SharedReader.read("Mira esta foto", today)!!.amount)
    }

    // ---------- Avisos de cobro ----------

    private fun data(bills: List<RecurringBill>, bank: Double = 500.0, paid: List<Expense> = emptyList()) = FinanceData(
        today, listOf(Account(1, "Santander", AccountType.BANK, bank)), emptyList(), emptyList(),
        listOf(Category(1, "Necesidades", 50.0), Category(2, "Ocio", 30.0)), paid, emptyList(), emptyList(), bills, emptyList(),
    )

    @Test fun nextChargeSkipsPaidMonths() {
        val netflix = RecurringBill(1, "Netflix", 13.99, day = 22, categoryId = 2)
        assertEquals(LocalDate.of(2026, 11, 22), BillReminders.next(data(listOf(netflix)), netflix))
        val paid = Expense(1, 2, 13.99, "Netflix", today.toEpochDay(), billId = 1)
        assertEquals(LocalDate.of(2026, 12, 22), BillReminders.next(data(listOf(netflix), paid = listOf(paid)), netflix))
        val early = RecurringBill(2, "Spotify", 11.99, day = 5, categoryId = 2)
        assertEquals(LocalDate.of(2026, 12, 5), BillReminders.next(data(listOf(early)), early))
    }

    @Test fun remindsOnceBeforeEachCharge() {
        val netflix = RecurringBill(1, "Netflix", 13.99, day = 22, categoryId = 2)
        val r = BillReminders.due(data(listOf(netflix)), 2, emptySet(), fmt)
        assertEquals(1, r.size)
        assertEquals("El domingo 22 te cobran Netflix", r[0].title)
        // Ya avisado: no se repite. Con 1 día de antelación aún no toca.
        assertTrue(BillReminders.due(data(listOf(netflix)), 2, setOf(r[0].key), fmt).isEmpty())
        assertTrue(BillReminders.due(data(listOf(netflix)), 1, emptySet(), fmt).isEmpty())
        // Sin aviso para esa suscripción.
        assertTrue(BillReminders.due(data(listOf(netflix.copy(remind = false))), 2, emptySet(), fmt).isEmpty())
    }

    @Test fun trialEndingAndLowBank() {
        val trial = RecurringBill(1, "Disney+", 9.99, day = 1, categoryId = 2, trialEnds = today.plusDays(1).toEpochDay())
        val t = BillReminders.due(data(listOf(trial)), 2, emptySet(), fmt)
        assertNotNull(t.firstOrNull { it.title == "Tu prueba de Disney+ se acaba mañana" })

        val rent = RecurringBill(2, "Alquiler", 650.0, day = 25, categoryId = 1)
        val low = BillReminders.due(data(listOf(rent), bank = 400.0), 2, emptySet(), fmt).single { it.title == "Te queda poco en el banco" }
        assertTrue(low.text.contains("Te faltan 250.00 €"))
        assertTrue(BillReminders.due(data(listOf(rent), bank = 900.0), 2, emptySet(), fmt).none { it.title == "Te queda poco en el banco" })
    }
}
