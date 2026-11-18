// Precios de mercado (copia de data/PriceService.kt).
// Cripto: API pública de CoinGecko (sin clave, permite CORS). Acciones: endpoint público de Yahoo Finance, que no es
// oficial, puede fallar y no permite CORS (va por proxy); por eso el precio de una acción también se puede poner a mano.
import { enc, fetchText, HttpError, NetError, sleep } from './net'

export interface CoinResult { id: string; symbol: string; name: string }
/** Una acción encontrada en Yahoo (ticker con formato Yahoo: AAPL, SAN.MC, VWCE.DE…). */
export interface StockResult { ticker: string; name: string; exchange: string; type: string }
/** Una cripto del mercado: precio, cambio en 24 h y su gráfico de las últimas 24 h. */
export interface MarketCoin { id: string; symbol: string; name: string; price: number; change24h: number | null; spark: number[] }
/** Un sitio donde se negocia una cripto (de CoinGecko): nombre, par, enlace y confianza. */
export interface CoinMarket { exchange: string; pair: string; url: string | null; trusted: boolean }
/** Estadísticas de una cripto según CoinGecko (gratis). Lo que no venga queda en null. */
export interface CoinStats {
  rank: number | null; marketCap: number | null; volume24h: number | null; high24h: number | null; low24h: number | null
  ath: number | null; athChangePct: number | null; change7d: number | null; change30d: number | null; change1y: number | null
  circulating: number | null; maxSupply: number | null
}
/** Una moneda en tendencia en CoinGecko (lo más buscado). */
export interface TrendingCoin { id: string; symbol: string; name: string; rank: number | null; change24h: number | null }
/** Un hilo de Reddit: título, votos, comentarios y enlace. */
export interface RedditPost { title: string; score: number; comments: number; url: string }
/** Una vela: apertura, máximo, mínimo y cierre de un tramo. */
export interface Candle { open: number; high: number; low: number; close: number }
/** Un punto del historial: [hora en ms, precio]. */
export type PricePoint = [number, number]
/** Precio y cambio (%) en 24 h / frente al cierre anterior. */
export interface Quote { price: number; change: number | null }

/** CoinGecko gratis limita las consultas por minuto: aviso propio para tratarlo con calma. */
export class RateLimited extends Error {
  constructor() { super('CoinGecko limita las consultas'); this.name = 'RateLimited' }
}

const CG = 'https://api.coingecko.com/api/v3'
const YH = 'https://query1.finance.yahoo.com'

// ---------- Ayudas de lectura de JSON (como optDouble/optInt de org.json) ----------
type J = Record<string, unknown>
const obj = (v: unknown): J | undefined => (v && typeof v === 'object' && !Array.isArray(v) ? (v as J) : undefined)
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : [])
const num = (v: unknown): number | null => (typeof v === 'number' && Number.isFinite(v) ? v : null)
const str = (v: unknown): string => (typeof v === 'string' ? v : v == null ? '' : String(v))
const posInt = (v: unknown): number | null => { const n = num(v); return n != null && n > 0 ? Math.trunc(n) : null }

// ---------- CoinGecko: espaciado de 2,5 s, caché de 60 s y un reintento ----------
const cgCache = new Map<string, { at: number; body: string }>()
let cgLast = 0
let cgChain: Promise<unknown> = Promise.resolve()

function isOnline(): boolean {
  try { return typeof navigator === 'undefined' || navigator.onLine !== false } catch { return true }
}

async function cgFetch(url: string): Promise<string> {
  try {
    return await fetchText(url, { cors: 'direct', fallback: false, expect: 'json' })
  } catch (e) {
    if (e instanceof HttpError && e.status === 429) throw new RateLimited()
    // CoinGecko contesta el 429 sin cabecera CORS: Safari lo ve como un fallo de red. Si hay conexión, es eso.
    if (e instanceof NetError && e.directNetworkFailure && isOnline()) throw new RateLimited()
    throw e
  }
}

/** Descarga de CoinGecko en cola: una consulta cada 2,5 s, guardada 60 s; si limita, espera 15 s y reintenta una vez. */
function cgGet(url: string): Promise<string> {
  const cached = cgCache.get(url)
  if (cached && Date.now() - cached.at < 60_000) return Promise.resolve(cached.body)
  const run = async (): Promise<string> => {
    const again = cgCache.get(url)
    if (again && Date.now() - again.at < 60_000) return again.body
    for (let attempt = 0; attempt < 2; attempt++) {
      const wait = cgLast + 2_500 - Date.now()
      if (wait > 0) await sleep(wait)
      cgLast = Date.now()
      try {
        const body = await cgFetch(url)
        cgCache.set(url, { at: Date.now(), body })
        return body
      } catch (e) {
        if (!(e instanceof RateLimited) || attempt === 1) throw e
        await sleep(15_000)
      }
    }
    throw new RateLimited()
  }
  const p = cgChain.then(run, run)
  cgChain = p.catch(() => undefined)
  return p
}
const cgJson = async (url: string): Promise<unknown> => JSON.parse(await cgGet(url))

