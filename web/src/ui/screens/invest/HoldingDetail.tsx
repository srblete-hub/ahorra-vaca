// Ficha de una inversión (HoldingDetailScreen de InvestmentsScreen.kt): precio, gráfico de línea o velas,
// máximos y mínimos, estadísticas, por qué se habla, dónde comprar y tu posición. Deslizar para refrescar.
import { useCallback, useEffect, useId, useRef, useState, type ReactNode, type RefObject } from 'react'
import { useDB, useSettings } from '../../../data/store'
import { reloadAsset, useAssetHistory, useCoinStats, useExchanges, useOhlc, type Resource } from '../../../data/services/actions'
import type { Candle } from '../../../data/services/prices'
import { Button, Card, IconButton, Initials, Overlay, useMoney } from '../../components'
import { useNav, type OverlayRoute } from '../../nav'
import { BuzzCard, buzzSession } from './buzz'
import {
  ChangeChip, ON_PASTEL, RetryNote, changeOf, compact, fixed, gainColor, num0, openUrl, pastelFor, quantity, signedPct, symbolColor,
  usd, usdSigned, usePositions, watchPosition, type Position,
} from './shared'

const RANGES = [
  { label: '24 h', days: 1 }, { label: '7 días', days: 7 }, { label: '1 mes', days: 30 }, { label: '3 meses', days: 90 }, { label: '1 año', days: 365 },
] as const
const CHART_H = 232
const MONTHS = ['ene.', 'feb.', 'mar.', 'abr.', 'may.', 'jun.', 'jul.', 'ago.', 'sept.', 'oct.', 'nov.', 'dic.']

function scrubDate(ms: number, days: number): string {
  const d = new Date(ms)
  const base = `${d.getDate()} ${MONTHS[d.getMonth()]}`
  return days <= 7 ? `${base}, ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` : `${base} ${d.getFullYear()}`
}

/** Busca la posición de la ruta: las tuyas primero; si no, la que se mira sin tenerla. */
function useRoutePosition(key: string): Position | null {
  const all = usePositions(null)
  const crypto = useDB((s) => s.crypto)
  const stocks = useDB((s) => s.stocks)
  // Compatibilidad con claves de Android basadas en el id de la base de datos ("c:12" / "s:3").
  let k = key
  const m = /^([cs]):(\d+)$/.exec(key)
  if (m) {
    const id = Number(m[2])
    if (m[1] === 'c') { const h = crypto.find((x) => x.id === id); if (h) k = `c:${h.coinId}` }
    else { const h = stocks.find((x) => x.id === id); if (h) k = `s:${h.ticker}` }
  }
  const base = k.split('|')[0]
  return all.find((p) => p.key === base || (k.startsWith('s:') && !p.isCrypto && p.symbol.toUpperCase() === base.slice(2).toUpperCase())) ?? watchPosition(k)
}

export function HoldingDetailScreen({ route }: { route: OverlayRoute }) {
  const nav = useNav()
  const key = route.name === 'holding' ? route.key : ''
  const p = useRoutePosition(key)
  if (!p) {
    return (
      <Overlay title="Inversión" onClose={nav.back}>
        <Card><div className="muted">No encuentro esta inversión. Puede que la hayas borrado.</div></Card>
      </Overlay>
    )
  }
  return <Detail p={p} />
}

