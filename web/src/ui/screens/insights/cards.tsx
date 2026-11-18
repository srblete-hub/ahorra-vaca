// Tarjetas del análisis (SignalCards.kt, SmartCards.kt → AnomaliesCard, GoalAgeScreens.kt → FinancialAgeMini, HomeScreen.kt → ShortcutCard).
import { useState, type ReactNode } from 'react'
import type { Anomaly, BillCalendar, FinancialAge, PaycheckSplit, Recoverable, Recurring, Trend } from '../../../domain'
import { Card, IconTile, SERIES_COLORS, Sheet, Button, StackedBar, useMoney } from '../../components'
import { Icon } from '../../icons'
import './insights.css'

export const ORANGE = '#FF9F0A'
const AMBER = '#FFB020'
const AGE_GREEN = '#34E08A'

export function Head({ icon, color, title, sub }: { icon: string; color: string; title: string; sub: string }) {
  return (
    <div className="in-head">
      <IconTile icon={icon} color={color} size={40} />
      <div className="grow"><div className="t">{title}</div><div className="s">{sub}</div></div>
    </div>
  )
}
const Muted = ({ children, top = 8 }: { children: ReactNode; top?: number }) => <div className="small muted" style={{ marginTop: top }}>{children}</div>

/** Suscripciones, recibos, cuotas y comisiones que se repiten, con su coste al año. */
export function RecurringCard({ list }: { list: Recurring[] }) {
  const m = useMoney()
  const [all, setAll] = useState(false)
  return (
    <Card>
      <Head icon="repeat" color={SERIES_COLORS[3]} title="Gastos recurrentes" sub="Lo que se repite cada mes" />
      {list.length === 0 ? <Muted>Aún no veo pagos que se repitan cada mes.</Muted> : <>
        <div className="in-box" style={{ background: `color-mix(in srgb, ${SERIES_COLORS[3]} 10%, transparent)` }}>
          {m.fmt(list.reduce((a, r) => a + r.monthly, 0))} al mes · {m.fmt(list.reduce((a, r) => a + r.annual, 0))} al año
        </div>
        <div style={{ marginTop: 4 }}>
          {list.slice(0, all ? 30 : 6).map((r, i) => (
            <div key={r.name + i} className="in-line sep" style={{ alignItems: 'center' }}>
              <div className="grow">
                <div className="t ellipsis">{r.name}</div>
                <div className="s" style={{ color: r.change >= 1 ? ORANGE : undefined }}>
                  {r.kind + (!r.fromBills ? ` · ${r.times} pagos` : '') + (r.change >= 1 ? ` · ha subido ${m.fmt(r.change)}` : r.change <= -1 ? ` · ha bajado ${m.fmt(-r.change)}` : '')}
                </div>
              </div>
              <div className="r">
                <div className="num" style={{ fontWeight: 500 }}>{m.fmt(r.monthly)}/mes</div>
                <div className="tiny muted num">{m.fmt(r.annual)}/año</div>
              </div>
            </div>
          ))}
        </div>
        {list.length > 6 && <button className="in-link" onClick={() => setAll(!all)}>{all ? 'Ver menos' : `Ver los ${list.length}`}</button>}
      </>}
    </Card>
  )
}

/** Dinero que quizá puedas recuperar: importe, evidencia y siguiente paso. */
export function RecoverableCard({ list, onOpen }: { list: Recoverable[]; onOpen: (r: Recoverable) => void }) {
  const m = useMoney()
  return (
    <Card>
      <Head icon="replay" color="var(--gain)" title="Dinero recuperable" sub="Situaciones concretas para revisar" />
      {list.length === 0 ? <Muted>Nada que reclamar ni revisar ahora mismo.</Muted> : <>
        <div className="in-box" style={{ background: 'color-mix(in srgb, var(--gain) 10%, transparent)' }}>Hasta {m.fmt(list.reduce((a, r) => a + r.amount, 0))} en juego</div>
        {list.map((r, i) => {
          const body = (
            <>
              <div className="row" style={{ alignItems: 'flex-start', gap: 8 }}>
                <div className="grow" style={{ fontWeight: 500 }}>{r.title}</div>
                <div className="num gain" style={{ fontWeight: 600, flexShrink: 0 }}>{m.fmt(r.amount)}</div>
              </div>
              <div className="small muted">{r.evidence}</div>
              <div className="small" style={{ marginTop: 2 }}>→ {r.nextStep}</div>
            </>
          )
          return (
            <div key={i} style={{ borderTop: i > 0 ? '1px solid var(--line)' : undefined, padding: '8px 0' }}>
              {r.expense ? <button className="in-click" onClick={() => onOpen(r)}>{body}</button> : body}
            </div>
          )
        })}
        <div className="tiny muted" style={{ marginTop: 8 }}>Que algo sea poco habitual no significa que esté mal cobrado: compruébalo antes de reclamar.</div>
      </>}
    </Card>
  )
}

