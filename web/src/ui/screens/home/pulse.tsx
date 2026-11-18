// El panel "Gasto de la semana" / "Presión de gasto" y sus fichas (PulseUi.kt).
// Los círculos Estabilidad/Control/Progreso se quitaron de Inicio (CLAUDE.md), así que CircleSheet no se porta.
import type { ReactNode } from 'react'
import { today as todayDate, toEpochDay, WEEKDAYS_SHORT } from '../../../data/dates'
import type { Pulse, Zone } from '../../../domain'
import { PulseCalculator } from '../../../domain'
import { fixed, monday, plusD } from '../../../domain/kt'
import { Button, Sheet, useMoney } from '../../components'
import { Icon } from '../../icons'
import { ZONE_DARK } from './shared'

const GREEN = '#BFE3C6', YELLOW = '#E9B949', RED = '#F5A3B4'
const zoneColor = (z: Zone) => (z === 'GREEN' ? GREEN : z === 'YELLOW' ? YELLOW : RED)

export function PulseHeader({ p, onOpen }: { p: Pulse; onOpen: (key: string) => void }) {
  const wc = zoneColor(p.weekZone), pc = zoneColor(p.pressureZone)
  return (
    <div className="hm-pulse">
      <div className="hm-pulse-row">
        <MonitorBox title={<>GASTO DE<br />LA SEMANA</>} onClick={() => onOpen('week')}>
          <div className="hm-pulse-badge" style={{ color: wc, background: `color-mix(in srgb, ${wc} 18%, transparent)` }}>{Math.trunc(p.weekScore)}</div>
          <div style={{ minWidth: 0 }}>
            <div className="hm-pulse-zone" style={{ color: wc }}>{p.weekZone === 'GREEN' ? 'EN RANGO' : p.weekZone === 'YELLOW' ? 'ALGO ALTO' : 'ALTO'}</div>
            <div className="hm-pulse-sub">óptimo hoy: {Math.trunc(p.optimalScore)}</div>
          </div>
        </MonitorBox>
        <MonitorBox title={<>PRESIÓN<br />DE GASTO</>} onClick={() => onOpen('pressure')}>
          <div className="hm-pulse-badge" style={{ color: pc, background: `color-mix(in srgb, ${pc} 18%, transparent)` }}>{fixed(p.pressure, 1)}</div>
          <div style={{ minWidth: 0 }}>
            <div className="hm-pulse-zone" style={{ color: pc }}>{p.pressureLabel}</div>
            <div className="hm-pulse-sub">hoy</div>
          </div>
        </MonitorBox>
      </div>
      <div className="hm-pulse-statement">{p.statement}</div>
    </div>
  )
}

function MonitorBox({ title, onClick, children }: { title: ReactNode; onClick: () => void; children: ReactNode }) {
  return (
    <button className="hm-monitor" onClick={onClick}>
      <div className="row" style={{ alignItems: 'flex-start', gap: 4 }}>
        <div className="grow hm-monitor-title">{title}</div>
        <Icon name="chevronRight" size={24} style={{ opacity: 0.6, flexShrink: 0 }} />
      </div>
      <div className="row" style={{ gap: 8, marginTop: 16 }}>{children}</div>
    </button>
  )
}

function SheetFooter({ onClose }: { onClose: () => void }) {
  return <Button onClick={onClose} style={{ marginTop: 8 }}>Entendido</Button>
}

/** Ficha de un cuadro: semana, presión o (claves antiguas) colchón / margen / salud. */
export function PulseInfoSheet({ k, p, onClose }: { k: string; p: Pulse; onClose: () => void }) {
  if (k === 'week') return <WeekVisualSheet p={p} onClose={onClose} />
  if (!['cushion', 'margin', 'health'].includes(k)) return <PressureVisualSheet p={p} onClose={onClose} />
  return <InfoSheet k={k} p={p} onClose={onClose} />
}

function InfoSheet({ k, p, onClose }: { k: string; p: Pulse; onClose: () => void }) {
  const m = useMoney()
  const i = PulseCalculator.info(k, p, m.fmt, todayDate())
  const c = ZONE_DARK[i.zone]
  return (
    <Sheet title={i.title} onClose={onClose}>
      <div className="col" style={{ gap: 16 }}>
        <div className="row" style={{ gap: 16 }}>
          <div className="hm-info-orb" style={{ background: `color-mix(in srgb, ${c} 18%, transparent)`, color: c }}>{i.value}</div>
          <div style={{ fontSize: 20, fontWeight: 600 }}>{i.zone === 'GREEN' ? 'En verde' : i.zone === 'YELLOW' ? 'En amarillo' : 'En rojo'}</div>
        </div>
        <Section title="Qué mide" text={i.what} />
        <Section title="Tu dato hoy" text={i.now} />
        <Section title="Lo óptimo" text={i.optimal} />
        <Section title="Qué hacer" text={i.todo} />
        <SheetFooter onClose={onClose} />
      </div>
    </Sheet>
  )
}

function Section({ title, text }: { title: string; text: string }) {
  return (
    <div>
      <div className="small muted" style={{ fontWeight: 600 }}>{title}</div>
      <div style={{ whiteSpace: 'pre-line' }}>{text}</div>
    </div>
  )
}

