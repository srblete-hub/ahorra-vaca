// Port de domain/Budget.kt: límites por tipo de gasto, rueda de presupuesto, consejo de ahorro y de hoy,
// perfil de ahorrador y por qué sube o baja el patrimonio.
import type { Expense, RecurringBill } from '../data/models'
import type { LocalDate, YearMonth } from '../data/dates'
import {
  atDay, atDayClamped, capFirst, dateEq, distinct, dow, ep, fixedSigned, fmtWeekdayDay, isBefore, lenOfMonth, maxBy, minBy, monday,
  sign, sortedByDesc, sum, sumOf, ymLen, ktNum, toDoubleOrNull,
} from './kt'
import { plusMonths } from '../data/dates'
import { InterestMath, isSaving } from './FinanceMath'
import { Classifier, SPEND_TAG_INFO, spendTag, spendTagOf } from './Spend'
import type { SpendTag } from './Spend'
import { byTag, Insights } from './Insights'
import type { FinanceData, Habits } from './Insights'
import { Bills } from './Planning'
import type { BankPlan } from './Planning'
import type { GoalStatus } from './Goals'
import { WeeklyNudge } from './Signals'

const WEEKS_PER_MONTH = 52.0 / 12
const lower = (t: SpendTag) => SPEND_TAG_INFO[t].label.toLowerCase()

/** Lo que fijas tú para un tipo de gasto: `amount` por semana o por mes. `monthly` se calcula al crearlo. */
export interface TagLimit { amount: number; weekly: boolean; monthly: number }
export function makeTagLimit(amount: number, weekly: boolean): TagLimit {
  return { amount, weekly, monthly: weekly ? amount * WEEKS_PER_MONTH : amount }
}

/** Límites por tipo de gasto (Map<SpendTag, TagLimit> en Kotlin; el orden de las claves se conserva). */
export type TagLimits = Partial<Record<SpendTag, TagLimit>>

/** Cuánto te toca gastar en un tipo de gasto este mes y cuánto llevas. `weekly` y `left` se calculan al crearlo. */
export interface TagBudget { tag: SpendTag; monthly: number; pct: number; fixedByYou: TagLimit | null; spent: number; weekly: number; left: number }

export interface SpendingBudget {
  /** Lo que hay para gastar este mes en total (lo que puedes gastar ahora + lo ya gastado). */
  base: number
  /** Sale del reparto de tu cuenta principal (si no, de ingresos − fijos − ahorro). */
  fromBank: boolean
  income: number
  fixed: number
  saving: number
  items: TagBudget[]
}

/** Reparto de partida si aún no hay historial. */
const DEFAULT_WEIGHTS: Partial<Record<SpendTag, number>> = {
  SUPER: 35.0, FOOD: 15.0, TRANSPORT: 12.0, LEISURE: 10.0, SHOPPING: 10.0, HEALTH: 5.0, SUBSCRIPTIONS: 3.0, OTHER: 10.0,
}

const HOUSING = ['alquiler', 'hipoteca', 'renta piso', 'renta del piso', 'comunidad de vecinos', 'cuota comunidad', 'ibi']

const entriesOf = <V>(r: Partial<Record<SpendTag, V>>) => Object.entries(r) as Array<[SpendTag, V]>

