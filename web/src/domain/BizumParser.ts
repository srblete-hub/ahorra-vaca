// Port de payments/BizumParser.kt: lee los avisos de Bizum que pegues desde la app del banco.
import { PaymentParser } from './PaymentParser'

/** Un Bizum leído de un aviso del banco: importe, si lo recibes o lo envías, con quién y el concepto. */
export interface Bizum { amount: number; incoming: boolean; person: string | null; concept: string | null; raw: string }

const INCOMING = ['recibid', 'te ha enviado', 'te han enviado', 'te ha pagado', 'has cobrado', 'te envía', 'te envia', 'received']
const OUTGOING = ['has enviado', 'enviado a', 'has pagado', 'has realizado', 'enviaste', 'sent']
const IGNORE = ['solicitud', 'te pide', 'te ha pedido', 'pendiente', 'rechazad', 'caducad', 'cancelad', 'devuelto', 'no se ha', 'regístrate', 'registrate']
const NUM = '(\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)'
const AMOUNT = new RegExp(`${NUM}\\s?(?:€|EUR|euros?)`, 'i')
const NAME = '([A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ.]+){0,3})'
const FROM = new RegExp(`\\bde\\s+${NAME}`, 'g')
const TO = new RegExp(`\\ba\\s+${NAME}`, 'g')
const WHO_SENT = new RegExp(`^${NAME}\\s+te\\s+ha`, 'm')
const CONCEPT = /(?:concepto|motivo)\s*:?\s*["«']?([^"»'\n.]{1,60})/i

/** "JUAN PABLO" → "Juan Pablo". */
function pretty(s: string): string {
  let t = s.split(/\.\s/)[0].trim()
  while (t.endsWith('.')) t = t.slice(0, -1)
  return t.toLowerCase().split(/\s+/).map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w)).join(' ')
}

function firstNotBizum(re: RegExp, text: string): string | null {
  for (const m of text.matchAll(re)) if (m[1] !== 'Bizum') return m[1]
  return null
}

/**
 * Lee los avisos de Bizum de la app del banco (Santander, BBVA, CaixaBank…) sin conectar el banco:
 * "Has recibido un Bizum de 8,00 € de JUAN PABLO", "Has enviado 10,00 € a JUAN PABLO por Bizum. Concepto: pádel".
 */
export const BizumParser = {
  parse(title: string | null, text: string | null): Bizum | null {
    const full = [title, text].filter((x): x is string => x != null).join('\n').trim()
    const lower = full.toLowerCase()
    if (!lower.includes('bizum') || IGNORE.some((w) => lower.includes(w))) return null
    const incoming = INCOMING.some((w) => lower.includes(w)) && !OUTGOING.some((w) => lower.includes(w) && w !== 'enviado a')
    const outgoing = OUTGOING.some((w) => lower.includes(w))
    if (!incoming && !outgoing) return null
    const raw = AMOUNT.exec(full)?.[1]
    if (raw == null) return null
    const amount = PaymentParser.parseNumber(raw)
    if (amount == null || amount <= 0) return null
    const who = incoming ? (WHO_SENT.exec(full)?.[1] ?? firstNotBizum(FROM, full)) : firstNotBizum(TO, full)
    const person = who != null ? pretty(who) : null
    const c = CONCEPT.exec(full)?.[1]?.trim()
    const concept = c != null && c !== '' ? c : null
    return { amount, incoming, person, concept, raw: full }
  },
}
