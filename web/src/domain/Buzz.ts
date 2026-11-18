// Port de domain/Buzz.kt: "¿De qué se está hablando?" en Reddit y CoinGecko. No predice precios.
import { distinctBy, maxBy, sortedByDesc, sumOf } from './kt'

/** Un hilo de Reddit: título, votos, comentarios y enlace (data.RedditPost en Kotlin). */
export interface RedditPost { title: string; score: number; comments: number; url: string }
/** Una moneda en tendencia en CoinGecko (lo más buscado) (data.TrendingCoin en Kotlin). */
export interface TrendingCoin { id: string; symbol: string; name: string; rank: number | null; change24h: number | null }

/**
 * Una moneda de la que se habla: menciones, votos, tono y si está en tendencia.
 * `score` y `tone` (calculadas en Kotlin) se rellenan al crearla.
 */
export interface BuzzItem {
  symbol: string
  name: string
  coinId: string | null
  mentions: number
  votes: number
  /** Tono de los hilos que la mencionan: 0 (todo negativo) a 1 (todo positivo); 0,5 = neutro. */
  positivity: number
  trending: boolean
  change24h: number | null
  posts: RedditPost[]
  /** Ruido positivo: más menciones y votos pesan, pero con rendimiento decreciente; el tono lo multiplica. */
  score: number
  /** "positivo", "negativo" o "neutro". */
  tone: string
}

/** `featured` (calculada) = la moneda con más ruido positivo (solo si de verdad hay tono positivo y se habla de ella). */
export interface Buzz { items: BuzzItem[]; headlines: RedditPost[]; featured: BuzzItem | null }

const POSITIVE = [
  'bull', 'bullish', 'moon', 'rally', 'surge', 'soar', 'pump', 'breakout', 'ath', 'all-time high', 'record', 'adoption',
  'partnership', 'approve', 'approved', 'approval', 'etf', 'upgrade', 'launch', 'gains', 'green', 'buy', 'accumulat', 'growth', 'rise', 'up ',
  'sube', 'subida', 'alcista', 'récord', 'compra',
]
const NEGATIVE = [
  'bear', 'bearish', 'crash', 'dump', 'scam', 'hack', 'hacked', 'exploit', 'lawsuit', 'sec ', 'sued', 'sell', 'selling', 'fall', 'drop',
  'plunge', 'red', 'rug', 'ban', 'fraud', 'fud', 'liquidat', 'down ', 'lose', 'loss', 'warning',
  'baja', 'caída', 'bajista', 'estafa', 'hackeo',
]

/** Monedas conocidas para reconocerlas en los títulos (además de las que tengas y las de tendencia): [id, símbolo, nombre]. */
const KNOWN: Array<[string, string, string]> = [
  ['bitcoin', 'BTC', 'Bitcoin'], ['ethereum', 'ETH', 'Ethereum'], ['solana', 'SOL', 'Solana'],
  ['ripple', 'XRP', 'XRP'], ['cardano', 'ADA', 'Cardano'], ['dogecoin', 'DOGE', 'Dogecoin'],
  ['chainlink', 'LINK', 'Chainlink'], ['avalanche-2', 'AVAX', 'Avalanche'], ['polkadot', 'DOT', 'Polkadot'],
  ['binancecoin', 'BNB', 'BNB'], ['litecoin', 'LTC', 'Litecoin'], ['tron', 'TRX', 'TRON'],
  ['shiba-inu', 'SHIB', 'Shiba Inu'], ['pepe', 'PEPE', 'Pepe'], ['sui', 'SUI', 'Sui'],
]

function tone(text: string): number | null {
  const t = ' ' + text.toLowerCase() + ' '
  const pos = POSITIVE.filter((w) => t.includes(w)).length
  const neg = NEGATIVE.filter((w) => t.includes(w)).length
  return pos + neg === 0 ? null : pos / (pos + neg)
}

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

function makeItem(symbol: string, name: string, coinId: string | null, mentions: number, votes: number, positivity: number, trending: boolean, change24h: number | null, posts: RedditPost[]): BuzzItem {
  const score = (Math.log(1.0 + mentions) * 2 + Math.log(1.0 + votes) / 2 + (trending ? 2 : 0)) * Math.max(positivity - 0.3, 0)
  const t = positivity >= 0.6 ? 'positivo' : positivity <= 0.4 ? 'negativo' : 'neutro'
  return { symbol, name, coinId, mentions, votes, positivity, trending, change24h, posts, score, tone: t }
}

/**
 * "¿De qué se está hablando?": cuenta en qué hilos populares de Reddit sale cada moneda (por nombre o
 * símbolo), suma sus votos y mira si las palabras de esos títulos son positivas o negativas. Le añade lo
 * más buscado en CoinGecko. No predice precios: solo resume la conversación.
 */
export const BuzzAnalyzer = {
  /** `mine`: tus criptos como [coinId, símbolo, nombre]. */
  analyze(posts: RedditPost[], trending: TrendingCoin[], mine: Array<[string, string, string]> = []): Buzz {
    const coins = distinctBy([...mine, ...trending.map((t): [string, string, string] => [t.id, t.symbol, t.name]), ...KNOWN], (c) => c[1].toUpperCase())
    const items: BuzzItem[] = []
    for (const [id, symbol, name] of coins) {
      const sym = new RegExp(`(^|[^A-Za-z])\\$?${escapeRe(symbol)}([^A-Za-z]|$)`)
      const nm = name.toLowerCase()
      const hits = posts.filter((p) => (symbol.length >= 3 && sym.test(p.title)) || (nm.length >= 3 && p.title.toLowerCase().includes(nm)))
      const tr = trending.find((t) => t.symbol.toLowerCase() === symbol.toLowerCase())
      if (hits.length === 0 && tr == null) continue
      // Tono medio ponderado por votos; los títulos sin palabras claras cuentan como neutros.
      const weights = hits.map((h) => Math.max(h.score + h.comments, 1))
      const tones = hits.map((h) => tone(h.title) ?? 0.5)
      const positivity = hits.length === 0 ? 0.5 : sumOf(tones.map((t, i) => t * weights[i]), (x) => x) / sumOf(weights, (x) => x)
      items.push(makeItem(symbol.toUpperCase(), name, id, hits.length, sumOf(hits, (h) => h.score), positivity, tr != null, tr?.change24h ?? null, sortedByDesc(hits, (h) => h.score).slice(0, 3)))
    }
    const sorted = sortedByDesc(items, (i) => i.score + i.mentions * 0.01)
    return {
      items: sorted,
      headlines: sortedByDesc(posts, (p) => p.score).slice(0, 6),
      featured: maxBy(sorted.filter((i) => i.positivity >= 0.6 && (i.mentions >= 2 || i.trending)), (i) => i.score) ?? null,
    }
  },
}