export const Budgeting = {
  /** "TRANSPORT:30:W;SUPER:250:M" ↔ límites. Sin nada guardado: gasolina 30 € a la semana. */
  parseLimits(raw: string | null | undefined): TagLimits {
    if (raw == null) return { TRANSPORT: makeTagLimit(30.0, true) }
    const out: TagLimits = {}
    for (const part of raw.split(';')) {
      const p = part.split(':')
      if (p.length !== 3) continue
      const tag = spendTagOf(p[0])
      if (!tag) continue
      const amount = toDoubleOrNull(p[1])
      if (amount == null) continue
      out[tag] = makeTagLimit(amount, p[2] === 'W')
    }
    return out
  },

  formatLimits(limits: TagLimits): string {
    return entriesOf(limits).map(([t, l]) => `${t}:${ktNum(l.amount)}:${l.weekly ? 'W' : 'M'}`).join(';')
  },

  /** Pago de un gasto fijo: enlazado, o con su nombre e importe parecido (ya cuenta en los fijos). */
  isBillPayment(e: Expense, bills: RecurringBill[]): boolean {
    return e.billId != null || bills.some((b) => e.note.toLowerCase().includes(b.name.toLowerCase()) && Math.abs(e.amount - b.amount) <= b.amount * 0.1)
  },

  /**
   * Gasto fijo de una vez al mes (alquiler, hipoteca, comunidad, recibos): no cuenta en el resumen del día a día
   * ni en la media diaria, porque la desvirtúa. Aunque no esté dado de alta como recibo, se reconoce el alquiler
   * por el texto y los pagos grandes de casa (250 € o más).
   */
  isFixed(e: Expense, bills: RecurringBill[]): boolean {
    if (Budgeting.isBillPayment(e, bills)) return true
    const n = Classifier.normalize(e.note)
    return HOUSING.some((h) => n.includes(h)) || (spendTag(e) === 'HOME' && e.amount >= 250)
  },

  /** Gasto variable (sin fijos ni ahorro) de un mes. */
  variable(d: FinanceData, m: YearMonth): Expense[] { return d.spendingIn(m).filter((e) => !Budgeting.isFixed(e, d.bills)) },

  plan(d: FinanceData, saving: number, limits: TagLimits, plan: BankPlan | null = null, profile: Partial<Record<SpendTag, number>> | null = null): SpendingBudget {
    const income = d.monthlyIncome
    const fixedV = sumOf(d.bills, (b) => Bills.monthly(b))
    const spentNow = sumOf(Budgeting.variable(d, d.thisMonth), (e) => e.amount)
    // Con reparto, la rueda parte de lo mismo que "Puedes gastar": lo que te queda + lo que ya llevas.
    const fromBank = plan?.target != null
    const base = fromBank ? Math.max(0, plan!.spendable + spentNow) : Math.max(0, income - fixedV - saving)
    const fixedTotal = sumOf(entriesOf(limits), ([, l]) => l.monthly)
    const rest = Math.max(0, base - fixedTotal)

    // Pesos: tu gasto medio por tipo (sin gastos fijos) en los meses cerrados; sin historial, uno estándar.
    const closed = d.closedMonths(3)
    const history: Partial<Record<SpendTag, number>> = Object.fromEntries(byTag(closed.flatMap((m) => Budgeting.variable(d, m))))
    // Con perfil de ahorrador, los % los fija el perfil; si no, tus hábitos.
    const src = profile ?? (sum(Object.values(history) as number[]) > 0 ? history : DEFAULT_WEIGHTS)
    const weights: Partial<Record<SpendTag, number>> = {}
    for (const [t, w] of entriesOf(src)) if (!(t in limits) && w > 0) weights[t] = w
    const weightSum = sum(Object.values(weights) as number[])

    const spent: Partial<Record<SpendTag, number>> = Object.fromEntries(byTag(Budgeting.variable(d, d.thisMonth)))
    const tags = distinct([...(Object.keys(limits) as SpendTag[]), ...(Object.keys(weights) as SpendTag[])])
    const items = sortedByDesc(tags.map((t): TagBudget => {
      const lim = limits[t]
      const monthly = lim?.monthly ?? (weightSum > 0 ? rest * weights[t]! / weightSum : 0)
      return { tag: t, monthly, pct: base > 0 ? monthly / base * 100 : 0, fixedByYou: lim ?? null, spent: spent[t] ?? 0, weekly: monthly / WEEKS_PER_MONTH, left: monthly - (spent[t] ?? 0) }
    }), (b) => b.monthly)
    return { base, fromBank, income, fixed: fixedV, saving, items }
  },
}

/** Cuánto meter en tu cuenta de ahorro (Robinhood…) al mes, con el porqué. */
export interface SavingsAdvice {
  recommended: number
  /** % de lo que entra cada mes. */
  pctOfIncome: number
  /** Lo máximo razonable sin ahogarte: ingresos − fijos − lo básico − un mínimo para ti. */
  capacity: number
  reasons: string[]
  inOneYear: number
  interestOneYear: number
}

