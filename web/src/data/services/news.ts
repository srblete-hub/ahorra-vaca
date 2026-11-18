// Noticias financieras de hoy (copia de data/NewsService.kt): feeds RSS públicos, gratis y sin clave.
// Nada de lo que tienes en la app se envía: solo se descargan titulares.
import { fetchText, type CorsMode } from './net'

/** Una noticia: titular, medio, enlace, hora (ms) y un resumen corto si el medio lo da. */
export interface NewsItem {
  title: string
  source: string
  url: string
  epochMs: number
  summary: string
  topic: string
  imageUrl?: string | null
}

export interface Feed { topic: string; url: string; cors: CorsMode }

/**
 * Los mismos feeds que en Android. Los de El País y Cinco Días mandan cabecera CORS (van directos);
 * el resto necesita proxy desde el navegador.
 */
export const FEEDS: readonly Feed[] = [
  { topic: 'Empresas', url: 'https://feeds.elpais.com/mrss-s/pages/ep/site/cincodias.elpais.com/portada', cors: 'direct' },
  { topic: 'Economía', url: 'https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/section/economia/portada', cors: 'direct' },
  { topic: 'Bolsa', url: 'https://e00-expansion.uecdn.es/rss/mercados.xml', cors: 'proxy' },
  { topic: 'Economía', url: 'https://e00-expansion.uecdn.es/rss/portada.xml', cors: 'proxy' },
  { topic: 'Empresas', url: 'https://e00-expansion.uecdn.es/rss/empresas.xml', cors: 'proxy' },
  { topic: 'Economía', url: 'https://e00-expansion.uecdn.es/rss/economia.xml', cors: 'proxy' },
  { topic: 'Bolsa', url: 'https://es.investing.com/rss/news.rss', cors: 'proxy' },
  { topic: 'Cripto', url: 'https://es.investing.com/rss/news_301.rss', cors: 'proxy' },
  { topic: 'Cripto', url: 'https://es.beincrypto.com/feed/', cors: 'proxy' },
]

/** Google Noticias en español (titulares acabados en " - Medio"); no está en la lista por defecto porque no trae fotos. */
export const GOOGLE_NEWS_FEED: Feed = {
  topic: 'Economía', url: 'https://news.google.com/rss/search?q=econom%C3%ADa+OR+bolsa&hl=es&gl=ES&ceid=ES:es', cors: 'proxy',
}

// ---------- Filtro de anuncios ----------

/** Fuera anuncios: contenido patrocinado, publirreportajes, ofertas y escaparates de compras. */
const AD_WORDS = [
  'patrocinado', 'patrocinada', 'publirreportaje', 'publicidad', 'contenido de marca', 'branded', 'sponsored', 'partner content',
  'ofrecido por', 'en colaboración con', 'en colaboracion con', 'oferta', 'ofertas', 'descuento', 'chollo', 'chollos', 'cupón', 'cupon',
  'código promocional', 'codigo promocional', 'black friday', 'cyber monday', 'rebajas', 'sorteo', 'promoción', 'promocion', 'compra ya',
  'mejores precios', 'amazon prime day', 'bono de bienvenida', 'regístrate', 'registrate', 'airdrop gratis', 'escaparate', 'showroom',
]
const AD_URL = ['/escaparate/', '/branded', '/patrocinad', '/publicidad', '/promocion', '/ofertas', '/compras/', '/brand-studio', '/sponsored', '/partner', '/press-release', '/comunicado']
const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const AD_RES = AD_WORDS.map((w) => new RegExp(`(^|[^a-záéíóúñ])${escapeRe(w)}([^a-záéíóúñ]|$)`))

export function isAd(n: Pick<NewsItem, 'title' | 'summary' | 'url'>, categories = ''): boolean {
  const t = `${n.title} ${n.summary} ${categories}`.toLowerCase()
  const url = n.url.toLowerCase()
  return AD_RES.some((re) => re.test(t)) || AD_URL.some((u) => url.includes(u))
}

// ---------- Lectura de XML ----------

/** Lo mínimo de un elemento XML que necesita el lector de RSS (lo cumplen el DOM y el lector de reserva). */
interface XEl {
  tag: string
  attr(name: string): string | null
  text(): string
  children(): XEl[]
}

function fromDom(el: Element): XEl {
  return {
    tag: el.tagName,
    attr: (n) => el.getAttribute(n),
    text: () => el.textContent ?? '',
    children: () => Array.from(el.children).map(fromDom),
  }
}

