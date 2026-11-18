// Port de domain/Typical.kt: tu mes típico y revisión de suscripciones.
import type { YearMonth } from '../data/dates'
import { clamp, date, distinctBy, fixed, minBy, range, sumOf, ymBefore, ymEq, ymLen, ymMinus, ymOfDate, ymOfDay } from './kt'
import { Classifier } from './Spend'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'
import { RecurringDetector } from './Signals'
import type { Recurring } from './Signals'

/**
 * Este mes frente a tu mes típico: lo que llevas gastado día a día (acumulado) y la línea gris de lo que sueles
 * llevar a cada día del mes (media de tus meses anteriores). Sin alquiler ni fijos.
 * `today`, `spent`, `typicalToday`, `typicalTotal` y `diff` (calculadas en Kotlin) se rellenan al crearlo.
 */
export interface TypicalMonth {
  month: YearMonth
  /** Gastado acumulado de este mes, día 1..hoy. */
  now: number[]
  /** Lo típico acumulado, día 1..fin de mes. */
  typical: number[]
  monthsUsed: number
  today: number
  spent: number
  typicalToday: number
  typicalTotal: number
  diff: number
}

export function makeTypicalMonth(month: YearMonth, now: number[], typical: number[], monthsUsed: number): TypicalMonth {
  const today = now.length
  const spent = now[now.length - 1] ?? 0
  const typicalToday = typical[today - 1] ?? 0
  return { month, now, typical, monthsUsed, today, spent, typicalToday, typicalTotal: typical[typical.length - 1] ?? 0, diff: spent - typicalToday }
}

export const TypicalSpend = {
  of(d: FinanceData, maxMonths = 6): TypicalMonth | null {
    const m = ymOfDate(d.today)
    const variable = d.spending.filter((e) => !Budgeting.isFixed(e, d.bills))
    if (d.expenses.length === 0) return null
    const first = ymOfDay(Math.min(...d.expenses.map((e) => e.epochDay)))
    const past = range(1, maxMonths).map((i) => ymMinus(m, i)).filter((x) => !ymBefore(x, first))
    if (past.length === 0) return null
    const cumulative = (ym: YearMonth, days: number): number[] => {
      const byDay = new Map<number, number>()
      for (const e of variable.filter((x) => ymEq(ymOfDay(x.epochDay), ym))) {
        const dd = date(e.epochDay).day
        byDay.set(dd, (byDay.get(dd) ?? 0) + e.amount)
      }
      let acc = 0
      return range(1, days).map((day) => { acc += byDay.get(day) ?? 0; return acc })
    }
    const len = ymLen(m)
    // Cada mes pasado se lleva a la longitud de este (por proporción del día del mes).
    const curves = past.map((ym) => {
      const c = cumulative(ym, ymLen(ym))
      return range(1, len).map((day) => c[clamp(Math.trunc(day * ymLen(ym) / len) - 1, 0, c.length - 1)])
    })
    const typical = range(0, len - 1).map((i) => sumOf(curves, (c) => c[i]) / curves.length)
    return makeTypicalMonth(m, cumulative(m, d.today.day), typical, past.length)
  },
}

/** Una acción para una suscripción: revisar una subida, pasarla a pago anual o ver si sobra alguna parecida. */
export interface SubAction { key: string; name: string; text: string; saving: number }

/** Revisión de suscripciones: las que tienes, qué conviene revisar y cuánto podrías ahorrar. Sin IA. `maxSaving` se calcula al crearla. */
export interface SubscriptionCheck { subs: Recurring[]; actions: SubAction[]; monthlyTotal: number; maxSaving: number }

const VIDEO = ['netflix', 'hbo', 'max', 'disney', 'prime video', 'amazon prime', 'filmin', 'skyshowtime', 'movistar plus', 'dazn', 'apple tv', 'crunchyroll']
const MUSIC = ['spotify', 'apple music', 'youtube music', 'tidal', 'deezer', 'amazon music']

export const SubscriptionChecker = {
  of(d: FinanceData): SubscriptionCheck | null {
    const subs = RecurringDetector.find(d).filter((r) => r.kind === 'Suscripción')
    if (subs.length === 0) return null
    const e = (v: number) => `${fixed(v, 2)} €`
    const actions: SubAction[] = []
    for (const s of subs) {
      if (s.change > 0.4) actions.push({ key: `up-${s.name}`, name: s.name, text: `Revisa la subida de ${s.name} (+${e(s.change)} al mes)`, saving: s.change })
      if (s.monthly >= 8) actions.push({ key: `annual-${s.name}`, name: s.name, text: `Mira si ${s.name} tiene pago anual: suele salir un 15 % más barato`, saving: s.monthly * 0.15 })
    }
    const group = (words: string[], label: string) => {
      const g = subs.filter((s) => { const n = Classifier.normalize(s.name); return words.some((w) => n.includes(w)) })
      if (g.length >= 2) {
        const cheapest = minBy(g, (s) => s.monthly)!
        actions.push({ key: `dup-${label}`, name: cheapest.name, text: `Tienes ${g.length} de ${label} (${g.map((s) => s.name).join(', ')}): ¿usas todas?`, saving: cheapest.monthly })
      }
    }
    group(VIDEO, 'vídeo'); group(MUSIC, 'música')
    const unique = distinctBy(actions, (a) => a.key)
    return { subs, actions: unique, monthlyTotal: sumOf(subs, (s) => s.monthly), maxSaving: sumOf(unique, (a) => a.saving) }
  },

  /** Salud del ahorro (0–100): lo que te queda de cada euro que entra, sobre un 30 % como ideal. */
  savingsHealth(d: FinanceData): number {
    const m = ymMinus(ymOfDate(d.today), 1)
    const inc = d.receivedIn(m)
    if (inc <= 0) return 0
    const rate = (inc - d.spentIn(m)) / inc
    return Math.trunc(Math.min(Math.max(0, rate) / 0.30 * 100, 100))
  },
}
