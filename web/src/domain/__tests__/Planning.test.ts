import { describe, expect, it } from 'vitest'
import { ld } from '../../data/dates'
import { Achievements, Bills, Forecasting, Spending } from '../Planning'
import { IncomeMath } from '../FinanceMath'
import { plusD } from '../kt'
import { bill, ed, exp, src } from './helpers'

describe('PlanningTest', () => {
  IncomeMath.confirmed = {}
  const today = ld(2026, 11, 4)
  const rent = bill(1, 'Alquiler', 650.0, 8, { categoryId: 1 })
  const netflix = bill(2, 'Netflix', 14.0, 20, { categoryId: 2 })
  const salary = src(1, 'Nómina', false, 1800.0, 28, 28)

  it('billPaidByLinkOrByNameAndAmount', () => {
    const nov = { year: 2026, month: 11 }
    expect(Bills.isPaid(rent, nov, [])).toBe(false)
    expect(Bills.isPaid(rent, nov, [exp(0, 1, 1.0, '', ed(today), { billId: 1 })])).toBe(true)
    expect(Bills.isPaid(rent, nov, [exp(0, 1, 640.0, 'alquiler noviembre', ed(today))])).toBe(true)
    expect(Bills.isPaid(rent, nov, [exp(0, 1, 300.0, 'Alquiler', ed(today))])).toBe(false)
  })

  it('yearlyBillOnlyInItsMonth', () => {
    const insurance = bill(0, 'Seguro', 420.0, 10, { yearly: true, month: 3, categoryId: 1 })
    expect(Bills.dueDate(insurance, { year: 2026, month: 11 })).toBeNull()
    expect(Bills.dueDate(insurance, { year: 2027, month: 3 })).toEqual(ld(2027, 3, 10))
    expect(Bills.monthly(insurance)).toBeCloseTo(35.0, 3)
  })

  it('forecastWarnsBeforeRunningShort', () => {
    // 500 € hoy, alquiler el 8 y nómina el 28: entre medias te quedas en negativo.
    const f = Forecasting.forecast(today, 500.0, 30, [salary], [], [rent, netflix], [], new Set())
    const negative = f.firstNegative
    expect(negative).not.toBeNull()
    expect(negative![0]).toEqual(ld(2026, 11, 8))
    expect(f.points.find((p) => ed(p[0]) === ed(ld(2026, 11, 28)))![1]).toBeCloseTo(500.0 - 650.0 - 14.0 + 1800.0, 3)
  })

  it('safeToSpendSubtractsBillsBeforePaydayAndSavings', () => {
    const events = Forecasting.events(today, plusD(today, 60), [salary], [], [rent, netflix], [])
    const s = Spending.safeToSpend(today, 1200.0, events, 200.0, 0.0)
    expect(s.nextPayday).toEqual(ld(2026, 11, 28))
    expect(s.pendingBills).toBeCloseTo(664.0, 3)
    expect(s.safe).toBeCloseTo(1200.0 - 664.0 - 200.0, 3)
    expect(s.daysToPayday).toBe(24)
  })

  it('levelsGrowWithPoints', () => {
    expect(Achievements.level(0).number).toBe(1)
    expect(Achievements.level(80).name).toBe('Ahorrador')
    expect(Achievements.level(10_000).to).toBeNull()
  })
})
