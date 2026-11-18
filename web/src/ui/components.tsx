// Componentes comunes (equivalen a ui/Components.kt y ui/Brand.kt).
import { useEffect, useId, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Icon, type IconName } from './icons'
import { useSettings, useToasts } from '../data/store'
import { HIDDEN, money, signedMoney } from '../data/format'
import { amountInput, parseAmount } from '../data/format'

export const SERIES_COLORS = ['#2E6B3F', '#E0607A', '#E9B949', '#6E9C7A', '#A07A52', '#F2A9B6', '#B7791F', '#9FC9A8']

/** Hash estable de un texto (como String.hashCode de Kotlin). */
export function hashCode(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0
  return h & 0x7fffffff
}
export function colorFor(name: string) { return SERIES_COLORS[hashCode(name) % SERIES_COLORS.length] }

// ---------- Importes ----------

/** Formatea importes respetando "ocultar importes". */
export function useMoney() {
  const s = useSettings()
  const cur = s.currency || 'EUR'
  return {
    hidden: s.hideAmounts,
    currency: cur,
    fmt: (v: number) => (s.hideAmounts ? HIDDEN : money(v, cur)),
    signed: (v: number) => (s.hideAmounts ? HIDDEN : signedMoney(v, cur)),
  }
}

/** Importe grande con los céntimos más pequeños (3.494,50 €). */
export function BigAmount({ value, size = 56, color, style }: { value: number; size?: number; color?: string; style?: CSSProperties }) {
  const m = useMoney()
  const text = m.fmt(value)
  const cut = text.lastIndexOf(',')
  return (
    <div className="hero-num" style={{ fontSize: size, color, ...style }}>
      {m.hidden || cut < 0 ? text : (<>{text.slice(0, cut)}<span className="dec">{text.slice(cut)}</span></>)}
    </div>
  )
}

/** Número que sube animado hasta `value` la primera vez. */
export function AnimatedAmount({ value, size = 56, color }: { value: number; size?: number; color?: string }) {
  const [shown, setShown] = useState(value)
  const from = useRef(0)
  useEffect(() => {
    const start = performance.now(), a = from.current, b = value
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setShown(b); from.current = b; return }
    let raf = 0
    const tick = (t: number) => {
      const k = Math.min(1, (t - start) / 900)
      const e = 1 - Math.pow(1 - k, 3)
      setShown(a + (b - a) * e)
      if (k < 1) raf = requestAnimationFrame(tick); else from.current = b
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [value])
  return <BigAmount value={shown} size={size} color={color} />
}

export function gainClass(v: number) { return v > 0.004 ? 'gain' : v < -0.004 ? 'loss' : 'muted' }

// ---------- Estructura ----------

export function Card({ children, className = '', onClick, style }: { children: ReactNode; className?: string; onClick?: () => void; style?: CSSProperties }) {
  return (
    <div className={`card ${onClick ? 'clickable' : ''} ${className}`} onClick={onClick} style={style} role={onClick ? 'button' : undefined}>
      {children}
    </div>
  )
}

export function CardHead({ title, subtitle, action }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode }) {
  return (
    <div className="card-head">
      <div className="grow">
        <div className="title">{title}</div>
        {subtitle && <div className="subtitle">{subtitle}</div>}
      </div>
      {action}
    </div>
  )
}

