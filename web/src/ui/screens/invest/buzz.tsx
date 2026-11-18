// "¿De qué se está hablando?" (BuzzSheet de InvestmentsScreen.kt) y la tarjeta "Por qué se habla de ella" de la ficha.
import { useMemo } from 'react'
import { BuzzAnalyzer, type BuzzItem } from '../../../domain'
import { useBuzz } from '../../../data/services/actions'
import type { RedditPost } from '../../../data/services/prices'
import { Card, Initials, SERIES_COLORS, Sheet } from '../../components'
import { Icon } from '../../icons'
import { ON_PASTEL, PASTELS, RetryNote, openUrl, signedPct, symbolColor } from './shared'

const SERIES1 = SERIES_COLORS[1]

/**
 * En Android la ficha solo enseña "Por qué se habla de ella" si ya se cargó el buzz en la sesión (al abrir
 * "¿De qué se está hablando?"). Así no se pide Reddit cada vez que se abre una ficha.
 */
export const buzzSession = { requested: false }

export function reason(b: BuzzItem): string {
  return [
    b.mentions > 0 ? `${b.mentions} ${b.mentions === 1 ? 'hilo' : 'hilos'} en Reddit (${b.votes} votos), tono ${b.tone}` : null,
    b.trending ? 'en tendencia en CoinGecko' : null,
    b.change24h != null ? `${signedPct(b.change24h)} en 24 h` : null,
  ].filter(Boolean).join(' · ')
}

const toneColor = (t: string) => (t === 'positivo' ? 'var(--gain)' : t === 'negativo' ? 'var(--loss)' : 'var(--muted)')

function useAnalyzedBuzz() {
  const r = useBuzz()
  const buzz = useMemo(() => (r.data ? BuzzAnalyzer.analyze(r.data.posts, r.data.trending, r.data.mine) : null), [r.data])
  return { ...r, buzz }
}

function PostRow({ h, quoted }: { h: RedditPost; quoted?: boolean }) {
  return (
    <button className="inv-post" onClick={() => openUrl(h.url)}>
      <div className="inv-clamp3 small">{quoted ? `“${h.title}”` : h.title}</div>
      <div className="tiny muted">▲ {h.score} · {h.comments} comentarios · Reddit</div>
    </button>
  )
}

/** Hoja "¿De qué se está hablando?": monedas con más conversación y su tono, y los titulares más votados. */
export function BuzzSheet({ onOpenCoin, onClose }: { onOpenCoin: (b: BuzzItem) => void; onClose: () => void }) {
  buzzSession.requested = true
  const { buzz, loading, error, reload } = useAnalyzedBuzz()
  return (
    <Sheet title="¿De qué se está hablando?" onClose={onClose}>
      <div className="col" style={{ gap: 16 }}>
        <div className="small muted">Lo más buscado en CoinGecko y los hilos más votados de r/CryptoCurrency y r/CryptoMarkets, ahora mismo.</div>
        {loading && !buzz && (
          <div className="row" style={{ gap: 8 }}>
            <span className="inv-spinner" style={{ width: 16, height: 16 }} />
            <span className="small">Leyendo CoinGecko y Reddit…</span>
          </div>
        )}
        {!loading && !buzz && <RetryNote text={error ?? 'No he podido conectar. Inténtalo de nuevo con internet.'} onRetry={reload} />}
        {buzz && (
          <>
            {buzz.featured && (
              <button className="inv-featured" style={{ background: PASTELS[1], color: ON_PASTEL }} onClick={() => onOpenCoin(buzz.featured as BuzzItem)}>
                <div className="tiny" style={{ fontWeight: 700, letterSpacing: 1, opacity: 0.7 }}>MÁS RUIDO POSITIVO</div>
                <div className="row" style={{ gap: 8, marginTop: 4 }}>
                  <Initials text={buzz.featured.symbol} color={symbolColor(buzz.featured.symbol)} size={40} />
                  <div style={{ fontSize: 20, fontWeight: 700 }}>{buzz.featured.name} ({buzz.featured.symbol})</div>
                </div>
                <div className="small" style={{ marginTop: 8 }}>{reason(buzz.featured)}</div>
                <div className="small" style={{ marginTop: 8, fontWeight: 600 }}>Ver gráfico, por qué se habla y dónde comprarla ›</div>
              </button>
            )}
            <div style={{ fontWeight: 600 }}>Monedas de las que se habla</div>
            {buzz.items.length === 0 && <div className="small muted">Ahora mismo no se habla de ninguna moneda en concreto.</div>}
            <div className="col" style={{ gap: 0 }}>
              {buzz.items.slice(0, 8).map((b) => (
                <button key={b.symbol} className="inv-buzz-row" onClick={() => onOpenCoin(b)}>
                  <Initials text={b.symbol} color={symbolColor(b.symbol)} size={40} />
                  <div className="grow" style={{ minWidth: 0, textAlign: 'left' }}>
                    <div className="ellipsis" style={{ fontWeight: 500 }}>{b.name} · {b.symbol}</div>
                    <div className="tiny muted inv-clamp2">{reason(b)}</div>
                  </div>
                  <span className="small" style={{ fontWeight: 600, color: toneColor(b.tone) }}>{b.tone}</span>
                </button>
              ))}
            </div>
            {buzz.headlines.length > 0 && (
              <>
                <div style={{ fontWeight: 600 }}>Titulares más votados</div>
                <div className="col" style={{ gap: 0 }}>{buzz.headlines.map((h) => <PostRow key={h.url} h={h} />)}</div>
              </>
            )}
            <button className="link-btn inv-link" onClick={reload} disabled={loading}>
              <Icon name="refresh" size={16} />{loading ? 'Actualizando…' : 'Actualizar'}
            </button>
          </>
        )}
        <div className="tiny muted">
          Esto resume de qué se habla; no es una recomendación de inversión. Que una moneda tenga buen ambiente no significa que vaya a subir: el hype también hunde precios.
        </div>
      </div>
    </Sheet>
  )
}

/** "Por qué se habla de ella" en la ficha de una cripto (solo si hay conversación sobre ella). */
export function BuzzCard({ coinId, symbol }: { coinId: string; symbol: string }) {
  const { buzz } = useAnalyzedBuzz()
  const b = buzz?.items.find((x) => x.coinId === coinId || x.symbol.toUpperCase() === symbol.toUpperCase())
  if (!b) return null
  const c = toneColor(b.tone)
  return (
    <Card>
      <div className="inv-card-title">Por qué se habla de ella</div>
      <div className="row" style={{ gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
        <span className="inv-tag" style={{ color: c, background: `color-mix(in srgb, ${c} 14%, transparent)` }}>Tono {b.tone}</span>
        {b.trending && <span className="inv-tag" style={{ color: SERIES1, background: `color-mix(in srgb, ${SERIES1} 14%, transparent)` }}>En tendencia</span>}
      </div>
      <div className="small" style={{ marginTop: 8 }}>{reason(b)}</div>
      <div className="col" style={{ gap: 0, marginTop: 4 }}>{b.posts.slice(0, 4).map((h) => <PostRow key={h.url} h={h} quoted />)}</div>
      <div className="tiny muted" style={{ marginTop: 8 }}>Resume la conversación; no es una recomendación de inversión.</div>
    </Card>
  )
}