/** Recibos de las próximas semanas y cómo aliviar las semanas apretadas. */
export function BillCalendarCard({ c }: { c: BillCalendar }) {
  const m = useMoney()
  const max = Math.max(...c.weeks.map((w) => w[1]), 0) || 1
  return (
    <Card>
      <Head icon="calendar" color={c.crunch ? ORANGE : SERIES_COLORS[1]} title="Tus recibos, semana a semana" sub={c.crunch ? 'Hay semanas apretadas' : 'Bien repartidos'} />
      <div className="in-bars" style={{ height: 88, marginTop: 12 }}>
        {c.weeks.map(([label, v], i) => (
          <div key={i}>
            {v > 0 && <span className="num">{m.fmt(v).split(',')[0]}</span>}
            <i style={{ height: `${Math.min(1, Math.max(0.04, v / max)) * 70}%`, background: v / max > 0.6 && c.crunch ? ORANGE : SERIES_COLORS[1] }} aria-label={label} />
          </div>
        ))}
      </div>
      <div className="row" style={{ gap: 8, marginTop: 4 }}>
        {c.weeks.map(([label], i) => <span key={i} className="tiny muted ellipsis" style={{ flex: 1, minWidth: 0, fontSize: 11 }}>{label.split('–')[0]}</span>)}
      </div>
      {c.beforePayday.length > 0 && c.payday && (
        <Muted>Antes de cobrar ({c.payday.day}): {c.beforePayday.map(([n, v]) => `${n} ${m.fmt(v)}`).join(', ')}</Muted>
      )}
      {c.suggestions.map((s, i) => <div key={i} className="small" style={{ marginTop: 8 }}>• {s}</div>)}
      {c.crunch && <div className="tiny muted" style={{ marginTop: 8 }}>No cambiamos nada por ti: son sugerencias para que lo pidas tú si te conviene.</div>}
    </Card>
  )
}

