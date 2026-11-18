// Piezas pequeñas que comparten las tarjetas de Inicio (equivalen a helpers de Components.kt, SpendUi.kt y NetImage.kt).
import { useState, type CSSProperties, type ReactNode } from 'react'
import type { LocalDate } from '../../../data/dates'
import { MONTHS_SHORT, WEEKDAYS, todayEpoch, toEpochDay } from '../../../data/dates'
import type { SpendTag } from '../../../domain'
import { Merchants } from '../../../domain'
import { fixed } from '../../../domain/kt'
import { Icon } from '../../icons'
import { SERIES_COLORS } from '../../components'
import '../home.css'

/** Naranja de aviso de Android (0xFFFF9F0A). */
export const ORANGE = '#FF9F0A'
export const SAVINGS_COLOR = SERIES_COLORS[1]
export const INVESTED_COLOR = SERIES_COLORS[3]
export const AVAILABLE_COLOR = SERIES_COLORS[0]

/** SpendTag.color() de SpendUi.kt. */
export const TAG_COLOR: Record<SpendTag, string> = {
  SUPER: '#34C759', FOOD: '#FF9F0A', TRANSPORT: '#0A84FF', HOME: '#5E5CE6', SUBSCRIPTIONS: '#FF375F', SHOPPING: '#BF5AF2',
  HEALTH: '#30B0C7', LEISURE: '#FFD60A', SPORT: '#30B0C7', TRAVEL: '#64D2FF', GIFTS: '#FF6B3D', EDUCATION: '#AC8E68',
  PETS: '#A2845E', OTHER: '#8E8E93',
}
/** SpendTag.icon() de SpendUi.kt. */
export const TAG_ICON: Record<SpendTag, string> = {
  SUPER: 'cart', FOOD: 'restaurant', TRANSPORT: 'car', HOME: 'home', SUBSCRIPTIONS: 'music', SHOPPING: 'bag', HEALTH: 'heart',
  LEISURE: 'game', SPORT: 'sport', TRAVEL: 'plane', GIFTS: 'gift', EDUCATION: 'school', PETS: 'pet', OTHER: 'receipt',
}

/** Verde/amarillo/rojo oscuros de las fichas (darkOf en PulseUi.kt). */
export const ZONE_DARK = { GREEN: '#2E6B3F', YELLOW: '#B7791F', RED: '#C2415C' } as const

/** "5,0 %" (pct de Components.kt). */
export const pct1 = (v: number) => `${fixed(v, 1)} %`
export const signedPct1 = (v: number) => (v > 0 ? '+' : '') + pct1(v)

/** quantity(): hasta 8 decimales, miles con punto. */
export function quantity(v: number): string {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits: 8, useGrouping: 'always' as any }).format(v)
}

/** "vie. 20 nov." — Hoy / Mañana (prettyDate de FutureScreen.kt). */
export function prettyDate(d: LocalDate): string {
  const e = toEpochDay(d), t = todayEpoch()
  if (e === t) return 'Hoy'
  if (e === t + 1) return 'Mañana'
  const dow = new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay()
  return `${WEEKDAYS[(dow + 6) % 7].slice(0, 3)}. ${d.day} ${MONTHS_SHORT[d.month - 1]}.`
}

/** "20 nov" (sin punto, como short() de CashPlanUi.kt). */
export const shortDay = (d: LocalDate) => `${d.day} ${MONTHS_SHORT[d.month - 1]}`

/** Texto en mayúsculas pequeñas y espaciadas (Overline). */
export function Overline({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return <div className="hm-overline" style={style}>{children}</div>
}

/** Título de sección (SectionTitle). */
export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="hm-section-title">
      <div className="grow">{children}</div>
      {action}
    </div>
  )
}

/** Importe a la derecha con su subtítulo (AmountTrailing). */
export function AmountTrailing({ text, sub, color, subColor }: { text: string; sub?: string | null; color?: string; subColor?: string }) {
  return (
    <div className="right" style={{ flexShrink: 0, maxWidth: '48%' }}>
      <div className="num ellipsis" style={{ fontWeight: 600, color }}>{text}</div>
      {sub && <div className="small ellipsis" style={{ color: subColor ?? 'var(--muted)' }}>{sub}</div>}
    </div>
  )
}