/** Gasto de la semana, de un vistazo: barra con la marca de hoy y una columna por día. */
function WeekVisualSheet({ p, onClose }: { p: Pulse; onClose: () => void }) {
  const m = useMoney()
  const today = todayDate()
  const todayE = toEpochDay(today)
  const mon = monday(today)
  const perDay = Array.from({ length: 7 }, (_, i) => {
    const d = plusD(mon, i)
    const e = toEpochDay(d)
    return { e, spent: p.days.find((x) => toEpochDay(x.date) === e)?.spent ?? 0 }
  })
  const dailyOpt = p.weekOptimal / 7
  const maxV = Math.max(...perDay.map((x) => x.spent), dailyOpt, 1) * 1.15
  const zc = ZONE_DARK[p.weekZone]
  const left = p.weekOptimal - p.weekSpent
  const f = Math.min(1, Math.max(0, p.weekSpent / Math.max(1, p.weekOptimal)))
  const mark = Math.min(1, Math.max(0, p.weekOptimalToDate / Math.max(1, p.weekOptimal)))
  return (
    <Sheet title="Gasto de la semana" onClose={onClose}>
      <div className="col" style={{ gap: 16 }}>
        <div className="row" style={{ alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
          <span className="num" style={{ fontSize: 36, fontWeight: 700, color: zc }}>{m.fmt(p.weekSpent)}</span>
          <span className="muted">de {m.fmt(p.weekOptimal)}</span>
        </div>
        <div className="hm-week-bar">
          <span className="fill" style={{ width: `${f * 100}%`, background: zc }} />
          <span className="mark" style={{ left: `calc(${mark * 100}% - 2px)` }} />
        </div>
        <div className="row small muted" style={{ gap: 8 }}>
          <span style={{ width: 4, height: 12, background: '#7C7566', flexShrink: 0 }} />
          Lo que tocaba hasta hoy: {m.fmt(p.weekOptimalToDate)}
        </div>
        <div className="hm-week-cols">
          {perDay.map((x, i) => {
            const future = x.e > todayE
            const c = future ? 'var(--chip)' : x.spent <= dailyOpt ? GREEN : x.spent <= dailyOpt * 1.5 ? YELLOW : RED
            const isToday = x.e === todayE
            return (
              <div key={i} className="hm-week-col">
                <div className="tiny muted" style={{ minHeight: 16 }}>{x.spent > 0 && !m.hidden ? Math.trunc(x.spent) : ''}</div>
                <div style={{ width: '100%', height: Math.max(4, 110 * (x.spent / maxV)), borderRadius: 8, background: c }} />
                <div className="small" style={{ fontWeight: isToday ? 700 : 400, color: isToday ? 'var(--ink)' : 'var(--muted)' }}>{WEEKDAYS_SHORT[i]}</div>
              </div>
            )
          })}
        </div>
        <div className="small muted" style={{ textAlign: 'center' }}>Ideal: ~{m.fmt(dailyOpt)} al día</div>
        <div className="hm-zone-box" style={{ background: `color-mix(in srgb, ${zc} 16%, transparent)`, color: zc }}>
          {left >= 0 ? `Margen de gasto: ${m.fmt(left)} hasta el domingo` : `Te has pasado ${m.fmt(-left)} esta semana`}
        </div>
        <SheetFooter onClose={onClose} />
      </div>
    </Sheet>
  )
}

/** Presión de gasto como un medidor: 1,0 = justo lo que tocaba hoy. */
function PressureVisualSheet({ p, onClose }: { p: Pulse; onClose: () => void }) {
  const m = useMoney()
  const z = p.pressureZone
  const zc = ZONE_DARK[z]
  const x = (v: number) => Math.min(1, Math.max(0, v / 2)) * 100
  const top = Math.max(p.spentToday, p.todayAllowance, 1)
  const rows: Array<[string, number]> = [['Hoy has gastado', p.spentToday], ['Hoy te toca', p.todayAllowance]]
  return (
    <Sheet title="Presión de gasto" onClose={onClose}>
      <div className="col" style={{ gap: 16 }}>
        <div className="row" style={{ alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
          <span className="num" style={{ fontSize: 48, fontWeight: 700, color: zc, lineHeight: 1 }}>{fixed(p.pressure, 1)}×</span>
          <span className="muted" style={{ fontSize: 20 }}>presión {p.pressureLabel.toLowerCase()}</span>
        </div>
        <div className="hm-gauge">
          <span style={{ left: 0, width: `${x(1)}%`, background: GREEN, borderRadius: '8px 0 0 8px' }} />
          <span style={{ left: `${x(1)}%`, width: `${x(1.3) - x(1)}%`, background: YELLOW }} />
          <span style={{ left: `${x(1.3)}%`, right: 0, background: RED, borderRadius: '0 8px 8px 0' }} />
          <i style={{ left: `calc(${x(p.pressure)}% - 4px)` }} />
        </div>
        <div className="row tiny muted" style={{ justifyContent: 'space-between' }}>
          <span>0</span><span>1,0 lo de hoy</span><span>2×</span>
        </div>
        {rows.map(([label, v], i) => (
          <div key={label}>
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <span className="small muted">{label}</span>
              <span className="num" style={{ fontWeight: 600 }}>{m.fmt(v)}</span>
            </div>
            <div style={{ marginTop: 4, height: 12, borderRadius: 8, width: `${Math.min(1, Math.max(0.02, v / top)) * 100}%`, background: i === 0 ? zc : '#CFC7B6' }} />
          </div>
        ))}
        <div className="hm-zone-box" style={{ background: `color-mix(in srgb, ${zc} 16%, transparent)`, color: zc }}>
          {z === 'GREEN' ? `Hoy aún puedes gastar ${m.fmt(Math.max(0, p.todayAllowance - p.spentToday))}` : `Hoy vas ${m.fmt(p.spentToday - p.todayAllowance)} por encima`}
        </div>
        <SheetFooter onClose={onClose} />
      </div>
    </Sheet>
  )
}

