// Tarjetas de Patrimonio: objetivo de ahorro (StoryCards.kt), mes típico y "Mis ahorros" (MoreCards.kt),
// tu dinero hasta la nómina (CashPlanUi.kt) y "Empieza aquí" (NowWidgets.kt).
import { Fragment, useState } from 'react'
import { HIDDEN, money0 } from '../../../data/format'
import { MONTHS, MONTHS_SHORT, toEpochDay, today as todayDate, type LocalDate } from '../../../data/dates'
import type { CashPlan, GoalStatus, TypicalMonth } from '../../../domain'
import { Button, IconTile, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav } from '../../nav'
import { AVAILABLE_COLOR, INVESTED_COLOR, MintCard, Overline, Ring, SAVINGS_COLOR, shortDay, smoothPath, useWidth } from './common'

const DAY = 86_400_000
const plusDays = (d: LocalDate, n: number): LocalDate => {
  const t = new Date((toEpochDay(d) + n) * DAY)
  return { year: t.getUTCFullYear(), month: t.getUTCMonth() + 1, day: t.getUTCDate() }
}
const between = (a: LocalDate, b: LocalDate) => toEpochDay(b) - toEpochDay(a)

/** "28.033 €" (sin decimales), respetando "ocultar importes". */
function useWhole() {
  const m = useMoney()
  return (v: number) => (m.hidden ? HIDDEN : money0(v, m.currency))
}

// ---------- Objetivo de ahorro ----------

/** Sin objetivo: invita a ponerse uno. */
export function SavingsGoalEmpty({ onEdit }: { onEdit: () => void }) {
  return (
    <div className="card">
      <div className="w-h20">Tu objetivo de ahorro</div>
      <div className="small muted" style={{ marginTop: 4 }}>Ponte una meta (un viaje, un colchón, una entrada…) y verás aquí cuánto te falta.</div>
      <div style={{ marginTop: 16 }}><Button onClick={onEdit}>Poner objetivo</Button></div>
    </div>
  )
}

