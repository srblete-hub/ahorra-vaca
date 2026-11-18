// Port de domain/Insights.kt: FinanceData (todo cargado) y los análisis (historia del mes, máquina del tiempo,
// coste de vida, radar, hábitos, contrafactual, salud financiera, gastos compartibles, simulador).
import type {
  Account, Category, CryptoHolding, Expense, Income, IncomeSource, NetWorthSnapshot, RecurringBill, Split, StockHolding,
} from '../data/models'
import type { LocalDate, YearMonth } from '../data/dates'
import {
  atDay, atEndOfMonth, average, capFirst, date, distinctBy, ep, fixed, groupBy, isAfter, isBefore, maxBy,
  minBy, minOf, minusD, monthNameFull, plusY, range, sortedByDesc, sumOf, weekdayName, ymBefore, ymEq, ymIdx, ymLen, ymMinus,
  ymOfDate, ymOfDay, dow,
} from './kt'
import { InterestMath, IncomeMath, Wealth } from './FinanceMath'
import type { Wealth as WealthT } from './FinanceMath'
import { Classifier, SPEND_TAG_INFO, SPEND_TAG_VALUES, spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { Bills, Forecasting } from './Planning'
import { Budgeting } from './Budget'

/** YearMonth.monthName(): "noviembre". */
export function monthName(m: YearMonth): string { return monthNameFull(m.month) }

/** Datos de partida de FinanceData (lo que se pasa al constructor en Kotlin). */
export interface FinanceDataInput {
  today: LocalDate
  accounts: Account[]
  crypto: CryptoHolding[]
  stocks: StockHolding[]
  categories: Category[]
  expenses: Expense[]
  incomes: Income[]
  sources: IncomeSource[]
  bills: RecurringBill[]
  snapshots: NetWorthSnapshot[]
  splits?: Split[]
  /** Dinero apartado para un plan (no cuenta como disponible para gastar). */
  planReserve?: number
}

/**
 * Todo lo que necesitan los análisis, ya cargado. Créalo con makeFinanceData(); para "copy(...)"
 * usa makeFinanceData({ ...d, expenses: nuevas }).
 */
export interface FinanceData extends Required<FinanceDataInput> {
  wealth: WealthT
  savingIds: Set<number>
  /** Gastos de verdad: sin lo que se aparta para ahorrar. */
  spending: Expense[]
  thisMonth: YearMonth
  firstMonth: YearMonth | null
  /** Ingreso mensual de referencia: la media cobrada en los últimos meses cerrados, o lo previsto. */
  monthlyIncome: number
  inMonth(day: number, m: YearMonth): boolean
  spendingIn(m: YearMonth): Expense[]
  spentIn(m: YearMonth): number
  receivedIn(m: YearMonth): number
  /** Meses ya cerrados con datos, del más reciente al más antiguo (como mucho n). */
  closedMonths(n: number): YearMonth[]
}

export function makeFinanceData(i: FinanceDataInput): FinanceData {
  const base = {
    today: i.today, accounts: i.accounts, crypto: i.crypto, stocks: i.stocks, categories: i.categories, expenses: i.expenses,
    incomes: i.incomes, sources: i.sources, bills: i.bills, snapshots: i.snapshots, splits: i.splits ?? [], planReserve: i.planReserve ?? 0,
  }
  const savingIds = new Set(i.categories.filter((c) => c.isSavings).map((c) => c.id))
  const spending = i.expenses.filter((e) => !savingIds.has(e.categoryId))
  const thisMonth = ymOfDate(i.today)
  const inMonth = (day: number, m: YearMonth) => ymEq(ymOfDay(day), m)
  const spendingIn = (m: YearMonth) => spending.filter((e) => inMonth(e.epochDay, m))
  const spentIn = (m: YearMonth) => sumOf(spendingIn(m), (e) => e.amount)
  const receivedIn = (m: YearMonth) => sumOf(i.incomes.filter((x) => inMonth(x.epochDay, m)), (x) => x.amount)
  const firstDay = minOf([...i.expenses.map((e) => e.epochDay), ...i.incomes.map((x) => x.epochDay)], (x) => x)
  const firstMonth = firstDay == null ? null : ymOfDay(firstDay)
  const closedMonths = (n: number) => (firstMonth == null ? [] : range(1, n).map((k) => ymMinus(thisMonth, k)).filter((m) => !ymBefore(m, firstMonth)))
  let monthlyIncomeCache: number | undefined
  const d: FinanceData = {
    ...base,
    wealth: Wealth.of(i.accounts, i.crypto, i.stocks),
    savingIds, spending, thisMonth, firstMonth,
    inMonth, spendingIn, spentIn, receivedIn, closedMonths,
    get monthlyIncome(): number {
      if (monthlyIncomeCache === undefined) {
        const closed = closedMonths(3).map(receivedIn).filter((v) => v > 0)
        monthlyIncomeCache = closed.length > 0 ? average(closed)
          : i.sources.length > 0 ? IncomeMath.forMonth(i.sources, i.incomes, thisMonth).total
            : receivedIn(thisMonth)
      }
      return monthlyIncomeCache
    },
  }
  return d
}

export function byTag(list: Expense[]): Array<[SpendTag, number]> {
  return sortedByDesc([...groupBy(list, spendTag).entries()].map(([t, l]): [SpendTag, number] => [t, sumOf(l, (e) => e.amount)]), (p) => p[1])
}

const amountOf = (list: Array<[SpendTag, number]>, t: SpendTag) => list.find((p) => p[0] === t)?.[1] ?? 0
const pctText = (v: number) => `${Math.round(v)} %`
const joinEs = (items: string[]) => (items.length === 0 ? '' : items.length === 1 ? items[0] : items.slice(0, -1).join(', ') + ' y ' + items[items.length - 1])
const lower = (t: SpendTag) => SPEND_TAG_INFO[t].label.toLowerCase()

// ---------- 7. ¿Dónde se ha ido mi dinero? ----------

export interface MonthStory {
  /** "este mes" o "en octubre". */
  label: string
  incomeNow: number
  incomePrev: number
  spentNow: number
  spentPrev: number
  /** Diferencia por tipo de gasto (positivo = más que antes), de mayor a menor. */
  changes: Array<[SpendTag, number]>
}

export interface TimePoint {
  month: YearMonth
  netWorth: number | null
  spent: number
  received: number
  byTag: Array<[SpendTag, number]>
  /** Lo prescindible (calculado). */
  wants: number
}
export interface TimeTravel {
  then: TimePoint
  now: TimePoint
  netWorthNow: number
  /** null si no hay foto de entonces (calculado). */
  netWorthChange: number | null
}
export interface CostOfLiving {
  fixed: number
  yearlySpread: number
  variable: number
  forgotten: number
  thisMonthPace: number | null
  /** fixed + yearlySpread + variable + forgotten (calculado). */
  monthly: number
  /** monthly × 12 (calculado). */
  yearly: number
}
export interface RadarItem { name: string; amount: number; due: LocalDate; fromHistory: boolean }
export function radarMonthsLeft(r: RadarItem, today: LocalDate): number { return Math.max(1, Math.trunc((ep(r.due) - ep(today)) / 30.4)) }
export function radarMonthlyReserve(r: RadarItem, today: LocalDate): number { return r.amount / radarMonthsLeft(r, today) }

export interface Habits {
  /** Media por día de lunes a domingo. */
  byWeekday: number[]
  weekdayAvg: number
  weekendAvg: number
  afterPaydayAvg: number | null
  otherDaysAvg: number
  /** Media por día a principios (1–10), mediados (11–20) y finales de mes. */
  thirds: number[]
  days: number
  notes: string[]
}
export interface WhatIf { title: string; amount: number; detail: string }
export type Status = 'GOOD' | 'OK' | 'BAD'
export const STATUS_VALUES: Status[] = ['GOOD', 'OK', 'BAD']
export interface Indicator { name: string; value: string; status: Status; explanation: string; advice: string }
export interface HealthReport {
  indicators: Indicator[]
  trend: string | null
  nextStep: string
  /** Indicadores en verde (calculado). */
  good: number
}
export interface ShareCandidate { expense: Expense; reason: string }
export interface SimBase {
  income: number; cost: number; savings: number; rent: number; fixedIncome: number
  /** income − cost (calculado). */
  monthlySaving: number
}
export interface SimResult {
  savingNow: number; savingAfter: number
  in1yNow: number; in1yAfter: number
  in5yNow: number; in5yAfter: number
  emergencyNow: number; emergencyAfter: number
  /** Meses hasta quedarte sin ahorro, si pasas a perder dinero cada mes. */
  runwayMonths: number | null
}

function timePoint(month: YearMonth, netWorth: number | null, spent: number, received: number, tags: Array<[SpendTag, number]>): TimePoint {
  return { month, netWorth, spent, received, byTag: tags, wants: sumOf(tags.filter((p) => !SPEND_TAG_INFO[p[0]].need), (p) => p[1]) }
}
function costOfLivingOf(fixedV: number, yearlySpread: number, variable: number, forgotten: number, thisMonthPace: number | null): CostOfLiving {
  const monthly = fixedV + yearlySpread + variable + forgotten
  return { fixed: fixedV, yearlySpread, variable, forgotten, thisMonthPace, monthly, yearly: monthly * 12 }
}
const indicator = (name: string, value: string, status: Status, explanation: string, advice: string): Indicator => ({ name, value, status, explanation, advice })

const YEARLY_TAGS: SpendTag[] = ['HOME', 'TRANSPORT', 'GIFTS', 'TRAVEL', 'EDUCATION', 'HEALTH', 'PETS', 'SUBSCRIPTIONS', 'OTHER']
const SHARED_HOME = ['luz', 'agua', 'gas', 'internet', 'fibra', 'alquiler', 'comunidad', 'limpieza']
const SHARED_SUBS = ['netflix', 'spotify', 'hbo', 'disney', 'prime', 'youtube', 'dazn', 'filmin', 'skyshowtime']

export const Insights = {
  /**
   * Este mes hasta hoy frente al mismo tramo del anterior. En los primeros días del mes,
   * cuenta mejor el último mes completo frente al anterior.
   */
  monthStory(d: FinanceData): MonthStory {
    const earlyInMonth = d.today.day <= 7 && d.spentIn(ymMinus(d.thisMonth, 1)) > 0
    let nowFrom: LocalDate, nowTo: LocalDate, prevFrom: LocalDate, prevTo: LocalDate, label: string
    if (earlyInMonth) {
      const last = ymMinus(d.thisMonth, 1); const before = ymMinus(last, 1)
      ;[nowFrom, nowTo, prevFrom, prevTo, label] = [atDay(last, 1), atEndOfMonth(last), atDay(before, 1), atEndOfMonth(before), `en ${monthName(last)}`]
    } else {
      const prev = ymMinus(d.thisMonth, 1)
      ;[nowFrom, nowTo, prevFrom, prevTo, label] = [atDay(d.thisMonth, 1), d.today, atDay(prev, 1), atDay(prev, Math.min(d.today.day, ymLen(prev))), 'este mes']
    }
    const inRange = (day: number, a: LocalDate, b: LocalDate) => day >= ep(a) && day <= ep(b)
    const now = d.spending.filter((e) => inRange(e.epochDay, nowFrom, nowTo))
    const prev = d.spending.filter((e) => inRange(e.epochDay, prevFrom, prevTo))
    const nowTags = byTag(now); const prevTags = byTag(prev)
    const changes = sortedByDesc(
      SPEND_TAG_VALUES.map((t): [SpendTag, number] => [t, amountOf(nowTags, t) - amountOf(prevTags, t)]).filter((p) => Math.abs(p[1]) >= 1),
      (p) => p[1],
    )
    return {
      label,
      incomeNow: sumOf(d.incomes.filter((x) => inRange(x.epochDay, nowFrom, nowTo)), (x) => x.amount),
      incomePrev: sumOf(d.incomes.filter((x) => inRange(x.epochDay, prevFrom, prevTo)), (x) => x.amount),
      spentNow: sumOf(now, (e) => e.amount), spentPrev: sumOf(prev, (e) => e.amount), changes,
    }
  },

  /** "Has cobrado lo mismo que el mes pasado, pero has gastado 184 € más: 72 € en restaurantes…" */
  describe(s: MonthStory, fmt: (v: number) => string): string {
    const incomeDiff = s.incomeNow - s.incomePrev
    const spentDiff = s.spentNow - s.spentPrev
    const subject = capFirst(s.label)
    const income =
      s.incomeNow <= 0 && s.incomePrev <= 0 ? `${subject} no hay cobros apuntados`
        : Math.abs(incomeDiff) < Math.max(10, s.incomePrev * 0.03) ? `${subject} has cobrado lo mismo que el mes anterior`
          : incomeDiff > 0 ? `${subject} has cobrado ${fmt(incomeDiff)} más que el mes anterior`
            : `${subject} has cobrado ${fmt(-incomeDiff)} menos que el mes anterior`
    const ups = s.changes.filter((p) => p[1] > 0).slice(0, 3).map((p) => `${fmt(p[1])} en ${lower(p[0])}`)
    const downs = [...s.changes.filter((p) => p[1] < 0)].sort((a, b) => a[1] - b[1]).slice(0, 2).map((p) => `${fmt(-p[1])} en ${lower(p[0])}`)
    const spent =
      Math.abs(spentDiff) < 5 ? ' y has gastado prácticamente lo mismo.'
        : spentDiff > 0 ? (incomeDiff >= 0 ? ', pero' : ' y') + ` has gastado ${fmt(spentDiff)} más` + (ups.length > 0 ? `: ${joinEs(ups)}.` : '.')
          : ` y has gastado ${fmt(-spentDiff)} menos` + (downs.length > 0 ? `, sobre todo ${joinEs(downs)}.` : '.') + ' ¡Bien!'
    return income + spent
  },

  // ---------- 8. Máquina del tiempo ----------

  /** Patrimonio al final de cada mes (la última foto de ese mes); el mes actual, el de hoy. */
  netWorthByMonth(d: FinanceData, months = 12): Array<[YearMonth, number]> {
    const out: Array<[YearMonth, number]> = []
    for (let back = months - 1; back >= 1; back--) {
      const m = ymMinus(d.thisMonth, back)
      const snaps = d.snapshots.filter((s) => ymEq(ymOfDay(s.epochDay), m))
      const last = snaps[snaps.length - 1]
      if (last) out.push([m, last.total])
    }
    out.push([d.thisMonth, d.wealth.total])
    return out
  },

  /** Cómo estabas hace `monthsBack` meses frente a ahora (último mes cerrado para los gastos). */
  timeTravel(d: FinanceData, monthsBack: number): TimeTravel {
    const thenMonth = ymMinus(d.thisMonth, monthsBack)
    const thenEnd = monthsBack === 0 ? d.today : atEndOfMonth(thenMonth)
    const matching = d.snapshots.filter((s) => !isAfter(date(s.epochDay), thenEnd) && !isBefore(date(s.epochDay), minusD(thenEnd, 45)))
    const snapshot = matching[matching.length - 1]
    const nowMonth = d.closedMonths(1)[0] ?? d.thisMonth
    const point = (m: YearMonth, nw: number | null) => timePoint(m, nw, d.spentIn(m), d.receivedIn(m), byTag(d.spendingIn(m)))
    const then = point(thenMonth, snapshot?.total ?? null)
    return { then, now: point(nowMonth, d.wealth.total), netWorthNow: d.wealth.total, netWorthChange: then.netWorth != null ? d.wealth.total - then.netWorth : null }
  },

  /** Meses que se puede viajar atrás: desde la primera foto de patrimonio o el primer movimiento. */
  earliestMonthsBack(d: FinanceData): number {
    const cands = [d.snapshots[0] ? ymOfDay(d.snapshots[0].epochDay) : null, d.firstMonth].filter((x): x is YearMonth => x != null)
    const first = minBy(cands, ymIdx)
    if (!first) return 0
    return Math.max((d.thisMonth.year - first.year) * 12 + d.thisMonth.month - first.month, 0)
  },

  describeTimeTravel(t: TimeTravel, monthsBack: number, fmt: (v: number) => string): string[] {
    const out: string[] = []
    const when = monthsBack === 1 ? 'Hace un mes' : monthsBack % 12 === 0 ? `Hace ${Math.trunc(monthsBack / 12)} año${monthsBack > 12 ? 's' : ''}` : `Hace ${monthsBack} meses`
    const change = t.netWorthChange
    if (t.then.netWorth != null && change != null) {
      out.push(`${when} tenías ${fmt(t.then.netWorth)}; hoy tienes ${fmt(t.netWorthNow)}` + (
        change > 1 ? ` (${fmt(change)} más). Estás mejor que entonces.`
          : change < -1 ? ` (${fmt(-change)} menos). Has ido a menos.`
            : '. Estás igual.'))
    } else out.push(`No hay foto de tu patrimonio de ${monthName(t.then.month)} ${t.then.month.year}: Ahorra, vaca la guarda cada día que abres la app.`)
    if (t.then.spent > 0 && t.now.spent > 0) {
      const diff = t.now.spent - t.then.spent
      out.push(`En ${monthName(t.then.month)} gastaste ${fmt(t.then.spent)}; en ${monthName(t.now.month)}, ${fmt(t.now.spent)}` +
        (Math.abs(diff) < 10 ? '.' : diff > 0 ? ` (${fmt(diff)} más).` : ` (${fmt(-diff)} menos).`))
      const wantsDiff = t.now.wants - t.then.wants
      if (t.then.wants > 0 || t.now.wants > 0) {
        out.push(`En ocio y caprichos pasaste de ${fmt(t.then.wants)} a ${fmt(t.now.wants)} al mes` +
          (wantsDiff > 10 ? '.' : wantsDiff < -10 ? ': has recortado.' : ': igual que entonces.'))
      }
    }
    if (t.then.received > 0 && t.now.received > 0) {
      const r = (t.now.received - t.then.received) / t.then.received * 100
      if (Math.abs(r) >= 3) out.push(`Cobras un ${pctText(Math.abs(r))} ${r > 0 ? 'más' : 'menos'} que entonces.`)
    }
    return out
  },

  // ---------- 12. Tu coste de vida real ----------

  costOfLiving(d: FinanceData): CostOfLiving {
    const fixedV = sumOf(d.bills.filter((b) => !b.yearly), (b) => b.amount)
    const yearly = sumOf(d.bills.filter((b) => b.yearly), (b) => b.amount) / 12
    // Lo variable: gastos que no son fijos, de media en los meses cerrados (máx. 3).
    const closed = d.closedMonths(3).map((m) => sumOf(d.spendingIn(m).filter((e) => e.billId == null), (e) => e.amount)).filter((v) => v > 0)
    const currentVar = sumOf(d.spendingIn(d.thisMonth).filter((e) => e.billId == null), (e) => e.amount)
    const len = ymLen(d.thisMonth)
    const pace = d.today.day >= 7 ? currentVar / d.today.day * len : null
    const variable = closed.length > 0 ? average(closed)
      : pace != null ? pace
        : Forecasting.dailyVariableSpend(d.expenses, d.today, d.savingIds) * 30
    const forgotten = sumOf(Insights.radar(d, 365).filter((r) => r.fromHistory), (r) => r.amount) / 12
    const thisMonthPace = d.today.day >= 7 ? d.spentIn(d.thisMonth) / d.today.day * len : null
    return costOfLivingOf(fixedV, yearly, variable, forgotten, thisMonthPace)
  },

  // ---------- 17. Radar de gastos olvidados ----------

  /** Pagos poco frecuentes que vienen: gastos fijos anuales y lo que pagaste hace casi un año. */
  radar(d: FinanceData, horizonDays = 120): RadarItem[] {
    const until = ep(d.today) + horizonDays
    const fromBills: RadarItem[] = []
    for (const b of d.bills.filter((x) => x.yearly)) {
      let due = Bills.dueDate(b, { year: d.today.year, month: b.month })!
      if (isBefore(due, d.today) || Bills.isPaid(b, ymOfDate(due), d.expenses)) due = Bills.dueDate(b, { year: d.today.year + 1, month: b.month })!
      if (ep(due) <= until) fromBills.push({ name: b.name, amount: b.amount, due, fromHistory: false })
    }
    const billNames = new Set(d.bills.map((b) => Classifier.normalize(b.name)))
    const fromHistoryAll: RadarItem[] = []
    for (const e of d.spending) {
      if (!(e.billId == null && e.amount >= 40 && YEARLY_TAGS.includes(spendTag(e)))) continue
      const nn = Classifier.normalize(e.note)
      if (!(nn !== '' && !billNames.has(nn))) continue
      const next = plusY(date(e.epochDay), 1)
      if (isBefore(next, d.today) || ep(next) > until) continue
      // Si lo pagas a menudo (cada mes), no es un gasto olvidado.
      const repeatedSince = d.spending.some((o) => o.id !== e.id && Classifier.normalize(o.note) === nn && o.epochDay > e.epochDay + 20)
      if (!repeatedSince) fromHistoryAll.push({ name: e.note, amount: e.amount, due: next, fromHistory: true })
    }
    const fromHistory = distinctBy(fromHistoryAll, (r) => Classifier.normalize(r.name))
    return [...fromBills, ...fromHistory].sort((a, b) => ep(a.due) - ep(b.due))
  },

  // ---------- 18. Mapa de hábitos ----------

  habits(d: FinanceData, fmt: (v: number) => string, window = 90): Habits | null {
    const t = ep(d.today)
    const variable = d.spending.filter((e) => !Budgeting.isFixed(e, d.bills) && e.epochDay <= t)
    const first = minOf(variable, (e) => e.epochDay)
    if (first == null) return null
    const start = Math.max(first, t - window + 1)
    const days = t - start + 1
    if (days < 14) return null
    const perDay = new Map<number, number>()
    for (const e of variable.filter((x) => x.epochDay >= start)) perDay.set(e.epochDay, (perDay.get(e.epochDay) ?? 0) + e.amount)
    const rangeDays = range(start, t)
    const avg = (filter: (x: LocalDate) => boolean) => {
      const ds = rangeDays.filter((x) => filter(date(x)))
      return ds.length === 0 ? 0 : sumOf(ds, (x) => perDay.get(x) ?? 0) / ds.length
    }
    const byWeekday = range(1, 7).map((w) => avg((x) => dow(x) === w))
    const weekend = avg((x) => dow(x) === 6 || dow(x) === 7)
    const weekday = avg((x) => dow(x) !== 6 && dow(x) !== 7)
    const paydays = d.incomes.filter((x) => x.sourceId != null).map((x) => x.epochDay)
    const afterPayday = (day: LocalDate) => paydays.some((p) => { const diff = ep(day) - p; return diff >= 0 && diff <= 6 })
    const hasPaydays = rangeDays.some((x) => afterPayday(date(x)))
    const afterPay = hasPaydays ? avg(afterPayday) : null
    const other = avg((x) => !afterPayday(x))
    const thirds = [avg((x) => x.day <= 10), avg((x) => x.day >= 11 && x.day <= 20), avg((x) => x.day > 20)]

    const notes: string[] = []
    if (weekday > 0 && weekend > weekday * 1.25) notes.push(`Los fines de semana gastas un ${pctText((weekend / weekday - 1) * 100)} más al día (${fmt(weekend)} frente a ${fmt(weekday)}).`)
    else if (weekend > 0 && weekday > weekend * 1.25) notes.push(`Entre semana gastas más que el fin de semana (${fmt(weekday)} al día frente a ${fmt(weekend)}).`)
    const topIdx = maxBy(range(0, 6), (i) => byWeekday[i])
    if (topIdx != null && byWeekday[topIdx] > 0) notes.push(`Tu día más caro es el ${weekdayName(topIdx + 1)}: ${fmt(byWeekday[topIdx])} de media.`)
    if (afterPay != null && other > 0 && afterPay > other * 1.3) notes.push(`La semana después de cobrar gastas un ${pctText((afterPay / other - 1) * 100)} más al día. Aparta el ahorro nada más cobrar.`)
    const names = ['principios', 'mediados', 'finales']
    const maxThird = maxBy([0, 1, 2], (i) => thirds[i])!
    const minThird = minBy([0, 1, 2], (i) => thirds[i])!
    if (thirds[minThird] > 0 && thirds[maxThird] > thirds[minThird] * 1.4) notes.push(`Gastas más a ${names[maxThird]} de mes y menos a ${names[minThird]}.`)
    if (notes.length === 0) notes.push('Tu gasto es bastante regular: no hay días ni épocas que se disparen.')
    return { byWeekday, weekdayAvg: weekday, weekendAvg: weekend, afterPaydayAvg: afterPay, otherDaysAvg: other, thirds, days, notes }
  },

  // ---------- 19. El contrafactual de ahorro ----------

  whatIfs(d: FinanceData, monthly: number, fmt: (v: number) => string): WhatIf[] {
    const first = d.firstMonth
    if (!first) return []
    const months = Math.max(1, (d.thisMonth.year - first.year) * 12 + d.thisMonth.month - first.month)
    const bestRate = d.accounts.length === 0 ? 0 : Math.max(...d.accounts.map((a) => a.interestRate))
    const past = InterestMath.project(0, bestRate, monthly, months)
    const out: WhatIf[] = [{
      title: `Si hubieras reservado ${fmt(monthly)} cada mes desde ${monthName(first)}`,
      amount: past.finalBalance,
      detail: `Tendrías ${fmt(past.finalBalance)} más` + (bestRate > 0 ? `, con ${fmt(past.interest)} de intereses al ${fixed(bestRate, 1)} %.` : '.'),
    }]
    const wants = byTag(d.spending.filter((e) => !ymBefore(ymOfDay(e.epochDay), first))).filter((p) => !SPEND_TAG_INFO[p[0]].need && p[0] !== 'OTHER')
    const top = wants[0]
    if (top) {
      const [tag, total] = top
      out.push({ title: `Si hubieras gastado un 20 % menos en ${lower(tag)}`, amount: total * 0.2, detail: `Habrías guardado ${fmt(total * 0.2)} desde ${monthName(first)} sin dejar de hacerlo.` })
    }
    const future = InterestMath.project(0, bestRate, monthly, 60)
    out.push({ title: `Si empiezas hoy con ${fmt(monthly)} al mes`, amount: future.finalBalance, detail: `En 5 años tendrías ${fmt(future.finalBalance)}` + (bestRate > 0 ? ` (${fmt(future.interest)} de intereses).` : '.') })
    return out
  },

  // ---------- 20. Informe de salud financiera ----------

  health(d: FinanceData, fmt: (v: number) => string): HealthReport {
    const list: Indicator[] = []
    const closed = d.closedMonths(3).filter((m) => d.receivedIn(m) > 0)
    const rate = (months: YearMonth[]): number | null => {
      const inc = sumOf(months, (m) => d.receivedIn(m))
      return inc <= 0 ? null : (inc - sumOf(months, (m) => d.spentIn(m))) / inc * 100
    }
    const savingRate = rate(closed) ?? rate([d.thisMonth])
    list.push(savingRate == null
      ? indicator('Capacidad de ahorro', 'Sin datos', 'OK', 'Apunta tus cobros para saber qué parte de lo que entra te queda.', 'Marca tus cobros como "Cobrado" en Gastos.')
      : indicator(
        'Capacidad de ahorro', pctText(savingRate),
        savingRate >= 20 ? 'GOOD' : savingRate >= 5 ? 'OK' : 'BAD',
        `De lo que entra, te queda un ${pctText(savingRate)}` + (closed.length > 0 ? ` (media de ${closed.length} ${closed.length === 1 ? 'mes' : 'meses'}).` : ' este mes.'),
        savingRate >= 20 ? 'Vas bien: automatiza el ahorro el día que cobras.' : 'Intenta llegar al 20 %: empieza recortando tu tipo de gasto prescindible más alto.',
      ))

    const prev = d.closedMonths(6).slice(3).filter((m) => d.receivedIn(m) > 0)
    const before = rate(prev)
    const trend = before == null || savingRate == null ? null
      : savingRate > before + 3 ? `Mejorando: ahorras un ${pctText(savingRate - before)} más que hace unos meses.`
        : savingRate < before - 3 ? `Empeorando: ahorras un ${pctText(before - savingRate)} menos que hace unos meses.`
          : 'Estable respecto a hace unos meses.'

    const nets = d.closedMonths(6).map((m) => d.receivedIn(m) - d.spentIn(m))
    const red = nets.filter((v) => v < 0).length
    list.push(nets.length === 0
      ? indicator('Estabilidad', 'Pronto', 'OK', 'Cuando cierres tu primer mes verás si entra más de lo que sale de forma regular.', 'Sigue apuntando tus movimientos este mes.')
      : indicator(
        'Estabilidad', `${nets.length - red} de ${nets.length} meses en verde`,
        red === 0 ? 'GOOD' : red === 1 ? 'OK' : 'BAD',
        red === 0 ? 'Todos tus meses cierran con más entrada que salida.' : `${red} ${red === 1 ? 'mes ha cerrado' : 'meses han cerrado'} gastando más de lo que entró.`,
        'Ten un pequeño colchón en la cuenta del día a día para los meses irregulares.',
      ))

    const income = d.monthlyIncome
    const fixedV = sumOf(d.bills, (b) => Bills.monthly(b))
    const fixedPct = income > 0 ? fixedV / income * 100 : null
    list.push(fixedPct == null
      ? indicator('Peso de los gastos fijos', fmt(fixedV) + '/mes', 'OK', 'Apunta tus ingresos para saber cuánto de tu sueldo se va en fijos.', 'Añade tus ingresos en Gastos.')
      : indicator(
        'Peso de los gastos fijos', pctText(fixedPct),
        fixedPct <= 35 ? 'GOOD' : fixedPct <= 50 ? 'OK' : 'BAD',
        `Tus gastos fijos (${fmt(fixedV)} al mes) se llevan un ${pctText(fixedPct)} de lo que ganas.`,
        fixedPct <= 35 ? 'Margen sano para imprevistos.' : 'Revisa suscripciones, tarifa de luz o móvil: cada fijo que bajas es ahorro todos los meses.',
      ))

    const cost = Insights.costOfLiving(d).monthly
    const months = cost > 0 ? d.wealth.savings / cost : null
    list.push(months == null
      ? indicator('Colchón de emergencia', fmt(d.wealth.savings), 'OK', 'Cuando haya gastos apuntados calcularemos cuántos meses te cubre.', 'Apunta tus gastos fijos.')
      : indicator(
        'Colchón de emergencia', `${fixed(months, 1)} meses`,
        months >= 3 ? 'GOOD' : months >= 1 ? 'OK' : 'BAD',
        `Tu ahorro (${fmt(d.wealth.savings)}) cubriría ${fixed(months, 1)} meses de tu coste de vida.`,
        months >= 3 ? 'Cubierto. Lo que pase de 6 meses puede trabajar en inversiones.' : `Objetivo: 3 meses (${fmt(cost * 3)}). Aparta un poco cada mes en una cuenta remunerada.`,
      ))

    const debts = d.wealth.debts
    const debtRatio = income > 0 ? debts / (income * 12) * 100 : null
    list.push(indicator(
      'Deudas', debts <= 0 ? 'Sin deudas' : fmt(debts),
      debts <= 0 ? 'GOOD' : debtRatio != null && debtRatio <= 35 ? 'OK' : 'BAD',
      debts <= 0 ? 'No tienes préstamos ni deudas apuntados.' : `Debes ${fmt(debts)}` + (debtRatio != null ? `, un ${pctText(debtRatio)} de lo que ganas en un año.` : '.'),
      debts <= 0 ? 'Mantenlo así.' : 'Paga primero la deuda con el interés más alto.',
    ))

    const worst = list.find((i) => i.status === 'BAD') ?? list.find((i) => i.status === 'OK')
    const next = worst ? `${worst.name}: ${worst.advice}` : 'Todo en orden. El siguiente paso: invertir lo que te sobre cada mes a largo plazo.'
    return { indicators: list, trend, nextStep: next, good: list.filter((i) => i.status === 'GOOD').length }
  },

  // ---------- 11. Gastos que se pueden compartir ----------

  shareable(d: FinanceData, days = 60): ShareCandidate[] {
    const since = ep(d.today) - days
    const split = new Set(d.splits.map((s) => s.expenseId))
    const out: ShareCandidate[] = []
    for (const e of d.spending.filter((x) => x.epochDay >= since && !split.has(x.id))) {
      const n = ` ${Classifier.normalize(e.note)} `
      let reason: string | null = null
      switch (spendTag(e)) {
        case 'HOME': reason = SHARED_HOME.some((w) => n.includes(` ${w} `)) ? 'Factura de casa: ¿la compartes con quien vives?' : null; break
        case 'SUBSCRIPTIONS': reason = SHARED_SUBS.some((w) => n.includes(` ${w} `)) ? 'Plataforma que se puede compartir' : null; break
        case 'TRAVEL': reason = 'Viaje: ¿ibais varios?'; break
        case 'FOOD': reason = e.amount >= 35 || n.includes(' amigos ') ? 'Comida o cena en grupo' : null; break
        case 'SUPER': reason = e.amount >= 60 ? 'Compra grande: ¿es para la casa?' : null; break
        case 'LEISURE': reason = n.includes(' amigos ') || n.includes(' entradas ') ? 'Plan con amigos' : null; break
        default: reason = null
      }
      if (reason) out.push({ expense: e, reason })
    }
    return sortedByDesc(out, (c) => c.expense.epochDay).slice(0, 6)
  },

  /** Lo que te deben de gastos repartidos aún sin saldar. */
  owed(splits: Split[], expenses: Expense[]): number {
    return sumOf(splits, (s) => {
      const e = expenses.find((x) => x.id === s.expenseId)
      if (!e) return 0
      return e.amount / s.people * Math.max(s.people - 1 - s.settled, 0)
    })
  },

  // ---------- 13. Simulador de decisiones vitales ----------

  simBase(d: FinanceData): SimBase {
    const rent = sumOf(d.bills.filter((b) => { const n = Classifier.normalize(b.name); return n.includes('alquiler') || n.includes('hipoteca') }), (b) => Bills.monthly(b))
    const income = d.monthlyIncome
    const cost = Insights.costOfLiving(d).monthly
    return { income, cost, savings: d.wealth.savings + d.wealth.available, rent, fixedIncome: sumOf(d.sources.filter((s) => !s.variable), (s) => s.amount), monthlySaving: income - cost }
  },

  simulate(base: SimBase, incomeDelta: number, costDelta: number, upfront: number): SimResult {
    const now = base.monthlySaving
    const after = base.income + incomeDelta - (base.cost + costDelta)
    const start = base.savings - upfront
    const costAfter = base.cost + costDelta
    return {
      savingNow: now, savingAfter: after,
      in1yNow: base.savings + now * 12, in1yAfter: start + after * 12,
      in5yNow: base.savings + now * 60, in5yAfter: start + after * 60,
      emergencyNow: base.cost > 0 ? base.savings / base.cost : 0,
      emergencyAfter: costAfter > 0 ? start / costAfter : 0,
      runwayMonths: after < 0 && start > 0 ? start / -after : null,
    }
  },
}
