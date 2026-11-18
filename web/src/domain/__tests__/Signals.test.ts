import { describe, expect, it } from 'vitest'
import type { Expense, RecurringBill } from '../../data/models'
import { ld } from '../../data/dates'
import { makeFinanceData } from '../Insights'
import type { FinanceData } from '../Insights'
import { PaycheckDistributor, PaymentOrganizer, RealPriceCalculator, RecoverableMoney, RecurringDetector, TrendDetector, WeeklyNudge } from '../Signals'
import { SavingProfile, WealthExplainer } from '../Budget'
import { FinancialCircles } from '../Circles'
import { FinancialAging } from '../Goals'
import { BuzzAnalyzer } from '../Buzz'
import { IncomeMath } from '../FinanceMath'
import { minusD, minusWeeks, plusD, plusM, range, withDayOfMonth } from '../kt'
import { acc, bill, cat, crypto, ed, exp, fmt, inc, src } from './helpers'

describe('SignalsTest', () => {
  IncomeMath.confirmed = {}
  const cats = [cat(1, 'Necesidades', 50.0), cat(2, 'Ocio y caprichos', 30.0), cat(3, 'Ahorro', 20.0, { isSavings: true })]
  const today = ld(2026, 11, 20) // viernes

  function data(extra: Expense[] = [], bills: RecurringBill[] = []): FinanceData {
    let id = 1
    const e: Expense[] = []
    for (let back = 1; back <= 4; back++) {
      const m = plusM(withDayOfMonth(today, 1), -back)
      e.push(exp(id++, 2, 10.99, 'Spotify', ed(plusD(m, 7))))
      e.push(exp(id++, 1, 4.5, 'Comisión mantenimiento', ed(m)))
    }
    e.push(exp(id++, 2, 12.99, 'Spotify', ed(minusD(today, 12))))
    e.push(exp(id++, 1, 4.5, 'Comisión mantenimiento', ed(withDayOfMonth(today, 1))))
    const sources = [src(1, 'Nómina', false, 1800.0, 28, 28, { accountId: 1 })]
    const incomes = range(1, 4).map((i) => inc(i, 1, 1800.0, 'Nómina', ed(plusM(withDayOfMonth(today, 28), -i))))
    return makeFinanceData({
      today, accounts: [acc(1, 'Santander', 'BANK', 900.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 3000.0, { interestRate: 5.0 })],
      crypto: [], stocks: [], categories: cats, expenses: [...e, ...extra.map((x) => ({ ...x, id: id++ }))], incomes, sources, bills, snapshots: [],
    })
  }

  it('recurringAndRecoverable', () => {
    const d = data()
    const rec = RecurringDetector.find(d)
    const spotify = rec.find((r) => r.name === 'Spotify')!
    expect(spotify.kind).toBe('Suscripción')
    expect(spotify.change).toBeCloseTo(2.0, 2)
    expect(rec.some((r) => r.kind === 'Comisión')).toBe(true)
    const refund = d.expenses.find((x) => x.note === 'Spotify')!
    const money = RecoverableMoney.find(d, [refund.id], fmt)
    expect(money.some((m) => m.title.includes('Spotify ha subido') && m.nextStep.trim() !== '')).toBe(true)
    expect(money.some((m) => m.title.startsWith('Comisiones'))).toBe(true)
    expect(money.some((m) => m.title.startsWith('Reembolso pendiente'))).toBe(true)
    // Nunca afirma que algo esté olvidado o mal cobrado.
    expect(money.every((m) => !m.title.includes('olvid') && !m.evidence.includes('incorrect'))).toBe(true)
  })

  it('weeklyNudgeNamesTheMerchant', () => {
    const monday = minusD(today, 4)
    const d = data(range(0, 2).map((i) => exp(0, 2, 2.2, 'Monster Energy', ed(plusD(monday, i)))))
    const text = WeeklyNudge.find(d, 'Pablo', fmt)!
    expect(text.startsWith('Pablo, esta semana has gastado 6.60 € en Monster Energy (3 veces)')).toBe(true)
  })

  it('billCrunchBeforePaydaySuggestsMovingBills', () => {
    // Cobras el 28 y el alquiler, el seguro y la luz caen antes.
    const bills = [
      bill(1, 'Alquiler', 700.0, 22, { categoryId: 1, accountId: 1 }),
      bill(2, 'Seguro', 180.0, 24, { categoryId: 1, accountId: 1 }),
      bill(3, 'Luz', 60.0, 25, { categoryId: 1, accountId: 1 }),
    ]
    const c = PaymentOrganizer.analyze(data([], bills), null, fmt)!
    expect(c.crunch).toBe(true)
    expect(c.suggestions[0]).toContain('Alquiler')
    expect(c.payday).toEqual(ld(2026, 11, 28))
  })

  it('paycheckSplitAndRealPrice', () => {
    const d = data([], [bill(1, 'Alquiler', 700.0, 3, { categoryId: 1, accountId: 1 }), bill(2, 'Seguro coche', 420.0, 10, { yearly: true, month: 12, categoryId: 1 })])
    const s = PaycheckDistributor.split(d, null, fmt)
    expect(s).not.toBeNull()
    expect(s!.income).toBeCloseTo(1800.0, 3)
    // El seguro anual de diciembre cae entre esta nómina y la siguiente: aparece y se avisa.
    expect(s!.notes.some((n) => n.includes('Seguro coche'))).toBe(true)
    expect(s!.parts.reduce((a, p) => a + p[1], 0)).toBeCloseTo(s!.income, 2)
    const r = RealPriceCalculator.of(800.0, d, null, fmt)
    expect(r.incomeDays).toBeGreaterThan(10)
    expect(r.months.length).toBe(3)
  })

  it('wealthChangeIsExplainedAndProfilesSum', () => {
    const base = data()
    const d = makeFinanceData({ ...base, crypto: [crypto(1, 'ethereum', 'ETH', 'Ethereum', 1.0, 3000.0, 2500.0)] })
    // Baja 150 € sin gastos ni cobros en el periodo: lo explica la bajada de ETH.
    const why = WealthExplainer.explain(d, ed(today), -150.0, { ETH: -5.0 }, fmt)!
    expect(why.startsWith('Debido a la bajada de ETH')).toBe(true)
    for (let st = 0; st <= 3; st++) {
      const w = SavingProfile.weights(st, ['TRAVEL'])!
      expect(Object.values(w).reduce((a, b) => a + b, 0)).toBeCloseTo(100.0, 3)
      expect(w.TRAVEL!).toBeGreaterThan(0)
    }
    // Más ahorrador = más peso al súper y menos a restaurantes.
    expect(SavingProfile.weights(3, [])!.FOOD!).toBeLessThan(SavingProfile.weights(0, [])!.FOOD!)
    expect(SavingProfile.spendFactor(3)).toBeLessThan(SavingProfile.spendFactor(0))
  })

  it('circlesAreWeightedAndMaturityRewardsGoodHabits', () => {
    const d = data()
    const circles = FinancialCircles.of(d, null, null, [], 80.0, fmt)
    expect(circles.map((c) => c.key)).toEqual(['stability', 'control', 'progress'])
    for (const c of circles) {
      expect(c.factors.reduce((a, f) => a + f.weight, 0)).toBeCloseTo(1.0, 3)
      expect(c.score).toBeGreaterThanOrEqual(0)
      expect(c.score).toBeLessThanOrEqual(100)
      expect(c.nextStep.trim()).not.toBe('')
    }
    // Sin objetivos, el progreso es provisional (no se inventa).
    expect(circles[circles.length - 1].provisional).toBe(true)
    expect(circles[circles.length - 1].factors.find((f) => f.name === 'Camino a tus objetivos')!.score).toBeNull()
    // Con 4 meses de historial la confianza es 4/6: ningún círculo puede llegar a 100.
    for (const c of circles) { expect(c.score).toBeLessThan(100.0); expect(c.confidence).toBeLessThan(1.0) }
    // Curvas: el colchón tiene rendimientos decrecientes y nunca llega a 100.
    expect(Math.abs(FinancialCircles.saturate(3.0, 2.5) - 69.9)).toBeLessThanOrEqual(0.1)
    expect(Math.abs(FinancialCircles.saturate(6.0, 2.5) - 90.9)).toBeLessThanOrEqual(0.1)
    expect(FinancialCircles.saturate(24.0, 2.5)).toBeLessThan(100.0)
    expect(FinancialCircles.logistic(1.15, 1.15, 4.0)).toBeCloseTo(50.0, 3)
    expect(FinancialCircles.changeText(70.0, 64.0)).toBe('Ha subido 6 puntos desde la semana pasada.')
    // Edad financiera: ahorrar mucho te hace "mayor" (más madura).
    const age = FinancialAging.evaluate(30, d, null)
    expect(age.factors.find((f) => f.name === 'Capacidad de ahorro')!.years).toBeGreaterThan(0)
    expect(age.pace).toBeGreaterThan(1.0)
  })

  it('buzzCountsMentionsAndTone', () => {
    const posts = [
      { title: 'Solana breakout: new ATH incoming, very bullish', score: 900, comments: 120, url: 'u1' },
      { title: 'SOL adoption keeps growing after the ETF approval', score: 400, comments: 80, url: 'u2' },
      { title: 'Dogecoin crash: another dump, bearish week', score: 300, comments: 60, url: 'u3' },
      { title: 'What wallet do you use?', score: 50, comments: 10, url: 'u4' },
    ]
    const trending = [{ id: 'solana', symbol: 'SOL', name: 'Solana', rank: 5, change24h: 6.2 }]
    const buzz = BuzzAnalyzer.analyze(posts, trending)
    const sol = buzz.items.find((i) => i.symbol === 'SOL')!
    expect(sol.mentions).toBe(2)
    expect(sol.positivity).toBeGreaterThan(0.6)
    expect(buzz.items.find((i) => i.symbol === 'DOGE')!.positivity).toBeLessThan(0.4)
    expect(buzz.featured?.symbol).toBe('SOL')
  })

  it('persistentTrendIsDetected', () => {
    const monday = minusD(today, 4)
    // 8 semanas a 20 € de gasolina y las 4 últimas a 45 €.
    const gas = range(1, 12).map((w) => exp(0, 1, w <= 4 ? 45.0 : 20.0, 'Repsol', ed(plusD(minusWeeks(monday, w), 2))))
    const trends = TrendDetector.find(data(gas), fmt)
    expect(trends.some((t) => t.title.startsWith('Transporte') && t.explanation.includes('Repsol')), JSON.stringify(trends)).toBe(true)
  })
})