function Detail({ p }: { p: Position }) {
  const nav = useNav()
  const money = useMoney()
  const settings = useSettings()
  // Cripto: el precio de la moneda se ve en dólares; tu posición, en euros.
  const cur = p.isCrypto ? 'usd' : undefined
  const fmtPrice = p.isCrypto ? usd : money.fmt
  const fmtSigned = p.isCrypto ? usdSigned : money.signed
  const [range, setRange] = useState<number>(30)
  const [candles, setCandles] = useState(false)
  const [scrub, setScrub] = useState<number | null>(null)

  const day1 = useAssetHistory(p.symbol, p.isCrypto, p.apiId, 1, cur)
  const lineRes = useAssetHistory(p.symbol, p.isCrypto, p.apiId, range, cur)
  const [ohlc, setOhlc] = useState<{ k: string; r: Resource<Candle[]> } | null>(null)
  const onOhlc = useCallback((k: string, r: Resource<Candle[]>) => setOhlc({ k, r }), [])
  const ohlcKey = `${range}`
  const barsRes = candles && ohlc?.k === ohlcKey ? ohlc.r : null

  const line = lineRes.data?.values
  const times = lineRes.data?.times
  const bars = barsRes?.data
  const dayLast = day1.data?.values.length ? day1.data.values[day1.data.values.length - 1] : undefined
  const lineLast = line?.length ? line[line.length - 1] : undefined
  const priceNow = p.isCrypto ? dayLast ?? lineLast ?? 0 : p.price > 0 ? p.price : dayLast ?? lineLast ?? 0
  const values = candles ? bars?.flatMap((c) => [c.high, c.low]) : line
  const change = line && line.length >= 2 ? line[line.length - 1] - line[0] : bars && bars.length ? bars[bars.length - 1].close - bars[0].open : null
  const base = line?.[0] ?? bars?.[0]?.open
  const active = candles ? barsRes : lineRes
  const dayChange = settings.dayChanges[p.symbol] ?? changeOf(day1.data?.values)

  // ---------- Deslizar hacia abajo para refrescar ----------
  const [reloading, setReloading] = useState(false)
  const reload = useCallback(() => {
    reloadAsset(p.symbol, p.apiId, p.isCrypto)
    setReloading(true)
    window.setTimeout(() => setReloading(false), 1200)
  }, [p.symbol, p.apiId, p.isCrypto])
  const pull = usePullToRefresh(reload)

  const scrubPrice = scrub != null ? line?.[scrub] : undefined
  const scrubTime = scrub != null ? times?.[scrub] : undefined
  const hi = values?.length ? Math.max(...values) : null
  const lo = values?.length ? Math.min(...values) : null

  return (
    <Overlay
      onClose={nav.back}
      title={(
        <div className="row inv-head" style={{ gap: 8, minWidth: 0 }}>
          <Initials text={p.symbol} color={symbolColor(p.symbol)} size={40} />
          <div style={{ minWidth: 0 }}>
            <div className="ellipsis" style={{ fontSize: 20, fontWeight: 700 }}>{p.name}</div>
            <div className="small muted" style={{ fontWeight: 400 }}>{p.symbol}</div>
          </div>
        </div>
      )}
      actions={(
        <div className="row" style={{ gap: 8 }}>
          <IconButton icon="refresh" label="Actualizar" onClick={reload} filled />
          <IconButton icon="edit" label={`Editar ${p.name}`} onClick={() => nav.edit(p.edit)} filled />
        </div>
      )}
    >
      {candles && <OhlcProbe k={ohlcKey} symbol={p.symbol} isCrypto={p.isCrypto} id={p.apiId} days={range} cur={cur} onData={onOhlc} />}
      <div ref={pull.ref} className="col" style={{ gap: 16 }}>
        <div className="inv-pull" style={{ height: reloading ? 40 : pull.offset, opacity: reloading ? 1 : Math.min(1, pull.offset / 72) }} aria-hidden={!reloading}>
          <span className={`inv-spinner ${reloading ? '' : 'still'}`} style={{ transform: reloading ? undefined : `rotate(${pull.offset * 4}deg)` }} />
        </div>

        {/* ---------- Precio y gráfico, sobre su color ---------- */}
        <div className="inv-hero" style={{ background: pastelFor(p.symbol), color: ON_PASTEL }}>
          <div style={{ opacity: 0.7 }}>
            {scrubPrice != null && scrubTime != null && scrubTime > 0 ? `Precio el ${scrubDate(scrubTime, range)}` : `Precio de ${p.name}`}
          </div>
          <div className="inv-price-row">
            <span className="num inv-price">{priceNow > 0 || scrubPrice != null ? fmtPrice(scrubPrice ?? priceNow) : '…'}</span>
            {change != null && base != null && base > 0 && (
              <span className="small" style={{ fontWeight: 500, paddingBottom: 6 }}>
                {change >= 0 ? '↗' : '↘'} {fmtSigned(change)} ({signedPct((change / base) * 100)})
              </span>
            )}
          </div>
          <div className="row" style={{ gap: 8, marginTop: 16 }}>
            <button className={`inv-toggle ${!candles ? 'on' : ''}`} onClick={() => setCandles(false)}>Línea</button>
            <button className={`inv-toggle ${candles ? 'on' : ''}`} onClick={() => { setScrub(null); setCandles(true) }}>Velas</button>
          </div>
          <div style={{ marginTop: 8 }}>
            {!values || values.length < 2
              ? (
                <div className="inv-chart-msg" style={{ height: CHART_H }}>
                  {active?.loading || !active
                    ? <span className="inv-spinner dark" aria-label="Cargando…" />
                    : (
                      <div className="col" style={{ gap: 8, alignItems: 'center', textAlign: 'center' }}>
                        <span style={{ opacity: 0.7 }}>{active.error ? `${active.error}. ` : ''}Sin datos ahora mismo (¿sin conexión?)</span>
                        <button className="inv-retry-btn dark" onClick={active.reload}>Reintentar</button>
                      </div>
                    )}
                </div>
              )
              : <PriceChart values={values} current={priceNow > 0 ? priceNow : values[values.length - 1]} bars={candles ? bars : undefined} scrub={candles ? null : scrub} onScrub={candles ? undefined : setScrub} />}
          </div>
          <div className="inv-ranges" role="tablist">
            {RANGES.map((r) => (
              <button key={r.days} role="tab" aria-selected={r.days === range} className={r.days === range ? 'on' : ''} onClick={() => { setScrub(null); setRange(r.days) }}>{r.label}</button>
            ))}
          </div>
        </div>

        {/* ---------- Máximo, mínimo y cambio ---------- */}
        <div className="inv-stats3">
          <Stat label="Máximo" value={hi != null ? fmtPrice(hi) : '—'} />
          <Stat label="Mínimo" value={lo != null ? fmtPrice(lo) : '—'} />
          <Stat label="24 h" value={dayChange != null ? signedPct(dayChange) : '—'} color={dayChange != null ? gainColor(dayChange) : undefined} />
        </div>

        {p.isCrypto && <StatsCard id={p.apiId} />}
        {p.isCrypto && buzzSession.requested && <BuzzCard coinId={p.apiId} symbol={p.symbol} />}
        {p.isCrypto && <ExchangesCard id={p.apiId} />}

        {/* ---------- Tu posición ---------- */}
        {p.qty <= 0 ? (
          <Card>
            <div className="small muted">No la tienes</div>
            <div style={{ marginTop: 4 }}>Solo estás mirando el precio. Si la compras en tu bróker, añádela para seguirla aquí.</div>
            <div style={{ marginTop: 16 }}>
              <Button onClick={() => nav.edit(p.edit)} style={{ width: '100%' }}>{p.isCrypto ? 'Añadir a mis criptos' : 'Añadir a mis acciones'}</Button>
            </div>
            <div className="tiny muted" style={{ marginTop: 8 }}>Desde aquí no se compra ni se vende.</div>
          </Card>
        ) : (
          <Card>
            <div className="small muted">{p.isCrypto ? 'Lo tuyo (en euros)' : 'Tu posición'}</div>
            <div className="row" style={{ gap: 8, alignItems: 'flex-end' }}>
              <div className="grow num" style={{ fontSize: 28, fontWeight: 700, minWidth: 0, overflowWrap: 'anywhere' }}>{money.fmt(p.value)}</div>
              {p.cost > 0 && <ChangeChip pct={(p.pnl / p.cost) * 100} />}
            </div>
            <div style={{ marginTop: 8 }}>
              <PosLine label="Cantidad" value={`${quantity(p.qty)} ${p.symbol}`} />
              {p.cost > 0 ? (
                <>
                  <PosLine label="Invertido" value={money.fmt(p.cost)} />
                  <PosLine label="Precio medio de compra" value={money.fmt(p.avg)} />
                  <PosLine label={p.pnl >= 0 ? 'Ganas' : 'Pierdes'} value={money.fmt(Math.abs(p.pnl))} color={gainColor(p.pnl)} />
                </>
              ) : <div className="small muted" style={{ marginTop: 4 }}>Pon lo que metiste (Editar) para ver cuánto ganas o pierdes.</div>}
            </div>
            <div className="tiny muted" style={{ marginTop: 8 }}>Solo para seguir tu inversión: desde aquí no se compra ni se vende.</div>
          </Card>
        )}
      </div>
    </Overlay>
  )
}

