// Movimientos: elegir qué añadir, gasto, ingreso, fuente de ingreso, gasto fijo, transferencia, saldo,
// gastos escritos como frase, repartir un gasto y convertirlo en traspaso (Sheets.kt).
import { useMemo, useState } from 'react'
import { AmountField, BigAmountInput, IconTile, SERIES_COLORS, TextField, parseAmount, useMoney } from '../components'
import { Icon } from '../icons'
import { useNav, type SheetRoute } from '../nav'
import { db, getState, toast, useDB, useSettings } from '../../data/store'
import type { Expense, Income, IncomeSource, RecurringBill } from '../../data/models'
import {
  MONTHS, MONTHS_SHORT, firstDay, lastDay, lengthOfMonth, ofEpochDay, plusDays, thisMonth, toEpochDay, today, todayEpoch, ymOf,
  type LocalDate,
} from '../../data/dates'
import {
  Classifier, Debts, IncomeMath, NaturalEntry, PaymentParser, SPEND_TAG_INFO, isDebt, isSaving, parsedExpenseDate, spendTagOf,
  type SpendTag,
} from '../../domain'
import {
  AccountChoices, CategoryChoices, Choice, Chips, DateField, FieldLabel, FormSheet, NumberField, Seg, SwitchBox, TAG_COLOR, TAG_ICON,
  TagPicker, accountIcon, defaultAccountId, editable, useConfirmDelete, useCurrencySymbol, useDefaultAccountId,
} from './form'

type R<N extends SheetRoute['name']> = Extract<SheetRoute, { name: N }>
interface P<N extends SheetRoute['name']> { route: R<N>; onClose: () => void }

const sameDay = (a: LocalDate, b: LocalDate) => toEpochDay(a) === toEpochDay(b)

// ---------- Elegir qué añadir ----------

export function AddChooserSheet({ onClose }: P<'chooser'>) {
  const nav = useNav()
  const categories = useDB((s) => s.categories)
  const accounts = useDB((s) => s.accounts)
  const def = useDefaultAccountId()
  const firstSpending = categories.find((c) => !c.isSavings)?.id ?? 0
  const options: Array<[icon: string, title: string, sub: string, go: () => void]> = [
    ['swap', 'Transferencia entre mis cuentas', 'De Santander a Revolut, a Robinhood… no es un gasto', () => nav.edit({ name: 'transfer', fromAccountId: def ?? accounts[0]?.id ?? 0 })],
    ['people', 'Me deben o debo', 'Pagaste por un amigo o él por ti (o el saldo de Tricount)', () => nav.edit({ name: 'debt' })],
    ['receipt', 'Gasto', 'Apunta un pago y descuéntalo de una cuenta', () => nav.edit({ name: 'expense', expense: { categoryId: firstSpending, amount: 0, epochDay: todayEpoch(), accountId: def } })],
    ['savings', 'Ingreso', 'Bizum, venta, devolución, un extra… y súmalo a una cuenta', () => nav.edit({ name: 'income', income: { sourceId: null, amount: 0, note: '', epochDay: todayEpoch(), accountId: def } })],
    ['bank', 'Cuenta o ahorro', 'Banco, efectivo, hucha, PayPal…', () => nav.edit({ name: 'account' })],
    ['bitcoin', 'Criptomoneda', 'Con su precio en vivo', () => nav.edit({ name: 'coinSearch' })],
    ['trendingUp', 'Acción o ETF', 'Con su ticker de bolsa', () => nav.edit({ name: 'stock' })],
    ['home', 'Gasto fijo', 'Alquiler, suscripciones, seguros… cada mes o cada año', () => nav.edit({ name: 'bill', bill: newBill(def) })],
    ['paste', 'Pegar aviso del banco', 'Copia el texto de la notificación de un pago o un Bizum y lo apunto', () => nav.edit({ name: 'pastePayment' })],
  ]
  return (
    <FormSheet title="¿Qué quieres añadir?" onClose={onClose} primary={null}>
      {options.map(([icon, title, sub, go], i) => (
        <button key={title} type="button" className="sh-option" onClick={go}>
          <IconTile icon={icon} color={SERIES_COLORS[i % SERIES_COLORS.length]} />
          <div className="grow" style={{ minWidth: 0 }}>
            <div className="t">{title}</div>
            <div className="s">{sub}</div>
          </div>
        </button>
      ))}
    </FormSheet>
  )
}