const ENTITIES: Record<string, string> = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' }
function decode(s: string): string {
  return s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e: string) => {
    if (e[0] === '#') {
      const code = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10)
      return Number.isFinite(code) ? String.fromCodePoint(code) : m
    }
    return ENTITIES[e.toLowerCase()] ?? m
  })
}

interface MiniNode { tag: string; attrs: Record<string, string>; kids: (MiniNode | string)[] }

/** Lector de XML tolerante (para feeds mal formados que DOMParser rechaza, o fuera del navegador). */
function miniParse(xml: string): XEl[] {
  const root: MiniNode = { tag: '#root', attrs: {}, kids: [] }
  const stack: MiniNode[] = [root]
  const re = /<!\[CDATA\[([\s\S]*?)\]\]>|<!--[\s\S]*?-->|<\?[\s\S]*?\?>|<!DOCTYPE[^>]*>|<(\/?)([A-Za-z_][\w:.-]*)((?:\s+[^\s=>/]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*(\/?)>|([^<]+|<)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(xml))) {
    const top = stack[stack.length - 1]
    if (m[1] !== undefined) top.kids.push(m[1])
    else if (m[3] !== undefined) {
      if (m[2] === '/') {
        const i = stack.map((n) => n.tag).lastIndexOf(m[3])
        if (i > 0) stack.length = i
      } else {
        const attrs: Record<string, string> = {}
        for (const a of (m[4] ?? '').matchAll(/([^\s=]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g)) attrs[a[1]] = decode(a[2] ?? a[3] ?? a[4] ?? '')
        const node: MiniNode = { tag: m[3], attrs, kids: [] }
        top.kids.push(node)
        if (!m[5]) stack.push(node)
      }
    } else if (m[6] !== undefined) top.kids.push(decode(m[6]))
  }
  const wrap = (n: MiniNode): XEl => ({
    tag: n.tag,
    attr: (a) => n.attrs[a] ?? null,
    text: () => n.kids.map((k) => (typeof k === 'string' ? k : wrap(k).text())).join(''),
    children: () => n.kids.filter((k): k is MiniNode => typeof k !== 'string').map(wrap),
  })
  const items: XEl[] = []
  const walk = (n: MiniNode) => { for (const k of n.kids) if (typeof k !== 'string') { if (k.tag === 'item') items.push(wrap(k)); else walk(k) } }
  walk(root)
  return items
}

function rssItems(xml: string): XEl[] {
  if (typeof DOMParser !== 'undefined') {
    try {
      const doc = new DOMParser().parseFromString(xml, 'text/xml')
      if (!doc.getElementsByTagName('parsererror').length) return Array.from(doc.getElementsByTagName('item')).map(fromDom)
    } catch { /* se usa el lector de reserva */ }
  }
  return miniParse(xml)
}

const squash = (s: string) => s.replace(/\s+/g, ' ').trim()

/** Fecha RFC 1123 del RSS; también "2026-11-18 09:37:35" (Investing, en UTC), que Safari no entiende solo. */
function parseDate(raw: string): number {
  const s = raw.trim()
  if (!s) return NaN
  const iso = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}(?::\d{2})?)$/.exec(s)
  return Date.parse(iso ? `${iso[1]}T${iso[2]}Z` : s)
}