const SPOTS = [
  'M-10 18c22-14 64-12 78 8 12 18-4 40-26 46-20 6-30 22-50 18-20-4-24-58-2-72Z',
  'M206 -14c30-6 66 6 72 30 6 22-18 32-12 52 6 18-20 30-40 22-22-8-14-30-34-42-22-14-22-56 14-62Z',
  'M118 120c20-10 48-4 56 14 8 16-6 30-24 34-18 4-40-2-44-18-4-12 2-24 12-30Z',
  'M268 148c18-8 44 0 50 18 6 16-10 32-30 32s-36-10-36-24c0-12 6-20 16-26Z',
  'M30 168c10-6 26-4 30 6s-4 20-16 22-24-4-24-12 2-12 10-16Z',
]
/** Las manchas de vaca de la marca. */
export function Spots({ tone = '#2B2820' }: { tone?: string }) {
  return (
    <svg className="spots" viewBox="0 0 320 200" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      {SPOTS.map((d, i) => <path key={i} d={d} fill={tone} />)}
    </svg>
  )
}
/** Tarjeta tinta con manchas (la del banco). */
export function SpottedCard({ children, onClick, style }: { children: ReactNode; onClick?: () => void; style?: CSSProperties }) {
  return (
    <div className="spotted" onClick={onClick} style={style}>
      <Spots />
      <div className="content">{children}</div>
    </div>
  )
}

export function SectionLabel({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="row" style={{ justifyContent: 'space-between' }}>
      <div className="section-label">{children}</div>
      {action}
    </div>
  )
}

export function EmptyState({ icon, title, body, action }: { icon: IconName; title: string; body?: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <IconTile icon={icon} color="var(--brand)" size={56} />
      <div className="title" style={{ color: 'var(--ink)' }}>{title}</div>
      {body && <div className="small">{body}</div>}
      {action}
    </div>
  )
}

// ---------- Iconos y logos ----------

/** Icono en un cuadrado redondeado con el color tintado. */
export function IconTile({ icon, color, size = 48, round }: { icon: IconName | string; color: string; size?: number; round?: boolean }) {
  return (
    <div className={`ico ${round ? 'round' : ''}`} style={{ width: size, height: size, borderRadius: round ? '50%' : size / 3, background: `color-mix(in srgb, ${color} 14%, transparent)`, color }}>
      <Icon name={icon} size={size / 2} />
    </div>
  )
}

/** Círculo con iniciales (comercios, monedas, tickers). */
export function Initials({ text, color, size = 40, image }: { text: string; color: string; size?: number; image?: string }) {
  const t = text.slice(0, 3).toLocaleUpperCase('es')
  const [broken, setBroken] = useState(false)
  if (image && !broken) {
    return <img src={image} alt="" width={size} height={size} style={{ borderRadius: '50%', flexShrink: 0, background: 'var(--chip)', objectFit: 'cover' }} onError={() => setBroken(true)} />
  }
  return (
    <div className="ico round" style={{ width: size, height: size, background: color, color: '#fff', fontSize: size * (t.length === 1 ? 0.42 : 0.26) }}>
      {t}
    </div>
  )
}

const BANKS: [test: (n: string) => boolean, bg: string, fg: string, text: string][] = [
  [(n) => n.includes('bbva'), '#004481', '#fff', 'BBVA'],
  [(n) => n.includes('santander'), '#EC0000', '#fff', 'S'],
  [(n) => n.split(' ').includes('ing') || n.startsWith('ing'), '#FF6200', '#fff', 'ING'],
  [(n) => n.includes('caixa'), '#007EAE', '#fff', 'CX'],
  [(n) => n.includes('imagin'), '#00C3A5', '#fff', 'im'],
  [(n) => n.includes('sabadell'), '#006DFF', '#fff', 'S'],
  [(n) => n.includes('openbank'), '#E30613', '#fff', 'OB'],
  [(n) => n.includes('revolut'), '#0B0B0C', '#fff', 'R'],
  [(n) => n.includes('n26'), '#36A18B', '#fff', 'N26'],
  [(n) => n.includes('wise'), '#9FE870', '#163300', 'W'],
  [(n) => n.includes('paypal'), '#003087', '#fff', 'P'],
  [(n) => n.includes('trade republic'), '#0B0B0C', '#fff', 'TR'],
  [(n) => n.includes('myinvestor'), '#1D1D1B', '#FFD100', 'MI'],
  [(n) => n.includes('robinhood'), '#CCFF00', '#0B0B0C', 'R'],
  [(n) => n.includes('binance'), '#181A20', '#F0B90B', 'B'],
  [(n) => n.includes('indexa'), '#00A1E0', '#fff', 'IX'],
]
/** Logo redondo de una cuenta: colores de los bancos conocidos, o un color estable por nombre. */
export function AccountLogo({ name, size = 40 }: { name: string; size?: number }) {
  const n = name.toLocaleLowerCase('es')
  const hit = BANKS.find(([t]) => t(n))
  const [bg, fg, text] = hit ? [hit[1], hit[2], hit[3]] : [colorFor(name), '#fff', (name.trim()[0] ?? '?').toLocaleUpperCase('es')]
  return (
    <div className="ico round" style={{ width: size, height: size, background: bg, color: fg, fontSize: size * (text.length === 1 ? 0.42 : 0.27) }}>
      {text}
    </div>
  )
}