/** newBill() de Sheets.kt. */
function newBill(accountId: number | null): Partial<RecurringBill> {
  const cats = getState().categories
  return { name: '', amount: 0, day: 1, yearly: false, month: 1, categoryId: cats.find((c) => !c.isSavings)?.id ?? 0, accountId }
}

// ---------- Gasto ----------

export function ExpenseSheet({ route, onClose }: P<'expense'>) {
  const nav = useNav()
  const categories = useDB((s) => s.categories)
  const accounts = useDB((s) => s.accounts)
  const settings = useSettings()
  const money = useMoney()
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const [initial] = useState<Expense>(() => {
    const r = route.expense ?? {}
    const stored = r.id ? getState().expenses.find((x) => x.id === r.id) : undefined
    const base: Partial<Expense> = stored ?? r
    return {
      id: 0, categoryId: getState().categories.find((c) => !c.isSavings)?.id ?? 0, amount: 0, note: '', epochDay: todayEpoch(),
      fromCard: false, sourceApp: '', createdAt: Date.now(), billId: null, tag: '', rawText: '',
      ...base,
      accountId: 'accountId' in base ? base.accountId ?? null : defaultAccountId(),
    }
  })
  const isNew = !initial.id
  const [amount, setAmount] = useState(editable(initial.amount))
  const [note, setNote] = useState(initial.note)
  const [refund, setRefund] = useState(settings.refundIds.includes(initial.id))
  const [categoryId, setCategoryId] = useState(initial.categoryId)
  const [accountId, setAccountId] = useState<number | null>(initial.accountId)
  const [date, setDate] = useState<LocalDate>(ofEpochDay(initial.epochDay))
  const [tag, setTag] = useState<SpendTag>(spendTagOf(initial.tag) ?? Classifier.guess(initial.note) ?? 'OTHER')
  // Mientras no elijas tú el tipo, se deduce de la descripción al escribir.
  const [tagChosen, setTagChosen] = useState(initial.tag.trim() !== '')
  const [categoryChosen, setCategoryChosen] = useState(!isNew)
  const [otherDay, setOtherDay] = useState(false)
  const t0 = today()
  const yesterday = plusDays(t0, -1)
  const value = parseAmount(amount)
  const isOther = otherDay || (!sameDay(date, t0) && !sameDay(date, yesterday))

  const save = () => {
    if (value == null) return
    db.saveExpense({ ...initial, id: initial.id || undefined, amount: value, note: note.trim(), categoryId, accountId, epochDay: toEpochDay(date), tag })
    onClose()
  }
  const notAPayment = () => {
    if (initial.rawText.trim() !== '') {
      const sig = PaymentParser.signature(initial.rawText)
      const list = getState().settings.ignoredPayments
      if (!list.includes(sig)) db.setSettings({ ignoredPayments: [...list, sig] })
    }
    db.deleteExpense(initial.id)
    toast('Borrado. No volveré a apuntar avisos como ese.')
    onClose()
  }

  return (
    <FormSheet
      title={isNew ? 'Nuevo gasto' : initial.fromCard ? 'Gasto detectado' : 'Editar gasto'}
      onClose={onClose}
      onPrimary={save}
      primaryEnabled={value != null && value > 0 && categories.some((c) => c.id === categoryId)}
      onDelete={isNew ? null : () => confirm.ask('este gasto', () => { db.deleteExpense(initial.id); onClose() })}
    >
      {initial.fromCard && (
        <>
          <div className="sh-box sh-mint sh-row">
            <Icon name="creditCard" style={{ color: 'var(--gain)' }} />
            <div className="grow sh-text small ink">Apuntado solo con tu tarjeta · {initial.sourceApp}</div>
          </div>
          <div className="sh-box">
            <div className="sh-label" style={{ fontSize: 13 }}>Notificación que leí</div>
            <div className="sh-raw">{initial.rawText.trim() || 'No guardada (este pago se apuntó con una versión anterior de la app).'}</div>
          </div>
          {!isNew && (
            <button type="button" className="sh-link loss" onClick={() => confirm.ask('este gasto', notAPayment, 'No volveré a apuntar avisos como ese.')}>
              No era un pago: borrarlo y no volver a apuntar avisos así
            </button>
          )}
        </>
      )}
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} autoFocus={isNew && !initial.fromCard} />
      <TextField label="Descripción (Mercadona, cena…)" value={note} onChange={(t) => {
        setNote(t)
        if (!tagChosen) {
          const g = Classifier.guess(t) ?? 'OTHER'
          setTag(g)
          if (!categoryChosen) { const b = Classifier.bucketFor(g, categories); if (b != null) setCategoryId(b) }
        }
      }} />

      <FieldLabel>{tagChosen ? 'Tipo de gasto' : 'Tipo de gasto · deducido solo'}</FieldLabel>
      <TagPicker selected={tag} onSelect={(t) => {
        setTag(t); setTagChosen(true)
        if (!categoryChosen) { const b = Classifier.bucketFor(t, categories); if (b != null) setCategoryId(b) }
      }} />

      <FieldLabel>Parte del reparto</FieldLabel>
      <CategoryChoices categories={categories} value={categoryId} onChange={(id) => { setCategoryId(id); setCategoryChosen(true) }} />

      <FieldLabel>Sale de</FieldLabel>
      <AccountChoices accounts={accounts} value={accountId} onChange={setAccountId} none="No descontar" />

      {!isNew && (
        <SwitchBox title="Espero que me lo devuelvan" sub="Te lo recordaré en Dinero recuperable hasta que llegue" checked={refund}
          onChange={(v) => { setRefund(v); db.setRefund(initial.id, v) }} />
      )}

      {!isNew && (
        <button type="button" className="sh-box sh-tap sh-row" style={{ padding: 16 }} onClick={() => {
          const s = getState().settings
          const known = Debts.people(Debts.parse(s.debtsRaw), Debts.parseTricount(s.tricountRaw))
          nav.edit({ name: 'debt', person: known[0] ?? '', total: initial.amount, note: initial.note, expenseId: initial.id, epochDay: initial.epochDay })
        }}>
          <Icon name="people" style={{ color: SERIES_COLORS[1] }} />
          <div className="grow">
            <div className="sh-strong" style={{ fontWeight: 600 }}>Alguien me debe parte</div>
            <div className="sh-text small">Juanpa me debe la mitad, por ejemplo</div>
          </div>
        </button>
      )}
      {!isNew && initial.accountId != null && (
        <button type="button" className="sh-box sh-tap sh-row" style={{ padding: 16 }} onClick={() => nav.edit({ name: 'convertTransfer', expenseId: initial.id })}>
          <Icon name="swap" style={{ color: 'var(--gain)' }} />
          <div className="grow">
            <div className="sh-strong" style={{ fontWeight: 600 }}>Era un traspaso a mis cuentas</div>
            <div className="sh-text small">Lo convierto en transferencia (no cuenta como gasto)</div>
          </div>
        </button>
      )}

      <FieldLabel>Fecha</FieldLabel>
      <Chips>
        <Choice text="Hoy" selected={!otherDay && sameDay(date, t0)} onClick={() => { setOtherDay(false); setDate(t0) }} />
        <Choice text="Ayer" selected={!otherDay && sameDay(date, yesterday)} onClick={() => { setOtherDay(false); setDate(yesterday) }} />
        <Choice text="Otro día" selected={isOther} onClick={() => setOtherDay(true)} />
      </Chips>
      {isOther && <DateField label={`Día de ${MONTHS[date.month - 1]}`} value={date} onChange={setDate} />}
      {confirm.dialog}
    </FormSheet>
  )
}

