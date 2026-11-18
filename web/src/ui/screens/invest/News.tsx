// Noticias de hoy (NewsScreen.kt): una por pantalla, deslizando hacia arriba (como TikTok), sin repetir las que
// ya viste, con "¿Cómo me afecta?" (NewsImpactAnalyzer, reglas fijas, sin IA).
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { db, getState, toast } from '../../../data/store'
import { useMarkets, useNews } from '../../../data/services/actions'
import { pageImage, type NewsItem } from '../../../data/services/news'
import type { MarketCoin } from '../../../data/services/prices'
import { NewsImpactAnalyzer, usePlanning, type FinanceData, type NewsImpact } from '../../../domain'
import { Button, Sheet, useMoney } from '../../components'
import { Icon, type IconName } from '../../icons'
import { useNav, type OverlayRoute } from '../../nav'
import { CRYPTO_MARKET_IDS, Spark, fixed, ktHash, usd } from './shared'

const TOPIC_COLORS: Record<string, [string, string]> = {
  Cripto: ['#1E3A8A', '#0B1022'],
  Bolsa: ['#0F3D2E', '#07130F'],
  Empresas: ['#3B1F5C', '#110A1C'],
  Economía: ['#1F2937', '#0A0D12'],
}

function ago(ms: number): string {
  if (ms <= 0) return 'hoy'
  const min = Math.floor((Date.now() - ms) / 60_000)
  if (min < 1) return 'ahora'
  if (min < 60) return `hace ${min} min`
  if (min < 24 * 60) return `hace ${Math.floor(min / 60)} h`
  return 'ayer'
}

// ---------- Vistas (hoy o ayer), como Prefs.seenNews / markNewsSeen ----------
const isoDay = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
function keepDays(): Set<string> {
  const now = new Date()
  const y = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  return new Set([isoDay(now), isoDay(y)])
}
const newsId = (url: string) => String(ktHash(url))
function seenNews(): Set<string> {
  const keep = keepDays()
  return new Set(getState().settings.seenNews.filter((s) => keep.has(s.split('|')[0])).map((s) => s.slice(s.indexOf('|') + 1)))
}
function markNewsSeen(url: string) {
  const id = newsId(url)
  const keep = keepDays()
  const now = getState().settings.seenNews.filter((s) => keep.has(s.split('|')[0]))
  if (now.some((s) => s.endsWith(`|${id}`))) {
    if (now.length !== getState().settings.seenNews.length) db.setSettings({ seenNews: now })
    return
  }
  db.setSettings({ seenNews: [...now, `${isoDay(new Date())}|${id}`] })
}

export function NewsScreen({ route: _route }: { route: OverlayRoute }) {
  const nav = useNav()
  const news = useNews()
  const markets = useMarkets(CRYPTO_MARKET_IDS)
  const planning = usePlanning()
  // Las que ya viste (hoy o ayer) no se repiten al volver a entrar.
  const [seenAtOpen] = useState(seenNews)
  const all = news.data
  const items = useMemo(() => all?.filter((n) => !seenAtOpen.has(newsId(n.url))), [all, seenAtOpen])
  const [impactOf, setImpactOf] = useState<[NewsItem, NewsImpact] | null>(null)
  const [page, setPage] = useState(0)
  const feed = useRef<HTMLDivElement>(null)

  // Pantalla completa: sin scroll detrás y "Escape" cierra.
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape' && !document.querySelector('.sheet')) nav.back() }
    window.addEventListener('keydown', h)
    return () => { document.body.style.overflow = prev; window.removeEventListener('keydown', h) }
  }, [nav])

  useEffect(() => {
    const n = items?.[page]
    if (n) markNewsSeen(n.url)
  }, [items, page])

  const onScroll = useCallback(() => {
    const el = feed.current
    if (!el || el.clientHeight === 0) return
    setPage(Math.round(el.scrollTop / el.clientHeight))
  }, [])

  let body
  if (!items && news.loading) {
    body = (
      <div className="nw-center">
        <span className="inv-spinner light" style={{ width: 32, height: 32 }} />
        <div style={{ marginTop: 16, opacity: 0.8 }}>Buscando las noticias de hoy…</div>
      </div>
    )
  } else if (!items) {
    body = <EndPage title="No he podido cargar noticias" sub="Comprueba la conexión y vuelve a intentarlo." onRetry={news.reload} />
  } else if (items.length === 0) {
    body = <EndPage title="Te has quedado sin noticias" sub={all && all.length > 0 ? 'Ya has visto todas las de hoy. Vuelve más tarde.' : 'Hoy aún no hay noticias.'} onRetry={all && all.length > 0 ? undefined : news.reload} />
  } else {
    body = (
      <div ref={feed} className="nw-feed" onScroll={onScroll}>
        {items.map((n, i) => (
          <div key={n.url} className="nw-page">
            {Math.abs(i - page) <= 2
              ? <NewsPage n={n} data={planning.data} near={Math.abs(i - page) <= 1} coins={markets.data} onImpact={(imp) => setImpactOf([n, imp])} />
              : null}
          </div>
        ))}
        <div className="nw-page"><EndPage title="Te has quedado sin noticias" sub="Has visto todas las de hoy. Vuelve mañana." /></div>
      </div>
    )
  }

  return createPortal(
    <div className="nw-root" role="dialog" aria-modal="true" aria-label="Noticias de hoy">
      {body}
      <div className="nw-top">
        <div className="grow" style={{ fontWeight: 700 }}>Noticias de hoy</div>
        <button className="nw-close" aria-label="Cerrar" onClick={nav.back}><Icon name="close" /></button>
      </div>
      {impactOf && <ImpactSheet n={impactOf[0]} i={impactOf[1]} onClose={() => setImpactOf(null)} />}
    </div>,
    document.body,
  )
}

