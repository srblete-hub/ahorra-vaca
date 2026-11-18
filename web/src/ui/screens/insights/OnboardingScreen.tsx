// Port de ui/screens/OnboardingScreen.kt: el asistente de la primera vez. Va pidiendo cada cosa con la opción de saltarla.
// Al terminar marca onboardingDone; si aún no dijo cuán ahorrador quiere ser, App abre después "Tu forma de ahorrar".
import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { db, useDB } from '../../../data/store'
import { ACCOUNT_TYPES, type Account, type AccountType, type Category } from '../../../data/models'
import { num } from '../../../data/format'
import { thisMonth, today, toEpochDay, lengthOfMonth, type YearMonth } from '../../../data/dates'
import { ALLOCATION_PRESET_INFO, ALLOCATION_PRESET_VALUES, Allocation, InterestMath, useMonthIncome, type SourceMonth } from '../../../domain'
import { BigAmount, Button, Card, IconTile, Initials, ListRow, SERIES_COLORS, colorFor, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav, type OverlayRoute } from '../../nav'
import './insights.css'

type Step = 'BANK' | 'SAVINGS' | 'INVEST' | 'INCOME' | 'SPLIT' | 'DONE'
const STEPS: Step[] = ['BANK', 'SAVINGS', 'INVEST', 'INCOME', 'SPLIT', 'DONE']
const STEP_INFO: Record<Step, { title: string; subtitle: string }> = {
  BANK: { title: 'Tu dinero en el banco', subtitle: 'Añade tus cuentas con el saldo que tienen hoy. Después se mantienen al día solas con tus pagos y cobros.' },
  SAVINGS: { title: 'Lo que tienes guardado fuera', subtitle: 'Efectivo, la hucha, PayPal, un fondo, un depósito… Si te da intereses, márcalo y verás cuánto te paga al mes.' },
  INVEST: { title: 'Tus inversiones', subtitle: 'Cripto y acciones o ETF. Los precios se actualizan solos.' },
  INCOME: { title: 'Lo que cobras', subtitle: 'Tu nómina fija y lo variable (autónomo, YouTube…) con su día de cobro. Lo variable se estima con tu media.' },
  SPLIT: { title: '¿Cómo quieres repartirlo?', subtitle: 'Elige cuánto quieres ahorrar. Lo puedes cambiar cuando quieras en Presupuesto.' },
  DONE: { title: '¡Listo!', subtitle: 'Esto es lo que tienes de verdad, todo junto.' },
}
const pct = (v: number) => `${num(v, 1)} %`

const TYPE_ICON: Record<AccountType, string> = {
  BANK: 'bank', BANK_SAVINGS: 'savings', CASH: 'wallet', HOME_SAVINGS: 'pig', WALLET: 'wallet', FUND: 'trendingUp', OTHER: 'more', DEBT: 'card',
}

