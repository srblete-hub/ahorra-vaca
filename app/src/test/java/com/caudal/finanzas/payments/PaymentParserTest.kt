package com.caudal.finanzas.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {

    private fun bank(title: String?, text: String?) = PaymentParser.parse(title, text, isWallet = false)
    private fun wallet(title: String?, text: String?) = PaymentParser.parse(title, text, isWallet = true)

    @Test fun googleWallet() {
        val p = wallet("Mercadona", "12,50 € con Visa ••1234")!!
        assertEquals(12.5, p.amount, 0.001)
        assertEquals("EUR", p.currency)
        assertEquals("Mercadona", p.merchant)
    }

    @Test fun bankPurchase() {
        val p = bank("BBVA", "Compra en MERCADONA por 23,45 EUR con tu tarjeta ••1234")!!
        assertEquals(23.45, p.amount, 0.001)
        assertEquals("Mercadona", p.merchant)
    }

    @Test fun thousandsAndDecimals() {
        assertEquals(1234.56, bank("Pago con tarjeta", "Has pagado 1.234,56 € en Media Markt")!!.amount, 0.001)
        assertEquals(1234.56, bank("Card payment", "You paid €1,234.56 at Amazon")!!.amount, 0.001)
        assertEquals(1234.0, PaymentParser.parseNumber("1.234")!!, 0.001)
        assertEquals(9.9, PaymentParser.parseNumber("9,9")!!, 0.001)
    }

    @Test fun englishMerchant() {
        val p = bank("Revolut", "Paid €4.20 at Starbucks")!!
        assertEquals(4.2, p.amount, 0.001)
        assertEquals("Starbucks", p.merchant)
    }

    @Test fun ignoresIncomeRefundsAndDeclines() {
        assertNull(bank("BBVA", "Has recibido un ingreso de 1.500,00 €"))
        assertNull(bank("CaixaBank", "Devolución de compra en Zara por 39,95 €"))
        assertNull(bank("Santander", "Pago denegado en Amazon por 20,00 €"))
        assertNull(bank("Bizum", "Te han enviado 15 € por Bizum"))
    }

    @Test fun ignoresVerificationsFutureChargesAndAds() {
        assertNull(bank("Santander", "Introduce el código para confirmar la compra de 15,00 € en AMAZON"))
        assertNull(bank("Santander", "Mañana se cargará tu recibo de 15,00 €"))
        assertNull(bank("Revolut", "Consigue 15 € de descuento en tu próxima compra"))
        assertNull(bank("BBVA", "Tienes un cargo pendiente de 15,00 €"))
        assertNull(bank("Santander", "Tu saldo disponible tras la compra es de 715,00 €"))
        // Un pago de verdad sigue entrando, y guarda el texto para poder revisarlo.
        val ok = bank("Santander", "Compra de 15,00 € en BAR MANOLO con tu tarjeta")!!
        assertTrue(ok.raw.contains("BAR MANOLO"))
        // La firma ignora los números: el mismo aviso con otro importe se reconoce.
        assertEquals(PaymentParser.signature("Pago de 15,00 € en X"), PaymentParser.signature("Pago de 9,99 € en X"))
    }

    @Test fun ignoresNonPaymentBankNotifications() {
        assertNull(bank("BBVA", "Tu saldo disponible es 2.340,10 €"))
        assertNull(bank("BBVA", "Compra en Mercadona"))
    }
}