// ---------- Yahoo: por proxy, con caché corta para no gastar el límite del proxy ----------
const yhCache = new Map<string, { at: number; body: Promise<string> }>()
function yhGet(url: string, ttl = 60_000): Promise<string> {
  const c = yhCache.get(url)
  if (c && Date.now() - c.at < ttl) return c.body
  const body = fetchText(url, { cors: 'proxy', expect: 'json' })
  yhCache.set(url, { at: Date.now(), body })
  body.catch(() => yhCache.delete(url))
  return body
}
const yhJson = async (url: string, ttl?: number): Promise<unknown> => JSON.parse(await yhGet(url, ttl))

function yahooResult(json: unknown, symbol: string): J {
  const r = obj(arr(obj(obj(json)?.chart)?.result)[0])
  if (!r) throw new Error(`No encontrado: ${symbol}`)
  return r
}

// ---------- Cripto ----------

export async function cryptoPrices(ids: string[], currency: string): Promise<Record<string, number>> {
  const q = await cryptoQuotes(ids, currency)
  return Object.fromEntries(Object.entries(q).map(([k, v]) => [k, v.price]))
}

/** Precio y cambio en 24 h (%) de cada cripto. */
export async function cryptoQuotes(ids: string[], currency: string): Promise<Record<string, Quote>> {
  if (ids.length === 0) return {}
  const cur = currency.toLowerCase()
  const json = obj(await cgJson(`${CG}/simple/price?ids=${enc(ids.join(','))}&vs_currencies=${cur}&include_24hr_change=true`)) ?? {}
  const out: Record<string, Quote> = {}
  for (const id of ids) {
    const o = obj(json[id])
    const price = num(o?.[cur])
    if (o && price != null) out[id] = { price, change: num(o[`${cur}_24h_change`]) }
  }
  return out
}

/** Dónde se puede comprar una cripto: los exchanges con más volumen (gratis, CoinGecko). */
export async function coinExchanges(id: string): Promise<CoinMarket[]> {
  const tickers = arr(obj(await cgJson(`${CG}/coins/${enc(id)}/tickers?order=volume_desc&depth=false`))?.tickers)
  const out: CoinMarket[] = []
  const seen = new Set<string>()
  for (const raw of tickers) {
    const t = obj(raw); if (!t) continue
    const name = str(obj(t.market)?.name)
    if (!name.trim() || t.is_stale === true || t.is_anomaly === true || seen.has(name)) continue
    seen.add(name)
    const url = str(t.trade_url)
    out.push({ exchange: name, pair: `${str(t.base)}/${str(t.target)}`, url: url.startsWith('https://') ? url : null, trusted: str(t.trust_score) === 'green' })
    if (out.length >= 8) break
  }
  return out
}

/** Precios de varias cripto en una sola llamada gratuita a CoinGecko (con minigráfico de 24 puntos). */
export async function cryptoMarkets(ids: string[], currency: string): Promise<MarketCoin[]> {
  if (ids.length === 0) return []
  const list = arr(await cgJson(`${CG}/coins/markets?vs_currency=${currency.toLowerCase()}&ids=${enc(ids.join(','))}&sparkline=true&price_change_percentage=24h`))
  return list.map(obj).filter((o): o is J => !!o && num(o.current_price) != null).map((o) => ({
    id: str(o.id), symbol: str(o.symbol).toUpperCase(), name: str(o.name), price: num(o.current_price) as number,
    change24h: num(o.price_change_percentage_24h),
    spark: arr(obj(o.sparkline_in_7d)?.price).map(num).filter((v): v is number => v != null).slice(-24),
  }))
}

/** Precios (hora en ms, precio) de los últimos [days] días de una cripto (como mucho 80 puntos). */
export async function cryptoHistory(id: string, currency: string, days: number): Promise<PricePoint[]> {
  const prices = arr(obj(await cgJson(`${CG}/coins/${enc(id)}/market_chart?vs_currency=${currency.toLowerCase()}&days=${days}`))?.prices)
  const points: PricePoint[] = []
  for (const p of prices) {
    const a = arr(p); const t = num(a[0]); const v = num(a[1])
    if (t != null && v != null) points.push([t, v])
  }
  return samplePoints(points)
}

