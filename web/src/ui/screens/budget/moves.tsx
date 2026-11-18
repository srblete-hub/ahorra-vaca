// Port de ui/screens/MovesUi.kt: lista y calendario de movimientos, gráficas y la suma de seleccionados.
import { useRef, type KeyboardEvent, type MouseEvent, type PointerEvent } from 'react'
import type { Account, Expense, Income, RecurringBill, Transfer } from '../../../data/models'
import {
  MONTHS_SHORT, WEEKDAYS_SHORT, capitalize, dayOfWeek, monthDays, ofEpochDay, sameMonth, thisMonth, todayEpoch, toEpochDay,
  type LocalDate, type YearMonth,
} from '../../../data/dates'
import { Budgeting, Classifier, Merchants, SPEND_TAG_INFO, spendTag, type Analytics, type SpendStats, type SpendTag } from '../../../domain'
import type { SheetRoute } from '../../nav'
import { Donut, ProgressLine, useMoney } from '../../components'
import { Icon } from '../../icons'
import { AutoFit, MerchantLogo, TAG_COLOR, TagCircle, dayShort, dayShortDot, dayTitle, hhmm, pct0 } from './spend'

export type MoveKind = 'EXPENSE' | 'INCOME' | 'TRANSFER'

/** Un movimiento para la lista y el calendario: gasto, ingreso o traspaso entre tus cuentas. */
export interface MoveItem {
  key: string
  epochDay: number
  title: string
  subtitle: string
  amount: number
  kind: MoveKind
  tag: SpendTag | null
  fixed: boolean
  createdAt: number
  edit: SheetRoute
  /** Texto original del gasto, para buscar el logo del comercio. */
  note: string
}

export function buildMoves(month: YearMonth, expenses: Expense[], incomes: Income[], transfers: Transfer[], accounts: Account[], bills: RecurringBill[]): MoveItem[] {
  const acc = (id: number | null | undefined) => accounts.find((a) => a.id === id)?.name
  const inM = (d: number) => sameMonth({ year: ofEpochDay(d).year, month: ofEpochDay(d).month }, month)
  const e = expenses.filter((x) => inM(x.epochDay)).map((x): MoveItem => {
    const t = spendTag(x)
    const label = SPEND_TAG_INFO[t].label
    return {
      key: `e${x.id}`, epochDay: x.epochDay, title: Merchants.clean(x.note) || label,
      subtitle: [label, acc(x.accountId)].filter(Boolean).join(' · '), amount: -x.amount, kind: 'EXPENSE', tag: t,
      fixed: Budgeting.isFixed(x, bills), createdAt: x.createdAt, edit: { name: 'expense', expense: x }, note: x.note,
    }
  })
  const i = incomes.filter((x) => inM(x.epochDay)).map((x): MoveItem => ({
    key: `i${x.id}`, epochDay: x.epochDay, title: x.note.trim() || 'Ingreso', subtitle: ['Ingreso', acc(x.accountId)].filter(Boolean).join(' · '),
    amount: x.amount, kind: 'INCOME', tag: null, fixed: false, createdAt: x.createdAt, edit: { name: 'income', income: x }, note: '',
  }))
  const t = transfers.filter((x) => inM(x.epochDay)).map((x): MoveItem => ({
    key: `t${x.id}`, epochDay: x.epochDay, title: x.note.trim() || 'Traspaso', subtitle: `${acc(x.fromAccountId) ?? '?'} → ${acc(x.toAccountId) ?? '?'}`,
    amount: x.amount, kind: 'TRANSFER', tag: null, fixed: false, createdAt: x.createdAt, edit: { name: 'transfer', fromAccountId: x.fromAccountId }, note: '',
  }))
  return [...e, ...i, ...t].sort((a, b) => b.epochDay - a.epochDay || b.createdAt - a.createdAt)
}

/** Color del día en el calendario según lo que gastaste (sin alquiler ni fijos) frente a lo que te toca. */
function dayColor(spent: number, allowance: number): string | null {
  if (spent <= 0) return null
  if (allowance <= 0) return '#E8F3EC'
  if (spent <= allowance) return '#DDF0E3'
  if (spent <= allowance * 1.5) return '#FBE3E1'
  return '#F2C4BF'
}

