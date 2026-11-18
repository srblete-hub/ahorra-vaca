// Objetivos (GoalsSection + GoalCard), edad financiera en pequeño y "Tu dinero trabajando"
// (SmartCards.kt, GoalAgeScreens.kt y NowWidgets.kt).
import { useId, useState } from 'react'
import type { Account } from '../../../data/models'
import { MONTHS } from '../../../data/dates'
import type { FinancialAge, GoalStatus } from '../../../domain'
import { InterestMath, isSaving } from '../../../domain'
import { fixed, fmtMonthShortYear } from '../../../domain/kt'
import { Button, ProgressLine, Sheet, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav } from '../../nav'
import { BrandCard, ORANGE, SAVINGS_COLOR, Tile, pct1 } from './shared'

const AMBER = '#FFB020'
const years = (v: number) => fixed(Math.abs(v), 1)

/** Varios objetivos por prioridad: fechas realistas y cómo acelerar uno retrasa otro. */
export function GoalsSection({ goals, swapText, onAdd, onEdit, onMove }: {
  goals: GoalStatus[]; swapText: string | null; onAdd: () => void; onEdit: (i: number) => void; onMove: (i: number, by: number) => void
}) {
  if (goals.length === 0) return <GoalCard g={null} onEdit={onAdd} />
  return (
    <div className="col">
      <div className="row">
        <div className="grow" style={{ fontSize: 20, fontWeight: 600 }}>{goals.length === 1 ? 'Tu objetivo' : 'Tus objetivos, por prioridad'}</div>
        <button className="link-btn row" style={{ gap: 4 }} onClick={onAdd}><Icon name="add" size={20} /> Añadir</button>
      </div>
      {goals.map((g, i) => (
        <div key={i} className="col">
          {goals.length > 1 && (
            <div className="row" style={{ paddingLeft: 8, gap: 8 }}>
              <div className="grow small muted" style={{ fontWeight: 500 }}>Prioridad {i + 1}</div>
              {i > 0 && <button className="icon-btn hm-glass" aria-label={`Subir prioridad de ${g.goal.name}`} onClick={() => onMove(i, -1)}><Icon name="chevronUp" /></button>}
              {i < goals.length - 1 && <button className="icon-btn hm-glass" aria-label={`Bajar prioridad de ${g.goal.name}`} onClick={() => onMove(i, 1)}><Icon name="chevronDown" /></button>}
            </div>
          )}
          <GoalCard g={g} onEdit={() => onEdit(i)} />
        </div>
      ))}
      {swapText && <div className="small muted" style={{ padding: '0 8px' }}>{swapText}</div>}
    </div>
  )
}

/** El objetivo: progreso, cuánto hace falta al mes, si llegas a tiempo y consejos. */
function GoalCard({ g, onEdit }: { g: GoalStatus | null; onEdit: () => void }) {
  const m = useMoney()
  if (!g) {
    return (
      <div className="card tight clickable" role="button" onClick={onEdit}>
        <div className="row" style={{ gap: 16 }}>
          <Tile icon="flag" color="var(--brand)" size={40} />
          <div className="grow">
            <div style={{ fontSize: 20, fontWeight: 600 }}>Ponte un objetivo</div>
            <div className="small muted">Ej.: 10.000 € ahorrados en 5 años. Te digo cuánto apartar y cómo llegar.</div>
          </div>
          <Icon name="chevronRight" style={{ color: 'var(--muted)' }} />
        </div>
      </div>
    )
  }
  const color = g.onTrack ? 'var(--gain)' : ORANGE
  const dl = g.goal.deadline
  return (
    <div className="card">
      <div className="row">
        <Tile icon="flag" color={color} size={40} />
        <div className="grow">
          <div className="ellipsis" style={{ fontSize: 20, fontWeight: 600 }}>{g.goal.name}</div>
          <div className="small muted">{m.fmt(g.goal.target)} · para {MONTHS[dl.month - 1]} de {dl.year}</div>
        </div>
        <button className="link-btn" style={{ fontSize: 14, padding: 8 }} onClick={onEdit}>Editar</button>
      </div>
      <div className="row" style={{ marginTop: 16, alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
        <span className="num" style={{ fontSize: 28, fontWeight: 600 }}>{m.fmt(g.current)}</span>
        <span className="muted grow">de {m.fmt(g.goal.target)}</span>
        <span style={{ fontWeight: 600, color }}>{Math.trunc(g.progress * 100)} %</span>
      </div>
      <div className="hm-goal-bar">
        <ProgressLine fraction={g.progress} color={color} height={12} />
        {[25, 50, 75].map((p) => <span key={p} style={{ left: `${p}%` }} />)}
      </div>
      <div className="hm-goal-stats">
        <Stat label="Necesitas" value={`${m.fmt(g.requiredMonthly)}/mes`} />
        <Stat label="Tu ritmo" value={`${m.fmt(g.pace)}/mes`} />
        <Stat label={g.onTrack ? 'Llegas' : 'Llegarías'} value={g.eta ? fmtMonthShortYear(g.eta) : '—'} color={color} />
      </div>
      <div className="hm-zone-box" style={{ marginTop: 12, background: `color-mix(in srgb, ${color} 12%, transparent)`, color: 'var(--ink)', textAlign: 'left', fontWeight: 500 }}>
        {g.done ? '¡Objetivo cumplido!'
          : g.onTrack ? 'Vas a tiempo' + (g.monthsAhead != null && g.monthsAhead > 0 ? `: ${g.monthsAhead} meses de adelanto.` : '.')
            : `Vas con retraso: a este ritmo tendrías ${m.fmt(g.projected)} en la fecha.`}
      </div>
      {g.tips.length > 0 && <div className="small" style={{ fontWeight: 600, marginTop: 8 }}>Consejos para llegar</div>}
      {g.tips.map((t) => (
        <div key={t} className="row small" style={{ gap: 8, alignItems: 'flex-start', marginTop: 8 }}>
          <Icon name="lightbulb" size={16} style={{ color: AMBER, flexShrink: 0, marginTop: 2 }} />
          <span>{t}</span>
        </div>
      ))}
    </div>
  )
}

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div style={{ minWidth: 0 }}>
      <div className="small muted">{label}</div>
      <div className="num ellipsis" style={{ fontWeight: 600, color }}>{value}</div>
    </div>
  )
}