// ---------- Ingresos ----------

export function IncomeSourceSheet({ route, onClose }: P<'incomeSource'>) {
  const accounts = useDB((s) => s.accounts)
  const incomes = useDB((s) => s.incomes)
  const money = useMoney()
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const [initial] = useState<IncomeSource>(() => {
    const r = route.source ?? {}
    const stored = r.id ? getState().incomeSources.find((x) => x.id === r.id) : undefined
    return { id: 0, name: '', variable: false, amount: 0, dayFrom: 1, dayTo: 5, accountId: null, ...(stored ?? r) }
  })
  const isNew = !initial.id
  const [name, setName] = useState(initial.name)
  const [variable, setVariable] = useState(initial.variable)
  const [amount, setAmount] = useState(editable(initial.amount))
  const [dayFrom, setDayFrom] = useState(String(initial.dayFrom))
  const [dayTo, setDayTo] = useState(String(initial.dayTo))
  const [accountId, setAccountId] = useState<number | null>(initial.accountId)
  const average = useMemo(() => (isNew ? null : IncomeMath.variableAverage(initial, incomes, thisMonth())), [isNew, initial, incomes])
  const value = parseAmount(amount)
  const day = (s: string) => { const n = parseInt(s, 10); return n >= 1 && n <= 31 ? n : null }
  const from = day(dayFrom)
  const to = dayTo.trim() === '' ? from : day(dayTo)

  return (
    <FormSheet
      title={isNew ? 'Nuevo ingreso' : initial.name}
      onClose={onClose}
      onPrimary={() => {
        if (value == null || from == null || to == null) return
        db.saveIncomeSource({ ...initial, id: initial.id || undefined, name: name.trim(), variable, amount: value, dayFrom: from, dayTo: Math.max(from, to), accountId })
        onClose()
      }}
      primaryEnabled={name.trim() !== '' && value != null && value >= 0 && from != null && to != null}
      onDelete={isNew ? null : () => confirm.ask(`"${initial.name}"`, () => { db.deleteIncomeSource(initial.id); onClose() })}
    >
      <Seg options={[false, true]} value={variable} onChange={setVariable} label={(v) => (v ? 'Variable' : 'Fijo')} />
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} autoFocus={isNew} />
      <div className="sh-text small">
        {variable
          ? average != null
            ? `Tu media de los últimos meses es ${money.fmt(average)}: es la que se usa para el presupuesto. Esta cantidad solo cuenta hasta que haya historial.`
            : 'Pon una estimación de lo que sueles cobrar. Cuando registres cobros, se usará la media de los últimos 3 meses.'
          : 'Lo que cobras cada mes.'}
      </div>
      <TextField label="Nombre (Nómina, Autónomo, YouTube…)" value={name} onChange={setName} />
      <FieldLabel>¿Qué días cobras?</FieldLabel>
      <div className="sh-two">
        <NumberField label="Del día" value={dayFrom} onChange={setDayFrom} maxLength={2} />
        <NumberField label="Al día" value={dayTo} onChange={setDayTo} maxLength={2} />
      </div>
      <FieldLabel>¿A qué cuenta llega?</FieldLabel>
      <AccountChoices accounts={accounts} value={accountId} onChange={setAccountId} none="Ninguna" />
      {confirm.dialog}
    </FormSheet>
  )
}

