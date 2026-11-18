// Port de payments/PaymentParser.kt: importe y comercio a partir del texto de un aviso de pago.
// En la web el usuario pega el texto de la notificación del banco o de la cartera.
import { toDoubleOrNull } from './kt'
import { Merchants } from './Merchants'

export interface DetectedPayment { amount: number; currency: string; merchant: string | null; raw: string }

/** Carteras: su notificación es "Comercio" + "12,50 € con Visa ••1234", sin la palabra "pago". */
const WALLET_APPS: Record<string, string> = {
  'com.google.android.apps.walletnfcrel': 'Google Wallet',
  'com.samsung.android.spay': 'Samsung Wallet',
  'com.samsung.android.samsungpay.gear': 'Samsung Wallet',
}

const BANK_APPS: Record<string, string> = {
  'com.bbva.bbvacontigo': 'BBVA',
  'es.lacaixa.mobile.android.newwapicon': 'CaixaBank',
  'com.imaginbank.app': 'imagin',
  'es.bancosantander.apps': 'Santander',
  'es.openbank.mobile': 'Openbank',
  'www.ingdirect.nativeframe': 'ING',
  'net.inverline.bancosabadell.officelocator.android': 'Sabadell',
  'com.kutxabank.android': 'Kutxabank',
  'es.unicajabanco.app': 'Unicaja',
  'com.abanca.bm.pt': 'ABANCA',
  'es.ibercaja.ibercajaapp': 'Ibercaja',
  'com.bankinter.launcher': 'Bankinter',
  'es.evobanco.bancamovil': 'EVO',
  'com.revolut.revolut': 'Revolut',
  'de.number26.android': 'N26',
  'com.bunq.android': 'bunq',
  'com.wise.android': 'Wise',
  'com.paypal.android.p2pmobile': 'PayPal',
}

const EXCLUDE = [
  'devoluci', 'reembols', 'ingreso', 'recibid', 'abono', 'te han enviado', 'te ha enviado',
  'rechazad', 'denegad', 'no se ha podido', 'fallid', 'cancelad', 'anulad',
  'refund', 'received', 'declined', 'failed', 'cancelled', 'canceled',
  // Verificaciones de compra online (aún no es un pago: llegará otro aviso si se completa).
  'código', 'codigo', 'confirma', 'autoriza', 'verifica', 'clave', 'otp', 'contraseña', 'seguridad',
  // Avisos de cargos futuros o pendientes.
  'se cargará', 'se cargara', 'se realizará', 'se realizara', 'programad', 'pendiente', 'próxim', 'proxim',
  'mañana', 'recordatorio', 'recuerda', 'vence', 'vencimiento', 'aviso de recibo', 'previsto',
  // Publicidad, saldos y límites.
  'descuento', 'consigue', 'gana ', 'oferta', 'promoci', 'cashback', 'regalo', 'sorteo', 'invita',
  'tu saldo es', 'tu saldo disponible', 'tu saldo actual', 'límite', 'limite', 'resumen', 'extracto',
  'verification', 'confirm', 'upcoming', 'scheduled', 'reminder', 'offer', 'balance',
]

const PAYMENT_WORDS = [
  'pago', 'pagado', 'compra', 'cargo', 'has gastado', 'gasto con', 'operación con tarjeta',
  'operacion con tarjeta', 'con tu tarjeta', 'con tarjeta',
  'payment', 'paid', 'purchase', 'spent', 'card transaction',
]

const CUR = '(€|EUR|\\$|USD|£|GBP)'
// Miles con punto, coma o espacio ("1.234,56", "1,234.56", "1 234"), o número simple ("23,45").
const NUM = '(\\d{1,3}(?:[.,\\s\\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)'
const AMOUNT_AFTER = new RegExp(`${NUM}\\s?${CUR}`, 'i')
const AMOUNT_BEFORE = new RegExp(`${CUR}\\s?${NUM}`, 'i')
const MERCHANT = /\b(?:en|at|in)\s+(.+?)(?=\s+(?:por|con|de|el|on|for|with|using)\b|[.,;:·]|\s+\d|$)/i