/** Tarjeta pequeña de la edad financiera (más edad = mejor). */
export function FinancialAgeMini({ age, onClick }: { age: FinancialAge | null; onClick: () => void }) {
  const [info, setInfo] = useState(false)
  const status: [string, boolean] = !age ? ['', true] : age.difference >= 1 ? ['Buena', true] : age.difference > -1 ? ['Normal', true] : ['Mejorable', false]
  return (
    <div className="hm-age" role="button" onClick={onClick}>
      <div className="row">
        <div className="grow">
          <div className="hm-age-kicker">EDAD FINANCIERA</div>
          {age == null ? (
            <>
              <div style={{ fontSize: 20, fontWeight: 600 }}>¿Cuántos años tiene tu dinero?</div>
              <div className="small" style={{ opacity: 0.7 }}>Descúbrelo según tus hábitos y tu ahorro</div>
            </>
          ) : (
            <>
              <div className="num" style={{ fontSize: 32, fontWeight: 700 }}>{fixed(age.age, 1)}</div>
              <div className="small" style={{ color: age.difference >= 0 ? '#34E08A' : AMBER }}>
                {years(age.difference)} años {age.difference >= 0 ? 'por delante' : 'por detrás'} de tu edad
              </div>
            </>
          )}
        </div>
        {age ? <AgeOrb mature={age.difference >= 0} size={96} /> : <Icon name="chevronRight" />}
      </div>
      {age && (
        <div className="hm-age-status" onClick={(e) => e.stopPropagation()}>
          <span className="grow" style={{ fontWeight: 500 }}>Estado</span>
          <span className="hm-age-pill" style={{ color: status[1] ? '#2E6B3F' : '#C2415C' }}>{status[0]}</span>
          <button className="hm-info-btn" aria-label="Qué significa" onClick={() => setInfo(true)}><Icon name="info" /></button>
        </div>
      )}
      {info && age && (
        <div onClick={(e) => e.stopPropagation()}>
          <Sheet title="Tu edad financiera" onClose={() => setInfo(false)}>
            <div className="col" style={{ gap: 12 }}>
              <div>Mide lo maduras que están tus finanzas frente a tu edad real. Más edad es mejor: significa que ahorras, tienes colchón y no dependes de deudas.</div>
              <div className="small muted">Buena: más de 1 año por delante. Normal: en torno a tu edad. Mejorable: más de 1 año por detrás.</div>
              {age.factors.map((f) => (
                <div key={f.name} className="row small">
                  <span className="grow">{f.name}</span>
                  <span style={{ fontWeight: 600, color: f.years >= 0 ? 'var(--gain)' : 'var(--loss)' }}>{(f.years >= 0 ? '+' : '') + fixed(f.years, 1)} años</span>
                </div>
              ))}
              <Button onClick={() => setInfo(false)}>Entendido</Button>
            </div>
          </Sheet>
        </div>
      )}
    </div>
  )
}

/** Orbe luminoso con chispas: verde si tus finanzas van por delante, dorado si van por detrás. */
function AgeOrb({ mature, size }: { mature: boolean; size: number }) {
  const id = useId().replace(/:/g, '')
  const base = mature ? '#34E08A' : AMBER
  const sparks = Array.from({ length: 40 }, (_, i) => {
    const a = (((i * 73) % 100) / 100) * Math.PI * 2
    const d = (0.55 + 0.45 * (((i * 37 + 11) % 100) / 100)) * 40
    const s = ((i * 53) % 10) / 10
    return { x: 50 + Math.cos(a) * d, y: 50 + Math.sin(a) * d, r: 0.5 + 1.2 * s, o: 0.35 + 0.6 * s }
  })
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" style={{ flexShrink: 0 }}>
      <defs>
        <radialGradient id={`g${id}`}>
          <stop offset="0" stopColor="#000" /><stop offset="0.45" stopColor="#0E0A02" />
          <stop offset="0.8" stopColor={base} stopOpacity="0.55" /><stop offset="1" stopColor={base} />
        </radialGradient>
        <radialGradient id={`h${id}`}><stop offset="0.6" stopColor={base} stopOpacity="0.35" /><stop offset="1" stopColor={base} stopOpacity="0" /></radialGradient>
      </defs>
      <circle cx="50" cy="50" r="50" fill={`url(#h${id})`} />
      <g className="hm-orb-spin">
        <ellipse cx="50" cy="50" rx="44" ry="41" fill={`url(#g${id})`} />
        {sparks.map((p, i) => <circle key={i} cx={p.x} cy={p.y} r={p.r} fill="#FFF4D6" opacity={p.o} />)}
      </g>
    </svg>
  )
}

