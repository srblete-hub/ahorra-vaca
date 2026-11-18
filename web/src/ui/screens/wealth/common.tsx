// Piezas comunes de Patrimonio (Overline, MintCard, colores, filas de movimientos, anillos…).
import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import type { AccountType, Category, Expense, Income, Account } from '../../../data/models'
import { MONTHS, MONTHS_SHORT, WEEKDAYS, dayLabel, dayOfWeek, ofEpochDay, toEpochDay, today as todayDate, type LocalDate } from '../../../data/dates'
import { SERIES_COLORS, IconTile, ListRow, useMoney } from '../../components'
import { Icon } from '../../icons'
import { spendTag, type SpendTag } from '../../../domain'
import { SPEND_TAG_INFO } from '../../../domain/Spend'

export const AVAILABLE_COLOR = SERIES_COLORS[0]
export const SAVINGS_COLOR = SERIES_COLORS[1]
export const INVESTED_COLOR = SERIES_COLORS[3]

/** "5,0 %" (siempre con un decimal, como pct() de Kotlin). */
export function pct1(v: number): string {
  const n = Number.isFinite(v) ? v : 0
  return n.toFixed(1).replace('.', ',').replace('-', '−') + ' %'
}
/** "+5,2 %" */
export function signedPct1(v: number): string { return (v > 0 ? '+' : '') + pct1(v) }
/** "%.1f" con coma. */
export function dec1(v: number): string { return (Number.isFinite(v) ? v : 0).toFixed(1).replace('.', ',') }

/** "18 nov" */
export function shortDay(d: LocalDate) { return `${d.day} ${MONTHS_SHORT[d.month - 1]}` }
/** "mié. 18 nov." (EEE d MMM en español) */
export function weekdayDayMonth(d: LocalDate) {
  return `${WEEKDAYS[dayOfWeek(d) - 1].slice(0, 3)}. ${d.day} ${MONTHS_SHORT[d.month - 1]}.`
}
/** Hoy / Mañana / mié. 18 nov. */
export function prettyDate(d: LocalDate): string {
  const t = toEpochDay(todayDate()), e = toEpochDay(d)
  if (e === t) return 'Hoy'
  if (e === t + 1) return 'Mañana'
  return weekdayDayMonth(d)
}
/** "18 de noviembre" */
export function dayDeMonth(d: LocalDate) { return `${d.day} de ${MONTHS[d.month - 1]}` }

export function Overline({ children, style, className = '' }: { children: ReactNode; style?: CSSProperties; className?: string }) {
  return <div className={`w-overline ${className}`} style={style}>{children}</div>
}

/** Tarjeta principal con degradado suave (MintCard). */
export function MintCard({ children, style, className = '' }: { children: ReactNode; style?: CSSProperties; className?: string }) {
  return <div className={`w-mint ${className}`} style={style}>{children}</div>
}

/** Título de sección con acción opcional a la derecha. */
export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="w-section">
      <h2>{children}</h2>
      {action}
    </div>
  )
}

/** Botón de texto "+ Añadir" en verde. */
export function AddButton({ onClick, label = 'Añadir' }: { onClick: () => void; label?: string }) {
  return (
    <button className="w-add" onClick={onClick}><Icon name="add" size={24} />{label}</button>
  )
}

/** Anillo de progreso (0–1). */
export function Ring({ fraction, size, stroke, color, track, children }: { fraction: number; size: number; stroke: number; color: string; track: string; children?: ReactNode }) {
  const f = Math.max(0, Math.min(1, Number.isFinite(fraction) ? fraction : 0))
  const r = (size - stroke) / 2, c = 2 * Math.PI * r
  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)', display: 'block' }} aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={track} strokeWidth={stroke} />
        {f > 0 && <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeDasharray={`${f * c} ${c}`} />}
      </svg>
      {children && <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', textAlign: 'center' }}>{children}</div>}
    </div>
  )
}

