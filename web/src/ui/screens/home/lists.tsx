// Listas y tarjetas de datos de Inicio: últimos movimientos, cuentas, posiciones, "Este mes", futuro, nivel… (HomeScreen.kt, NowWidgets.kt, BudgetScreen.kt).
import { useMemo, useState } from 'react'
import type { Account, Category, CryptoHolding, Expense, Income, IncomeSource, StockHolding, Transfer } from '../../../data/models'
import type { LocalDate, YearMonth } from '../../../data/dates'
import { capitalize, dayLabel, lengthOfMonth, MONTHS, sameMonth, thisMonth, today as todayDate, toEpochDay } from '../../../data/dates'
import type { Achievement, Level, MonthComparison, SafeToSpend, SourceMonth } from '../../../domain'
import { InterestMath, levelProgress, signedBalance, SPEND_TAG_INFO, spendTag } from '../../../domain'
import { hashCode, Initials, LineChart, ProgressLine, SERIES_COLORS, useMoney, AccountLogo, EmptyState } from '../../components'
import { Icon } from '../../icons'
import { useNav } from '../../nav'
import { ACCOUNT_TYPES } from '../../../data/models'
import { AmountTrailing, pct1, prettyDate, quantity, Row, SectionTitle, signedPct1, TAG_COLOR, TAG_ICON, Tile } from './shared'

type Move =
  | { kind: 'out'; e: Expense; day: number; created: number }
  | { kind: 'in'; i: Income; day: number; created: number }
  | { kind: 'moved'; t: Transfer; day: number; created: number }

/** Nada más entrar: los últimos movimientos de todas tus cuentas y de dónde salen o adónde van. */
export function RecentMovesCard({ expenses, incomes, transfers, accounts }: { expenses: Expense[]; incomes: Income[]; transfers: Transfer[]; accounts: Account[] }) {
  const m = useMoney()
  const nav = useNav()
  const [showAll, setShowAll] = useState(false)
  const moves = useMemo(() => ([
    ...expenses.map((e): Move => ({ kind: 'out', e, day: e.epochDay, created: e.createdAt })),
    ...incomes.map((i): Move => ({ kind: 'in', i, day: i.epochDay, created: i.createdAt })),
    ...transfers.map((t): Move => ({ kind: 'moved', t, day: t.epochDay, created: t.createdAt })),
  ]).sort((a, b) => b.day - a.day || b.created - a.created), [expenses, incomes, transfers])
  const name = (id: number | null | undefined) => accounts.find((a) => a.id === id)?.name
  return (
    <div className="card hm-list-card">
      <div className="row" style={{ padding: '16px 16px 4px' }}>
        <div className="grow">
          <div style={{ fontSize: 20, fontWeight: 600 }}>Últimos movimientos</div>
          <div className="small muted">De todas tus cuentas</div>
        </div>
        {moves.length > 6 && <button className="link-btn" style={{ padding: 8 }} onClick={() => setShowAll(!showAll)}>{showAll ? 'Ver menos' : 'Ver más'}</button>}
      </div>
      {moves.length === 0 && <div className="small muted" style={{ padding: 16 }}>Aún no hay movimientos. Pulsa + para apuntar un gasto o pega el aviso de un pago de tu banco.</div>}
      <div className="hm-rows">
        {moves.slice(0, showAll ? 25 : 6).map((mv) => {
          if (mv.kind === 'out') {
            const t = spendTag(mv.e)
            const acc = name(mv.e.accountId)
            return (
              <Row key={'e' + mv.e.id} title={mv.e.note || SPEND_TAG_INFO[t].label}
                subtitle={[dayLabel(mv.e.epochDay), acc ? `de ${acc}` : 'sin cuenta', mv.e.fromCard ? 'tarjeta' : null].filter(Boolean).join(' · ')}
                onClick={() => nav.edit({ name: 'expense', expense: mv.e })}
                leading={<Tile icon={TAG_ICON[t]} color={TAG_COLOR[t]} />}
                trailing={<AmountTrailing text={'−' + m.fmt(mv.e.amount)} sub={SPEND_TAG_INFO[t].label} />} />
            )
          }
          if (mv.kind === 'in') {
            const acc = name(mv.i.accountId)
            return (
              <Row key={'i' + mv.i.id} title={mv.i.note || 'Ingreso'}
                subtitle={[dayLabel(mv.i.epochDay), acc ? `a ${acc}` : 'ya estaba en el saldo'].join(' · ')}
                onClick={() => nav.edit({ name: 'income', income: mv.i })}
                leading={<Tile icon="work" color="var(--gain)" />}
                trailing={<AmountTrailing text={'+' + m.fmt(mv.i.amount)} sub="Ingreso" color="var(--gain)" />} />
            )
          }
          const from = accounts.find((a) => a.id === mv.t.fromAccountId)
          return (
            <Row key={'t' + mv.t.id} title={mv.t.note || 'Transferencia'}
              subtitle={`${dayLabel(mv.t.epochDay)} · ${from?.name ?? '?'} → ${name(mv.t.toAccountId) ?? '?'}`}
              onClick={from ? () => nav.open({ name: 'account', accountId: from.id }) : undefined}
              leading={<Tile icon="swap" color={SERIES_COLORS[1]} />}
              trailing={<AmountTrailing text={m.fmt(mv.t.amount)} sub="Transferencia" color={SERIES_COLORS[1]} />} />
          )
        })}
      </div>
      <div style={{ height: 8 }} />
    </div>
  )
}