/** Registrar un cobro: suma el importe a la cuenta y alimenta la media de los variables. */
export function IncomeSheet({ route, onClose }: P<'income'>) {
  const accounts = useDB((s) => s.accounts)
  const sources = useDB((s) => s.incomeSources)
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const [initial] = useState<Income>(() => {
    const r = route.income ?? {}
    const stored = r.id ? getState().incomes.find((x) => x.id === r.id) : undefined
    const base: Partial<Income> = stored ?? r
    return {
      id: 0, sourceId: null, amount: 0, note: '', epochDay: todayEpoch(), createdAt: Date.now(), ...base,
      accountId: 'accountId' in base ? base.accountId ?? null : defaultAccountId(),
    }
  })
  const isNew = !initial.id
  const source = sources.find((s) => s.id === initial.sourceId) ?? null
  const loose = source == null
  const start = ofEpochDay(initial.epochDay)
  const [amount, setAmount] = useState(editable(initial.amount))
  const [accountId, setAccountId] = useState<number | null>(initial.accountId)
  const [date, setDate] = useState<LocalDate>(start)
  const [note, setNote] = useState(initial.note)
  const value = parseAmount(amount)
  const month = ymOf(start)
  const dayOk = date.year === start.year && date.month === start.month && date.day >= 1 && date.day <= lengthOfMonth(start.year, start.month)

  return (
    <FormSheet
      title={isNew ? (loose ? 'Nuevo ingreso' : `Cobro de ${source!.name}`) : initial.note.trim() || 'Ingreso'}
      onClose={onClose}
      primary={isNew && !loose ? 'Marcar como cobrado' : 'Guardar'}
      onPrimary={() => {
        if (value == null) return
        db.saveIncome({ ...initial, id: initial.id || undefined, amount: value, accountId, epochDay: toEpochDay(date), note: loose ? note.trim() || 'Ingreso' : initial.note })
        onClose()
      }}
      primaryEnabled={value != null && value > 0 && dayOk}
      onDelete={isNew ? null : () => confirm.ask('este cobro', () => { db.deleteIncome(initial.id); onClose() })}
    >
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} autoFocus={isNew} />
      {loose && <TextField label="Concepto (Bizum de Juanpa, venta Wallapop…)" value={note} onChange={setNote} />}
      {source?.variable && <div className="sh-text small">Pon lo que has cobrado de verdad: así la media de los próximos meses será más exacta.</div>}
      <FieldLabel>Entra en</FieldLabel>
      <AccountChoices accounts={accounts} value={accountId} onChange={setAccountId} none="No sumar a ninguna" />
      <DateField label="Día del mes" value={date} onChange={setDate} min={firstDay(month)} max={lastDay(month)} />
      {confirm.dialog}
    </FormSheet>
  )
}

