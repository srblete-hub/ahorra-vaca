// Piezas comunes de Inversión, ficha y noticias (port de la parte de arriba de InvestmentsScreen.kt).
import { useEffect, useId, useMemo } from 'react'
import type { CryptoHolding, StockHolding } from '../../../data/models'
import { db, getState, useDB } from '../../../data/store'
import { num, usd } from '../../../data/format'
import { useAssetHistory, type History } from '../../../data/services/actions'
import { Icon } from '../../icons'
import type { SheetRoute } from '../../nav'
import './invest.css'

export { usd }

/** Tonos pastel de las tarjetas (lila, menta, melocotón…), con texto oscuro. */
export const PASTELS = ['#B9B1F6', '#A9DCC7', '#F6CBA8', '#B4D5F6', '#F4B9CB']
export const ON_PASTEL = '#15151C'
/** Azul de "sube" en el mercado (como Android). */
export const UP_BLUE = '#3B82F6'

/** String.hashCode de Kotlin (con signo). */
export function ktHash(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0
  return h
}
const floorMod = (a: number, n: number) => ((a % n) + n) % n

/** Colores de marca conocidos; el resto, un tono estable por símbolo. */
export function symbolColor(symbol: string): string {
  switch (symbol.toUpperCase()) {
    case 'BTC': return '#F7931A'
    case 'ETH': return '#627EEA'
    case 'SOL': return '#9945FF'
    case 'ADA': return '#0033AD'
    case 'XRP': return '#23292F'
    case 'DOGE': return '#C2A633'
    case 'USDT': case 'USDC': return '#26A17B'
    case 'AAPL': return '#1C1C1E'
  }
  return ['#1E9D57', '#3B82F6', '#8B5CF6', '#EF6C4A', '#14B8A6', '#E5578A'][floorMod(ktHash(symbol), 6)]
}
export const pastelFor = (symbol: string) => PASTELS[floorMod(ktHash(symbol), PASTELS.length)]

/** Acciones conocidas para mirar precios aunque no tengas ninguna. Solo para ver: no se compra nada. */
export const MARKET: Array<[ticker: string, name: string]> = [
  ['AAPL', 'Apple'], ['MSFT', 'Microsoft'], ['NVDA', 'Nvidia'], ['AMZN', 'Amazon'], ['GOOGL', 'Alphabet (Google)'],
  ['META', 'Meta'], ['TSLA', 'Tesla'], ['NFLX', 'Netflix'], ['MCD', "McDonald's"], ['DIS', 'Walt Disney'],
  ['KO', 'Coca-Cola'], ['SAN.MC', 'Banco Santander'], ['ITX.MC', 'Inditex'], ['IBE.MC', 'Iberdrola'], ['BBVA.MC', 'BBVA'],
]

/** Criptos conocidas (id de CoinGecko, símbolo, nombre) para mirar aunque no tengas. */
export const CRYPTO_MARKET: Array<[id: string, symbol: string, name: string]> = [
  ['bitcoin', 'BTC', 'Bitcoin'], ['ethereum', 'ETH', 'Ethereum'], ['solana', 'SOL', 'Solana'],
  ['ripple', 'XRP', 'XRP'], ['binancecoin', 'BNB', 'BNB'], ['dogecoin', 'DOGE', 'Dogecoin'],
  ['cardano', 'ADA', 'Cardano'], ['tron', 'TRX', 'TRON'], ['chainlink', 'LINK', 'Chainlink'],
  ['avalanche-2', 'AVAX', 'Avalanche'], ['polkadot', 'DOT', 'Polkadot'], ['litecoin', 'LTC', 'Litecoin'],
  ['sui', 'SUI', 'Sui'], ['shiba-inu', 'SHIB', 'Shiba Inu'], ['pepe', 'PEPE', 'Pepe'],
]
export const CRYPTO_MARKET_IDS = CRYPTO_MARKET.map((c) => c[0])

// ---------- Formato (igual que Kotlin) ----------

const fixedFmt = new Map<number, Intl.NumberFormat>()
/** "%.Nf" en español, sin separador de miles. */
export function fixed(v: number, digits: number): string {
  let f = fixedFmt.get(digits)
  if (!f) { f = new Intl.NumberFormat('es-ES', { minimumFractionDigits: digits, maximumFractionDigits: digits, useGrouping: false }); fixedFmt.set(digits, f) }
  return f.format(Number.isFinite(v) ? v : 0)
}
/** pct de Components.kt: "4,2 %". */
export const pct1 = (v: number) => fixed(v, 1) + ' %'
/** signedPct de Components.kt: "+1,3 %" / "-4,2 %". */
export const signedPct = (v: number) => (v > 0 ? '+' : '') + pct1(v)
export const usdSigned = (v: number) => (v >= 0 ? '+' : '−') + usd(Math.abs(v))
/** quantity de Components.kt: hasta 8 decimales, con miles. */
export const quantity = (v: number) => num(v, 8)
/** "%,.0f": sin decimales, con miles. */
export const num0 = (v: number) => num(v, 0)
/** Etiquetas del eje del gráfico. */
export function compact(v: number): string {
  if (v >= 10_000) return num(v, 0)
  if (v >= 100) return fixed(v, 0)
  if (v >= 1) return fixed(v, 2)
  return fixed(v, 4)
}

