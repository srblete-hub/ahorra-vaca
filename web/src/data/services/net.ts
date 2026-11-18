// Red: descargas con tiempo límite y, cuando el navegador no deja (CORS), a través de un proxy público gratis.
// Solo viajan en las URL tickers, ids de monedas o direcciones de feeds: nunca datos tuyos.

/**
 * Proxies CORS gratis y sin clave, en orden de preferencia. A cada uno se le pega la URL de destino codificada.
 * Para usar un Cloudflare Worker propio basta con poner su prefijo aquí (p. ej. 'https://mi-worker.workers.dev/?url=')
 * o definir VITE_CORS_PROXY al compilar, que se prueba antes que estos.
 * Comprobado el 18-11-2026: cors.lol responde (con límite de consultas por minuto y por IP); el Worker público de
 * sirjosh responde (es de un particular: puede desaparecer); allorigins y codetabs daban 522 (caídos) pero se dejan
 * como reserva porque suelen volver. corsproxy.io ya exige clave: fuera.
 */
export const CORS_PROXIES: readonly string[] = [
  'https://api.cors.lol/?url=',
  'https://cors-get-proxy.sirjosh.workers.dev/?url=',
  'https://api.allorigins.win/raw?url=',
  'https://api.codetabs.com/v1/proxy?quest=',
]

/** 'direct': primero directo (la web permite CORS) y, si falla la red, los proxies. 'proxy': solo proxies. */
export type CorsMode = 'direct' | 'proxy'

export interface FetchOptions {
  cors?: CorsMode
  /** Si es false, en modo 'direct' no se prueba ningún proxy. */
  fallback?: boolean
  /** Tiempo máximo por intento, en ms (directo 10 s, proxy 15 s). */
  timeoutMs?: number
  /** Comprueba que lo que llega es lo esperado (un proxy puede devolver 200 con su propia página de error). */
  expect?: 'json' | 'xml' | 'html' | 'any'
  signal?: AbortSignal
}

/** Error HTTP con su código (429 = demasiadas consultas). */
export class HttpError extends Error {
  constructor(readonly status: number, readonly url: string) { super(`HTTP ${status}`) }
}

/** No se ha podido descargar ni directo ni por ningún proxy. */
export class NetError extends Error {
  constructor(message: string, readonly directNetworkFailure = false) { super(message) }
}

const PROXY_KEY = 'ahorra-vaca:proxy'

