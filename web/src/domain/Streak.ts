// Port de domain/Streak.kt: racha de días gastando lo que te toca o menos.
import type { LocalDate } from '../data/dates'
import { ep, monday, plusD, range } from './kt'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'

/** Cómo fue un día para la racha. */
export type DayMark = 'NO_SPEND' | 'OK' | 'OVER' | 'FUTURE'
export const DAY_MARK_VALUES: DayMark[] = ['NO_SPEND', 'OK', 'OVER', 'FUTURE']

/**
 * Racha: días seguidos gastando lo que te toca al día o menos (sin contar alquiler ni fijos, que son de una
 * vez al mes). Un día sin gastar nada también suma. Hoy cuenta si de momento no te has pasado.
 */
export interface SpendStreak {
  current: number
  record: number
  /** De lunes a domingo de esta semana: [día, marca]. */
  week: Array<[LocalDate, DayMark]>
  today: DayMark
  spentToday: number
  allowance: number
  /** Días sin gastar nada en lo que va de mes. */
  noSpendThisMonth: number
  /** Próxima meta de racha (7, 14, 30…) o null si ya pasaste la última. */
  nextGoal: number | null
}

export const Streaks = {
  GOALS: [7, 14, 30, 60, 100, 180, 365],

  of(d: FinanceData, dailyAllowance: number): SpendStreak {
    const today = ep(d.today)
    const allowance = Math.max(dailyAllowance, 1.0)
    const spentBy = new Map<number, number>()
    for (const e of d.spending.filter((x) => !Budgeting.isFixed(x, d.bills))) spentBy.set(e.epochDay, (spentBy.get(e.epochDay) ?? 0) + e.amount)
    const minExp = d.expenses.length === 0 ? today : Math.min(...d.expenses.map((e) => e.epochDay))
    const first = Math.max(minExp, today - 400)
    const mark = (day: number): DayMark => {
      if (day > today) return 'FUTURE'
      const s = spentBy.get(day) ?? 0
      return s <= 0 ? 'NO_SPEND' : s <= allowance ? 'OK' : 'OVER'
    }
    // Racha actual: hacia atrás desde hoy mientras no te pases.
    let current = 0
    let day = today
    while (day >= first && mark(day) !== 'OVER') { current++; day-- }
    // Récord: la racha más larga desde que hay datos.
    let record = 0; let run = 0
    for (let x = first; x <= today; x++) { if (mark(x) === 'OVER') run = 0; else { run++; record = Math.max(record, run) } }
    const mon = monday(d.today)
    const week = range(0, 6).map((i): [LocalDate, DayMark] => { const dd = plusD(mon, i); return [dd, mark(ep(dd))] })
    const monthStart = today - d.today.day + 1
    let noSpend = 0
    for (let x = Math.max(monthStart, first); x <= today; x++) if (mark(x) === 'NO_SPEND') noSpend++
    return {
      current, record: Math.max(record, current), week, today: mark(today), spentToday: spentBy.get(today) ?? 0, allowance,
      noSpendThisMonth: noSpend, nextGoal: Streaks.GOALS.find((g) => g > current) ?? null,
    }
  },
}
