// Port de domain/Wrapped.kt: el "Wrapped" de un mes, contado como tarjetas.
import type { Expense, Transfer } from '../data/models'
import type { LocalDate, YearMonth } from '../data/dates'
import { thisMonth } from '../data/dates'
import { atDay, date, dow, groupBy, maxBy, range, sumOf, ymAfter, ymBefore, ymEq, ymLen, ymMinus, ymOfDate } from './kt'
import { spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'

/**
 * El "Wrapped" de un mes: tus números contados como tarjetas. Todo sale de tus datos, sin IA.
 * `left`, `perDay`, `isCurrent` y `vsPrevPct` (calculadas en Kotlin) se rellenan al crearlo.
 */
export interface MonthWrapped {
  month: YearMonth
  income: number
  spent: number
  /** Gasto de cada día del mes (los días que aún no han llegado, 0). */
  daily: number[]
  /** Días del mes que ya han pasado (todo el mes si está cerrado). */
  daysElapsed: number
  payments: number
  /** [tipo, total, % del gasto]. */
  topTag: [SpendTag, number, number] | null
  /** Comercio donde más has gastado: [nombre, total, veces]. */
  topPlace: [string, number, number] | null
  biggest: Expense | null
  /** [día de la semana 1 = lunes … 7 = domingo, total]. */
  priciestWeekday: [number, number] | null
  noSpendDays: number
  topDay: [LocalDate, number] | null
  /** Gasto del mes anterior en los mismos días (null si no hay datos). */
  prevSpent: number | null
  saved: number
  /** Saldo de tus cuentas corrientes ahora (tu liquidez; sin ahorros ni inversiones). */
  bank: number
  /** Alquiler y fijos del mes (de una vez al mes): aparte, no cuentan en lo gastado ni en la media. */
  fixed: number
  left: number
  perDay: number
  isCurrent: boolean
  /** Cambio frente al mes anterior en %, solo si hay base. */
  vsPrevPct: number | null
}

export const Wrapped = {
  of(d: FinanceData, month: YearMonth, transfers: Transfer[] = []): MonthWrapped {
    const today = d.today
    const len = ymLen(month)
    const tm = ymOfDate(today)
    const elapsed = ymEq(month, tm) ? today.day : ymAfter(month, tm) ? 0 : len
    const all = d.spendingIn(month)
    const spending = all.filter((e) => !Budgeting.isFixed(e, d.bills))
    const spent = sumOf(spending, (e) => e.amount)
    const daily = range(1, len).map((day) => sumOf(spending.filter((e) => date(e.epochDay).day === day), (e) => e.amount))
    const tags = maxBy([...groupBy(spending, spendTag).entries()].map(([k, l]): [SpendTag, number] => [k, sumOf(l, (e) => e.amount)]), (p) => p[1])
    const place = maxBy([...groupBy(spending.filter((e) => e.note.trim() !== ''), (e) => e.note.trim().toLowerCase()).values()]
      .map((l): [string, number, number] => [l[0].note.trim(), sumOf(l, (e) => e.amount), l.length]), (t) => t[1])
    const weekday = maxBy([...groupBy(spending, (e) => dow(date(e.epochDay))).entries()].map(([k, l]): [number, number] => [k, sumOf(l, (e) => e.amount)]), (p) => p[1])
    const topIdx = maxBy(range(0, daily.length - 1), (i) => daily[i])
    const topDayIdx = topIdx != null && daily[topIdx] > 0 ? topIdx : null
    const prevMonth = ymMinus(month, 1)
    const prevList = d.spendingIn(prevMonth).filter((e) => !Budgeting.isFixed(e, d.bills)).filter((e) => date(e.epochDay).day <= elapsed)
    const hasPrev = d.firstMonth != null && !ymBefore(prevMonth, d.firstMonth)
    const savingAccounts = new Set(d.accounts.filter((a) => a.type === 'BANK_SAVINGS' || a.type === 'HOME_SAVINGS' || a.type === 'FUND').map((a) => a.id))
    const savedByTransfer = sumOf(transfers.filter((t) => savingAccounts.has(t.toAccountId) && !savingAccounts.has(t.fromAccountId) && d.inMonth(t.epochDay, month)), (t) => t.amount)
    const savedByCategory = sumOf(d.expenses.filter((e) => d.savingIds.has(e.categoryId) && d.inMonth(e.epochDay, month)), (e) => e.amount)
    const income = d.receivedIn(month)
    const prevSpent = hasPrev ? sumOf(prevList, (e) => e.amount) : null
    return {
      month,
      income,
      spent,
      daily,
      daysElapsed: elapsed,
      payments: spending.length,
      topTag: tags ? [tags[0], tags[1], spent > 0 ? tags[1] / spent * 100 : 0] : null,
      topPlace: place ?? null,
      biggest: maxBy(spending, (e) => e.amount) ?? null,
      priciestWeekday: weekday ?? null,
      noSpendDays: daily.slice(0, elapsed).filter((v) => v <= 0).length,
      topDay: topDayIdx != null ? [atDay(month, topDayIdx + 1), daily[topDayIdx]] : null,
      prevSpent,
      saved: savedByTransfer + savedByCategory,
      bank: sumOf(d.accounts.filter((a) => a.type === 'BANK'), (a) => a.balance),
      fixed: sumOf(all.filter((e) => Budgeting.isFixed(e, d.bills)), (e) => e.amount),
      left: income - spent,
      perDay: elapsed > 0 ? spent / elapsed : 0,
      isCurrent: ymEq(month, thisMonth()),
      vsPrevPct: prevSpent != null && prevSpent > 0 ? (spent - prevSpent) / prevSpent * 100 : null,
    }
  },
}
