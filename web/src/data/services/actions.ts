// Acciones de red del FinanceViewModel (refreshCrypto, refreshStocks, refreshIfStale, reloadAsset) y ganchos de React
// para la UI (historial, velas, estadísticas, dónde comprar, mercado, noticias y "de qué se habla").
// Cada gancho devuelve { data, loading, error, reload } y guarda lo descargado en memoria: volver a pintar no repite consultas.
import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react'
import { db, getState, toast } from '../store'
import { fetchBuzzInputs, type BuzzInputs } from './buzz'
import { today, type NewsItem } from './news'
import {
  coinExchanges, coinStats, cryptoHistory, cryptoMarkets, cryptoOhlc, cryptoQuotes, RateLimited, stockHistory, stockOhlc, stockQuote,
  type Candle, type CoinMarket, type CoinStats, type MarketCoin,
} from './prices'

// ---------- "Actualizando…" (para el deslizar para refrescar) ----------
let refreshingCount = 0
const refreshListeners = new Set<() => void>()
function setRefreshing(on: boolean) {
  refreshingCount = Math.max(0, refreshingCount + (on ? 1 : -1))
  refreshListeners.forEach((l) => l())
}
/** true mientras se actualizan precios de cripto o acciones. */
export function useRefreshing(): boolean {
  return useSyncExternalStore((l) => { refreshListeners.add(l); return () => refreshListeners.delete(l) }, () => refreshingCount > 0)
}

const currency = () => getState().settings.currency

// ---------- Precios de lo que tienes ----------
let retryScheduled = false

/** Actualiza el precio de tus cripto y su cambio en 24 h (una sola consulta a CoinGecko). */
export async function refreshCrypto(quiet = false): Promise<void> {
  const list = getState().crypto
  if (list.length === 0) return
  setRefreshing(true)
  try {
    const quotes = await cryptoQuotes([...new Set(list.map((h) => h.coinId))], currency())
    const prices: Record<string, number> = {}
    const changes: Record<string, number> = {}
    for (const h of list) {
      const q = quotes[h.coinId]
      if (!q) continue
      prices[h.coinId] = q.price
      if (q.change != null) changes[h.symbol] = q.change
    }
    db.setCryptoPrices(prices)
    db.setDayChanges(changes)
    if (!quiet) toast('Precios de cripto actualizados')
  } catch (e) {
    if (e instanceof RateLimited) {
      // CoinGecko gratis: demasiadas consultas seguidas. Se reintenta solo, sin molestar.
      if (!quiet) toast('CoinGecko va saturado: reintento en un minuto con los precios de antes')
      if (!retryScheduled) {
        retryScheduled = true
        setTimeout(() => { retryScheduled = false; void refreshCrypto(true) }, 60_000)
      }
    } else if (!quiet) toast('No se pudieron actualizar las cripto (sin conexión)')
  } finally {
    setRefreshing(false)
  }
}

/** Actualiza el precio de tus acciones (Yahoo, por proxy) y su cambio frente al cierre anterior. */
export async function refreshStocks(quiet = false): Promise<void> {
  const list = getState().stocks
  if (list.length === 0) return
  setRefreshing(true)
  const failed: string[] = []
  for (const ticker of [...new Set(list.map((h) => h.ticker))]) {
    try {
      const q = await stockQuote(ticker, currency())
      db.setStockPrice(ticker, q.price)
      if (q.change != null) db.setDayChanges({ [ticker]: q.change })
    } catch {
      failed.push(ticker)
    }
  }
  setRefreshing(false)
  if (failed.length > 0) toast(`Sin precio para: ${failed.join(', ')}. Puedes ponerlo a mano.`)
  else if (!quiet) toast('Precios de acciones actualizados')
}