/** Atajo de dos columnas ("¿Me lo compro?", "Análisis"). */
export function ShortcutCard({ title, sub, icon, color, onClick }: { title: string; sub: string; icon: string; color: string; onClick: () => void }) {
  return (
    <button className="card tight hm-shortcut" onClick={onClick}>
      <Tile icon={icon} color={color} size={40} />
      <div className="ellipsis" style={{ fontWeight: 600, marginTop: 8 }}>{title}</div>
      <div className="small muted ellipsis">{sub}</div>
    </button>
  )
}

/** Cuentas, cripto y acciones con su peso en el patrimonio. */
export function AccountsCard({ accounts, crypto, stocks, total }: { accounts: Account[]; crypto: CryptoHolding[]; stocks: StockHolding[]; total: number }) {
  const m = useMoney()
  const nav = useNav()
  const base = total > 0 ? total : 1
  const rows: Array<{ key: string; logo: JSX.Element; title: string; sub: string; value: number; onClick: () => void }> = accounts.map((a) => ({
    key: 'a' + a.id,
    logo: <AccountLogo name={a.name} size={48} />,
    title: a.name,
    sub: a.interestRate > 0 ? `${pct1(a.interestRate)} TAE · ahora +${m.fmt(InterestMath.monthlyInterest(a.balance, a.interestRate))}/mes` : ACCOUNT_TYPES[a.type].label,
    value: signedBalance(a),
    onClick: () => nav.open({ name: 'account', accountId: a.id }),
  }))
  if (crypto.length) rows.push({ key: 'c', logo: <Initials text="₿" color="#F7931A" size={40} />, title: 'Criptomonedas', sub: `${crypto.length} monedas`, value: crypto.reduce((s, c) => s + c.amount * c.price, 0), onClick: () => nav.openInvest('CRYPTO') })
  if (stocks.length) rows.push({ key: 's', logo: <Initials text="↗" color="#BF5AF2" size={40} />, title: 'Acciones y ETF', sub: `${stocks.length} posiciones`, value: stocks.reduce((s, c) => s + c.shares * c.price, 0), onClick: () => nav.openInvest('STOCKS') })
  return (
    <div className="card hm-list-card">
      <div className="hm-rows" style={{ padding: '0 16px' }}>
        {rows.map((r) => (
          <Row key={r.key} title={r.title} subtitle={r.sub} onClick={r.onClick} leading={r.logo}
            trailing={<AmountTrailing text={m.fmt(r.value)} sub={r.value >= 0 ? pct1(r.value / base * 100) : 'deuda'} color={r.value < 0 ? 'var(--loss)' : undefined} />} />
        ))}
      </div>
    </div>
  )
}

/** Color fijo de los símbolos conocidos (symbolColor de InvestmentsScreen.kt). */
export function symbolColor(symbol: string): string {
  const s = symbol.toUpperCase()
  const known: Record<string, string> = { BTC: '#F7931A', ETH: '#627EEA', SOL: '#9945FF', ADA: '#0033AD', XRP: '#23292F', DOGE: '#C2A633', USDT: '#26A17B', USDC: '#26A17B', AAPL: '#1C1C1E' }
  return known[s] ?? ['#1E9D57', '#3B82F6', '#8B5CF6', '#EF6C4A', '#14B8A6', '#E5578A'][hashCode(symbol) % 6]
}

