// Pepe, el pastor: tarjeta de Inicio, chat a pantalla completa (PepeUi.kt) y "Pepe te recomienda" (StoryCards.kt).
// Sin IA: las respuestas salen de PepeBot (reglas fijas con tus datos).
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import type { PepeCtx, PepeTip } from '../../../domain'
import { Pepe, PepeBot } from '../../../domain'
import { Button, useMoney } from '../../components'
import { Icon } from '../../icons'
import { PepeAvatar, PillButton } from './shared'

/** Tarjeta de Inicio: Pepe te contesta aquí mismo con tus números. */
export function PepeCard({ onAsk }: { onAsk: (q: string) => void }) {
  const [own, setOwn] = useState('')
  return (
    <div className="card">
      <div className="row" style={{ gap: 16, alignItems: 'flex-start' }}>
        <PepeAvatar size={64} title="Pepe, el pastor" />
        <div className="grow">
          <div style={{ fontSize: 20, fontWeight: 600 }}>Pregúntale a Pepe</div>
          <div className="hm-bubble-chip">¿Te echo una mano con tus cuentas? Como con el rebaño: ni una oveja suelta.</div>
        </div>
      </div>
      <div className="hm-flow" style={{ marginTop: 16 }}>
        {Pepe.QUESTIONS.map((q) => <button key={q} className="hm-q-chip" onClick={() => onAsk(q)}>{q}</button>)}
      </div>
      <form className="col" style={{ marginTop: 16 }} onSubmit={(e) => { e.preventDefault(); if (own.trim()) { onAsk(own); setOwn('') } }}>
        <div className="field">
          <input aria-label="O pregúntale lo que quieras" placeholder="O pregúntale lo que quieras" value={own} onChange={(e) => setOwn(e.target.value)} enterKeyHint="send" />
        </div>
        <Button type="submit" disabled={!own.trim()}>Preguntar a Pepe</Button>
      </form>
      <div className="tiny muted" style={{ marginTop: 8 }}>Pepe te contesta aquí, con tus números. Gratis y sin salir de la app.</div>
    </div>
  )
}

interface PepeMsg { fromPepe: boolean; text: string }
/** La conversación se guarda mientras la app esté abierta (PepeChatState). */
const chatState: PepeMsg[] = []

const EXTRA = ['¿Puedo gastarme 30 €?', '¿Cuánto gasto en comida?', 'Mis suscripciones']

/** Chat con Pepe a pantalla completa. */
export function PepeChat({ ctx, first, onClose }: { ctx: PepeCtx; first: string | null; onClose: () => void }) {
  const [msgs, setMsgs] = useState<PepeMsg[]>(() => [...chatState])
  const [input, setInput] = useState('')
  const listRef = useRef<HTMLDivElement>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const started = useRef(false)

  // Con el teclado de iPhone abierto, el chat se ajusta a lo que queda visible.
  useEffect(() => {
    const vv = window.visualViewport
    const el = rootRef.current
    if (!vv || !el) return
    const fit = () => { el.style.height = `${vv.height}px`; el.style.top = `${vv.offsetTop}px` }
    fit()
    vv.addEventListener('resize', fit)
    vv.addEventListener('scroll', fit)
    return () => { vv.removeEventListener('resize', fit); vv.removeEventListener('scroll', fit) }
  }, [])

  const ask = (q: string) => {
    if (!q.trim()) return
    chatState.push({ fromPepe: false, text: q.trim() }, { fromPepe: true, text: PepeBot.answer(q, ctx) })
    setMsgs([...chatState])
  }

  useEffect(() => {
    if (started.current) return
    started.current = true
    if (chatState.length === 0) chatState.push({ fromPepe: true, text: PepeBot.greeting(ctx.userName) })
    if (first) ask(first)
    else setMsgs([...chatState])
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const el = listRef.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }, [msgs.length])

  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', h)
    return () => { document.body.style.overflow = prev; window.removeEventListener('keydown', h) }
  }, [onClose])

  return createPortal(
    <div ref={rootRef} className="hm-chat" role="dialog" aria-modal="true" aria-label="Pepe">
      <div className="hm-chat-head">
        <PepeAvatar size={48} />
        <div className="grow">
          <div style={{ fontSize: 18, fontWeight: 600 }}>Pepe</div>
          <div className="tiny muted">Tu pastor de las cuentas</div>
        </div>
        <button className="icon-btn filled" aria-label="Cerrar" onClick={onClose}><Icon name="close" /></button>
      </div>
      <div className="hm-chat-list" ref={listRef}>
        {msgs.map((m, i) => (
          <div key={i} className={`hm-msg-row ${m.fromPepe ? '' : 'me'}`}>
            {m.fromPepe && <PepeAvatar size={32} />}
            <div className={`hm-msg ${m.fromPepe ? 'other' : 'me'}`}>{m.text}</div>
          </div>
        ))}
      </div>
      <div className="hm-chat-chips">
        {[...Pepe.QUESTIONS, ...EXTRA].map((q) => <button key={q} className="hm-chat-chip" onClick={() => ask(q)}>{q}</button>)}
      </div>
      <form className="hm-chat-input" onSubmit={(e) => { e.preventDefault(); ask(input); setInput('') }}>
        <textarea rows={1} placeholder="Escribe a Pepe…" aria-label="Escribe a Pepe" value={input} onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); ask(input); setInput('') } }} />
        <button type="submit" className={`hm-send ${input.trim() ? 'on' : ''}`} disabled={!input.trim()} aria-label="Enviar"><Icon name="send" /></button>
      </form>
    </div>,
    document.body,
  )
}

/** "Pepe te recomienda": pasar una cantidad segura al ahorro esta semana. */
export function PepeTipCard({ tip, onMove, onLater }: { tip: PepeTip; onMove: () => void; onLater: () => void }) {
  const m = useMoney()
  const amount = m.hidden ? m.fmt(tip.amount) : `${m.fmt(tip.amount).split(',')[0]} €`
  return (
    <div className="hm-paper-card">
      <div className="hm-spark"><PepeAvatar size={20} /> Pepe te recomienda</div>
      <div className="hm-serif" style={{ fontSize: 22, lineHeight: '28px', marginTop: 16 }}>{tip.text}</div>
      <div className="row" style={{ gap: 8, marginTop: 16 }}>
        <PillButton dark onClick={onMove}>Pasar {amount}</PillButton>
        <PillButton onClick={onLater}>Ahora no</PillButton>
      </div>
    </div>
  )
}