function MoveIcon({ m }: { m: MoveItem }) {
  if (m.kind === 'EXPENSE') return <MerchantLogo key={m.note} note={m.note} tag={m.tag ?? 'OTHER'} size={40} />
  const [icon, color] = m.kind === 'INCOME' ? ['payments', 'var(--gain)'] : ['swap', '#8E8E93']
  return (
    <div className="ico round" style={{ width: 40, height: 40, background: `color-mix(in srgb, ${color} 14%, transparent)`, color }}>
      <Icon name={icon} size={22} />
    </div>
  )
}

/** Toque normal o largo (mantener pulsado ~0,5 s) sin que iOS abra su menú. */
function usePress(onTap: () => void, onLong: () => void) {
  const timer = useRef<number | undefined>(undefined)
  const long = useRef(false)
  const start = useRef<[number, number] | null>(null)
  const cancel = () => { window.clearTimeout(timer.current); timer.current = undefined }
  return {
    onPointerDown: (e: PointerEvent) => {
      long.current = false
      start.current = [e.clientX, e.clientY]
      cancel()
      timer.current = window.setTimeout(() => { long.current = true; try { navigator.vibrate?.(15) } catch { /* nada */ } onLong() }, 500)
    },
    onPointerMove: (e: PointerEvent) => {
      if (start.current && Math.hypot(e.clientX - start.current[0], e.clientY - start.current[1]) > 10) cancel()
    },
    onPointerUp: cancel,
    onPointerCancel: cancel,
    onPointerLeave: cancel,
    onContextMenu: (e: MouseEvent) => e.preventDefault(),
    onClick: () => { if (!long.current) onTap(); long.current = false },
    onKeyDown: (e: KeyboardEvent) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onTap() } },
  }
}

export interface Selection { picked: Record<string, MoveItem>; toggle: (m: MoveItem) => void }

function MoveRow({ m, sel, onEdit }: { m: MoveItem; sel: Selection; onEdit: (r: SheetRoute) => void }) {
  const money = useMoney()
  const isPicked = m.key in sel.picked
  const any = Object.keys(sel.picked).length > 0
  const press = usePress(() => (any ? sel.toggle(m) : onEdit(m.edit)), () => sel.toggle(m))
  const amount = m.kind === 'INCOME' ? '+' + money.fmt(m.amount) : m.kind === 'TRANSFER' ? money.fmt(m.amount) : '−' + money.fmt(-m.amount)
  return (
    <div className={`bd-move ${isPicked ? 'picked' : ''}`} role="button" tabIndex={0} aria-pressed={isPicked} aria-label={`${m.title}, ${amount}. Mantén pulsado para seleccionar y sumar`} {...press}>
      {isPicked
        ? <div className="ico round" style={{ width: 40, height: 40, background: 'var(--gain)', color: 'var(--on-brand)' }}><Icon name="check" size={24} /></div>
        : <MoveIcon m={m} />}
      <div className="grow">
        <div className="bd-move-t ellipsis">{m.title}</div>
        <div className="tiny muted ellipsis">{dayShort(ofEpochDay(m.epochDay))} · {m.subtitle}{m.fixed ? ' · fijo' : ''}</div>
      </div>
      <div className="right" style={{ flexShrink: 0 }}>
        <div className="num bd-move-a" style={{ color: m.kind === 'INCOME' ? 'var(--gain)' : m.kind === 'TRANSFER' ? 'var(--muted)' : 'var(--ink)' }}>{amount}</div>
        {m.createdAt > 0 && <div className="tiny muted num">{hhmm(m.createdAt)}</div>}
      </div>
    </div>
  )
}

function DayCard({ moves, sel, onEdit }: { moves: MoveItem[]; sel: Selection; onEdit: (r: SheetRoute) => void }) {
  return <div className="bd-daycard">{moves.map((m) => <MoveRow key={m.key} m={m} sel={sel} onEdit={onEdit} />)}</div>
}

function Tile({ label, value, color, dark }: { label: string; value: string; color: string; dark?: boolean }) {
  return (
    <div className={`bd-tile ${dark ? 'dark' : ''}`}>
      <div className="bd-tile-l">{label}</div>
      <AutoFit text={value} max={20} min={11} style={{ fontWeight: 700, color: dark ? '#fff' : color, marginTop: 4 }} />
    </div>
  )
}