export function OnboardingScreen({ route: _route }: { route: OverlayRoute }) {
  const nav = useNav()
  const m = useMoney()
  const [step, setStep] = useState<Step>('BANK')
  const accounts = useDB((s) => s.accounts)
  const crypto = useDB((s) => s.crypto)
  const stocks = useDB((s) => s.stocks)
  const sources = useDB((s) => s.incomeSources)
  const month = useMemo(() => thisMonth(), [])
  const monthIncome = useMonthIncome(month)
  const body = useRef<HTMLDivElement>(null)
  const idx = STEPS.indexOf(step)

  const finish = () => {
    db.setSettings({ onboardingDone: true })
    nav.setTab('HOME')
    nav.back()
  }
  const next = () => { if (step === 'DONE') finish(); else setStep(STEPS[idx + 1]) }
  useEffect(() => { body.current?.scrollTo({ top: 0 }) }, [step])
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [])

  const info = STEP_INFO[step]
  return createPortal(
    <div className="in-ob" role="dialog" aria-modal="true" aria-label="Configuración inicial">
      <div className="in-ob-inner">
        <div className="in-ob-progress">
          {idx > 0 && <button className="icon-btn" aria-label="Paso anterior" onClick={() => setStep(STEPS[idx - 1])}><Icon name="back" /></button>}
          <div className="bars" aria-label={`Paso ${idx + 1} de ${STEPS.length}`}>
            {STEPS.map((s, i) => <span key={s} className={i <= idx ? 'on' : ''} />)}
          </div>
          {step !== 'DONE' && <button className="link-btn" style={{ color: 'var(--muted)', fontWeight: 600 }} onClick={finish}>Saltar todo</button>}
        </div>

        <div className="in-ob-body" ref={body}>
          <div className="in-over">Paso {idx + 1} de {STEPS.length}</div>
          <h1 className="title" style={{ fontSize: 28, marginTop: -8 }}>{info.title}</h1>
          <div className="muted">{info.subtitle}</div>

          {step === 'BANK' && <>
            <AccountList list={accounts.filter((a) => ACCOUNT_TYPES[a.type].inBank)} />
            <AddButton text="Añadir cuenta del banco" icon="bank" onClick={() => nav.edit({ name: 'account', account: { name: '', type: 'BANK', balance: 0 } })} />
          </>}

          {step === 'SAVINGS' && <>
            <AccountList list={accounts.filter((a) => !ACCOUNT_TYPES[a.type].inBank)} />
            <AddButton text="Añadir ahorro" icon="savings" onClick={() => nav.edit({ name: 'account', account: { name: '', type: 'HOME_SAVINGS', balance: 0 } })} />
          </>}

          {step === 'INVEST' && <>
            {(crypto.length > 0 || stocks.length > 0) && (
              <Card className="tight">
                {crypto.map((c) => <ListRow key={'c' + c.id} leading={<Initials text={c.symbol} color={colorFor(c.symbol)} />} title={c.name} trailing={m.fmt(c.amount * c.price)} />)}
                {stocks.map((st) => <ListRow key={'s' + st.id} leading={<Initials text={st.ticker} color={colorFor(st.ticker)} />} title={st.name.trim() || st.ticker} trailing={m.fmt(st.shares * st.price)} />)}
              </Card>
            )}
            <div className="grid2" style={{ gap: 8 }}>
              <AddButton text="Cripto" icon="bitcoin" onClick={() => nav.edit({ name: 'coinSearch' })} />
              <AddButton text="Acción o ETF" icon="trendingUp" onClick={() => nav.edit({ name: 'stock', holding: { ticker: '', name: '', shares: 0, avgBuyPrice: 0 } })} />
            </div>
          </>}

          {step === 'INCOME' && <>
            {monthIncome.sources.length > 0 && <>
              <Card className="tight">
                {monthIncome.sources.map((sm) => <IncomeSourceRow key={sm.source.id} sm={sm} month={month} accounts={accounts} />)}
              </Card>
              <div style={{ fontWeight: 700, color: 'var(--gain)' }}>Total previsto este mes: {m.fmt(monthIncome.total)}</div>
            </>}
            <AddButton text={sources.length === 0 ? 'Añadir nómina u otro ingreso' : 'Añadir otro ingreso'} icon="add"
              onClick={() => nav.edit({ name: 'incomeSource', source: { name: '', variable: false, amount: 0, dayFrom: 1, dayTo: 5 } })} />
          </>}

          {step === 'SPLIT' && <SplitStep income={monthIncome.total} />}

          {step === 'DONE' && <DoneStep />}
          <div style={{ height: 8 }} />
        </div>

        <div className="in-ob-foot">
          {step !== 'DONE' && <button className="link-btn" style={{ color: 'var(--muted)', fontWeight: 600 }} onClick={next}>Saltar paso</button>}
          <Button onClick={next}>{step === 'DONE' ? '¡A ahorrar!' : 'Siguiente'}</Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

function AddButton({ text, icon, onClick }: { text: string; icon: string; onClick: () => void }) {
  return <button className="in-add" onClick={onClick}><Icon name={icon} />{text}</button>
}

function AccountList({ list }: { list: Account[] }) {
  const nav = useNav()
  const m = useMoney()
  if (list.length === 0) return null
  return (
    <Card className="tight">
      {list.map((a) => (
        <ListRow key={a.id} onClick={() => nav.edit({ name: 'account', account: a })}
          leading={<IconTile icon={TYPE_ICON[a.type]} color={ACCOUNT_TYPES[a.type].inBank ? SERIES_COLORS[0] : SERIES_COLORS[1]} />}
          title={a.name}
          subtitle={a.interestRate > 0 ? `${pct(a.interestRate)} TAE · +${m.fmt(InterestMath.monthlyInterest(a.balance, a.interestRate))}/mes` : ACCOUNT_TYPES[a.type].label}
          trailing={m.fmt(a.balance)} />
      ))}
    </Card>
  )
}

/** Fila de una fuente de ingreso (IncomeSourceRow de BudgetScreen.kt). */
function IncomeSourceRow({ sm, month, accounts }: { sm: SourceMonth; month: YearMonth; accounts: Account[] }) {
  const nav = useNav()
  const m = useMoney()
  const s = sm.source
  const days = s.dayFrom === s.dayTo ? `día ${s.dayFrom}` : `días ${s.dayFrom}–${s.dayTo}`
  const account = accounts.find((a) => a.id === s.accountId)?.name
  const kind = s.variable ? (sm.confirmed != null ? 'Variable · confirmado este mes' : sm.average != null ? 'Variable · media de 3 meses' : 'Variable · estimado') : 'Fijo'
  const markReceived = () => {
    const t = today()
    const day = t.year === month.year && t.month === month.month ? t.day : Math.min(s.dayFrom, lengthOfMonth(month.year, month.month))
    nav.edit({ name: 'income', income: { sourceId: s.id, amount: sm.amount, note: s.name, epochDay: toEpochDay({ ...month, day }), accountId: s.accountId } })
  }
  return (
    <div className="list-item">
      <button className="row grow" style={{ textAlign: 'left', gap: 12 }} onClick={() => nav.edit({ name: 'incomeSource', source: s })}>
        <IconTile icon={s.variable ? 'bolt' : 'work'} color={s.variable ? SERIES_COLORS[2] : SERIES_COLORS[0]} />
        <div className="grow">
          <div className="t ellipsis">{s.name}</div>
          <div className="s">{[kind, days, account].filter(Boolean).join(' · ')}</div>
        </div>
      </button>
      <div className="right" style={{ flexShrink: 0 }}>
        <div className="num" style={{ fontWeight: 700 }}>{(sm.isEstimate ? '~' : '') + m.fmt(sm.amount)}</div>
        {sm.isReceived
          ? <div className="tiny gain" style={{ fontWeight: 600 }}><Icon name="checkCircle" size={12} style={{ verticalAlign: '-1px' }} /> Cobrado</div>
          : <button className="in-cobrado" onClick={markReceived}>Cobrado</button>}
        {!sm.isReceived && s.variable && (
          <button className="in-mini-link" onClick={() => nav.edit({ name: 'expectedIncome', sourceId: s.id, month })}>
            {sm.confirmed != null ? 'Cambiar importe' : '¿Cuánto este mes?'}
          </button>
        )}
      </div>
    </div>
  )
}

function SplitStep({ income }: { income: number }) {
  const m = useMoney()
  const categories = useDB((s) => s.categories)
  const [draft, setDraft] = useState<Category[]>(categories)
  useEffect(() => { setDraft(categories) }, [categories])
  const savings = draft.find((c) => c.isSavings)
  const savingPct = draft.filter((c) => c.isSavings).reduce((a, c) => a + c.percent, 0)
  const save = (list: Category[]) => { setDraft(list); db.saveCategories(list) }
  return (
    <>
      {ALLOCATION_PRESET_VALUES.map((p) => {
        const pi = ALLOCATION_PRESET_INFO[p]
        const selected = savingPct === pi.savings
        return (
          <Card key={p} className={`tight in-preset ${selected ? 'on' : ''}`} onClick={() => save(Allocation.applyPreset(draft, p))}>
            <div style={{ fontWeight: 700, fontSize: 17 }}>{pi.label}</div>
            <div className="small muted">
              {`${Math.trunc(pi.needs)} % necesidades · ${Math.trunc(pi.wants)} % ocio · ${Math.trunc(pi.savings)} % ahorro`}
              {income > 0 ? ` → ahorras ${m.fmt(income * pi.savings / 100)}/mes` : ''}
            </div>
          </Card>
        )
      })}
      {savings && (
        <Card>
          <div className="row" style={{ alignItems: 'baseline' }}>
            <span className="grow" style={{ fontWeight: 700, fontSize: 17 }}>O elige tú cuánto ahorrar</span>
            <span className="num" style={{ fontSize: 24, fontWeight: 700, color: SERIES_COLORS[1] }}>{pct(savings.percent)}</span>
          </div>
          <input type="range" min={0} max={100} step={1} value={savings.percent} aria-label="Porcentaje de ahorro" style={{ accentColor: SERIES_COLORS[1], margin: '12px 0' }}
            onChange={(e) => setDraft(Allocation.rebalance(draft, savings.id, Math.round(+e.target.value)))}
            onPointerUp={() => db.saveCategories(draft)} onTouchEnd={() => db.saveCategories(draft)} onKeyUp={() => db.saveCategories(draft)} onBlur={() => db.saveCategories(draft)} />
          <div className="small muted">{draft.map((c) => `${c.name} ${pct(c.percent)}`).join(' · ')}</div>
        </Card>
      )}
    </>
  )
}

function DoneStep() {
  const m = useMoney()
  const accounts = useDB((s) => s.accounts)
  const crypto = useDB((s) => s.crypto)
  const stocks = useDB((s) => s.stocks)
  const sources = useDB((s) => s.incomeSources)
  const total = accounts.reduce((a, x) => a + x.balance, 0) + crypto.reduce((a, x) => a + x.amount * x.price, 0) + stocks.reduce((a, x) => a + x.shares * x.price, 0)
  const interest = accounts.filter((a) => a.interestRate > 0).reduce((a, x) => a + InterestMath.monthlyInterest(x.balance, x.interestRate), 0)
  return (
    <Card className="in-mint">
      <div className="in-over">Tu patrimonio</div>
      <BigAmount value={total} size={40} style={{ marginTop: 4 }} />
      <div className="muted" style={{ marginTop: 8 }}>{accounts.length} cuentas · {crypto.length + stocks.length} inversiones · {sources.length} ingresos</div>
      {interest > 0 && <div style={{ fontWeight: 700, color: 'var(--gain)' }}>Tus ahorros te dan {m.fmt(interest)} al mes de intereses</div>}
    </Card>
  )
}
