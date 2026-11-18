// Pestaña Inversión (InvestmentsScreen de InvestmentsScreen.kt): valor actual, favoritas en tarjetas de color,
// la lista de activos y el mercado (populares aunque no los tengas). Sin comprar ni vender.
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSettings } from '../../../data/store'
import { refreshCrypto, refreshIfStale, refreshStocks, useMarkets, useRefreshing } from '../../../data/services/actions'
import type { BuzzItem } from '../../../domain'
import { Button, Initials, Segmented, useMoney } from '../../components'
import { ScreenHeader } from '../../header'
import { Icon } from '../../icons'
import { useNav, type InvestKind } from '../../nav'
import { BuzzSheet } from './buzz'
import {
  CRYPTO_MARKET, CRYPTO_MARKET_IDS, ChangeChip, HistProbe, MARKET, ON_PASTEL, PASTELS, RetryNote, Spark, UP_BLUE,
  changeOf, gainColor, markViewed, signedPct, symbolColor, usd, usePositions, type HistState, type Position,
} from './shared'

const KINDS: InvestKind[] = ['CRYPTO', 'STOCKS']
const KIND_LABEL: Record<InvestKind, string> = { CRYPTO: 'Cripto', STOCKS: 'Acciones' }

/** Una fila del mercado: id (ticker o id de CoinGecko), etiqueta corta, nombre, precio, cambio y minigráfico. */
interface MarketRow { id: string; label: string; name: string; price: number | null; change: number | null; spark: number[] }

