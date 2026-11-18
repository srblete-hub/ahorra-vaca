// Cuentas con amigos e ingresos variables confirmados (FriendsUi.kt).
import { useMemo, useState } from 'react'
import { BigAmountInput, TextField, parseAmount, useMoney } from '../components'
import { Icon } from '../icons'
import type { SheetRoute } from '../nav'
import { db, getState, useDB, useSettings } from '../../data/store'
import { MONTHS, todayEpoch } from '../../data/dates'
import { Debts, IncomeMath, makeDebt, type Debt } from '../../domain'
import { Choice, Chips, FormSheet, Seg, editable, useConfirmDelete, useCurrencySymbol } from './form'

type R<N extends SheetRoute['name']> = Extract<SheetRoute, { name: N }>
interface P<N extends SheetRoute['name']> { route: R<N>; onClose: () => void }

const eqIgnoreCase = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()
const putDebts = (list: Debt[]) => db.setSettings({ debtsRaw: Debts.format(list) })

/** Apuntar que alguien te debe (o que debes): desde un gasto se propone la mitad. */
export function DebtSheet({ route, onClose }: P<'debt'>) {
  const settings = useSettings()
  const money = useMoney()
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const people = useMemo(() => Debts.people(Debts.parse(settings.debtsRaw), Debts.parseTricount(settings.tricountRaw)), [settings.debtsRaw, settings.tricountRaw])
  const total = route.total ?? null
  const [initial] = useState<Debt>(() => {
    const stored = route.debtId ? Debts.parse(getState().settings.debtsRaw).find((d) => d.id === route.debtId) : undefined
    return stored ?? makeDebt(0, route.person ?? '', total != null ? total / 2 : 0, route.note ?? '', route.epochDay ?? todayEpoch(), route.expenseId ?? null)
  })
  const isNew = !initial.id
  const [person, setPerson] = useState(initial.person)
  const [owesMe, setOwesMe] = useState(initial.amount >= 0)
  const [amount, setAmount] = useState(editable(Math.abs(initial.amount)))
  const [note, setNote] = useState(initial.note)
  const [tricount, setTricount] = useState(initial.inTricount)
  const v = parseAmount(amount)
  const who = person.trim()

  return (
    <FormSheet
      title={isNew ? 'Cuenta con un amigo' : 'Editar cuenta'}
      onClose={onClose}
      onPrimary={() => {
        if (v == null) return
        const d: Debt = { ...initial, person: who, amount: owesMe ? v : -v, note: note.trim(), inTricount: tricount }
        // saveDebt del ViewModel: las nuevas llevan como id la hora actual.
        const item = d.id === 0 ? { ...d, id: Date.now() } : d
        putDebts([...Debts.parse(getState().settings.debtsRaw).filter((x) => x.id !== item.id), item])
        onClose()
      }}
      primaryEnabled={v != null && v > 0 && who !== ''}
      onDelete={isNew ? null : () => confirm.ask('esta cuenta', () => {
        putDebts(Debts.parse(getState().settings.debtsRaw).filter((x) => x.id !== initial.id))
        onClose()
      })}
    >
      {total != null && <div className="sh-text" style={{ fontSize: 16 }}>{initial.note.trim() || 'Gasto'} · pagaste {money.fmt(total)}</div>}
      <Seg options={[true, false]} value={owesMe} onChange={setOwesMe} label={(x) => (x ? 'Me debe' : 'Le debo')} />
      <TextField label="¿Quién? (Juanpa…)" value={person} onChange={setPerson} />
      {people.length > 0 && (
        <Chips>{people.slice(0, 6).map((p) => <Choice key={p} text={p} selected={eqIgnoreCase(p, who)} onClick={() => setPerson(p)} />)}</Chips>
      )}
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} autoFocus={false} />
      {total != null && (
        <Chips>
          <Choice text="La mitad" selected={v === total / 2} onClick={() => setAmount(editable(total / 2))} />
          <Choice text="Un tercio" selected={false} onClick={() => setAmount(editable(Math.round(total / 3 * 100) / 100))} />
          <Choice text="Todo" selected={v === total} onClick={() => setAmount(editable(total))} />
        </Chips>
      )}
      <TextField label="Concepto (pádel, cena…)" value={note} onChange={setNote} />
      <button type="button" className="sh-box sh-tap sh-row" aria-pressed={tricount} onClick={() => setTricount(!tricount)} style={{ alignItems: 'flex-start' }}>
        <Icon name={tricount ? 'checkBox' : 'checkBoxBlank'} style={{ color: tricount ? 'var(--gain)' : 'var(--muted)', flexShrink: 0 }} />
        <div className="grow">
          <div style={{ fontSize: 16 }}>También está en Tricount</div>
          <div className="sh-text small">Cuando pongas el saldo de Tricount no se contará dos veces.</div>
        </div>
      </button>
      {v != null && v > 0 && who !== '' && (
        <div className={`sh-strong ${owesMe ? 'sh-gain' : 'sh-loss'}`} style={{ fontWeight: 600 }}>
          {owesMe ? `${who} te debe ${money.fmt(v)}` : `Le debes ${money.fmt(v)} a ${who}`}
        </div>
      )}
      {confirm.dialog}
    </FormSheet>
  )
}