function NewsPage({ n, data, near, coins, onImpact }: { n: NewsItem; data: FinanceData; near: boolean; coins: MarketCoin[] | undefined; onImpact: (i: NewsImpact) => void }) {
  const money = useMoney()
  const impact = useMemo(() => NewsImpactAnalyzer.analyze(n.title, n.summary, data, money.fmt), [n, data, money.hidden, money.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const coinSym = useMemo(() => NewsImpactAnalyzer.coinsIn(n.title + ' ' + n.summary)[0], [n])
  const coin = coinSym ? coins?.find((c) => c.symbol.toUpperCase() === coinSym.toUpperCase()) : undefined
  // Foto de la noticia; si no trae, la de su página; si tampoco, un gráfico.
  const [img, setImg] = useState<string | null>(n.imageUrl ?? null)
  const [broken, setBroken] = useState(false)
  useEffect(() => {
    if (img || !near) return
    let alive = true
    void pageImage(n.url).then((u) => { if (alive && u) setImg(u) })
    return () => { alive = false }
  }, [img, near, n.url])
  const colors = TOPIC_COLORS[n.topic] ?? TOPIC_COLORS['Economía']

  const share = async () => {
    const text = `${n.title}\n${n.url}`
    try {
      if (navigator.share) { await navigator.share({ title: n.title, text: n.title, url: n.url }); return }
      await navigator.clipboard.writeText(text)
      toast('Enlace copiado')
    } catch { /* cancelado */ }
  }

  return (
    <div className="nw-card" style={{ background: `linear-gradient(180deg, ${colors[0]}, ${colors[1]})` }}>
      <div className="nw-media">
        {img && !broken
          ? <img src={img} alt="" className="nw-img" onError={() => setBroken(true)} referrerPolicy="no-referrer" />
          : coin && coin.spark.length >= 2 ? <CoinGraphic c={coin} /> : <TopicGraphic topic={n.topic} />}
        <div className="nw-shade" style={{ background: `linear-gradient(180deg, rgba(0,0,0,0.45), transparent 50%, ${colors[0]})` }} />
      </div>
      <div className="nw-text">
        <div className="row" style={{ gap: 8 }}>
          <span className="nw-badge">{n.topic.toUpperCase()}</span>
          {impact.relevant && <span className="nw-badge yellow">TE AFECTA</span>}
        </div>
        <div className="nw-title">{n.title}</div>
        {n.summary.trim() && <div className="nw-summary">{n.summary}</div>}
        <div className="nw-meta">{n.source} · {ago(n.epochMs)}</div>
        <div className="nw-hint">Desliza hacia arriba para la siguiente</div>
      </div>
      <div className="nw-side">
        <SideButton icon="person" label={<>¿Cómo me<br />afecta?</>} highlight={impact.relevant} onClick={() => onImpact(impact)} />
        <SideButton icon="openInNew" label="Leer" onClick={() => window.open(n.url, '_blank', 'noopener,noreferrer')} />
        <SideButton icon="share" label="Compartir" onClick={() => void share()} />
      </div>
    </div>
  )
}

/** Gráfico de la cripto de la que habla la noticia (24 h, en dólares). */
function CoinGraphic({ c }: { c: MarketCoin }) {
  const up = (c.change24h ?? 0) >= 0
  return (
    <div className="nw-coin">
      <div style={{ fontWeight: 600, opacity: 0.85 }}>{c.name} · {c.symbol}</div>
      <div className="nw-coin-price">
        {usd(c.price)}{c.change24h != null ? `  ${c.change24h >= 0 ? '▲ ' : '▼ '}${fixed(Math.abs(c.change24h), 1)} %` : ''}
      </div>
      <div className="grow" style={{ marginTop: 16, minHeight: 0 }}>
        <Spark values={c.spark} color={up ? '#4ADE80' : '#FF6B6B'} height={160} fill strokeWidth={4} />
      </div>
      <div className="tiny" style={{ opacity: 0.6 }}>Últimas 24 h</div>
    </div>
  )
}

/** Sin foto: un gráfico grande con el tema de la noticia. */
function TopicGraphic({ topic }: { topic: string }) {
  const icon: IconName = topic === 'Cripto' ? 'bitcoin' : topic === 'Bolsa' ? 'line' : topic === 'Empresas' ? 'business' : 'bank'
  return (
    <div className="nw-topic">
      <span className="nw-ring" style={{ width: 232, height: 232, opacity: 0.05 }} />
      <span className="nw-ring" style={{ width: 160, height: 160, opacity: 0.07 }} />
      <Icon name={icon} size={96} style={{ position: 'relative', opacity: 0.85 }} />
    </div>
  )
}

function SideButton({ icon, label, highlight, onClick }: { icon: IconName; label: ReactNode; highlight?: boolean; onClick: () => void }) {
  return (
    <button className="nw-side-btn" onClick={onClick}>
      <span className={`nw-side-ico ${highlight ? 'on' : ''}`}><Icon name={icon} size={32} /></span>
      <span className="nw-side-label">{label}</span>
    </button>
  )
}

function EndPage({ title, sub, onRetry }: { title: string; sub: string; onRetry?: () => void }) {
  return (
    <div className="nw-center" style={{ padding: 32 }}>
      <div style={{ fontSize: 56 }} aria-hidden="true">🐄</div>
      <div className="nw-end-title">{title}</div>
      <div style={{ marginTop: 8, opacity: 0.7 }}>{sub}</div>
      {onRetry && (
        <button className="nw-retry" onClick={onRetry}><Icon name="refresh" size={20} />Reintentar</button>
      )}
    </div>
  )
}

/** La respuesta a "¿Cómo puede afectarme?": tus números al lado de la noticia. */
function ImpactSheet({ n, i, onClose }: { n: NewsItem; i: NewsImpact; onClose: () => void }) {
  return (
    <Sheet title="¿Cómo puede afectarte?" onClose={onClose}>
      <div className="col" style={{ gap: 16 }}>
        <div className="small muted inv-clamp3">{n.title}</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: i.relevant ? 'var(--ink)' : 'var(--muted)' }}>{i.headline}</div>
        {i.tags.length > 0 && <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>{i.tags.map((t) => <span key={t} className="pill">{t}</span>)}</div>}
        {i.lines.length > 0 && (
          <div className="col" style={{ gap: 8 }}>
            {i.lines.map((l, k) => (
              <div key={k} className="row" style={{ gap: 0, alignItems: 'flex-start' }}>
                <span style={{ width: 16, color: 'var(--gain)', flexShrink: 0 }}>•</span>
                <span style={{ lineHeight: 1.5 }}>{l}</span>
              </div>
            ))}
          </div>
        )}
        <div className="tiny muted">Cálculo con tus datos de la app y reglas fijas (sin IA). No es una recomendación de inversión.</div>
        <Button onClick={onClose}>Entendido</Button>
      </div>
    </Sheet>
  )
}