export interface Holding { symbol: string; name: string; qty: number; avg: number; price: number }

/** Lista corta de posiciones: logo, nombre, cantidad, valor y cuánto ganas o pierdes. */
export function HoldingsList({ items, onClick }: { items: Holding[]; onClick: () => void }) {
  const m = useMoney()
  const sorted = [...items].sort((a, b) => b.qty * b.price - a.qty * a.price).slice(0, 5)
  return (
    <div className="card hm-list-card">
      <div className="hm-rows" style={{ padding: '0 16px' }}>
        {sorted.map((h) => {
          const pnl = h.avg > 0 ? (h.price - h.avg) / h.avg * 100 : null
          return (
            <Row key={h.symbol + h.name} title={h.name} subtitle={`${quantity(h.qty)} ${h.symbol}`} onClick={onClick}
              leading={<Initials text={h.symbol} color={symbolColor(h.symbol)} size={48} />}
              trailing={<AmountTrailing text={m.fmt(h.qty * h.price)} sub={pnl != null ? signedPct1(pnl) : null}
                subColor={pnl == null || Math.abs(pnl) < 0.004 ? 'var(--muted)' : pnl > 0 ? 'var(--gain)' : 'var(--loss)'} />} />
          )
        })}
      </div>
    </div>
  )
}

/** "Puedes gastar": lo disponible menos los fijos que faltan y lo reservado para ahorrar (si no hay plan del banco). */
export function SafeToSpendCard({ s }: { s: SafeToSpend }) {
  const m = useMoney()
  return (
    <div className="card">
      <div className="muted" style={{ fontSize: 18, fontWeight: 500 }}>Puedes gastar</div>
      <div className="mid-num" style={{ fontSize: 36, marginTop: 8, color: s.safe > 0 ? 'var(--gain)' : 'var(--loss)' }}>{m.fmt(Math.max(0, s.safe))}</div>
      <div className="small muted">
        {s.safe <= 0 ? 'Lo que tienes está comprometido hasta tu próximo cobro'
          : `≈ ${m.fmt(s.perDay)} al día` + (s.nextPayday ? ` · faltan ${s.daysToPayday} días para tu cobro del ${prettyDate(s.nextPayday).toLowerCase()}` : ' hasta fin de mes')}
      </div>
      <div className="divider" style={{ margin: '12px 0 8px' }} />
      <Breakdown label="Disponible en cuentas" value={m.fmt(s.available)} />
      <Breakdown label="Gastos fijos pendientes" value={(s.pendingBills > 0 ? '-' : '') + m.fmt(s.pendingBills)} color={s.pendingBills > 0 ? 'var(--loss)' : 'var(--muted)'} />
      <Breakdown label="Reservado para ahorrar" value={(s.savingsReserve > 0 ? '-' : '') + m.fmt(s.savingsReserve)} color={s.savingsReserve > 0 ? SERIES_COLORS[1] : 'var(--muted)'} />
    </div>
  )
}

function Breakdown({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="row small" style={{ padding: '4px 0' }}>
      <span className="grow muted">{label}</span>
      <span className="num" style={{ fontWeight: 700, color }}>{value}</span>
    </div>
  )
}

/** "Tu futuro": saldo previsto de los próximos 30 días. */
export function FutureMiniCard({ points, onOpen }: { points: Array<[LocalDate, number]>; onOpen: () => void }) {
  const m = useMoney()
  if (points.length === 0) return null
  const end = points[points.length - 1]
  const negative = points.find((p) => p[1] < 0)
  return (
    <div className="card clickable" role="button" onClick={onOpen}>
      <div className="row">
        <div className="grow">
          <div style={{ fontSize: 18, fontWeight: 600 }}>Tu futuro</div>
          <div className="small" style={{ color: negative ? 'var(--loss)' : 'var(--muted)' }}>
            {negative ? `Ojo: ${prettyDate(negative[0]).toLowerCase()} te quedarías en ${m.fmt(negative[1])}` : `En 30 días tendrás ≈ ${m.fmt(end[1])} disponibles`}
          </div>
        </div>
        <Icon name="line" style={{ color: 'var(--muted)' }} />
      </div>
      <div style={{ marginTop: 8 }}>
        <LineChart values={points.map((p) => p[1])} color={negative ? 'var(--loss)' : 'var(--gain)'} height={72} />
      </div>
    </div>
  )
}