/** Foto de perfil o la inicial del nombre. */
export function Avatar({ size = 48, ring }: { size?: number; ring?: string }) {
  const s = useSettings()
  const style: CSSProperties = { width: size, height: size, borderRadius: '50%', flexShrink: 0, overflow: 'hidden', display: 'grid', placeItems: 'center', background: 'var(--gain)', color: '#fff', fontWeight: 800, fontSize: size * 0.42, boxShadow: ring ? `0 0 0 4px ${ring}` : undefined }
  if (s.avatar) return <img src={s.avatar} alt="Foto de perfil" style={{ ...style, objectFit: 'cover' }} />
  return <div style={style}>{(s.userName.trim()[0] ?? '?').toLocaleUpperCase('es')}</div>
}

export function Dot({ color, size = 8 }: { color: string; size?: number }) {
  return <span style={{ width: size, height: size, borderRadius: '50%', background: color, display: 'inline-block', flexShrink: 0 }} />
}

// ---------- Listas ----------

export function ListRow({ leading, title, subtitle, trailing, trailingSub, trailingColor, onClick }: {
  leading?: ReactNode; title: ReactNode; subtitle?: ReactNode; trailing?: ReactNode; trailingSub?: ReactNode; trailingColor?: string; onClick?: () => void
}) {
  return (
    <div className={`list-item ${onClick ? 'clickable' : ''}`} onClick={onClick} role={onClick ? 'button' : undefined}>
      {leading}
      <div className="grow">
        <div className="t ellipsis">{title}</div>
        {subtitle && <div className="s ellipsis">{subtitle}</div>}
      </div>
      {(trailing != null || trailingSub != null) && (
        <div className="right" style={{ flexShrink: 0, maxWidth: '45%' }}>
          {trailing != null && <div className="t num" style={{ color: trailingColor }}>{trailing}</div>}
          {trailingSub != null && <div className="s ellipsis">{trailingSub}</div>}
        </div>
      )}
    </div>
  )
}

// ---------- Gráficos sencillos ----------

export function ProgressLine({ fraction, color = 'var(--brand)', height = 8, track = 'var(--chip)' }: { fraction: number; color?: string; height?: number; track?: string }) {
  const f = Math.max(0, Math.min(1, Number.isFinite(fraction) ? fraction : 0))
  return (
    <div className="progress" style={{ height, background: track }}>
      <span style={{ width: `${f * 100}%`, background: color }} />
    </div>
  )
}

export interface Slice { value: number; color: string; label?: string }
export function StackedBar({ slices, height = 12 }: { slices: Slice[]; height?: number }) {
  const total = slices.reduce((a, s) => a + Math.max(0, s.value), 0)
  if (total <= 0) return <div className="bar" style={{ height }} />
  return (
    <div className="bar" style={{ height, background: 'transparent' }}>
      {slices.filter((s) => s.value > 0).map((s, i) => <span key={i} style={{ flex: `${s.value / total} 1 0`, minWidth: height, background: s.color }} />)}
    </div>
  )
}

