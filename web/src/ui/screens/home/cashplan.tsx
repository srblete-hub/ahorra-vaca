// "Tu dinero hasta la nómina" (CashPlanUi.kt). La tarjeta lleva su propio fondo claro, igual en claro y oscuro.
import { useEffect, useRef, useState, type ReactNode, type RefObject } from 'react'
import type { LocalDate } from '../../../data/dates'
import type { CashPlan } from '../../../domain'
import { daysBetween, ep, isAfter, isBefore, lenOfMonth, plusD } from '../../../domain/kt'
import { useMoney } from '../../components'
import { Icon } from '../../icons'
import { shortDay } from './shared'

const INK = '#17150F', MUTED = '#7C7566', GRID = '#EDE6D6', BLUE = '#2E6B3F', BAND = '#DCEBD3', ORANGE = '#C2415C'
const GREEN = '#2E6B3F', RED = '#C2415C', PAPER = '#FFFDF8', PEACH_ZONE = '#FBE3E8', MINT = '#DCEBD3'

function useWidth<T extends HTMLElement>(): [RefObject<T>, number] {
  const ref = useRef<T>(null)
  const [w, setW] = useState(300)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    setW(el.clientWidth)
    const ro = new ResizeObserver(() => setW(el.clientWidth))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  return [ref, w]
}