// ---------- Gasto fijo ----------

export function BillSheet({ route, onClose }: P<'bill'>) {
  const categories = useDB((s) => s.categories)
  const accounts = useDB((s) => s.accounts)
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const [initial] = useState<RecurringBill>(() => {
    const r = route.bill ?? newBill(defaultAccountId())
    const stored = r.id ? getState().bills.find((x) => x.id === r.id) : undefined
    return { id: 0, ...(newBill(defaultAccountId()) as Omit<RecurringBill, 'id'>), ...(stored ?? r) }
  })
  const isNew = !initial.id
  const [name, setName] = useState(initial.name)
  const [amount, setAmount] = useState(editable(initial.amount))
  const [day, setDay] = useState(String(initial.day))
  const [yearly, setYearly] = useState(initial.yearly)
  const [month, setMonth] = useState(initial.month)
  const [categoryId, setCategoryId] = useState(initial.categoryId)
  const [accountId, setAccountId] = useState<number | null>(initial.accountId)
  const value = parseAmount(amount)
  const n = parseInt(day, 10)
  const dayNum = n >= 1 && n <= 31 ? n : null

  return (
    <FormSheet
      title={isNew ? 'Nuevo gasto fijo' : initial.name}
      onClose={onClose}
      onPrimary={() => {
        if (value == null || dayNum == null) return
        db.saveBill({ ...initial, id: initial.id || undefined, name: name.trim(), amount: value, day: dayNum, yearly, month, categoryId, accountId })
        onClose()
      }}
      primaryEnabled={name.trim() !== '' && value != null && value > 0 && dayNum != null && categories.some((c) => c.id === categoryId)}
      onDelete={isNew ? null : () => confirm.ask(`"${initial.name}"`, () => { db.deleteBill(initial.id); onClose() })}
    >
      <Seg options={[false, true]} value={yearly} onChange={setYearly} label={(v) => (v ? 'Cada año' : 'Cada mes')} />
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} autoFocus={isNew} />
      <TextField label="Qué es (Alquiler, Netflix, Seguro coche…)" value={name} onChange={setName} />
      <NumberField label="Día en que se cobra" value={day} onChange={setDay} maxLength={2} />
      {yearly && (
        <>
          <FieldLabel>Mes</FieldLabel>
          <Chips>
            {MONTHS_SHORT.map((m, i) => <Choice key={m} text={m} selected={i + 1 === month} onClick={() => setMonth(i + 1)} />)}
          </Chips>
        </>
      )}
      <FieldLabel>Categoría</FieldLabel>
      <CategoryChoices categories={categories} value={categoryId} onChange={setCategoryId} />
      <FieldLabel>Sale de</FieldLabel>
      <AccountChoices accounts={accounts} value={accountId} onChange={setAccountId} none="Ninguna" />
      {confirm.dialog}
    </FormSheet>
  )
}