export const SavingsAdvisor = {
  advise(d: FinanceData, goal: GoalStatus | null, fmt: (v: number) => string, plan: BankPlan | null = null): SavingsAdvice | null {
    const income = d.monthlyIncome
    if (income <= 0) return null
    const fixedV = sumOf(d.bills, (b) => Bills.monthly(b))
    // Lo básico (súper, transporte, salud…) según tu media; si no hay datos, un 25 % de lo que entra.
    const closed = d.closedMonths(3)
    const needs = closed.length === 0 ? income * 0.25
      : sumOf(closed, (m) => sumOf(Budgeting.variable(d, m).filter((e) => SPEND_TAG_INFO[spendTag(e)].need), (e) => e.amount)) / closed.length
    const capacity = Math.max(0, income - fixedV - needs - income * 0.10)

    const reasons: string[] = []
    const candidates: Array<[number, string]> = [[income * 0.20, `La regla clásica: un 20 % de lo que entra (${fmt(income * 0.20)}).`]]
    const cost = Insights.costOfLiving(d).monthly
    const cushionGap = cost * 3 - d.wealth.savings
    if (cushionGap > 0) candidates.push([cushionGap / 12, `Te faltan ${fmt(cushionGap)} para tener 3 meses de colchón: en un año serían ${fmt(cushionGap / 12)} al mes.`])
    if (goal != null && !goal.done) candidates.push([goal.requiredMonthly, `Para "${goal.goal.name}" necesitas ${fmt(goal.requiredMonthly)} al mes.`])
    const [want, why] = maxBy(candidates, (c) => c[0])!
    reasons.push(why)
    const recommended = Math.round(Math.min(want, capacity) / 5) * 5.0
    if (want > capacity) reasons.push(`No te recomiendo más de ${fmt(capacity)}: después de fijos y lo básico necesitas margen para vivir.`)
    else reasons.push(`Te deja ${fmt(capacity - recommended)} de margen sobre lo que puedes permitirte.`)
    // Dinero arrastrado de meses anteriores en la cuenta principal: se puede mover de una vez.
    if (plan?.target != null) {
      const carried = plan.monthMoney - Math.max(0, income - fixedV)
      if (carried > 100) reasons.push(`Además arrastras unos ${fmt(carried)} de meses anteriores en ${plan.main.name}: si no los necesitas, muévelos de una vez a ${plan.target.name}.`)
    }
    const savingAccounts = d.accounts.filter((a) => isSaving(a))
    const rate = savingAccounts.length === 0 ? 0 : Math.max(...savingAccounts.map((a) => a.interestRate))
    const year = InterestMath.project(0, rate, recommended, 12)
    return { recommended, pctOfIncome: recommended / income * 100, capacity, reasons, inOneYear: year.finalBalance, interestOneYear: year.interest }
  },
}

/** Consejo de hoy: según el día que es, lo que queda para cobrar y cómo vas. */
export interface DailyAdvice { headline: string; perDay: number; spentToday: number; tips: string[] }

