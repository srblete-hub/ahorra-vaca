// Solo web: en iPhone la app no puede leer las notificaciones del banco (PaymentListenerService en Android).
// Se pega el texto del aviso y se apunta igual que lo haría PaymentRecorder.
import { useEffect, useMemo, useState } from 'react'
import { AmountField, TextField, parseAmount, useMoney } from '../components'
import { Icon } from '../icons'
import { useNav, type SheetRoute } from '../nav'
import { db, getState, toast, useDB, useSettings } from '../../data/store'
import { todayEpoch } from '../../data/dates'
import { BizumParser, Classifier, Merchants, PaymentParser, type Bizum, type DetectedPayment, type SpendTag } from '../../domain'
import { AccountChoices, CategoryChoices, FieldLabel, FormSheet, TagPicker, editable, useCurrencySymbol, useDefaultAccountId } from './form'

type R<N extends SheetRoute['name']> = Extract<SheetRoute, { name: N }>
interface P<N extends SheetRoute['name']> { route: R<N>; onClose: () => void }

type Detected =
  | { kind: 'bizum'; b: Bizum }
  | { kind: 'card'; p: DetectedPayment }
  | null

/** La primera línea hace de título de la notificación y el resto de texto, como en Android. */
function detect(text: string): Detected {
  const lines = text.replace(/\r/g, '').split('\n').map((l) => l.trim()).filter((l) => l !== '')
  if (lines.length === 0) return null
  const title = lines[0]
  const body = lines.length > 1 ? lines.slice(1).join('\n') : null
  const b = BizumParser.parse(title, body)
  if (b) return { kind: 'bizum', b }
  // Aviso del banco (con "pago", "compra"…). Si no, como el de una cartera ("Comercio" + "12,50 € con Visa").
  const p = PaymentParser.parse(title, body, false) ?? (lines.length >= 2 ? PaymentParser.parse(title, body, true) : null)
  return p ? { kind: 'card', p } : null
}

