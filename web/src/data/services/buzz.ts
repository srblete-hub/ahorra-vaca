// "¿De qué se está hablando?": la parte de red de domain/Buzz.kt (loadBuzz del ViewModel).
// Descarga lo más buscado en CoinGecko y los hilos populares de Reddit; el análisis (tono, menciones…)
// es lógica pura y va en domain/Buzz.ts: BuzzAnalyzer.analyze(posts, trending, mine).
import { getState } from '../store'
import { redditHot, trending as fetchTrending, type RedditPost, type TrendingCoin } from './prices'

export type { RedditPost, TrendingCoin }

/** Moneda tuya para reconocerla en los títulos: [coinId, símbolo, nombre] (el Triple de Kotlin). */
export type MineCoin = [id: string, symbol: string, name: string]

/** Lo que necesita BuzzAnalyzer.analyze(posts, trending, mine). */
export interface BuzzInputs {
  posts: RedditPost[]
  trending: TrendingCoin[]
  mine: MineCoin[]
}

export const BUZZ_SUBREDDITS = ['CryptoCurrency', 'CryptoMarkets'] as const

/**
 * Descarga tendencias y hilos (cada fuente que falle se queda vacía). Si no llega nada lanza
 * "Sin conexión: no he podido ver de qué se habla ahora mismo." como en Android.
 */
export async function fetchBuzzInputs(): Promise<BuzzInputs> {
  const [trending, ...subs] = await Promise.all([
    fetchTrending().catch(() => [] as TrendingCoin[]),
    ...BUZZ_SUBREDDITS.map((s) => redditHot(s).catch(() => [] as RedditPost[])),
  ])
  const seen = new Set<string>()
  const posts = (subs as RedditPost[][]).flat().filter((p) => (seen.has(p.url) ? false : (seen.add(p.url), true)))
  if (trending.length === 0 && posts.length === 0) throw new Error('Sin conexión: no he podido ver de qué se habla ahora mismo.')
  const mine: MineCoin[] = getState().crypto.map((h) => [h.coinId, h.symbol, h.name])
  return { posts, trending, mine }
}
