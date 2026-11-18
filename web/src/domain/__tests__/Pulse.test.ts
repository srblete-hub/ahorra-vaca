import { describe, expect, it } from 'vitest'
import { ld } from '../../data/dates'
import { makePulse, PulseCalculator, zoneOf } from '../Pulse'
import { LimitAlerts, LimitSnapshot } from '../Alerts'
import { minusD } from '../kt'
import { ed, exp, fmt } from './helpers'

const near = (a: number, b: number, eps: number) => expect(Math.abs(a - b)).toBeLessThanOrEqual(eps)

describe('PulseTest', () => {
  it('zonesFollowTheWhoopRanges', () => {
    expect(zoneOf(67.0)).toBe('GREEN')
    expect(zoneOf(66.9)).toBe('YELLOW')
    expect(zoneOf(34.0)).toBe('YELLOW')
    expect(zoneOf(33.9)).toBe('RED')
  })

  it('marginIsFullWhenSpendingLittleAndZeroWhenFarAbove', () => {
    // Mes de 30 días, 600 € para gastar: el día 10 tocaban 240 € (con 2 días de cortesía).
    near(PulseCalculator.marginFor(80.0, 600.0, 10, 30), 100.0, 0.01)
    near(PulseCalculator.marginFor(240.0, 600.0, 10, 30), 50.0, 0.01)
    near(PulseCalculator.marginFor(384.0, 600.0, 10, 30), 0.0, 0.01)
  })

  it('spendScoreNeverPasses100', () => {
    near(PulseCalculator.spendScore(100.0, 100.0), 60.2, 0.1)
    near(PulseCalculator.spendScore(200.0, 100.0), 84.5, 0.1)
    near(PulseCalculator.spendScore(300.0, 100.0), 100.0, 0.01)
    near(PulseCalculator.spendScore(2400.0, 100.0), 100.0, 0.01)
    near(PulseCalculator.spendScore(0.0, 100.0), 0.0, 0.01)
  })

  it('limitAlertsAt80And100', () => {
    const today = ld(2026, 11, 20)
    const snap = { perDay: 30.0, weekOptimal: 150.0, tagMonthly: { TRANSPORT: 130.0 } }
    expect(LimitSnapshot.decode(LimitSnapshot.encode(snap))).toEqual(snap)
    const e = [
      exp(1, 1, 26.0, 'Mercadona', ed(today)),
      exp(2, 1, 110.0, 'Repsol', ed(minusD(today, 3))),
    ]
    const alerts = LimitAlerts.check(today, e, [], new Set(), snap, fmt)
    expect(alerts.find((a) => a.key === 'day80')).toBeDefined()
    expect(alerts.some((a) => a.key === 'tag80-TRANSPORT')).toBe(true)
    const over = LimitAlerts.check(today, [...e, exp(3, 1, 30.0, 'Repsol', ed(today))], [], new Set(), snap, fmt)
    expect(over.some((a) => a.key === 'day100' && a.severe)).toBe(true)
    expect(over.some((a) => a.key === 'tag100-TRANSPORT' && a.severe)).toBe(true)
    expect(over.some((a) => a.key === 'week100')).toBe(true)
  })

  it('pressureIsAboutToday', () => {
    const input = {
      cushion: 100.0, margin: 50.0, weekSpent: 0.0, weekOptimal: 100.0, weekOptimalToDate: 50.0, todayAllowance: 30.0,
      healthInRange: 5, healthTotal: 5, pressure: 45.0 / 30.0, days: [], statement: '', tip: '', spentToday: 45.0,
    }
    const p = makePulse(input)
    expect(p.pressureZone).toBe('RED')
    const calm = makePulse({ ...input, pressure: 20.0 / 30.0, spentToday: 20.0 })
    expect(calm.pressureZone).toBe('GREEN')
  })
})