/** Explica con palabras qué ha cambiado este mes frente al mismo tramo del anterior. */
export function WhereDidItGoCard({ c }: { c: MonthComparison }) {
  const m = useMoney()
  if (c.spentPrev <= 0 && c.incomePrev <= 0) return null
  const diff = c.spentNow - c.spentPrev
  const ups = c.byCategory.filter((x) => x[1] > 1).slice(0, 3)
  const incomeDiff = c.incomeNow - c.incomePrev
  let text = Math.abs(incomeDiff) < 1 ? 'Has cobrado lo mismo que a estas alturas del mes pasado'
    : incomeDiff > 0 ? `Has cobrado ${m.fmt(incomeDiff)} más que a estas alturas del mes pasado`
      : `Has cobrado ${m.fmt(-incomeDiff)} menos que a estas alturas del mes pasado`
  text += Math.abs(diff) < 1 ? ' y has gastado lo mismo.' : diff > 0 ? `, pero has gastado ${m.fmt(diff)} más` : ` y has gastado ${m.fmt(-diff)} menos. ¡Bien!`
  if (diff > 0 && ups.length > 0) text += ': ' + ups.map(([cat, v]) => `${m.fmt(v)} en ${cat.name.toLowerCase()}`).join(', ') + '.'
  else if (diff > 0) text += '.'
  return (
    <div className="card">
      <div className="hm-overline">¿DÓNDE SE HA IDO TU DINERO?</div>
      <div style={{ marginTop: 8, fontSize: 17 }}>{text}</div>
    </div>
  )
}

/** Nivel y logros (lleva a Perfil). */
export function LevelCard({ level, points, achievements }: { level: Level; points: number; achievements: Achievement[] }) {
  const nav = useNav()
  const pending = achievements.filter((a) => !a.unlocked)
  const next = pending.length ? pending.reduce((a, b) => (b.progress > a.progress ? b : a)) : null
  return (
    <div className="card tight clickable" role="button" onClick={() => nav.setTab('PROFILE')}>
      <div className="row">
        <div className="hm-level" style={{ background: `color-mix(in srgb, ${SERIES_COLORS[3]} 18%, transparent)`, color: SERIES_COLORS[3] }}>{level.number}</div>
        <div className="grow">
          <div style={{ fontSize: 17, fontWeight: 600 }}>Nivel {level.number} · {level.name}</div>
          <div className="small muted ellipsis">
            {achievements.filter((a) => a.unlocked).length} de {achievements.length} logros{next ? ` · siguiente: ${next.title}` : ''}
          </div>
        </div>
        <Icon name="chevronRight" style={{ color: 'var(--muted)' }} />
      </div>
      <div style={{ marginTop: 8 }}><ProgressLine fraction={levelProgress(level, points)} color={SERIES_COLORS[3]} /></div>
    </div>
  )
}

// ---------- Este mes ----------

export function MonthSelector({ month, onShift }: { month: YearMonth; onShift: (by: number) => void }) {
  const now = thisMonth()
  const canNext = month.year * 12 + month.month < now.year * 12 + now.month
  return (
    <div className="row">
      <button className="icon-btn hm-glass" aria-label="Mes anterior" onClick={() => onShift(-1)}><Icon name="chevronLeft" /></button>
      <div className="grow" style={{ display: 'flex', justifyContent: 'center' }}>
        <span className="hm-month-pill">{capitalize(MONTHS[month.month - 1])} de {month.year}</span>
      </div>
      {canNext ? <button className="icon-btn hm-glass" aria-label="Mes siguiente" onClick={() => onShift(1)}><Icon name="chevronRight" /></button> : <span style={{ width: 40 }} />}
    </div>
  )
}

