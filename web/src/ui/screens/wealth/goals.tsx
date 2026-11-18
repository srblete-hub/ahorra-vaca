// Port de GoalAgeScreens.kt: tarjeta del objetivo, orbe y pantalla de la edad financiera.
// (GoalSheet y AgeSheet viven en ui/sheets: se abren con nav.edit({name:'goal'}) / nav.edit({name:'ageForm'}).)
import { useEffect, useRef, useState } from 'react'
import { MONTHS, MONTHS_SHORT, dayOfWeek, today as todayDate } from '../../../data/dates'
import type { FinancialAge, GoalStatus } from '../../../domain'
import { usePlanning } from '../../../domain'
import { Button, IconTile, Overlay, SERIES_COLORS, Sheet, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav } from '../../nav'
import { dec1 } from './common'

const AMBER = '#FFB020'
const MINT = '#34E08A'
const years = (v: number) => dec1(Math.abs(v))

// ---------- Objetivo ----------

/** El objetivo: progreso, cuánto hace falta al mes, si llegas a tiempo, consejos y un botón para ajustar el reparto. */
export function GoalCard({ g, onEdit, onApplyPct }: { g: GoalStatus | null; onEdit: () => void; onApplyPct?: (pct: number) => void }) {
  const m = useMoney()
  if (g == null) {
    return (
      <div className="card clickable tight" role="button" onClick={onEdit}>
        <div className="row" style={{ gap: 16 }}>
          <IconTile icon="flag" color={SERIES_COLORS[0]} size={40} />
          <div className="grow">
            <div className="w-h20">Ponte un objetivo</div>
            <div className="small muted">Ej.: 10.000 € ahorrados en 5 años. Te digo cuánto apartar y cómo llegar.</div>
          </div>
          <Icon name="chevronRight" style={{ color: 'var(--muted)' }} />
        </div>
      </div>
    )
  }
  const color = g.onTrack ? 'var(--gain)' : '#FF9F0A'
  const dl = g.goal.deadline
  return (
    <div className="card">
      <div className="row">
        <IconTile icon="flag" color={color} size={40} />
        <div className="grow">
          <div className="w-h20 ellipsis">{g.goal.name}</div>
          <div className="small muted">{m.fmt(g.goal.target)} · para {MONTHS[dl.month - 1]} de {dl.year}</div>
        </div>
        <button className="w-text-btn" onClick={onEdit}>Editar</button>
      </div>
      <div className="row" style={{ alignItems: 'flex-end', gap: 0, marginTop: 16, flexWrap: 'wrap' }}>
        <span className="num" style={{ fontSize: 28, fontWeight: 600 }}>{m.fmt(g.current)}</span>
        <span className="muted" style={{ paddingBottom: 4 }}>&nbsp; de {m.fmt(g.goal.target)}</span>
        <span className="grow" />
        <span style={{ fontWeight: 600, color, paddingBottom: 4 }}>{Math.trunc(g.progress * 100)} %</span>
      </div>
      {/* Hitos del 25, 50 y 75 %. */}
      <div className="w-goal-bar" style={{ marginTop: 8 }}>
        <span style={{ width: `${g.progress * 100}%`, background: color }} />
        <i style={{ left: '25%' }} /><i style={{ left: '50%' }} /><i style={{ left: '75%' }} />
      </div>
      <div className="row" style={{ marginTop: 16, alignItems: 'flex-start', gap: 8 }}>
        <GoalStat label="Necesitas" value={m.fmt(g.requiredMonthly) + '/mes'} />
        <GoalStat label="Tu ritmo" value={m.fmt(g.pace) + '/mes'} />
        <GoalStat label={g.onTrack ? 'Llegas' : 'Llegarías'} value={g.eta ? `${MONTHS_SHORT[g.eta.month - 1]}. ${g.eta.year}` : '—'} color={color} />
      </div>
      <div className="w-goal-verdict" style={{ background: `color-mix(in srgb, ${color} 12%, transparent)` }}>
        {g.done ? '¡Objetivo cumplido!'
          : g.onTrack ? 'Vas a tiempo' + (g.monthsAhead != null && g.monthsAhead > 0 ? `: ${g.monthsAhead} meses de adelanto.` : '.')
            : `Vas con retraso: a este ritmo tendrías ${m.fmt(g.projected)} en la fecha.`}
      </div>
      <div className="small" style={{ fontWeight: 600, marginTop: 8 }}>Consejos para llegar</div>
      {g.tips.map((t, i) => (
        <div key={i} className="row small" style={{ alignItems: 'flex-start', gap: 8, marginTop: 8 }}>
          <Icon name="lightbulb" size={16} style={{ color: AMBER, flexShrink: 0, marginTop: 2 }} />
          <span>{t}</span>
        </div>
      ))}
      {onApplyPct && g.suggestedPct != null && !g.onTrack && (
        <div style={{ marginTop: 16 }}>
          <Button onClick={() => onApplyPct(g.suggestedPct!)}>Ajustar mi reparto al {Math.trunc(g.suggestedPct)} %</Button>
        </div>
      )}
    </div>
  )
}