/** Fila de lista con contenido libre a la derecha. */
export function Row({ leading, title, subtitle, trailing, onClick }: { leading?: ReactNode; title: ReactNode; subtitle?: ReactNode; trailing?: ReactNode; onClick?: () => void }) {
  return (
    <div className={`list-item ${onClick ? 'clickable' : ''}`} onClick={onClick} role={onClick ? 'button' : undefined}>
      {leading}
      <div className="grow">
        <div className="t ellipsis">{title}</div>
        {subtitle && <div className="s hm-clamp2">{subtitle}</div>}
      </div>
      {trailing}
    </div>
  )
}

/** Icono en cuadrado tintado (IconTile). */
export function Tile({ icon, color, size = 48, round }: { icon: string; color: string; size?: number; round?: boolean }) {
  return (
    <div className="hm-tile" style={{ width: size, height: size, borderRadius: round ? '50%' : size / 3, background: `color-mix(in srgb, ${color} 14%, transparent)`, color }}>
      <Icon name={icon} size={size / 2} />
    </div>
  )
}

/** Logo del comercio si se encuentra; si no, el icono de su tipo de gasto (MerchantLogo). */
export function MerchantLogo({ note, tag, size = 40 }: { note: string; tag: SpendTag; size?: number }) {
  const urls = Merchants.logoUrls(note)
  const [i, setI] = useState(0)
  const url = urls[i]
  if (url) {
    return (
      <div className="hm-logo" style={{ width: size, height: size }}>
        <img src={url} alt="" width={size * 0.62} height={size * 0.62} loading="lazy" referrerPolicy="no-referrer" onError={() => setI(i + 1)} />
      </div>
    )
  }
  const c = TAG_COLOR[tag]
  return (
    <div className="hm-tile" style={{ width: size, height: size, borderRadius: '50%', background: `color-mix(in srgb, ${c} 14%, transparent)`, color: c }}>
      <Icon name={TAG_ICON[tag]} size={size * 0.55} />
    </div>
  )
}

/** Tarjeta oscura de la marca (BrandCard). */
export function BrandCard({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return <div className="hm-brand" style={style}>{children}</div>
}

/** Tarjeta con degradado suave (MintCard). */
export function MintCard({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return <div className="hm-mint" style={style}>{children}</div>
}

/** Pastilla de acción (PillButton de StoryCards.kt). */
export function PillButton({ children, dark, onClick, disabled }: { children: ReactNode; dark?: boolean; onClick: () => void; disabled?: boolean }) {
  return <button className={`hm-pill-btn ${dark ? 'dark' : ''}`} onClick={onClick} disabled={disabled}>{children}</button>
}

/** Pepe, el pastor: dibujo propio (sustituye a ic_pepe de Android). */
export function PepeAvatar({ size = 48, title }: { size?: number; title?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role={title ? 'img' : undefined} aria-label={title} aria-hidden={title ? undefined : true} style={{ flexShrink: 0 }}>
      <circle cx="32" cy="32" r="32" fill="#DCEBD3" />
      <clipPath id="pepe-clip"><circle cx="32" cy="32" r="32" /></clipPath>
      <g clipPath="url(#pepe-clip)">
        <path d="M12 64c0-12 8-18 18-18s18 6 18 18Z" fill="#2E6B3F" />
        <path d="M23 46c2 3 5 4 7 4s5-1 7-4Z" fill="#F2A9B6" />
      </g>
      <circle cx="30" cy="34" r="11" fill="#F3C9A8" />
      <path d="M19 36c1 9 6 13 11 13s10-4 11-13c-3 3-7 4-11 4s-8-1-11-4Z" fill="#fff" />
      <circle cx="26" cy="33" r="1.6" fill="#2B2820" />
      <circle cx="34" cy="33" r="1.6" fill="#2B2820" />
      <circle cx="23" cy="36" r="1.8" fill="#F08AA0" opacity=".7" />
      <circle cx="37" cy="36" r="1.8" fill="#F08AA0" opacity=".7" />
      <path d="M16 26h28c0 0-1 2-3 2H19c-2 0-3-2-3-2Z" fill="#17150F" />
      <path d="M21 26c0-5 4-8 9-8s9 3 9 8Z" fill="#17150F" />
      <rect x="21" y="23" width="18" height="2" fill="#E0607A" />
      <path d="M46 62V20a4 4 0 0 1 8 0v3" fill="none" stroke="#17150F" strokeWidth="2.6" strokeLinecap="round" />
    </svg>
  )
}
