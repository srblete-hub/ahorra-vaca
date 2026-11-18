// Port de ui/screens/SimulatorScreen.kt: tu situación actual frente a un escenario paralelo.
import { Fragment, useMemo, useState } from 'react'
import { Insights, usePlanning, type SimBase } from '../../../domain'
import { AmountField, Card, Overlay, Segmented, amountInput, parseAmount, useMoney } from '../../components'
import { useNav, type OverlayRoute } from '../../nav'
import './insights.css'

type Decision = 'MOVE' | 'JOB' | 'CAR' | 'ALONE'
const DECISIONS: Record<Decision, { label: string; intro: string; fields: string[] }> = {
  MOVE: { label: 'Mudarme', intro: 'Cambiar de casa: nuevo alquiler y lo que cuesta la mudanza.', fields: ['Nuevo alquiler al mes', 'Fianza y mudanza'] },
  JOB: { label: 'Otro trabajo', intro: 'Cambiar de trabajo: sueldo neto nuevo y lo que cambia ir y volver.', fields: ['Nuevo sueldo neto al mes', 'Transporte extra al mes'] },
  CAR: { label: 'Coche', intro: 'Comprar un coche: entrada, financiación, seguro y gasolina.', fields: ['Precio del coche', 'Entrada', 'Meses de financiación', 'Seguro al año', 'Gasolina al mes'] },
  ALONE: { label: 'Vivir solo', intro: 'Irte a vivir solo: alquiler, facturas y compra, menos lo que pagas ahora en casa.', fields: ['Alquiler', 'Luz, agua e internet', 'Compra extra al mes', 'Lo que pagas ahora en casa'] },
}
const ORDER: Decision[] = ['MOVE', 'JOB', 'CAR', 'ALONE']
const one = (v: number) => v.toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

