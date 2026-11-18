// Port de ui/screens/WrappedScreen.kt: el Wrapped del mes en tarjetas a pantalla completa y la ficha para compartir.
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useDB, useSettings, toast } from '../../../data/store'
import { MONTHS, WEEKDAYS, capitalize, lengthOfMonth, monthIndex, plusMonths, thisMonth, today, ymKey, ofEpochDay, type YearMonth } from '../../../data/dates'
import { SPEND_TAG_INFO, Wrapped, usePlanning, type MonthWrapped, type SpendTag } from '../../../domain'
import { useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav, type OverlayRoute } from '../../nav'
import { renderShareCard, shortDay } from './shareCard'
import './profile.css'

const NEON = '#4ADE80'

const TAG_ICON: Record<SpendTag, string> = {
  SUPER: 'cart', FOOD: 'restaurant', TRANSPORT: 'car', HOME: 'home', SUBSCRIPTIONS: 'music', SHOPPING: 'bag', HEALTH: 'heart',
  LEISURE: 'game', SPORT: 'sport', TRAVEL: 'plane', GIFTS: 'gift', EDUCATION: 'school', PETS: 'pet', OTHER: 'receipt',
}

interface Story { colors: [string, string]; overline: string; content: ReactNode }

const Big = ({ children, color = '#fff' }: { children: ReactNode; color?: string }) => <div className="pf-wr-big num" style={{ color }}>{children}</div>
const Small = ({ children }: { children: ReactNode }) => <div className="pf-wr-small">{children}</div>
const Body = ({ children }: { children: ReactNode }) => <div className="pf-wr-body">{children}</div>

