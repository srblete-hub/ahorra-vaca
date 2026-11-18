// Pestaña Inicio (HomeScreen.kt) y las pantallas que se abren desde ella: "¿Me lo compro?" y el chat.
import { useEffect, useMemo, useState } from 'react'
import { db, toast, useDB, useSettings } from '../../data/store'
import { plusMonths, sameMonth, thisMonth, today as todayDate, todayEpoch, ymOfEpoch } from '../../data/dates'
import type { Settings } from '../../data/models'
import {
  Anomalies, BuyChecker, CashPlanner, GoalList, IncomeMath, LimitAlerts, PaymentOrganizer, PepeSuggest, planningUpdates, PulseCalculator,
  SubscriptionChecker, TodayLeft, TrendDetector, UpcomingBills, useMonthIncome, usePlanning, WeeklyReader, isSaving,
} from '../../domain'
import type { BuyCheck } from '../../domain'
import { fixed, lenOfMonth } from '../../domain/kt'
import { SERIES_COLORS, useMoney } from '../components'
import { ScreenHeader } from '../header'
import { Icon } from '../icons'
import { useNav } from '../nav'
import { BankHero, SetupCard } from './home/hero'
import { PulseHeader, PulseInfoSheet } from './home/pulse'
import { PepeCard, PepeChat, PepeTipCard } from './home/pepe'
import { CashPlanCard } from './home/cashplan'
import {
  AnomaliesCard, BillCalendarCard, ExpectedIncomeNudge, LimitAlertCard, SubscriptionCheckCard, SubscriptionSheet, TrendsCard, UpcomingBillsCard, WeeklyReadCard,
} from './home/cards'
import { FinancialAgeMini, GoalsSection, MoneyWorkingSection, suggestedMonthly } from './home/goals'
import { BuyCheckCard, BuyCheckSheet } from './home/buycheck'
import {
  AccountsCard, DetectedCard, FutureMiniCard, HoldingsList, LevelCard, MonthFlowCard, MonthMovesList, MonthSelector, NextIncomeCard, RecentMovesCard,
  SafeToSpendCard, ShortcutCard, WhereDidItGoCard,
} from './home/lists'
import { BrandCard, SectionTitle } from './home/shared'

export { PurchaseScreen } from './home/purchase'
export { ChatScreen } from './home/chat'

function greeting(): string {
  const h = new Date().getHours()
  return h >= 6 && h <= 13 ? 'Buenos días' : h >= 14 && h <= 20 ? 'Buenas tardes' : 'Buenas noches'
}