/** Pide las velas y se las pasa a la ficha (solo se monta en modo "Velas"). */
function OhlcProbe({ k, symbol, isCrypto, id, days, cur, onData }: { k: string; symbol: string; isCrypto: boolean; id: string; days: number; cur?: string; onData: (k: string, r: Resource<Candle[]>) => void }) {
  const r = useOhlc(symbol, isCrypto, id, days, cur)
  useEffect(() => { onData(k, r) }, [k, r.data, r.loading, r.error]) // eslint-disable-line react-hooks/exhaustive-deps
  return null
}

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="inv-stat">
      <div className="tiny muted">{label}</div>
      <div className="small num ellipsis" style={{ fontWeight: 600, color: color ?? 'var(--ink)' }}>{value}</div>
    </div>
  )
}

function PosLine({ label, value, color }: { label: string; value: ReactNode; color?: string }) {
  return (
    <div className="inv-posline">
      <span className="muted">{label}</span>
      <span className="num" style={{ fontWeight: 500, color: color ?? 'var(--ink)' }}>{value}</span>
    </div>
  )
}

function big(v: number | null): string {
  if (v == null) return '—'
  if (v >= 1e12) return '$' + fixed(v / 1e12, 2) + ' bill.'
  if (v >= 1e9) return '$' + fixed(v / 1e9, 2) + ' mil M'
  if (v >= 1e6) return '$' + fixed(v / 1e6, 1) + ' M'
  return usd(v)
}
function units(v: number | null): string {
  if (v == null) return '—'
  if (v >= 1e9) return fixed(v / 1e9, 2) + ' mil M'
  if (v >= 1e6) return fixed(v / 1e6, 1) + ' M'
  return num0(v)
}

