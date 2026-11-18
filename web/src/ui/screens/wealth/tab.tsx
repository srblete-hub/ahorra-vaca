// Port de WealthScreen.kt: la pestaña Patrimonio.
import { useEffect, useMemo, useState } from 'react'
import { ACCOUNT_TYPES, type Account } from '../../../data/models'
import { dayLabel, ofEpochDay, todayEpoch, today as todayDate } from '../../../data/dates'
import { db, useDB, useSettings } from '../../../data/store'
import { refreshIfStale } from '../../../data/services/actions'
import {
  CashPlanner, InterestEarned, PepeSuggest, TypicalSpend, WealthExplainer, isDebt, isSaving, usePlanning,
  type FinanceData, type InterestEarnedResult,
} from '../../../domain'
import { Button, IconTile, LineChart, ProgressLine, SERIES_COLORS, Sheet, gainClass, useMoney } from '../../components'
import { ScreenHeader } from '../../header'
import { Icon } from '../../icons'
import { useNav } from '../../nav'
import { CashPlanCard, GoalsRingList, SavingsGoalEmpty, SavingsGoalHero, SetupCard, TypicalMonthCard } from './cards'
import {
  AVAILABLE_COLOR, AddButton, INVESTED_COLOR, MintCard, Overline, SAVINGS_COLOR, accountTypeIcon, pct1, signedPct1,
} from './common'

type Period = { label: string; days: number | null }
const PERIODS: Period[] = [
  { label: 'Semana', days: 7 }, { label: 'Mes', days: 30 }, { label: '3M', days: 90 }, { label: '6M', days: 180 }, { label: '1A', days: 365 }, { label: 'Todo', days: null },
]

function sessionGet(k: string) { try { return sessionStorage.getItem('av:w:' + k) } catch { return null } }
function sessionSet(k: string, v: string) { try { sessionStorage.setItem('av:w:' + k, v) } catch { /* nada */ } }

