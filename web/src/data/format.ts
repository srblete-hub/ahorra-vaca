// Formato de números en español (1.234,56 €).

const cache = new Map<string, Intl.NumberFormat>()
function nf(key: string, make: () => Intl.NumberFormat) {
  let f = cache.get(key)
  if (!f) { f = make(); cache.set(key, f) }
  return f
}

export const SYMBOLS: Record<string, string> = { EUR: '€', USD: '$', GBP: '£', MXN: '$', CHF: 'CHF' }

/** 1.234,56 € (siempre con dos decimales). */
export function money(v: number, currency = 'EUR'): string {
  const f = nf('m' + currency, () => new Intl.NumberFormat('es-ES', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: 'always' as any }))
  return f.format(Number.isFinite(v) ? v : 0).replace(/ /g, ' ')
}
/** 1.235 € (sin decimales). */
export function money0(v: number, currency = 'EUR'): string {
  const f = nf('m0' + currency, () => new Intl.NumberFormat('es-ES', { style: 'currency', currency, maximumFractionDigits: 0, useGrouping: 'always' as any }))
  return f.format(Number.isFinite(v) ? v : 0).replace(/ /g, ' ')
}
/** +12,30 € / −12,30 € */
export function signedMoney(v: number, currency = 'EUR'): string {
  return (v > 0 ? '+' : v < 0 ? '−' : '') + money(Math.abs(v), currency)
}
/** Precio en dólares: $61.234,50 (o con más decimales si es pequeño). */
export function usd(v: number): string {
  const digits = v >= 1 ? 2 : v >= 0.01 ? 4 : 8
  return '$' + num(v, digits)
}
export function num(v: number, digits = 2): string {
  const f = nf('n' + digits, () => new Intl.NumberFormat('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: digits, useGrouping: 'always' as any }))
  return f.format(Number.isFinite(v) ? v : 0)
}
export function num2(v: number): string {
  const f = nf('n2f', () => new Intl.NumberFormat('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: 'always' as any }))
  return f.format(Number.isFinite(v) ? v : 0)
}
/** +1,3 % */
export function pct(v: number, digits = 1, signed = true): string {
  const f = nf('p' + digits, () => new Intl.NumberFormat('es-ES', { minimumFractionDigits: digits, maximumFractionDigits: digits, useGrouping: 'always' as any }))
  return (signed && v > 0 ? '+' : v < 0 ? '−' : '') + f.format(Math.abs(Number.isFinite(v) ? v : 0)) + ' %'
}
/** Lee "1.234,56", "1234.56" o "12,5" como número; null si no vale. */
export function parseAmount(s: string): number | null {
  let t = s.trim().replace(/[€$\s]/g, '')
  if (!t) return null
  if (t.includes(',')) t = t.replace(/\./g, '').replace(',', '.')
  const v = Number(t)
  return Number.isFinite(v) ? v : null
}
/** Texto para un campo editable (sin agrupar miles): 12,5 */
export function amountInput(v: number): string {
  if (!v) return ''
  return String(Math.round(v * 100) / 100).replace('.', ',')
}
/** Oculta importes cuando el usuario lo pide. */
export const HIDDEN = '••••'