/** Ancho real de un elemento (para dibujar gráficos sin deformar los puntos). */
export function useWidth<T extends HTMLElement>(): [React.RefObject<T>, number] {
  const ref = useRef<T>(null)
  const [w, setW] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    setW(el.clientWidth)
    if (typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver(() => setW(el.clientWidth))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  return [ref, w]
}

/** Trazado suave (como cubicTo con puntos medios en Kotlin). */
export function smoothPath(pts: Array<[number, number]>): string {
  if (pts.length === 0) return ''
  let d = `M${pts[0][0]},${pts[0][1]}`
  for (let i = 1; i < pts.length; i++) {
    const [x0, y0] = pts[i - 1], [x1, y1] = pts[i]
    const mid = (x0 + x1) / 2
    d += ` C${mid},${y0} ${mid},${y1} ${x1},${y1}`
  }
  return d
}

// ---------- Iconos y colores ----------

export function accountTypeIcon(t: AccountType): string {
  switch (t) {
    case 'BANK': return 'bank'
    case 'BANK_SAVINGS': return 'savings'
    case 'CASH': return 'payments'
    case 'HOME_SAVINGS': return 'home'
    case 'WALLET': return 'wallet'
    case 'FUND': return 'pie'
    case 'OTHER': return 'wallet'
    case 'DEBT': return 'creditCard'
  }
}

const TAG_STYLE: Record<SpendTag, [string, string]> = {
  SUPER: ['cart', '#34C759'], FOOD: ['restaurant', '#FF9F0A'], TRANSPORT: ['car', '#0A84FF'], HOME: ['home', '#5E5CE6'],
  SUBSCRIPTIONS: ['music', '#FF375F'], SHOPPING: ['bag', '#BF5AF2'], HEALTH: ['heart', '#30B0C7'], LEISURE: ['game', '#FFD60A'],
  SPORT: ['sport', '#30B0C7'], TRAVEL: ['plane', '#64D2FF'], GIFTS: ['gift', '#FF6B3D'], EDUCATION: ['school', '#AC8E68'],
  PETS: ['pet', '#A2845E'], OTHER: ['receipt', '#8E8E93'],
}
export function tagIcon(t: SpendTag) { return TAG_STYLE[t][0] }
export function tagColor(t: SpendTag) { return TAG_STYLE[t][1] }

export function categoryColor(categories: Category[], id: number): string {
  return SERIES_COLORS[Math.max(0, categories.findIndex((c) => c.id === id)) % SERIES_COLORS.length]
}
export function categoryIcon(c: Category | undefined): string {
  if (!c) return 'receipt'
  if (c.isSavings) return 'savings'
  if (c.name.toLocaleLowerCase('es').includes('necesidad')) return 'home'
  return 'bag'
}

// ---------- Filas de movimientos (ExpenseRow / IncomeRow de HomeScreen.kt) ----------

export function ExpenseRow({ e, categories, accounts, onClick }: { e: Expense; categories: Category[]; accounts: Account[]; onClick: () => void }) {
  const m = useMoney()
  const cat = categories.find((c) => c.id === e.categoryId)
  const account = accounts.find((a) => a.id === e.accountId)
  const t = spendTag(e)
  const sub = [dayLabel(e.epochDay), SPEND_TAG_INFO[t].label, e.fromCard ? 'Tarjeta' : null, e.billId != null ? 'Gasto fijo' : null].filter(Boolean).join(' · ')
  return (
    <ListRow
      title={e.note.trim() || cat?.name || 'Gasto'}
      subtitle={sub}
      onClick={onClick}
      leading={cat?.isSavings ? <IconTile icon={categoryIcon(cat)} color={SAVINGS_COLOR} /> : <IconTile icon={tagIcon(t)} color={tagColor(t)} />}
      trailing={'-' + m.fmt(e.amount)}
      trailingSub={account?.name}
    />
  )
}

export function IncomeRow({ i, accounts, onClick }: { i: Income; accounts: Account[]; onClick: () => void }) {
  const m = useMoney()
  const interest = i.sourceId == null
  return (
    <ListRow
      title={i.note.trim() || 'Ingreso'}
      subtitle={`${dayLabel(i.epochDay)} · ${interest ? 'Intereses' : 'Ingreso'}`}
      onClick={onClick}
      leading={<IconTile icon={interest ? 'savings' : 'work'} color="var(--gain)" />}
      trailing={'+' + m.fmt(i.amount)}
      trailingColor="var(--gain)"
      trailingSub={accounts.find((a) => a.id === i.accountId)?.name}
    />
  )
}

export { ofEpochDay }
