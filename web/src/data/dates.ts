// Fechas al estilo de java.time, para portar la lógica de Kotlin sin sorpresas.
// epochDay = días desde 1970-01-01 (fecha local, sin hora). Todo se calcula en UTC para no tener saltos de horario.

const DAY_MS = 86_400_000

export interface LocalDate { year: number; month: number /* 1–12 */; day: number }
export interface YearMonth { year: number; month: number /* 1–12 */ }

export function ofEpochDay(epochDay: number): LocalDate {
  const d = new Date(epochDay * DAY_MS)
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1, day: d.getUTCDate() }
}
export function toEpochDay(d: LocalDate): number {
  return Math.round(Date.UTC(d.year, d.month - 1, d.day) / DAY_MS)
}
export function ld(year: number, month: number, day: number): LocalDate {
  return { year, month, day }
}
/** Hoy, en la zona horaria del móvil. */
export function today(): LocalDate {
  const n = new Date()
  return { year: n.getFullYear(), month: n.getMonth() + 1, day: n.getDate() }
}
export function todayEpoch(): number { return toEpochDay(today()) }

export function plusDays(d: LocalDate, n: number): LocalDate { return ofEpochDay(toEpochDay(d) + n) }
export function lengthOfMonth(y: number, m: number): number { return new Date(Date.UTC(y, m, 0)).getUTCDate() }
export function plusMonthsDate(d: LocalDate, n: number): LocalDate {
  const ym = plusMonths({ year: d.year, month: d.month }, n)
  return { ...ym, day: Math.min(d.day, lengthOfMonth(ym.year, ym.month)) }
}
/** 1 = lunes … 7 = domingo (como DayOfWeek.value). */
export function dayOfWeek(d: LocalDate): number {
  const w = new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay()
  return w === 0 ? 7 : w
}
/** Lunes de la semana de d. */
export function startOfWeek(d: LocalDate): LocalDate { return plusDays(d, 1 - dayOfWeek(d)) }
export function dayOfYear(d: LocalDate): number { return toEpochDay(d) - toEpochDay({ year: d.year, month: 1, day: 1 }) + 1 }
export function isLeap(y: number) { return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0 }
/** Semana ISO ("2026-47"). */
export function isoWeekKey(d: LocalDate): string {
  const t = new Date(Date.UTC(d.year, d.month - 1, d.day))
  const dow = t.getUTCDay() || 7
  t.setUTCDate(t.getUTCDate() + 4 - dow)
  const y0 = new Date(Date.UTC(t.getUTCFullYear(), 0, 1))
  const w = Math.ceil(((t.getTime() - y0.getTime()) / DAY_MS + 1) / 7)
  return `${t.getUTCFullYear()}-${w}`
}

export function ym(year: number, month: number): YearMonth { return { year, month } }
export function ymOf(d: LocalDate): YearMonth { return { year: d.year, month: d.month } }
export function ymOfEpoch(e: number): YearMonth { return ymOf(ofEpochDay(e)) }
export function thisMonth(): YearMonth { return ymOf(today()) }
export function plusMonths(m: YearMonth, n: number): YearMonth {
  const idx = m.year * 12 + (m.month - 1) + n
  return { year: Math.floor(idx / 12), month: (idx % 12 + 12) % 12 + 1 }
}
/** Índice de mes: año·12 + mes − 1 (igual que monthIndex en Kotlin). */
export function monthIndex(m: YearMonth): number { return m.year * 12 + m.month - 1 }
export function fromMonthIndex(i: number): YearMonth { return { year: Math.floor(i / 12), month: (i % 12) + 1 } }
export function sameMonth(a: YearMonth, b: YearMonth) { return a.year === b.year && a.month === b.month }
export function compareYm(a: YearMonth, b: YearMonth) { return monthIndex(a) - monthIndex(b) }
export function firstDay(m: YearMonth): LocalDate { return { year: m.year, month: m.month, day: 1 } }
export function lastDay(m: YearMonth): LocalDate { return { year: m.year, month: m.month, day: lengthOfMonth(m.year, m.month) } }
export function monthDays(m: YearMonth) { return lengthOfMonth(m.year, m.month) }
export function inMonth(epochDay: number, m: YearMonth) { return sameMonth(ymOfEpoch(epochDay), m) }
/** "2026-11" */
export function ymKey(m: YearMonth) { return `${m.year}-${String(m.month).padStart(2, '0')}` }
export function compareDate(a: LocalDate, b: LocalDate) { return toEpochDay(a) - toEpochDay(b) }

export const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
export const MONTHS_SHORT = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic']
export const WEEKDAYS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo']
export const WEEKDAYS_SHORT = ['L', 'M', 'X', 'J', 'V', 'S', 'D']

export function capitalize(s: string) { return s ? s[0].toLocaleUpperCase('es') + s.slice(1) : s }
/** "noviembre 2026" */
export function monthLabel(m: YearMonth) { return `${MONTHS[m.month - 1]} ${m.year}` }
/** "Hoy", "Ayer", "lun 3 nov" */
export function dayLabel(epochDay: number): string {
  const t = todayEpoch()
  if (epochDay === t) return 'Hoy'
  if (epochDay === t - 1) return 'Ayer'
  const d = ofEpochDay(epochDay)
  const wd = WEEKDAYS[dayOfWeek(d) - 1].slice(0, 3)
  const y = d.year !== today().year ? ` ${d.year}` : ''
  return `${wd} ${d.day} ${MONTHS_SHORT[d.month - 1]}${y}`
}
/** "3 nov 2026" */
export function shortDate(d: LocalDate) { return `${d.day} ${MONTHS_SHORT[d.month - 1]} ${d.year}` }
/** Para <input type="date">: "2026-11-03" */
export function isoDate(d: LocalDate) { return `${d.year}-${String(d.month).padStart(2, '0')}-${String(d.day).padStart(2, '0')}` }
export function parseIsoDate(s: string): LocalDate | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s)
  return m ? { year: +m[1], month: +m[2], day: +m[3] } : null
}
