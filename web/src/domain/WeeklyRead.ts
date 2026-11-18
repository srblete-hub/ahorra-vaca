// Port de domain/WeeklyRead.kt: "Lectura semanal" de en qué se te fue el dinero.
import type { LocalDate } from '../data/dates'
import { dow, ep, fixed, groupBy, maxBy, minusWeeks, monday, plusD, range, sumOf } from './kt'
import { Merchants } from './Merchants'
import { SPEND_TAG_INFO, SPEND_TAG_VALUES, spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'

/** Un hallazgo de la semana: título, explicación, comprobaciones y la categoría a la que afecta. */
export interface WeeklyInsight { tag: SpendTag; title: string; detail: string; checks: string[]; suggestedWeekly: number | null; up: boolean }

/** "Lectura semanal": en qué se te fue el dinero esta semana frente a tu semana normal (media de 8 semanas). Sin IA. */
export interface WeeklyRead { from: LocalDate; to: LocalDate; insights: WeeklyInsight[] }

export const WeeklyReader = {
  of(d: FinanceData, fmt: (v: number) => string): WeeklyRead | null {
    const today = d.today
    const mon = monday(today)
    const variable = d.spending.filter((e) => !Budgeting.isFixed(e, d.bills))
    const week = variable.filter((e) => e.epochDay >= ep(mon) && e.epochDay <= ep(today))
    if (week.length === 0) return null
    // Media de las 8 semanas anteriores, contando solo las que ya existían en la app.
    if (d.expenses.length === 0) return null
    const first = Math.min(...d.expenses.map((e) => e.epochDay))
    const weeks = range(1, 8).map((i) => minusWeeks(mon, i)).filter((w) => ep(plusD(w, 6)) >= first)
    if (weeks.length === 0) return null
    // La semana en curso aún no ha terminado: se compara con la misma parte de la semana normal.
    const partOfWeek = dow(today) / 7.0
    const avgOf = (tag: SpendTag) => sumOf(weeks, (w) => sumOf(variable.filter((e) => spendTag(e) === tag && e.epochDay >= ep(w) && e.epochDay <= ep(plusD(w, 6))), (e) => e.amount)) / weeks.length

    const insights: WeeklyInsight[] = []
    for (const tag of SPEND_TAG_VALUES) {
      const now = week.filter((e) => spendTag(e) === tag)
      const spent = sumOf(now, (e) => e.amount)
      const avgWeek = avgOf(tag)
      const expected = avgWeek * partOfWeek
      const diff = spent - expected
      if (Math.abs(diff) < 10 || (spent < 5 && expected < 5)) continue
      const pct = expected > 0 ? diff / expected * 100 : 100.0
      if (Math.abs(pct) < 25) continue
      const up = diff > 0
      const night = now.filter((e) => { const h = new Date(e.createdAt).getHours(); return h >= 22 || h < 5 }).length
      const top = maxBy([...groupBy(now, (e) => Merchants.clean(e.note)).entries()], ([, v]) => v.length)
      const checks: string[] = []
      if (up && night * 2 > now.length && now.length >= 3) checks.push('Sobre todo de noche')
      checks.push(`Comparado con tu media de ${weeks.length} ${weeks.length === 1 ? 'semana' : 'semanas'}`)
      const limit = up ? Math.ceil(avgWeek / 5) * 5 : null
      if (limit != null && limit > 0) checks.push(`Límite sugerido: ${fmt(limit)}/semana`)
      const label = SPEND_TAG_INFO[tag].label
      insights.push({
        tag,
        title: up ? `${label} sube un ${fixed(Math.min(pct, 999.0), 0)} % esta semana` : `${label} baja un ${fixed(Math.abs(pct), 0)} % esta semana`,
        detail: `${now.length} ${now.length === 1 ? 'pago' : 'pagos'}` +
          (top && top[1].length >= 2 ? `, sobre todo en ${top[0]}` : '') +
          (up ? `. Son ${fmt(diff)} más que en tu semana normal a estas alturas.` : `. Llevas ${fmt(-diff)} menos que en una semana normal.`),
        checks, suggestedWeekly: limit, up,
      })
    }
    // sortedByDescending { up }: primero las subidas, orden estable.
    const sorted = [...insights].sort((a, b) => Number(b.up) - Number(a.up))
    if (sorted.length === 0) return null
    return { from: mon, to: today, insights: sorted.slice(0, 5) }
  },
}