/** Línea suave con los valores (sparkline o gráfico grande). */
export function LineChart({ values, color = 'var(--brand)', height = 120, fill = true, strokeWidth = 2.5 }: { values: number[]; color?: string; height?: number; fill?: boolean; strokeWidth?: number }) {
  const id = useId()
  if (values.length < 2) return <div style={{ height }} />
  const w = 300, h = height, pad = strokeWidth
  const min = Math.min(...values), max = Math.max(...values)
  const range = max - min || 1
  const pts = values.map((v, i) => [(i / (values.length - 1)) * w, pad + (h - 2 * pad) * (1 - (v - min) / range)] as const)
  let d = `M${pts[0][0]},${pts[0][1]}`
  for (let i = 1; i < pts.length; i++) {
    const [x0, y0] = pts[i - 1], [x1, y1] = pts[i]
    const cx = (x0 + x1) / 2
    d += ` C${cx},${y0} ${cx},${y1} ${x1},${y1}`
  }
  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" width="100%" height={h} style={{ display: 'block', overflow: 'visible' }} aria-hidden="true">
      {fill && (
        <>
          <defs>
            <linearGradient id={id} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor={color} stopOpacity="0.22" />
              <stop offset="1" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={`${d} L${w},${h} L0,${h} Z`} fill={`url(#${id})`} />
        </>
      )}
      <path d={d} fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

/** Donut con porciones. */
export function Donut({ slices, size = 160, thickness = 22, children }: { slices: Slice[]; size?: number; thickness?: number; children?: ReactNode }) {
  const total = slices.reduce((a, s) => a + Math.max(0, s.value), 0)
  const r = (size - thickness) / 2, c = 2 * Math.PI * r
  let acc = 0
  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }} aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--chip)" strokeWidth={thickness} />
        {total > 0 && slices.filter((s) => s.value > 0).map((s, i) => {
          const len = (s.value / total) * c
          const gap = slices.length > 1 ? Math.min(4, len / 2) : 0
          const el = <circle key={i} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={s.color} strokeWidth={thickness} strokeDasharray={`${Math.max(0, len - gap)} ${c}`} strokeDashoffset={-acc} />
          acc += len
          return el
        })}
      </svg>
      {children && <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', textAlign: 'center' }}>{children}</div>}
    </div>
  )
}

/** Barras verticales sencillas. */
export function Bars({ values, labels, color = 'var(--brand)', height = 120, highlight }: { values: number[]; labels?: string[]; color?: string; height?: number; highlight?: number }) {
  const max = Math.max(1, ...values)
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 6, height: height + (labels ? 20 : 0) }}>
      {values.map((v, i) => (
        <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, minWidth: 0 }}>
          <div style={{ width: '100%', height: Math.max(2, (v / max) * height), background: highlight === i || highlight == null ? color : 'var(--chip)', borderRadius: 6 }} />
          {labels && <div className="tiny muted ellipsis" style={{ maxWidth: '100%' }}>{labels[i]}</div>}
        </div>
      ))}
    </div>
  )
}

// ---------- Botones y controles ----------

export function Button({ children, onClick, variant = 'primary', disabled, icon, small, style, type = 'button' }: {
  children: ReactNode; onClick?: () => void; variant?: 'primary' | 'secondary' | 'danger' | 'ghost'; disabled?: boolean; icon?: IconName; small?: boolean; style?: CSSProperties; type?: 'button' | 'submit'
}) {
  return (
    <button type={type} className={`btn ${variant !== 'primary' ? variant : ''} ${small ? 'small' : ''}`} onClick={onClick} disabled={disabled} style={style}>
      {icon && <Icon name={icon} size={20} />}
      {children}
    </button>
  )
}

export function IconButton({ icon, label, onClick, filled, badge, style }: { icon: IconName; label: string; onClick: () => void; filled?: boolean; badge?: boolean; style?: CSSProperties }) {
  return (
    <button type="button" className={`icon-btn ${filled ? 'filled' : ''}`} aria-label={label} title={label} onClick={onClick} style={{ position: 'relative', ...style }}>
      <Icon name={icon} />
      {badge && <span style={{ position: 'absolute', top: 6, right: 6, width: 8, height: 8, borderRadius: '50%', background: 'var(--ubre)' }} />}
    </button>
  )
}