/** El objetivo en grande: progreso, retirar/añadir/bloquear, fecha estimada y ahorro semanal. */
export function SavingsGoalHero({ g, weekly, thisWeek, locked, weeklyTip, onWithdraw, onAdd, onLock, onWeeklyTip, onEdit }: {
  g: GoalStatus; weekly: Array<[string, number]>; thisWeek: number | null; locked: boolean; weeklyTip: boolean
  onWithdraw: () => void; onAdd: () => void; onLock: () => void; onWeeklyTip: (v: boolean) => void; onEdit: () => void
}) {
  const whole = useWhole()
  const m = useMoney()
  const frac = g.goal.target > 0 ? Math.max(0, Math.min(1, g.current / g.goal.target)) : 0
  const left = Math.max(0, g.goal.target - g.current)
  const today = todayDate()
  const eta: LocalDate | null = left <= 0 ? today : g.pace > 0 ? plusDays(today, Math.trunc(left / g.pace * 30.4)) : null
  const fmtDate = (d: LocalDate) => `${d.day} de ${MONTHS[d.month - 1]} ${d.year}`
  const weeksDiff = eta ? Math.trunc(between(eta, g.goal.deadline) / 7) : null
  const etaText = eta == null ? 'Aún no ahorras para él: empieza con algo cada semana'
    : weeksDiff == null || weeksDiff === 0 ? fmtDate(eta)
      : weeksDiff > 0 ? `${fmtDate(eta)} · ${weeksDiff} ${weeksDiff === 1 ? 'semana' : 'semanas'} antes`
        : `${fmtDate(eta)} · ${-weeksDiff} ${weeksDiff === -1 ? 'semana' : 'semanas'} tarde`
  const start = g.goal.start
  const bars = weekly.map(([l, v]): [string, number] => (l === 'Esta' && thisWeek != null ? [l, Math.max(v, thisWeek)] : [l, v]))
  const max = Math.max(...bars.map((b) => b[1]), 0) || 1
  return (
    <div className="w-hero">
      <button className="w-hero-top" onClick={onEdit}>
        <div className="w-hero-name">{g.goal.name.trim() || 'Mi objetivo'}</div>
        <div className="w-hero-sub">{whole(g.current)} de {whole(g.goal.target)}</div>
      </button>
      <div className="w-hero-progress">
        <div className="grow">
          <div style={{ fontSize: 18, fontWeight: 500 }}>Progreso del objetivo</div>
          <div className="small" style={{ opacity: 0.8 }}>{left <= 0 ? '¡Conseguido!' : `Te faltan ${whole(left)}`}</div>
        </div>
        <Ring fraction={frac} size={64} stroke={5} color="#fff" track="rgba(255,255,255,0.25)">
          <span style={{ fontSize: 14, fontWeight: 600 }}>{Math.trunc(frac * 100)}%</span>
        </Ring>
      </div>
      <div className="w-hero-actions">
        <GoalAction icon="remove" label="Retirar" enabled={!locked} onClick={onWithdraw} />
        <GoalAction icon="add" label="Añadir" onClick={onAdd} />
        <GoalAction icon={locked ? 'lock' : 'lockOpen'} label={locked ? 'Bloqueado' : 'Bloquear'} onClick={onLock} />
      </div>
      <div className="w-hero-detail">
        <div className="w-grab" />
        <button className="w-hero-eta" onClick={onEdit}>
          <span className="w-hero-flag"><Icon name="flag" size={24} /></span>
          <span className="grow" style={{ textAlign: 'left' }}>
            <span style={{ display: 'block', fontSize: 18, fontWeight: 500 }}>Fecha estimada</span>
            <span style={{ display: 'block', fontSize: 14, color: '#7C7566' }}>{etaText}</span>
          </span>
          <Icon name="chevronRight" size={24} />
        </button>
        <div className="w-hero-line" />
        <DetailRow icon="calendar" label="Fecha de inicio" value={`${start.day} ${MONTHS_SHORT[start.month - 1]} ${start.year}`} />
        <DetailRow icon="schedule" label="Quedan" value={`${Math.max(0, between(today, g.goal.deadline))} días`} />
        <div className="w-weekly">
          <div className="row" style={{ alignItems: 'center' }}>
            <div className="grow">
              <div style={{ fontSize: 16, fontWeight: 500 }}>Ahorro semanal</div>
              <div style={{ fontSize: 12, color: '#7C7566' }}>Pepe elige una cantidad segura cada semana</div>
            </div>
            <button className={`w-switch ${weeklyTip ? 'on' : ''}`} role="switch" aria-checked={weeklyTip} aria-label="Ahorro semanal" onClick={() => onWeeklyTip(!weeklyTip)} />
          </div>
          <div className="w-weekly-bars">
            {bars.map(([label, v]) => {
              const current = label === 'Esta'
              return (
                <div key={label} className="w-weekly-col">
                  <div style={{ fontSize: 11, color: current ? '#2E6B3F' : '#7C7566', minHeight: 15 }}>{v > 0 ? (m.hidden ? HIDDEN : `${Math.round(v)} €`) : ''}</div>
                  <div style={{ width: '100%', height: Math.max(4, 64 * v / max), borderRadius: 8, background: current ? '#2E6B3F' : '#EDE6D6' }} />
                  <div style={{ fontSize: 11, color: '#7C7566', marginTop: 4 }}>{label}</div>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}

function GoalAction({ icon, label, enabled = true, onClick }: { icon: string; label: string; enabled?: boolean; onClick: () => void }) {
  return (
    <button className="w-goal-action" disabled={!enabled} onClick={onClick}>
      <span className="w-goal-action-ico"><Icon name={icon} size={16} /></span>
      <span>{label}</span>
    </button>
  )
}

function DetailRow({ icon, label, value }: { icon: string; label: string; value: string }) {
  return (
    <div className="w-detail-row">
      <Icon name={icon} size={24} style={{ color: '#7C7566' }} />
      <span className="grow" style={{ color: '#7C7566' }}>{label}</span>
      <span style={{ fontWeight: 500 }}>{value}</span>
    </div>
  )
}

// ---------- Mes típico ----------

/** Este mes frente a tu mes típico: la línea discontinua es lo que sueles llevar gastado a cada día. */
export function TypicalMonthCard({ t }: { t: TypicalMonth }) {
  const m = useMoney()
  const [ref, w] = useWidth<HTMLDivElement>()
  const H = 160
  const maxV = Math.max(...t.typical, ...t.now, 0) || 1
  const len = t.typical.length
  const x = (i: number) => (w * i) / Math.max(1, len - 1)
  const y = (v: number) => H * (1 - v / (maxV * 1.08))
  const curve = (v: number[]) => smoothPath(v.map((val, i) => [x(i), y(val)]))
  const above = t.diff > 0
  const last = t.now.length > 0 ? t.now[t.now.length - 1] : null
  return (
    <div className="card w-card32">
      <div className="w-h24">Este mes frente a tu mes típico</div>
      <div className="row" style={{ gap: 16, marginTop: 8 }}>
        <Legend color="var(--gain)" text="Este mes" />
        <Legend color="var(--muted)" text="Tu mes típico" dashed />
      </div>
      <div ref={ref} style={{ marginTop: 16, height: H }}>
        {w > 0 && (
          <svg width={w} height={H} style={{ display: 'block', overflow: 'visible' }} aria-hidden="true">
            {[1, 2, 3].map((k) => <line key={k} x1={0} x2={w} y1={(H * k) / 4} y2={(H * k) / 4} stroke="var(--muted)" strokeOpacity={0.15} strokeWidth={1} />)}
            {t.typical.length >= 2 && <path d={curve(t.typical)} fill="none" stroke="var(--muted)" strokeWidth={2} strokeLinecap="round" strokeDasharray="5 5" />}
            {t.now.length >= 2 && <path d={curve(t.now)} fill="none" stroke="var(--gain)" strokeWidth={3} strokeLinecap="round" />}
            {last != null && (
              <>
                <circle cx={x(t.now.length - 1)} cy={y(last)} r={6} fill="var(--gain)" />
                <circle cx={x(t.now.length - 1)} cy={y(last)} r={2} fill="var(--ink)" />
              </>
            )}
          </svg>
        )}
      </div>
      <div className="row tiny muted" style={{ marginTop: 4, gap: 0 }}>
        <span className="grow">1</span>
        <span>Hoy, {t.today}</span>
        <span className="grow" style={{ textAlign: 'right' }}>{len}</span>
      </div>
      <div style={{ marginTop: 16, color: above ? 'var(--loss)' : 'var(--gain)' }}>
        {Math.abs(t.diff) < 5
          ? `Vas justo como sueles: ${m.fmt(t.spent)} a día ${t.today}.`
          : `Llevas ${m.fmt(t.spent)}: ${m.fmt(Math.abs(t.diff))} ${above ? 'más' : 'menos'} que en tu mes típico a estas alturas.`}
      </div>
      <div className="tiny muted" style={{ marginTop: 4 }}>
        Un mes típico acaba en unos {m.fmt(t.typicalTotal)} (media de {t.monthsUsed} {t.monthsUsed === 1 ? 'mes' : 'meses'}, sin alquiler ni fijos).
      </div>
    </div>
  )
}

function Legend({ color, text, dashed }: { color: string; text: string; dashed?: boolean }) {
  return (
    <div className="row" style={{ gap: 8 }}>
      <span style={{ width: 16, height: 16, borderRadius: '50%', flexShrink: 0, ...(dashed ? { border: `2px solid ${color}` } : { background: color }) }} />
      <span className="small muted">{text}</span>
    </div>
  )
}

// ---------- Mis ahorros ----------

/** Cada objetivo con lo que llevas y su anillo de %. */
export function GoalsRingList({ goals, onOpen }: { goals: GoalStatus[]; onOpen: (i: number) => void }) {
  const m = useMoney()
  return (
    <div className="col">
      <div className="w-h28" style={{ margin: '0 0 8px 8px' }}>Mis ahorros</div>
      {goals.map((g, i) => {
        const frac = g.goal.target > 0 ? Math.max(0, Math.min(1, g.current / g.goal.target)) : 0
        return (
          <button key={i} className="w-ring-row" onClick={() => onOpen(i)}>
            <span className="w-ring-ico"><Icon name="flag" size={24} /></span>
            <span className="grow" style={{ minWidth: 0 }}>
              <span className="ellipsis" style={{ display: 'block', fontWeight: 500 }}>{g.goal.name.trim() || 'Objetivo'}</span>
              <span style={{ display: 'block' }}>
                <span className="num" style={{ fontWeight: 600 }}>{m.fmt(g.current)}</span>
                <span className="tiny muted"> de {m.fmt(g.goal.target)}</span>
              </span>
            </span>
            <Ring fraction={frac} size={56} stroke={4} color="var(--gain)" track="var(--chip)">
              <span style={{ fontSize: 12, fontWeight: 600 }}>{Math.trunc(frac * 100)} %</span>
            </Ring>
          </button>
        )
      })}
    </div>
  )
}

// ---------- Tu dinero hasta la nómina ----------

// Paleta propia de la tarjeta (igual en claro y oscuro, como en Android).
const C = {
  peach: '#DCEBD3', peachSoft: '#EAF2E4', zone: '#FBE3E8', paper: '#F6F1E6', ink: '#17150F', muted: '#7C7566',
  grid: '#EDE6D6', blue: '#2E6B3F', band: '#DCEBD3', orange: '#C2415C', green: '#2E6B3F', red: '#C2415C', mint: '#DCEBD3', white: '#FFFDF8',
}

/**
 * Tu dinero hasta la nómina: saldo previsto, curva día a día con el colchón, cobros y recibos, lo que puedes
 * gastar cada semana y el aviso del momento justo.
 */
export function CashPlanCard({ c, onCushion, onMoveFromSavings }: { c: CashPlan; onCushion: (v: number) => void; onMoveFromSavings?: () => void }) {
  const m = useMoney()
  const eur = (v: number) => { const s = m.fmt(v).split(',')[0]; return s.endsWith('€') ? s : `${s} €` }
  const all = c.series.length > 0 ? c.series.map((p) => p[1]) : [c.balance]

  const monthEnd: LocalDate = { year: c.today.year, month: c.today.month, day: new Date(Date.UTC(c.today.year, c.today.month, 0)).getUTCDate() }
  const options: Array<[string, number]> = [[`Hasta tu ${c.paydayName.toLocaleLowerCase('es')}`, all.length]]
  if (all.length > 8) options.push(['Próximos 7 días', 8])
  const toMonthEnd = between(c.today, monthEnd) + 1
  if (toMonthEnd >= 2 && toMonthEnd < all.length) options.push(['Hasta fin de mes', toMonthEnd])
  return <CashPlanBody c={c} all={all} options={options} eur={eur} onCushion={onCushion} onMoveFromSavings={onMoveFromSavings} />
}

function CashPlanBody({ c, all, options, eur, onCushion, onMoveFromSavings }: {
  c: CashPlan; all: number[]; options: Array<[string, number]>; eur: (v: number) => string; onCushion: (v: number) => void; onMoveFromSavings?: () => void
}) {
  const [pick, setPick] = useState(0)
  const [ref, w] = useWidth<HTMLDivElement>()
  const [rangeLabel, len] = options[Math.max(0, Math.min(pick, options.length - 1))]
  const values = all.slice(0, len)
  const lastDay = plusDays(c.today, values.length - 1)
  let minIdx = 0
  values.forEach((v, i) => { if (v < values[minIdx]) minIdx = i })
  const minValue = values[minIdx]
  const endValue = values[values.length - 1]
  const tightDay = plusDays(c.today, minIdx)
  const tight = c.perDayBefore < 0 || minValue < c.cushion + 25
  const tightCause = c.events.filter((e) => e.amount < 0 && toEpochDay(e.date) <= toEpochDay(tightDay)).reduce<CashPlan['events'][number] | null>((a, e) => (a == null || -e.amount > -a.amount ? e : a), null)
  const payIdxRaw = between(c.today, c.payday)
  const payIdx = payIdxRaw >= 0 && payIdxRaw < values.length ? payIdxRaw : null
  const shownEvents = c.events.filter((e) => toEpochDay(e.date) >= toEpochDay(c.today) && toEpochDay(e.date) <= toEpochDay(lastDay))
  const need = Math.ceil(Math.max(10, c.cushion + 25 - minValue) / 10) * 10
  const perDay = (d: LocalDate) => {
    const after = c.perDayAfter, split = c.splitDate
    return Math.max(0, split != null && after != null && toEpochDay(d) >= toEpochDay(split) ? after : c.perDayBefore)
  }

  // Gráfico
  const H = 160
  const minV = Math.min(...values, 0)
  const maxV = Math.max(Math.max(...values), 1) * 1.15
  const range = maxV - minV > 0 ? maxV - minV : 1
  const fx = (i: number) => 0.04 + (0.92 * i) / Math.max(1, values.length - 1)
  const fy = (v: number) => 0.18 + 0.72 * (1 - (v - minV) / range)
  const X = (i: number) => w * fx(i), Y = (v: number) => H * fy(v)
  const floatLeft = (x: number, width: number) => Math.max(0, Math.min(x - width / 2, Math.max(0, w - width)))

  // Etiquetas del eje
  const labels: Array<[number, string, string]> = [[fx(0), 'Hoy', C.muted]]
  if (minIdx > 0) labels.push([fx(minIdx), shortDay(tightDay), C.orange])
  if (payIdx != null && payIdx > 0 && payIdx !== minIdx) labels.push([fx(payIdx), c.paydayName.charAt(0).toLocaleUpperCase('es') + c.paydayName.slice(1), C.muted])
  if (values.length - 1 !== minIdx && values.length - 1 !== payIdx) labels.push([fx(values.length - 1), shortDay(lastDay), C.muted])
  const shownLabels: Array<[number, string, string]> = []
  let lastF = -1
  for (const l of [...labels].sort((a, b) => a[0] - b[0])) {
    if (lastF < 0 || (l[0] - lastF) * w >= 64) { shownLabels.push(l); lastF = l[0] }
  }

  // Semanas
  const idx = values.map((_, i) => i)
  let weeks: number[][] = []
  for (let i = 0; i < idx.length; i += 7) weeks.push(idx.slice(i, i + 7))
  weeks = weeks.slice(0, 4)
  if (weeks.length >= 2 && weeks[weeks.length - 1].length < 3) weeks = [...weeks.slice(0, -2), [...weeks[weeks.length - 2], ...weeks[weeks.length - 1]]]

  const next = c.cushion < 75 ? 100 : c.cushion < 125 ? 150 : 50

  return (
    <div className="w-cash">
      <label className="w-cash-pick">
        <span>{rangeLabel}</span>
        <Icon name="chevronDown" size={16} />
        <select aria-label="Cambiar periodo" value={pick} onChange={(e) => setPick(Number(e.target.value))}>
          {options.map(([l], i) => <option key={l} value={i}>{l}</option>)}
        </select>
      </label>

      <div className="w-cash-white" style={{ marginTop: 16, padding: 16 }}>
        <div className="row" style={{ alignItems: 'flex-start', gap: 8 }}>
          <div className="grow">
            <div style={{ fontSize: 12, color: C.muted }}>Tu dinero en el banco</div>
            <div className="num" style={{ fontSize: 24, fontWeight: 600, letterSpacing: '-0.02em' }}>{eur(c.balance)} → {eur(endValue)}</div>
            <div style={{ fontSize: 12, color: C.muted }}>hoy → {shortDay(lastDay)}</div>
          </div>
          <div className="w-cash-chip">
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: C.green }} />
            {eur(perDay(c.today))} al día
          </div>
        </div>

        <div ref={ref} style={{ position: 'relative', height: H, marginTop: 16 }}>
          {w > 0 && (
            <>
              <svg width={w} height={H} style={{ display: 'block', overflow: 'visible' }} aria-hidden="true">
                {[1, 2, 3].map((k) => <line key={k} x1={0} x2={w} y1={(H * k) / 4} y2={(H * k) / 4} stroke={C.grid} strokeWidth={1} />)}
                <line x1={0} x2={w} y1={Y(c.cushion)} y2={Y(c.cushion)} stroke={C.orange} strokeOpacity={0.6} strokeDasharray="4 4" />
                {payIdx != null && <line x1={X(payIdx)} x2={X(payIdx)} y1={0} y2={H} stroke={C.muted} strokeOpacity={0.5} strokeDasharray="2 3" />}
                {values.length >= 2 && (() => {
                  const d = smoothPath(values.map((v, i) => [X(i), Y(v)]))
                  return (
                    <>
                      <path d={d} fill="none" stroke={C.band} strokeWidth={16} strokeLinecap="round" />
                      <path d={d} fill="none" stroke={C.blue} strokeWidth={2.5} strokeLinecap="round" />
                    </>
                  )
                })()}
                {shownEvents.map((e, k) => {
                  const i = Math.max(0, Math.min(values.length - 1, between(c.today, e.date)))
                  return (
                    <Fragment key={k}>
                      <circle cx={X(i)} cy={Y(values[i])} r={6} fill={C.white} />
                      <circle cx={X(i)} cy={Y(values[i])} r={4} fill={e.amount > 0 ? C.green : C.red} />
                    </Fragment>
                  )
                })}
                <circle cx={X(0)} cy={Y(values[0])} r={7} fill={C.white} />
                <circle cx={X(0)} cy={Y(values[0])} r={7} fill="none" stroke={C.blue} strokeWidth={2.5} />
                {minIdx > 0 && (
                  <>
                    <circle cx={X(minIdx)} cy={Y(minValue)} r={7} fill={C.white} />
                    <circle cx={X(minIdx)} cy={Y(minValue)} r={5} fill={C.orange} />
                  </>
                )}
              </svg>
              {minIdx > 0 && (
                <div className="w-cash-tip" style={{ left: floatLeft(X(minIdx), 168), top: Math.max(0, Y(minValue) - 40), width: 168 }}>
                  <span>Lo más bajo: {eur(minValue)}</span>
                </div>
              )}
            </>
          )}
        </div>
        <div style={{ position: 'relative', height: 24, marginTop: 8 }}>
          {w > 0 && shownLabels.map(([f, t, col]) => (
            <span key={t + f} className="ellipsis" style={{ position: 'absolute', left: floatLeft(w * f, 64), width: 64, textAlign: 'center', fontSize: 12, color: col }}>{t}</span>
          ))}
        </div>
        <div className="w-cash-legend">
          <LegendDot color={C.blue} text="Tu saldo" /><LegendDot color={C.green} text="Cobros" /><LegendDot color={C.red} text="Recibos" />
          <span className="row" style={{ gap: 4 }}><span style={{ width: 16, height: 2, background: C.orange, opacity: 0.6 }} /><span>Colchón</span></span>
        </div>
        <div style={{ fontSize: 12, lineHeight: '16px', color: C.muted, marginTop: 8 }}>
          Lo que tendrás cada día si gastas {eur(perDay(c.today))} al día. Baja con tus gastos y recibos, y sube cuando cobras.
        </div>
      </div>

      <div style={{ fontSize: 16, fontWeight: 500, margin: '16px 0 8px 8px' }}>Para gastar cada semana</div>
      <div className="w-cash-weeks">
        {weeks.map((ix, k) => {
          const from = plusDays(c.today, ix[0]), to = plusDays(c.today, ix[ix.length - 1])
          const budget = ix.reduce((a, i) => a + perDay(plusDays(c.today, i)), 0)
          const ev = shownEvents.filter((e) => toEpochDay(e.date) >= toEpochDay(from) && toEpochDay(e.date) <= toEpochDay(to))
            .reduce<CashPlan['events'][number] | null>((a, e) => (a == null || Math.abs(e.amount) > Math.abs(a.amount) ? e : a), null)
          const hot = tight && ix.includes(minIdx)
          const label = k === 0 ? 'Esta semana' : toEpochDay(from) === toEpochDay(to) ? shortDay(to)
            : from.month === to.month ? `${from.day}–${shortDay(to)}` : `${shortDay(from)}–${shortDay(to)}`
          return (
            <div key={k} className="w-cash-week" style={{ background: hot ? C.zone : C.white }}>
              <div className="ellipsis" style={{ fontSize: 12, color: hot ? C.orange : C.muted }}>{label}</div>
              <div className="ellipsis num" style={{ fontSize: 18, fontWeight: 600, color: hot ? C.orange : C.ink, margin: '8px 0' }}>{eur(budget)}</div>
              <div className="ellipsis" style={{ fontSize: 12, color: ev == null ? C.muted : ev.amount > 0 ? C.green : C.red }}>
                {ev ? (ev.amount > 0 ? '+ ' : '') + ev.label : `${ix.length} ${ix.length === 1 ? 'día' : 'días'}`}
              </div>
            </div>
          )
        })}
      </div>

      <div className="w-cash-white" style={{ marginTop: 8, padding: 24 }}>
        <span className="w-cash-badge" style={{ background: tight ? C.zone : C.mint, color: tight ? C.orange : C.green }}>
          <Icon name="warning" size={16} />
          {tight ? `${shortDay(tightDay)} · Vas justo` : 'Vas bien'}
        </span>
        <div className="w-cash-msg">
          {c.perDayBefore < 0
            ? `No llegas con ${eur(c.cushion)} de colchón: te faltarían unos ${eur(c.shortfall)} antes de cobrar.`
            : tightCause != null
              ? `${tightCause.label} llega antes de la ${c.paydayName.toLocaleLowerCase('es')}. Tu saldo podría bajar a ${eur(minValue)}.`
              : `Gastando lo de cada día, tu punto más bajo será ${eur(minValue)} el ${shortDay(tightDay)}.`}
        </div>
        <div className="row" style={{ gap: 8, marginTop: 24 }}>
          {tight && onMoveFromSavings && (
            <button className="w-cash-btn" style={{ flex: 1.5, background: C.ink, color: C.white }} onClick={onMoveFromSavings}>Pasar {need} € del ahorro</button>
          )}
          <button className="w-cash-btn" style={{ flex: 1, background: C.grid, color: C.ink }} onClick={() => onCushion(next)}>Colchón {eur(c.cushion)}</button>
        </div>
      </div>
    </div>
  )
}

function LegendDot({ color, text }: { color: string; text: string }) {
  return <span className="row" style={{ gap: 4 }}><span style={{ width: 8, height: 8, borderRadius: '50%', background: color }} /><span>{text}</span></span>
}

// ---------- Empieza aquí ----------

/** Cuando aún no hay nada: pide el saldo de hoy de cada sitio, con atajos claros. */
export function SetupCard({ onStepByStep }: { onStepByStep: () => void }) {
  const nav = useNav()
  return (
    <MintCard>
      <Overline>Empieza aquí</Overline>
      <div className="w-h24" style={{ marginTop: 8 }}>¿Cuánto tienes ahora mismo?</div>
      <div className="muted" style={{ marginTop: 4 }}>Pon el saldo que tiene hoy cada sitio donde guardas dinero. Lo que arrastras de meses anteriores ya va incluido ahí.</div>
      <div className="col" style={{ marginTop: 16 }}>
        <SetupButton title="Cuenta del banco" sub="Nómina, cuenta corriente…" icon="bank" color={AVAILABLE_COLOR} onClick={() => nav.edit({ name: 'account', account: { name: '', type: 'BANK', balance: 0 } })} />
        <SetupButton title="Ahorro" sub="Cuenta de ahorro, hucha, fondo, con o sin intereses" icon="savings" color={SAVINGS_COLOR} onClick={() => nav.edit({ name: 'account', account: { name: '', type: 'BANK_SAVINGS', balance: 0 } })} />
        <SetupButton title="Inversión" sub="Cripto, acciones o ETF" icon="trendingUp" color={INVESTED_COLOR} onClick={() => nav.edit({ name: 'chooser' })} />
      </div>
      <div style={{ textAlign: 'center', marginTop: 8 }}>
        <button className="link-btn" onClick={onStepByStep}>Hacerlo paso a paso</button>
      </div>
    </MintCard>
  )
}

function SetupButton({ title, sub, icon, color, onClick }: { title: string; sub: string; icon: string; color: string; onClick: () => void }) {
  return (
    <button className="w-setup-btn" onClick={onClick}>
      <IconTile icon={icon} color={color} />
      <span className="grow" style={{ textAlign: 'left' }}>
        <span style={{ display: 'block', fontWeight: 700 }}>{title}</span>
        <span className="small muted" style={{ display: 'block' }}>{sub}</span>
      </span>
      <span style={{ fontSize: 24, color: 'var(--gain)' }}>+</span>
    </button>
  )
}
