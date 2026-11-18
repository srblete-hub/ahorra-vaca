// Port de ui/SpendUi.kt (color e icono de cada tipo de gasto) y MerchantLogo de ui/NetImage.kt.
import { useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import type { Category } from '../../../data/models'
import { MONTHS_SHORT, WEEKDAYS, MONTHS, capitalize, ofEpochDay, type LocalDate } from '../../../data/dates'
import { Merchants, type SpendTag } from '../../../domain'
import { Icon } from '../../icons'
import { SERIES_COLORS } from '../../components'

export const TAG_COLOR: Record<SpendTag, string> = {
  SUPER: '#34C759', FOOD: '#FF9F0A', TRANSPORT: '#0A84FF', HOME: '#5E5CE6', SUBSCRIPTIONS: '#FF375F', SHOPPING: '#BF5AF2',
  HEALTH: '#30B0C7', LEISURE: '#FFD60A', SPORT: '#30B0C7', TRAVEL: '#64D2FF', GIFTS: '#FF6B3D', EDUCATION: '#AC8E68',
  PETS: '#A2845E', OTHER: '#8E8E93',
}

export const TAG_ICON: Record<SpendTag, string> = {
  SUPER: 'cart', FOOD: 'restaurant', TRANSPORT: 'car', HOME: 'home', SUBSCRIPTIONS: 'music', SHOPPING: 'bag',
  HEALTH: 'heart', LEISURE: 'game', SPORT: 'sport', TRAVEL: 'plane', GIFTS: 'gift', EDUCATION: 'school', PETS: 'pet', OTHER: 'receipt',
}

/** Icono de la categoría del reparto (categoryIcon de Sheets.kt). */
export function categoryIcon(c: Category | undefined | null): string {
  if (!c) return 'receipt'
  if (c.isSavings) return 'savings'
  if (c.name.toLowerCase().includes('necesidad')) return 'home'
  return 'bag'
}
export function categoryColor(categories: Category[], id: number): string {
  return SERIES_COLORS[Math.max(0, categories.findIndex((c) => c.id === id)) % SERIES_COLORS.length]
}

/** Icono redondo tintado de un tipo de gasto. */
export function TagCircle({ tag, size = 40 }: { tag: SpendTag; size?: number }) {
  const c = TAG_COLOR[tag]
  return (
    <div className="ico round" style={{ width: size, height: size, background: `color-mix(in srgb, ${c} 14%, transparent)`, color: c }}>
      <Icon name={TAG_ICON[tag]} size={Math.round(size * 0.55)} />
    </div>
  )
}

/** Logo del comercio si es conocido (o su web adivinada); si no, el icono del tipo de gasto. */
export function MerchantLogo({ note, tag, size = 40 }: { note: string; tag: SpendTag; size?: number }) {
  const urls = Merchants.logoUrls(note)
  const [idx, setIdx] = useState(0)
  const [ok, setOk] = useState(false)
  const url = urls[idx]
  if (!url) return <TagCircle tag={tag} size={size} />
  return (
    <div className="bd-logo" style={{ width: size, height: size }}>
      {!ok && <TagCircle tag={tag} size={size} />}
      <img
        src={url} alt="" loading="lazy" referrerPolicy="no-referrer"
        style={{ width: size * 0.62, height: size * 0.62, display: ok ? 'block' : 'none' }}
        onLoad={(e) => { if ((e.currentTarget.naturalWidth || 0) >= 24) setOk(true); else setIdx((i) => i + 1) }}
        onError={() => { setOk(false); setIdx((i) => i + 1) }}
      />
    </div>
  )
}

/** Texto de una línea que encoge la letra hasta caber (como TextAutoSize.StepBased). */
export function AutoFit({ text, max, min, style, className }: { text: string; max: number; min: number; style?: CSSProperties; className?: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState(max)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const fit = () => {
      let s = max
      el.style.fontSize = s + 'px'
      while (s > min && el.scrollWidth > el.clientWidth + 0.5) { s -= 1; el.style.fontSize = s + 'px' }
      setSize(s)
    }
    fit()
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(fit) : null
    ro?.observe(el)
    return () => ro?.disconnect()
  }, [text, max, min])
  return <div ref={ref} className={`num ${className ?? ''}`} style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', fontSize: size, ...style }}>{text}</div>
}

// ---------- Fechas con el formato de Android ----------

/** "Miércoles, 18 de noviembre" */
export function dayTitle(d: LocalDate): string {
  const wd = new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay()
  return `${capitalize(WEEKDAYS[(wd + 6) % 7])}, ${d.day} de ${MONTHS[d.month - 1]}`
}
/** "18 nov" (sin punto, como MoveRow). */
export function dayShort(d: LocalDate): string { return `${d.day} ${MONTHS_SHORT[d.month - 1]}` }
/** "18 nov." (con punto, como "d MMM" de java.time). */
export function dayShortDot(epochDay: number): string { const d = ofEpochDay(epochDay); return `${d.day} ${MONTHS_SHORT[d.month - 1]}.` }
/** "14:05" */
export function hhmm(ms: number): string {
  const t = new Date(ms)
  return `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`
}
/** "12,5 %" (pct de Components.kt: un decimal). */
export function pct1(v: number): string { return (Number.isFinite(v) ? v : 0).toFixed(1).replace('.', ',') + ' %' }
/** "51 %" */
export function pct0(v: number): string { return `${Math.round(Number.isFinite(v) ? v : 0)} %` }
