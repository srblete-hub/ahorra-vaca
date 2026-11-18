// Port de domain/PepeSuggest.kt: "Pepe te recomienda" pasar una cantidad segura al ahorro (reglas fijas, sin IA).
import type { Transfer } from '../data/models'
import type { LocalDate } from '../data/dates'
import { isoWeekKey } from '../data/dates'
import { ep, maxBy, minusWeeks, monday, plusD, range, sumOf } from './kt'
import { isSaving } from './FinanceMath'
import type { FinanceData } from './Insights'
import type { CashPlan } from './CashPlan'
import type { GoalStatus } from './Goals'

/** Lo que Pepe propone esta semana: pasar una cantidad segura a tu ahorro sin bajar del colchón antes de cobrar. */
export interface PepeTip { amount: number; text: string; fromId: number; toId: number; toName: string }

/**
 * "Pepe te recomienda": reglas fijas, sin IA. Mira el punto más bajo de tu saldo previsto hasta la nómina
 * (gastando lo de cada día) y propone pasar lo que sobra por encima del colchón, con un margen, a tu ahorro.
 */
export const PepeSuggest = {
  /** Margen de seguridad por encima del colchón. */
  MARGIN: 25.0,

  /** Semana ISO: "2026-47". */
  weekKey(d: LocalDate): string { return isoWeekKey(d) },

  of(d: FinanceData, plan: CashPlan | null, goal: GoalStatus | null, fmt: (v: number) => string): PepeTip | null {
    if (!plan) return null
    if (plan.perDayBefore < 0) return null
    // Liquidez = solo la cuenta del banco (tipo BANK); el ahorro es el destino.
    const main = maxBy(d.accounts.filter((a) => a.type === 'BANK'), (a) => a.balance)
    if (!main) return null
    const target = maxBy(d.accounts.filter((a) => isSaving(a)), (a) => a.interestRate * 1_000_000 + a.balance)
    if (!target) return null
    const rest = plan.series.slice(1)
    if (rest.length === 0) return null
    const low = Math.min(...rest.map((p) => p[1]))
    let amount = Math.floor((low - plan.cushion - PepeSuggest.MARGIN) / 10) * 10
    if (goal != null) amount = Math.min(amount, Math.max(0, goal.goal.target - goal.current))
    if (amount < 10) return null
    const where = goal != null && goal.goal.name.trim() !== '' ? `«${goal.goal.name}»` : target.name
    return {
      amount,
      text: `Pasa ${fmt(amount)} a ${where} esta semana. Seguirás llegando a la nómina con ${fmt(plan.cushion)} de colchón.`,
      fromId: main.id, toId: target.id, toName: target.name,
    }
  },

  /** Lo que has ido metiendo en el ahorro las últimas `weeks` semanas (de la más antigua a la actual): [etiqueta, total]. */
  weekly(d: FinanceData, transfers: Transfer[], weeks = 5): Array<[string, number]> {
    const saving = new Set(d.accounts.filter((a) => isSaving(a)).map((a) => a.id))
    const mon = monday(d.today)
    return range(0, weeks - 1).reverse().map((w): [string, number] => {
      const from = minusWeeks(mon, w)
      const to = plusD(from, 6)
      const sum = sumOf(transfers.filter((t) => saving.has(t.toAccountId) && !saving.has(t.fromAccountId) && t.epochDay >= ep(from) && t.epochDay <= ep(to)), (t) => t.amount)
      return [w === 0 ? 'Esta' : `S${weeks - w}`, sum]
    })
  },
}