export function HomeScreen() {
  const nav = useNav()
  const s = useSettings()
  const m = useMoney()
  const p = usePlanning()
  const accounts = useDB((x) => x.accounts)
  const categories = useDB((x) => x.categories)
  const allExpenses = useDB((x) => x.expenses)
  const allIncomes = useDB((x) => x.incomes)
  const transfers = useDB((x) => x.transfers)
  const crypto = useDB((x) => x.crypto)
  const stocks = useDB((x) => x.stocks)
  const snapshots = useDB((x) => x.snapshots)

  const [month, setMonth] = useState(thisMonth)
  const monthIncome = useMonthIncome(month)
  const [pulseInfo, setPulseInfo] = useState<string | null>(null)
  const [buyCheck, setBuyCheck] = useState<BuyCheck | null>(null)
  const [pepeAsk, setPepeAsk] = useState<string | null>(null)
  const [subsOpen, setSubsOpen] = useState(false)

  const now = todayDate()
  const isCurrent = sameMonth(month, thisMonth())
  const wealth = p.wealth
  const hasMoney = accounts.length > 0 || crypto.length > 0 || stocks.length > 0
  const monthExpenses = useMemo(() => allExpenses.filter((e) => sameMonth(ymOfEpoch(e.epochDay), month)), [allExpenses, month])
  const monthIncomes = useMemo(() => allIncomes.filter((i) => sameMonth(ymOfEpoch(i.epochDay), month)), [allIncomes, month])
  const income = monthIncome.total
  const allowed = income * categories.filter((c) => !c.isSavings).reduce((a, c) => a + c.percent, 0) / 100
  const savingsPct = categories.filter((c) => c.isSavings).reduce((a, c) => a + c.percent, 0)

  // Lo que el ViewModel hace al abrir: foto diaria del patrimonio y lo que guarda tras cada cálculo.
  useEffect(() => { if (hasMoney) db.recordNetWorth(wealth.total) }, [wealth.total, hasMoney])
  useEffect(() => {
    const { patch } = planningUpdates(p, s)
    delete patch.achievements // los logros nuevos los celebra Perfil
    if (Object.keys(patch).length > 0) db.setSettings(patch)
  }, [p]) // eslint-disable-line react-hooks/exhaustive-deps

  const derived = useMemo(() => {
    const d = p.data
    const pulse = PulseCalculator.of(d, p.budget, p.bankPlan, m.fmt)
    const len = lenOfMonth(now)
    const alerts = p.budget ? LimitAlerts.check(now, d.expenses, d.bills, d.savingIds, {
      perDay: p.bankPlan?.perDay ?? p.safe.perDay,
      weekOptimal: p.budget.base / len * 7,
      tagMonthly: Object.fromEntries(p.budget.items.filter((it) => it.monthly >= 20).map((it) => [it.tag, it.monthly])),
    }, m.fmt) : []
    const cash = CashPlanner.of(d, s.bankBuffer)
    const left = TodayLeft.of(d, cash)
    const tip = cash ? PepeSuggest.of(d, cash, p.goals[0] ?? null, m.fmt) : null
    const upcoming = UpcomingBills.of(d)
    const bankTotal = d.accounts.filter((a) => a.type === 'BANK').reduce((a, x) => a + x.balance, 0)
    const savingsTarget = d.accounts.filter((a) => isSaving(a)).reduce<typeof d.accounts[number] | null>((best, a) => (!best || a.balance > best.balance ? a : best), null)
    const weekly = WeeklyReader.of(d, m.fmt)
    const subs = SubscriptionChecker.of(d)
    const swapText = GoalList.swapImpact(p.goalList, d, p.bankPlan, m.fmt)
    const anomalies = Anomalies.find(d, m.fmt)
    const calendar = PaymentOrganizer.analyze(d, p.bankPlan, m.fmt)
    const trends = TrendDetector.find(d, m.fmt)
    return { pulse, alerts, cash, todayLeft: left ? left[1] : null, tip, upcoming, bankTotal, savingsTarget, weekly, subs, swapText, anomalies, calendar, trends }
  }, [p, m.hidden, s.bankBuffer]) // eslint-disable-line react-hooks/exhaustive-deps

  const weekKey = PepeSuggest.weekKey(now)
  const nudges = now.day >= 3 ? p.data.sources.filter((src) =>
    src.variable && IncomeMath.confirmedFor(src, thisMonth()) == null && now.day <= src.dayTo &&
    !allIncomes.some((i) => i.sourceId === src.id && sameMonth(ymOfEpoch(i.epochDay), thisMonth()))) : []
  const nextIncome = isCurrent ? monthIncome.sources.filter((x) => !x.isReceived).sort((a, b) => a.source.dayFrom - b.source.dayFrom)[0] ?? null : null
  const detected = monthExpenses.find((e) => e.fromCard && Date.now() - e.createdAt < 24 * 3600_000) ?? null
  const usesPaste = allExpenses.some((e) => e.fromCard || e.sourceApp !== '')

  const moveGoal = (i: number, by: number) => {
    const list = GoalList.parse(s.goalsRaw)
    const to = i + by
    if (i < 0 || i >= list.length || to < 0 || to >= list.length) return
    const [g] = list.splice(i, 1)
    list.splice(to, 0, g)
    db.setSettings({ goalsRaw: GoalList.format(list) })
  }
  const toggleReviewed = (key: string) => {
    const set = new Set(s.reviewedSubs)
    if (set.has(key)) set.delete(key); else set.add(key)
    db.setSettings({ reviewedSubs: [...set] } as Partial<Settings>)
  }

  return (
    <div className="screen hm">
      <ScreenHeader title={s.userName.trim() ? `Hola, ${s.userName.trim()}` : greeting()} />

      {!hasMoney ? <SetupCard /> : (
        <>
          <BankHero wealth={wealth} accounts={accounts} snapshots={snapshots} todayLeft={derived.todayLeft} />

          <PulseHeader p={derived.pulse} onOpen={setPulseInfo} />
          {derived.alerts.map((a) => <LimitAlertCard key={a.key} a={a} />)}

          <PepeCard onAsk={(q) => setPepeAsk(q)} />

          {derived.upcoming.length > 0 && <UpcomingBillsCard bills={derived.upcoming} bank={derived.bankTotal} today={p.data.today} />}

          {derived.cash && (
            <CashPlanCard c={derived.cash} onCushion={(v) => db.setSettings({ bankBuffer: v })}
              onMoveFromSavings={derived.savingsTarget && !s.savingsLocked ? () => nav.edit({ name: 'transfer', fromAccountId: derived.savingsTarget!.id }) : null} />
          )}
          {derived.tip && s.weeklyTip && s.pepeTipHiddenWeek !== weekKey && (
            <PepeTipCard tip={derived.tip}
              onMove={() => {
                const t = derived.tip!
                db.saveTransfer({ fromAccountId: t.fromId, toAccountId: t.toId, amount: t.amount, note: 'Pepe: al ahorro', epochDay: todayEpoch(), createdAt: Date.now() })
                toast(`Apuntado: ${fixed(t.amount, 0)} € al ahorro`)
              }}
              onLater={() => db.setSettings({ pepeTipHiddenWeek: weekKey })} />
          )}
          {derived.weekly && (
            <WeeklyReadCard r={derived.weekly} onLimit={(tag) => nav.edit({ name: 'tagLimit', tag })} onDetails={(tag) => nav.edit({ name: 'tagDetail', tag })} />
          )}
          {derived.subs && (
            <SubscriptionCheckCard c={derived.subs} pending={derived.subs.actions.filter((a) => !s.reviewedSubs.includes(a.key)).length} onOpen={() => setSubsOpen(true)} />
          )}

          <GoalsSection goals={p.goals} swapText={derived.swapText} onAdd={() => nav.edit({ name: 'goal' })} onEdit={(i) => nav.edit({ name: 'goal', index: i })} onMove={moveGoal} />
          <FinancialAgeMini age={p.age} onClick={() => nav.open({ name: 'age' })} />
          <MoneyWorkingSection accounts={accounts} available={wealth.available} monthly={suggestedMonthly(income, savingsPct)} />

          <BuyCheckCard onCheck={(n, pr) => setBuyCheck(BuyChecker.of(n, pr, p.data, p.bankPlan, p.budget, p.goals[0] ?? null, m.fmt))} />

          <RecentMovesCard expenses={allExpenses} incomes={allIncomes} transfers={transfers} accounts={accounts} />

          {nudges.map((src) => <ExpectedIncomeNudge key={src.id} source={src} onSet={() => nav.edit({ name: 'expectedIncome', sourceId: src.id, month: thisMonth() })} />)}

          {derived.anomalies.length > 0 && <AnomaliesCard list={derived.anomalies} onOpen={(a) => a.expense && nav.edit({ name: 'expense', expense: a.expense })} />}
          {derived.calendar?.crunch && <BillCalendarCard c={derived.calendar} />}
          {derived.trends.length > 0 && <TrendsCard list={derived.trends} />}

          <div className="grid2">
            <ShortcutCard title="¿Me lo compro?" sub="Mira cómo te afecta" icon="cart" color={SERIES_COLORS[4]} onClick={() => nav.open({ name: 'purchase' })} />
            <ShortcutCard title="Análisis" sub="Salud, fugas, hábitos…" icon="insights" color={SERIES_COLORS[1]} onClick={() => nav.open({ name: 'insights' })} />
          </div>

          <SectionTitle>Cuentas</SectionTitle>
          <AccountsCard accounts={accounts} crypto={crypto} stocks={stocks} total={wealth.total} />
          <div className="card tight clickable" role="button" onClick={() => nav.setTab('WEALTH')}>
            <div className="row">
              <div className="grow">
                <div style={{ fontSize: 20, fontWeight: 500 }}>Ver gráficos</div>
                <div className="small muted">Por cuenta y evolución de tu patrimonio</div>
              </div>
              <Icon name="chevronRight" style={{ color: 'var(--muted)' }} />
            </div>
          </div>

          {crypto.length > 0 && (
            <>
              <SectionTitle>Tus criptomonedas</SectionTitle>
              <HoldingsList items={crypto.map((c) => ({ symbol: c.symbol, name: c.name, qty: c.amount, avg: c.avgBuyPrice, price: c.price }))} onClick={() => nav.openInvest('CRYPTO')} />
            </>
          )}
          {stocks.length > 0 && (
            <>
              <SectionTitle>Tus acciones</SectionTitle>
              <HoldingsList items={stocks.map((x) => ({ symbol: x.ticker, name: x.name || x.ticker, qty: x.shares, avg: x.avgBuyPrice, price: x.price }))} onClick={() => nav.openInvest('STOCKS')} />
            </>
          )}

          {p.bankPlan == null && <SafeToSpendCard s={p.safe} />}
          <FutureMiniCard points={p.forecast.points.slice(0, 31)} onOpen={() => nav.open({ name: 'future' })} />
          <WhereDidItGoCard c={p.comparison} />
          <LevelCard level={p.level} points={p.points} achievements={p.achievements} />
        </>
      )}

      <SectionTitle>{isCurrent ? 'Este mes' : 'Ese mes'}</SectionTitle>
      <MonthSelector month={month} onShift={(by) => setMonth((cur) => plusMonths(cur, by))} />
      <MonthFlowCard received={monthIncome.received} spent={monthExpenses.reduce((a, e) => a + e.amount, 0)} spendLimit={income > 0 ? allowed : 0}
        estimated={monthIncome.sources.some((x) => x.isEstimate)} />

      {nextIncome && <NextIncomeCard sm={nextIncome} month={month} accounts={accounts} />}

      {!usesPaste && (
        <BrandCard>
          <div className="hm-brand-title">Pega el aviso<br /><span style={{ color: '#34C77B' }}>y se apunta solo.</span></div>
          <div className="small" style={{ marginTop: 8, color: 'rgba(255,255,255,.8)' }}>
            Copia la notificación de tu banco o de Apple Pay después de pagar y pégala aquí: Ahorra, vaca saca el comercio y el importe, lo añade a tus movimientos y lo descuenta de tu cuenta.
          </div>
          <button className="hm-white-btn" onClick={() => nav.edit({ name: 'pastePayment' })}><Icon name="creditCard" size={16} /> Pegar aviso</button>
        </BrandCard>
      )}

      {detected && <DetectedCard e={detected} categories={categories} />}

      <MonthMovesList expenses={monthExpenses} incomes={monthIncomes} categories={categories} accounts={accounts} />

      {pulseInfo && <PulseInfoSheet k={pulseInfo} p={derived.pulse} onClose={() => setPulseInfo(null)} />}
      {buyCheck && <BuyCheckSheet c={buyCheck} onClose={() => setBuyCheck(null)} />}
      {pepeAsk != null && (
        <PepeChat ctx={{ d: p.data, cash: derived.cash, goal: p.goals[0] ?? null, userName: s.userName, fmt: m.fmt }} first={pepeAsk.trim() || null} onClose={() => setPepeAsk(null)} />
      )}
      {subsOpen && derived.subs && (
        <SubscriptionSheet c={derived.subs} reviewed={s.reviewedSubs} savingsHealth={SubscriptionChecker.savingsHealth(p.data)} onToggle={toggleReviewed} onClose={() => setSubsOpen(false)} />
      )}
    </div>
  )
}