export function InvestmentsScreen() {
  const nav = useNav()
  const kind = nav.investKind
  const money = useMoney()
  const settings = useSettings()
  const refreshing = useRefreshing()
  const positions = usePositions(kind)
  const [buzzOpen, setBuzzOpen] = useState(false)

  useEffect(() => { refreshIfStale() }, [kind])

  // Cripto: precios y minigráficos de una sola llamada al mercado (CoinGecko gratis limita las consultas).
  const marketIds = useMemo(
    () => [...new Set([...CRYPTO_MARKET_IDS, ...positions.filter((p) => p.isCrypto).map((p) => p.apiId)])],
    [positions],
  )
  const markets = useMarkets(kind === 'CRYPTO' ? marketIds : [])

  // Acciones: historial de 1 día de las tuyas y de las populares (Yahoo, por proxy).
  const [hist, setHist] = useState<Record<string, HistState>>({})
  const onHist = useCallback((sym: string, s: HistState) => setHist((h) => ({ ...h, [sym]: s })), [])
  const stockTickers = useMemo(
    () => (kind === 'STOCKS' ? [...new Set([...positions.map((p) => p.symbol), ...MARKET.map((m) => m[0])])] : []),
    [kind, positions],
  )

  /** Minigráfico de 24 h de una posición. */
  const sparkOf = (p: Position): number[] => (p.isCrypto ? markets.data?.find((m) => m.id === p.apiId)?.spark ?? [] : hist[p.symbol]?.values ?? [])
  const coinUsd = (p: Position) => (p.isCrypto ? markets.data?.find((m) => m.id === p.apiId)?.price ?? null : null)
  const dayChange = (p: Position) => settings.dayChanges[p.symbol] ?? changeOf(sparkOf(p))

  const marketRows: MarketRow[] = kind === 'STOCKS'
    ? MARKET.map(([t, n]) => {
      const h = hist[t]?.values ?? []
      return { id: t, label: t.split('.')[0], name: n, price: h.length ? h[h.length - 1] : null, change: changeOf(h), spark: h }
    })
    : CRYPTO_MARKET.map(([id, sym, n]) => {
      const m = markets.data?.find((x) => x.id === id)
      return { id, label: sym, name: n, price: m?.price ?? null, change: m?.change24h ?? null, spark: m?.spark ?? [] }
    })
  const stockErrors = kind === 'STOCKS' && MARKET.every(([t]) => hist[t] && !hist[t].loading && hist[t].error)
  const marketError = kind === 'CRYPTO' ? (!markets.data && !markets.loading ? markets.error ?? 'Sin conexión' : null) : stockErrors ? 'Sin conexión con Yahoo Finance' : null
  const retryMarket = () => {
    if (kind === 'CRYPTO') markets.reload()
    else MARKET.forEach(([t]) => hist[t]?.reload())
  }

  const openMarket = (id: string) => {
    markViewed(id)
    const owned = kind === 'STOCKS'
      ? positions.find((p) => !p.isCrypto && p.symbol.toUpperCase() === id.toUpperCase())
      : positions.find((p) => p.isCrypto && p.apiId === id)
    nav.open({ name: 'holding', key: owned?.key ?? (kind === 'STOCKS' ? `s:${id}` : `c:${id}`) })
  }
  const openBuzzCoin = (b: BuzzItem) => {
    const id = b.coinId ?? CRYPTO_MARKET.find((c) => c[1].toUpperCase() === b.symbol.toUpperCase())?.[0]
    if (!id) return
    setBuzzOpen(false)
    markViewed(id)
    const known = CRYPTO_MARKET.some((c) => c[0] === id)
    nav.open({ name: 'holding', key: known ? `c:${id}` : `c:${id}|${b.symbol}|${b.name}` })
  }

  const value = positions.reduce((a, p) => a + p.value, 0)
  const cost = positions.reduce((a, p) => a + p.cost, 0)
  const series = positions.map((p) => sparkOf(p).map((v) => v * p.qty))
  let portfolio: number[] = []
  if (series.length > 0 && series.every((s) => s.length >= 2)) {
    const n = Math.min(...series.map((s) => s.length))
    portfolio = Array.from({ length: n }, (_, i) => series.reduce((a, s) => a + s[s.length - n + i], 0))
  }

  const text = money.fmt(value)
  const cut = money.hidden ? -1 : text.lastIndexOf(',')
  const marketNote = kind === 'STOCKS'
    ? 'Precios de Yahoo Finance, con retraso. Solo para mirar: aquí no se compra ni se vende.'
    : 'Precio de cada moneda en dólares (CoinGecko). Lo que tienes tú, en euros. Solo para mirar: aquí no se compra ni se vende.'
  const marketFmt = kind === 'STOCKS' ? money.fmt : usd

  return (
    <div className="screen">
      {stockTickers.map((t) => <HistProbe key={t} id={t} symbol={t} isCrypto={false} days={1} onData={onHist} />)}
      <ScreenHeader title="Inversiones" />
      <Segmented options={KINDS} value={kind} onChange={(k) => nav.openInvest(k)} label={(k) => KIND_LABEL[k]} />

      {/* ---------- Valor actual ---------- */}
      <div className="col" style={{ gap: 4, padding: '0 4px' }}>
        <div className="row" style={{ gap: 8 }}>
          <div className="grow muted" style={{ fontSize: 20 }}>Valor actual</div>
          {refreshing
            ? <span className="inv-spinner" aria-label="Actualizando…" />
            : <button className="icon-btn filled" aria-label="Actualizar precios" onClick={() => {
              if (kind === 'CRYPTO') { void refreshCrypto(); markets.reload() } else { void refreshStocks(); MARKET.forEach(([t]) => hist[t]?.reload()) }
            }}><Icon name="refresh" /></button>}
        </div>
        <div className="row" style={{ gap: 8, minWidth: 0 }}>
          <div className="hero-num inv-value">
            {cut > 0 ? <>{text.slice(0, cut)}<span className="dec">{text.slice(cut)}</span></> : text}
          </div>
          {portfolio.length >= 2 && <Spark values={portfolio} color={portfolio[portfolio.length - 1] >= portfolio[0] ? 'var(--gain)' : 'var(--loss)'} width={56} height={32} />}
        </div>
        {cost > 0 && (
          <div style={{ fontSize: 16, fontWeight: 500, color: gainColor(value - cost) }}>
            {money.signed(value - cost)} ({signedPct(((value - cost) / cost) * 100)}) desde que compraste
          </div>
        )}
      </div>

      {/* ---------- ¿De qué se está hablando? (solo cripto) ---------- */}
      {kind === 'CRYPTO' && (
        <button className="inv-buzz-card" onClick={() => setBuzzOpen(true)}>
          <Icon name="news" size={32} />
          <div className="grow" style={{ minWidth: 0, textAlign: 'left' }}>
            <div style={{ fontWeight: 600 }}>¿De qué se está hablando?</div>
            <div className="small" style={{ opacity: 0.7 }}>Tendencias de CoinGecko y lo más votado en Reddit</div>
          </div>
          <span style={{ fontWeight: 600, color: PASTELS[1] }}>Ver</span>
        </button>
      )}

      {positions.length === 0 ? (
        <div className="card inv-empty">
          <div className="row" style={{ gap: 16, alignItems: 'flex-start' }}>
            <div className="ico" style={{ width: 48, height: 48, borderRadius: 16, background: 'color-mix(in srgb, var(--brand) 14%, transparent)', color: 'var(--brand)' }}>
              <Icon name={kind === 'CRYPTO' ? 'bitcoin' : 'trendingUp'} />
            </div>
            <div className="grow">
              <div style={{ fontWeight: 700 }}>{kind === 'CRYPTO' ? 'Sin criptomonedas' : 'Sin acciones'}</div>
              <div className="small muted" style={{ marginTop: 4 }}>
                {kind === 'CRYPTO'
                  ? 'Pulsa + y pon cuánto dinero tienes en cada una: la cantidad se calcula sola con el precio en vivo.'
                  : 'Pulsa + y añade tus acciones o ETF con su ticker (AAPL, SAN.MC, VWCE.DE…).'}
              </div>
            </div>
          </div>
          <Button small icon="add" onClick={() => nav.edit(kind === 'CRYPTO' ? { name: 'coinSearch' } : { name: 'stock' })}>
            {kind === 'CRYPTO' ? 'Añadir cripto' : 'Añadir acción'}
          </Button>
        </div>
      ) : (
        <>
          {/* ---------- Favoritas: tarjetas de color ---------- */}
          <div className="section-label">Favoritas</div>
          <div className="hscroll">
            {positions.slice(0, 5).map((p, i) => {
              const usdPrice = coinUsd(p)
              const ch = dayChange(p)
              return (
                <button key={p.key} className="inv-fav" style={{ background: PASTELS[i % PASTELS.length], color: ON_PASTEL }} onClick={() => nav.open({ name: 'holding', key: p.key })}>
                  <div className="row" style={{ gap: 8, minWidth: 0 }}>
                    <Initials text={p.symbol} color={symbolColor(p.symbol)} size={32} />
                    <div style={{ minWidth: 0, textAlign: 'left' }}>
                      <div className="ellipsis" style={{ fontWeight: 600 }}>{p.name}</div>
                      <div className="tiny" style={{ opacity: 0.6 }}>{p.symbol}</div>
                    </div>
                  </div>
                  <div style={{ margin: '8px 0' }}><Spark values={sparkOf(p)} color="#fff" height={56} /></div>
                  {p.isCrypto && <div style={{ fontSize: 11, opacity: 0.6 }}>Lo tuyo</div>}
                  <div className="num" style={{ fontSize: 20, fontWeight: 700 }}>{money.fmt(p.value)}</div>
                  {usdPrice != null && <div className="tiny ellipsis" style={{ opacity: 0.7 }}>1 {p.symbol} = {usd(usdPrice)}</div>}
                  {ch != null && <div style={{ marginTop: 4 }}><ChangeChip pct={ch} dark /></div>}
                </button>
              )
            })}
          </div>

          {/* ---------- Activos ---------- */}
          <div className="section-label">Activos</div>
          <div className="col" style={{ gap: 8 }}>
            {positions.map((p) => {
              const h = sparkOf(p)
              const usdPrice = coinUsd(p)
              return (
                <button key={p.key} className="card inv-asset" onClick={() => nav.open({ name: 'holding', key: p.key })}>
                  <Initials text={p.symbol} color={symbolColor(p.symbol)} size={40} />
                  <div className="grow" style={{ minWidth: 0, textAlign: 'left' }}>
                    <div className="ellipsis" style={{ fontWeight: 600 }}>{p.name}</div>
                    <div className="small muted ellipsis">{p.symbol}{usdPrice != null ? ` · precio ${usd(usdPrice)}` : ''}</div>
                  </div>
                  {h.length >= 2 && <Spark values={h} color={h[h.length - 1] >= h[0] ? 'var(--gain)' : 'var(--loss)'} width={56} height={24} />}
                  <div className="right" style={{ flexShrink: 0 }}>
                    {p.isCrypto && <div style={{ fontSize: 11 }} className="muted">lo tuyo</div>}
                    <div className="num" style={{ fontWeight: 600 }}>{money.fmt(p.value)}</div>
                    {p.cost > 0 && <div className="small" style={{ color: gainColor(p.pnl) }}>{signedPct((p.pnl / p.cost) * 100)}</div>}
                  </div>
                </button>
              )
            })}
          </div>
        </>
      )}

      <MarketItems rows={marketRows} recent={settings.recentTickers} onOpen={openMarket} note={marketNote} fmt={marketFmt} error={marketError} onRetry={retryMarket} />

      {buzzOpen && <BuzzSheet onOpenCoin={openBuzzCoin} onClose={() => setBuzzOpen(false)} />}
    </div>
  )
}