export function WrappedScreen({ route: _route }: { route: OverlayRoute }) {
  const nav = useNav()
  const p = usePlanning()
  const s = useSettings()
  const transfers = useDB((st) => st.transfers)
  const m = useMoney()
  const [month, setMonth] = useState<YearMonth>(() => thisMonth())
  const w = useMemo(() => Wrapped.of(p.data, month, transfers), [p.data, month, transfers])
  const name = s.userName.trim() || 'tú'
  const mName = MONTHS[month.month - 1]
  const canNext = monthIndex(month) < monthIndex(thisMonth())

  const stories: Story[] = []
  stories.push({ colors: ['#1DB954', '#0B3D20'], overline: `Tu ${mName} en Ahorra, vaca`, content: <>
    <Big>Hola, {name}.</Big>
    <Body>{w.isCurrent ? `Así va tu ${mName} hasta hoy (día ${w.daysElapsed}). Desliza →` : `Así fue tu ${mName}. Desliza →`}</Body>
  </> })
  stories.push({ colors: ['#5E5CE6', '#1C1A5E'], overline: 'Entró y salió', content: <>
    <Small>Ha entrado</Small>
    <Big color={NEON}>{m.fmt(w.income)}</Big>
    <div style={{ height: 16 }} />
    <Small>Has gastado</Small>
    <Big>{m.fmt(w.spent)}</Big>
    <Body>{w.payments} pagos · {m.fmt(w.perDay)} al día de media</Body>
    {w.fixed > 0 && <Body>Aparte, {m.fmt(w.fixed)} de alquiler y fijos (no cuentan en la media).</Body>}
  </> })
  if (w.topTag) {
    const [tag, amount, share] = w.topTag
    stories.push({ colors: ['#FF6B3D', '#5A1A06'], overline: 'Tu categoría estrella', content: <>
      <Icon name={TAG_ICON[tag]} size={64} />
      <Big>{SPEND_TAG_INFO[tag].label}</Big>
      <Body>{m.fmt(amount)} · el {Math.trunc(share)} % de todo lo que gastaste</Body>
    </> })
  }
  if (w.topPlace) {
    const [place, total, times] = w.topPlace
    stories.push({ colors: ['#E91E63', '#4A0820'], overline: 'Tu sitio favorito', content: <>
      <Big>{place}</Big>
      <Body>Fuiste {times} {times === 1 ? 'vez' : 'veces'} y te dejaste {m.fmt(total)}.</Body>
    </> })
  }
  if (w.biggest) {
    const e = w.biggest
    const d = ofEpochDay(e.epochDay)
    stories.push({ colors: ['#FFB300', '#5C3A00'], overline: 'Tu mayor compra', content: <>
      <Big>{m.fmt(e.amount)}</Big>
      <Body>{e.note.trim() || 'Sin descripción'} · {shortDay(d.day, d.month)}</Body>
    </> })
  }
  if (w.priciestWeekday || w.noSpendDays > 0) {
    stories.push({ colors: ['#00BCD4', '#00363D'], overline: 'Tus días', content: <>
      {w.priciestWeekday && <>
        <Small>El día que más gastas</Small>
        <Big>{capitalize(WEEKDAYS[w.priciestWeekday[0] - 1])}</Big>
        <Body>{m.fmt(w.priciestWeekday[1])} en total los {WEEKDAYS[w.priciestWeekday[0] - 1]}</Body>
        <div style={{ height: 16 }} />
      </>}
      <Small>Días sin gastar nada</Small>
      <Big color={NEON}>{w.noSpendDays}</Big>
      {w.topDay && <Body>Tu día más caro: {shortDay(w.topDay[0].day, w.topDay[0].month)}, {m.fmt(w.topDay[1])}.</Body>}
    </> })
  }
  if (w.vsPrevPct != null) {
    const pct = w.vsPrevPct
    const less = pct <= 0
    stories.push({ colors: [less ? '#16A34A' : '#DC2626', '#111111'], overline: 'Frente al mes pasado', content: <>
      <Big color={less ? NEON : '#FF8A8A'}>{(pct > 0 ? '+' : '') + Math.trunc(pct)} %</Big>
      <Body>{(less ? 'Has gastado menos' : 'Has gastado más') + ` que en los mismos días del mes anterior (${m.fmt(w.prevSpent ?? 0)}).` + (less ? ' Bien hecho.' : '')}</Body>
    </> })
  }
  if (w.saved > 0) {
    stories.push({ colors: ['#0E9F6E', '#032B1D'], overline: 'Tu ahorro', content: <>
      <Big color={NEON}>{m.fmt(w.saved)}</Big>
      <Body>apartado para tu futuro este mes. La vaca está orgullosa. 🐄</Body>
    </> })
  }
  const total = stories.length + 1

  // ---------- Paginador (deslizar o tocar, como las historias) ----------
  const pager = useRef<HTMLDivElement>(null)
  const [page, setPage] = useState(0)
  const goTo = (i: number, smooth = true) => {
    const el = pager.current
    if (!el) return
    const t = Math.max(0, Math.min(total - 1, i))
    el.scrollTo({ left: t * el.clientWidth, behavior: smooth && !window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'auto' })
  }
  const changeMonth = (delta: number) => { setMonth((mm) => plusMonths(mm, delta)); goTo(0, false); setPage(0) }

  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const k = (e: KeyboardEvent) => {
      if (e.key === 'Escape') nav.back()
      else if (e.key === 'ArrowRight') goTo(page + 1)
      else if (e.key === 'ArrowLeft') goTo(page - 1)
    }
    window.addEventListener('keydown', k)
    return () => { document.body.style.overflow = prev; window.removeEventListener('keydown', k) }
  }) // eslint-disable-line react-hooks/exhaustive-deps

  return createPortal(
    <div className="pf-wr" role="dialog" aria-modal="true" aria-label="Tu Wrapped">
      <div className="pf-wr-pages" ref={pager} onScroll={(e) => {
        const el = e.currentTarget
        const i = Math.round(el.scrollLeft / Math.max(1, el.clientWidth))
        if (i !== page) setPage(i)
      }}>
        {stories.map((st, i) => (
          <div key={ymKey(month) + i} className="pf-wr-page" style={{ background: `linear-gradient(180deg, ${st.colors[0]}, ${st.colors[1]})` }}
            onClick={(e) => {
              const r = e.currentTarget.getBoundingClientRect()
              goTo(e.clientX - r.left < r.width / 3 ? i - 1 : i + 1)
            }}>
            <div className="pf-wr-story">
              <div className="pf-wr-over">{st.overline}</div>
              {st.content}
            </div>
          </div>
        ))}
        <div className="pf-wr-page" style={{ background: '#050505' }}>
          <ShareStory w={w} active={page === total - 1} />
        </div>
      </div>

      <div className="pf-wr-top">
        <div className="pf-wr-bars" aria-hidden="true">
          {Array.from({ length: total }, (_, i) => <span key={i} className={i <= page ? 'on' : ''} />)}
        </div>
        <div className="pf-wr-controls">
          <button className="pf-wr-arrow" aria-label="Mes anterior" onClick={() => changeMonth(-1)}><Icon name="chevronLeft" /></button>
          <span className="m">{mName.toUpperCase()}</span>
          <button className="pf-wr-arrow" aria-label="Mes siguiente" disabled={!canNext} onClick={() => changeMonth(1)}><Icon name="chevronRight" /></button>
          <button className="pf-wr-close" aria-label="Cerrar" onClick={nav.back}><Icon name="close" /></button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

/** Última tarjeta: la ficha del mes y el botón para compartirla como imagen. */
function ShareStory({ w, active }: { w: MonthWrapped; active: boolean }) {
  const m = useMoney()
  const amount = w.isCurrent ? m.fmt(w.bank) : m.signed(w.left)
  const income = m.fmt(w.income)
  const spent = m.fmt(w.spent)
  const [blob, setBlob] = useState<Blob | null>(null)
  // Se prepara la imagen antes del toque: así navigator.share se llama dentro del gesto (Safari lo exige).
  useEffect(() => {
    if (!active) return
    let alive = true
    setBlob(null)
    renderShareCard(w, { amount, income, spent }).then((b) => { if (alive) setBlob(b) }).catch(() => { if (alive) setBlob(null) })
    return () => { alive = false }
  }, [active, w, amount, income, spent])

  const share = async () => {
    const b = blob ?? await renderShareCard(w, { amount, income, spent })
    const file = new File([b], `ahorra-vaca-${ymKey(w.month)}.png`, { type: 'image/png' })
    const n = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
    if (n.canShare && n.canShare({ files: [file] })) {
      try { await n.share({ files: [file], title: 'Compartir mi mes' }); return } catch (e) {
        if ((e as DOMException)?.name === 'AbortError') return
      }
    }
    const url = URL.createObjectURL(file)
    const a = document.createElement('a')
    a.href = url
    a.download = file.name
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 4000)
    toast('Imagen descargada')
  }

  return (
    <div className="pf-wr-share">
      <div className="pf-wr-over" style={{ marginBottom: 16 }}>Tu ficha del mes</div>
      <MonthShareCard w={w} amount={amount} income={income} spent={spent} />
      <button className="pf-wr-share-btn" onClick={share}><Icon name="share" />Compartir</button>
      <div style={{ fontSize: 14, color: 'rgba(255,255,255,0.55)', textAlign: 'center', marginTop: 16, padding: '0 16px' }}>
        Se comparte como imagen. Nada sale de tu móvil hasta que tú eliges dónde.
      </div>
    </div>
  )
}