/** Estadísticas de CoinGecko, en dólares. */
function StatsCard({ id }: { id: string }) {
  const r = useCoinStats(id, 'usd')
  const st = r.data
  const pctLine = (label: string, v: number | null) => <PosLine label={label} value={v != null ? signedPct(v) : '—'} color={v != null ? gainColor(v) : undefined} />
  return (
    <Card>
      <div className="inv-card-title">Estadísticas</div>
      <div className="tiny muted">Datos de CoinGecko, en dólares</div>
      <div style={{ marginTop: 8 }}>
        {!st
          ? r.error && !r.loading ? <RetryNote text={r.error} onRetry={r.reload} /> : <div className="small muted">Cargando… (necesita conexión)</div>
          : (
            <>
              <PosLine label="Puesto por capitalización" value={st.rank != null ? `#${st.rank}` : '—'} />
              <PosLine label="Capitalización" value={big(st.marketCap)} />
              <PosLine label="Volumen 24 h" value={big(st.volume24h)} />
              <PosLine label="Máximo / mínimo 24 h" value={`${st.high24h != null ? usd(st.high24h) : '—'} / ${st.low24h != null ? usd(st.low24h) : '—'}`} />
              <PosLine label="Máximo histórico" value={(st.ath != null ? usd(st.ath) : '—') + (st.athChangePct != null ? ` (${signedPct(st.athChangePct)})` : '')} />
              {pctLine('Cambio 7 días', st.change7d)}
              {pctLine('Cambio 30 días', st.change30d)}
              {pctLine('Cambio 1 año', st.change1y)}
              <PosLine label="En circulación" value={units(st.circulating) + (st.maxSupply != null ? ` de ${units(st.maxSupply)}` : '')} />
            </>
          )}
      </div>
    </Card>
  )
}

/** Dónde se puede comprar (exchanges con más volumen, según CoinGecko). Solo enlaces: la app no compra nada. */
function ExchangesCard({ id }: { id: string }) {
  const r = useExchanges(id)
  const list = r.data
  return (
    <Card>
      <div className="inv-card-title">Dónde se puede comprar</div>
      <div className="tiny muted">Exchanges con más volumen, según CoinGecko</div>
      <div style={{ marginTop: 8 }}>
        {!list
          ? r.error && !r.loading ? <RetryNote text={r.error} onRetry={r.reload} /> : <div className="small muted">Cargando… (necesita conexión)</div>
          : list.length === 0
            ? <div className="small muted">No he encontrado dónde se negocia ahora mismo.</div>
            : list.map((m) => (
              <button key={m.exchange + m.pair} className="inv-xrow" disabled={!m.url} onClick={() => m.url && openUrl(m.url)}>
                <Initials text={m.exchange.slice(0, 2)} color={symbolColor(m.exchange)} size={32} />
                <div className="grow" style={{ minWidth: 0, textAlign: 'left' }}>
                  <div className="ellipsis" style={{ fontWeight: 500 }}>{m.exchange}</div>
                  <div className="tiny ellipsis" style={{ color: m.trusted ? 'var(--gain)' : 'var(--muted)' }}>{m.pair}{m.trusted ? ' · confianza alta' : ''}</div>
                </div>
                {m.url && <span className="small" style={{ color: 'var(--gain)', flexShrink: 0 }}>Abrir ›</span>}
              </button>
            ))}
      </div>
      <div className="tiny muted" style={{ marginTop: 8 }}>
        Antes de abrir cuenta, comprueba que el exchange puede operar en España (registro de la CNMV / MiCA). Esta app no compra ni vende nada.
      </div>
    </Card>
  )
}

