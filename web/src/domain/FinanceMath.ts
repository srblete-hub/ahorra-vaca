// Port de domain/FinanceMath.kt.
import type { Account, AccountType, Category, CryptoHolding, Income, IncomeSource, StockHolding, Transfer } from '../data/models'
import type { LocalDate, YearMonth } from '../data/dates'
import { average, clamp, roundEven, ep, lenOfMonth, maxBy, range, sumOf, ymMinus, ymEq, ymOfDay } from './kt'

/**
 * Lo que tienes ahora mismo, separado: disponible para el día a día, ahorro (lo guardado y lo que
 * da intereses) e invertido (cripto y acciones a precio de mercado).
 * `total` y `built` (propiedades calculadas en Kotlin) se calculan al crearlo con makeWealth().
 */
export interface Wealth {
  available: number
  savings: number
  invested: number
  debts: number
  /** Patrimonio neto: lo que tienes menos lo que debes. */
  total: number
  /** Lo construido a largo plazo (ahorro + inversiones − deudas), frente a la liquidez del día a día. */
  built: number
}

export function makeWealth(available: number, savings: number, invested: number, debts = 0): Wealth {
  return { available, savings, invested, debts, total: available + savings + invested - debts, built: savings + invested - debts }
}

export const Wealth = {
  of(accounts: Account[], crypto: CryptoHolding[], stocks: StockHolding[]): Wealth {
    return makeWealth(
      sumOf(accounts.filter((a) => !isSaving(a) && !isDebt(a)), (a) => a.balance),
      sumOf(accounts.filter((a) => isSaving(a)), (a) => a.balance),
      sumOf(crypto, (c) => c.amount * c.price) + sumOf(stocks, (s) => s.shares * s.price),
      sumOf(accounts.filter((a) => isDebt(a)), (a) => a.balance),
    )
  },
}

const SavingTypes: AccountType[] = ['BANK_SAVINGS', 'HOME_SAVINGS', 'FUND']

/** Es ahorro si da intereses o si es un sitio de guardar (cuenta de ahorro, hucha, fondo). */
export function isSaving(a: Account): boolean { return !isDebt(a) && (a.interestRate > 0 || SavingTypes.includes(a.type)) }

export function isDebt(a: Account): boolean { return a.type === 'DEBT' }

/** Cuánto suma al patrimonio: las deudas restan. */
export function signedBalance(a: Account): number { return isDebt(a) ? -a.balance : a.balance }

// ---------- Intereses ----------

export interface Projection {
  months: number
  finalBalance: number
  contributed: number
  interest: number
}

/** Intereses de una cuenta remunerada. annualRatePct es la TAE en % (5.0 = 5 %). */
export const InterestMath = {
  /** Tipo mensual equivalente a la TAE: (1 + TAE)^(1/12) − 1. */
  monthlyRate(annualRatePct: number): number { return Math.pow(1 + annualRatePct / 100, 1.0 / 12) - 1 },

  /** Intereses de un mes si el saldo no se toca. */
  monthlyInterest(balance: number, annualRatePct: number): number { return balance * InterestMath.monthlyRate(annualRatePct) },

  /** Lo que daría de intereses al mes con el saldo final (Projection.monthlyInterestAtEnd). */
  monthlyInterestAtEnd(p: Projection, annualRatePct: number): number { return InterestMath.monthlyInterest(p.finalBalance, annualRatePct) },

  /** Saldo tras `months` meses metiendo `monthly` € al final de cada mes, con interés compuesto mensual. */
  project(balance: number, annualRatePct: number, monthly: number, months: number): Projection {
    const r = InterestMath.monthlyRate(annualRatePct)
    const growth = Math.pow(1 + r, months)
    const fv = r === 0 ? balance + monthly * months : balance * growth + monthly * (growth - 1) / r
    const contributed = monthly * months
    return { months, finalBalance: fv, contributed, interest: fv - balance - contributed }
  },
}

// ---------- Ingresos ----------

export interface SourceMonth {
  source: IncomeSource
  /** Lo que se cuenta para el presupuesto: lo cobrado, o lo esperado si aún no ha llegado. */
  amount: number
  received: number
  isEstimate: boolean
  /** Media de los últimos meses (solo variables con historial). */
  average: number | null
  /** received > 0 (propiedad calculada en Kotlin). */
  isReceived: boolean
  /** Importe que confirmaste para este mes (null = se estima). */
  confirmed: number | null
}