/** Cantidad redonda para los "¿sabías que…?": lo que te toca ahorrar al mes, o 200 € si aún no hay presupuesto. */
export function suggestedMonthly(income: number, savingsPct: number): number {
  const v = income * savingsPct / 100
  return v >= 10 ? Math.ceil(v / 10) * 10 : 200
}

/** "Tu dinero trabajando": lo que dan tus ahorros y cálculos con tus propios números (no son consejos). */
export function MoneyWorkingSection({ accounts, available, monthly }: { accounts: Account[]; available: number; monthly: number }) {
  const m = useMoney()
  const nav = useNav()
  const earning = accounts.filter((a) => a.interestRate > 0).sort((a, b) => b.balance * b.interestRate - a.balance * a.interestRate)
  const savingNoRate = accounts.filter((a) => isSaving(a) && a.interestRate <= 0)
  if (earning.length === 0 && savingNoRate.length === 0) return null
  const edit = (a: Account) => nav.edit({ name: 'account', account: a })
  const best = earning.length ? earning.reduce((x, y) => (y.interestRate > x.interestRate ? y : x)) : null
  const y1 = best ? InterestMath.project(0, best.interestRate, monthly, 12) : null
  const y5 = best ? InterestMath.project(best.balance, best.interestRate, monthly, 60) : null
  return (
    <div className="col" style={{ gap: 12 }}>
      {earning.map((a) => (
        <div key={a.id} className="card tight clickable" role="button" onClick={() => edit(a)}>
          <div className="row">
            <Tile icon="savings" color={SAVINGS_COLOR} />
            <div className="grow">
              <div className="ellipsis" style={{ fontWeight: 700 }}>{a.name}</div>
              <div className="small muted">{m.fmt(a.balance)} al {pct1(a.interestRate)} TAE</div>
            </div>
            <div className="right">
              <div className="num gain" style={{ fontSize: 18, fontWeight: 600 }}>+{m.fmt(a.balance * a.interestRate / 100)}</div>
              <div className="tiny muted">estimado en 1 año</div>
            </div>
          </div>
          <div className="small muted" style={{ marginTop: 8 }}>
            Si no lo tocas, ≈ {m.fmt(InterestMath.monthlyInterest(a.balance, a.interestRate))} al mes de intereses.{a.autoInterest ? ' Se suman solos cada mes.' : ''}
          </div>
        </div>
      ))}
      {best && y1 && y5 && (
        <BrandCard>
          <div className="row" style={{ gap: 8 }}>
            <Icon name="lightbulb" style={{ color: '#34C77B' }} />
            <span className="hm-overline" style={{ color: 'rgba(255,255,255,.7)' }}>¿SABÍAS QUE…?</span>
          </div>
          <div style={{ fontSize: 18, fontWeight: 600, marginTop: 8 }}>
            Si metes {m.fmt(monthly)} todos los meses en {best.name}, en un año ganarías <b style={{ color: '#34C77B', fontWeight: 800 }}>{m.fmt(y1.interest)}</b> solo de intereses.
          </div>
          <div className="small" style={{ marginTop: 8, color: 'rgba(255,255,255,.75)' }}>
            Y en 5 años, con lo que ya tienes ahí, llegarías a {m.fmt(y5.finalBalance)}: {m.fmt(y5.interest)} serían intereses y te daría {m.fmt(InterestMath.monthlyInterestAtEnd(y5, best.interestRate))} al mes.
          </div>
        </BrandCard>
      )}
      {best && available > 300 && (
        <div className="card tight">
          <div style={{ fontWeight: 700 }}>Dinero sin dar intereses</div>
          <div className="muted" style={{ marginTop: 4 }}>
            Tienes {m.fmt(available)} en cuentas que no dan intereses. Al {pct1(best.interestRate)} de {best.name} serían +{m.fmt(available * best.interestRate / 100)} al año.
          </div>
        </div>
      )}
      {earning.length === 0 && (
        <div className="card tight clickable" role="button" onClick={() => edit(savingNoRate[0])}>
          <div className="row">
            <Tile icon="lightbulb" color="var(--debt)" />
            <div className="grow">
              <div style={{ fontWeight: 700 }}>¿Tu ahorro da intereses?</div>
              <div className="small muted">Abre "{savingNoRate[0].name}", marca "Da intereses" y pon su TAE: verás cuánto te paga al mes y al año.</div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