/** La ficha: tu dinero en el banco (o el balance de un mes cerrado), una barra por día y lo que entró y salió. */
function MonthShareCard({ w, amount, income, spent }: { w: MonthWrapped; amount: string; income: string; spent: string }) {
  const len = lengthOfMonth(w.month.year, w.month.month)
  const shown = w.isCurrent ? w.daysElapsed : len
  const max = Math.max(1, ...w.daily.slice(0, shown))
  return (
    <div className="pf-card">
      <div className="row" style={{ justifyContent: 'space-between', fontWeight: 700, letterSpacing: '0.06em' }}>
        <span className="f">‹  {MONTHS[w.month.month - 1].toUpperCase()} ⌄  ›</span>
        <span className="f" style={{ fontWeight: 600, letterSpacing: 0 }}>{w.isCurrent ? 'Hoy' : 'Último día'}</span>
      </div>
      <div className="f" style={{ marginTop: 16 }}>{w.isCurrent ? 'En el banco' : 'Balance del mes'}</div>
      <div className="pf-card-amount num">{amount}</div>
      <div className="pf-card-bars" aria-hidden="true">
        {Array.from({ length: len }, (_, i) => {
          const v = w.daily[i] ?? 0
          const last = i === shown - 1
          return (
            <div key={i}>
              {last && <b />}
              <i style={{ height: i < shown ? 6 + 54 * (v / max) : 4, background: last ? '#fff' : `rgba(255,255,255,${i < shown ? 0.42 : 0.12})` }} />
            </div>
          )
        })}
      </div>
      <div className="row" style={{ marginTop: 8, fontSize: 14, gap: 8 }}>
        <span className="f grow">{shortDay(1, w.month.month)}</span>
        {w.isCurrent && <span style={{ fontWeight: 700 }}>Hoy · {today().day}</span>}
        <span className="f">{shortDay(len, w.month.month)}</span>
      </div>
      <div className="pf-card-foot">
        <span className="f small">Ingresos</span>
        <span className="num" style={{ fontWeight: 700, color: NEON, flex: 1 }}>+{income}</span>
        <span className="f small">Gastos</span>
        <span className="num" style={{ fontWeight: 700 }}>−{spent}</span>
      </div>
      <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.4)', marginTop: 12 }}>Ahorra, vaca 🐄</div>
    </div>
  )
}
