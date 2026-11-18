// Port de FriendsWrappedTest.kt (la prueba de NewsService.isAd no es del dominio y se queda fuera).
import { describe, expect, it } from 'vitest'
import { ld, plusMonths } from '../../data/dates'
import { Debts, makeDebt } from '../Debts'
import { IncomeMath, InterestEarned, InterestMath } from '../FinanceMath'
import { makeFinanceData } from '../Insights'
import { Wrapped } from '../Wrapped'
import { Budgeting } from '../Budget'
import { Streaks } from '../Streak'
import { Merchants, Duplicates } from '../Merchants'
import { Classifier } from '../Spend'
import { Stats } from '../Stats'
import { BuyChecker } from '../BuyCheck'
import { CryptoMoves } from '../CryptoMoves'
import { PepeBot } from '../PepeBot'
import { Pepe } from '../Pepe'
import { NewsImpactAnalyzer } from '../NewsImpact'
import { BizumParser } from '../BizumParser'
import { minusD, range, ymOfDate } from '../kt'
import { acc, atDay, cat, crypto, ed, exp, fmt, inc, src, transfer } from './helpers'

describe('FriendsWrappedTest', () => {
  const today = ld(2026, 11, 20)

  it('tricountBalanceReplacesWhatIsAlreadyInside', () => {
    const d1 = makeDebt(1, 'Juanpa', 8.0, 'Pádel', ed(minusD(today, 5)), 10, true)
    const d2 = makeDebt(2, 'Juanpa', 5.0, 'Café', ed(minusD(today, 4)))
    const d3 = makeDebt(3, 'juanpa', 6.0, 'Cena', ed(today), null, true)
    const debts = [d1, d2, d3]
    expect(Debts.parse(Debts.format(debts))).toEqual(debts)
    // Sin Tricount: suma todo.
    const all = Debts.balances(debts, {})
    expect(all.length).toBe(1)
    expect(all[0].net).toBeCloseTo(19.0, 3)
    // Tricount dice que le debo 20 € (a 2 días de hoy): el pádel ya está dentro; el café y la cena (posterior) se suman.
    const tri = { Juanpa: { amount: -20.0, epochDay: ed(minusD(today, 2)) } }
    expect(Debts.parseTricount(Debts.formatTricount(tri))).toEqual(tri)
    const b = Debts.balances(debts, tri)
    expect(b.length).toBe(1)
    expect(b[0].net).toBeCloseTo(-20.0 + 5.0 + 6.0, 3)
    expect([...new Set(Debts.people(debts, tri).map((p) => p.toLowerCase()))]).toEqual(['juanpa'])
  })

  it('confirmedVariableIncomeIsUsedAsIs', () => {
    const yt = src(2, 'YouTube', true, 200.0, 22, 22, { accountId: 1 })
    const m = { year: 2026, month: 11 }
    const past = range(1, 3).map((i) => inc(i, 2, 400.0, 'YouTube', ed(atDay(plusMonths(m, -i), 22)), 1))
    IncomeMath.confirmed = {}
    expect(IncomeMath.forMonth([yt], past, m).total).toBeCloseTo(400.0, 3)
    IncomeMath.confirmed = { [IncomeMath.key(2, m)]: 290.0 }
    const mi = IncomeMath.forMonth([yt], past, m)
    expect(mi.total).toBeCloseTo(290.0, 3)
    expect(mi.sources[0].confirmed!).toBeCloseTo(290.0, 3)
    IncomeMath.confirmed = {}
  })

  it('compoundInterestCountsEachDepositFromItsDate', () => {
    const account = acc(2, 'Robinhood', 'BANK_SAVINGS', 450.0, { interestRate: 5.0 })
    const start = minusD(today, 61)
    const transfers = [transfer(1, 1, 2, 200.0, ed(start)), transfer(2, 1, 2, 250.0, ed(minusD(today, 30)))]
    const e = InterestEarned.of(account, [], transfers, today, start)
    const r = InterestMath.monthlyRate(5.0)
    const expected = 200 * (Math.pow(1 + r, 61 / 30.4375) - 1) + 250 * (Math.pow(1 + r, 30 / 30.4375) - 1)
    expect(e.earned).toBeCloseTo(expected, 3)
    expect(e.estimated).toBe(true)
    // Mucho menos que "saldo × 5 %/año".
    expect(e.earned).toBeLessThan(450 * 0.05 / 2)
  })

  it('wrappedSummarisesTheMonth', () => {
    const cats = [cat(1, 'Necesidades', 50.0), cat(3, 'Ahorro', 20.0, { isSavings: true })]
    const m = ymOfDate(today)
    const e = [
      exp(1, 1, 16.0, 'Pádel', ed(atDay(m, 3))),
      exp(2, 1, 16.0, 'Pádel', ed(atDay(m, 10))),
      exp(3, 1, 60.0, 'Mercadona', ed(atDay(m, 12))),
      exp(4, 1, 30.0, 'Mercadona', ed(atDay(plusMonths(m, -1), 5))),
    ]
    const d = makeFinanceData({
      today, accounts: [acc(1, 'Santander', 'BANK', 700.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 450.0)],
      crypto: [], stocks: [], categories: cats, expenses: e, incomes: [inc(1, null, 1500.0, 'Nómina', ed(atDay(m, 2)), 1)], sources: [], bills: [], snapshots: [],
    })
    const w = Wrapped.of(d, m, [transfer(1, 1, 2, 250.0, ed(atDay(m, 4)))])
    expect(w.spent).toBeCloseTo(92.0, 3)
    expect(w.left).toBeCloseTo(1500.0 - 92.0, 3)
    expect(w.topPlace![0]).toBe('Mercadona')
    expect(w.biggest!.amount).toBeCloseTo(60.0, 3)
    expect(w.saved).toBeCloseTo(250.0, 3)
    expect(w.bank).toBeCloseTo(700.0, 3)
    expect(w.daysElapsed).toBe(20)
    expect(w.noSpendDays).toBe(17)
    expect(w.prevSpent!).toBeCloseTo(30.0, 3)
    expect(Wrapped.of(d, plusMonths(m, -1)).prevSpent).toBeNull()
  })

  it('newsImpactUsesYourNumbers', () => {
    const d = makeFinanceData({
      today,
      accounts: [acc(1, 'Santander', 'BANK', 700.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 3000.0, { interestRate: 5.0 })],
      crypto: [crypto(1, 'ethereum', 'ETH', 'Ethereum', 1.0, 2000.0, 2500.0)],
      stocks: [], categories: [cat(1, 'Necesidades', 50.0)], expenses: [], incomes: [], sources: [], bills: [], snapshots: [],
    })
    const eth = NewsImpactAnalyzer.analyze('Ethereum cae un 7 % tras la salida de fondos', '', d, fmt)
    expect(eth.relevant).toBe(true)
    expect(eth.headline).toBe('Puede restarte dinero')
    expect(eth.lines.some((l) => l.includes('perderías unos 175.00 €') && l.includes('6025.00 €')), JSON.stringify(eth.lines)).toBe(true)
    const bce = NewsImpactAnalyzer.analyze('El BCE baja los tipos de interés', '', d, fmt)
    expect(bce.lines.some((l) => l.startsWith('Robinhood') && l.includes('12.50 €') && l.includes('0,25 puntos menos')), JSON.stringify(bce.lines)).toBe(true)
    const other = NewsImpactAnalyzer.analyze('Apple presenta un nuevo iPhone', '', d, fmt)
    expect(other.relevant).toBe(false)
  })

  it('rentIsMonthlyFixedAndStreakCountsDays', () => {
    const cats = [cat(1, 'Necesidades', 50.0)]
    const m = ymOfDate(today)
    const e = [
      exp(1, 1, 700.0, 'Alquiler noviembre', ed(atDay(m, 1))),
      exp(2, 1, 300.0, 'Transferencia casero piso', ed(atDay(m, 2)), { tag: 'HOME' }),
      exp(3, 1, 20.0, 'Mercadona', ed(minusD(today, 1))),
      exp(4, 1, 80.0, 'Zara', ed(minusD(today, 3))),
      exp(5, 1, 10.0, 'Café', ed(today)),
    ]
    const d = makeFinanceData({ today, accounts: [acc(1, 'Santander', 'BANK', 900.0)], crypto: [], stocks: [], categories: cats, expenses: e, incomes: [], sources: [], bills: [], snapshots: [] })
    expect(Budgeting.isFixed(e[0], [])).toBe(true)
    expect(Budgeting.isFixed(e[1], [])).toBe(true)
    expect(Budgeting.isFixed(e[2], [])).toBe(false)
    // El resumen del mes (Wrapped) no cuenta el alquiler, pero lo dice aparte.
    const w = Wrapped.of(d, m)
    expect(w.spent).toBeCloseTo(110.0, 3)
    expect(w.fixed).toBeCloseTo(1000.0, 3)
    // Racha con 30 €/día: hoy (10) y ayer (20) bien, anteayer sin gastos, hace 3 días 80 € → se rompe.
    const s = Streaks.of(d, 30.0)
    expect(s.current).toBe(3)
    expect(s.today).toBe('OK')
    expect(s.nextGoal).toBe(7)
  })

  it('merchantsAreCleanedAndDuplicatesFound', () => {
    expect(Merchants.clean('MPVinted*')).toBe('Vinted')
    expect(Merchants.clean('PAYPAL *STEAM GAMES')).toBe('Steam')
    expect(Merchants.clean('AMZN Mktp ES*2K4AB')).toBe('Amazon')
    expect(Merchants.clean('SUMUP *BAR PEPE')).toBe('Bar Pepe')
    expect(Merchants.clean('MERCADONA 2345 MADRID')).toBe('Mercadona')
    expect(Merchants.clean('CAFETERIA LUNA 23')).toBe('Cafeteria Luna')
    expect(Classifier.guess('MPVinted*')).toBe('SHOPPING')
    expect(Classifier.guess('TELPARK')).toBe('TRANSPORT')
    const t = 1_000_000
    const a = exp(1, 1, 12.5, 'Bar', 100, { createdAt: t })
    const b = exp(2, 1, 12.5, 'Bar', 100, { createdAt: t + 30_000, fromCard: true })
    const c = exp(3, 1, 12.5, 'Bar', 100, { createdAt: t + 5 * 60_000 })
    expect(Duplicates.extras([a, b, c])).toEqual([b])
  })

  it('statsCompareWithThePreviousPeriod', () => {
    const m = ymOfDate(today)
    const e = [
      exp(1, 1, 60.0, 'Mercadona', ed(atDay(m, 3))),
      exp(2, 1, 40.0, 'Starbucks', ed(atDay(m, 10))),
      exp(3, 1, 700.0, 'Alquiler', ed(atDay(m, 1))),
      exp(4, 1, 80.0, 'Mercadona', ed(atDay(plusMonths(m, -1), 5))),
    ]
    const d = makeFinanceData({ today, accounts: [], crypto: [], stocks: [], categories: [cat(1, 'Necesidades', 50.0)], expenses: e, incomes: [], sources: [], bills: [], snapshots: [] })
    const s = Stats.of(d, 0)
    expect(s.total).toBeCloseTo(100.0, 3)
    expect(s.prev).toBeCloseTo(80.0, 3)
    expect(s.changePct!).toBeCloseTo(25.0, 3)
    expect(s.byTag[0][0]).toBe('SUPER')
    expect(Merchants.logoUrl('STARBUCKS 0123 MADRID')).toBe('https://www.google.com/s2/favicons?domain=starbucks.es&sz=128')
    expect(Merchants.logoUrl('Bar Pepe')).toBeNull()
  })

  it('buyCheckCountsWorkHours', () => {
    const m = ymOfDate(today)
    const d = makeFinanceData({
      today, accounts: [acc(1, 'Santander', 'BANK', 900.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 3000.0, { interestRate: 5.0 })],
      crypto: [], stocks: [], categories: [cat(1, 'Necesidades', 50.0)],
      expenses: range(1, 4).map((i) => exp(i, 1, 2.2, 'Monster Energy', ed(minusD(today, i)))),
      incomes: [inc(1, null, 1600.0, 'Nómina', ed(atDay(m, 2)), 1)], sources: [], bills: [], snapshots: [],
    })
    const c = BuyChecker.of('Camiseta Eme Studio', 25.0, d, null, null, null, fmt)
    expect(c.tag).toBe('SHOPPING')
    // 1.600 € / 160 h = 10 €/h → 25 € son 2 h 30 min.
    expect(c.facts.find((f) => f.title === 'Horas de trabajo')!.value).toBe('2 h 30 min')
    expect(c.facts.some((f) => f.value === '= 11 × Monster Energy')).toBe(true)
    expect(c.invested[0][1]).toBeCloseTo(25 * 1.05, 3)
  })

  it('cryptoMovesAlertOnBigChangesOnly', () => {
    const btc = crypto(1, 'bitcoin', 'BTC', 'Bitcoin', 0.5, 50000.0)
    const first = CryptoMoves.check([btc], { bitcoin: 60000.0 }, {})
    expect(first[0].length).toBe(0) // primera vez: solo guarda la referencia
    const small = CryptoMoves.check([btc], { bitcoin: 61000.0 }, first[1])
    expect(small[0].length).toBe(0) // +1,7 %: no avisa
    const big = CryptoMoves.check([btc], { bitcoin: 54000.0 }, first[1])
    expect(big[0].length).toBe(1) // −10 %: avisa
    expect(big[0][0].change).toBeCloseTo(-0.10, 4)
    expect(big[0][0].value).toBeCloseTo(27000.0, 2)
    expect(big[0][0].delta).toBeCloseTo(-3000.0, 2)
    expect(big[1].bitcoin).toBeCloseTo(54000.0, 2) // la referencia pasa al precio nuevo
  })

  it('pepeBotAnswersInsideTheApp', () => {
    const m = ymOfDate(today)
    const d = makeFinanceData({
      today, accounts: [acc(1, 'Santander', 'BANK', 900.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 3000.0, { interestRate: 5.0 })],
      crypto: [], stocks: [], categories: [cat(1, 'Necesidades', 50.0)],
      expenses: [exp(1, 1, 60.0, 'Mercadona', ed(atDay(m, 1))), exp(2, 1, 25.0, 'Cines Yelmo', ed(atDay(m, 1)))],
      incomes: [], sources: [], bills: [], snapshots: [],
    })
    const ctx = { d, cash: null, goal: null, userName: 'Pablo', fmt }
    expect(PepeBot.answer('hola', ctx).startsWith('¡Buenas, Pablo!')).toBe(true)
    expect(PepeBot.answer('¿Cuánto tengo en el banco?', ctx)).toContain('900.00 €')
    expect(PepeBot.answer('¿cuánto gasto en ocio?', ctx)).toContain('25.00 €')
    expect(PepeBot.answer('¿Cómo va mi cartera?', ctx).startsWith('No tienes inversiones')).toBe(true)
    expect(PepeBot.answer('¿qué tiempo hace?', ctx).startsWith('Esa no me la sé')).toBe(true)
  })

  it('pepeBriefHasNumbersAndQuestion', () => {
    const m = ymOfDate(today)
    const d = makeFinanceData({
      today, accounts: [acc(1, 'Santander', 'BANK', 900.0), acc(2, 'Robinhood', 'BANK_SAVINGS', 3000.0, { interestRate: 5.0 })],
      crypto: [], stocks: [], categories: [cat(1, 'Necesidades', 50.0)],
      expenses: [exp(1, 1, 60.0, 'Mercadona', ed(atDay(m, 3))), exp(2, 1, 700.0, 'Alquiler', ed(atDay(m, 1)))],
      incomes: [], sources: [], bills: [], snapshots: [],
    })
    const t = Pepe.brief('¿En qué puedo recortar?', 'Pablo', d, null, null, null, ['YouTube ~290.00 € el día 22'], fmt)
    expect(t.startsWith('Eres Pepe, un pastor')).toBe(true)
    expect(t).toContain('Santander 900.00 €')
    expect(t).toContain('Robinhood 3000.00 € al 5,0 % TAE')
    expect(t).toContain('Por llegar: YouTube ~290.00 € el día 22')
    expect(t).toContain('sin alquiler ni fijos): 60.00 €. Alquiler y fijos: 700.00 €')
    expect(t.endsWith('Mi pregunta: ¿En qué puedo recortar?')).toBe(true)
  })

  it('moreCategoriesAndBizum', () => {
    expect(Classifier.guess('CINES YELMO PLAZA')).toBe('LEISURE')
    expect(Classifier.guess('Cine con Juanpa')).toBe('LEISURE')
    expect(Classifier.guess('Futbol 7 jueves')).toBe('SPORT')
    expect(Classifier.guess('PLAYTOMIC*CLUB PADEL')).toBe('SPORT')
    expect(Classifier.guess('Pista futbol sala')).toBe('SPORT')
    const incoming = BizumParser.parse('Bizum recibido', 'Has recibido un Bizum de 8,00 € de JUAN PABLO GARCIA. Concepto: pádel')!
    expect(incoming.incoming).toBe(true)
    expect(incoming.amount).toBeCloseTo(8.0, 3)
    expect(incoming.person).toBe('Juan Pablo Garcia')
    expect(incoming.concept).toBe('pádel')
    const out = BizumParser.parse('Bizum', 'Has enviado 12,50 € a MARIA LOPEZ por Bizum')!
    expect(out.incoming).toBe(false)
    expect(out.person).toBe('Maria Lopez')
    expect(BizumParser.parse('Bizum', 'MARIA te pide 10,00 € por Bizum')).toBeNull()
  })
})
