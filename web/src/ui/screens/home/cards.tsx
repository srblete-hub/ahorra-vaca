// Tarjetas de avisos de Inicio: límites, recibos, suscripciones, lectura semanal, anomalías, recibos por semana y tendencias
// (HomeScreen.kt, MoreCards.kt, StoryCards.kt, SmartCards.kt, SignalCards.kt y FriendsUi.kt).
import { useRef, useState } from 'react'
import type { IncomeSource } from '../../../data/models'
import type { LocalDate } from '../../../data/dates'
import { MONTHS_SHORT } from '../../../data/dates'
import type { Anomaly, BillCalendar, LimitAlert, SpendTag, SubscriptionCheck, Trend, UpcomingBill, WeeklyRead } from '../../../domain'
import { upcomingWhenText } from '../../../domain'
import { Button, Sheet, SERIES_COLORS, useMoney } from '../../components'
import { Icon } from '../../icons'
import { capFirst } from '../../../domain/kt'
import { MerchantLogo, ORANGE, PillButton, Tile } from './shared'

/** Aviso de límite (los mismos que llegan como notificación en Android). */
export function LimitAlertCard({ a }: { a: LimitAlert }) {
  const tone = a.severe ? 'var(--loss)' : ORANGE
  return (
    <div className="hm-alert" style={{ background: `color-mix(in srgb, ${tone} 12%, var(--card))` }}>
      <div style={{ fontSize: 16, fontWeight: 600, color: a.severe ? 'var(--loss)' : 'var(--ink)' }}>{a.title}</div>
      <div className="small">{a.text}</div>
    </div>
  )
}

/** "Próximos recibos": lo que te van a cobrar en los próximos días y si el banco lo cubre. */
export function UpcomingBillsCard({ bills, bank, today }: { bills: UpcomingBill[]; bank: number; today: LocalDate }) {
  const m = useMoney()
  const total = bills.reduce((a, b) => a + b.amount, 0)
  const ok = bank >= total
  const c = ok ? '#2E6B3F' : '#C2415C'
  return (
    <div className="card tight col">
      <div className="row">
        <div className="grow" style={{ fontWeight: 600 }}>Próximos recibos</div>
        <span className="hm-status" style={{ color: ok ? 'var(--gain)' : 'var(--loss)', background: `color-mix(in srgb, ${c} 12%, transparent)` }}>
          {ok ? 'Cubierto' : `Falta ${m.fmt(total - bank)}`}
        </span>
      </div>
      {bills.map((b, i) => (
        <div key={i} className="row" style={{ gap: 16 }}>
          <MerchantLogo note={b.name} tag="HOME" size={40} />
          <div className="grow">
            <div className="ellipsis" style={{ fontWeight: 500 }}>{b.name}</div>
            <div className="small muted">{capFirst(upcomingWhenText(b, today))}</div>
          </div>
          <div className="num" style={{ fontWeight: 600 }}>−{m.fmt(b.amount)}</div>
        </div>
      ))}
    </div>
  )
}

/** Tarjeta "Revisión de suscripciones", con los logos apilados y lo que podrías ahorrar. */
export function SubscriptionCheckCard({ c, pending, onOpen }: { c: SubscriptionCheck; pending: number; onOpen: () => void }) {
  const m = useMoney()
  return (
    <button className="hm-big-card" onClick={onOpen}>
      <div className="row" style={{ gap: 16 }}>
        <div className="hm-round48"><Icon name="premium" style={{ color: 'var(--brand)' }} /></div>
        <div className="grow" style={{ textAlign: 'left' }}>
          <div style={{ fontSize: 18, fontWeight: 500 }}>Revisión de suscripciones</div>
          <div className="small muted">Repasa tus planes antes de que se renueven</div>
        </div>
      </div>
      <div className="row" style={{ marginTop: 24, alignItems: 'flex-end' }}>
        <div className="grow" style={{ textAlign: 'left' }}>
          <div style={{ fontWeight: 600 }}>{pending === 0 ? 'Todo revisado' : `${pending} ${pending === 1 ? 'suscripción necesita' : 'suscripciones necesitan'} atención`}</div>
          {c.maxSaving >= 1 && (
            <div className="small muted">Podrías ahorrar hasta <b style={{ color: 'var(--brand)' }}>{m.fmt(c.maxSaving)}/mes</b></div>
          )}
        </div>
        <div className="hm-stack">
          {c.subs.slice(0, 3).map((s, i) => (
            <div key={i} className="hm-stack-item" style={{ left: i * 24, top: i === 1 ? 0 : 12 }}>
              <MerchantLogo note={s.name} tag="SUBSCRIPTIONS" size={36} />
            </div>
          ))}
          {c.subs.length > 3 && <div className="hm-stack-more">+{c.subs.length - 3}</div>}
        </div>
      </div>
    </button>
  )
}