function currencyCode(raw: string): string {
  switch (raw.toUpperCase()) {
    case '€': case 'EUR': return 'EUR'
    case '$': case 'USD': return 'USD'
    case '£': case 'GBP': return 'GBP'
    default: return raw.toUpperCase()
  }
}

function trimChars(s: string, chars: string): string {
  let a = 0
  let b = s.length
  while (a < b && chars.includes(s[a])) a++
  while (b > a && chars.includes(s[b - 1])) b--
  return s.slice(a, b)
}

/** "MERCADONA S.A." → "Mercadona S.A."; corta comercios muy largos. */
function cleanMerchant(raw: string): string | null {
  const m = trimChars(Merchants.clean(raw), '"\'*').slice(0, 40).trim()
  if (m.length < 2) return null
  if (m === m.toUpperCase() && /\p{L}/u.test(m)) {
    return m.toLowerCase().split(' ').map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w)).join(' ')
  }
  return m
}

/**
 * Saca importe y comercio del texto de una notificación de pago (Google Wallet o la app del banco).
 */
export const PaymentParser = {
  WALLET_APPS,
  BANK_APPS,

  parse(title: string | null, text: string | null, isWallet: boolean): DetectedPayment | null {
    const full = [title, text].filter((x): x is string => x != null).join('\n').trim()
    if (full === '') return null
    const lower = full.toLowerCase()
    if (EXCLUDE.some((w) => lower.includes(w))) return null
    if (!isWallet && !PAYMENT_WORDS.some((w) => lower.includes(w))) return null

    let rawNumber: string
    let rawCurrency: string
    const after = AMOUNT_AFTER.exec(full)
    if (after) { rawNumber = after[1]; rawCurrency = after[2] } else {
      const before = AMOUNT_BEFORE.exec(full)
      if (!before) return null
      rawNumber = before[2]; rawCurrency = before[1]
    }
    const amount = PaymentParser.parseNumber(rawNumber)
    if (amount == null || amount <= 0) return null

    const fromText = text != null ? MERCHANT.exec(text)?.[1] ?? null : null
    const fromTitle = title != null ? MERCHANT.exec(title)?.[1] ?? null : null
    const walletTitle = title != null && isWallet && !AMOUNT_AFTER.test(title) && !AMOUNT_BEFORE.test(title) ? title : null
    const merchant = fromText ?? fromTitle ?? walletTitle
    return { amount, currency: currencyCode(rawCurrency), merchant: merchant != null ? cleanMerchant(merchant) : null, raw: full }
  },

  /**
   * Firma de una notificación sin números (importes, fechas, tarjetas): si marcas un aviso como
   * "no era un pago", los que tengan la misma firma se ignoran.
   */
  signature(raw: string): string {
    return raw.toLowerCase().replace(/[0-9]+/g, '#').replace(/[^a-záéíóúñü#]+/g, ' ').trim().slice(0, 120)
  },

  /** "1.234,56", "1,234.56", "12,5", "1 234" → número. */
  parseNumber(raw: string): number | null {
    const s = raw.replace(/[\s  ]/g, '')
    const lastComma = s.lastIndexOf(',')
    const lastDot = s.lastIndexOf('.')
    let decimalSep: string | null
    if (lastComma >= 0 && lastDot >= 0) decimalSep = lastComma > lastDot ? ',' : '.'
    else if (lastComma >= 0) { const n = s.length - lastComma - 1; decimalSep = n >= 1 && n <= 2 ? ',' : null }
    else if (lastDot >= 0) { const n = s.length - lastDot - 1; decimalSep = n >= 1 && n <= 2 ? '.' : null }
    else decimalSep = null
    const normalized = decimalSep == null ? s.replace(/,/g, '').replace(/\./g, '')
      : s.split(decimalSep === ',' ? '.' : ',').join('').split(decimalSep).join('.')
    return toDoubleOrNull(normalized)
  },
}
