// Port de domain/BuyCheck.kt: "¿Lo compro o no?" con reglas fijas y tus datos reales (sin IA).
import { capFirst, distinct, ep, fixed, groupBy, maxBy, ymOfDate } from './kt'
import { Merchants } from './Merchants'
import { Classifier, SPEND_TAG_INFO } from './Spend'
import type { SpendTag } from './Spend'
import type { FinanceData } from './Insights'
import type { BankPlan } from './Planning'
import type { SpendingBudget } from './Budget'
import type { GoalStatus } from './Goals'

/** Una línea del análisis: título corto, el dato y una explicación. */
export interface BuyFact { title: string; value: string; detail: string }

/** El resultado de "¿Lo compro?": veredicto (0 sí, 1 piénsalo, 2 mejor no) y los datos que lo explican. */
export interface BuyCheck {
  name: string
  price: number
  tag: SpendTag
  verdict: number
  verdictText: string
  facts: BuyFact[]
  /** Lo que valdría ese dinero invertido a tu mejor TAE en 1, 5 y 10 años: [años, valor]. */
  invested: Array<[number, number]>
  rate: number
}

/**
 * "¿Lo compro o no?": con reglas fijas y tus datos reales (sin IA). Mira cuántas horas de trabajo cuesta según
 * lo que te ha entrado este mes, cómo deja tu mes y tu banco, tu límite en ese tipo de gasto, cuánto retrasa tu
 * objetivo de ahorro y lo que valdría ese dinero si lo invirtieras.
 */
export const BuyChecker = {
  /** Horas de trabajo al mes que se suponen (jornada completa). */
  HOURS_PER_MONTH: 160.0,

  of(
    name: string, price: number, d: FinanceData, plan: BankPlan | null, budget: SpendingBudget | null, goal: GoalStatus | null, fmt: (v: number) => string,
  ): BuyCheck {
    const tag: SpendTag = Classifier.guess(name) ?? 'SHOPPING'
    const tagLower = SPEND_TAG_INFO[tag].label.toLowerCase()
    const facts: BuyFact[] = []
    const m = ymOfDate(d.today)

    // 1. Horas de trabajo, con lo que ha entrado este mes (o lo previsto si aún no has cobrado).
    const received = d.receivedIn(m)
    const income = received > 0 ? received : d.monthlyIncome
    if (income > 0) {
      const perHour = income / BuyChecker.HOURS_PER_MONTH
      const hours = price / perHour
      const h = Math.trunc(hours); const min = Math.round((hours - h) * 60)
      facts.push({
        title: 'Horas de trabajo',
        value: h === 0 ? `${min} min` : min === 0 ? `${h} h` : `${h} h ${min} min`,
        detail: `Este mes te han entrado ${fmt(income)}: unos ${fmt(perHour)} por hora (contando ${Math.trunc(BuyChecker.HOURS_PER_MONTH)} h al mes).`,
      })
    }

    // 2. Tu gasto del día a día y lo que te queda este mes.
    let verdict = 0
    const reasons: string[] = []
    if (plan != null) {
      const perDay = plan.perDay
      if (perDay > 0) facts.push({ title: 'Días de tu gasto diario', value: `${fixed(price / perDay, 1)} días`, detail: `Hoy te tocan ${fmt(perDay)} al día hasta el cobro.` })
      const spendable = plan.spendable
      const after = spendable - price
      const afterPerDay = after / Math.max(1, plan.days)
      facts.push({
        title: 'Cómo queda tu mes',
        value: after >= 0 ? `${fmt(afterPerDay)}/día` : `Te pasas ${fmt(-after)}`,
        detail: after >= 0 ? `Te quedarían ${fmt(after)} para gastar en ${plan.days} días (${fixed(price / Math.max(1.0, spendable) * 100, 0)} % de lo que te queda).`
          : `Ahora mismo solo te quedan ${fmt(Math.max(0, spendable))} para gastar hasta el cobro.`,
      })
      facts.push({ title: 'En el banco después', value: fmt(plan.main.balance - price), detail: `${plan.main.name} pasaría de ${fmt(plan.main.balance)} a ${fmt(plan.main.balance - price)}.` })
      if (after < 0) { verdict = 2; reasons.push('te pasarías de lo que puedes gastar este mes') }
      else if (price > spendable * 0.25) { verdict = Math.max(verdict, 1); reasons.push('se lleva más de un cuarto de lo que te queda') }
      else if (price > perDay * 2) { verdict = Math.max(verdict, 1); reasons.push('son más de dos días de tu gasto diario') }
    }

    // 3. Tu límite en ese tipo de gasto.
    const b = budget?.items.find((x) => x.tag === tag)
    if (b) {
      const total = b.spent + price
      facts.push({
        title: `En ${tagLower}`,
        value: `${fmt(total)} de ${fmt(b.monthly)}`,
        detail: total <= b.monthly ? `Llevas ${fmt(b.spent)} este mes; con esto te quedarían ${fmt(b.monthly - total)}.`
          : `Llevas ${fmt(b.spent)} este mes; con esto te pasarías ${fmt(total - b.monthly)} de lo que te toca.`,
      })
      if (total > b.monthly) { verdict = Math.max(verdict, total > b.monthly * 1.5 ? 2 : 1); reasons.push(`te pasas en ${tagLower}`) }
    }

    // 4. Tu objetivo de ahorro.
    if (goal != null && goal.pace > 0) {
      const days = Math.trunc(price / goal.pace * 30.4)
      facts.push({
        title: `Tu objetivo «${goal.goal.name.trim() === '' ? 'de ahorro' : goal.goal.name}»`,
        value: days <= 0 ? 'Sin retraso' : `+${days} ${days === 1 ? 'día' : 'días'}`,
        detail: `Ahorras unos ${fmt(goal.pace)} al mes: ese dinero tardarías ${days <= 0 ? 'menos de un día' : days === 1 ? '1 día' : `${days} días`} en volver a ahorrarlo.`,
      })
    }

    // 5. En tus propios gastos: "= 11 Monster Energy".
    const since = ep(d.today) - 90
    const groups = [...groupBy(d.spending.filter((e) => e.epochDay >= since && e.amount >= 1.0 && e.amount <= price / 2 && e.note.trim() !== ''), (e) => Merchants.clean(e.note)).entries()]
      .filter(([, l]) => l.length >= 3)
    const top = maxBy(groups, ([, l]) => l.length)
    if (top) {
      const [shop, list] = top
      const avg = list.reduce((s, e) => s + e.amount, 0) / list.length
      facts.push({ title: 'En lo que sueles comprar', value: `= ${Math.round(price / avg)} × ${shop}`, detail: `Pagas unos ${fmt(avg)} cada vez que vas a ${shop}.` })
    }

    // 6. Si lo invirtieras.
    const maxRate = d.accounts.length === 0 ? null : Math.max(...d.accounts.map((a) => a.interestRate))
    const rate = maxRate != null && maxRate > 0 ? maxRate : 5.0
    const invested = [1, 5, 10].map((y): [number, number] => [y, price * Math.pow(1 + rate / 100, y)])

    if (price >= Math.max(100.0, d.monthlyIncome * 0.1)) { verdict = Math.max(verdict, 1); reasons.push('es una compra grande: espera 48 h antes de decidir') }
    const text = verdict === 0 ? 'Puedes permitírtelo sin descolocar tu mes.' : capFirst(distinct(reasons).join(', ')) + '.'
    return { name: name.trim(), price, tag, verdict, verdictText: text, facts, invested, rate }
  },
}
