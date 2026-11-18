// Ayudas internas para portar la lógica de Kotlin sin cambiar resultados:
// colecciones (sumOf, groupBy, maxBy…), fechas tipo java.time y formatos de String.format en español.
import type { LocalDate, YearMonth } from '../data/dates'
import {
  dayOfWeek, lengthOfMonth, MONTHS, MONTHS_SHORT, ofEpochDay, plusMonths, plusMonthsDate, startOfWeek, toEpochDay, WEEKDAYS,
} from '../data/dates'

// ---------- Colecciones ----------

export function sumOf<T>(list: readonly T[], f: (x: T) => number): number {
  let s = 0
  for (const x of list) s += f(x)
  return s
}
export function sum(list: readonly number[]): number { return sumOf(list, (x) => x) }
/** Como average() de Kotlin: NaN si la lista está vacía. */
export function average(list: readonly number[]): number { return list.length === 0 ? NaN : sum(list) / list.length }

/** El primero con el valor más alto (como maxByOrNull). */
export function maxBy<T>(list: readonly T[], f: (x: T) => number): T | undefined {
  let best: T | undefined
  let bv = -Infinity
  let first = true
  for (const x of list) {
    const v = f(x)
    if (first || v > bv) { best = x; bv = v; first = false }
  }
  return best
}
/** El primero con el valor más bajo (como minByOrNull). */
export function minBy<T>(list: readonly T[], f: (x: T) => number): T | undefined {
  let best: T | undefined
  let bv = Infinity
  let first = true
  for (const x of list) {
    const v = f(x)
    if (first || v < bv) { best = x; bv = v; first = false }
  }
  return best
}
export function maxOf<T>(list: readonly T[], f: (x: T) => number): number | undefined {
  return list.length === 0 ? undefined : Math.max(...list.map(f))
}
export function minOf<T>(list: readonly T[], f: (x: T) => number): number | undefined {
  return list.length === 0 ? undefined : Math.min(...list.map(f))
}

/** groupBy que mantiene el orden de aparición (como LinkedHashMap). */
export function groupBy<T, K>(list: readonly T[], key: (x: T) => K): Map<K, T[]> {
  const m = new Map<K, T[]>()
  for (const x of list) {
    const k = key(x)
    const l = m.get(k)
    if (l) l.push(x); else m.set(k, [x])
  }
  return m
}
export function distinctBy<T, K>(list: readonly T[], key: (x: T) => K): T[] {
  const seen = new Set<K>()
  const out: T[] = []
  for (const x of list) {
    const k = key(x)
    if (!seen.has(k)) { seen.add(k); out.push(x) }
  }
  return out
}
export function distinct<T>(list: readonly T[]): T[] { return distinctBy(list, (x) => x) }
/** Orden estable de mayor a menor (como sortedByDescending). */
export function sortedByDesc<T>(list: readonly T[], f: (x: T) => number): T[] {
  return [...list].sort((a, b) => f(b) - f(a))
}
export function sortedBy<T>(list: readonly T[], f: (x: T) => number): T[] {
  return [...list].sort((a, b) => f(a) - f(b))
}
export function takeLast<T>(list: readonly T[], n: number): T[] { return n <= 0 ? [] : list.slice(Math.max(0, list.length - n)) }
export function dropLast<T>(list: readonly T[], n: number): T[] { return list.slice(0, Math.max(0, list.length - n)) }
export function range(from: number, toInclusive: number): number[] {
  const out: number[] = []
  for (let i = from; i <= toInclusive; i++) out.push(i)
  return out
}
export function clamp(v: number, lo: number, hi: number): number { return Math.min(hi, Math.max(lo, v)) }
/** Int.toInt() / Double.toInt() de Kotlin: trunca hacia cero. */
export const toInt = (v: number): number => Math.trunc(v)
/** División entera de Kotlin (Int / Int). */
export const idiv = (a: number, b: number): number => Math.trunc(a / b)
/** kotlin.math.round(): al entero más cercano, empates al par (HALF_EVEN). */
export function roundEven(v: number): number {
  const f = Math.floor(v)
  const d = v - f
  if (d > 0.5) return f + 1
  if (d < 0.5) return f
  return f % 2 === 0 ? f : f + 1
}
/** Math.signum de Kotlin. */
export const sign = (v: number): number => (v > 0 ? 1 : v < 0 ? -1 : 0)

