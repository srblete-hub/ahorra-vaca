// Port de domain/Goals.kt: objetivos de ahorro, edad financiera y lista de objetivos por prioridad.
import type { LocalDate } from '../data/dates'
import {
  clamp, date, ep, fixed, fmtMonthShortYear, isBefore, ktNum, maxBy, monthNameFull, monthsBetweenDates, plusM, range, sortedByDesc,
  sumOf, toDoubleOrNull, toLongOrNull, withDayOfMonth,
} from './kt'
import { InterestMath, isSaving } from './FinanceMath'
import { Classifier, SPEND_TAG_INFO, spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { byTag, Insights } from './Insights'
import type { FinanceData } from './Insights'
import { Bills } from './Planning'
import type { BankPlan } from './Planning'

/** Un objetivo de ahorro: "10.000 € ahorrados en 5 años". */
export interface Goal { name: string; target: number; start: LocalDate; deadline: LocalDate }

/** Estado de un objetivo. `progress`, `done`, `onTrack` y `monthsAhead` (calculadas en Kotlin) se rellenan al crearlo. */
export interface GoalStatus {
  goal: Goal
  /** Lo que ya tienes para el objetivo: ahorro + inversiones. */
  current: number
  monthsLeft: number
  /** Rentabilidad que se supone (la mejor cuenta remunerada que tienes). */
  rate: number
  /** Cuánto hay que meter cada mes desde hoy para llegar a tiempo. */
  requiredMonthly: number
  /** Lo que estás ahorrando de verdad al mes (media reciente, o lo del reparto). */
  pace: number
  /** Con lo que tienes y tu ritmo, cuánto tendrías en la fecha. */
  projected: number
  /** Cuándo llegarías a tu ritmo (null si no llegarías nunca). */
  eta: LocalDate | null
  tips: string[]
  /** % de reparto que haría falta en la cuenta principal (null sin reparto). */
  suggestedPct: number | null
  progress: number
  done: boolean
  onTrack: boolean
  /** Meses de adelanto (+) o retraso (−) respecto a la fecha. */
  monthsAhead: number | null
}

function makeGoalStatus(
  goal: Goal, current: number, monthsLeft: number, rate: number, requiredMonthly: number, pace: number, projected: number,
  eta: LocalDate | null, tips: string[], suggestedPct: number | null,
): GoalStatus {
  const done = current >= goal.target
  return {
    goal, current, monthsLeft, rate, requiredMonthly, pace, projected, eta, tips, suggestedPct,
    progress: clamp(current / goal.target, 0, 1),
    done,
    onTrack: done || projected >= goal.target * 0.98,
    monthsAhead: eta ? monthsBetweenDates(eta, goal.deadline) : null,
  }
}

const lower = (t: SpendTag) => SPEND_TAG_INFO[t].label.toLowerCase()

export const Goals = {
  /** Cuota mensual para pasar de `pv` a `target` en `months` meses con interés compuesto. */
  requiredMonthly(pv: number, target: number, ratePct: number, months: number): number {
    if (months <= 0) return Math.max(0, target - pv)
    const r = InterestMath.monthlyRate(ratePct)
    if (r === 0) return Math.max(0, (target - pv) / months)
    const g = Math.pow(1 + r, months)
    return Math.max(0, (target - pv * g) * r / (g - 1))
  },

  /** Meses hasta llegar a `target` metiendo `monthly` al mes (null si no se llega en 100 años). */
  monthsTo(pv: number, target: number, ratePct: number, monthly: number): number | null {
    if (pv >= target) return 0
    const r = InterestMath.monthlyRate(ratePct)
    if (r === 0) return monthly <= 0 ? null : Math.trunc(Math.ceil((target - pv) / monthly))
    const num = target * r + monthly
    const den = pv * r + monthly
    if (den <= 0 || num <= 0) return null
    const n = Math.log(num / den) / Math.log(1 + r)
    return Number.isNaN(n) || n > 1200 ? null : Math.trunc(Math.ceil(n))
  },

  rateOf(d: FinanceData): number {
    const s = d.accounts.filter((a) => isSaving(a))
    return s.length === 0 ? 0 : Math.max(...s.map((a) => a.interestRate))
  },

  /** Ritmo real de ahorro: lo que te sobra de media en los meses cerrados; si no hay, lo que manda el reparto. */
  paceOf(d: FinanceData, plan: BankPlan | null): number {
    const closed = d.closedMonths(3).filter((m) => d.receivedIn(m) > 0)
    const netAvg = closed.length === 0 ? null : sumOf(closed, (m) => d.receivedIn(m) - d.spentIn(m)) / closed.length
    return Math.max(0, netAvg ?? plan?.saveGoal ?? 0)
  },

  monthsLeft(d: FinanceData, goal: Goal): number {
    return Math.max(0, monthsBetweenDates(withDayOfMonth(d.today, 1), withDayOfMonth(goal.deadline, 1)))
  },

  status(goal: Goal, d: FinanceData, plan: BankPlan | null, fmt: (v: number) => string): GoalStatus {
    const current = d.wealth.savings + d.wealth.invested
    const rate = Goals.rateOf(d)
    const pace = Goals.paceOf(d, plan)
    const months = Goals.monthsLeft(d, goal)
    const projected = InterestMath.project(current, rate, pace, months).finalBalance
    const m = Goals.monthsTo(current, goal.target, rate, pace)
    const eta = m == null ? null : plusM(d.today, m)
    return Goals.build(goal, d, plan, fmt, current, pace, eta, projected)
  },

  build(goal: Goal, d: FinanceData, plan: BankPlan | null, fmt: (v: number) => string, current: number, pace: number, eta: LocalDate | null, projected: number): GoalStatus {
    const months = Goals.monthsLeft(d, goal)
    const rate = Goals.rateOf(d)
    const required = Goals.requiredMonthly(current, goal.target, rate, months)
    const suggestedPct: number | null = null

    const tips: string[] = []
    const gap = required - pace
    if (current >= goal.target) tips.push('¡Objetivo cumplido! Ponte uno nuevo para seguir avanzando.')
    else if (gap <= 0) {
      tips.push(`Vas bien: a tu ritmo (${fmt(pace)} al mes) llegarías` + (eta ? ` en ${monthNameFull(eta.month)} de ${eta.year}` : '') + '.')
      tips.push(`Automatiza: manda ${fmt(required)} al ahorro el día que cobras y olvídate.`)
    } else {
      tips.push(`Te faltan ${fmt(gap)} al mes para llegar a tiempo.`)
      if (plan?.target != null) tips.push(`Este mes tu reparto manda ${fmt(plan.saveGoal)} a ${plan.target.name}; para llegar harían falta ${fmt(required)}. Baja el margen de fin de mes o tu gasto habitual.`)
      const closed = d.closedMonths(3)
      const wants = byTag(closed.flatMap((m) => d.spendingIn(m)))
        .filter((p) => !SPEND_TAG_INFO[p[0]].need && p[0] !== 'OTHER').map((p): [SpendTag, number] => [p[0], p[1] / Math.max(1, closed.length)])
      const top = wants[0]
      if (top) {
        const [t, avg] = top
        const cut = Math.min(gap, avg * 0.3)
        if (cut >= 5) tips.push(`Recorta ${lower(t)} un ${Math.trunc(cut / avg * 100)} % (${fmt(cut)} al mes): es tu gasto prescindible más alto.`)
      }
      const sub = maxBy(d.bills.filter((b) => !b.yearly && Classifier.guess(b.name) === 'SUBSCRIPTIONS'), (b) => b.amount)
      if (sub) tips.push(`Cancelar o compartir ${sub.name} te da ${fmt(sub.amount)} al mes más.`)
      const longer = Goals.monthsTo(current, goal.target, rate, pace)
      if (longer != null && pace > 0) tips.push(`Si no cambias nada, llegarías en ${Math.trunc(longer / 12)} años y ${longer % 12} meses.`)
    }
    if (rate < 2 && current > 500) tips.push('Tu ahorro no da intereses: en una cuenta al 2–5 % llegarías antes sin poner más.')
    if (rate >= 2 && !isBefore(goal.deadline, d.today)) {
      const interest = InterestMath.project(current, rate, required, months).interest
      if (interest > 1) tips.push(`Los intereses (${fixed(rate, 1)} %) pondrán unos ${fmt(interest)} por ti.`)
    }
    return makeGoalStatus(goal, current, months, rate, required, pace, projected, eta, tips, suggestedPct)
  },
}

// ---------- Edad financiera (inspirada en la "WHOOP Age") ----------

/** Un factor que suma o resta años a tu edad financiera. `years` positivo = te da madurez (bueno). */
export interface AgeFactor { name: string; value: string; years: number; explanation: string }

/** `age` y `difference` (calculadas en Kotlin) se rellenan al crearla. */
export interface FinancialAge {
  realAge: number
  factors: AgeFactor[]
  /** Velocidad a la que maduran tus finanzas: 1x normal, >1 más rápido (bueno), <1 te quedas atrás (−1x…3x). */
  pace: number
  /** Con pocos meses de datos la cifra aún se mueve mucho. */
  calibrating: boolean
  /** Madurez financiera: cuanto mayor, mejor (tener la edad financiera de un adolescente es malo). */
  age: number
  difference: number
}

const factor = (name: string, value: string, years: number, explanation: string): AgeFactor => ({ name, value, years, explanation })

export const FinancialAging = {
  /** Patrimonio que se espera para tu edad, en años de ingresos (≈1x a los 30, 3x a los 38, 6x a los 50). */
  expectedMultiple(age: number): number { return Math.max(0.25, (age - 26) * 0.25) },

  evaluate(realAge: number, d: FinanceData, goal: GoalStatus | null): FinancialAge {
    const f: AgeFactor[] = []
    const annual = d.monthlyIncome * 12
    const w = d.wealth

    const multiple = annual > 0 ? w.total / annual : null
    const expected = FinancialAging.expectedMultiple(realAge)
    f.push(multiple == null ? factor('Patrimonio para tu edad', 'Sin ingresos', 0, 'Apunta tus ingresos para compararlo.')
      : factor(
        'Patrimonio para tu edad', `${fixed(multiple, 1)}x tu sueldo anual`,
        clamp((expected - multiple) * 1.2, -4, 4),
        `Referencia habitual a tu edad: unas ${fixed(expected, 1)} veces lo que ganas en un año (1× a los 30, 3,5× a los 40, 6× a los 50). Orientativo.`,
      ))

    const closed = d.closedMonths(3).filter((m) => d.receivedIn(m) > 0)
    const inc = sumOf(closed, (m) => d.receivedIn(m))
    const rate = inc > 0 ? (inc - sumOf(closed, (m) => d.spentIn(m))) / inc * 100 : null
    f.push(rate == null ? factor('Capacidad de ahorro', 'Calibrando', 0, 'Necesito al menos un mes cerrado con cobros.')
      : factor(
        'Capacidad de ahorro', `${fixed(rate, 0)} %`,
        rate >= 30 ? -2.5 : rate >= 20 ? -1.5 : rate >= 10 ? -0.5 : rate >= 0 ? 1.0 : 3.0,
        'Lo que te queda de cada euro que entra. Lo ideal, un 20 % o más.',
      ))

    const cost = Insights.costOfLiving(d).monthly
    const cushion = cost > 0 ? w.savings / cost : null
    f.push(cushion == null ? factor('Colchón de emergencia', 'Calibrando', 0, 'Apunta gastos para calcularlo.')
      : factor(
        'Colchón de emergencia', `${fixed(cushion, 1)} meses`,
        cushion >= 6 ? -2.0 : cushion >= 3 ? -1.0 : cushion >= 1 ? 0.5 : 2.0,
        'Meses que aguantarías sin ingresos con tu coste de vida real.',
      ))

    const longTerm = w.savings + w.invested
    const investedShare = longTerm > 0 ? w.invested / longTerm * 100 : 0
    f.push(factor(
      'Dinero invertido', `${fixed(investedShare, 0)} % de tu ahorro`,
      investedShare >= 30 ? -1.5 : investedShare > 0 ? -0.5 : 1.0,
      'Invertir a largo plazo hace que tu dinero crezca más que la inflación.',
    ))

    const debtRatio = annual > 0 ? w.debts / annual * 100 : w.debts > 0 ? 100 : 0
    f.push(factor(
      'Deudas', w.debts <= 0 ? 'Ninguna' : `${fixed(debtRatio, 0)} % de un año de sueldo`,
      w.debts <= 0 ? -0.5 : debtRatio <= 35 ? 1.0 : 3.0,
      'Las deudas caras son lo que más envejece tus finanzas.',
    ))

    const fixedPct = d.monthlyIncome > 0 ? sumOf(d.bills, (b) => Bills.monthly(b)) / d.monthlyIncome * 100 : null
    if (fixedPct != null) f.push(factor(
      'Gastos fijos', `${fixed(fixedPct, 0)} % de lo que ganas`,
      fixedPct <= 35 ? -0.5 : fixedPct <= 50 ? 0.5 : 1.5,
      'Cuanto menos se va en fijos, más margen tienes cada mes.',
    ))

    const recent = d.spending.filter((e) => e.epochDay > ep(d.today) - 90 && e.billId == null)
    const total = sumOf(recent, (e) => e.amount)
    if (total > 0) {
      const wants = sumOf(recent.filter((e) => !SPEND_TAG_INFO[spendTag(e)].need), (e) => e.amount) / total * 100
      f.push(factor(
        'Hábitos de gasto', `${fixed(wants, 0)} % prescindible`,
        wants <= 30 ? -0.5 : wants <= 50 ? 0.0 : 1.0,
        'Parte de tu gasto variable que va a caprichos, en los últimos 90 días.',
      ))
    }

    f.push(goal == null ? factor('Objetivo', 'Sin objetivo', 0.5, 'Tener una meta concreta ayuda a ahorrar más.')
      : goal.onTrack ? factor('Objetivo', 'Vas a tiempo', -1.0, `${goal.goal.name}: a tu ritmo llegas.`)
        : factor('Objetivo', 'Vas con retraso', 0.5, `${goal.goal.name}: te faltan ${fixed(goal.requiredMonthly - goal.pace, 0)} € al mes.`))

    const pace = rate == null ? 1.0 : clamp(1.0 + (rate - 10) / 15, -1.0, 3.0)
    // Más madurez = mejor: lo que antes "rejuvenecía" ahora suma años y viceversa.
    const maturity = sortedByDesc(f.map((x) => ({ ...x, years: -x.years })), (x) => x.years)
    const age = Math.max(realAge + sumOf(maturity, (x) => x.years), 14.0)
    return { realAge, factors: maturity, pace, calibrating: d.closedMonths(3).length < 2 || d.monthlyIncome <= 0, age, difference: age - realAge }
  },
}

/** Varios objetivos en orden de prioridad: el ahorro y lo que apartas cada mes van primero al de arriba. */
export const GoalList = {
  parse(raw: string): Goal[] {
    const out: Goal[] = []
    for (const line of raw.split(/\r\n|\n|\r/)) {
      const p = line.split('|')
      if (p.length !== 4) continue
      const target = toDoubleOrNull(p[1])
      if (target == null || !(target > 0)) continue
      const deadline = toLongOrNull(p[3])
      if (deadline == null || !(deadline > 0)) continue
      out.push({ name: p[0].trim() === '' ? 'Mi objetivo' : p[0], target, start: date(toLongOrNull(p[2]) ?? deadline), deadline: date(deadline) })
    }
    return out
  },

  format(list: Goal[]): string {
    return list.map((g) => [g.name.replace(/\|/g, ' ').replace(/\n/g, ' '), ktNum(g.target), String(ep(g.start)), String(ep(g.deadline))].join('|')).join('\n')
  },

  /** Simula mes a mes: cada objetivo recibe primero lo que necesita, por orden; lo que sobra va al primero sin completar. */
  plan(goals: Goal[], d: FinanceData, plan: BankPlan | null, fmt: (v: number) => string, extraNow = 0): GoalStatus[] {
    if (goals.length === 0) return []
    const rate = Goals.rateOf(d)
    const r = InterestMath.monthlyRate(rate)
    const pace = Goals.paceOf(d, plan)
    let left = Math.max(0, d.wealth.savings + d.wealth.invested - extraNow)
    const current = goals.map((g) => { const v = Math.min(g.target, left); left -= v; return v })
    const bal = [...current]
    const req = goals.map((g, i) => Goals.requiredMonthly(current[i], g.target, rate, Goals.monthsLeft(d, g)))
    const eta: Array<LocalDate | null> = goals.map(() => null)
    const projected = [...current]
    const firstGive = goals.map(() => 0)
    goals.forEach((g, i) => { if (bal[i] >= g.target) eta[i] = d.today })
    for (let m = 1; m <= 600; m++) {
      let avail = pace
      const open = range(0, goals.length - 1).filter((i) => eta[i] == null)
      if (open.length === 0) break
      open.forEach((i) => { bal[i] *= 1 + r })
      for (const i of open) { const give = Math.min(avail, req[i]); bal[i] += give; avail -= give; if (m === 1) firstGive[i] += give }
      const firstOpen = open[0]
      if (firstOpen != null) { bal[firstOpen] += avail; if (m === 1) firstGive[firstOpen] += avail }
      for (let i = 0; i < goals.length; i++) {
        if (eta[i] == null && bal[i] >= goals[i].target) eta[i] = plusM(d.today, m)
        if (m === Goals.monthsLeft(d, goals[i])) projected[i] = bal[i]
      }
    }
    return goals.map((g, i) => {
      const months = Goals.monthsLeft(d, g)
      const e = eta[i]
      const proj = months === 0 ? current[i] : e != null && ep(e) <= ep(g.deadline) ? Math.max(projected[i], g.target) : projected[i]
      return Goals.build(g, d, plan, fmt, current[i], firstGive[i], e, proj)
    })
  },

  /** "Si pones primero B…": cómo cambian las fechas al cambiar el orden de los dos primeros. */
  swapImpact(goals: Goal[], d: FinanceData, plan: BankPlan | null, fmt: (v: number) => string): string | null {
    if (goals.length < 2) return null
    const now = GoalList.plan(goals, d, plan, fmt)
    const swapped = GoalList.plan([goals[1], goals[0], ...goals.slice(2)], d, plan, fmt)
    const e = (s: GoalStatus) => (s.eta ? fmtMonthShortYear(s.eta) : 'nunca')
    return `Si pones primero "${goals[1].name}", llegaría en ${e(swapped[0])} (ahora ${e(now[1])}) y "${goals[0].name}" pasaría a ${e(swapped[1])} (ahora ${e(now[0])}).`
  },
}