function envProxy(): string | undefined {
  try {
    const v = (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_CORS_PROXY
    return v && v.startsWith('https://') ? v : undefined
  } catch { return undefined }
}

function lastGoodProxy(): string | null {
  try { return sessionStorage.getItem(PROXY_KEY) } catch { return null }
}
function rememberProxy(p: string) {
  try { sessionStorage.setItem(PROXY_KEY, p) } catch { /* modo privado o sin sessionStorage */ }
}

/** Proxies en pausa (hasta cuándo, en ms): 1 min si limitan (429), 5 min si no contestan o fallan (5xx). */
const coolUntil = new Map<string, number>()
function coolDown(proxy: string, ms: number) { coolUntil.set(proxy, Date.now() + ms) }

/** Orden en que se prueban los proxies: el propio (si hay), el último que funcionó y luego el resto; los que están en pausa, al final. */
export function proxyOrder(): string[] {
  const all = [envProxy(), ...CORS_PROXIES].filter((p): p is string => !!p)
  const last = lastGoodProxy()
  const unique = [...new Set(all)]
  const ordered = last && unique.includes(last) ? [last, ...unique.filter((p) => p !== last)] : unique
  const now = Date.now()
  const ready = ordered.filter((p) => (coolUntil.get(p) ?? 0) <= now)
  // Si todos están en pausa se prueban igual, por si acaso.
  return ready.length > 0 ? ready : ordered
}

/** Pocos proxies a la vez: los gratuitos cortan si se les piden muchas cosas seguidas. */
const MAX_PROXY_PARALLEL = 3
let proxyActive = 0
const proxyQueue: (() => void)[] = []
async function proxySlot<T>(fn: () => Promise<T>): Promise<T> {
  if (proxyActive >= MAX_PROXY_PARALLEL) await new Promise<void>((r) => proxyQueue.push(r))
  proxyActive++
  try { return await fn() } finally {
    proxyActive--
    proxyQueue.shift()?.()
  }
}

function looksRight(body: string, expect: FetchOptions['expect']): boolean {
  const b = body.trimStart().slice(0, 2000).toLowerCase()
  switch (expect) {
    case 'json': return b.startsWith('{') || b.startsWith('[')
    case 'xml': return b.includes('<rss') || b.includes('<feed') || b.includes('<rdf') || (b.startsWith('<?xml') && !b.includes('<html'))
    case 'html': return b.length > 0
    default: return true
  }
}

async function attempt(url: string, timeoutMs: number, outer?: AbortSignal): Promise<{ status: number; body: string }> {
  const ac = new AbortController()
  const timer = setTimeout(() => ac.abort(), timeoutMs)
  const onAbort = () => ac.abort()
  outer?.addEventListener('abort', onAbort)
  try {
    const res = await fetch(url, { signal: ac.signal, credentials: 'omit', referrerPolicy: 'no-referrer' })
    const body = await res.text()
    return { status: res.status, body }
  } finally {
    clearTimeout(timer)
    outer?.removeEventListener('abort', onAbort)
  }
}

/**
 * Descarga el texto de [url]. En modo 'direct' prueba primero sin proxy; si el navegador lo bloquea (CORS) o
 * no hay respuesta, prueba los proxies en orden y recuerda (sessionStorage) el que funcionó.
 * Un 429 directo se lanza tal cual (HttpError 429) sin probar proxies: es la web la que limita.
 */
export async function fetchText(url: string, opts: FetchOptions = {}): Promise<string> {
  const mode = opts.cors ?? 'direct'
  const expect = opts.expect ?? 'any'
  let directNetworkFailure = false
  let lastError: unknown = null

  if (mode === 'direct') {
    try {
      const r = await attempt(url, opts.timeoutMs ?? 10_000, opts.signal)
      if (r.status >= 200 && r.status < 300) return r.body
      throw new HttpError(r.status, url)
    } catch (e) {
      if (opts.signal?.aborted) throw e
      if (e instanceof HttpError) {
        // La web contestó: un proxy no lo va a arreglar (salvo 403 por bloqueo de IP, p. ej. Reddit).
        if (e.status !== 403 || opts.fallback === false) throw e
      } else {
        directNetworkFailure = true
      }
      lastError = e
      if (opts.fallback === false) throw new NetError('Sin conexión', directNetworkFailure)
    }
  }

  for (const proxy of proxyOrder()) {
    if (opts.signal?.aborted) break
    try {
      const r = await proxySlot(() => attempt(proxy + encodeURIComponent(url), opts.timeoutMs ?? 12_000, opts.signal))
      if (r.status >= 200 && r.status < 300 && looksRight(r.body, expect)) {
        rememberProxy(proxy)
        return r.body
      }
      // Un 4xx distinto de 429 o una página rara suele ser cosa de la web de destino (p. ej. Reddit bloquea): sin pausa.
      if (r.status === 429) coolDown(proxy, 60_000)
      else if (r.status >= 500) coolDown(proxy, 5 * 60_000)
      lastError = new HttpError(r.status, proxy)
    } catch (e) {
      if (!opts.signal?.aborted) coolDown(proxy, 5 * 60_000)
      lastError = e
    }
  }
  if (opts.signal?.aborted) throw new DOMException('Cancelado', 'AbortError')
  if (lastError instanceof HttpError && lastError.url === url) throw lastError
  throw new NetError('Sin conexión', directNetworkFailure)
}

/** Igual que fetchText pero devuelve el JSON ya leído. */
export async function fetchJson<T = unknown>(url: string, opts: FetchOptions = {}): Promise<T> {
  const text = await fetchText(url, { ...opts, expect: opts.expect ?? 'json' })
  return JSON.parse(text) as T
}

/** Codifica un trozo de URL (equivale a URLEncoder.encode de Kotlin). */
export const enc = (s: string) => encodeURIComponent(s)

export const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