// ---------- Gráfico ----------

/**
 * Ejes con precios a la izquierda y una línea discontinua con el precio actual; dentro, la línea (con
 * "mantén pulsado y desliza" para ver el precio de cada momento) o las velas.
 */
function PriceChart({ values, current, bars, scrub, onScrub }: { values: number[]; current: number; bars?: Candle[]; scrub: number | null; onScrub?: (i: number | null) => void }) {
  const gid = useId()
  const box = useRef<HTMLDivElement>(null)
  const [w, setW] = useState(280)
  useEffect(() => {
    const el = box.current
    if (!el) return
    const ro = new ResizeObserver(() => setW(Math.max(40, el.clientWidth)))
    ro.observe(el)
    setW(Math.max(40, el.clientWidth))
    return () => ro.disconnect()
  }, [])
  useScrub(box, values.length, onScrub)

  const min0 = Math.min(...values, current), max0 = Math.max(...values, current)
  const pad = ((max0 - min0) || max0 * 0.02) * 0.08
  const min = min0 - pad, max = max0 + pad
  const labels = [0, 1, 2, 3, 4].map((i) => min + ((max - min) * i) / 4)
  const top = 8, bottom = CHART_H - 8
  const y = (v: number) => bottom - (bottom - top) * ((v - min) / (max - min || 1))

  let content: ReactNode
  if (bars) {
    const slot = w / bars.length
    const body = Math.max(2, slot * 0.6)
    content = bars.map((c, i) => {
      const x = slot * i + slot / 2
      const color = c.close >= c.open ? '#6F5BEA' : '#D08A57'
      const t = y(Math.max(c.open, c.close)), b = y(Math.min(c.open, c.close))
      return (
        <g key={i}>
          <line x1={x} x2={x} y1={y(c.high)} y2={y(c.low)} stroke={color} strokeWidth={1.5} />
          <rect x={x - body / 2} y={t} width={body} height={Math.max(2, b - t)} fill={color} />
        </g>
      )
    })
  } else {
    const step = w / (values.length - 1)
    const d = values.map((v, i) => `${i === 0 ? 'M' : 'L'}${(i * step).toFixed(1)},${y(v).toFixed(1)}`).join(' ')
    content = (
      <>
        <defs>
          <linearGradient id={gid} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" stopColor="#fff" stopOpacity="0.45" />
            <stop offset="1" stopColor="#fff" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={`${d} L${w},${bottom} L0,${bottom} Z`} fill={`url(#${gid})`} />
        <path d={d} fill="none" stroke="#fff" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
        {scrub != null && scrub < values.length && (
          <>
            <line x1={scrub * step} x2={scrub * step} y1={top} y2={bottom} stroke={ON_PASTEL} strokeOpacity={0.6} strokeWidth={1.5} />
            <circle cx={scrub * step} cy={y(values[scrub])} r={8} fill="#fff" />
            <circle cx={scrub * step} cy={y(values[scrub])} r={4} fill={ON_PASTEL} />
          </>
        )}
      </>
    )
  }
  return (
    <div className="inv-chart" style={{ height: CHART_H }}>
      <div className="inv-axis">{[...labels].reverse().map((v, i) => <span key={i}>{compact(v)}</span>)}</div>
      <div ref={box} className="inv-plot" aria-label={bars ? 'Gráfico de velas' : 'Gráfico de precio. Mantén pulsado y desliza para ver cada momento.'} role="img">
        <svg width={w} height={CHART_H} style={{ display: 'block', overflow: 'visible' }}>
          {labels.map((v, i) => <line key={i} x1={0} x2={w} y1={y(v)} y2={y(v)} stroke={ON_PASTEL} strokeOpacity={0.08} />)}
          {content}
          <line x1={0} x2={w} y1={y(current)} y2={y(current)} stroke={ON_PASTEL} strokeOpacity={0.7} strokeDasharray="6 5" />
        </svg>
      </div>
    </div>
  )
}

