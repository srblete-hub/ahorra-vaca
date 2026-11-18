import { describe, expect, it } from 'vitest'
import { PaymentParser } from '../PaymentParser'

const bank = (title: string | null, text: string | null) => PaymentParser.parse(title, text, false)
const wallet = (title: string | null, text: string | null) => PaymentParser.parse(title, text, true)

describe('PaymentParserTest', () => {
  it('googleWallet', () => {
    const p = wallet('Mercadona', '12,50 € con Visa ••1234')!
    expect(p.amount).toBeCloseTo(12.5, 3)
    expect(p.currency).toBe('EUR')
    expect(p.merchant).toBe('Mercadona')
  })

  it('bankPurchase', () => {
    const p = bank('BBVA', 'Compra en MERCADONA por 23,45 EUR con tu tarjeta ••1234')!
    expect(p.amount).toBeCloseTo(23.45, 3)
    expect(p.merchant).toBe('Mercadona')
  })

  it('thousandsAndDecimals', () => {
    expect(bank('Pago con tarjeta', 'Has pagado 1.234,56 € en Media Markt')!.amount).toBeCloseTo(1234.56, 3)
    expect(bank('Card payment', 'You paid €1,234.56 at Amazon')!.amount).toBeCloseTo(1234.56, 3)
    expect(PaymentParser.parseNumber('1.234')!).toBeCloseTo(1234.0, 3)
    expect(PaymentParser.parseNumber('9,9')!).toBeCloseTo(9.9, 3)
  })

  it('englishMerchant', () => {
    const p = bank('Revolut', 'Paid €4.20 at Starbucks')!
    expect(p.amount).toBeCloseTo(4.2, 3)
    expect(p.merchant).toBe('Starbucks')
  })

  it('ignoresIncomeRefundsAndDeclines', () => {
    expect(bank('BBVA', 'Has recibido un ingreso de 1.500,00 €')).toBeNull()
    expect(bank('CaixaBank', 'Devolución de compra en Zara por 39,95 €')).toBeNull()
    expect(bank('Santander', 'Pago denegado en Amazon por 20,00 €')).toBeNull()
    expect(bank('Bizum', 'Te han enviado 15 € por Bizum')).toBeNull()
  })

  it('ignoresVerificationsFutureChargesAndAds', () => {
    expect(bank('Santander', 'Introduce el código para confirmar la compra de 15,00 € en AMAZON')).toBeNull()
    expect(bank('Santander', 'Mañana se cargará tu recibo de 15,00 €')).toBeNull()
    expect(bank('Revolut', 'Consigue 15 € de descuento en tu próxima compra')).toBeNull()
    expect(bank('BBVA', 'Tienes un cargo pendiente de 15,00 €')).toBeNull()
    expect(bank('Santander', 'Tu saldo disponible tras la compra es de 715,00 €')).toBeNull()
    // Un pago de verdad sigue entrando, y guarda el texto para poder revisarlo.
    const ok = bank('Santander', 'Compra de 15,00 € en BAR MANOLO con tu tarjeta')!
    expect(ok.raw).toContain('BAR MANOLO')
    // La firma ignora los números: el mismo aviso con otro importe se reconoce.
    expect(PaymentParser.signature('Pago de 15,00 € en X')).toBe(PaymentParser.signature('Pago de 9,99 € en X'))
  })

  it('ignoresNonPaymentBankNotifications', () => {
    expect(bank('BBVA', 'Tu saldo disponible es 2.340,10 €')).toBeNull()
    expect(bank('BBVA', 'Compra en Mercadona')).toBeNull()
  })
})