/** Cómo repartir la próxima nómina, con colchón y objetivos ajustables. */
export function PaycheckSplitCard({ s }: { s: PaycheckSplit }) {
  const m = useMoney()
  const colors = ['#5E5CE6', SERIES_COLORS[0], SERIES_COLORS[1], SERIES_COLORS[3], 'var(--gain)']
  const fixedPart = s.parts[0][1] + s.parts[1][1]
  const room = Math.max(0, s.income - fixedPart)
  const [cushion, setCushion] = useState(s.parts[2][1])
  const [goals, setGoals] = useState(s.parts[3][1])
  const [showBills, setShowBills] = useState(false)
  const free = s.income - fixedPart - cushion - goals
  const parts: Array<[string, number]> = [[s.parts[0][0], s.parts[0][1]], [s.parts[1][0], s.parts[1][1]], [s.parts[2][0], cushion], [s.parts[3][0], goals], ['Dinero libre', free]]
  const step = (v: number, cap: number) => Math.min(Math.round(v / 5) * 5, Math.max(0, cap))
  const maxRange = Math.max(1, room)
  return (
    <Card>
      <Head icon="payments" color="var(--gain)" title="Reparte tu nómina" sub={`${s.label} · ${m.fmt(s.income)}`} />
      <Muted>Cuando te llegue, así te propongo repartirla: primero lo que no puedes evitar (los recibos que salen antes de la siguiente nómina y lo básico para vivir), luego tu colchón y tus objetivos. Lo que queda es dinero libre para lo que quieras. Mueve las barras para probar otros repartos.</Muted>
      <div style={{ margin: '12px 0 8px' }}>
        <StackedBar slices={parts.map(([n, v], i) => ({ label: n, value: Math.max(0, v), color: colors[i % colors.length] }))} />
      </div>
      {parts.map(([n, v], i) => (
        <div key={i}>
          <div className={`row ${i === 0 && s.bills.length ? 'in-click' : ''}`} style={{ gap: 8, padding: '4px 0' }} onClick={i === 0 && s.bills.length ? () => setShowBills(!showBills) : undefined}>
            <span className="in-dot" style={{ marginTop: 0, background: colors[i % colors.length] }} />
            <span className="grow">{n}{i === 0 && s.bills.length > 0 ? (showBills ? ' ▴' : ' ▾') : ''}</span>
            <span className="num" style={{ fontWeight: 500, color: v < 0 ? 'var(--loss)' : undefined }}>{m.fmt(v)}</span>
          </div>
          {i === 0 && showBills && s.bills.map(([bn, bv], j) => (
            <div key={j} className="row small muted" style={{ paddingLeft: 24, gap: 8 }}><span className="grow">{bn}</span><span className="num">{m.fmt(bv)}</span></div>
          ))}
          {i === 2 && <input type="range" aria-label={n} min={0} max={maxRange} step={1} value={cushion} style={{ accentColor: colors[2] }} onChange={(e) => setCushion(step(+e.target.value, room - goals))} />}
          {i === 3 && <input type="range" aria-label={n} min={0} max={maxRange} step={1} value={goals} style={{ accentColor: colors[3] }} onChange={(e) => setGoals(step(+e.target.value, room - cushion))} />}
        </div>
      ))}
      <div className="small" style={{ marginTop: 8, fontWeight: 500, color: free < 0 ? 'var(--loss)' : 'var(--gain)' }}>
        {free < 0 ? 'Así no te llega: baja el colchón o los objetivos.' : `Te quedan ${m.fmt(free)} libres: unos ${m.fmt(free / 30)} al día hasta la siguiente nómina.`}
      </div>
      {s.notes.map((n, i) => <div key={i} className="small muted" style={{ marginTop: 4 }}>• {n}</div>)}
      <div className="tiny muted" style={{ marginTop: 8 }}>Es una propuesta: tú decides qué mover y cuándo.</div>
    </Card>
  )
}

/** Cambios que se mantienen en el tiempo, con lo que los explica. */
export function TrendsCard({ list }: { list: Trend[] }) {
  return (
    <Card>
      <Head icon="line" color={ORANGE} title="Cambios en tu economía" sub="Tendencias que se mantienen, no días sueltos" />
      {list.length === 0 && <Muted>No veo cambios persistentes: tu forma de gastar y cobrar se mantiene.</Muted>}
      {list.map((t, i) => (
        <div key={i} style={{ marginTop: 12 }}>
          <div style={{ fontWeight: 500 }}>{t.title}</div>
          <div className="small muted">{t.explanation}</div>
        </div>
      ))}
    </Card>
  )
}

/** Cambios respecto a tu forma normal de gastar. */
export function AnomaliesCard({ list, onOpen }: { list: Anomaly[]; onOpen: (a: Anomaly) => void }) {
  return (
    <Card>
      <Head icon="warning" color={ORANGE} title="Algo distinto a lo normal" sub="Comparado contigo mismo, no con otros" />
      {list.length === 0 && <Muted>Todo dentro de lo habitual este mes.</Muted>}
      {list.map((a, i) => {
        const body = (
          <div className="row" style={{ alignItems: 'flex-start', gap: 8 }}>
            <span className="in-dot" style={{ background: a.severe ? 'var(--loss)' : ORANGE }} />
            <div className="grow"><div style={{ fontWeight: 500 }}>{a.title}</div><div className="small muted">{a.detail}</div></div>
          </div>
        )
        return <div key={i} style={{ marginTop: 12 }}>{a.expense ? <button className="in-click" onClick={() => onOpen(a)}>{body}</button> : body}</div>
      })}
    </Card>
  )
}

