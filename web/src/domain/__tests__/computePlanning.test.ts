// Prueba de humo de computePlanning (port del cálculo de FinanceViewModel) con unos datos como los de Pablo.
import { describe, expect, it } from 'vitest'
import type { DB } from '../../data/store'
import { DEFAULT_SETTINGS } from '../../data/models'
import { ld } from '../../data/dates'
import { computeMonthIncome, computePlanning, planningUpdates } from '../computePlanning'
import { minusD } from '../kt'
import { acc, bill, cat, ed, exp, inc, src } from './helpers'

const today = ld(2026, 11, 20)

function sampleDb(): DB {
  return {
    accounts: [acc(1, 'Santander', 'BANK', 1500.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 4000.0, { interestRate: 5.0 })],
    categories: [cat(1, 'Necesidades', 50.0), cat(2, 'Ocio y caprichos', 30.0), cat(3, 'Ahorro', 20.0, { isSavings: true })],
    incomeSources: [src(10, 'Nómina', false, 1800.0, 1, 5, { accountId: 1 }), src(11, 'YouTube', true, 200.0, 22, 22, { accountId: 1 })],
    incomes: [inc(20, 10, 1800.0, 'Nómina', ed(ld(2026, 11, 2)), 1), inc(21, 10, 1800.0, 'Nómina', ed(ld(2026, 10, 2)), 1)],
    expenses: [
      exp(30, 1, 650.0, 'Alquiler', ed(ld(2026, 11, 3)), { billId: 40, accountId: 1 }),
      exp(31, 1, 80.0, 'Mercadona', ed(minusD(today, 2)), { accountId: 1 }),
      exp(32, 2, 25.0, 'Cines Yelmo', ed(today), { accountId: 1 }),
      exp(33, 1, 300.0, 'Mercadona', ed(ld(2026, 10, 10)), { accountId: 1 }),
    ],
    splits: [],
    bills: [bill(40, 'Alquiler', 650.0, 3, { accountId: 1 }), bill(41, 'Netflix', 13.99, 15, { categoryId: 2, accountId: 1 })],
    transfers: [],
    snapshots: [],
    crypto: [],
    stocks: [],
    settings: { ...DEFAULT_SETTINGS, birthYear: 1995, goalsRaw: `Colchón|10000.0|${ed(today)}|${ed(ld(2031, 11, 20))}` },
    nextId: 100,
  }
}

describe('computePlanning', () => {
  it('calcula la planificación con la cuenta principal y el ahorro', () => {
    const p = computePlanning(sampleDb(), today)
    expect(p.wealth.available).toBeCloseTo(1500.0, 3)
    expect(p.wealth.savings).toBeCloseTo(4000.0, 3)
    expect(p.bankPlan?.main.name).toBe('Santander')
    expect(p.bankPlan?.target?.name).toBe('Robinhood')
    expect(p.goal?.goal.name).toBe('Colchón')
    expect(p.age).not.toBeNull()
    expect(p.budget?.fromBank).toBe(true)
    expect(p.points).toBeGreaterThan(0)
    expect(p.level.number).toBeGreaterThanOrEqual(1)
    expect(p.limits.TRANSPORT?.weekly).toBe(true)
    // "Puedes gastar" sale de la cuenta del banco (nunca del ahorro).
    expect(p.safe.available).toBeCloseTo(1500.0 + p.bankPlan!.incomingTotal, 3)
  })

  it('el ingreso del mes usa lo confirmado y lo cobrado', () => {
    const db = sampleDb()
    db.settings = { ...db.settings, expectedIncome: { '11@2026-11': 290.0 } }
    const mi = computeMonthIncome(db, { year: 2026, month: 11 })
    expect(mi.total).toBeCloseTo(1800.0 + 290.0, 3)
    expect(computeMonthIncome({ ...db, incomeSources: [], settings: { ...db.settings, monthlyIncome: 1234 } }, { year: 2026, month: 11 }).total).toBe(1234)
  })

  it('planningUpdates guarda límites, círculos y logros nuevos una sola vez', () => {
    const db = sampleDb()
    const p = computePlanning(db, today)
    const { patch, fresh } = planningUpdates(p, db.settings, 123)
    expect(patch.limitSnapshot).toBeTruthy()
    expect(patch.circleHistory).toContain(`${ed(today)}:`)
    expect(fresh.length).toBeGreaterThan(0)
    const again = planningUpdates(p, { ...db.settings, ...patch }, 456)
    expect(again.fresh.length).toBe(0)
    expect(Object.keys(again.patch).length).toBe(0)
  })
})