export function Pill({ children, on, onClick, tone }: { children: ReactNode; on?: boolean; onClick?: () => void; tone?: 'brand' | 'loss' }) {
  return <button type="button" className={`pill ${on ? 'on' : ''} ${tone ?? ''}`} onClick={onClick}>{children}</button>
}

export function Segmented<T extends string | number>({ options, value, onChange, label }: { options: T[]; value: T; onChange: (v: T) => void; label: (v: T) => string }) {
  return (
    <div className="segmented" role="tablist">
      {options.map((o) => (
        <button type="button" key={String(o)} role="tab" aria-selected={o === value} className={o === value ? 'on' : ''} onClick={() => onChange(o)}>{label(o)}</button>
      ))}
    </div>
  )
}

export function Switch({ checked, onChange, label, sub }: { checked: boolean; onChange: (v: boolean) => void; label: ReactNode; sub?: ReactNode }) {
  return (
    <div className="switch-row" role="switch" aria-checked={checked} onClick={() => onChange(!checked)}>
      <div className="grow">
        <div style={{ fontWeight: 600 }}>{label}</div>
        {sub && <div className="small muted">{sub}</div>}
      </div>
      <div className={`switch ${checked ? 'on' : ''}`} />
    </div>
  )
}

// ---------- Formularios ----------

export function TextField({ label, value, onChange, placeholder, type = 'text', inputMode, autoFocus, error, hint, maxLength }: {
  label: string; value: string; onChange: (v: string) => void; placeholder?: string; type?: string; inputMode?: 'text' | 'decimal' | 'numeric' | 'email' | 'search'; autoFocus?: boolean; error?: string | null; hint?: string; maxLength?: number
}) {
  const id = useId()
  return (
    <div className="col" style={{ gap: 4 }}>
      <div className={`field ${error ? 'error' : ''}`}>
        <label htmlFor={id}>{label}</label>
        <input id={id} type={type} value={value} placeholder={placeholder} inputMode={inputMode} autoFocus={autoFocus} maxLength={maxLength} onChange={(e) => onChange(e.target.value)} />
      </div>
      {error ? <div className="field-error">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
    </div>
  )
}

/** Campo de importe: guarda el texto tal cual (acepta coma) y devuelve el número con parseAmount. */
export function AmountField({ label, value, onChange, suffix = '€', autoFocus, hint, error }: { label: string; value: string; onChange: (v: string) => void; suffix?: string; autoFocus?: boolean; hint?: string; error?: string | null }) {
  const id = useId()
  return (
    <div className="col" style={{ gap: 4 }}>
      <div className={`field ${error ? 'error' : ''}`}>
        <label htmlFor={id}>{label}</label>
        <div className="row" style={{ gap: 4 }}>
          <input id={id} inputMode="decimal" value={value} autoFocus={autoFocus} onChange={(e) => onChange(e.target.value.replace(/[^0-9.,-]/g, ''))} />
          {suffix && <span className="muted">{suffix}</span>}
        </div>
      </div>
      {error ? <div className="field-error">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
    </div>
  )
}

/** Importe grande centrado (formularios de gasto e ingreso). */
export function BigAmountInput({ value, onChange, symbol = '€', autoFocus = true }: { value: string; onChange: (v: string) => void; symbol?: string; autoFocus?: boolean }) {
  return (
    <div className="row" style={{ justifyContent: 'center', gap: 8, padding: '8px 0' }}>
      <input
        className="amount-input num"
        inputMode="decimal"
        placeholder="0"
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/[^0-9.,]/g, ''))}
        style={{ border: 0, outline: 'none', background: 'transparent', textAlign: 'right', width: `${Math.max(1, value.length) * 0.62 + 0.4}em`, maxWidth: '75%', padding: 0 }}
      />
      <span style={{ fontSize: 40, fontWeight: 800 }}>{symbol}</span>
    </div>
  )
}