/** Lee un RSS 2.0. Los titulares de Google Noticias acaban en " - Medio": se separa. */
export function parse(xml: string, topic: string): NewsItem[] {
  const out: NewsItem[] = []
  for (const item of rssItems(xml)) {
    const f: Record<string, string> = {}
    let image: string | undefined
    let imageTag = ''
    for (const c of item.children()) {
      const tag = c.tag
      if (['title', 'link', 'pubDate', 'description', 'source', 'category'].includes(tag)) {
        f[tag] = (f[tag] ?? '') + ' ' + c.text()
      } else if (tag === 'media:content' || tag === 'media:thumbnail' || tag === 'enclosure') {
        const u = c.attr('url')
        const type = (c.attr('type') ?? '') + (c.attr('medium') ?? '')
        const isImage = (type.trim() === '' && tag !== 'enclosure') || type.includes('image')
        // Mejor la foto grande (media:content) que la miniatura.
        if (u && u.startsWith('http') && isImage && (image == null || (tag === 'media:content' && imageTag !== 'media:content'))) {
          image = u; imageTag = tag
        }
      }
    }
    const rawTitle = squash(f.title ?? '')
    const sourceTag = squash(f.source ?? '')
    const dash = rawTitle.lastIndexOf(' - ')
    const source = sourceTag || (dash >= 0 ? rawTitle.slice(dash + 3).trim() : '')
    const title = source && rawTitle.endsWith(` - ${source}`) ? rawTitle.slice(0, -(` - ${source}`).length).trim() : rawTitle
    const parsedMs = parseDate(f.pubDate ?? '')
    const desc = f.description ?? ''
    const img = image ?? (/<img[^>]+src=["']([^"']+)/.exec(desc)?.[1])
    const summaryRaw = squash(desc.replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' '))
    const summary = summaryRaw && !summaryRaw.startsWith(title.slice(0, 30)) ? summaryRaw : ''
    const link = (f.link ?? '').trim()
    const n: NewsItem = {
      title, source: source || sourceOf(link), url: link, epochMs: Number.isFinite(parsedMs) ? parsedMs : 0,
      summary: summary.slice(0, 280), topic, imageUrl: img && img.startsWith('http') ? img : null,
    }
    if (title && f.link != null && !isAd(n, f.category ?? '')) out.push(n)
  }
  return out
}

/** Nombre del medio a partir del enlace, para los feeds que no lo dicen. */
function sourceOf(link: string): string {
  if (link.includes('cincodias')) return 'Cinco Días'
  if (link.includes('elpais')) return 'El País'
  if (link.includes('expansion')) return 'Expansión'
  if (link.includes('beincrypto')) return 'BeInCrypto'
  if (link.includes('investing')) return 'Investing.com'
  if (link.includes('criptonoticias')) return 'CriptoNoticias'
  return 'Google Noticias'
}

/** Quita repetidas, viejas (más de 26 h) y anuncios; ordena de más nueva a más vieja. */
export function freshNews(all: NewsItem[], now = Date.now(), max = 60): NewsItem[] {
  const since = now - 26 * 3600_000
  const seen = new Set<string>()
  return all
    .filter((n) => n.epochMs === 0 || n.epochMs >= since)
    .filter((n) => !isAd(n))
    .filter((n) => {
      const k = (n.title.toLowerCase().match(/[\p{L}\p{N}]/gu) ?? []).join('').slice(0, 60)
      if (seen.has(k)) return false
      seen.add(k); return true
    })
    .sort((a, b) => b.epochMs - a.epochMs)
    .slice(0, max)
}

/**
 * Noticias de hoy de todos los feeds (los que fallen se saltan). A diferencia de Android no se descarga la página
 * de cada noticia para sacar su foto (serían decenas de consultas por proxy): las que no traen foto llegan con
 * imageUrl null y se pueden completar con pageImage() solo para las que se vayan a ver.
 * Lanza error solo si no llega ninguna noticia.
 */
export async function today(feeds: readonly Feed[] = FEEDS): Promise<NewsItem[]> {
  const lists = await Promise.all(feeds.map(async (feed) => {
    try { return parse(await fetchText(feed.url, { cors: feed.cors, expect: 'xml' }), feed.topic) } catch { return [] }
  }))
  const okFeeds = lists.filter((l) => l.length > 0).length
  if (okFeeds === 0) throw new Error('Sin conexión: no he podido cargar las noticias')
  return freshNews(lists.flat())
}

const ogCache = new Map<string, Promise<string | null>>()

/** Foto de la propia noticia (la que la web pone para compartir), si la noticia no trae. Va por proxy. */
export function pageImage(url: string): Promise<string | null> {
  if (url.includes('news.google.com') || url.startsWith('https://example.com') || !url.startsWith('https://')) return Promise.resolve(null)
  const cached = ogCache.get(url)
  if (cached) return cached
  const p = (async () => {
    try {
      const html = (await fetchText(url, { cors: 'proxy', expect: 'html' })).slice(0, 300_000)
      const m = /<meta[^>]+(?:property|name)=["'](?:og:image|twitter:image)["'][^>]*content=["']([^"']+)/i.exec(html)
        ?? /<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["'](?:og:image|twitter:image)/i.exec(html)
      const img = m?.[1]?.replace(/&amp;/g, '&')
      return img && img.startsWith('https://') ? img : null
    } catch { return null }
  })()
  ogCache.set(url, p)
  return p
}