/** El saldo que marca Tricount con una persona: manda sobre lo apuntado "en Tricount" hasta hoy. */
export function TricountSheet({ route, onClose }: P<'tricount'>) {
  const settings = useSettings()
  const sym = useCurrencySymbol()
  const current = Object.entries(Debts.parseTricount(settings.tricountRaw)).find(([k]) => eqIgnoreCase(k, route.person))?.[1] ?? null
  const [name, setName] = useState(route.person)
  const [owesMe, setOwesMe] = useState((current?.amount ?? 0) >= 0)
  const [amount, setAmount] = useState(editable(Math.abs(current?.amount ?? 0)))
  const v = amount.trim() === '' ? 0 : parseAmount(amount)
  return (
    <FormSheet
      title="Saldo de Tricount"
      onClose={onClose}
      onPrimary={() => {
        if (v == null) return
        const person = name.trim()
        const m = Object.fromEntries(Object.entries(Debts.parseTricount(getState().settings.tricountRaw)).filter(([k]) => !eqIgnoreCase(k, person)))
        m[person] = { amount: owesMe ? v : -v, epochDay: todayEpoch() }
        db.setSettings({ tricountRaw: Debts.formatTricount(m) })
        onClose()
      }}
      primaryEnabled={v != null && name.trim() !== ''}
    >
      <div className="sh-text small">Pon lo que te dice Tricount ahora mismo. Lo apuntado «en Tricount» hasta hoy ya va dentro; lo que no esté en Tricount (o lo que apuntes después) se suma o resta encima.</div>
      <TextField label="Con quién" value={name} onChange={setName} />
      <Seg options={[true, false]} value={owesMe} onChange={setOwesMe} label={(x) => (x ? 'Me debe' : 'Le debo')} />
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} autoFocus={false} />
      <div className="sh-text small">0 = estáis en paz en Tricount.</div>
    </FormSheet>
  )
}

/** Lo que te pagará este mes un ingreso variable (YouTube…), cuando ya lo sabes. */
export function ExpectedIncomeSheet({ route, onClose }: P<'expectedIncome'>) {
  const source = useDB((s) => s.incomeSources.find((x) => x.id === route.sourceId))
  const incomes = useDB((s) => s.incomes)
  const settings = useSettings()
  const money = useMoney()
  const sym = useCurrencySymbol()
  const key = IncomeMath.key(route.sourceId, route.month)
  const current = settings.expectedIncome[key] ?? null
  const average = useMemo(() => (source ? IncomeMath.variableAverage(source, incomes, route.month) : null), [source, incomes, route.month])
  const [amount, setAmount] = useState(current != null ? editable(current) : '')
  const v = parseAmount(amount)
  /** setExpectedIncome del ViewModel: null = volver a estimar. */
  const put = (value: number | null) => {
    const m = { ...getState().settings.expectedIncome }
    if (value == null) delete m[key]; else m[key] = value
    db.setSettings({ expectedIncome: m })
    onClose()
  }
  if (!source) return <FormSheet title="Ingreso" onClose={onClose} primary={null}><div className="sh-text">Este ingreso ya no existe.</div></FormSheet>
  return (
    <FormSheet title={`${source.name} en ${MONTHS[route.month.month - 1]}`} onClose={onClose} onPrimary={() => { if (v != null) put(v) }} primaryEnabled={v != null && v >= 0}>
      <div className="sh-text small">¿Cuánto te pagará el día {source.dayFrom}? Con la cantidad confirmada, el consejo de hoy y el reparto la usan tal cual en vez de estimar.</div>
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} />
      {average != null && (
        <Chips><Choice text={`Media: ${money.fmt(average)}`} selected={false} onClick={() => setAmount(editable(Math.round(average)))} /></Chips>
      )}
      <div className="sh-text small">
        {current == null
          ? `Ahora se estima con el 70 % de tu media${average != null ? ` (${money.fmt(average * 0.7)})` : ''}, por prudencia.`
          : `Confirmado: ${money.fmt(current)}.`}
      </div>
      {current != null && <button type="button" className="sh-link" onClick={() => put(null)}>Volver a la estimación</button>}
    </FormSheet>
  )
}