/** Detalle: progreso de la revisión, salud del ahorro, acciones a marcar y estado/prioridad. */
export function SubscriptionSheet({ c, reviewed, savingsHealth, onToggle, onClose }: { c: SubscriptionCheck; reviewed: string[]; savingsHealth: number; onToggle: (key: string) => void; onClose: () => void }) {
  const m = useMoney()
  const done = c.actions.filter((a) => reviewed.includes(a.key)).length
  const all = c.actions.length
  const urgent = c.actions.some((a) => a.key.startsWith('up-') && !reviewed.includes(a.key))
  return (
    <Sheet title="Tus suscripciones" onClose={onClose}>
      <div className="col" style={{ gap: 16 }}>
        <div className="row" style={{ gap: 16, alignItems: 'flex-start' }}>
          <Segments label={`${done} de ${all} revisadas`} frac={all === 0 ? 1 : done / all} color="var(--brand)" />
          <Segments label="Salud del ahorro" frac={savingsHealth / 100} color="#C2415C" />
        </div>
        <div className="hm-sub-list">
          {all === 0 && <div className="muted" style={{ padding: 16 }}>No veo nada que revisar ahora mismo.</div>}
          {c.actions.map((a) => {
            const ok = reviewed.includes(a.key)
            return (
              <button key={a.key} className="hm-sub-row" onClick={() => onToggle(a.key)} aria-pressed={ok}>
                <MerchantLogo note={a.name} tag="SUBSCRIPTIONS" size={40} />
                <span className="grow" style={{ textAlign: 'left', color: ok ? 'var(--muted)' : 'var(--ink)' }}>{a.text}</span>
                <Icon name={ok ? 'checkCircle' : 'radioUnchecked'} style={{ color: ok ? 'var(--gain)' : 'var(--muted)', flexShrink: 0 }} />
              </button>
            )
          })}
        </div>
        <StatusLine label="Estado" value={done === all ? 'Hecho' : done === 0 ? 'Pendiente' : 'En curso'} color={done === all ? '#2E6B3F' : '#B7791F'} />
        <StatusLine label="Prioridad" value={urgent ? 'Urgente' : done < all ? 'Media' : 'Baja'} color={urgent ? '#C2415C' : 'var(--muted)'} />
        <div className="tiny muted">Pagas {m.fmt(c.monthlyTotal)} al mes en {c.subs.length} suscripciones. Toca una acción para marcarla como revisada.</div>
        <Button onClick={onClose}>Hecho</Button>
      </div>
    </Sheet>
  )
}

function Segments({ label, frac, color }: { label: string; frac: number; color: string }) {
  const n = 24
  const on = Math.trunc(frac * n)
  return (
    <div className="grow">
      <div className="row" style={{ gap: 4 }}>
        <span className="grow small muted ellipsis">{label}</span>
        <span className="small" style={{ fontWeight: 600 }}>{Math.trunc(frac * 100)} %</span>
      </div>
      <div className="hm-segments">{Array.from({ length: n }, (_, i) => <span key={i} style={{ background: i < on ? color : 'var(--chip)' }} />)}</div>
    </div>
  )
}

function StatusLine({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="row">
      <span className="grow muted">{label}</span>
      <span className="hm-status" style={{ color, background: `color-mix(in srgb, ${color} 14%, transparent)`, padding: '8px 16px', fontSize: 14 }}>{value}</span>
    </div>
  )
}

/** "Lectura semanal": tarjetas deslizables con en qué se te fue el dinero esta semana. */
export function WeeklyReadCard({ r, onLimit, onDetails }: { r: WeeklyRead; onLimit: (t: SpendTag) => void; onDetails: (t: SpendTag) => void }) {
  const [page, setPage] = useState(0)
  const scroller = useRef<HTMLDivElement>(null)
  const range = `${r.from.day}–${r.to.day} ${MONTHS_SHORT[r.to.month - 1]}`.toLocaleUpperCase('es')
  const onScroll = () => {
    const el = scroller.current
    if (!el || !el.firstElementChild) return
    const w = (el.firstElementChild as HTMLElement).offsetWidth + 16
    setPage(Math.round(el.scrollLeft / w))
  }
  return (
    <div className="hm-weekly">
      <div className="hm-weekly-kicker">LECTURA SEMANAL · {range}</div>
      <div className="hm-serif hm-weekly-title">En qué se te fue el dinero sin darte cuenta</div>
      <div className="hm-weekly-pager" ref={scroller} onScroll={onScroll}>
        {r.insights.map((ins, i) => (
          <div key={ins.tag} className="hm-weekly-page">
            <div className={`hm-weekly-num ${ins.up ? 'up' : ''}`}>{i + 1}</div>
            <div className="hm-serif" style={{ fontSize: 22, lineHeight: '28px', marginTop: 16, color: '#17150F' }}>{ins.title}</div>
            <div style={{ fontSize: 14, lineHeight: '20px', color: '#7C7566', marginTop: 8 }}>{ins.detail}</div>
            <div className="col" style={{ marginTop: 16 }}>
              {ins.checks.map((c) => (
                <div key={c} className="row" style={{ gap: 8, fontSize: 14, color: '#17150F' }}><Icon name="check" size={16} />{c}</div>
              ))}
            </div>
            <div className="row" style={{ gap: 8, marginTop: 24 }}>
              {ins.up && <PillButton dark onClick={() => onLimit(ins.tag)}>Poner límite</PillButton>}
              <PillButton onClick={() => onDetails(ins.tag)}>Detalles</PillButton>
            </div>
          </div>
        ))}
      </div>
      {r.insights.length > 1 && (
        <div className="row" style={{ gap: 8, justifyContent: 'center', marginTop: 16 }} aria-hidden="true">
          {r.insights.map((_, i) => <span key={i} className={`hm-dotpage ${i === page ? 'on' : ''}`} />)}
        </div>
      )}
    </div>
  )
}