export interface MonthIncome {
  total: number
  received: number
  sources: SourceMonth[]
  extra: number
}

const ymKeyKt = (m: YearMonth) => `${m.year}-${String(m.month).padStart(2, '0')}`
const ymContains = (m: YearMonth, epochDay: number) => ymEq(ymOfDay(epochDay), m)

/** Ingreso de un mes con lo fijo más la media de lo variable, corregido con lo ya cobrado. */
export const IncomeMath = {
  /** Importes confirmados por ti: "idFuente@2026-11" → €. Lo rellena computePlanning desde los ajustes. */
  confirmed: {} as Record<string, number>,

  key(sourceId: number, month: YearMonth): string { return `${sourceId}@${ymKeyKt(month)}` },

  confirmedFor(source: IncomeSource, month: YearMonth): number | null {
    if (!source.variable) return null
    const v = IncomeMath.confirmed[IncomeMath.key(source.id, month)]
    return v == null ? null : v
  },

  /** Media mensual de lo cobrado de `source` en los `lookback` meses anteriores a `month` que tengan cobros. */
  variableAverage(source: IncomeSource, incomes: Income[], month: YearMonth, lookback = 3): number | null {
    const totals = range(1, lookback).map((i) => ymMinus(month, i))
      .map((m) => sumOf(incomes.filter((it) => it.sourceId === source.id && ymContains(m, it.epochDay)), (it) => it.amount))
      .filter((t) => t > 0)
    return totals.length === 0 ? null : average(totals)
  },

  forMonth(sources: IncomeSource[], incomes: Income[], month: YearMonth): MonthIncome {
    const perSource: SourceMonth[] = sources.map((s) => {
      const received = sumOf(incomes.filter((it) => it.sourceId === s.id && ymContains(month, it.epochDay)), (it) => it.amount)
      const avg = s.variable ? IncomeMath.variableAverage(s, incomes, month) : null
      const conf = IncomeMath.confirmedFor(s, month)
      const expected = conf ?? (s.variable ? avg ?? s.amount : s.amount)
      return {
        source: s,
        amount: received > 0 ? received : expected,
        received,
        isEstimate: received <= 0 && s.variable && conf == null,
        average: avg,
        isReceived: received > 0,
        confirmed: conf,
      }
    })
    // Cobros sin fuente (intereses, extras): suman a lo cobrado pero no al presupuesto previsto.
    const extra = sumOf(incomes.filter((it) => it.sourceId == null && ymContains(month, it.epochDay)), (it) => it.amount)
    return { total: sumOf(perSource, (x) => x.amount), received: sumOf(perSource, (x) => x.received) + extra, sources: perSource, extra }
  },
}

// ---------- Reparto ----------

export type AllocationPreset = 'BALANCED' | 'SAVER' | 'MAX'
export const ALLOCATION_PRESET_INFO: Record<AllocationPreset, { label: string; needs: number; wants: number; savings: number }> = {
  BALANCED: { label: 'Equilibrado', needs: 50.0, wants: 30.0, savings: 20.0 },
  SAVER: { label: 'Priorizar ahorro', needs: 50.0, wants: 15.0, savings: 35.0 },
  MAX: { label: 'Ahorro máximo', needs: 45.0, wants: 5.0, savings: 50.0 },
}
export const ALLOCATION_PRESET_VALUES: AllocationPreset[] = ['BALANCED', 'SAVER', 'MAX']

const r1 = (v: number) => roundEven(v * 10) / 10