export const Advisor = {
  /** Próximo cobro fijo (nómina): el día de inicio de la fuente fija que aún no ha llegado. */
  nextFixedPayday(d: FinanceData): LocalDate | null { return Advisor.nextPayday(d, true) },

  /** Próximo cobro (fijo o variable, como YouTube el día 22) que aún no ha llegado. */
  nextPayday(d: FinanceData, fixedOnly = false): LocalDate | null {
    const fixedS = d.sources.filter((s) => !fixedOnly || !s.variable)
    if (fixedS.length === 0) return null
    const dates = fixedS.map((s) => {
      const thisMonth = d.thisMonth
      const received = d.incomes.some((it) => it.sourceId === s.id && d.inMonth(it.epochDay, thisMonth))
      const day = atDay(thisMonth, Math.min(s.dayFrom, ymLen(thisMonth)))
      if (!received && !isBefore(day, d.today)) return day
      return atDayClamped(plusMonths(thisMonth, 1), s.dayFrom)
    })
    return minBy(dates, ep) ?? null
  },

  today(d: FinanceData, plan: BankPlan | null, budget: SpendingBudget | null, habits: Habits | null, fmt: (v: number) => string, userName = ''): DailyAdvice {
    const today = d.today
    const payday = Advisor.nextPayday(d)
    const payName = payday ? d.sources.find((s) => payday.day === Math.min(s.dayFrom, lenOfMonth(payday)))?.name ?? null : null
    const daysToPay = payday ? ep(payday) - ep(today) : null
    const spendable = plan?.spendable ?? null
    const days = Math.max(1, daysToPay ?? (lenOfMonth(today) - today.day + 1))
    const perDay = spendable != null && spendable > 0 ? spendable / days : 0
    const spentToday = sumOf(d.spending.filter((e) => e.epochDay === ep(today)), (e) => e.amount)
    const dayName = capFirst(fmtWeekdayDay(today))
    const headline = dayName + (
      daysToPay == null ? ''
        : daysToPay === 0 ? ' · hoy cobras'
          : daysToPay === 1 ? ' · mañana cobras' + (payName != null ? ` (${payName})` : '')
            : ` · cobras en ${daysToPay} días` + (payName != null ? ` (${payName})` : ''))

    const tips: string[] = []
    // 1) Acabas de cobrar: primero el ahorro.
    const fixedPays = d.incomes.filter((it) => it.sourceId != null && d.sources.some((s) => s.id === it.sourceId && !s.variable)).map((it) => it.epochDay)
    const lastPay = fixedPays.length === 0 ? null : Math.max(...fixedPays)
    if (plan?.target != null && plan.moveNow > 1 && lastPay != null && ep(today) - lastPay >= 0 && ep(today) - lastPay <= 4) {
      tips.push(`Acabas de cobrar: mueve ya ${fmt(plan.moveNow)} a ${plan.target.name} (tu ahorro del mes) antes de empezar a gastar.`)
    }
    const inc = plan?.incoming.find((x) => dateEq(x[2], today))
    if (plan && inc) {
      const [name, amount] = inc
      tips.push(`Hoy debería llegarte ${name} (~${fmt(amount)}). Cuando llegue, márcalo como cobrado con el importe real` +
        (plan.moveLater > 0 && plan.target != null ? ` y mueve ${fmt(plan.moveLater)} a ${plan.target.name}.` : '.'))
    }
    if (plan != null && plan.buffer > 0 && today.day >= lenOfMonth(today) - 3) {
      const endWith = plan.spendable + plan.buffer - plan.expectedSpend
      tips.push(endWith >= plan.buffer * 0.5 ? `Fin de mes a la vista: a tu ritmo llegas con ~${fmt(endWith)} de sobra.` : `Fin de mes justo: a tu ritmo llegarías con ${fmt(endWith)}. Estos días, solo lo imprescindible.`)
    }
    // 2) Cuánto tienes al día y cómo vas hoy.
    if (plan?.target != null && plan.saveGoal < plan.targetSaving - 5 && plan.targetSaving < Number.MAX_VALUE / 2) {
      tips.push(plan.saveGoal <= 0 ? `Este mes no te llega para ahorrar sin quedarte justo: prioriza llegar a fin de mes con +${fmt(plan.buffer)}.`
        : `Este mes solo puedes ahorrar ${fmt(plan.saveGoal)} de los ${fmt(plan.targetSaving)} que te propones: lo demás lo necesitas para vivir hasta fin de mes.`)
    }
    if (spendable != null) {
      if (spendable <= 0) tips.push('Lo de este mes ya está comprometido: hasta cobrar, solo lo imprescindible.')
      else if (spentToday > perDay * 1.2) tips.push(`Hoy llevas ${fmt(spentToday)}, más de tus ${fmt(perDay)} diarios: mañana intenta quedarte en ${fmt(Math.max(0, (spendable - (spentToday - perDay)) / days))}.`)
      else if (spentToday > perDay) tips.push(`Hoy llevas ${fmt(spentToday)}: justo en tu límite de ${fmt(perDay)}. Mejor no gastar más hoy.`)
      else if (spentToday > 0) tips.push(`Hoy llevas ${fmt(spentToday)} de ${fmt(perDay)}: vas bien.`)
      else tips.push(`Tienes ${fmt(perDay)} para hoy. Si no los gastas, se reparten entre los días que quedan.`)
    }
    // 3) Ayer.
    const yesterday = sumOf(d.spending.filter((e) => e.epochDay === ep(today) - 1 && e.billId == null), (e) => e.amount)
    if (perDay > 0 && yesterday > perDay * 1.5) tips.push(`Ayer gastaste ${fmt(yesterday)}, bastante más de lo previsto. Compénsalo con un par de días tranquilos.`)
    // 4) Final de mes / cerca del cobro.
    if (daysToPay != null && daysToPay >= 1 && daysToPay <= 3) tips.push(`Faltan ${daysToPay} ${daysToPay === 1 ? 'día' : 'días'} para cobrar: aguanta con lo que tengas en casa.`)
    else if (daysToPay != null && daysToPay > 20 && spendable != null && spendable > 0) tips.push(`Queda mucho mes por delante: no gastes de golpe, ve semana a semana (${fmt(perDay * 7)} por semana).`)
    // 5) Fin de semana.
    const w = dow(today)
    if (w === 4 || w === 5) {
      const weekend = habits ? habits.weekendAvg * 2 : null
      tips.push('Llega el fin de semana' + (weekend != null && weekend > 0 ? `: sueles gastar unos ${fmt(weekend)}.` : '.') +
        (perDay > 0 ? ` Para no pasarte, deja el finde en ${fmt(perDay * 2.5)}.` : ''))
    }
    // 6) Gastos fijos que salen pronto.
    for (const b of d.bills) {
      const due = Bills.dueDate(b, d.thisMonth)
      if (due && !isBefore(due, today) && ep(due) - ep(today) <= 3 && !Bills.isPaid(b, d.thisMonth, d.expenses)) {
        tips.push(`${dateEq(due, today) ? 'Hoy' : `El ${due.day}`} se cobra ${b.name} (${fmt(b.amount)}): que haya saldo en la cuenta.`)
      }
    }
    // 7) Presupuesto por tipo: semana de gasolina y lo que va pasado de ritmo.
    for (const b of budget?.items ?? []) {
      const lim = b.fixedByYou
      if (lim != null && lim.weekly) {
        const mon = ep(monday(today))
        const week = sumOf(d.spending.filter((e) => e.epochDay >= mon && spendTag(e) === b.tag), (e) => e.amount)
        tips.push(week >= lim.amount ? `Esta semana ya has gastado ${fmt(week)} en ${lower(b.tag)} (tu límite: ${fmt(lim.amount)}).`
          : `Te quedan ${fmt(lim.amount - week)} de ${lower(b.tag)} esta semana.`)
      } else if (b.monthly > 0) {
        const expected = b.monthly * today.day / lenOfMonth(today)
        if (b.spent > expected * 1.3 && b.spent > 20) tips.push(`En ${lower(b.tag)} llevas ${fmt(b.spent)} de ${fmt(b.monthly)} y estamos a día ${today.day}: frena un poco.`)
      }
    }
    const nudge = WeeklyNudge.find(d, userName, fmt)
    if (nudge != null) tips.unshift(nudge)
    return { headline, perDay, spentToday, tips: tips.slice(0, 5) }
  },
}