/** Estadísticas de mercado de una cripto (CoinGecko, gratis). */
export async function coinStats(id: string, currency: string): Promise<CoinStats> {
  const md = obj(await cgJson(`${CG}/coins/${enc(id)}?localization=false&tickers=false&community_data=false&developer_data=false&sparkline=false`)) ?? {}
  const m = obj(md.market_data)
  if (!m) throw new Error(`Sin datos de mercado: ${id}`)
  const cur = currency.toLowerCase()
  const byCur = (k: string) => num(obj(m[k])?.[cur])
  const n = (k: string) => num(m[k])
  return {
    rank: posInt(md.market_cap_rank), marketCap: byCur('market_cap'), volume24h: byCur('total_volume'), high24h: byCur('high_24h'),
    low24h: byCur('low_24h'), ath: byCur('ath'), athChangePct: byCur('ath_change_percentage'),
    change7d: n('price_change_percentage_7d'), change30d: n('price_change_percentage_30d'), change1y: n('price_change_percentage_1y'),
    circulating: n('circulating_supply'), maxSupply: n('max_supply'),
  }
}

/** Lo más buscado en CoinGecko ahora mismo (gratis). El cambio en 24 h va en euros, como en Android. */
export async function trending(): Promise<TrendingCoin[]> {
  const coins = arr(obj(await cgJson(`${CG}/search/trending`))?.coins)
  return coins.map((c) => obj(obj(c)?.item)).filter((it): it is J => !!it && !!str(it.id)).map((it) => ({
    id: str(it.id), symbol: str(it.symbol).toUpperCase(), name: str(it.name), rank: posInt(it.market_cap_rank),
    change24h: num(obj(obj(it.data)?.price_change_percentage_24h)?.eur),
  }))
}

/** Velas de los últimos [days] días de una cripto (CoinGecko, gratis; como mucho 40). */
export async function cryptoOhlc(id: string, currency: string, days: number): Promise<Candle[]> {
  const d = [7, 14, 30, 90, 180, 365].find((x) => x >= days) ?? 365
  const rows = arr(await cgJson(`${CG}/coins/${enc(id)}/ohlc?vs_currency=${currency.toLowerCase()}&days=${d}`))
  const candles: Candle[] = []
  for (const r of rows) {
    const a = arr(r).map(num)
    if (a.length >= 5 && a.slice(1, 5).every((v) => v != null)) candles.push({ open: a[1]!, high: a[2]!, low: a[3]!, close: a[4]! })
  }
  return sampleCandles(candles)
}

export async function searchCoins(query: string): Promise<CoinResult[]> {
  const coins = arr(obj(await cgJson(`${CG}/search?query=${enc(query)}`))?.coins)
  return coins.slice(0, 8).map(obj).filter((c): c is J => !!c).map((c) => ({ id: str(c.id), symbol: str(c.symbol).toUpperCase(), name: str(c.name) }))
}

// ---------- Reddit ----------

/** Hilos más populares de un subreddit (JSON público de Reddit, gratis y sin cuenta). */
export async function redditHot(sub: string, limit = 60): Promise<RedditPost[]> {
  const url = `https://www.reddit.com/r/${enc(sub)}/hot.json?limit=${limit}&raw_json=1`
  const json = JSON.parse(await fetchText(url, { cors: 'direct', expect: 'json' }))
  return arr(obj(obj(json)?.data)?.children).map((c) => obj(obj(c)?.data)).filter((d): d is J => !!d).map((d) => ({
    title: str(d.title), score: num(d.score) ?? 0, comments: num(d.num_comments) ?? 0, url: 'https://www.reddit.com' + str(d.permalink),
  })).filter((p) => p.title.trim() !== '')
}

// ---------- Acciones (Yahoo) ----------

/** Precio de [symbol] en su moneda de cotización, cierre anterior y moneda. */
async function yahooQuote(symbol: string): Promise<{ price: number; prevClose: number | null; currency: string }> {
  const r = yahooResult(await yhJson(`${YH}/v8/finance/chart/${enc(symbol)}?range=1d&interval=1d`), symbol)
  const meta = obj(r.meta) ?? {}
  const price = num(meta.regularMarketPrice)
  if (price == null) throw new Error(`No encontrado: ${symbol}`)
  const prev = num(meta.chartPreviousClose) ?? num(meta.previousClose)
  return { price, prevClose: prev != null && prev > 0 ? prev : null, currency: str(meta.currency) || 'USD' }
}

/** Cuánto vale 1 unidad de la moneda de cotización en [currency] (y si cotiza en peniques). */
async function fxFactor(quoteCurrency: string, currency: string): Promise<number> {
  // La Bolsa de Londres cotiza en peniques (GBp).
  const pence = quoteCurrency === 'GBp'
  const cur = pence ? 'GBP' : quoteCurrency
  const base = pence ? 0.01 : 1
  if (cur.toUpperCase() === currency.toUpperCase()) return base
  const fx = yahooResult(await yhJson(`${YH}/v8/finance/chart/${enc(`${cur.toUpperCase()}${currency.toUpperCase()}=X`)}?range=1d&interval=1d`, 10 * 60_000), cur)
  const rate = num(obj(fx.meta)?.regularMarketPrice)
  if (rate == null || rate <= 0) throw new Error(`Sin cambio ${cur}/${currency}`)
  return base * rate
}