export const gainColor = (v: number) => (v > 0.004 ? 'var(--gain)' : v < -0.004 ? 'var(--loss)' : 'var(--muted)')

// ---------- Posiciones ----------

/** Una posición, cripto o acción, con lo necesario para pintarla. */
export interface Position {
  key: string
  isCrypto: boolean
  /** id de CoinGecko o ticker de Yahoo. */
  apiId: string
  symbol: string
  name: string
  qty: number
  avg: number
  /** Precio en la moneda de la app (0 = sin precio). */
  price: number
  edit: SheetRoute
  value: number
  cost: number
  pnl: number
}

function makePosition(p: Omit<Position, 'value' | 'cost' | 'pnl'>): Position {
  const value = p.qty * p.price, cost = p.qty * p.avg
  return { ...p, value, cost, pnl: value - cost }
}

/**
 * Agrupa varias compras de la misma moneda / acción en una sola posición (precio medio ponderado). La clave
 * es "c:<coinId>" o "s:<ticker>", como espera la ruta { name: 'holding' }.
 */
function cryptoPositions(list: CryptoHolding[]): Position[] {
  const by = new Map<string, CryptoHolding[]>()
  for (const h of list) by.set(h.coinId, [...(by.get(h.coinId) ?? []), h])
  return [...by.entries()].map(([coinId, hs]) => {
    const qty = hs.reduce((a, h) => a + h.amount, 0)
    const cost = hs.reduce((a, h) => a + h.amount * h.avgBuyPrice, 0)
    const first = hs[0]
    return makePosition({
      key: `c:${coinId}`, isCrypto: true, apiId: coinId, symbol: first.symbol, name: first.name || first.symbol,
      qty, avg: qty > 0 ? cost / qty : 0, price: hs.find((h) => h.price > 0)?.price ?? 0, edit: { name: 'crypto', holding: first },
    })
  })
}
function stockPositions(list: StockHolding[]): Position[] {
  const by = new Map<string, StockHolding[]>()
  for (const h of list) by.set(h.ticker.toUpperCase(), [...(by.get(h.ticker.toUpperCase()) ?? []), h])
  return [...by.values()].map((hs) => {
    const qty = hs.reduce((a, h) => a + h.shares, 0)
    const cost = hs.reduce((a, h) => a + h.shares * h.avgBuyPrice, 0)
    const first = hs[0]
    return makePosition({
      key: `s:${first.ticker}`, isCrypto: false, apiId: first.ticker, symbol: first.ticker, name: first.name.trim() || first.ticker,
      qty, avg: qty > 0 ? cost / qty : 0, price: hs.find((h) => h.price > 0)?.price ?? 0, edit: { name: 'stock', holding: first },
    })
  })
}

/** Tus posiciones de un tipo (o todas), de más a menos valor. */
export function usePositions(kind: 'CRYPTO' | 'STOCKS' | null): Position[] {
  const crypto = useDB((s) => s.crypto)
  const stocks = useDB((s) => s.stocks)
  return useMemo(() => {
    const c = kind === 'STOCKS' ? [] : cryptoPositions(crypto)
    const s = kind === 'CRYPTO' ? [] : stockPositions(stocks)
    return [...c, ...s].sort((a, b) => b.value - a.value)
  }, [crypto, stocks, kind])
}

/**
 * Lo que se mira sin tenerlo. Acepta "c:<coinId>" (con "|SÍMBOLO|Nombre" opcional para monedas fuera de la
 * lista), "s:<ticker>" y las claves de Android "wc:…" / "w:…".
 */