function Header({ icon, color, title, sub }: { icon: string; color: string; title: string; sub: string }) {
  return (
    <div className="row">
      <Tile icon={icon} color={color} size={40} />
      <div className="grow">
        <div style={{ fontSize: 20, fontWeight: 600 }}>{title}</div>
        <div className="small muted">{sub}</div>
      </div>
    </div>
  )
}

/** Cambios respecto a tu forma normal de gastar. */
export function AnomaliesCard({ list, onOpen }: { list: Anomaly[]; onOpen: (a: Anomaly) => void }) {
  return (
    <div className="card">
      <Header icon="warning" color={ORANGE} title="Algo distinto a lo normal" sub="Comparado contigo mismo, no con otros" />
      {list.length === 0 && <div className="small muted" style={{ marginTop: 8 }}>Todo dentro de lo habitual este mes.</div>}
      {list.map((a, i) => (
        <div key={i} className={`hm-anomaly ${a.expense ? 'clickable' : ''}`} onClick={a.expense ? () => onOpen(a) : undefined} role={a.expense ? 'button' : undefined}>
          <span className="hm-dot" style={{ background: a.severe ? 'var(--loss)' : ORANGE, marginTop: 8 }} />
          <div>
            <div style={{ fontWeight: 500 }}>{a.title}</div>
            <div className="small muted">{a.detail}</div>
          </div>
        </div>
      ))}
    </div>
  )
}

/** Recibos de las próximas semanas y cómo aliviar las semanas apretadas. */
export function BillCalendarCard({ c }: { c: BillCalendar }) {
  const m = useMoney()
  const max = Math.max(0, ...c.weeks.map((w) => w[1])) || 1
  return (
    <div className="card">
      <Header icon="calendar" color={c.crunch ? ORANGE : SERIES_COLORS[1]} title="Tus recibos, semana a semana" sub={c.crunch ? 'Hay semanas apretadas' : 'Bien repartidos'} />
      <div className="hm-billweeks">
        {c.weeks.map(([label, v], i) => (
          <div key={i} className="hm-billweek">
            {v > 0 && <div className="tiny muted ellipsis">{m.fmt(v).split(',')[0]}</div>}
            <div style={{ width: '100%', height: `${Math.min(1, Math.max(0.04, v / max)) * 70}%`, borderRadius: 8, background: v / max > 0.6 && c.crunch ? ORANGE : SERIES_COLORS[1] }} />
            <div className="tiny muted ellipsis" style={{ maxWidth: '100%' }}>{label.split('–')[0]}</div>
          </div>
        ))}
      </div>
      {c.beforePayday.length > 0 && c.payday && (
        <div className="small muted" style={{ marginTop: 8 }}>Antes de cobrar ({c.payday.day}): {c.beforePayday.map((b) => `${b[0]} ${m.fmt(b[1])}`).join(', ')}</div>
      )}
      {c.suggestions.map((s) => <div key={s} className="small" style={{ marginTop: 8 }}>• {s}</div>)}
      {c.crunch && <div className="tiny muted" style={{ marginTop: 8 }}>No cambiamos nada por ti: son sugerencias para que lo pidas tú si te conviene.</div>}
    </div>
  )
}

/** Cambios que se mantienen en el tiempo, con lo que los explica. */
export function TrendsCard({ list }: { list: Trend[] }) {
  return (
    <div className="card">
      <Header icon="line" color={ORANGE} title="Cambios en tu economía" sub="Tendencias que se mantienen, no días sueltos" />
      {list.length === 0 && <div className="small muted" style={{ marginTop: 8 }}>No veo cambios persistentes: tu forma de gastar y cobrar se mantiene.</div>}
      {list.map((t) => (
        <div key={t.title} style={{ marginTop: 12 }}>
          <div style={{ fontWeight: 500 }}>{t.title}</div>
          <div className="small muted">{t.explanation}</div>
        </div>
      ))}
    </div>
  )
}

/** "¿Ya sabes cuánto te paga X este mes?" para los ingresos variables. */
export function ExpectedIncomeNudge({ source, onSet }: { source: IncomeSource; onSet: () => void }) {
  return (
    <div className="card">
      <div className="row">
        <Tile icon="bolt" color={SERIES_COLORS[2]} />
        <div className="grow">
          <div style={{ fontWeight: 600 }}>¿Ya sabes cuánto te paga {source.name} este mes?</div>
          <div className="small muted">Cobras el día {source.dayFrom}. Ponlo y el consejo dejará de estimar.</div>
        </div>
      </div>
      <Button onClick={onSet} style={{ marginTop: 8 }}>Poner importe</Button>
    </div>
  )
}
