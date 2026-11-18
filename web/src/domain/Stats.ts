// Port de domain/Stats.kt: estadísticas y analítica de gasto por periodos.
import type { Expense } from '../data/models'
import type { LocalDate, YearMonth } from '../data/dates'
import { MONTHS_SHORT } from '../data/dates'
import {
  atDay, atDayClamped, atEndOfMonth, dateEq, ep, groupBy, isAfter, minusWeeks, monday, monthNameFull, plusD, plusY, range, sortedByDesc,
  sumOf, withDayOfYear1, ymAfter, ymEq, ymMinus, ymOfDate, ymOfDay,
} from './kt'
import { spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'

/** Estadísticas de gasto de un periodo: total, el periodo anterior, por categorías, por meses y lo último. `changePct` se calcula al crearlas. */
export interface SpendStats {
  total: number
  /** Lo gastado en el mismo tramo del periodo anterior (0 si no hay datos). */
  prev: number
  byTag: Array<[SpendTag, number]>
  bars: Array<[YearMonth, number]>
  recent: Expense[]
  prevLabel: string
  /** Cambio frente al periodo anterior en %, solo si hay con qué comparar. */
  changePct: number | null
}

/** Una barra del gráfico: etiqueta, gasto y si es el periodo actual. */
export interface Bar { label: string; value: number; current: boolean }

/** Analítica de Movimientos: total gastado del periodo, el anterior y las barras (días, meses). `changePct` se calcula al crearla. */
export interface Analytics { total: number; prev: number; bars: Bar[]; title: string; prevLabel: string; changePct: number | null }

const changePct = (total: number, prev: number) => (prev > 0 ? (total - prev) / prev * 100 : null)
/** "Nov" (abreviatura sin punto y con mayúscula). */
const monthShort = (ym: YearMonth) => { const s = MONTHS_SHORT[ym.month - 1]; return s[0].toUpperCase() + s.slice(1) }
const minusYears = (d: LocalDate, n: number) => plusY(d, -n)

/** Periodos: 0 = este mes, 1 = últimos 3 meses, 2 = este año. Sin alquiler ni fijos (son de una vez al mes). */
export const Stats = {
  /** 0 = semana (lunes a domingo), 1 = mes (últimos 6 meses en barras), 2 = año (meses del año). Sin alquiler ni fijos. */
  analytics(d: FinanceData, kind: number): Analytics {
    const today = d.today
    const m = ymOfDate(today)
    const variable = d.spending.filter((e) => !Budgeting.isFixed(e, d.bills))
    const sum = (a: LocalDate, b: LocalDate) => sumOf(variable.filter((e) => e.epochDay >= ep(a) && e.epochDay <= ep(b)), (e) => e.amount)
    const minDay = d.expenses.length === 0 ? Number.MAX_SAFE_INTEGER : Math.min(...d.expenses.map((e) => e.epochDay))
    const has = (a: LocalDate) => minDay <= ep(a)
    if (kind === 0) {
      const mon = monday(today)
      const days = range(0, 6).map((i) => plusD(mon, i))
      const names = ['L', 'M', 'X', 'J', 'V', 'S', 'D']
      const total = sum(mon, today)
      const prev = has(minusWeeks(mon, 1)) ? sum(minusWeeks(mon, 1), minusWeeks(today, 1)) : 0
      return {
        total, prev,
        bars: days.map((day, i) => ({ label: names[i], value: isAfter(day, today) ? 0 : sum(day, day), current: dateEq(day, today) })),
        title: 'Esta semana', prevLabel: 'vs semana pasada', changePct: changePct(total, prev),
      }
    }
    if (kind === 1) {
      const prevM = ymMinus(m, 1)
      const total = sum(atDay(m, 1), today)
      const prev = has(atDay(prevM, 1)) ? sum(atDay(prevM, 1), atDayClamped(prevM, today.day)) : 0
      const title = monthNameFull(m.month)
      return {
        total, prev,
        bars: range(0, 5).reverse().map((i) => ymMinus(m, i)).map((ym) => ({ label: monthShort(ym), value: sum(atDay(ym, 1), atEndOfMonth(ym)), current: ymEq(ym, m) })),
        title: title[0].toUpperCase() + title.slice(1) + ` ${m.year}`, prevLabel: 'vs mes anterior', changePct: changePct(total, prev),
      }
    }
    const start = withDayOfYear1(today)
    const total = sum(start, today)
    const prev = has(minusYears(start, 1)) ? sum(minusYears(start, 1), minusYears(today, 1)) : 0
    return {
      total, prev,
      bars: range(1, 12).map((mm) => ({ year: m.year, month: mm })).map((ym) => ({ label: monthShort(ym).slice(0, 1), value: ymAfter(ym, m) ? 0 : sum(atDay(ym, 1), atEndOfMonth(ym)), current: ymEq(ym, m) })),
      title: `Año ${m.year}`, prevLabel: 'vs año pasado', changePct: changePct(total, prev),
    }
  },

  of(d: FinanceData, period: number): SpendStats {
    const today = d.today
    const m = ymOfDate(today)
    let from: LocalDate, prevFrom: LocalDate, prevTo: LocalDate
    if (period === 0) [from, prevFrom, prevTo] = [atDay(m, 1), atDay(ymMinus(m, 1), 1), atDayClamped(ymMinus(m, 1), today.day)]
    else if (period === 1) [from, prevFrom, prevTo] = [atDay(ymMinus(m, 2), 1), atDay(ymMinus(m, 5), 1), atDayClamped(ymMinus(m, 3), today.day)]
    else [from, prevFrom, prevTo] = [withDayOfYear1(today), withDayOfYear1(minusYears(today, 1)), minusYears(today, 1)]
    const variable = d.spending.filter((e) => !Budgeting.isFixed(e, d.bills))
    const inRange = (e: Expense, a: LocalDate, b: LocalDate) => e.epochDay >= ep(a) && e.epochDay <= ep(b)
    const now = variable.filter((e) => inRange(e, from, today))
    const before = variable.filter((e) => inRange(e, prevFrom, prevTo))
    const hasPrev = d.expenses.some((e) => e.epochDay <= ep(prevTo) && e.epochDay >= ep(prevFrom))
    const months = period === 2 ? range(1, m.month).map((mm) => ({ year: m.year, month: mm })) : range(0, 5).reverse().map((i) => ymMinus(m, i))
    const bars = months.map((ym): [YearMonth, number] => [ym, sumOf(variable.filter((e) => ymEq(ymOfDay(e.epochDay), ym)), (e) => e.amount)])
    const total = sumOf(now, (e) => e.amount)
    const prev = hasPrev ? sumOf(before, (e) => e.amount) : 0
    return {
      total, prev,
      byTag: sortedByDesc([...groupBy(now, spendTag).entries()].map(([t, l]): [SpendTag, number] => [t, sumOf(l, (e) => e.amount)]), (p) => p[1]),
      bars,
      recent: [...now].sort((a, b) => b.epochDay - a.epochDay || b.createdAt - a.createdAt).slice(0, 8),
      prevLabel: period === 0 ? 'vs mes anterior' : period === 1 ? 'vs 3 meses anteriores' : 'vs año pasado',
      changePct: changePct(total, prev),
    }
  },
}