export function SelectField<T extends string | number>({ label, value, onChange, options }: { label: string; value: T; onChange: (v: T) => void; options: { value: T; label: string }[] }) {
  const id = useId()
  return (
    <div className="field" style={{ position: 'relative' }}>
      <label htmlFor={id}>{label}</label>
      <select id={id} value={String(value)} onChange={(e) => {
        const raw = e.target.value
        const hit = options.find((o) => String(o.value) === raw)
        if (hit) onChange(hit.value)
      }}>
        {options.map((o) => <option key={String(o.value)} value={String(o.value)}>{o.label}</option>)}
      </select>
      <Icon name="chevronDown" size={20} style={{ position: 'absolute', right: 12, bottom: 14, color: 'var(--muted)', pointerEvents: 'none' }} />
    </div>
  )
}

export { parseAmount, amountInput }

// ---------- Hojas, pantallas superpuestas y diálogos ----------

function useLockScroll() {
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [])
}
function useEscape(onClose: () => void) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', h)
    return () => window.removeEventListener('keydown', h)
  }, [onClose])
}

/** Hoja que sube desde abajo (formularios). */
export function Sheet({ title, onClose, children, action }: { title: ReactNode; onClose: () => void; children: ReactNode; action?: ReactNode }) {
  useLockScroll()
  useEscape(onClose)
  return createPortal(
    <>
      <div className="scrim" onClick={onClose} />
      <div className="sheet" role="dialog" aria-modal="true">
        <div className="sheet-grab" />
        <div className="sheet-head">
          <div className="title">{title}</div>
          <div className="row" style={{ gap: 4 }}>
            {action}
            <IconButton icon="close" label="Cerrar" onClick={onClose} />
          </div>
        </div>
        <div className="sheet-body">{children}</div>
      </div>
    </>,
    document.body,
  )
}

/** Pantalla completa por encima de las pestañas, con botón de volver. */
export function Overlay({ title, onClose, children, actions }: { title?: ReactNode; onClose: () => void; children: ReactNode; actions?: ReactNode }) {
  useLockScroll()
  useEscape(onClose)
  return createPortal(
    <div className="overlay" role="dialog" aria-modal="true">
      <div className="overlay-inner">
        <div className="overlay-head">
          <IconButton icon="back" label="Volver" onClick={onClose} filled />
          <div className="title ellipsis">{title}</div>
          {actions}
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}

export function ConfirmDialog({ title, body, confirm = 'Borrar', danger = true, onConfirm, onClose }: { title: string; body?: ReactNode; confirm?: string; danger?: boolean; onConfirm: () => void; onClose: () => void }) {
  useEscape(onClose)
  return createPortal(
    <>
      <div className="scrim dialog-scrim" onClick={onClose} />
      <div className="dialog" role="alertdialog" aria-modal="true">
        <div className="title">{title}</div>
        {body && <div className="muted">{body}</div>}
        <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
          <Button variant="ghost" small onClick={onClose}>Cancelar</Button>
          <Button small variant={danger ? 'secondary' : 'primary'} style={danger ? { color: 'var(--loss)' } : undefined} onClick={() => { onConfirm(); onClose() }}>{confirm}</Button>
        </div>
      </div>
    </>,
    document.body,
  )
}

export function Toasts() {
  const list = useToasts()
  return (
    <div className="toasts" aria-live="polite">
      {list.map((t) => <div key={t.id} className="toast">{t.text}</div>)}
    </div>
  )
}

/** Aviso de "no es una recomendación". */
export function NotAdvice() {
  return <div className="tiny muted" style={{ textAlign: 'center', padding: '4px 16px' }}>Información orientativa. No es una recomendación de compra ni de venta.</div>
}

export function Money({ value, signed, className, style }: { value: number; signed?: boolean; className?: string; style?: CSSProperties }) {
  const m = useMoney()
  return <span className={`num ${className ?? ''}`} style={style}>{signed ? m.signed(value) : m.fmt(value)}</span>
}