/** Pastillas de periodo (Semana / Mes / Año…). */
export function PeriodPills({ options, value, onChange }: { options: string[]; value: number; onChange: (i: number) => void }) {
  return (
    <div className="bd-periods" role="tablist">
      {options.map((t, i) => <button key={t} role="tab" aria-selected={i === value} className={i === value ? 'on' : ''} onClick={() => onChange(i)}>{t}</button>)}
    </div>
  )
}

/**
 * Movimientos en calendario (cada día con el color de lo que gastaste) o en lista por días con buscador.
 * `allowance` = lo que te toca gastar al día; el alquiler y los fijos no colorean el día.
 */
export function MovesSection(p: {
  calendar: boolean; month: YearMonth; moves: MoveItem[]; allowance: number; pending: number
  query: string; onQuery: (q: string) => void; selected: number | null; onSelect: (epochDay: number) => void
  onEdit: (r: SheetRoute) => void; sel: Selection
  /** Cobros que aún esperas este mes (día → nombre e importe, "~" si es estimado). */
  expected: Map<number, Array<[string, string]>>
  analytics: Analytics | null; period: number; onPeriod: (i: number) => void
}) {
  const money = useMoney()
  const { month, moves, sel, onEdit } = p
  if (p.calendar) {
    const byDay = new Map<number, MoveItem[]>()
    for (const m of moves) byDay.set(m.epochDay, [...(byDay.get(m.epochDay) ?? []), m])
    const first: LocalDate = { year: month.year, month: month.month, day: 1 }
    const offset = dayOfWeek(first) - 1
    const len = monthDays(month)
    const cells = Array.from({ length: Math.ceil((offset + len) / 7) * 7 }, (_, i) => i - offset + 1)
    const today = todayEpoch()
    const isThisMonth = sameMonth(month, thisMonth())
    const day = p.selected ?? (isThisMonth ? today : moves[0]?.epochDay ?? null)
    const list = day == null ? [] : moves.filter((m) => m.epochDay === day)
    const net = list.filter((m) => m.kind !== 'TRANSFER').reduce((a, m) => a + m.amount, 0)
    const waits = day == null ? undefined : p.expected.get(day)
    return (
      <>
        <div className="bd-tiles">
          <Tile label="INGRESADO" value={money.fmt(moves.filter((m) => m.kind === 'INCOME').reduce((a, m) => a + m.amount, 0))} color="var(--gain)" />
          <Tile label="GASTADO" value={money.fmt(moves.filter((m) => m.kind === 'EXPENSE' && !m.fixed).reduce((a, m) => a - m.amount, 0))} color="var(--loss)" />
          <Tile label="POR PAGAR" value={money.fmt(p.pending)} color="var(--ink)" dark />
        </div>
        <div className="bd-cal">
          <div className="bd-cal-grid">
            {WEEKDAYS_SHORT.map((w) => <div key={w} className="bd-cal-wd">{w}</div>)}
            {cells.map((idx, i) => {
              if (idx < 1 || idx > len) return <div key={'x' + i} />
              const ep = toEpochDay({ year: month.year, month: month.month, day: idx })
              const items = byDay.get(ep) ?? []
              const spent = items.filter((m) => m.kind === 'EXPENSE' && !m.fixed).reduce((a, m) => a - m.amount, 0)
              const income = items.filter((m) => m.kind === 'INCOME').reduce((a, m) => a + m.amount, 0)
              const isToday = ep === today
              const waiting = p.expected.get(ep)
              const bg = isToday ? 'var(--prado)' : dayColor(spent, p.allowance)
              const cls = ['bd-day', isToday ? 'today' : bg ? 'tinted' : '', ep === p.selected && !isToday ? 'sel' : waiting ? 'wait' : '', items.length === 0 && !waiting && !isToday ? 'empty' : ''].join(' ')
              const sub = spent > 0 ? { t: '−' + Math.round(spent), c: 'soft' } : income > 0 ? { t: '+' + Math.round(income), c: 'in' } : waiting ? { t: waiting[0][1], c: 'in' } : null
              return (
                <button key={ep} className={cls} style={bg ? { background: bg } : undefined} onClick={() => p.onSelect(ep)} aria-label={`Día ${idx}`} aria-pressed={ep === day}>
                  <span className="n">{idx}</span>
                  {sub && <span className={`a ${sub.c}`}>{money.hidden ? '•••' : sub.t}</span>}
                </button>
              )
            })}
          </div>
          <div className="bd-cal-legend">
            <div className="tiny muted">Cada día pone lo que gastaste (sin alquiler ni fijos).</div>
            <div className="bd-legend-row">
              {([['#DDF0E3', 'Dentro de lo tuyo'], ['#FBE3E1', 'Algo de más'], ['#F2C4BF', 'Mucho de más']] as const).map(([c, t]) => (
                <span key={t} className="bd-legend"><span className="sq" style={{ background: c }} />{t}</span>
              ))}
            </div>
            {p.expected.size > 0 && <span className="bd-legend"><span className="sq" style={{ border: '2px solid var(--gain)' }} />Día en que esperas cobrar</span>}
          </div>
        </div>
        {day != null && (
          <div className="bd-dayhead">
            <div className="grow">{dayTitle(ofEpochDay(day))}</div>
            {list.length > 0 && <div className="num" style={{ fontSize: 16, fontWeight: 600, color: net >= 0 ? 'var(--gain)' : 'var(--ink)' }}>{(net >= 0 ? '+' : '−') + money.fmt(Math.abs(net))}</div>}
          </div>
        )}
        {waits && (
          <div className="bd-waits">
            {waits.map(([name, amount]) => (
              <div key={name} className="row" style={{ gap: 16 }}>
                <div className="ico round" style={{ width: 40, height: 40, background: 'color-mix(in srgb, var(--gain) 14%, transparent)', color: 'var(--gain)' }}><Icon name="payments" size={24} /></div>
                <div className="grow">
                  <div style={{ fontWeight: 500 }}>Esperas cobrar {name}</div>
                  <div className="tiny muted">{amount.startsWith('+~') ? 'Importe estimado; confírmalo en Resumen → Tus ingresos' : 'Aún no ha llegado'}</div>
                </div>
                <div className="num" style={{ fontWeight: 600, color: 'var(--gain)' }}>{money.hidden ? '••••' : amount.replace('+~', '~')}</div>
              </div>
            ))}
          </div>
        )}
        {day != null && (list.length === 0
          ? <div className="muted" style={{ padding: '0 8px' }}>Sin movimientos este día.</div>
          : <DayCard moves={list} sel={sel} onEdit={onEdit} />)}
      </>
    )
  }

  const q = Classifier.normalize(p.query)
  const shown = q.trim() === '' ? moves : moves.filter((m) => Classifier.normalize(m.title + ' ' + m.subtitle).includes(q))
  const groups: Array<[number, MoveItem[]]> = []
  for (const m of shown) {
    const last = groups[groups.length - 1]
    if (last && last[0] === m.epochDay) last[1].push(m); else groups.push([m.epochDay, [m]])
  }
  return (
    <>
      {p.analytics && (
        <>
          <PeriodPills options={['Semana', 'Mes', 'Año']} value={p.period} onChange={p.onPeriod} />
          <AnalyticsHeader a={p.analytics} />
          <h2 className="bd-h2" style={{ marginTop: 8 }}>Transacciones</h2>
        </>
      )}
      <label className="bd-search">
        <Icon name="search" />
        <input type="search" placeholder="Buscar movimientos" value={p.query} onChange={(e) => p.onQuery(e.target.value)} enterKeyHint="search" />
      </label>
      <div className="tiny muted" style={{ padding: '0 8px' }}>Mantén pulsado un movimiento y toca otros para sumarlos.</div>
      {shown.length === 0 && <div className="muted" style={{ padding: 8 }}>{q.trim() === '' ? 'No hay movimientos este mes.' : `Nada coincide con «${p.query}».`}</div>}
      {groups.map(([d, list]) => (
        <div key={d} className="col" style={{ gap: 8 }}>
          <div className="bd-daylabel">{dayTitle(ofEpochDay(d))}</div>
          <DayCard moves={list} sel={sel} onEdit={onEdit} />
        </div>
      ))}
    </>
  )
}