/** Si los precios tienen más de 10 minutos, se piden otra vez (sin mensajes). */
export function refreshIfStale(): void {
  const limit = Date.now() - 10 * 60_000
  const s = getState()
  if (s.crypto.some((h) => h.updatedAt < limit)) void refreshCrypto(true)
  if (s.stocks.some((h) => h.updatedAt >= 1 && h.updatedAt < limit)) void refreshStocks(true)
}

// ---------- Caché en memoria compartida por los ganchos ----------

export interface Resource<T> {
  data: T | undefined
  loading: boolean
  /** Mensaje en español para enseñar tal cual, o null. */
  error: string | null
  reload: () => void
}

interface Entry { data: unknown; loading: boolean; error: string | null; at: number }
const entries = new Map<string, Entry>()
const inflight = new Map<string, Promise<void>>()
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())
const subscribe = (l: () => void) => { listeners.add(l); return () => { listeners.delete(l) } }

function errorText(e: unknown): string {
  if (e instanceof RateLimited) return 'CoinGecko va saturado: prueba otra vez en un minuto'
  if (e instanceof Error && /^(Sin |No )/.test(e.message)) return e.message
  return 'Sin conexión'
}

function load<T>(key: string, loader: () => Promise<T>): Promise<void> {
  const running = inflight.get(key)
  if (running) return running
  const prev = entries.get(key)
  entries.set(key, { data: prev?.data, loading: true, error: null, at: prev?.at ?? 0 })
  emit()
  const p = loader().then(
    (data) => { entries.set(key, { data, loading: false, error: null, at: Date.now() }) },
    (e: unknown) => { entries.set(key, { data: prev?.data, loading: false, error: errorText(e), at: Date.now() }) },
  ).finally(() => { inflight.delete(key); emit() })
  inflight.set(key, p)
  return p
}

/** Borra de la caché las claves que cumplan [test] (los ganchos montados vuelven a pedirlas). */
export function invalidate(test: (key: string) => boolean): void {
  let changed = false
  for (const k of [...entries.keys()]) if (test(k) && !inflight.has(k)) { entries.delete(k); changed = true }
  if (changed) emit()
}

/**
 * Gancho genérico: descarga con [loader] la primera vez que se usa [key] (o si han pasado [ttl] ms), y comparte el
 * resultado con todos los componentes que usen la misma clave. key null = no cargar nada.
 */
export function useResource<T>(key: string | null, loader: () => Promise<T>, ttl = Infinity): Resource<T> {
  const entry = useSyncExternalStore(subscribe, () => (key ? entries.get(key) : undefined))
  const loaderRef = useRef(loader)
  loaderRef.current = loader
  const missing = entry === undefined
  useEffect(() => {
    if (!key) return
    const e = entries.get(key)
    // Los errores se reintentan al volver a montar pasados 30 s.
    const stale = !e || (!e.loading && Date.now() - e.at > (e.error ? Math.min(ttl, 30_000) : ttl))
    if (stale) void load(key, () => loaderRef.current())
  }, [key, missing, ttl])
  const reload = useCallback(() => { if (key) void load(key, () => loaderRef.current()) }, [key])
  return {
    data: entry?.data as T | undefined,
    loading: key != null && (entry === undefined || entry.loading),
    error: entry?.error ?? null,
    reload,
  }
}

// ---------- Ganchos de Inversión ----------

/** Historial para los gráficos: horas (ms) y precios, en el mismo orden. */
export interface History { times: number[]; values: number[] }

const histKey = (key: string, days: number, cur?: string) => (cur == null ? `${key}:${days}` : `${key}:${days}:${cur}`)

/** Historial de precios de [days] días. key es el símbolo (como en Android, "BTC:7" o "BTC:7:usd" con cur). */
export function useAssetHistory(key: string, isCrypto: boolean, id: string, days: number, cur?: string): Resource<History> {
  return useResource(`h:${histKey(key, days, cur)}`, async () => {
    const c = cur ?? currency()
    const list = (isCrypto ? await cryptoHistory(id, c, days) : await stockHistory(id, c, days)).filter(([, v]) => Number.isFinite(v) && v > 0)
    return { times: list.map((p) => p[0]), values: list.map((p) => p[1]) }
  })
}

