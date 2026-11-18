// Port de domain/CashPlan.kt: tu dinero del banco hasta la próxima nómina.
import type { LocalDate } from '../data/dates'
import { plusMonths } from '../data/dates'
import { atDayClamped, daysBetween, isAfter, isBefore, maxBy, plusD, range, sumOf, ymEq, ymOfDate, ymOfDay } from './kt'
import { IncomeMath } from './FinanceMath'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'

/** Una entrada (+) o salida (−) de dinero en el banco antes de la nómina. */
export interface CashEvent { date: LocalDate; label: string; amount: number; estimated: boolean }

/**
 * Tu dinero del banco hasta la próxima nómina: cuánto puedes gastar al día para llegar con el colchón
 * que elijas, contando los recibos que vienen (alquiler…) y lo que entra antes (YouTube el 22…).
 * Si hasta que llega el ingreso grande vas más justo, se divide en dos tramos.
 * `days` y `shortfall` (calculadas en Kotlin) se rellenan al crearlo.
 */
export interface CashPlan {
  today: LocalDate
  payday: LocalDate
  paydayName: string
  balance: number
  cushion: number
  events: CashEvent[]
  /** Gasto diario permitido en el primer tramo (o en todo el periodo si no hay dos). */
  perDayBefore: number
  /** Gasto diario del segundo tramo (desde splitDate); null si es un solo tramo. */
  perDayAfter: number | null
  splitDate: LocalDate | null
  /** Saldo previsto cada día (gastando lo permitido): [día, saldo]. */
  series: Array<[LocalDate, number]>
  days: number
  /** Si no se llega con el colchón: cuánto falta. */
  shortfall: number
}

export const CashPlanner = {
  of(d: FinanceData, cushion: number): CashPlan | null {
    const today = d.today
    // Liquidez = solo las cuentas del banco (tipo BANK).
    const bank = d.accounts.filter((a) => a.type === 'BANK')
    if (bank.length === 0) return null
    const bankIds = new Set(bank.map((a) => a.id))
    const balance = sumOf(bank, (a) => a.balance)

    // La nómina: el ingreso fijo más grande. Próximo cobro que aún no ha llegado.
    const salary = maxBy(d.sources.filter((s) => !s.variable), (s) => s.amount)
    if (!salary) return null
    const m = ymOfDate(today)
    const received = (sourceId: number, ym: { year: number; month: number }) => d.incomes.some((i) => i.sourceId === sourceId && ymEq(ymOfDay(i.epochDay), ym))
    const thisPay = atDayClamped(m, salary.dayFrom)
    const payday = !received(salary.id, m) && !isBefore(thisPay, today) ? thisPay : atDayClamped(plusMonths(m, 1), salary.dayFrom)
    if (!isAfter(payday, today)) return null

    const events: CashEvent[] = []
    // Otros cobros antes de la nómina (YouTube el 22, el resto de la nómina…).
    IncomeMath.forMonth(d.sources, d.incomes, m).sources.filter((sm) => sm.source.id !== salary.id && !sm.isReceived && sm.amount > 0).forEach((sm) => {
      const s = sm.source
      if (s.accountId != null && !bankIds.has(s.accountId)) return
      const day = atDayClamped(m, s.dayFrom)
      const dt = isBefore(day, today) ? today : day
      if (isBefore(dt, payday)) {
        const amount = s.variable && sm.confirmed == null ? Math.floor(sm.amount * 0.7 / 5) * 5 : sm.amount
        events.push({ date: dt, label: s.name, amount, estimated: s.variable && sm.confirmed == null })
      }
    })
    // Recibos que se pagan antes de la nómina y aún no están pagados.
    d.bills.filter((b) => b.accountId == null || bankIds.has(b.accountId)).forEach((b) => {
      let ym = m
      for (let k = 0; k < 2; k++) {
        const due = atDayClamped(ym, b.day)
        const applies = !b.yearly || b.month === ym.month
        const cur = ym
        const paid = d.expenses.some((e) => ymEq(ymOfDay(e.epochDay), cur) && Budgeting.isBillPayment(e, [b]))
        if (applies && !paid && isBefore(due, payday)) {
          events.push({ date: isBefore(due, today) ? today : due, label: b.name, amount: -b.amount, estimated: false })
        }
        ym = plusMonths(ym, 1)
      }
    })
    events.sort((a, b) => daysBetween(b.date, a.date))

    const days = daysBetween(today, payday)
    const dayIndex = (x: LocalDate) => daysBetween(today, x)
    const net = (upTo: number) => sumOf(events.filter((e) => dayIndex(e.date) <= upTo), (e) => e.amount)
    const total = balance + sumOf(events, (e) => e.amount) - cushion

    // Un solo ritmo: lo que sobra repartido entre los días. Hay que comprobar que antes del ingreso grande no se baja del colchón.
    const uniform = total / days
    const big = maxBy(events.filter((e) => e.amount > 0), (e) => e.amount)
    let before = uniform
    let after: number | null = null
    let split: LocalDate | null = null
    const firstOk = range(0, days - 1).every((i) => balance + net(i) - uniform * (i + 1) >= cushion - 0.01)
    if (!firstOk && big != null && dayIndex(big.date) >= 1 && dayIndex(big.date) < days) {
      const k = dayIndex(big.date)
      before = Math.min(Math.min(...range(0, k - 1).map((i) => (balance + net(i) - cushion) / (i + 1))), uniform)
      const rest = total - before * k
      after = rest / (days - k)
      split = big.date
    } else if (!firstOk) {
      before = Math.min(...range(0, days - 1).map((i) => (balance + net(i) - cushion) / (i + 1)))
    }
    const splitIdx = split ? dayIndex(split) : null
    const rate = (i: number) => (splitIdx != null && i >= splitIdx ? after! : before)
    let bal = balance
    const series = range(0, days).map((i): [LocalDate, number] => {
      if (i > 0) bal -= Math.max(0, rate(i - 1))
      bal += sumOf(events.filter((e) => dayIndex(e.date) === i), (e) => e.amount)
      return [plusD(today, i), bal]
    })
    return {
      today, payday, paydayName: salary.name, balance, cushion, events, perDayBefore: before, perDayAfter: after, splitDate: split, series,
      days: Math.max(days, 1), shortfall: before < 0 ? -before * Math.max(days, 1) : 0,
    }
  },
}
