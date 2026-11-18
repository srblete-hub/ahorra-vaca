// "¿Lo compro o no?": tarjeta de Inicio y su hoja con el resultado (BuyCheckUi.kt). Reglas fijas, sin IA.
import { useState } from 'react'
import type { BuyCheck } from '../../../domain'
import { SPEND_TAG_INFO } from '../../../domain'
import { fixed } from '../../../domain/kt'
import { Button, parseAmount, Sheet, TextField, useMoney } from '../../components'
import { Icon } from '../../icons'
import { ORANGE, TAG_COLOR, TAG_ICON } from './shared'

export function BuyCheckCard({ onCheck }: { onCheck: (name: string, price: number) => void }) {
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const value = parseAmount(price)
  const ok = value != null && value > 0
  return (
    <div className="card">
      <div className="row" style={{ gap: 16 }}>
        <div className="hm-round40" style={{ background: `color-mix(in srgb, ${ORANGE} 16%, transparent)`, color: ORANGE }}><Icon name="bag" /></div>
        <div className="grow">
          <div style={{ fontSize: 20, fontWeight: 600 }}>¿Lo compro o no?</div>
          <div className="small muted">Mira lo que te cuesta de verdad antes de pagar</div>
        </div>
      </div>
      <form className="col" style={{ marginTop: 16 }} onSubmit={(e) => { e.preventDefault(); if (ok) onCheck(name.trim() || 'Esta compra', value!) }}>
        <TextField label="Qué es (ej.: camiseta Eme Studio)" value={name} onChange={setName} />
        <TextField label="Cuánto cuesta (€)" value={price} onChange={(v) => setPrice(v.replace(/[^0-9.,]/g, ''))} inputMode="decimal" />
        <Button type="submit" disabled={!ok} style={{ marginTop: 8 }}>Simular</Button>
      </form>
    </div>
  )
}

/** El resultado: veredicto, horas de trabajo, cómo queda tu mes, tu límite, tu objetivo y si lo invirtieras. */
export function BuyCheckSheet({ c, onClose }: { c: BuyCheck; onClose: () => void }) {
  const m = useMoney()
  const [vColor, vTitle] = c.verdict === 0 ? ['var(--gain)', 'Puedes'] : c.verdict === 1 ? [ORANGE, 'Piénsalo'] : ['var(--loss)', 'Mejor no ahora']
  const tc = TAG_COLOR[c.tag]
  return (
    <Sheet title="¿Lo compro?" onClose={onClose}>
      <div className="col" style={{ gap: 16 }}>
        <div className="row" style={{ gap: 16 }}>
          <div className="hm-round48" style={{ background: `color-mix(in srgb, ${tc} 14%, transparent)`, color: tc }}><Icon name={TAG_ICON[c.tag]} /></div>
          <div className="grow">
            <div className="hm-clamp2" style={{ fontSize: 20, fontWeight: 600 }}>{c.name}</div>
            <div className="small muted">{SPEND_TAG_INFO[c.tag].label}</div>
          </div>
          <div className="num" style={{ fontSize: 24, fontWeight: 700 }}>{m.fmt(c.price)}</div>
        </div>
        <div className="hm-verdict" style={{ background: `color-mix(in srgb, ${vColor} 14%, transparent)` }}>
          <div style={{ fontSize: 24, fontWeight: 700, color: vColor }}>{vTitle}</div>
          <div style={{ marginTop: 4 }}>{c.verdictText}</div>
        </div>
        <div className="hm-facts">
          {c.facts.map((f, i) => (
            <div key={i} className="hm-fact">
              <div className="row" style={{ gap: 8 }}>
                <span className="grow small muted">{f.title}</span>
                <span className="num" style={{ fontWeight: 600 }}>{f.value}</span>
              </div>
              <div className="tiny muted" style={{ marginTop: 4 }}>{f.detail}</div>
            </div>
          ))}
        </div>
        <div className="hm-facts" style={{ padding: 16 }}>
          <div className="small muted">Si en vez de comprarlo lo invirtieras al {fixed(c.rate, 1).replace(',0', '')} %</div>
          <div className="row" style={{ justifyContent: 'space-between', marginTop: 8 }}>
            {c.invested.map(([y, v]) => (
              <div key={y} style={{ textAlign: 'center' }}>
                <div className="num gain" style={{ fontWeight: 600 }}>{m.fmt(v)}</div>
                <div className="tiny muted">{y === 1 ? 'en 1 año' : `en ${y} años`}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="tiny muted">Cálculo con tus datos y reglas fijas (sin IA). La decisión es tuya.</div>
        <Button onClick={onClose}>Entendido</Button>
      </div>
    </Sheet>
  )
}