/** Objetivos que puedes marcar en la ventana inicial. */
export type LifeGoal = 'CUSHION' | 'TRAVEL' | 'HOME' | 'CAR' | 'INVEST' | 'DEBTS' | 'TREATS'
export const LIFE_GOAL_VALUES: LifeGoal[] = ['CUSHION', 'TRAVEL', 'HOME', 'CAR', 'INVEST', 'DEBTS', 'TREATS']
export const LIFE_GOAL_INFO: Record<LifeGoal, { label: string }> = {
  CUSHION: { label: 'Colchón de emergencia' }, TRAVEL: { label: 'Viajar' }, HOME: { label: 'Comprar casa o la entrada' }, CAR: { label: 'Coche' },
  INVEST: { label: 'Invertir' }, DEBTS: { label: 'Pagar deudas' }, TREATS: { label: 'Darme caprichos sin culpa' },
}

/**
 * Cuán ahorrador quieres ser fija los % de cada tipo de gasto y cuánto se supone que gastarás
 * (más ahorrador = la app reserva menos para gastar y manda más al ahorro).
 */
export const SavingProfile = {
  STYLES: [
    ['Relajado', 'Disfruto el día a día; ahorro lo que sobre'],
    ['Equilibrado', 'Ni me privo ni derrocho'],
    ['Ahorrador', 'Prefiero recortar caprichos para ahorrar más'],
    ['Muy ahorrador', 'Lo justo para vivir; el resto, a ahorrar'],
  ] as Array<[string, string]>,

  /** Reparto del ingreso [necesidades, ocio, ahorro] según cuán ahorrador eres. */
  split(style: number): [number, number, number] {
    switch (style) {
      case 0: return [55.0, 30.0, 15.0]
      case 2: return [50.0, 20.0, 30.0]
      case 3: return [45.0, 10.0, 45.0]
      default: return [50.0, 30.0, 20.0]
    }
  },

  /** Cuánto de tu gasto habitual cuenta el reparto: el ahorrador asume que gastarás menos. */
  spendFactor(style: number): number { return style === 0 ? 1.1 : style === 2 ? 0.9 : style === 3 ? 0.8 : 1.0 },

  weights(style: number, goals: Iterable<string>): Partial<Record<SpendTag, number>> | null {
    if (style < 0) return null
    const g = new Set(goals)
    const w: Partial<Record<SpendTag, number>> =
      style === 0 ? { SUPER: 30.0, FOOD: 18.0, TRANSPORT: 12.0, LEISURE: 14.0, SHOPPING: 12.0, HEALTH: 5.0, SUBSCRIPTIONS: 4.0, OTHER: 5.0 }
        : style === 1 ? { SUPER: 35.0, FOOD: 14.0, TRANSPORT: 12.0, LEISURE: 11.0, SHOPPING: 10.0, HEALTH: 6.0, SUBSCRIPTIONS: 4.0, OTHER: 8.0 }
          : style === 2 ? { SUPER: 42.0, FOOD: 10.0, TRANSPORT: 13.0, LEISURE: 8.0, SHOPPING: 7.0, HEALTH: 7.0, SUBSCRIPTIONS: 3.0, OTHER: 10.0 }
            : { SUPER: 48.0, FOOD: 6.0, TRANSPORT: 14.0, LEISURE: 5.0, SHOPPING: 5.0, HEALTH: 8.0, SUBSCRIPTIONS: 2.0, OTHER: 12.0 }
    const move = (from: SpendTag, to: SpendTag, pts: number) => {
      const take = Math.min(pts, w[from] ?? 0)
      w[from] = (w[from] ?? 0) - take
      w[to] = (w[to] ?? 0) + take
    }
    if (g.has('TRAVEL')) { move('SHOPPING', 'TRAVEL', 3.0); move('LEISURE', 'TRAVEL', 2.0) }
    if (g.has('TREATS')) { move('OTHER', 'LEISURE', 3.0); move('SUPER', 'SHOPPING', 2.0) }
    if (g.has('HOME') || g.has('DEBTS')) { move('FOOD', 'OTHER', 2.0); move('SHOPPING', 'OTHER', 2.0) }
    const out: Partial<Record<SpendTag, number>> = {}
    for (const [t, v] of entriesOf(w)) if (v > 0) out[t] = v
    return out
  },
}