function GoalStat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="grow">
      <div className="small muted">{label}</div>
      <div className="ellipsis num" style={{ fontWeight: 600, color }}>{value}</div>
    </div>
  )
}

// ---------- Edad financiera ----------

const SPARKS = Array.from({ length: 70 }, (_, i) => [((i * 73) % 100) / 100, ((i * 37 + 11) % 100) / 100, ((i * 53) % 10) / 10])

/** Orbe luminoso con chispas: verde si tus finanzas son más maduras que tú, dorado si van por detrás. */
export function AgeOrb({ age, size = 256 }: { age: FinancialAge; size?: number }) {
  const ref = useRef<HTMLCanvasElement>(null)
  const mature = age.difference >= 0
  const base = mature ? MINT : AMBER
  useEffect(() => {
    const cv = ref.current
    const ctx = cv?.getContext('2d')
    if (!cv || !ctx) return
    const dpr = Math.min(3, window.devicePixelRatio || 1)
    cv.width = size * dpr; cv.height = size * dpr
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    const rgba = (hex: string, a: number) => {
      const n = parseInt(hex.slice(1), 16)
      return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
    }
    const draw = (phase: number) => {
      ctx.clearRect(0, 0, size, size)
      const c = size / 2, r = (size / 2) * 0.86
      const glow = ctx.createRadialGradient(c, c, 0, c, c, r * 1.25)
      glow.addColorStop(0, rgba(base, 0.35)); glow.addColorStop(1, rgba(base, 0))
      ctx.fillStyle = glow
      ctx.beginPath(); ctx.arc(c, c, r * 1.25, 0, Math.PI * 2); ctx.fill()
      ctx.beginPath()
      for (let i = 0; i <= 72; i++) {
        const a = (i / 72) * 2 * Math.PI
        const wob = 1 + 0.035 * Math.sin(3 * a + phase) + 0.025 * Math.cos(5 * a - phase)
        const x = c + Math.cos(a) * r * wob, y = c + Math.sin(a) * r * wob
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
      }
      ctx.closePath()
      const body = ctx.createRadialGradient(c, c, 0, c, c, r * 1.05)
      body.addColorStop(0, '#000'); body.addColorStop(0.33, '#0E0A02'); body.addColorStop(0.66, rgba(base, 0.55)); body.addColorStop(1, base)
      ctx.fillStyle = body; ctx.fill()
      for (const [x, y, s] of SPARKS) {
        const a = x * 2 * Math.PI + phase * 0.15
        const d = (0.55 + 0.45 * y) * r
        ctx.fillStyle = `rgba(255,244,214,${0.35 + 0.6 * s})`
        ctx.beginPath(); ctx.arc(c + Math.cos(a) * d, c + Math.sin(a) * d, (1.2 + 2.4 * s) / 2.2, 0, Math.PI * 2); ctx.fill()
      }
    }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { draw(0); return }
    let raf = 0
    const t0 = performance.now()
    const tick = (t: number) => { draw((((t - t0) % 9000) / 9000) * 2 * Math.PI); raf = requestAnimationFrame(tick) }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [size, base])
  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
      <canvas ref={ref} style={{ width: size, height: size, display: 'block' }} aria-hidden="true" />
      {size >= 150 && (
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
          <div className="num" style={{ fontSize: 54, fontWeight: 700, color: '#fff', letterSpacing: -1, lineHeight: 1.05 }}>{dec1(age.age)}</div>
          <div style={{ fontSize: 12, letterSpacing: 2, fontWeight: 600, color: 'rgba(255,255,255,0.8)' }}>EDAD FINANCIERA</div>
          <div style={{ fontSize: 16, fontWeight: 500, color: base, marginTop: 4 }}>
            {Math.abs(age.difference) < 0.05 ? 'igual que tu edad' : age.difference > 0 ? `${years(age.difference)} años más madura` : `${years(age.difference)} años por detrás`}
          </div>
        </div>
      )}
    </div>
  )
}