// ---------- Fechas (java.time) ----------

export const date = (epochDay: number): LocalDate => ofEpochDay(epochDay)
export const ep = (d: LocalDate): number => toEpochDay(d)
export const ymOfDay = (epochDay: number): YearMonth => { const d = ofEpochDay(epochDay); return { year: d.year, month: d.month } }
export const ymOfDate = (d: LocalDate): YearMonth => ({ year: d.year, month: d.month })
export const ymEq = (a: YearMonth, b: YearMonth): boolean => a.year === b.year && a.month === b.month
export const ymIdx = (m: YearMonth): number => m.year * 12 + m.month - 1
export const ymBefore = (a: YearMonth, b: YearMonth): boolean => ymIdx(a) < ymIdx(b)
export const ymAfter = (a: YearMonth, b: YearMonth): boolean => ymIdx(a) > ymIdx(b)
export const ymMinus = (m: YearMonth, n: number): YearMonth => plusMonths(m, -n)
export const ymLen = (m: YearMonth): number => lengthOfMonth(m.year, m.month)
export const atDay = (m: YearMonth, d: number): LocalDate => ({ year: m.year, month: m.month, day: d })
/** atDay(min(d, lengthOfMonth())). */
export const atDayClamped = (m: YearMonth, d: number): LocalDate => atDay(m, Math.min(d, ymLen(m)))
export const atEndOfMonth = (m: YearMonth): LocalDate => atDay(m, ymLen(m))
export const isBefore = (a: LocalDate, b: LocalDate): boolean => ep(a) < ep(b)
export const isAfter = (a: LocalDate, b: LocalDate): boolean => ep(a) > ep(b)
export const dateEq = (a: LocalDate, b: LocalDate): boolean => ep(a) === ep(b)
export const plusD = (d: LocalDate, n: number): LocalDate => ofEpochDay(ep(d) + n)
export const minusD = (d: LocalDate, n: number): LocalDate => ofEpochDay(ep(d) - n)
export const plusM = (d: LocalDate, n: number): LocalDate => plusMonthsDate(d, n)
export const plusY = (d: LocalDate, n: number): LocalDate => plusMonthsDate(d, 12 * n)
export const minusWeeks = (d: LocalDate, n: number): LocalDate => plusD(d, -7 * n)
export const withDayOfMonth = (d: LocalDate, day: number): LocalDate => ({ year: d.year, month: d.month, day })
export const lenOfMonth = (d: LocalDate): number => lengthOfMonth(d.year, d.month)
/** Lunes de la semana (TemporalAdjusters.previousOrSame(MONDAY)). */
export const monday = (d: LocalDate): LocalDate => startOfWeek(d)
/** 1 = lunes … 7 = domingo. */
export const dow = (d: LocalDate): number => dayOfWeek(d)
export const withDayOfYear1 = (d: LocalDate): LocalDate => ({ year: d.year, month: 1, day: 1 })
/** ChronoUnit.MONTHS.between(a, b): meses completos (con el día del mes en cuenta). */
export function monthsBetweenDates(a: LocalDate, b: LocalDate): number {
  const p1 = (a.year * 12 + a.month - 1) * 32 + a.day
  const p2 = (b.year * 12 + b.month - 1) * 32 + b.day
  return Math.trunc((p2 - p1) / 32)
}
export const daysBetween = (a: LocalDate, b: LocalDate): number => ep(b) - ep(a)

// ---------- Textos en español (como java.time con Locale es-ES) ----------