// ---------- Por qué sube o baja tu patrimonio ----------

export const WealthExplainer = {
  /**
   * "Debido a la bajada de ETH (−4,2 % en 24 h) y a tus gastos (−820 €); lo compensa en parte lo que has cobrado".
   * Separa lo que entra y sale de tus cuentas desde `fromDay` y atribuye el resto a tus inversiones.
   */
  explain(d: FinanceData, fromDay: number, change: number, dayChanges: Record<string, number>, fmt: (v: number) => string): string | null {
    if (Math.abs(change) < 1) return null
    const income = sumOf(d.incomes.filter((i) => i.epochDay > fromDay && i.accountId != null), (i) => i.amount)
    const spent = sumOf(d.expenses.filter((e) => e.epochDay > fromDay && e.accountId != null && !d.savingIds.has(e.categoryId)), (e) => e.amount)
    const invest = change - income + spent
    const raw: Array<[string, number, number | undefined]> = [
      ...d.crypto.map((c): [string, number, number | undefined] => [c.symbol, c.amount * c.price, dayChanges[c.symbol]]),
      ...d.stocks.map((s): [string, number, number | undefined] => [s.ticker, s.shares * s.price, dayChanges[s.ticker]]),
    ]
    const moves = raw.filter((m) => m[2] != null).map(([sym, value, pct]): [string, number, number] => [sym, pct!, value - value / (1 + pct! / 100)])
    const parts: Array<[string, number]> = []
    if (Math.abs(invest) >= 1) {
      const drivers = sortedByDesc(moves.filter((m) => sign(m[2]) === sign(invest) && Math.abs(m[1]) >= 0.5), (m) => Math.abs(m[2])).slice(0, 2)
      const word = invest < 0 ? 'la bajada' : 'la subida'
      parts.push([drivers.length > 0 ? `${word} de ` + drivers.map((m) => `${m[0]} (${fixedSigned(m[1], 1)} % en 24 h)`).join(' y ')
        : `${word} de tus inversiones (${(invest > 0 ? '+' : '−') + fmt(Math.abs(invest))})`, invest])
    }
    if (income >= 1) parts.push([`lo que has cobrado (+${fmt(income)})`, income])
    if (spent >= 1) parts.push([`tus gastos (−${fmt(spent)})`, -spent])
    const pro = sortedByDesc(parts.filter((p) => sign(p[1]) === sign(change)), (p) => Math.abs(p[1]))
    const contra = sortedByDesc(parts.filter((p) => sign(p[1]) !== sign(change)), (p) => Math.abs(p[1]))
    if (pro.length === 0) return null
    return 'Debido a ' + pro.slice(0, 2).map((p) => p[0]).join(' y ') + (contra[0] ? (change > 0 ? '; frenado en parte por ' : '; suavizado en parte por ') + contra[0][0] : '') + '.'
  },
}