export function WealthScreen() {
  const nav = useNav()
  const settings = useSettings()
  const accounts = useDB((s) => s.accounts)
  const crypto = useDB((s) => s.crypto)
  const stocks = useDB((s) => s.stocks)
  const snapshots = useDB((s) => s.snapshots)
  const incomes = useDB((s) => s.incomes)
  const transfers = useDB((s) => s.transfers)
  const expenses = useDB((s) => s.expenses)
  const planning = usePlanning()
  const m = useMoney()
  const [periodIdx, setPeriodIdx] = useState(() => { const v = Number(sessionGet('period')); return v >= 0 && v < PERIODS.length && sessionGet('period') != null ? v : 1 })
  const period = PERIODS[periodIdx]
  const [showWhy, setShowWhy] = useState(false)

  const availableAccounts = accounts.filter((a) => !isSaving(a) && !isDebt(a))
  const debtAccounts = accounts.filter((a) => isDebt(a))
  const savingAccounts = accounts.filter((a) => isSaving(a))
  const sum = (l: Account[]) => l.reduce((a, x) => a + x.balance, 0)
  const available = sum(availableAccounts)
  const saved = sum(savingAccounts)
  const cryptoValue = crypto.reduce((a, c) => a + c.amount * c.price, 0)
  const stockValue = stocks.reduce((a, s) => a + s.shares * s.price, 0)
  const total = available + saved + cryptoValue + stockValue - sum(debtAccounts)
  const hasData = accounts.length > 0 || crypto.length > 0 || stocks.length > 0

  const earnedBy = useMemo(() => {
    const days = [...incomes.map((i) => i.epochDay), ...expenses.map((e) => e.epochDay), ...snapshots.map((s) => s.epochDay)]
    const since = days.length ? ofEpochDay(Math.min(...days)) : todayDate()
    const map = new Map<number, InterestEarnedResult>()
    for (const a of accounts) if (a.interestRate > 0) map.set(a.id, InterestEarned.of(a, incomes, transfers, todayDate(), since))
    return map
  }, [accounts, incomes, transfers, expenses, snapshots])
  const yearlyInterest = [...earnedBy.values()].reduce((a, e) => a + e.earned, 0)

  // Una foto diaria del patrimonio alimenta el gráfico (como LaunchedEffect(total, hasData) en Android).
  useEffect(() => { if (hasData) db.recordNetWorth(total) }, [total, hasData])
  useEffect(() => { refreshIfStale() }, [])

  const today = todayEpoch()
  const inPeriod = snapshots.filter((s) => (period.days == null || s.epochDay >= today - period.days) && s.epochDay !== today)
  const series = [...inPeriod.map((s) => s.total), total]
  const changeBase = series[0]
  const change = total - changeBase
  const fromDay = inPeriod[0]?.epochDay ?? today
  const d = planning.data
  const why = WealthExplainer.explain(d, fromDay, change, settings.dayChanges, m.fmt)

  const buckets = [
    { label: 'Disponible', sub: `${availableAccounts.length} cuentas, efectivo y monederos`, value: available, color: AVAILABLE_COLOR, icon: 'bank', onClick: undefined },
    { label: 'Ahorro', sub: `${savingAccounts.length} sitios` + (yearlyInterest > 0 ? ` · +${m.fmt(yearlyInterest)} de intereses ganados` : ''), value: saved, color: SAVINGS_COLOR, icon: 'savings', onClick: undefined },
    { label: 'Cripto', sub: `${crypto.length} monedas`, value: cryptoValue, color: SERIES_COLORS[2], icon: 'bitcoin', onClick: () => nav.openInvest('CRYPTO') },
    { label: 'Acciones', sub: `${stocks.length} posiciones`, value: stockValue, color: INVESTED_COLOR, icon: 'trendingUp', onClick: () => nav.openInvest('STOCKS') },
  ]

  // Objetivo, mes típico y plan de caja.
  const goal = planning.goals[0] ?? null
  const main = d.accounts.filter((a) => a.type === 'BANK').reduce<Account | null>((a, x) => (a == null || x.balance > a.balance ? x : a), null)
  const savings = d.accounts.filter((a) => isSaving(a)).reduce<Account | null>((a, x) => (a == null || x.balance > a.balance ? x : a), null)
  const cash = useMemo(() => CashPlanner.of(d, settings.bankBuffer), [d, settings.bankBuffer])
  const typical = useMemo(() => TypicalSpend.of(d), [d])
  const tip = goal ? PepeSuggest.of(d, cash, goal, m.fmt) : null
  const weekly = useMemo(() => PepeSuggest.weekly(d, transfers), [d, transfers])

  const groups: Array<[string, Account[]]> = ([['Disponible', availableAccounts], ['Ahorro', savingAccounts], ['Deudas', debtAccounts]] as Array<[string, Account[]]>).filter(([, l]) => l.length > 0)

  return (
    <div className="screen">
      <ScreenHeader title="Patrimonio" />

      <MintCard>
        <div className="w-period" role="tablist">
          {PERIODS.map((p, i) => (
            <button key={p.label} role="tab" aria-selected={i === periodIdx} className={i === periodIdx ? 'on' : ''} onClick={() => { setPeriodIdx(i); sessionSet('period', String(i)) }}>{p.label}</button>
          ))}
        </div>
        <div className="w-net">
          <Overline>Patrimonio neto</Overline>
          <BigNumber value={total} />
          <button className={`w-change ${gainClass(change)}`} onClick={() => setShowWhy(true)}>
            {(change >= 0 ? '↑ ' : '↓ ') + m.signed(change) + (changeBase > 0 ? ` (${signedPct1((change / changeBase) * 100)})` : '') + ` · ${period.label.toLocaleLowerCase('es')}  ›`}
          </button>
          {why && <div className="small muted" style={{ padding: '4px 8px 0', textAlign: 'center' }}>{why}</div>}
        </div>
        <div style={{ marginTop: 8 }}>
          <LineChart values={series} color="var(--gain)" height={112} />
        </div>
      </MintCard>

      <div className="card tight list">
        {buckets.map((b) => (
          <div key={b.label} className={`w-bucket ${b.onClick ? 'clickable' : ''}`} onClick={b.onClick} role={b.onClick ? 'button' : undefined}>
            <IconTile icon={b.icon} color={b.color} />
            <div className="grow">
              <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
                <div className="grow">
                  <div style={{ fontWeight: 600 }}>{b.label}</div>
                  <div className="small muted">{b.sub}</div>
                </div>
                <div className="num" style={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{m.fmt(b.value)}</div>
                {b.onClick && <Icon name="chevronRight" size={24} style={{ color: 'var(--muted)', flexShrink: 0 }} />}
              </div>
              <div className="row" style={{ gap: 8, marginTop: 8 }}>
                <div className="grow"><ProgressLine fraction={total > 0 ? b.value / total : 0} color={b.color} /></div>
                <span className="tiny muted num">{total > 0 ? pct1((b.value / total) * 100) : '—'}</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* ---------- Objetivo de ahorro: cuánto llevas y cuánto falta ---------- */}
      {goal ? (
        <SavingsGoalHero
          g={goal} weekly={weekly} thisWeek={tip?.amount ?? null} locked={settings.savingsLocked} weeklyTip={settings.weeklyTip}
          onWithdraw={() => { if (savings) nav.edit({ name: 'transfer', fromAccountId: savings.id }) }}
          onAdd={() => { if (main) nav.edit({ name: 'transfer', fromAccountId: main.id }) }}
          onLock={() => db.setSettings({ savingsLocked: !settings.savingsLocked })}
          onWeeklyTip={(v) => db.setSettings({ weeklyTip: v })}
          onEdit={() => nav.edit({ name: 'goal', index: 0 })}
        />
      ) : (
        <SavingsGoalEmpty onEdit={() => nav.edit({ name: 'goal' })} />
      )}
      {typical && <TypicalMonthCard t={typical} />}
      {planning.goals.length > 0 && <GoalsRingList goals={planning.goals} onOpen={(i) => nav.edit({ name: 'goal', index: i })} />}
      {cash && (
        <CashPlanCard
          c={cash}
          onCushion={(v) => db.setSettings({ bankBuffer: v })}
          onMoveFromSavings={savings && !settings.savingsLocked ? () => nav.edit({ name: 'transfer', fromAccountId: savings.id }) : undefined}
        />
      )}

      <div className="w-section">
        <h2>Tus cuentas</h2>
        <AddButton onClick={() => nav.edit({ name: 'account', account: { name: '', type: 'BANK', balance: 0 } })} />
      </div>
      {accounts.length === 0 && <SetupCard onStepByStep={() => nav.open({ name: 'onboarding' })} />}
      {groups.map(([label, list]) => (
        <div key={label} className="col">
          <Overline style={{ paddingLeft: 8, paddingTop: 4 }}>{label} · {m.fmt(sum(list))}</Overline>
          <div className="card tight list">
            {list.map((a) => {
              const e = earnedBy.get(a.id)
              const sub = [ACCOUNT_TYPES[a.type].label, a.note.trim() || null].filter(Boolean).join(' · ')
              return (
                <div key={a.id} className="list-item clickable" role="button" onClick={() => nav.open({ name: 'account', accountId: a.id })}>
                  <IconTile icon={accountTypeIcon(a.type)} color={isSaving(a) ? SAVINGS_COLOR : AVAILABLE_COLOR} />
                  <div className="grow">
                    <div className="t ellipsis">{a.name}</div>
                    <div className="s ellipsis">{sub}</div>
                  </div>
                  <div className="right" style={{ flexShrink: 0, maxWidth: '50%' }}>
                    <div className="t num">{m.fmt(a.balance)}</div>
                    {a.interestRate > 0 && (
                      <div className="tiny gain">{pct1(a.interestRate)} · {e?.estimated ? '≈' : ''}+{m.fmt(e?.earned ?? 0)} ganados</div>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      ))}

      {showWhy && <WealthChangeSheet d={d} fromDay={fromDay} change={change} periodLabel={period.label.toLocaleLowerCase('es')} dayChanges={settings.dayChanges} onClose={() => setShowWhy(false)} />}
    </div>
  )
}

/** Importe grande con céntimos pequeños (Amount de 40 sp). */
function BigNumber({ value }: { value: number }) {
  const m = useMoney()
  const text = m.fmt(value)
  const cut = text.lastIndexOf(',')
  return (
    <div className="hero-num w-net-num">
      {m.hidden || cut < 0 ? text : (<>{text.slice(0, cut)}<span className="dec">{text.slice(cut)}</span></>)}
    </div>
  )
}

/** Por qué ha cambiado tu patrimonio: cobros, gastos e inversiones del periodo, con cada movimiento. */
function WealthChangeSheet({ d, fromDay, change, periodLabel, dayChanges, onClose }: {
  d: FinanceData; fromDay: number; change: number; periodLabel: string; dayChanges: Record<string, number>; onClose: () => void
}) {
  const m = useMoney()
  const incomes = d.incomes.filter((i) => i.epochDay > fromDay && i.accountId != null).sort((a, b) => b.epochDay - a.epochDay)
  const spent = d.expenses.filter((e) => e.epochDay > fromDay && e.accountId != null).sort((a, b) => b.epochDay - a.epochDay)
  const inTotal = incomes.reduce((a, i) => a + i.amount, 0)
  const outTotal = spent.reduce((a, e) => a + e.amount, 0)
  const market = change - inTotal + outTotal
  const moves = [
    ...d.crypto.map((c): [string, string, number] => [c.name, c.symbol, c.amount * c.price]),
    ...d.stocks.map((s): [string, string, number] => [s.name.trim() || s.ticker, s.ticker, s.shares * s.price]),
  ].flatMap(([n, s, v]): Array<[string, number, number]> => {
    const pct = dayChanges[s]
    return pct == null ? [] : [[`${n} (${s})`, pct, v - v / (1 + pct / 100)]]
  }).sort((a, b) => Math.abs(b[2]) - Math.abs(a[2]))
  const accName = (id: number | null) => d.accounts.find((a) => a.id === id)?.name ?? ''
  return (
    <Sheet title="Por qué ha cambiado" onClose={onClose}>
      <div className="col" style={{ gap: 8 }}>
        <div className={`num ${gainClass(change)}`} style={{ fontSize: 28, fontWeight: 700 }}>{m.signed(change)} · {periodLabel}</div>
        <WhyLine label="Lo que ha entrado en tus cuentas" value={inTotal} className="gain" />
        <WhyLine label="Lo que has gastado" value={-outTotal} className="loss" />
        <WhyLine label="Inversiones y ajustes de saldo" value={market} className={gainClass(market)} />
        <div className="tiny muted">Las transferencias entre tus cuentas no cambian el total. Si corriges a mano el saldo de una cuenta, la diferencia sale en «ajustes de saldo».</div>
        {moves.length > 0 && (
          <>
            <div className="w-why-h">Tus inversiones hoy (24 h)</div>
            {moves.map(([n, p, v]) => <MoveLine key={n} title={n} sub={`${signedPct1(p)} en 24 h`} value={v} />)}
          </>
        )}
        {incomes.length > 0 && (
          <>
            <div className="w-why-h">Entradas</div>
            {incomes.slice(0, 15).map((i) => <MoveLine key={i.id} title={i.note.trim() || 'Ingreso'} sub={`${dayLabel(i.epochDay)} · ${accName(i.accountId)}`} value={i.amount} />)}
          </>
        )}
        {spent.length > 0 && (
          <>
            <div className="w-why-h">Salidas (las más grandes)</div>
            {[...spent].sort((a, b) => b.amount - a.amount).slice(0, 15).map((e) => <MoveLine key={e.id} title={e.note.trim() || 'Gasto'} sub={`${dayLabel(e.epochDay)} · ${accName(e.accountId)}`} value={-e.amount} />)}
          </>
        )}
        <div style={{ marginTop: 16 }}><Button onClick={onClose}>Entendido</Button></div>
      </div>
    </Sheet>
  )
}

function WhyLine({ label, value, className }: { label: string; value: number; className: string }) {
  const m = useMoney()
  return (
    <div className="row" style={{ padding: '4px 0', alignItems: 'flex-start' }}>
      <span className="grow">{label}</span>
      <span className={`num ${className}`} style={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{m.signed(value)}</span>
    </div>
  )
}

function MoveLine({ title, sub, value }: { title: string; sub: string; value: number }) {
  const m = useMoney()
  return (
    <div className="row" style={{ padding: '4px 0' }}>
      <div className="grow">
        <div className="small ellipsis">{title}</div>
        <div className="tiny muted ellipsis">{sub}</div>
      </div>
      <span className={`small num ${gainClass(value)}`} style={{ fontWeight: 500, whiteSpace: 'nowrap' }}>{m.signed(value)}</span>
    </div>
  )
}