function Change({ ch, label }: { ch: number | null; label: string }) {
  if (ch == null) return null
  const good = ch <= 0
  const c = good ? 'var(--gain)' : 'var(--loss)'
  return (
    <div className="col" style={{ gap: 4, alignItems: 'flex-end', flexShrink: 0 }}>
      <span className="bd-change" style={{ color: c, background: `color-mix(in srgb, ${c} 14%, transparent)` }}>{(ch >= 0 ? '↑ ' : '↓ ') + pct0(Math.abs(ch))}</span>
      <span className="tiny muted">{label}</span>
    </div>
  )
}

function compactEuros(v: number): string {
  if (v >= 1000) return ((v / 1000).toFixed(1).replace('.', ',') + 'K').replace(',0K', 'K')
  return String(Math.round(v))
}

/** Cabecera de la lista: total gastado grande, el cambio frente al periodo anterior y las barras con el actual resaltado. */
function AnalyticsHeader({ a }: { a: Analytics }) {
  const money = useMoney()
  const max = Math.max(...a.bars.map((b) => b.value), 0) || 1
  return (
    <div className="bd-card">
      <div className="row" style={{ alignItems: 'flex-start' }}>
        <div className="grow">
          <AutoFit text={money.fmt(a.total)} max={32} min={20} style={{ fontWeight: 800, letterSpacing: '-0.02em' }} />
          <div className="small muted">Total gastado · {a.title}</div>
        </div>
        <Change ch={a.changePct} label={a.prevLabel} />
      </div>
      <div className="bd-axis-chart">
        <div className="bd-axis">{[max, max / 2, 0].map((v, i) => <span key={i}>{money.hidden ? '•' : compactEuros(v)}</span>)}</div>
        <div className="bd-bars" style={{ height: 184 }}>
          {a.bars.map((b, i) => (
            <div key={i} className="bd-bar-col">
              <div className="bd-bar" style={{ height: Math.max(4, 152 * b.value / max), background: b.current ? 'var(--brand)' : 'var(--chip)' }} />
              <span className="tiny" style={{ marginTop: 8, color: b.current ? 'var(--ink)' : 'var(--muted)', fontWeight: b.current ? 600 : 400 }}>{b.label}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

/** Gráficas del gasto: donut por tipos con el total y el cambio, barras por mes y los últimos gastos. */
export function StatsSection({ stats, period, onPeriod, onTag, onEdit }: { stats: SpendStats; period: number; onPeriod: (i: number) => void; onTag: (t: SpendTag) => void; onEdit: (r: SheetRoute) => void }) {
  const money = useMoney()
  const max = Math.max(0, ...stats.bars.map((b) => b[1])) || 1
  const current = thisMonth()
  return (
    <>
      <PeriodPills options={['Este mes', '3 meses', 'Este año']} value={period} onChange={onPeriod} />
      <div className="bd-card bd-center" style={{ padding: '24px 16px' }}>
        <Donut slices={stats.byTag.map(([t, v]) => ({ label: SPEND_TAG_INFO[t].label, value: v, color: TAG_COLOR[t] }))} size={230} thickness={32}>
          <div className="col" style={{ gap: 0, alignItems: 'center', maxWidth: 160 }}>
            <span className="small muted">Total gastado</span>
            <AutoFit text={money.fmt(stats.total)} max={28} min={16} style={{ fontWeight: 800, maxWidth: 160 }} />
            {stats.changePct != null && (
              <>
                <span className="bd-change" style={{ marginTop: 8, color: stats.changePct <= 0 ? 'var(--gain)' : 'var(--loss)', background: `color-mix(in srgb, ${stats.changePct <= 0 ? 'var(--gain)' : 'var(--loss)'} 12%, transparent)` }}>
                  {(stats.changePct >= 0 ? '↑ ' : '↓ ') + pct0(Math.abs(stats.changePct))}
                </span>
                <span className="tiny muted" style={{ marginTop: 2 }}>{stats.prevLabel}</span>
              </>
            )}
          </div>
        </Donut>
        <div className="tiny muted" style={{ marginTop: 8 }}>Sin alquiler ni fijos</div>
      </div>
      {stats.byTag.length > 0 && (
        <div className="bd-daycard">
          {stats.byTag.map(([t, v]) => (
            <button key={t} className="bd-move" onClick={() => onTag(t)}>
              <TagCircle tag={t} />
              <div className="grow">
                <div className="row" style={{ gap: 8 }}>
                  <div className="grow bd-move-t ellipsis">{SPEND_TAG_INFO[t].label}</div>
                  <div className="num bd-move-a">{money.fmt(v)}</div>
                </div>
                <div className="row" style={{ gap: 8, marginTop: 8 }}>
                  <div className="grow"><ProgressLine fraction={stats.total > 0 ? v / stats.total : 0} color={TAG_COLOR[t]} /></div>
                  <span className="small muted num">{pct0(stats.total > 0 ? v / stats.total * 100 : 0)}</span>
                </div>
              </div>
              <Icon name="chevronRight" style={{ color: 'var(--muted)', flexShrink: 0 }} />
            </button>
          ))}
        </div>
      )}
      <div className="bd-card" style={{ padding: 16 }}>
        <h2 className="bd-h2">Por meses</h2>
        <div className="small muted">Gasto del día a día de cada mes</div>
        <div className="bd-bars" style={{ height: 170, paddingTop: 16 }}>
          {stats.bars.map(([ym, v]) => {
            const cur = sameMonth(ym, current)
            return (
              <div key={ym.year * 12 + ym.month} className="bd-bar-col">
                {cur && <span className="num" style={{ fontSize: 11, fontWeight: 600, whiteSpace: 'nowrap' }}>{money.fmt(v).split(',')[0]}</span>}
                <div className="bd-bar" style={{ height: Math.max(4, 120 * v / max), background: cur ? 'var(--gain)' : 'var(--chip)' }} />
                <span className="tiny muted" style={{ marginTop: 8 }}>{capitalize(MONTHS_SHORT[ym.month - 1])}</span>
              </div>
            )
          })}
        </div>
      </div>
      {stats.recent.length > 0 && (
        <div className="bd-daycard" style={{ paddingTop: 8 }}>
          <h2 className="bd-h2" style={{ padding: '8px 16px 4px' }}>Últimos gastos</h2>
          {stats.recent.map((e) => {
            const t = spendTag(e)
            return (
              <button key={e.id} className="bd-move" style={{ paddingTop: 8, paddingBottom: 8 }} onClick={() => onEdit({ name: 'expense', expense: e })}>
                <MerchantLogo key={e.note} note={e.note} tag={t} size={40} />
                <div className="grow">
                  <div className="bd-move-t ellipsis">{Merchants.clean(e.note) || SPEND_TAG_INFO[t].label}</div>
                  <div className="small muted ellipsis">{dayShortDot(e.epochDay)} · {SPEND_TAG_INFO[t].label}</div>
                </div>
                <div className="right" style={{ flexShrink: 0 }}>
                  <div className="num bd-move-a">−{money.fmt(e.amount)}</div>
                  <div className="tiny muted num">{hhmm(e.createdAt)}</div>
                </div>
              </button>
            )
          })}
        </div>
      )}
    </>
  )
}

/** Barra con la suma de los movimientos seleccionados (gastos, ingresos y el neto). */
export function MoveSelectionBar({ picked, onDone }: { picked: MoveItem[]; onDone: () => void }) {
  const money = useMoney()
  if (picked.length === 0) return null
  const out = picked.filter((m) => m.kind === 'EXPENSE').reduce((a, m) => a - m.amount, 0)
  const inc = picked.filter((m) => m.kind === 'INCOME').reduce((a, m) => a + m.amount, 0)
  const transfers = picked.filter((m) => m.kind === 'TRANSFER').length
  const net = inc - out
  const parts = [
    out > 0 ? `Gastos −${money.fmt(out)}` : null,
    inc > 0 ? `Ingresos +${money.fmt(inc)}` : null,
    transfers > 0 ? `${transfers} ${transfers === 1 ? 'traspaso' : 'traspasos'} (no suma)` : null,
  ].filter(Boolean).join(' · ')
  return (
    <div className="bd-selbar" role="status" aria-live="polite">
      <div className="grow">
        <div className="tiny" style={{ opacity: 0.7 }}>{picked.length} {picked.length === 1 ? 'movimiento' : 'movimientos'}</div>
        <div className="num" style={{ fontSize: 24, fontWeight: 800, color: net >= 0 ? '#4ADE80' : '#fff' }}>{(net >= 0 ? '+' : '−') + money.fmt(Math.abs(net))}</div>
        <div className="tiny" style={{ opacity: 0.7 }}>{parts}</div>
      </div>
      <button className="bd-selbar-btn" onClick={onDone}>Listo</button>
    </div>
  )
}