/** Precio de [ticker] convertido a [currency]. */
export async function stockPrice(ticker: string, currency: string): Promise<number> {
  const q = await yahooQuote(ticker)
  return q.price * await fxFactor(q.currency, currency)
}

/**
 * Precio de [ticker] en [currency] y su cambio (%) frente al cierre anterior.
 * Nota: en Android se usaba chartPreviousClose de range=5d, que es el cierre de hace 5 sesiones; aquí se usa el de
 * range=1d, que sí es el de ayer (y de paso se ahorra una consulta).
 */
export async function stockQuote(ticker: string, currency: string): Promise<Quote> {
  const q = await yahooQuote(ticker)
  const factor = await fxFactor(q.currency, currency)
  return { price: q.price * factor, change: q.prevClose != null ? (q.price - q.prevClose) / q.prevClose * 100 : null }
}

/** Cierres (hora en ms, precio) de los últimos [days] días de una acción, en [currency]. */
export async function stockHistory(ticker: string, currency: string, days: number): Promise<PricePoint[]> {
  const [range, interval] = days <= 1 ? ['1d', '5m'] : days <= 7 ? ['5d', '1h'] : days <= 31 ? ['1mo', '1d'] : days <= 92 ? ['3mo', '1d'] : ['1y', '1d']
  const r = yahooResult(await yhJson(`${YH}/v8/finance/chart/${enc(ticker)}?range=${range}&interval=${interval}`), ticker)
  const times = arr(r.timestamp)
  const closes = arr(obj(arr(obj(r.indicators)?.quote)[0])?.close)
  const factor = await fxFactor(str(obj(r.meta)?.currency) || 'USD', currency)
  const raw: PricePoint[] = []
  closes.forEach((c, i) => { const v = num(c); if (v != null) raw.push([(num(times[i]) ?? 0) * 1000, v * factor]) })
  return samplePoints(raw)
}

/** Velas diarias de una acción (Yahoo, gratis), en [currency]. */
export async function stockOhlc(ticker: string, currency: string, days: number): Promise<Candle[]> {
  const range = days <= 7 ? '5d' : days <= 31 ? '1mo' : days <= 92 ? '3mo' : '1y'
  const r = yahooResult(await yhJson(`${YH}/v8/finance/chart/${enc(ticker)}?range=${range}&interval=1d`), ticker)
  const q = obj(arr(obj(r.indicators)?.quote)[0]) ?? {}
  const factor = await fxFactor(str(obj(r.meta)?.currency) || 'USD', currency)
  const o = arr(q.open), h = arr(q.high), l = arr(q.low), c = arr(q.close)
  const out: Candle[] = []
  c.forEach((_, i) => {
    const v = [num(o[i]), num(h[i]), num(l[i]), num(c[i])]
    if (v.every((x) => x != null)) out.push({ open: v[0]! * factor, high: v[1]! * factor, low: v[2]! * factor, close: v[3]! * factor })
  })
  return sampleCandles(out)
}

/** Busca acciones y fondos cotizados en Yahoo por nombre o ticker (solo se envía lo que escribas en el buscador). */
export async function searchStocks(query: string): Promise<StockResult[]> {
  const json = obj(await yhJson(`${YH}/v1/finance/search?q=${enc(query)}&quotesCount=8&newsCount=0&listsCount=0`))
  return arr(json?.quotes).map(obj).filter((q): q is J => !!q && !!str(q.symbol) && ['EQUITY', 'ETF', 'MUTUALFUND', 'INDEX'].includes(str(q.quoteType)))
    .map((q) => ({ ticker: str(q.symbol), name: str(q.longname) || str(q.shortname) || str(q.symbol), exchange: str(q.exchDisp) || str(q.exchange), type: str(q.quoteType) }))
}

// ---------- Muestreo ----------

export function samplePoints(list: PricePoint[], max = 80): PricePoint[] {
  if (list.length <= max) return list
  return Array.from({ length: max }, (_, i) => list[Math.floor(i * (list.length - 1) / (max - 1))])
}

/** Junta velas hasta dejar como mucho [max] (la primera apertura, máximos y mínimos del grupo y el último cierre). */
export function sampleCandles(list: Candle[], max = 40): Candle[] {
  if (list.length <= max) return list
  const size = Math.ceil(list.length / max)
  const out: Candle[] = []
  for (let i = 0; i < list.length; i += size) {
    const g = list.slice(i, i + size)
    out.push({ open: g[0].open, high: Math.max(...g.map((x) => x.high)), low: Math.min(...g.map((x) => x.low)), close: g[g.length - 1].close })
  }
  return out
}