/** El mes con dinero real: lo cobrado, lo gastado y la diferencia; el presupuesto va como límite de gasto. */
export function MonthFlowCard({ received, spent, spendLimit, estimated }: { received: number; spent: number; spendLimit: number; estimated: boolean }) {
  const m = useMoney()
  const net = received - spent
  return (
    <div className="card">
      <div className="hm-flow-stats">
        <FlowStat label="Cobrado" value={'+' + m.fmt(received)} color="var(--gain)" />
        <FlowStat label="Gastado" value={'-' + m.fmt(spent)} color="var(--loss)" />
        <FlowStat label="Balance" value={m.signed(net)} color={Math.abs(net) < 0.004 ? 'var(--muted)' : net > 0 ? 'var(--gain)' : 'var(--loss)'} />
      </div>
      {spendLimit > 0 && (
        <>
          <div className="row small" style={{ marginTop: 16, alignItems: 'flex-end', gap: 8 }}>
            <span className="grow" style={{ fontWeight: 600 }}>Límite de gasto</span>
            <span className="num" style={{ fontWeight: 600, color: spent > spendLimit ? 'var(--loss)' : 'var(--muted)' }}>{m.fmt(spent)} de {m.fmt(spendLimit)}</span>
          </div>
          <div style={{ marginTop: 8 }}><ProgressLine fraction={spent / spendLimit} color={spent > spendLimit ? 'var(--loss)' : SERIES_COLORS[0]} /></div>
          <div className="tiny muted" style={{ marginTop: 8 }}>Sale de tu reparto en Presupuesto{estimated ? ', con lo variable estimado por tu media.' : '.'}</div>
        </>
      )}
    </div>
  )
}

function FlowStat({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div style={{ minWidth: 0 }}>
      <div className="small muted">{label}</div>
      <div className="num ellipsis" style={{ fontWeight: 800, color }}>{value}</div>
    </div>
  )
}

/** Cobro a apuntar de una fuente (newIncomeFor de BudgetScreen.kt). */
function newIncomeFor(source: IncomeSource, expected: number, month: YearMonth): Partial<Income> {
  const t = todayDate()
  const day = sameMonth(month, { year: t.year, month: t.month }) ? t.day : Math.min(source.dayFrom, lengthOfMonth(month.year, month.month))
  return { sourceId: source.id, amount: expected, note: source.name, epochDay: toEpochDay({ year: month.year, month: month.month, day }), accountId: source.accountId }
}

/** "Próximo cobro": una fuente de ingresos con su estado de este mes. */
export function NextIncomeCard({ sm, month, accounts }: { sm: SourceMonth; month: YearMonth; accounts: Account[] }) {
  const m = useMoney()
  const nav = useNav()
  const s = sm.source
  const days = s.dayFrom === s.dayTo ? `día ${s.dayFrom}` : `días ${s.dayFrom}–${s.dayTo}`
  const account = accounts.find((a) => a.id === s.accountId)?.name
  const kind = s.variable ? (sm.confirmed != null ? 'Variable · confirmado este mes' : sm.average != null ? 'Variable · media de 3 meses' : 'Variable · estimado') : 'Fijo'
  return (
    <div className="card hm-list-card">
      <div className="hm-overline" style={{ padding: '16px 16px 0' }}>PRÓXIMO COBRO</div>
      <div style={{ padding: '0 16px' }}>
        <Row title={s.name} subtitle={[kind, days, account].filter(Boolean).join(' · ')}
          onClick={() => nav.edit({ name: 'incomeSource', source: s })}
          leading={<Tile icon={s.variable ? 'bolt' : 'work'} color={s.variable ? SERIES_COLORS[2] : SERIES_COLORS[0]} />}
          trailing={
            <div className="col right" style={{ gap: 4, alignItems: 'flex-end', flexShrink: 0 }}>
              <div className="num" style={{ fontWeight: 700 }}>{(sm.isEstimate ? '~' : '') + m.fmt(sm.amount)}</div>
              {sm.isReceived ? (
                <span className="tiny gain row" style={{ gap: 2 }}><Icon name="checkCircle" size={12} /> Cobrado</span>
              ) : (
                <>
                  <button className="hm-mini-btn" onClick={(e) => { e.stopPropagation(); nav.edit({ name: 'income', income: newIncomeFor(s, sm.amount, month) }) }}>Cobrado</button>
                  {s.variable && (
                    <button className="tiny gain" style={{ fontWeight: 700, padding: '2px 4px' }} onClick={(e) => { e.stopPropagation(); nav.edit({ name: 'expectedIncome', sourceId: s.id, month }) }}>
                      {sm.confirmed != null ? 'Cambiar importe' : '¿Cuánto este mes?'}
                    </button>
                  )}
                </>
              )}
            </div>
          } />
      </div>
    </div>
  )
}