/** Ritmo de maduración de tus finanzas: de −1x (te quedas atrás) a 3x (maduras deprisa). En la paleta oscura. */
export function PaceOfAging({ pace }: { pace: number }) {
  const muted = '#A39C8C', ink = '#F6F1E6'
  const f = Math.max(0, Math.min(1, (pace + 1) / 4))
  return (
    <div style={{ width: '100%' }}>
      <div style={{ fontSize: 12, letterSpacing: 1.5, fontWeight: 600, color: muted }}>RITMO DE MADURACIÓN</div>
      <div className="num" style={{ fontSize: 24, fontWeight: 700, color: ink, textAlign: 'center', marginTop: 4 }}>{dec1(pace)}x</div>
      <div className="row small" style={{ justifyContent: 'space-between', color: muted }}><span>Lento</span><span>Rápido</span></div>
      <svg width="100%" height={32} viewBox="0 0 600 32" preserveAspectRatio="none" aria-hidden="true" style={{ display: 'block' }}>
        {Array.from({ length: 61 }, (_, i) => {
          const x = Math.min(599, Math.max(1, (600 * i) / 60))
          const major = i % 15 === 0
          return <line key={i} x1={x} x2={x} y1={major ? 4 : 10} y2={26} stroke={muted} strokeOpacity={major ? 0.9 : 0.45} strokeWidth={major ? 1.5 : 1} vectorEffect="non-scaling-stroke" />
        })}
        <line x1={600 * f} x2={600 * f} y1={0} y2={32} stroke={ink} strokeWidth={3} vectorEffect="non-scaling-stroke" />
      </svg>
      <div className="row tiny" style={{ justifyContent: 'space-between', color: muted }}><span>−1,0x</span><span>1,0x</span><span>3,0x</span></div>
    </div>
  )
}

/** Fila "Estado [Bueno] (i)" para la edad financiera. */
export function StatusRow({ label, status, good, onInfo, dark }: { label: string; status: string; good: boolean; onInfo: () => void; dark?: boolean }) {
  return (
    <div className="w-status" style={{ background: dark ? 'rgba(255,255,255,0.10)' : 'var(--chip)', color: dark ? '#fff' : 'var(--ink)' }}>
      <span className="grow" style={{ fontWeight: 500 }}>{label}</span>
      <span className="w-status-pill" style={{ background: dark ? '#fff' : 'var(--card)', color: good ? '#2E6B3F' : '#C2415C' }}>{status}</span>
      <button aria-label="Qué significa" className="w-status-info" style={{ color: dark ? 'rgba(255,255,255,0.7)' : 'var(--muted)' }} onClick={(e) => { e.stopPropagation(); onInfo() }}>
        <Icon name="info" size={24} />
      </button>
    </div>
  )
}