// ---------- Transferencia y saldo ----------

/** Mover dinero entre tus cuentas: de la nómina al ahorro, a la hucha… */
export function TransferSheet({ route, onClose }: P<'transfer'>) {
  const accounts = useDB((s) => s.accounts)
  const sym = useCurrencySymbol()
  const [from, setFrom] = useState<number>(() => route.fromAccountId || defaultAccountId() || accounts[0]?.id || 0)
  const [to, setTo] = useState<number | null>(() => accounts.find((a) => a.id !== from)?.id ?? null)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const value = parseAmount(amount)
  return (
    <FormSheet
      title="Transferir"
      onClose={onClose}
      primary="Transferir"
      onPrimary={() => {
        if (value == null || to == null) return
        db.saveTransfer({ fromAccountId: from, toAccountId: to, amount: value, note: note.trim(), epochDay: todayEpoch(), createdAt: Date.now() })
        onClose()
      }}
      primaryEnabled={value != null && value > 0 && to != null && to !== from && accounts.some((a) => a.id === from)}
    >
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} />
      {accounts.length < 2 && <div className="sh-text">Necesitas al menos dos cuentas para mover dinero entre ellas.</div>}
      <FieldLabel>Desde</FieldLabel>
      <AccountChoices accounts={accounts} value={from} onChange={(id) => { if (id == null) return; setFrom(id); if (to === id) setTo(null) }} />
      <FieldLabel>Hacia</FieldLabel>
      <AccountChoices accounts={accounts.filter((a) => a.id !== from)} value={to} onChange={setTo} />
      <TextField label="Concepto (opcional)" value={note} onChange={setNote} />
      <div className="sh-text small">Una transferencia no es un gasto: tu patrimonio no cambia, solo dónde está el dinero.</div>
    </FormSheet>
  )
}

/** Poner el saldo que dice el banco, sin tocar nada más. */
export function BalanceSheet({ route, onClose }: P<'balance'>) {
  const account = useDB((s) => s.accounts.find((a) => a.id === route.accountId))
  const money = useMoney()
  const sym = useCurrencySymbol()
  const [amount, setAmount] = useState(() => editable(account?.balance ?? 0))
  const value = amount.trim() === '' ? 0 : parseAmount(amount)
  if (!account) return <FormSheet title="Ajustar saldo" onClose={onClose} primary={null}><div className="sh-text">Esta cuenta ya no existe.</div></FormSheet>
  const diff = value != null ? value - account.balance : 0
  return (
    <FormSheet title="Ajustar saldo" onClose={onClose} onPrimary={() => { if (value != null) { db.setBalance(account.id, value); onClose() } }} primaryEnabled={value != null}>
      <div className="sh-text">Pon el saldo que te dice el banco ahora mismo en {account.name}.</div>
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} />
      {value != null && Math.abs(diff) > 0.0001 && (
        <div className={`sh-strong ${diff >= 0 ? 'sh-gain' : 'sh-loss'}`}>Diferencia: {money.signed(diff)}</div>
      )}
    </FormSheet>
  )
}