/** Reparto del ingreso: cambiar un % reajusta el resto para que siempre sume 100. */
export const Allocation = {
  /**
   * Pone `id` a `newPercent` y reparte la diferencia entre las demás categorías sin candado,
   * en proporción a su peso. Si las demás no pueden absorberla, `newPercent` se recorta.
   */
  rebalance(categories: Category[], id: number, newPercent: number): Category[] {
    const target = categories.find((c) => c.id === id)
    if (!target) return categories
    const others = categories.filter((c) => c.id !== id && !c.locked)
    const lockedSum = sumOf(categories.filter((c) => c.id !== id && c.locked), (c) => c.percent)
    const available = Math.max(100 - lockedSum, 0)
    const value = r1(clamp(newPercent, 0, others.length === 0 ? target.percent : available))
    const rest = available - value
    const othersSum = sumOf(others, (c) => c.percent)
    const newOthers = new Map<number, number>()
    for (const c of others) newOthers.set(c.id, r1(othersSum > 0 ? rest * c.percent / othersSum : rest / others.length))
    // Corrige el redondeo para que el total sea exactamente 100.
    const drift = 100 - lockedSum - value - [...newOthers.values()].reduce((a, b) => a + b, 0)
    if (newOthers.size > 0 && Math.abs(drift) > 0.001) {
      const biggest = maxBy([...newOthers.entries()], (e) => e[1])![0]
      newOthers.set(biggest, r1(newOthers.get(biggest)! + drift))
    }
    return categories.map((c) => {
      if (c.id === id) return { ...c, percent: value }
      if (newOthers.has(c.id)) return { ...c, percent: newOthers.get(c.id)! }
      return c
    })
  },

  /** Aplica un atajo: la primera categoría de gasto son las "necesidades", el resto de gasto es ocio. */
  applyPreset(categories: Category[], preset: AllocationPreset): Category[] {
    const p = ALLOCATION_PRESET_INFO[preset]
    const savings = categories.filter((c) => c.isSavings)
    const spending = categories.filter((c) => !c.isSavings)
    const needs = spending.find((c) => c.name.toLowerCase().includes('necesidad')) ?? spending[0]
    const wants = spending.filter((c) => c.id !== needs?.id)
    const split = (list: Category[], total: number): Map<number, number> => {
      const m = new Map<number, number>()
      if (list.length === 0) return m
      const s = sumOf(list, (c) => c.percent)
      for (const c of list) m.set(c.id, r1(s > 0 ? total * c.percent / s : total / list.length))
      return m
    }
    const values = new Map<number, number>()
    if (needs) values.set(needs.id, wants.length === 0 ? p.needs + p.wants : p.needs)
    split(wants, p.wants).forEach((v, k) => values.set(k, v))
    split(savings, savings.length === 0 ? 0 : p.savings).forEach((v, k) => values.set(k, v))
    const result = categories.map((c) => (values.has(c.id) ? { ...c, percent: values.get(c.id)! } : c))
    const drift = 100 - sumOf(result, (c) => c.percent)
    if (Math.abs(drift) > 0.001 && result.length > 0) {
      const biggest = maxBy(result, (c) => c.percent)!
      return result.map((c) => (c.id === biggest.id ? { ...c, percent: r1(c.percent + drift) } : c))
    }
    return result
  },
}

// ---------- Intereses ganados ----------

export interface InterestEarnedResult { earned: number; estimated: boolean; monthlyNow: number }

/**
 * Intereses ganados de verdad por una cuenta remunerada, con interés compuesto mensual:
 * - si se abonan solos, la suma de los abonos + lo que lleva acumulado este mes;
 * - si no, cada ingreso (transferencia) cuenta desde su fecha: 200 € el primer mes y 250 € el segundo
 *   generan intereses distintos. El dinero que ya había al empezar cuenta desde que usas la app.
 */
export const InterestEarned = {
  of(account: Account, incomes: Income[], transfers: Transfer[], today: LocalDate, since: LocalDate): InterestEarnedResult {
    const r = InterestMath.monthlyRate(account.interestRate)
    const monthlyNow = account.balance * r
    if (account.interestRate <= 0) return { earned: 0, estimated: false, monthlyNow: 0 }
    const partOfMonth = (today.day - 1) / lenOfMonth(today)
    if (account.autoInterest) {
      const credited = sumOf(incomes.filter((it) => it.accountId === account.id && it.sourceId == null && it.note.startsWith('Intereses')), (it) => it.amount)
      return { earned: credited + monthlyNow * partOfMonth, estimated: false, monthlyNow }
    }
    const ins = transfers.filter((t) => t.toAccountId === account.id)
    const outs = transfers.filter((t) => t.fromAccountId === account.id)
    const months = (day: number) => Math.max(0, (ep(today) - day) / 30.4375)
    const grown = (amount: number, day: number) => amount * (Math.pow(1 + r, months(day)) - 1)
    // Lo que había antes de los movimientos apuntados, desde que empezaste a usar la app.
    const start = Math.max(0, account.balance - sumOf(ins, (t) => t.amount) + sumOf(outs, (t) => t.amount))
    const earned = grown(start, ep(since)) + sumOf(ins, (t) => grown(t.amount, t.epochDay)) - sumOf(outs, (t) => grown(t.amount, t.epochDay))
    return { earned: Math.max(0, earned), estimated: true, monthlyNow }
  },
}
