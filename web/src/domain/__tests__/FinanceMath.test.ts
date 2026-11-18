import { describe, expect, it } from 'vitest'
import { plusMonths } from '../../data/dates'
import type { YearMonth } from '../../data/dates'
import { Allocation, ALLOCATION_PRESET_INFO, ALLOCATION_PRESET_VALUES, IncomeMath, InterestMath, Wealth } from '../FinanceMath'
import { acc, atDay, cat, crypto, ed, inc, src } from './helpers'

describe('FinanceMathTest', () => {
  // ---------- Disponible / ahorro / invertido ----------

  it('wealthSplit', () => {
    const accounts = [
      acc(1, 'Nómina', 'BANK', 1000.0),
      acc(2, 'Cartera', 'CASH', 50.0),
      acc(3, 'Hucha', 'HOME_SAVINGS', 300.0),
      // Una cuenta corriente que da intereses cuenta como ahorro.
      acc(4, 'Remunerada', 'BANK', 200.0, { interestRate: 5.0 }),
    ]
    const w = Wealth.of(accounts, [crypto(0, 'bitcoin', 'BTC', 'Bitcoin', 0.01, 0, 60000.0)], [])
    expect(w.available).toBeCloseTo(1050.0, 3)
    expect(w.savings).toBeCloseTo(500.0, 3)
    expect(w.invested).toBeCloseTo(600.0, 3)
    expect(w.total).toBeCloseTo(2150.0, 3)
  })

  // ---------- Intereses ----------

  it('fivePercentOnTenThousand', () => {
    // 5 % TAE sobre 10.000 €: unos 40,74 € el primer mes y 500 € al año.
    expect(Math.abs(InterestMath.monthlyInterest(10_000.0, 5.0) - 40.74)).toBeLessThanOrEqual(0.01)
    const year = InterestMath.project(10_000.0, 5.0, 0.0, 12)
    expect(Math.abs(year.finalBalance - 10_500.0)).toBeLessThanOrEqual(0.01)
    expect(Math.abs(year.interest - 500.0)).toBeLessThanOrEqual(0.01)
  })

  it('monthlyContributions', () => {
    const p = InterestMath.project(0.0, 5.0, 100.0, 12)
    expect(p.contributed).toBeCloseTo(1200.0, 3)
    // Metiendo 100 €/mes se ganan intereses
    expect(p.interest).toBeGreaterThanOrEqual(20)
    expect(p.interest).toBeLessThanOrEqual(30)
    const zero = InterestMath.project(1000.0, 0.0, 50.0, 10)
    expect(zero.finalBalance).toBeCloseTo(1500.0, 3)
  })

  // ---------- Ingresos ----------

  const nov: YearMonth = { year: 2026, month: 11 }
  const day = (m: YearMonth, d: number) => ed(atDay(m, d))
  const salary = src(1, 'Nómina', false, 1650.0, 1, 5)
  const youtube = src(2, 'YouTube', true, 200.0, 22, 22)

  it('fixedPlusVariableAverage', () => {
    IncomeMath.confirmed = {}
    const history = [
      inc(0, 2, 300.0, '', day(plusMonths(nov, -1), 22)),
      inc(0, 2, 450.0, '', day(plusMonths(nov, -2), 22)),
      inc(0, 2, 300.0, '', day(plusMonths(nov, -3), 22)),
      inc(0, 2, 9999.0, '', day(plusMonths(nov, -4), 22)), // fuera de la media
    ]
    const m = IncomeMath.forMonth([salary, youtube], history, nov)
    expect(m.total).toBeCloseTo(1650.0 + 350.0, 3)
    const yt = m.sources.find((s) => s.source.id === 2)!
    expect(yt.isEstimate).toBe(true)
    expect(yt.average!).toBeCloseTo(350.0, 3)
  })

  it('variableWithoutHistoryUsesEstimate', () => {
    const m = IncomeMath.forMonth([youtube], [], nov)
    expect(m.total).toBeCloseTo(200.0, 3)
  })

  it('receivedReplacesEstimate', () => {
    const incomes = [
      inc(0, 1, 1700.0, '', day(nov, 3)),
      inc(0, 2, 512.0, '', day(nov, 22)),
      inc(0, null, 40.0, 'Intereses', day(nov, 1)),
    ]
    const m = IncomeMath.forMonth([salary, youtube], incomes, nov)
    expect(m.total).toBeCloseTo(1700.0 + 512.0, 3)
    expect(m.received).toBeCloseTo(1700.0 + 512.0 + 40.0, 3)
    expect(m.sources.some((s) => s.isEstimate)).toBe(false)
  })

  // ---------- Reparto ----------

  const base = [cat(1, 'Necesidades', 50.0), cat(2, 'Ocio', 30.0), cat(3, 'Ahorro', 20.0, { isSavings: true })]
  const sum = (l: { percent: number }[]) => l.reduce((a, c) => a + c.percent, 0)

  it('raisingSavingsShrinksTheRestProportionally', () => {
    const r = Allocation.rebalance(base, 3, 36.0)
    expect(sum(r)).toBeCloseTo(100.0, 3)
    expect(r.find((c) => c.id === 3)!.percent).toBeCloseTo(36.0, 3)
    expect(r.find((c) => c.id === 1)!.percent).toBeCloseTo(40.0, 3)
    expect(r.find((c) => c.id === 2)!.percent).toBeCloseTo(24.0, 3)
  })

  it('lockedCategoryDoesNotMove', () => {
    const locked = base.map((c) => (c.id === 1 ? { ...c, locked: true } : c))
    const r = Allocation.rebalance(locked, 3, 40.0)
    expect(r.find((c) => c.id === 1)!.percent).toBeCloseTo(50.0, 3)
    expect(r.find((c) => c.id === 2)!.percent).toBeCloseTo(10.0, 3)
    // No puede pasar del 50 % que queda libre.
    expect(Allocation.rebalance(locked, 3, 90.0).find((c) => c.id === 3)!.percent).toBeCloseTo(50.0, 3)
  })

  it('presetsAlwaysSumHundred', () => {
    for (const p of ALLOCATION_PRESET_VALUES) {
      const r = Allocation.applyPreset(base, p)
      expect(sum(r)).toBeCloseTo(100.0, 3)
      expect(r.find((c) => c.isSavings)!.percent).toBeCloseTo(ALLOCATION_PRESET_INFO[p].savings, 3)
    }
  })
})