export function SimulatorScreen({ route: _route }: { route: OverlayRoute }) {
  const nav = useNav()
  const p = usePlanning()
  const m = useMoney()
  const base = useMemo(() => Insights.simBase(p.data), [p.data])
  const [decision, setDecision] = useState<Decision>('MOVE')
  const [values, setValues] = useState<Record<string, string>>({})
  const v = (k: string) => parseAmount(values[`${decision}:${k}`] ?? '') ?? 0

  let incomeDelta = 0, costDelta = 0, upfront = 0
  if (decision === 'MOVE') { costDelta = v('Nuevo alquiler al mes') > 0 ? v('Nuevo alquiler al mes') - base.rent : 0; upfront = v('Fianza y mudanza') }
  else if (decision === 'JOB') { incomeDelta = v('Nuevo sueldo neto al mes') > 0 ? v('Nuevo sueldo neto al mes') - base.fixedIncome : 0; costDelta = v('Transporte extra al mes') }
  else if (decision === 'CAR') {
    const months = v('Meses de financiación') > 0 ? v('Meses de financiación') : 1
    const financed = Math.max(0, v('Precio del coche') - v('Entrada'))
    costDelta = v('Precio del coche') > 0 ? financed / months + v('Seguro al año') / 12 + v('Gasolina al mes') + 30 : v('Seguro al año') / 12 + v('Gasolina al mes')
    upfront = v('Entrada')
  } else {
    costDelta = v('Alquiler') + v('Luz, agua e internet') + v('Compra extra al mes') - v('Lo que pagas ahora en casa')
    upfront = v('Alquiler') * 2
  }
  const r = Insights.simulate(base, incomeDelta, costDelta, upfront)
  const info = DECISIONS[decision]

  return (
    <Overlay title="¿Qué pasaría si…?" onClose={nav.back}>
      <div className="in-sub">Tu vida ahora frente a un escenario paralelo</div>
      <div className="in-seg"><Segmented options={ORDER} value={decision} onChange={setDecision} label={(d) => DECISIONS[d].label} /></div>
      {decision === 'MOVE' ? <MoveComparison base={base} /> : <>
        <Card>
          <div>{info.intro}</div>
          <div className="small muted" style={{ marginTop: 4 }}>Ahora: ingresas {m.fmt(base.income)} y tu vida cuesta {m.fmt(base.cost)} al mes.</div>
          <div className="form" style={{ marginTop: 8, gap: 8 }}>
            {info.fields.map((f) => {
              const key = `${decision}:${f}`
              return <AmountField key={key} label={f} value={values[key] ?? ''} onChange={(t) => setValues((o) => ({ ...o, [key]: t }))} suffix={f.startsWith('Meses') ? '' : m.currency === 'EUR' ? '€' : m.currency} />
            })}
          </div>
          {decision === 'ALONE' && <div className="tiny muted" style={{ marginTop: 8 }}>Contamos dos meses de alquiler de fianza como pago inicial.</div>}
        </Card>
        <Card className="in-mint">
          <div className="in-over">Resultado</div>
          <div className="row" style={{ marginTop: 8, alignItems: 'flex-start' }}>
            <div className="grow">
              <div className="small muted">Ahora</div>
              <div className="num" style={{ fontSize: 24, fontWeight: 600, color: r.savingNow >= 0 ? 'var(--gain)' : 'var(--loss)' }}>{m.signed(r.savingNow)}</div>
              <div className="tiny muted">ahorras al mes</div>
            </div>
            <div className="grow right">
              <div className="small muted">Con el cambio</div>
              <div className="num" style={{ fontSize: 24, fontWeight: 600, color: r.savingAfter >= 0 ? 'var(--gain)' : 'var(--loss)' }}>{m.signed(r.savingAfter)}</div>
              <div className="tiny muted">ahorrarías al mes</div>
            </div>
          </div>
          <hr className="divider" style={{ margin: '12px 0' }} />
          <div className="in-compare">
            <Compare label="Dinero en 1 año" now={m.fmt(r.in1yNow)} after={m.fmt(r.in1yAfter)} />
            <Compare label="Dinero en 5 años" now={m.fmt(r.in5yNow)} after={m.fmt(r.in5yAfter)} />
            <Compare label="Colchón (meses de vida)" now={one(r.emergencyNow)} after={one(r.emergencyAfter)} />
            {upfront > 0 && <Compare label="Pago inicial" now="—" after={m.fmt(upfront)} />}
          </div>
          <div style={{ marginTop: 8, fontWeight: 500 }}>
            {r.runwayMonths != null ? `Con este cambio perderías ${m.fmt(-r.savingAfter)} al mes: tus ahorros durarían unos ${Math.trunc(r.runwayMonths)} meses.`
              : r.savingAfter < r.savingNow * 0.5 && r.savingNow > 0 ? 'Es posible, pero ahorrarías menos de la mitad que ahora. Mira si puedes compensarlo en otra parte.'
                : r.savingAfter >= r.savingNow ? `Saldrías ganando: ahorrarías ${m.fmt(r.savingAfter - r.savingNow)} más al mes.`
                  : `Asumible: ahorrarías ${m.fmt(r.savingNow - r.savingAfter)} menos al mes.`}
          </div>
          <div className="tiny muted" style={{ marginTop: 8 }}>Estimación con tus ingresos y tu coste de vida reales; no incluye intereses ni subidas de precios.</div>
        </Card>
      </>}
    </Overlay>
  )
}

function Compare({ label, now, after }: { label: string; now: string; after: string }) {
  return (
    <>
      <span className="l">{label}</span>
      <span className="num">{now}</span>
      <span className="a num">{after}</span>
    </>
  )
}

const MOVE_FIELDS = (rent: number): Array<[string, number]> => [
  ['Tu alquiler ahora', rent], ['Alquiler del piso nuevo', 750], ['Meses de fianza', 2], ['Mudanza y muebles', 300],
  ['Suministros extra al mes (luz, agua, internet)', 0], ['Transporte extra al mes', 0],
  ['Tu parte si compartes piso', 400], ['Alquiler de un piso más barato', 600],
]

