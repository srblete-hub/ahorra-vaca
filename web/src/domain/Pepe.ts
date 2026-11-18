// Port de domain/Pepe.kt: "Pregúntale a Pepe" (prepara un resumen para abrirlo en la app de Claude; la app NO tiene IA).
import { fixed, groupBy, monthNameFull, sortedByDesc, sumOf, ymOfDate } from './kt'
import { isSaving } from './FinanceMath'
import { SPEND_TAG_INFO, spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { Budgeting } from './Budget'
import type { SpendingBudget } from './Budget'
import type { FinanceData } from './Insights'
import type { BankPlan } from './Planning'
import type { GoalStatus } from './Goals'

const ifBlank = (s: string, other: string) => (s.trim() === '' ? other : s)

/**
 * "Pregúntale a Pepe": Pepe es un pastor que ayuda con las cuentas. La app NO tiene IA: solo prepara un resumen
 * de tus números y tu pregunta, y lo abre en la app oficial de Claude, que contesta con tu suscripción (gratis).
 * Antes de enviar, ves y puedes editar todo el texto.
 */
export const Pepe = {
  QUESTIONS: ['¿Cuánto me queda hoy?', '¿Cómo voy este mes?', '¿En qué puedo recortar?', '¿Llego a fin de mes?', '¿Cuánto debería ahorrar?', '¿Cómo va mi cartera hoy?'],

  brief(
    question: string, userName: string, d: FinanceData, plan: BankPlan | null, budget: SpendingBudget | null, goal: GoalStatus | null,
    expected: string[], fmt: (v: number) => string,
  ): string {
    const m = ymOfDate(d.today)
    const monthName = monthNameFull(m.month)
    const name = ifBlank(userName, 'mí')
    const bank = d.accounts.filter((a) => a.type === 'BANK')
    const savings = d.accounts.filter((a) => isSaving(a) && a.balance > 0)
    const spending = d.spendingIn(m)
    const variable = spending.filter((e) => !Budgeting.isFixed(e, d.bills))
    const fixedV = sumOf(spending.filter((e) => Budgeting.isFixed(e, d.bills)), (e) => e.amount)
    const byTag = sortedByDesc([...groupBy(variable, spendTag).entries()].map(([t, l]): [SpendTag, number] => [t, sumOf(l, (e) => e.amount)]), (p) => p[1]).slice(0, 6)

    const lines: string[] = []
    lines.push(`- En el banco (mi dinero para el día a día): ${fmt(sumOf(bank, (a) => a.balance))}` +
      (bank.length > 0 ? ` (${bank.map((a) => `${a.name} ${fmt(a.balance)}`).join(', ')})` : ''))
    if (savings.length > 0) lines.push(`- Ahorro (no lo toco): ${savings.map((a) => `${a.name} ${fmt(a.balance)}` + (a.interestRate > 0 ? ` al ${fixed(a.interestRate, 1)} % TAE` : '')).join(', ')}`)
    if (d.wealth.invested > 0) lines.push(`- Inversiones (cripto y acciones): ${fmt(d.wealth.invested)}`)
    const holdings: Array<[[string, string, number], number]> = [
      ...d.crypto.map((c): [[string, string, number], number] => [[c.name, c.symbol, c.amount * c.price], c.amount * c.avgBuyPrice]),
      ...d.stocks.map((s): [[string, string, number], number] => [[ifBlank(s.name, s.ticker), s.ticker, s.shares * s.price], s.shares * s.avgBuyPrice]),
    ]
    if (holdings.length > 0) {
      lines.push('- Mi cartera: ' + sortedByDesc(holdings, (h) => h[0][2]).slice(0, 8).map(([h, cost]) =>
        `${h[0]} (${h[1]}) ${fmt(h[2])}` + (cost > 0 ? ` (${h[2] >= cost ? '+' : '−'}${fmt(Math.abs(h[2] - cost))} desde que compré)` : '')).join(', '))
    }
    lines.push(`- Este mes ha entrado: ${fmt(d.receivedIn(m))}` + (expected.length > 0 ? `. Por llegar: ${expected.join(', ')}` : ''))
    lines.push(`- Gastado en ${monthName} (día a día, sin alquiler ni fijos): ${fmt(sumOf(variable, (e) => e.amount))}` + (fixedV > 0 ? `. Alquiler y fijos: ${fmt(fixedV)}` : ''))
    if (byTag.length > 0) {
      lines.push('- Por categorías: ' + byTag.map(([t, v]) => {
        const lim = budget?.items.find((b) => b.tag === t)?.monthly
        return `${SPEND_TAG_INFO[t].label} ${fmt(v)}` + (lim != null && lim > 0 ? ` (límite ${fmt(lim)})` : '')
      }).join(', '))
    }
    if (plan) lines.push(`- Me tocan unos ${fmt(plan.perDay)} al día durante ${plan.days} días hasta el próximo cobro`)
    if (goal) lines.push(`- Objetivo de ahorro «${ifBlank(goal.goal.name, 'mi objetivo')}»: llevo ${fmt(goal.current)} de ${fmt(goal.goal.target)}, ahorro unos ${fmt(goal.pace)} al mes`)
    if (d.bills.length > 0) lines.push('- Gastos fijos: ' + d.bills.slice(0, 8).map((b) => `${b.name} ${fmt(b.amount)}`).join(', '))

    return `Eres Pepe, un pastor de toda la vida, sabio y cercano, que ayuda a ${name} a cuidar su dinero como cuida su rebaño. Contesta en español, en pocas frases claras y prácticas, con alguna expresión de pastor sin pasarte. Usa solo los datos de abajo y, si te falta algo, pregúntamelo. No me recomiendes comprar ni vender inversiones concretas.

Mis números (día ${d.today.day} de ${monthName}):
${lines.join('\n')}

Mi pregunta: ${question.trim()}`
  },
}