/** Velas para la ficha de una inversión. */
export function useOhlc(key: string, isCrypto: boolean, id: string, days: number, cur?: string): Resource<Candle[]> {
  return useResource(`o:${histKey(key, days, cur)}`, async () => {
    const c = cur ?? currency()
    const list = isCrypto ? await cryptoOhlc(id, c, days) : await stockOhlc(id, c, days)
    return list.filter((k) => [k.open, k.high, k.low, k.close].every((v) => Number.isFinite(v) && v > 0))
  })
}

/** Estadísticas de CoinGecko de una cripto (una vez por sesión). id null = nada. */
export function useCoinStats(id: string | null, cur?: string): Resource<CoinStats> {
  return useResource(id ? `s:${cur == null ? id : `${id}:${cur}`}` : null, () => coinStats(id as string, cur ?? currency()))
}

/** Dónde comprar una cripto (exchanges con más volumen). */
export function useExchanges(coinId: string | null): Resource<CoinMarket[]> {
  return useResource(coinId ? `x:${coinId}` : null, () => coinExchanges(coinId as string))
}

/**
 * Criptos populares del mercado en dólares (aunque no las tengas), refrescadas como mucho cada 2 min.
 * De paso deja su minigráfico de 24 h como historial "SÍMBOLO:1:usd" (y "SÍMBOLO:1" si no había).
 */
export function useMarkets(ids: string[]): Resource<MarketCoin[]> {
  const idsKey = ids.join(',')
  return useResource(ids.length ? `m:${idsKey}` : null, async () => {
    const list = await cryptoMarkets(idsKey.split(','), 'usd')
    const now = Date.now()
    for (const m of list.filter((x) => x.spark.length >= 2)) {
      const times = m.spark.map((_, i) => now - 86_400_000 + (86_400_000 * i) / (m.spark.length - 1))
      const h: History = { times, values: m.spark }
      entries.set(`h:${m.symbol}:1:usd`, { data: h, loading: false, error: null, at: now })
      if (!entries.has(`h:${m.symbol}:1`)) entries.set(`h:${m.symbol}:1`, { data: h, loading: false, error: null, at: now })
    }
    return list
  }, 120_000)
}

/** Deslizar para refrescar en la ficha: vuelve a pedir precio, gráfico y estadísticas (gratis). */
export function reloadAsset(symbol: string, id: string, isCrypto: boolean): void {
  invalidate((k) => k.startsWith(`h:${symbol}:`) || k.startsWith(`o:${symbol}:`) || k === `s:${id}` || k.startsWith(`s:${id}:`))
  if (isCrypto) void refreshCrypto(true); else void refreshStocks()
}

// ---------- Noticias y "de qué se habla" ----------

/** Noticias de hoy (se refrescan como mucho cada 15 min; force = pedirlas ya). */
export function useNews(force = false): Resource<NewsItem[]> {
  const r = useResource('news', () => today(), 15 * 60_000)
  const forced = useRef(false)
  useEffect(() => {
    if (force && !forced.current && !inflight.has('news')) { forced.current = true; r.reload() }
  }, [force, r])
  return r
}

/**
 * Datos para "¿De qué se está hablando?" (como mucho cada 30 min). Pasa data a BuzzAnalyzer.analyze(data.posts,
 * data.trending, data.mine) de domain/Buzz.ts, por ejemplo dentro de un useMemo.
 */
export function useBuzz(): Resource<BuzzInputs> {
  return useResource('buzz', async () => {
    try { return await fetchBuzzInputs() } catch (e) {
      toast('Sin conexión: no he podido ver de qué se habla ahora mismo.')
      throw e
    }
  }, 30 * 60_000)
}