export function watchPosition(key: string): Position | null {
  const crypto = key.startsWith('wc:') ? key.slice(3) : key.startsWith('c:') ? key.slice(2) : null
  if (crypto != null) {
    const [id, sym0, name0] = crypto.split('|')
    if (!id) return null
    const known = CRYPTO_MARKET.find((c) => c[0] === id)
    const sym = known?.[1] ?? sym0 ?? id.toUpperCase()
    const name = known?.[2] ?? name0 ?? id.charAt(0).toUpperCase() + id.slice(1)
    return makePosition({
      key, isCrypto: true, apiId: id, symbol: sym, name, qty: 0, avg: 0, price: 0,
      edit: { name: 'crypto', holding: { coinId: id, symbol: sym, name, amount: 0, avgBuyPrice: 0 } },
    })
  }
  const t = key.startsWith('w:') ? key.slice(2) : key.startsWith('s:') ? key.slice(2) : null
  if (!t) return null
  const name = MARKET.find((m) => m[0].toUpperCase() === t.toUpperCase())?.[1] ?? t
  return makePosition({
    key, isCrypto: false, apiId: t, symbol: t, name, qty: 0, avg: 0, price: 0,
    edit: { name: 'stock', holding: { ticker: t, name, shares: 0, avgBuyPrice: 0 } },
  })
}

/** Guarda lo que acabas de mirar ("Vistas recientemente"), como markViewed del ViewModel. */
export function markViewed(id: string) {
  const prev = getState().settings.recentTickers
  db.setSettings({ recentTickers: [id, ...prev.filter((x) => x !== id)].slice(0, 8) })
}

// ---------- Historiales de varios activos a la vez ----------

export interface HistState { values: number[]; loading: boolean; error: string | null; reload: () => void }

/**
 * Pide el historial de un activo y se lo pasa al padre (así una pantalla puede juntar varios sin romper las
 * reglas de los ganchos). No pinta nada.
 */
export function HistProbe({ id, symbol, isCrypto, days, onData }: { id: string; symbol: string; isCrypto: boolean; days: number; onData: (symbol: string, s: HistState) => void }) {
  const r = useAssetHistory(symbol, isCrypto, id, days)
  useEffect(() => {
    onData(symbol, { values: (r.data as History | undefined)?.values ?? [], loading: r.loading, error: r.error, reload: r.reload })
  }, [r.data, r.loading, r.error, r.reload, symbol, onData])
  return null
}

// ---------- Minigráficos y chips ----------

/** Minigráfico sin ejes (para tarjetas y filas). */
export function Spark({ values, color, width, height, fill = false, strokeWidth = 2 }: { values: number[]; color: string; width?: number | string; height: number; fill?: boolean; strokeWidth?: number }) {
  const id = useId()
  if (values.length < 2) return <div style={{ width, height, flexShrink: 0 }} />
  const W = 100, H = 100
  const min = Math.min(...values), max = Math.max(...values), range = max - min || 1
  const pts = values.map((v, i) => `${((i / (values.length - 1)) * W).toFixed(2)},${(H * (1 - (v - min) / range)).toFixed(2)}`)
  const line = 'M' + pts.join(' L')
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" width={width ?? '100%'} height={height} style={{ display: 'block', overflow: 'visible', flexShrink: 0 }} aria-hidden="true">
      {fill && (
        <>
          <defs>
            <linearGradient id={id} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor={color} stopOpacity="0.35" />
              <stop offset="1" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={`${line} L${W},${H} L0,${H} Z`} fill={`url(#${id})`} />
        </>
      )}
      <path d={line} fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

/** Chip con flecha y % (verde/rojo, u oscuro sobre las tarjetas pastel). */
export function ChangeChip({ pct, dark }: { pct: number; dark?: boolean }) {
  const color = dark ? ON_PASTEL : gainColor(pct)
  return (
    <span className="inv-chip" style={{ color, background: dark ? 'rgba(21,21,28,0.10)' : `color-mix(in srgb, ${gainColor(pct)} 12%, transparent)` }}>
      <Icon name={pct >= 0 ? 'trendingUp' : 'trendingDown'} size={16} />
      {signedPct(pct)}
    </span>
  )
}

/** Variación del primer al último punto, en %. */
export function changeOf(values: number[] | undefined): number | null {
  if (!values || values.length < 2 || values[0] <= 0) return null
  return (values[values.length - 1] / values[0] - 1) * 100
}

/** Mensaje de error amable con botón de reintentar. */
export function RetryNote({ text, onRetry, dark }: { text: string; onRetry: () => void; dark?: boolean }) {
  return (
    <div className="inv-retry" style={dark ? { color: ON_PASTEL } : undefined}>
      <span className="grow">{text}</span>
      <button className={`inv-retry-btn ${dark ? 'dark' : ''}`} onClick={onRetry}>
        <Icon name="refresh" size={16} />Reintentar
      </button>
    </div>
  )
}

/** Abre un enlace fuera de la app. */
export function openUrl(url: string) {
  try { window.open(url, '_blank', 'noopener,noreferrer') } catch { /* nada */ }
}