/** "noviembre" */
export const monthNameFull = (month: number): string => MONTHS[month - 1]
/** "nov." (abreviatura de CLDR/Android, con punto). */
export const monthNameShort = (month: number): string => MONTHS_SHORT[month - 1] + '.'
/** "lunes" */
export const weekdayName = (dayOfWeekValue: number): string => WEEKDAYS[dayOfWeekValue - 1]
/** Formato "d MMM": "5 nov." */
export const fmtDayMonthShort = (d: LocalDate): string => `${d.day} ${monthNameShort(d.month)}`
/** Formato "MMM yyyy": "nov. 2027" */
export const fmtMonthShortYear = (d: LocalDate): string => `${monthNameShort(d.month)} ${d.year}`
/** Formato "EEEE d 'de' MMMM": "viernes 20 de noviembre" */
export const fmtWeekdayDayMonth = (d: LocalDate): string => `${weekdayName(dayOfWeek(d))} ${d.day} de ${monthNameFull(d.month)}`
/** Formato "EEEE d": "viernes 20" */
export const fmtWeekdayDay = (d: LocalDate): string => `${weekdayName(dayOfWeek(d))} ${d.day}`
/** Formato "d 'de' MMMM yyyy": "3 de marzo 2027" (el patrón no lleva "de" antes del año). */
export const fmtDayDeMonthYear = (d: LocalDate): string => `${d.day} de ${monthNameFull(d.month)} ${d.year}`
/** replaceFirstChar { it.titlecase() } */
export const capFirst = (s: string): string => (s ? s[0].toLocaleUpperCase('es') + s.slice(1) : s)

// ---------- Números (String.format de Java) ----------

/** Redondeo HALF_UP sobre la representación decimal corta, como el Formatter de Java. */
function roundHalfUp(abs: number, digits: number): string {
  const f = Math.pow(10, digits)
  const scaled = Number((abs * f).toPrecision(15))
  const r = Math.floor(scaled + 0.5)
  return (r / f).toFixed(digits)
}
/** String.format("%.nf"): en español (coma decimal) por defecto. */
export function fixed(v: number, digits: number, decimal = ','): string {
  if (!Number.isFinite(v)) return Number.isNaN(v) ? 'NaN' : v > 0 ? 'Infinity' : '-Infinity'
  const neg = v < 0 || Object.is(v, -0)
  const s = roundHalfUp(Math.abs(v), digits)
  return (neg ? '-' : '') + (decimal === '.' ? s : s.replace('.', decimal))
}
/** String.format(Locale.ROOT, "%.nf"). */
export const fixedRoot = (v: number, digits: number): string => fixed(v, digits, '.')
/** String.format(es, "%+.1f"): "+4,2" / "-4,2". */
export function fixedSigned(v: number, digits: number): string {
  const s = fixed(v, digits)
  return s.startsWith('-') ? s : '+' + s
}
/** String.format(es, "%,.0f"): miles con punto. */
export function grouped0(v: number): string {
  const s = fixed(v, 0, '.')
  const neg = s.startsWith('-')
  const digits = neg ? s.slice(1) : s
  return (neg ? '-' : '') + digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}
/** Double.toString() de Kotlin para guardar números (30.0 → "30.0"). */
export function ktNum(v: number): string {
  if (Number.isInteger(v) && Math.abs(v) < 1e7) return v.toFixed(1)
  return String(v)
}
/** toDoubleOrNull() de Kotlin. */
export function toDoubleOrNull(s: string | undefined | null): number | null {
  if (s == null) return null
  const t = s.trim()
  if (t !== s || t === '' || !/^[+-]?(NaN|Infinity|(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)[fFdD]?$/.test(t)) return null
  const v = Number(t.replace(/[fFdD]$/, ''))
  return Number.isNaN(v) && !t.includes('NaN') ? null : v
}
/** toLongOrNull() / toIntOrNull() de Kotlin. */
export function toLongOrNull(s: string | undefined | null): number | null {
  if (s == null || !/^[+-]?\d+$/.test(s)) return null
  return Number(s)
}