export function CashPlanCard({ c, onCushion, onMoveFromSavings }: { c: CashPlan; onCushion: (v: number) => void; onMoveFromSavings: (() => void) | null }) {
  const m = useMoney()
  const eur = (v: number) => {
    if (m.hidden) return m.fmt(v)
    const s = m.fmt(v).split(',')[0]
    return s.endsWith('€') ? s : `${s} €`
  }
  const all = c.series.length > 0 ? c.series.map((x) => x[1]) : [c.balance]

  // Desplegable: hasta la nómina, los próximos 7 días o hasta fin de mes.
  const toMonthEnd = lenOfMonth(c.today) - c.today.day + 1
  const options: Array<[string, number]> = [[`Hasta tu ${c.paydayName.toLowerCase()}`, all.length]]
  if (all.length > 8) options.push(['Próximos 7 días', 8])
  if (toMonthEnd >= 2 && toMonthEnd < all.length) options.push(['Hasta fin de mes', toMonthEnd])
  const [pick, setPick] = useState(0)
  const [open, setOpen] = useState(false)
  const [rangeLabel, len] = options[Math.min(pick, options.length - 1)]
  const values = all.slice(0, len)
  const lastDay = plusD(c.today, values.length - 1)

  let minIdx = 0
  values.forEach((v, i) => { if (v < values[minIdx]) minIdx = i })
  const minValue = values[minIdx]
  const endValue = values[values.length - 1]
  const tightDay = plusD(c.today, minIdx)
  const tight = c.perDayBefore < 0 || minValue < c.cushion + 25
  const causes = c.events.filter((e) => e.amount < 0 && !isAfter(e.date, tightDay))
  const tightCause = causes.length ? causes.reduce((a, b) => (-b.amount > -a.amount ? b : a)) : null
  const payRaw = daysBetween(c.today, c.payday)
  const payIdx = payRaw >= 0 && payRaw < values.length ? payRaw : null
  const shownEvents = c.events.filter((e) => !isBefore(e.date, c.today) && !isAfter(e.date, lastDay))
  const need = Math.ceil(Math.max(10, c.cushion + 25 - minValue) / 10) * 10
  const perDay = (d: LocalDate) => Math.max(0, c.splitDate && c.perDayAfter != null && !isBefore(d, c.splitDate) ? c.perDayAfter : c.perDayBefore)

  // Semanas (un último trozo de 1–2 días se junta con la anterior).
  let weeks: number[][] = []
  for (let i = 0; i < values.length; i += 7) weeks.push(values.map((_, k) => k).slice(i, i + 7))
  weeks = weeks.slice(0, 4)
  if (weeks.length >= 2 && weeks[weeks.length - 1].length < 3) {
    const last = weeks.pop()!
    weeks[weeks.length - 1] = [...weeks[weeks.length - 1], ...last]
  }
  const nextCushion = c.cushion < 75 ? 100 : c.cushion < 125 ? 150 : 50

  return (
    <div className="hm-cash">
      <div style={{ position: 'relative', alignSelf: 'flex-start' }}>
        <button className="hm-cash-range" onClick={() => setOpen(!open)} aria-expanded={open} aria-label={`${rangeLabel}. Cambiar periodo`}>
          {rangeLabel} <Icon name="chevronDown" size={16} />
        </button>
        {open && (
          <>
            <div className="hm-menu-scrim" onClick={() => setOpen(false)} />
            <div className="hm-menu" role="menu">
              {options.map(([label], i) => (
                <button key={label} role="menuitem" style={{ fontWeight: i === pick ? 600 : 400 }} onClick={() => { setPick(i); setOpen(false) }}>{label}</button>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="hm-cash-white">
        <div className="row" style={{ alignItems: 'flex-start', gap: 8 }}>
          <div className="grow">
            <div style={{ fontSize: 12, color: MUTED }}>Tu dinero en el banco</div>
            <div className="num" style={{ fontSize: 24, fontWeight: 600, letterSpacing: -0.5, color: INK }}>{eur(c.balance)} → {eur(endValue)}</div>
            <div style={{ fontSize: 12, color: MUTED }}>hoy → {shortDay(lastDay)}</div>
          </div>
          <div className="hm-cash-perday"><span style={{ width: 6, height: 6, borderRadius: 3, background: GREEN }} />{eur(perDay(c.today))} al día</div>
        </div>
        <Chart values={values} c={c} minIdx={minIdx} payIdx={payIdx} events={shownEvents.map((e) => [Math.min(values.length - 1, Math.max(0, daysBetween(c.today, e.date))), e.amount])}
          minLabel={`Lo más bajo: ${eur(minValue)}`}
          axis={(() => {
            const labels: Array<[number, string, string]> = [[0, 'Hoy', MUTED]]
            if (minIdx > 0) labels.push([minIdx, shortDay(tightDay), ORANGE])
            if (payIdx != null && payIdx > 0 && payIdx !== minIdx) labels.push([payIdx, c.paydayName.charAt(0).toUpperCase() + c.paydayName.slice(1), MUTED])
            if (values.length - 1 !== minIdx && values.length - 1 !== payIdx) labels.push([values.length - 1, shortDay(lastDay), MUTED])
            return labels
          })()} />
        <div className="hm-legend">
          <Legend color={BLUE}>Tu saldo</Legend><Legend color={GREEN}>Cobros</Legend><Legend color={RED}>Recibos</Legend>
          <span className="row" style={{ gap: 4 }}><span style={{ width: 16, height: 2, background: ORANGE, opacity: 0.6 }} />Colchón</span>
        </div>
        <div style={{ fontSize: 12, lineHeight: '16px', color: MUTED, marginTop: 8 }}>
          Lo que tendrás cada día si gastas {eur(perDay(c.today))} al día. Baja con tus gastos y recibos, y sube cuando cobras.
        </div>
      </div>

      <div style={{ fontSize: 16, fontWeight: 500, color: INK, margin: '16px 0 8px 8px' }}>Para gastar cada semana</div>
      <div className="hm-cash-weeks">
        {weeks.map((idx, k) => {
          const from = plusD(c.today, idx[0]), to = plusD(c.today, idx[idx.length - 1])
          const budget = idx.reduce((a, i) => a + perDay(plusD(c.today, i)), 0)
          const inWeek = shownEvents.filter((e) => !isBefore(e.date, from) && !isAfter(e.date, to))
          const ev = inWeek.length ? inWeek.reduce((a, b) => (Math.abs(b.amount) > Math.abs(a.amount) ? b : a)) : null
          const hot = tight && idx.includes(minIdx)
          const label = k === 0 ? 'Esta semana' : ep(from) === ep(to) ? shortDay(to) : from.month === to.month ? `${from.day}–${shortDay(to)}` : `${shortDay(from)}–${shortDay(to)}`
          return (
            <div key={k} className="hm-cash-week" style={{ background: hot ? PEACH_ZONE : PAPER }}>
              <div className="ellipsis" style={{ fontSize: 12, color: hot ? ORANGE : MUTED }}>{label}</div>
              <div className="num ellipsis" style={{ fontSize: 18, fontWeight: 600, color: hot ? ORANGE : INK, margin: '8px 0' }}>{eur(budget)}</div>
              <div className="ellipsis" style={{ fontSize: 12, color: ev == null ? MUTED : ev.amount > 0 ? GREEN : RED }}>
                {ev ? (ev.amount > 0 ? '+ ' : '') + ev.label : `${idx.length} ${idx.length === 1 ? 'día' : 'días'}`}
              </div>
            </div>
          )
        })}
      </div>

      <div className="hm-cash-white" style={{ marginTop: 8, padding: 24 }}>
        <div className="hm-cash-flag" style={{ background: tight ? PEACH_ZONE : MINT, color: tight ? ORANGE : GREEN }}>
          <Icon name="warningOutline" size={16} />
          {tight ? `${shortDay(tightDay)} · Vas justo` : 'Vas bien'}
        </div>
        <div className="hm-serif" style={{ fontSize: 22, lineHeight: '28px', fontWeight: 800, color: INK, marginTop: 16 }}>
          {c.perDayBefore < 0 ? `No llegas con ${eur(c.cushion)} de colchón: te faltarían unos ${eur(c.shortfall)} antes de cobrar.`
            : tightCause ? `${tightCause.label} llega antes de la ${c.paydayName.toLowerCase()}. Tu saldo podría bajar a ${eur(minValue)}.`
              : `Gastando lo de cada día, tu punto más bajo será ${eur(minValue)} el ${shortDay(tightDay)}.`}
        </div>
        <div className="row" style={{ gap: 8, marginTop: 24 }}>
          {tight && onMoveFromSavings && (
            <button className="hm-cash-btn dark" style={{ flex: 1.5 }} onClick={onMoveFromSavings}>Pasar {need} € del ahorro</button>
          )}
          <button className="hm-cash-btn" style={{ flex: 1 }} onClick={() => onCushion(nextCushion)}>Colchón {eur(c.cushion)}</button>
        </div>
      </div>
    </div>
  )
}

function Legend({ color, children }: { color: string; children: ReactNode }) {
  return <span className="row" style={{ gap: 4 }}><span style={{ width: 8, height: 8, borderRadius: 4, background: color }} />{children}</span>
}

function Chart({ values, c, minIdx, payIdx, events, minLabel, axis }: {
  values: number[]; c: CashPlan; minIdx: number; payIdx: number | null; events: Array<[number, number]>; minLabel: string; axis: Array<[number, string, string]>
}) {
  const [ref, w] = useWidth<HTMLDivElement>()
  const H = 160
  const minV = Math.min(Math.min(...values), 0)
  const maxV = Math.max(Math.max(...values), 1) * 1.15
  const range = maxV - minV || 1
  const fx = (i: number) => 0.04 + 0.92 * i / Math.max(1, values.length - 1)
  const fy = (v: number) => 0.18 + 0.72 * (1 - (v - minV) / range)
  const x = (i: number) => w * fx(i), y = (v: number) => H * fy(v)
  let d = `M${x(0)},${y(values[0])}`
  for (let i = 1; i < values.length; i++) {
    const mid = (x(i - 1) + x(i)) / 2
    d += ` C${mid},${y(values[i - 1])} ${mid},${y(values[i])} ${x(i)},${y(values[i])}`
  }
  const clampLeft = (cx: number, width: number) => Math.min(Math.max(0, cx - width / 2), Math.max(0, w - width))
  const sorted = [...axis].sort((a, b) => a[0] - b[0])
  let last = -1
  const shownAxis = sorted.filter(([i]) => {
    const f = fx(i)
    if (last < 0 || (f - last) * w >= 64) { last = f; return true }
    return false
  })
  return (
    <div ref={ref} style={{ marginTop: 16 }}>
      <div style={{ position: 'relative', height: H }}>
        <svg width={w} height={H} style={{ display: 'block', overflow: 'visible' }} aria-hidden="true">
          {[1, 2, 3].map((k) => <line key={k} x1={0} x2={w} y1={H * k / 4} y2={H * k / 4} stroke={GRID} strokeWidth={1} />)}
          <line x1={0} x2={w} y1={y(c.cushion)} y2={y(c.cushion)} stroke={ORANGE} strokeOpacity={0.6} strokeDasharray="8 8" />
          {payIdx != null && <line x1={x(payIdx)} x2={x(payIdx)} y1={0} y2={H} stroke={MUTED} strokeOpacity={0.5} strokeDasharray="4 6" />}
          {values.length >= 2 && <>
            <path d={d} fill="none" stroke={BAND} strokeWidth={16} strokeLinecap="round" />
            <path d={d} fill="none" stroke={BLUE} strokeWidth={2.5} strokeLinecap="round" />
          </>}
          {events.map(([i, amount], k) => (
            <g key={k}>
              <circle cx={x(i)} cy={y(values[i])} r={6} fill={PAPER} />
              <circle cx={x(i)} cy={y(values[i])} r={4} fill={amount > 0 ? GREEN : RED} />
            </g>
          ))}
          <circle cx={x(0)} cy={y(values[0])} r={7} fill={PAPER} stroke={BLUE} strokeWidth={2.5} />
          {minIdx > 0 && <>
            <circle cx={x(minIdx)} cy={y(values[minIdx])} r={7} fill={PAPER} />
            <circle cx={x(minIdx)} cy={y(values[minIdx])} r={5} fill={ORANGE} />
          </>}
        </svg>
        {minIdx > 0 && (
          <div className="hm-cash-tip" style={{ left: clampLeft(x(minIdx), 168), top: Math.max(0, y(values[minIdx]) - 40), width: 168 }}>
            <span>{minLabel}</span>
          </div>
        )}
      </div>
      <div style={{ position: 'relative', height: 24, marginTop: 8 }}>
        {shownAxis.map(([i, t, col]) => (
          <div key={i} className="ellipsis" style={{ position: 'absolute', left: clampLeft(x(i), 64), width: 64, textAlign: 'center', fontSize: 12, color: col }}>{t}</div>
        ))}
      </div>
    </div>
  )
}
