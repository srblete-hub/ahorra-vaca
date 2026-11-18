// Port de domain/Alerts.kt: avisos al acercarte o pasarte de los límites.
import type { Expense, RecurringBill } from '../data/models'
import type { LocalDate } from '../data/dates'
import { ep, groupBy, ktNum, monday, sumOf, toDoubleOrNull, ymEq, ymOfDate, ymOfDay } from './kt'
import { SPEND_TAG_INFO, spendTag, spendTagOf } from './Spend'
import type { SpendTag } from './Spend'
import { Budgeting } from './Budget'

/** Los límites calculados la última vez que se abrió la app, para poder avisar en segundo plano. */
export interface LimitSnapshot { perDay: number; weekOptimal: number; tagMonthly: Partial<Record<SpendTag, number>> }

export const LimitSnapshot = {
  encode(s: LimitSnapshot): string {
    return `${ktNum(s.perDay)}|${ktNum(s.weekOptimal)}|` + (Object.entries(s.tagMonthly) as Array<[SpendTag, number]>).map(([k, v]) => `${k}:${ktNum(v)}`).join(';')
  },
  decode(raw: string | null | undefined): LimitSnapshot | null {
    if (raw == null) return null
    const p = raw.split('|')
    if (p.length !== 3) return null
    const tags: Partial<Record<SpendTag, number>> = {}
    for (const e of p[2].split(';')) {
      const kv = e.split(':')
      if (kv.length !== 2) continue
      const t = spendTagOf(kv[0])
      const v = toDoubleOrNull(kv[1])
      if (t && v != null) tags[t] = v
    }
    const perDay = toDoubleOrNull(p[0])
    const weekOptimal = toDoubleOrNull(p[1])
    if (perDay == null || weekOptimal == null) return null
    return { perDay, weekOptimal, tagMonthly: tags }
  },
}

/** Un aviso: `key` evita repetirlo el mismo día; `severe` = ya te has pasado. */
export interface LimitAlert { key: string; title: string; text: string; severe: boolean }

/** Avisos al acercarte (80 %) o pasarte (100 %) de lo de hoy, lo óptimo de la semana o un tipo de gasto. */
export const LimitAlerts = {
  check(today: LocalDate, expenses: Expense[], bills: RecurringBill[], savingIds: Set<number>, snap: LimitSnapshot, fmt: (v: number) => string): LimitAlert[] {
    const variable = expenses.filter((e) => !savingIds.has(e.categoryId) && !Budgeting.isFixed(e, bills))
    const out: LimitAlert[] = []
    const t = ep(today)

    const todaySpent = sumOf(variable.filter((e) => e.epochDay === t), (e) => e.amount)
    if (snap.perDay > 0) {
      if (todaySpent >= snap.perDay) out.push({ key: 'day100', title: 'Has llegado a tu gasto de hoy', text: `Llevas ${fmt(todaySpent)} y hoy tocaban ${fmt(snap.perDay)}. Lo que gastes de más se descuenta de los próximos días.`, severe: true })
      else if (todaySpent >= snap.perDay * 0.8) out.push({ key: 'day80', title: 'Casi en tu límite de hoy', text: `Llevas ${fmt(todaySpent)} de ${fmt(snap.perDay)}: te quedan ${fmt(snap.perDay - todaySpent)} para hoy.`, severe: false })
    }

    const mon = ep(monday(today))
    const week = sumOf(variable.filter((e) => e.epochDay >= mon && e.epochDay <= t), (e) => e.amount)
    if (snap.weekOptimal > 0) {
      if (week >= snap.weekOptimal) out.push({ key: 'week100', title: 'Has superado lo óptimo de la semana', text: `Llevas ${fmt(week)} desde el lunes; lo óptimo eran ${fmt(snap.weekOptimal)}.`, severe: true })
      else if (week >= snap.weekOptimal * 0.85) out.push({ key: 'week85', title: 'Cerca del límite de la semana', text: `Llevas ${fmt(week)} de ${fmt(snap.weekOptimal)} y aún queda semana.`, severe: false })
    }

    const month = ymOfDate(today)
    const byTag = new Map<SpendTag, number>()
    groupBy(variable.filter((e) => ymEq(ymOfDay(e.epochDay), month)), spendTag).forEach((l, k) => byTag.set(k, sumOf(l, (e) => e.amount)))
    for (const [tag, limit] of Object.entries(snap.tagMonthly) as Array<[SpendTag, number]>) {
      const spent = byTag.get(tag) ?? 0
      if (limit <= 0) continue
      const label = SPEND_TAG_INFO[tag].label.toLowerCase()
      if (spent >= limit) out.push({ key: `tag100-${tag}`, title: `Te has pasado en ${label}`, text: `Llevas ${fmt(spent)} este mes y tu límite era ${fmt(limit)}.`, severe: true })
      else if (spent >= limit * 0.8) out.push({ key: `tag80-${tag}`, title: `Llevas el ${Math.trunc(spent / limit * 100)} % de ${label}`, text: `${fmt(spent)} de ${fmt(limit)} este mes: te quedan ${fmt(limit - spent)}.`, severe: false })
    }
    return out
  },
}