/** Mantén pulsado (o arrastra con el ratón) y desliza: devuelve el índice del punto señalado; al soltar, null. */
function useScrub(box: RefObject<HTMLDivElement | null>, n: number, onScrub?: (i: number | null) => void) {
  const cb = useRef(onScrub)
  cb.current = onScrub
  useEffect(() => {
    const el = box.current
    if (!el || !onScrub || n < 2) return
    let timer = 0, active = false, sx = 0, sy = 0
    const index = (x: number) => {
      const r = el.getBoundingClientRect()
      return Math.max(0, Math.min(n - 1, Math.round(((x - r.left) / r.width) * (n - 1))))
    }
    const stop = () => { window.clearTimeout(timer); if (active) cb.current?.(null); active = false }
    const ts = (e: TouchEvent) => {
      const t = e.touches[0]; sx = t.clientX; sy = t.clientY
      timer = window.setTimeout(() => { active = true; cb.current?.(index(sx)) }, 300)
    }
    const tm = (e: TouchEvent) => {
      const t = e.touches[0]
      if (active) { e.preventDefault(); cb.current?.(index(t.clientX)) }
      else if (Math.hypot(t.clientX - sx, t.clientY - sy) > 8) window.clearTimeout(timer)
    }
    const md = (e: PointerEvent) => { if (e.pointerType !== 'mouse') return; active = true; cb.current?.(index(e.clientX)) }
    const mm = (e: PointerEvent) => { if (e.pointerType === 'mouse' && active) cb.current?.(index(e.clientX)) }
    const mu = (e: PointerEvent) => { if (e.pointerType === 'mouse') stop() }
    el.addEventListener('touchstart', ts, { passive: true })
    el.addEventListener('touchmove', tm, { passive: false })
    el.addEventListener('touchend', stop)
    el.addEventListener('touchcancel', stop)
    el.addEventListener('pointerdown', md)
    window.addEventListener('pointermove', mm)
    window.addEventListener('pointerup', mu)
    return () => {
      window.clearTimeout(timer)
      el.removeEventListener('touchstart', ts)
      el.removeEventListener('touchmove', tm)
      el.removeEventListener('touchend', stop)
      el.removeEventListener('touchcancel', stop)
      el.removeEventListener('pointerdown', md)
      window.removeEventListener('pointermove', mm)
      window.removeEventListener('pointerup', mu)
    }
  }, [box, n, !!onScrub]) // eslint-disable-line react-hooks/exhaustive-deps
}

/** Deslizar hacia abajo desde arriba del todo (dentro de la pantalla superpuesta) llama a [onRefresh]. */
function usePullToRefresh(onRefresh: () => void) {
  const ref = useRef<HTMLDivElement>(null)
  const [offset, setOffset] = useState(0)
  const cb = useRef(onRefresh)
  cb.current = onRefresh
  useEffect(() => {
    const el = ref.current
    const scroller = el?.closest('.overlay') as HTMLElement | null
    if (!el || !scroller) return
    let startY: number | null = null, dy = 0
    const ts = (e: TouchEvent) => {
      // No empieza dentro del gráfico (allí mantener pulsado sirve para ver precios).
      if ((e.target as HTMLElement).closest('.inv-plot')) { startY = null; return }
      startY = scroller.scrollTop <= 0 ? e.touches[0].clientY : null; dy = 0
    }
    const tm = (e: TouchEvent) => {
      if (startY == null) return
      if (scroller.scrollTop > 0) { startY = null; setOffset(0); return }
      dy = Math.max(0, e.touches[0].clientY - startY)
      setOffset(Math.min(72, dy / 2))
    }
    const te = () => {
      if (startY != null && dy / 2 >= 64) cb.current()
      startY = null; dy = 0; setOffset(0)
    }
    el.addEventListener('touchstart', ts, { passive: true })
    el.addEventListener('touchmove', tm, { passive: true })
    el.addEventListener('touchend', te)
    el.addEventListener('touchcancel', te)
    return () => {
      el.removeEventListener('touchstart', ts)
      el.removeEventListener('touchmove', tm)
      el.removeEventListener('touchend', te)
      el.removeEventListener('touchcancel', te)
    }
  }, [])
  return { ref, offset }
}