/** Tarjeta pequeña para Inicio. */
export function FinancialAgeMini({ age, onClick }: { age: FinancialAge | null; onClick: () => void }) {
  const [info, setInfo] = useState(false)
  const status: [string, boolean] | null = age == null ? null
    : age.difference >= 1 ? ['Buena', true] : age.difference > -1 ? ['Normal', true] : ['Mejorable', false]
  return (
    <div className="card ink clickable tight" role="button" onClick={onClick}>
      <div className="row">
        <div className="grow">
          <div style={{ fontSize: 12, letterSpacing: 1.5, color: 'rgba(255,255,255,0.7)' }}>EDAD FINANCIERA</div>
          {age == null ? (
            <>
              <div className="w-h20" style={{ color: '#fff' }}>¿Cuántos años tiene tu dinero?</div>
              <div className="small" style={{ color: 'rgba(255,255,255,0.7)' }}>Descúbrelo según tus hábitos y tu ahorro</div>
            </>
          ) : (
            <>
              <div className="num" style={{ fontSize: 32, fontWeight: 700, color: '#fff' }}>{dec1(age.age)}</div>
              <div className="small" style={{ color: age.difference >= 0 ? MINT : AMBER }}>
                {age.difference >= 0 ? `${years(age.difference)} años por delante de tu edad` : `${years(age.difference)} años por detrás de tu edad`}
              </div>
            </>
          )}
        </div>
        {age != null ? <AgeOrb age={age} size={96} /> : <Icon name="chevronRight" style={{ color: '#fff' }} />}
      </div>
      {age != null && status && (
        <div style={{ marginTop: 16 }}>
          <StatusRow label="Estado" status={status[0]} good={status[1]} onInfo={() => setInfo(true)} dark />
        </div>
      )}
      {info && age && (
        <div onClick={(e) => e.stopPropagation()}>
          <Sheet title="Tu edad financiera" onClose={() => setInfo(false)}>
            <div className="col" style={{ gap: 16 }}>
              <div>Mide lo maduras que están tus finanzas frente a tu edad real. Más edad es mejor: significa que ahorras, tienes colchón y no dependes de deudas.</div>
              <div className="small muted">Buena: más de 1 año por delante. Normal: en torno a tu edad. Mejorable: más de 1 año por detrás.</div>
              {age.factors.map((f) => (
                <div key={f.name} className="row small">
                  <span className="grow">{f.name}</span>
                  <span style={{ fontWeight: 600, color: f.years >= 0 ? 'var(--gain)' : 'var(--loss)' }}>{(f.years >= 0 ? '+' : '') + dec1(f.years)} años</span>
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

/** Pantalla de la edad financiera (siempre en oscuro, como la de WHOOP). */
export function FinancialAgeScreen() {
  const nav = useNav()
  const p = usePlanning()
  const [hideCalibrating, setHideCalibrating] = useState(false)
  const age = p.age
  const nextUpdate = 7 - (dayOfWeek(todayDate()) % 7)
  const title = (
    <div style={{ textAlign: 'center' }}>
      <div style={{ fontSize: 14, letterSpacing: 2, fontWeight: 700, color: '#fff' }}>SALUD FINANCIERA</div>
      <div style={{ fontSize: 11, letterSpacing: 1, fontWeight: 400, color: 'rgba(255,255,255,0.6)' }}>PRÓXIMA ACTUALIZACIÓN EN {nextUpdate} {nextUpdate === 1 ? 'DÍA' : 'DÍAS'}</div>
    </div>
  )
  return (
    <Overlay title={title} onClose={nav.back} actions={<div style={{ width: 48, flexShrink: 0 }} />}>
      <div className="w-age">
        {age == null ? (
          <div className="w-age-empty">
            <div style={{ fontSize: 28, fontWeight: 700, color: '#fff' }}>Tu edad financiera</div>
            <div style={{ color: 'rgba(255,255,255,0.7)' }}>Comparamos tus hábitos, tu ahorro y tu patrimonio con lo esperable para tu edad. Dinos en qué año naciste.</div>
            <button className="w-age-btn" onClick={() => nav.edit({ name: 'ageForm' })}>Poner mi año de nacimiento</button>
          </div>
        ) : (
          <>
            <AgeOrb age={age} size={256} />
            <div className="small" style={{ color: 'rgba(255,255,255,0.6)' }}>Tu edad real: {age.realAge} años</div>
            {age.calibrating && !hideCalibrating && (
              <div className="w-age-box" style={{ background: '#0E2A3A' }}>
                <div className="row" style={{ gap: 8 }}>
                  <Icon name="lightbulb" size={16} style={{ color: '#5AC8FA', flexShrink: 0 }} />
                  <span className="grow" style={{ fontWeight: 600, color: '#5AC8FA' }}>Tu edad financiera se está calibrando</span>
                  <button aria-label="Cerrar" className="w-age-x" onClick={() => setHideCalibrating(true)}>✕</button>
                </div>
                <div className="small" style={{ color: 'rgba(255,255,255,0.85)' }}>Con pocos meses de datos la cifra puede moverse mucho. Según Ahorra, vaca vaya conociendo tus meses, se estabilizará.</div>
              </div>
            )}
            <PaceOfAging pace={age.pace} />
            {(() => {
              const [t, b] = age.pace > 1.2
                ? ['Maduras deprisa', 'Ahorras lo suficiente para que tus finanzas ganen experiencia más rápido que tú. Sigue así.']
                : age.pace >= 0.8
                  ? ['A ritmo normal', 'Tus finanzas maduran al mismo ritmo que tú. Ahorrar un poco más te haría ganar años.']
                  : ['Te estás quedando atrás', 'Gastas casi todo lo que entra: tus finanzas no ganan experiencia. Mira qué te resta años abajo.']
              return (
                <div className="w-age-box">
                  <div style={{ fontSize: 20, fontWeight: 700, color: '#fff' }}>{t}</div>
                  <div className="small" style={{ color: 'rgba(255,255,255,0.75)' }}>{b}</div>
                </div>
              )
            })()}
            <div className="w-age-box" style={{ padding: '8px 0', gap: 0 }}>
              <div style={{ fontSize: 12, letterSpacing: 1.5, color: 'rgba(255,255,255,0.6)', padding: '8px 16px' }}>QUÉ TE SUMA Y QUÉ TE RESTA</div>
              {age.factors.map((f, i) => {
                const c = f.years > 0.05 ? MINT : f.years < -0.05 ? '#FF6B5A' : 'rgba(255,255,255,0.6)'
                return (
                  <div key={i} className="w-age-factor" style={i > 0 ? { borderTop: '1px solid rgba(255,255,255,0.08)' } : undefined}>
                    <div className="grow">
                      <div style={{ fontWeight: 500, color: '#fff' }}>{f.name}</div>
                      <div className="small" style={{ color: 'rgba(255,255,255,0.85)' }}>{f.value}</div>
                      <div className="tiny" style={{ color: 'rgba(255,255,255,0.55)' }}>{f.explanation}</div>
                    </div>
                    <div style={{ fontWeight: 700, color: c, whiteSpace: 'nowrap' }}>
                      {Math.abs(f.years) < 0.05 ? '±0' : (f.years < 0 ? '−' : '+') + years(f.years) + ' años'}
                    </div>
                  </div>
                )
              })}
            </div>
            <button className="w-age-change" onClick={() => nav.edit({ name: 'ageForm' })}>Cambiar año de nacimiento</button>
          </>
        )}
      </div>
    </Overlay>
  )
}