export function PastePaymentSheet({ onClose }: P<'pastePayment'>) {
  const nav = useNav()
  const settings = useSettings()
  const categories = useDB((s) => s.categories)
  const accounts = useDB((s) => s.accounts)
  const expenses = useDB((s) => s.expenses)
  const money = useMoney()
  const defAcc = useDefaultAccountId()
  const sym = useCurrencySymbol()
  const [text, setText] = useState('')
  const [canPaste] = useState(() => typeof navigator !== 'undefined' && !!navigator.clipboard?.readText)
  const found = useMemo(() => detect(text), [text])

  // Lo que se va a apuntar (editable): se rellena con lo detectado, con las mismas reglas que PaymentRecorder.
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [tag, setTag] = useState<SpendTag>('OTHER')
  const [categoryId, setCategoryId] = useState(0)
  const [accountId, setAccountId] = useState<number | null>(defAcc)
  useEffect(() => {
    if (!found) return
    let n: string, t: SpendTag, a: number
    if (found.kind === 'bizum') {
      const b = found.b
      const who = b.person ?? 'alguien'
      n = (b.incoming ? `Bizum de ${who}` : `Bizum a ${who}`) + (b.concept ? ` · ${b.concept}` : '')
      t = (b.concept ? Classifier.guess(b.concept) : null) ?? Classifier.classify(n, getState().expenses)
      a = b.amount
    } else {
      n = found.p.merchant ?? 'Pago con tarjeta'
      t = Classifier.classify(n, getState().expenses)
      a = found.p.amount
    }
    const cats = getState().categories
    setNote(n); setTag(t); setAmount(editable(a))
    setCategoryId(Classifier.bucketFor(t, cats) ?? cats.find((c) => !c.isSavings)?.id ?? cats[0]?.id ?? 0)
  }, [found])

  const raw = found ? (found.kind === 'bizum' ? found.b.raw : found.p.raw) : ''
  const incoming = found?.kind === 'bizum' && found.b.incoming
  const otherCurrency = found?.kind === 'card' && found.p.currency.toUpperCase() !== (settings.currency || 'EUR').toUpperCase() ? found.p.currency : null
  const ignored = found != null && settings.ignoredPayments.includes(PaymentParser.signature(raw))
  const value = parseAmount(amount)
  // Mismo importe apuntado desde un aviso hace menos de 3 minutos: probablemente ya está.
  const duplicate = value != null && expenses.some((e) => e.fromCard && Math.abs(e.amount - value) < 0.005 && Date.now() - e.createdAt < 3 * 60_000)

  const save = () => {
    if (!found || value == null) return
    const sourceApp = found.kind === 'bizum' ? 'Aviso pegado · Bizum' : 'Aviso pegado'
    if (incoming) {
      db.saveIncome({ sourceId: null, amount: value, note: note.trim() || 'Ingreso', epochDay: todayEpoch(), accountId, createdAt: Date.now() })
      toast('Ingreso apuntado')
    } else {
      db.saveExpense({
        categoryId, amount: value, note: note.trim() || 'Pago con tarjeta', epochDay: todayEpoch(), accountId,
        fromCard: true, sourceApp, createdAt: Date.now(), tag, rawText: raw.slice(0, 300),
      })
      toast('Gasto apuntado')
    }
    onClose()
  }

  const paste = async () => {
    try {
      const t = await navigator.clipboard.readText()
      if (t) setText(t)
      else toast('El portapapeles está vacío')
    } catch {
      toast('No se pudo leer el portapapeles: mantén pulsado el cuadro y elige Pegar')
    }
  }

  return (
    <FormSheet
      title="Pegar aviso del banco"
      onClose={onClose}
      primary={found ? (incoming ? 'Apuntar ingreso' : 'Apuntar gasto') : null}
      onPrimary={save}
      primaryEnabled={found != null && value != null && value > 0 && otherCurrency == null && (incoming || categories.some((c) => c.id === categoryId))}
    >
      <div className="sh-text">En iPhone la app no puede leer tus notificaciones. Copia el texto del aviso de tu banco (un pago con tarjeta o un Bizum) y pégalo aquí: saco el importe y el comercio.</div>
      {canPaste && (
        <button type="button" className="sh-choice" style={{ alignSelf: 'flex-start' }} onClick={() => void paste()}>
          <Icon name="paste" size={16} /><span>Pegar del portapapeles</span>
        </button>
      )}
      <textarea
        className="sh-textarea"
        aria-label="Texto del aviso"
        placeholder={'Santander\nPago con tarjeta de 23,45 € en MERCADONA'}
        value={text}
        rows={4}
        onChange={(e) => setText(e.target.value)}
      />

      {text.trim() !== '' && !found && (
        <div className="sh-box col" style={{ gap: 8 }}>
          <div className="sh-row" style={{ gap: 8 }}>
            <Icon name="info" style={{ color: 'var(--loss)' }} />
            <div className="sh-strong">No he encontrado ningún pago</div>
          </div>
          <div className="sh-text small">El texto tiene que incluir el importe (por ejemplo 12,50 €) y ser un pago o un Bizum, no un aviso de saldo, de código de seguridad o de devolución.</div>
          <button type="button" className="sh-link" onClick={() => nav.edit({ name: 'expense' })}>Apuntarlo a mano</button>
        </div>
      )}

      {found && (
        <>
          <div className="sh-box sh-mint sh-row">
            <Icon name={found.kind === 'bizum' ? 'swap' : 'creditCard'} style={{ color: 'var(--gain)', flexShrink: 0 }} />
            <div className="grow sh-text small ink">
              {found.kind === 'bizum'
                ? `${found.b.incoming ? 'Bizum recibido' : 'Bizum enviado'} de ${money.fmt(found.b.amount)}${found.b.person ? (found.b.incoming ? ` de ${found.b.person}` : ` a ${found.b.person}`) : ''}`
                : `Pago de ${money.fmt(found.p.amount)}${found.p.merchant ? ` en ${Merchants.clean(found.p.merchant)}` : ''}`}
            </div>
          </div>
          {otherCurrency && <div className="sh-text small sh-loss">El aviso está en {otherCurrency} y tu app usa {settings.currency}: no lo apunto (igual que en Android).</div>}
          {ignored && <div className="sh-text small sh-loss">Marcaste un aviso como este como "no era un pago". Si lo apuntas, se guardará igualmente.</div>}
          {duplicate && <div className="sh-text small sh-loss">Ya apuntaste un pago de este importe hace un momento: revisa que no esté repetido.</div>}
          <AmountField label="Importe" value={amount} onChange={setAmount} suffix={sym} />
          <TextField label="Descripción" value={note} onChange={setNote} />
          {!incoming && (
            <>
              <FieldLabel>Tipo de gasto</FieldLabel>
              <TagPicker selected={tag} onSelect={(t) => { setTag(t); const b = Classifier.bucketFor(t, categories); if (b != null) setCategoryId(b) }} />
              <FieldLabel>Parte del reparto</FieldLabel>
              <CategoryChoices categories={categories} value={categoryId} onChange={setCategoryId} />
            </>
          )}
          <FieldLabel>{incoming ? 'Entra en' : 'Sale de'}</FieldLabel>
          <AccountChoices accounts={accounts} value={accountId} onChange={setAccountId} none={incoming ? 'No sumar a ninguna' : 'No descontar'} />
        </>
      )}
    </FormSheet>
  )
}
