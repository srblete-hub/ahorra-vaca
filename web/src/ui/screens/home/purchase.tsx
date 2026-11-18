// "¿Me lo compro?": cómo afecta una compra a tu mes y a tus objetivos, con tres opciones (SmartCards.kt + RealPriceCard de SignalCards.kt).
import { useMemo, useState } from 'react'
import { SYMBOLS } from '../../../data/format'
import type { RealPrice } from '../../../domain'
import { PurchaseSimulator, RealPriceCalculator, usePlanning } from '../../../domain'
import { fixed } from '../../../domain/kt'
import { AmountField, BigAmountInput, Overlay, parseAmount, useMoney } from '../../components'
import { useNav } from '../../nav'
import { MintCard, ORANGE, Overline } from './shared'

export function PurchaseScreen(_: { route: unknown }) {
  const nav = useNav()
  const p = usePlanning()
  const m = useMoney()
  const [price, setPrice] = useState('')
  const [cheaper, setCheaper] = useState('')
  const value = parseAmount(price)
  const alt = parseAmount(cheaper) ?? (value != null ? value * 0.7 : null)
  const pending = p.goals.find((g) => !g.done) ?? null
  const goalName = pending?.goal.name ?? null
  const ok = value != null && value > 0
  const options = useMemo(() => (ok ? PurchaseSimulator.compare(value!, alt ?? value!, p.data, p.bankPlan, p.goals, m.fmt) : []), [ok, value, alt, p, m.fmt])
  const real = useMemo(() => (ok ? RealPriceCalculator.of(value!, p.data, pending, m.fmt) : null), [ok, value, p, pending, m.fmt])

  return (
    <Overlay title={<div><div>¿Me lo compro?</div><div className="small muted" style={{ fontWeight: 400, letterSpacing: 0, whiteSpace: 'normal' }}>No te digo que no: te enseño qué pasa en cada caso</div></div>} onClose={nav.back}>
      <div className="card">
        <div className="muted">¿Cuánto cuesta?</div>
        <BigAmountInput value={price} onChange={setPrice} symbol={SYMBOLS[m.currency] ?? m.currency} />
        <div style={{ height: 8 }} />
        <AmountField label="Alternativa más barata (vacío = un 30 % menos)" value={cheaper} onChange={setCheaper} suffix="€" />
      </div>
      {real && <RealPriceCard r={real} goalName={goalName} />}
      {options.map((o, i) => {
        const delayed = (o.goalDelayDays ?? 0) > 0
        const body = (
          <>
            <Overline>{o.title}</Overline>
            <div className="num" style={{ fontSize: 28, fontWeight: 600, marginTop: 4 }}>{m.fmt(o.cost)}</div>
            <div style={{ marginTop: 8, fontWeight: 500, color: delayed ? ORANGE : 'var(--gain)' }}>{PurchaseSimulator.goalDelayText(o.goalDelayDays, goalName)}</div>
            {o.note.trim() !== '' && <div className="small muted">{o.note}</div>}
          </>
        )
        return i === 0 ? <MintCard key={o.title}>{body}</MintCard> : <div key={o.title} className="card">{body}</div>
      })}
      {options.length === 0 && (
        <div className="small muted" style={{ padding: '0 4px' }}>Pon el precio y verás cuánto te quedaría al día este mes y cuánto se retrasa tu objetivo en cada opción.</div>
      )}
    </Overlay>
  )
}

/** El precio real de una compra según tus circunstancias. */
function RealPriceCard({ r, goalName }: { r: RealPrice; goalName: string | null }) {
  const m = useMoney()
  return (
    <div className="card">
      <div style={{ fontSize: 20, fontWeight: 600 }}>El precio real</div>
      <div style={{ height: 8 }} />
      <Fact label="Días de trabajo" value={`${fixed(r.incomeDays, 1)} días de tus ingresos netos`} />
      {r.goalDelayDays != null && goalName != null && <Fact label="Tu objetivo" value={`"${goalName}" llegaría ${r.goalDelayDays} días más tarde`} />}
      {r.cushionMonthsBefore != null && r.cushionMonthsAfter != null && (
        <Fact label="Colchón de emergencia" value={`de ${fixed(r.cushionMonthsBefore, 1)} a ${fixed(r.cushionMonthsAfter, 1)} meses de gastos`} />
      )}
      <div className="small muted" style={{ marginTop: 8 }}>Tu ahorro en los próximos meses</div>
      {r.months.map(([mo, without, withBuy]) => (
        <div key={mo} className="hm-realprice-row small">
          <span className="muted" style={{ width: 56, flexShrink: 0 }}>{mo}</span>
          <span className="grow">sin comprar {m.fmt(without)}</span>
          <span style={{ color: withBuy < 0 ? 'var(--loss)' : undefined }}>comprando {m.fmt(withBuy)}</span>
        </div>
      ))}
      <div className="small" style={{ marginTop: 8 }}>{r.waitNote}</div>
    </div>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="row small" style={{ padding: '4px 0', alignItems: 'flex-start' }}>
      <span className="muted" style={{ width: 136, flexShrink: 0 }}>{label}</span>
      <span className="grow" style={{ fontWeight: 500 }}>{value}</span>
    </div>
  )
}