/** Movimientos del mes elegido (gastos y cobros). */
export function MonthMovesList({ expenses, incomes, categories, accounts }: { expenses: Expense[]; incomes: Income[]; categories: Category[]; accounts: Account[] }) {
  const m = useMoney()
  const nav = useNav()
  const moves = useMemo(() => ([
    ...expenses.map((e) => ({ e, i: null as Income | null, day: e.epochDay, created: e.createdAt })),
    ...incomes.map((i) => ({ e: null as Expense | null, i, day: i.epochDay, created: i.createdAt })),
  ]).sort((a, b) => b.day - a.day || b.created - a.created), [expenses, incomes])
  return (
    <>
      <SectionTitle action={moves.length > 0 ? <span className="muted" style={{ fontSize: 18, paddingRight: 4 }}>{moves.length}</span> : undefined}>Movimientos</SectionTitle>
      {moves.length === 0 ? (
        <div className="card"><EmptyState icon="receipt" title="Sin movimientos este mes" body="Pulsa + para apuntar un gasto, o pega el aviso de un pago de tu banco para que se apunte solo." /></div>
      ) : (
        <div className="card hm-list-card">
          <div className="hm-rows" style={{ padding: '0 16px' }}>
            {moves.map(({ e, i }) => {
              if (e) {
                const cat = categories.find((c) => c.id === e.categoryId)
                const acc = accounts.find((a) => a.id === e.accountId)
                const t = spendTag(e)
                return (
                  <Row key={'e' + e.id} title={e.note || cat?.name || 'Gasto'}
                    subtitle={[dayLabel(e.epochDay), SPEND_TAG_INFO[t].label, e.fromCard ? 'Tarjeta' : null, e.billId != null ? 'Gasto fijo' : null].filter(Boolean).join(' · ')}
                    onClick={() => nav.edit({ name: 'expense', expense: e })}
                    leading={
                      <div style={{ position: 'relative', flexShrink: 0 }}>
                        {cat?.isSavings ? <Tile icon="savings" color={SERIES_COLORS[1]} /> : <Tile icon={TAG_ICON[t]} color={TAG_COLOR[t]} />}
                        {e.fromCard && <span className="hm-card-badge"><Icon name="creditCard" size={8} /></span>}
                      </div>
                    }
                    trailing={<AmountTrailing text={'-' + m.fmt(e.amount)} sub={acc?.name} />} />
                )
              }
              const inc = i!
              const interest = inc.sourceId == null
              return (
                <Row key={'i' + inc.id} title={inc.note || 'Ingreso'} subtitle={`${dayLabel(inc.epochDay)} · ${interest ? 'Intereses' : 'Ingreso'}`}
                  onClick={() => nav.edit({ name: 'income', income: inc })}
                  leading={<Tile icon={interest ? 'savings' : 'work'} color="var(--gain)" />}
                  trailing={<AmountTrailing text={'+' + m.fmt(inc.amount)} sub={accounts.find((a) => a.id === inc.accountId)?.name} color="var(--gain)" />} />
              )
            })}
          </div>
        </div>
      )}
    </>
  )
}

/** Un pago apuntado solo hace poco (desde el aviso pegado): para revisarlo. */
export function DetectedCard({ e, categories }: { e: Expense; categories: Category[] }) {
  const m = useMoney()
  const nav = useNav()
  return (
    <div className="card tight clickable" role="button" onClick={() => nav.edit({ name: 'expense', expense: e })}>
      <div className="row">
        <AccountLogo name={e.note || '?'} size={40} />
        <div className="grow">
          <div className="small muted">Gasto detectado{e.sourceApp ? ` · ${e.sourceApp}` : ''}</div>
          <div style={{ fontWeight: 700 }}>{e.note} · {m.fmt(e.amount)} · {categories.find((c) => c.id === e.categoryId)?.name ?? ''}</div>
        </div>
        <span className="gain" style={{ fontWeight: 600 }}>Revisar</span>
      </div>
    </div>
  )
}