export function ShortcutCard({ title, sub, icon, color, onClick }: { title: string; sub: string; icon: string; color: string; onClick: () => void }) {
  return (
    <Card className="tight" onClick={onClick}>
      <IconTile icon={icon} color={color} size={40} />
      <div style={{ fontWeight: 600, marginTop: 8 }} className="ellipsis">{title}</div>
      <div className="small muted">{sub}</div>
    </Card>
  )
}

const years = (v: number) => Math.abs(v).toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** Orbe de la edad financiera (versión estática del AgeOrb animado). */
function AgeOrb({ age, size }: { age: FinancialAge; size: number }) {
  const base = age.difference >= 0 ? AGE_GREEN : AMBER
  return (
    <div className="in-orb" aria-hidden="true" style={{
      width: size, height: size,
      background: `radial-gradient(circle at 50% 50%, #000 0%, #0E0A02 35%, color-mix(in srgb, ${base} 55%, transparent) 75%, ${base} 100%)`,
      boxShadow: `0 0 ${size / 4}px color-mix(in srgb, ${base} 35%, transparent)`,
    }} />
  )
}

/** Tarjeta pequeña de la edad financiera. */
export function FinancialAgeMini({ age, onClick }: { age: FinancialAge | null; onClick: () => void }) {
  const [info, setInfo] = useState(false)
  const [status, good] = !age ? ['', true] : age.difference >= 1 ? ['Buena', true] : age.difference > -1 ? ['Normal', true] : ['Mejorable', false]
  return (
    <>
      <Card className="tight in-age" onClick={onClick}>
        <div className="row">
          <div className="grow">
            <div style={{ fontSize: 12, letterSpacing: '0.12em', color: 'rgba(255,255,255,0.7)' }}>EDAD FINANCIERA</div>
            {!age ? <>
              <div style={{ fontSize: 20, fontWeight: 600 }}>¿Cuántos años tiene tu dinero?</div>
              <div className="small muted">Descúbrelo según tus hábitos y tu ahorro</div>
            </> : <>
              <div className="num" style={{ fontSize: 32, fontWeight: 700 }}>{age.age.toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}</div>
              <div className="small" style={{ color: age.difference >= 0 ? AGE_GREEN : AMBER }}>
                {years(age.difference)} años {age.difference >= 0 ? 'por delante' : 'por detrás'} de tu edad
              </div>
            </>}
          </div>
          {age ? <AgeOrb age={age} size={96} /> : <Icon name="chevronRight" />}
        </div>
        {age && (
          <div className="in-status" onClick={(e) => e.stopPropagation()}>
            <span className="grow" style={{ fontWeight: 500 }}>Estado</span>
            <span className="pill-w" style={{ color: good ? '#2E6B3F' : ORANGE }}>{status}</span>
            <button aria-label="Qué significa" onClick={() => setInfo(true)} style={{ color: 'rgba(255,255,255,0.7)', display: 'grid' }}><Icon name="info" /></button>
          </div>
        )}
      </Card>
      {info && age && (
        <Sheet title="Tu edad financiera" onClose={() => setInfo(false)}>
          <div className="form">
            <div>Mide lo maduras que están tus finanzas frente a tu edad real. Más edad es mejor: significa que ahorras, tienes colchón y no dependes de deudas.</div>
            <div className="small muted">Buena: más de 1 año por delante. Normal: en torno a tu edad. Mejorable: más de 1 año por detrás.</div>
            {age.factors.map((f, i) => (
              <div key={i} className="row small" style={{ gap: 8 }}>
                <span className="grow">{f.name}</span>
                <span style={{ fontWeight: 600, color: f.years >= 0 ? 'var(--gain)' : 'var(--loss)' }}>{(f.years >= 0 ? '+' : '') + f.years.toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} años</span>
              </div>
            ))}
            <Button onClick={() => setInfo(false)}>Entendido</Button>
          </div>
        </Sheet>
      )}
    </>
  )
}
