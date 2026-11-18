// Chat con tus finanzas (ChatScreen.kt): preguntas en lenguaje normal, respuestas con tus números y de dónde salen.
// Todo se calcula en el móvil con reglas fijas (Assistant), sin IA.
import { useEffect, useRef, useState } from 'react'
import { getState, db, toast } from '../../../data/store'
import { toEpochDay } from '../../../data/dates'
import type { Answer, ParsedExpense } from '../../../domain'
import { Assistant, Classifier, parsedExpenseDate, usePlanning } from '../../../domain'
import { Button, Overlay, SERIES_COLORS, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav } from '../../nav'

interface Message { fromUser: boolean; text: string; answer?: Answer }

const HELLO = 'Hola. Pregúntame lo que quieras sobre tu dinero: respondo con tus movimientos, tus gastos fijos y lo que viene, y te digo de dónde sale cada cosa. Todo se calcula en tu móvil.'

export function ChatScreen(_: { route: unknown }) {
  const nav = useNav()
  const p = usePlanning()
  const m = useMoney()
  const [messages, setMessages] = useState<Message[]>([{ fromUser: false, text: HELLO }])
  const [input, setInput] = useState('')
  const end = useRef<HTMLDivElement>(null)

  useEffect(() => { end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [messages.length])

  const ask = (q: string) => {
    if (!q.trim()) return
    const a = Assistant.answer(q, p.data, p.safe, p.forecast.events, m.fmt)
    setMessages((list) => [...list, { fromUser: true, text: q.trim() }, { fromUser: false, text: a.text, answer: a }])
    setInput('')
  }

  /** Apunta los gastos escritos en el chat (saveParsed del ViewModel). */
  const record = (list: ParsedExpense[]) => {
    const cats = getState().categories
    const fallback = cats.find((c) => !c.isSavings)?.id
    if (fallback == null) return
    const today = p.data.today
    const accountId = p.bankPlan?.main.id ?? null
    for (const x of list) {
      db.saveExpense({ categoryId: Classifier.bucketFor(x.tag, cats) ?? fallback, amount: x.amount, note: x.note, epochDay: toEpochDay(parsedExpenseDate(x, today)), accountId, tag: x.tag })
    }
    toast(list.length === 1 ? 'Gasto apuntado' : `${list.length} gastos apuntados`)
    setMessages((l) => [...l, { fromUser: false, text: `Hecho: ${list.length === 1 ? 'gasto apuntado' : `${list.length} gastos apuntados`}.` }])
  }

  return (
    <Overlay title={<div><div>Pregúntale a Ahorra, vaca</div><div className="small muted" style={{ fontWeight: 400, letterSpacing: 0 }}>Sin enviar tus datos a nadie</div></div>} onClose={nav.back}>
      <div className="col" style={{ gap: 8, paddingBottom: 88 }}>
        {messages.map((msg, i) => <Bubble key={i} m={msg} onRecord={record} />)}
        <div className="hm-chips-row">
          {Assistant.SUGGESTIONS.map((s) => <button key={s} className="hm-chat-chip chip" onClick={() => ask(s)}>{s}</button>)}
        </div>
        <div ref={end} />
      </div>
      <form className="hm-chat-input fixed" onSubmit={(e) => { e.preventDefault(); ask(input) }}>
        <input placeholder="Escribe tu pregunta o tus gastos…" aria-label="Escribe tu pregunta o tus gastos" value={input} onChange={(e) => setInput(e.target.value)} enterKeyHint="send" />
        <button type="submit" className={`hm-send ${input.trim() ? 'on' : ''}`} disabled={!input.trim()} aria-label="Enviar"><Icon name="send" /></button>
      </form>
    </Overlay>
  )
}

function Bubble({ m, onRecord }: { m: Message; onRecord: (l: ParsedExpense[]) => void }) {
  const [recorded, setRecorded] = useState(false)
  const a = m.answer
  return (
    <div className={`hm-msg-row ${m.fromUser ? 'me' : ''}`}>
      <div className={`hm-msg ${m.fromUser ? 'me' : 'other'}`} style={{ maxWidth: 320 }}>
        {m.text}
        {a && a.sources.length > 0 && (
          <div style={{ marginTop: 8 }}>
            <div className="tiny" style={{ fontWeight: 600, color: SERIES_COLORS[1] }}>De dónde sale</div>
            {a.sources.map((s) => <div key={s} className="small muted">· {s}</div>)}
          </div>
        )}
        {a && a.expenses.length > 0 && !recorded && (
          <Button small style={{ marginTop: 8 }} onClick={() => { setRecorded(true); onRecord(a.expenses) }}>
            {a.expenses.length === 1 ? 'Apuntar' : `Apuntar los ${a.expenses.length}`}
          </Button>
        )}
      </div>
    </div>
  )
}