// ---------- Escribir gastos como hablas ----------

export function QuickEntrySheet({ route, onClose }: P<'quickEntry'>) {
  const expenses = useDB((s) => s.expenses)
  const accounts = useDB((s) => s.accounts)
  const money = useMoney()
  const [text, setText] = useState(route.text ?? '')
  const [accountId, setAccountId] = useState<number | null>(() => defaultAccountId())
  const parsed = useMemo(() => NaturalEntry.parse(text, expenses), [text, expenses])

  const save = () => {
    const cats = getState().categories
    const fallback = cats.find((c) => !c.isSavings)?.id
    if (fallback == null) { onClose(); return }
    const t = today()
    for (const p of parsed) {
      db.saveExpense({
        categoryId: Classifier.bucketFor(p.tag, cats) ?? fallback, amount: p.amount, note: p.note,
        epochDay: toEpochDay(parsedExpenseDate(p, t)), accountId, tag: p.tag,
      })
    }
    toast(parsed.length === 1 ? 'Gasto apuntado' : `${parsed.length} gastos apuntados`)
    onClose()
  }

  return (
    <FormSheet
      title="Escribe tus gastos"
      onClose={onClose}
      primary={parsed.length > 1 ? `Apuntar ${parsed.length} gastos` : 'Apuntar'}
      onPrimary={save}
      primaryEnabled={parsed.length > 0}
    >
      <div className="sh-text">Como se lo contarías a alguien. Ahorra, vaca saca los importes y los clasifica solo.</div>
      <textarea
        className="sh-textarea"
        aria-label="Tus gastos"
        placeholder="He pagado 18 € en una cena con amigos y 12 € en gasolina"
        value={text}
        rows={3}
        autoFocus
        onChange={(e) => setText(e.target.value)}
      />
      {parsed.map((p, i) => (
        <div key={i} className="sh-box sh-row">
          <IconTile icon={TAG_ICON[p.tag]} color={TAG_COLOR[p.tag]} size={40} />
          <div className="grow">
            <div className="sh-strong" style={{ overflowWrap: 'anywhere' }}>{p.note}</div>
            <div className="sh-text small">{SPEND_TAG_INFO[p.tag].label}{p.daysAgo === 1 ? ' · ayer' : ' · hoy'}</div>
          </div>
          <div className="sh-strong num" style={{ flexShrink: 0 }}>−{money.fmt(p.amount)}</div>
        </div>
      ))}
      <FieldLabel>Sale de</FieldLabel>
      <AccountChoices accounts={accounts.filter((a) => !isDebt(a))} value={accountId} onChange={setAccountId} none="No descontar" />
    </FormSheet>
  )
}

// ---------- Repartir un gasto ----------