const arrowPct = (v: number) => (v >= 0 ? '▲ ' : '▼ ') + signedPct(Math.abs(v)).replace(/^\+/, '')

/** Vistas recientemente (tarjetas) y las más populares (lista), con precio y cambio del día. */
function MarketItems({ rows, recent, onOpen, note, fmt, error, onRetry }: {
  rows: MarketRow[]; recent: string[]; onOpen: (id: string) => void; note: string; fmt: (v: number) => string; error: string | null; onRetry: () => void
}) {
  const seen = recent.filter((r) => rows.some((x) => x.id === r))
  const cards = [...new Set([...seen, ...rows.map((r) => r.id)])].slice(0, 6).map((id) => rows.find((r) => r.id === id) as MarketRow)
  const price = (r: MarketRow) => (r.price != null ? fmt(r.price) : error ? '—' : '…')
  return (
    <>
      <div className="inv-h2">{seen.length === 0 ? 'Para echar un vistazo' : 'Vistas recientemente'}</div>
      {error && <RetryNote text={`${error}. Los precios del mercado no han cargado.`} onRetry={onRetry} />}
      <div className="hscroll">
        {cards.map((r) => (
          <button key={r.id} className="card inv-mcard" onClick={() => onOpen(r.id)}>
            <div className="row" style={{ gap: 8 }}>
              <Initials text={r.label} color={symbolColor(r.label)} size={40} />
              <div className="grow" style={{ minWidth: 0 }}>
                {r.spark.length >= 2 && <Spark values={r.spark} color={r.change != null && r.change < 0 ? 'var(--loss)' : UP_BLUE} height={32} />}
              </div>
            </div>
            <div style={{ marginTop: 16, fontWeight: 700 }}>{r.label}</div>
            <div className="small muted ellipsis">{r.name}</div>
            <div className="row" style={{ marginTop: 8, gap: 4 }}>
              <div className="grow num ellipsis" style={{ fontWeight: 600 }}>{price(r)}</div>
              {r.change != null && <span className="tiny" style={{ color: r.change >= 0 ? UP_BLUE : 'var(--loss)', flexShrink: 0 }}>{arrowPct(r.change)}</span>}
            </div>
          </button>
        ))}
      </div>
      <div className="inv-h2">Las más populares</div>
      <div className="card inv-list">
        {rows.map((r) => (
          <button key={r.id} className="inv-mrow" onClick={() => onOpen(r.id)}>
            <Initials text={r.label} color={symbolColor(r.label)} size={40} />
            <div className="grow" style={{ minWidth: 0, textAlign: 'left' }}>
              <div className="ellipsis" style={{ fontWeight: 600 }}>{r.name}</div>
              <div className="small muted">{r.label}</div>
            </div>
            <div className="right" style={{ flexShrink: 0 }}>
              <div className="num" style={{ fontWeight: 600 }}>{price(r)}</div>
              {r.change != null && <div className="small" style={{ color: r.change >= 0 ? UP_BLUE : 'var(--loss)' }}>{arrowPct(r.change)}</div>}
            </div>
          </button>
        ))}
      </div>
      <div className="tiny muted" style={{ padding: '0 4px' }}>{note}</div>
    </>
  )
}