/** Mudarse comparado de verdad: seguir, el piso nuevo, compartir o uno más barato, con el coste total. */
function MoveComparison({ base }: { base: SimBase }) {
  const m = useMoney()
  const [f, setF] = useState<Record<string, string>>({})
  const v = (k: string, def: number) => (f[k] != null ? parseAmount(f[k]) ?? def : def)
  const rentNow = v('Tu alquiler ahora', base.rent)
  const newRent = v('Alquiler del piso nuevo', 750)
  const depositMonths = v('Meses de fianza', 2)
  const moving = v('Mudanza y muebles', 300)
  const utilities = v('Suministros extra al mes (luz, agua, internet)', 0)
  const transport = v('Transporte extra al mes', 0)
  const shared = v('Tu parte si compartes piso', 400)
  const cheaper = v('Alquiler de un piso más barato', 600)
  const options = [
    { name: 'Seguir donde estás', monthly: rentNow, upfront: 0, detail: 'Sin cambios' },
    { name: 'Mudarte a ese piso', monthly: newRent + utilities + transport, upfront: newRent * depositMonths + moving, detail: `Fianza ${m.fmt(newRent * depositMonths)} + mudanza ${m.fmt(moving)}` },
    { name: 'Compartir vivienda', monthly: shared + utilities / 2 + transport, upfront: shared * depositMonths + moving / 2, detail: 'Fianza de tu parte + media mudanza' },
    { name: 'Un piso más barato', monthly: cheaper + utilities + transport, upfront: cheaper * depositMonths + moving, detail: `Fianza ${m.fmt(cheaper * depositMonths)} + mudanza ${m.fmt(moving)}` },
  ]
  const sym = m.currency === 'EUR' ? '€' : m.currency
  return (
    <>
      <Card>
        <div style={{ fontWeight: 600 }}>Cuéntame el piso que estás mirando</div>
        <div className="small muted">Rellena lo que sepas; lo demás lleva un valor de ejemplo.</div>
        <div className="form" style={{ marginTop: 8, gap: 8 }}>
          {MOVE_FIELDS(base.rent).map(([k, def]) => (
            <AmountField key={k} label={k} value={f[k] ?? amountInput(def)} onChange={(t) => setF((o) => ({ ...o, [k]: t }))} suffix={k.startsWith('Meses') ? '' : sym} />
          ))}
        </div>
      </Card>
      {options.map((o) => {
        const costAfter = base.cost - rentNow + o.monthly
        const saving = base.income - costAfter
        const start = base.savings - o.upfront
        return (
          <Card key={o.name}>
            <div className="row" style={{ gap: 8, alignItems: 'flex-start', flexWrap: 'wrap' }}>
              <span className="grow" style={{ fontSize: 20, fontWeight: 600 }}>{o.name}</span>
              <span className="num" style={{ fontWeight: 600, color: saving >= 0 ? 'var(--gain)' : 'var(--loss)' }}>{m.signed(saving)}/mes</span>
            </div>
            <div className="small muted">{o.detail}</div>
            <hr className="divider" />
            <div className="in-compare" style={{ gridTemplateColumns: 'minmax(0, 1fr) auto' }}>
              {([
                ['Vivienda al mes (todo incluido)', m.fmt(o.monthly)], ['Pago al empezar', m.fmt(o.upfront)], ['Gastos fijos del mes', m.fmt(costAfter)],
                ['Tu dinero en 6 meses', m.fmt(start + saving * 6)], ['Tu dinero en 12 meses', m.fmt(start + saving * 12)],
                ['Colchón (meses de vida)', costAfter > 0 ? one(start / costAfter) : '—'],
              ] as Array<[string, string]>).map(([l, val]) => <Fragment key={l}><span className="l">{l}</span><span className="a num">{val}</span></Fragment>)}
            </div>
          </Card>
        )
      })}
      <div className="tiny muted" style={{ padding: '0 4px' }}>Estimación con tus ingresos y tu coste de vida reales; no incluye subidas de precio.</div>
    </>
  )
}