export function SplitSheet({ route, onClose }: P<'split'>) {
  const expense = useDB((s) => s.expenses.find((e) => e.id === route.expenseId))
  const current = useDB((s) => s.splits.find((x) => x.expenseId === route.expenseId))
  const money = useMoney()
  const confirm = useConfirmDelete()
  const [people, setPeople] = useState(current?.people ?? 2)
  const [settled, setSettled] = useState(current?.settled ?? 0)
  const [note, setNote] = useState(current?.note ?? '')
  if (!expense) return <FormSheet title="Repartir gasto" onClose={onClose} primary={null}><div className="sh-text">Este gasto ya no existe.</div></FormSheet>
  const share = expense.amount / people
  const owed = people - 1 - settled
  return (
    <FormSheet
      title="Repartir gasto"
      onClose={onClose}
      onPrimary={() => { db.saveSplit({ expenseId: expense.id, people, settled: Math.min(settled, people - 1), note: note.trim() }); onClose() }}
      onDelete={current ? () => confirm.ask('este reparto', () => { db.deleteSplit(expense.id); onClose() }) : null}
    >
      <div className="sh-strong" style={{ fontSize: 17, fontWeight: 600 }}>{expense.note.trim() || 'Gasto'} · {money.fmt(expense.amount)}</div>
      <FieldLabel>¿Entre cuántas personas? (tú incluido)</FieldLabel>
      <Chips>
        {[2, 3, 4, 5, 6, 7, 8].map((n) => <Choice key={n} text={String(n)} selected={n === people} onClick={() => { setPeople(n); setSettled((s) => Math.min(s, n - 1)) }} />)}
      </Chips>
      <div className="sh-box big sh-mint">
        <div className="sh-text small">A cada uno le toca</div>
        <div className="mid-num num" style={{ fontSize: 24, fontWeight: 800 }}>{money.fmt(share)}</div>
        <div className="sh-strong" style={{ color: owed > 0 ? SERIES_COLORS[1] : 'var(--gain)', fontSize: 15 }}>Te deben {money.fmt(share * owed)}</div>
      </div>
      <FieldLabel>¿Cuántos te han pagado ya su parte?</FieldLabel>
      <Chips>
        {Array.from({ length: people }, (_, n) => <Choice key={n} text={n === 0 ? 'Nadie' : String(n)} selected={n === settled} onClick={() => setSettled(n)} />)}
      </Chips>
      <TextField label="Con quién (Laura, Marcos…)" value={note} onChange={setNote} />
      {confirm.dialog}
    </FormSheet>
  )
}

// ---------- Era un traspaso ----------

/** Un gasto que en realidad fue un traspaso: eliges a qué cuenta tuya fue y se convierte en transferencia. */
export function ConvertTransferSheet({ route, onClose }: P<'convertTransfer'>) {
  const expense = useDB((s) => s.expenses.find((e) => e.id === route.expenseId))
  const accounts = useDB((s) => s.accounts)
  const money = useMoney()
  const options = accounts.filter((a) => a.id !== expense?.accountId && !isDebt(a))
  const [to, setTo] = useState<number | null>(() => {
    const text = ((expense?.note ?? '') + ' ' + (expense?.rawText ?? '')).toLowerCase()
    return options.find((a) => text.includes(a.name.toLowerCase()))?.id ?? options.find((a) => isSaving(a))?.id ?? null
  })
  if (!expense) return <FormSheet title="Era un traspaso" onClose={onClose} primary={null}><div className="sh-text">Este gasto ya no existe.</div></FormSheet>
  const from = accounts.find((a) => a.id === expense.accountId)
  return (
    <FormSheet
      title="Era un traspaso"
      onClose={onClose}
      primary="Convertir en transferencia"
      onPrimary={() => { if (to != null) { db.convertToTransfer(expense, to); onClose() } }}
      primaryEnabled={to != null}
    >
      <div className="sh-text ink">{money.fmt(expense.amount)} desde {from?.name ?? 'tu cuenta'}. ¿A qué cuenta tuya fue?</div>
      <Chips>
        {options.map((a) => <Choice key={a.id} text={a.name} selected={a.id === to} onClick={() => setTo(a.id)} icon={accountIcon(a.type)} />)}
      </Chips>
      <div className="sh-text small">El gasto desaparece de tus gastos y el dinero pasa de una cuenta a otra: tu patrimonio no cambia. Si fue a tu cuenta de ahorro, cuenta como el ahorro del mes.</div>
    </FormSheet>
  )
}

