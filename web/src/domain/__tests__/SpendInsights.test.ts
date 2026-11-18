import { describe, expect, it } from 'vitest'
import type { Expense } from '../../data/models'
import type { LocalDate } from '../../data/dates'
import { ld } from '../../data/dates'
import { Classifier, NaturalEntry } from '../Spend'
import { BankPlanning, makeSafeToSpend } from '../Planning'
import { Insights, makeFinanceData } from '../Insights'
import type { FinanceData } from '../Insights'
import { Advisor, Budgeting, SavingsAdvisor } from '../Budget'
import { FinancialAging, GoalList, Goals } from '../Goals'
import type { Goal } from '../Goals'
import { Anomalies, InvisibleMoney, PurchaseSimulator } from '../Leaks'
import { Assistant } from '../Assistant'
import { IncomeMath } from '../FinanceMath'
import { minusD, plusD, plusM, plusY, range, withDayOfMonth } from '../kt'
import { acc, bill, cat, ed, exp, fmt, inc, split, src, transfer } from './helpers'

describe('SpendInsightsTest', () => {
  IncomeMath.confirmed = {}
  const cats = [cat(1, 'Necesidades', 50.0), cat(2, 'Ocio y caprichos', 30.0), cat(3, 'Ahorro', 20.0, { isSavings: true })]

  it('classifiesMerchants', () => {
    expect(Classifier.classify('MERCADONA VALENCIA')).toBe('SUPER')
    expect(Classifier.classify('Uber Eats')).toBe('FOOD')
    expect(Classifier.classify('Uber *Trip')).toBe('TRANSPORT')
    expect(Classifier.classify('Netflix.com')).toBe('SUBSCRIPTIONS')
    expect(Classifier.classify('Gasolinera Repsol')).toBe('TRANSPORT')
    expect(Classifier.classify('Cena con amigos')).toBe('FOOD')
    expect(Classifier.classify('Bizum Pepe')).toBe('OTHER')
    // Aprende de cómo lo clasificaste tú.
    const history = [exp(0, 2, 5.0, 'Bizum Pepe', 0, { tag: 'LEISURE' })]
    expect(Classifier.classify('bizum pepe', history)).toBe('LEISURE')
    expect(Classifier.bucketFor('SUPER', cats)).toBe(1)
    expect(Classifier.bucketFor('FOOD', cats)).toBe(2)
  })

  it('parsesSentences', () => {
    const list = NaturalEntry.parse('He pagado 18 € en una cena con amigos y 12,50 € en gasolina')
    expect(list.length).toBe(2)
    expect(list[0].amount).toBeCloseTo(18.0, 3)
    expect(list[0].note).toBe('Cena con amigos')
    expect(list[0].tag).toBe('FOOD')
    expect(list[1].amount).toBeCloseTo(12.5, 3)
    expect(list[1].note).toBe('Gasolina')
    expect(list[1].tag).toBe('TRANSPORT')
    const y = NaturalEntry.parse('ayer 30 euros de cine')
    expect(y.length).toBe(1)
    expect(y[0].daysAgo).toBe(1)
    expect(y[0].tag).toBe('LEISURE')
  })

  it('bankPlanRentLateSalaryBufferThenSave', () => {
    // 2 de noviembre: 2.000 € en Santander, alquiler de 700 € el día 3 y 250 € del sueldo que llegan el 20.
    const today = ld(2026, 11, 2)
    const santander = acc(1, 'Santander', 'BANK', 2000.0)
    const robinhood = acc(2, 'Robinhood', 'BANK_SAVINGS', 500.0, { interestRate: 5.0 })
    const rent = bill(1, 'Alquiler', 700.0, 3, { categoryId: 1, accountId: 1 })
    const late = src(1, 'Resto de nómina', false, 250.0, 20, 25, { accountId: 1 })
    // Gastas 20 € al día y quieres llegar a fin de mes con 75 € de sobra.
    const plan = BankPlanning.plan(today, santander, robinhood, 75.0, [rent], [], [], [late], [], 20.0, 0.0)
    expect(plan.days).toBe(29)
    expect(plan.monthMoney).toBeCloseTo(1550.0, 3)
    expect(plan.expectedSpend).toBeCloseTo(580.0, 3)
    expect(plan.saveGoal).toBeCloseTo(895.0, 3)
    // Lo del sueldo que aún no ha llegado se mueve cuando llegue.
    expect(plan.moveNow).toBeCloseTo(645.0, 3)
    expect(plan.moveLater).toBeCloseTo(250.0, 3)
    expect(plan.spendable).toBeCloseTo(580.0, 3)
    // Tras mover 645 €, ya no pide mover más ahora y lo gastable no cambia.
    const after = BankPlanning.plan(today, { ...santander, balance: 1355.0 }, robinhood, 75.0, [rent], [], [transfer(1, 1, 2, 645.0, ed(today))], [late], [], 20.0, 0.0)
    expect(after.moveNow).toBeCloseTo(0.0, 3)
    expect(after.moveLater).toBeCloseTo(250.0, 3)
    expect(after.spendable).toBeCloseTo(580.0, 3)
    // Cuando llega el cobro, deja de contar como pendiente.
    const paid = BankPlanning.plan(
      today, { ...santander, balance: 1605.0 }, robinhood, 75.0, [rent], [],
      [transfer(1, 1, 2, 645.0, ed(today))], [late], [inc(1, 1, 250.0, 'Resto', ed(today), 1)], 20.0, 0.0,
    )
    expect(paid.moveNow).toBeCloseTo(250.0, 3)
    expect(BankPlanning.defaultTarget([santander, robinhood])).toEqual(robinhood)
    // Con 700 € en el banco no se puede proponer ahorrar 670 €: se reserva lo necesario para vivir
    // (según tu perfil) y el ahorro no pasa de lo que te propones al mes.
    const tight = BankPlanning.plan(today, { ...santander, balance: 700.0 }, robinhood, 75.0, [], [], [], [], [], 2.0, 0.0, 330.0, 20.0)
    expect(tight.expectedSpend).toBeCloseTo(580.0, 3)
    expect(tight.saveGoal).toBeCloseTo(45.0, 3)
    expect(tight.spendable).toBeGreaterThanOrEqual(tight.expectedSpend)
    const rich = BankPlanning.plan(today, { ...santander, balance: 5000.0 }, robinhood, 75.0, [], [], [], [], [], 20.0, 0.0, 330.0, 20.0)
    expect(rich.saveGoal).toBeCloseTo(330.0, 3)
    expect(BankPlanning.defaultMain([santander, robinhood], -1)).toEqual(santander)
  })

  function data(today: LocalDate): FinanceData {
    const e: Expense[] = []
    let id = 1
    // Tres meses: restaurantes van subiendo, supermercado estable.
    for (let back = 1; back <= 3; back++) {
      const m = plusM(withDayOfMonth(today, 1), -back)
      e.push(exp(id++, 1, 200.0, 'Mercadona', ed(plusD(m, 4))))
      e.push(exp(id++, 2, 40.0 * (4 - back), 'Restaurante', ed(plusD(m, 5))))
      e.push(exp(id++, 1, 650.0, 'Alquiler', ed(plusD(m, 2)), { billId: 1 }))
    }
    e.push(exp(id++, 1, 120.0, 'Mercadona', ed(minusD(today, 1))))
    e.push(exp(id++, 2, 90.0, 'Cena con amigos', ed(today)))
    const incomes = range(0, 3).map((back) => inc(back + 1, 1, 1800.0, 'Nómina', ed(plusM(withDayOfMonth(today, 1), -back))))
    return makeFinanceData({
      today, accounts: [acc(1, 'Santander', 'BANK', 1500.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 4000.0, { interestRate: 5.0 })],
      crypto: [], stocks: [], categories: cats, expenses: e, incomes, sources: [],
      bills: [bill(1, 'Alquiler', 650.0, 3, { categoryId: 1 }), bill(2, 'Netflix', 13.99, 15, { categoryId: 2 })],
      snapshots: [], splits: [split(e[e.length - 1].id, 3, 1)],
    })
  }

  it('healthCostAndShare', () => {
    const d = data(ld(2026, 11, 20))
    const h = Insights.health(d, fmt)
    expect(h.indicators.length).toBe(5)
    expect(h.nextStep.trim()).not.toBe('')
    const cost = Insights.costOfLiving(d)
    expect(cost.fixed).toBeCloseTo(663.99, 2)
    expect(cost.variable).toBeGreaterThan(0)
    // 90 € entre 3, uno ya ha pagado: te deben 30 €.
    expect(Insights.owed(d.splits, d.expenses)).toBeCloseTo(30.0, 3)
    expect(Insights.describe(Insights.monthStory(d), fmt).trim()).not.toBe('')
    expect(Insights.timeTravel(d, 3).then.spent).toBeGreaterThan(0)
  })

  it('goalAndFinancialAge', () => {
    // 10.000 € en 5 años sin intereses: 166,67 € al mes.
    expect(Goals.requiredMonthly(0.0, 10000.0, 0.0, 60)).toBeCloseTo(166.67, 2)
    // Con 2.000 € ya y un 5 %, hace falta menos.
    expect(Goals.requiredMonthly(2000.0, 10000.0, 5.0, 60)).toBeLessThan(120.0)
    expect(Goals.monthsTo(0.0, 10000.0, 0.0, 10000.0 / 60)).toBe(60)
    const d = data(ld(2026, 11, 20))
    const g = Goals.status({ name: 'Colchón', target: 10000.0, start: d.today, deadline: plusY(d.today, 5) }, d, null, fmt)
    expect(g.tips.length).toBeGreaterThan(0)
    expect(g.current).toBeCloseTo(4000.0, 3)
    const age = FinancialAging.evaluate(30, d, g)
    expect(age.factors.length).toBeGreaterThanOrEqual(6)
    expect(age.pace).toBeGreaterThanOrEqual(-1.0)
    expect(age.pace).toBeLessThanOrEqual(3.0)
    expect(age.age).toBeCloseTo(30.0 + age.factors.reduce((a, f) => a + f.years, 0), 3)
  })

  it('budgetWheelAndSavingsAdvice', () => {
    const d = data(ld(2026, 11, 20))
    const limits = Budgeting.parseLimits(null)
    expect(limits.TRANSPORT!.amount).toBeCloseTo(30.0, 3)
    const b = Budgeting.plan(d, 300.0, limits)
    // 1.800 − 663,99 de fijos − 300 de ahorro.
    expect(b.base).toBeCloseTo(836.01, 2)
    const gas = b.items.find((i) => i.tag === 'TRANSPORT')!
    expect(gas.monthly).toBeCloseTo(130.0, 2)
    expect(b.items.reduce((a, i) => a + i.monthly, 0)).toBeCloseTo(b.base, 2)
    expect(b.items.reduce((a, i) => a + i.pct, 0)).toBeCloseTo(100.0, 2)
    expect(Budgeting.parseLimits(Budgeting.formatLimits(limits))).toEqual(limits)
    const advice = SavingsAdvisor.advise(d, null, fmt)!
    expect(advice.recommended > 0 && advice.recommended <= advice.capacity + 5).toBe(true)
    const t = Advisor.today(d, null, b, null, fmt)
    expect(t.headline.trim()).not.toBe('')
  })

  it('leaksAnomaliesAndGoalPriorities', () => {
    const today = ld(2026, 11, 20)
    const base = data(today)
    const extra: Expense[] = []
    let id = 500
    for (let back = 1; back <= 3; back++) {
      const m = plusM(withDayOfMonth(today, 1), -back)
      extra.push(exp(id++, 1, 4.5, 'Comisión mantenimiento', ed(m)))
      extra.push(exp(id++, 2, 10.99, 'Spotify', ed(plusD(m, 7))))
    }
    extra.push(exp(id++, 2, 12.99, 'Spotify', ed(minusD(today, 2))))
    extra.push(exp(id++, 2, 29.99, 'Amazon', ed(minusD(today, 1))))
    extra.push(exp(id++, 2, 29.99, 'Amazon', ed(today)))
    const d = makeFinanceData({ ...base, expenses: [...base.expenses, ...extra] })
    const leaks = InvisibleMoney.find(d, fmt)
    expect(leaks.some((l) => l.kind === 'FEE')).toBe(true)
    // Spotify sale una sola vez: suscripción que además ha subido de precio.
    expect(leaks.filter((l) => l.name === 'Spotify').length).toBe(1)
    expect(leaks.find((l) => l.name === 'Spotify')!.detail).toContain('Ha subido')
    expect(leaks.some((l) => l.kind === 'SUBSCRIPTION' && l.name === 'Netflix')).toBe(true)
    expect(Anomalies.find(d, fmt).some((a) => a.title.includes('duplicado'))).toBe(true)

    const goals: Goal[] = [
      { name: 'Colchón', target: 10000.0, start: today, deadline: plusY(today, 5) },
      { name: 'Viaje', target: 2000.0, start: today, deadline: plusY(today, 1) },
    ]
    expect(GoalList.parse(GoalList.format(goals))).toEqual(goals)
    const plan = GoalList.plan(goals, d, null, fmt)
    // El ahorro actual (4.000 €) va primero al de más prioridad.
    expect(plan[0].current).toBeCloseTo(4000.0, 3)
    expect(plan[1].current).toBeCloseTo(0.0, 3)
    expect(GoalList.swapImpact(goals, d, null, fmt)!).toContain('Viaje')
    const options = PurchaseSimulator.compare(180.0, 120.0, d, null, plan, fmt)
    expect(options.length).toBe(3)
    expect(options[0].goalDelayDays ?? 0).toBeGreaterThan(options[2].goalDelayDays ?? 0)
  })

  it('assistantAnswers', () => {
    const d = data(ld(2026, 11, 20))
    const safe = makeSafeToSpend(1500.0, 0.0, 200.0, ld(2026, 12, 1), 11)
    const yes = Assistant.answer('¿Puedo gastarme 250 € este fin de semana?', d, safe, [], fmt)
    expect(yes.text.startsWith('Sí'), yes.text).toBe(true)
    expect(yes.sources.length).toBeGreaterThan(0)
    const no = Assistant.answer('¿me puedo permitir 9000 €?', d, safe, [], fmt)
    expect(no.text.startsWith('No'), no.text).toBe(true)
    const save = Assistant.answer('¿Qué tres cambios me permitirían ahorrar 100 € más?', d, safe, [], fmt)
    expect(save.text, save.text).toContain('1.')
    const rec = Assistant.answer('He pagado 18 € en una cena y 12 € en gasolina', d, safe, [], fmt)
    expect(rec.expenses.length).toBe(2)
  })
})
