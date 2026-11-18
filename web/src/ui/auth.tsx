// Bienvenida, crear PIN y desbloqueo (equivale a ui/auth/AuthScreens.kt y AppViewModel).
// El PIN nunca se guarda: solo su hash PBKDF2-SHA256 con sal (igual que en Android).
import { useEffect, useState } from 'react'
import { db, getState, useSettings } from '../data/store'
import { Icon } from './icons'
import { Spots } from './components'

const ITERATIONS = 120_000
const MAX_ATTEMPTS = 5
const LOCKOUT_MS = 30_000

function b64(bytes: ArrayBuffer | Uint8Array) {
  const a = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  let s = ''
  a.forEach((b) => (s += String.fromCharCode(b)))
  return btoa(s)
}
function unb64(s: string) { return Uint8Array.from(atob(s), (c) => c.charCodeAt(0)) }

async function derive(pin: string, salt: Uint8Array, iterations: number): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveBits'])
  return crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations }, key, 256)
}
/** Formato guardado: "iteraciones:sal:hash" en Base64. */
export async function hashPin(pin: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16))
  return `${ITERATIONS}:${b64(salt)}:${b64(await derive(pin, salt, ITERATIONS))}`
}
export async function verifyPin(pin: string, stored: string): Promise<boolean> {
  const parts = stored.split(':')
  if (parts.length !== 3) return false
  const got = new Uint8Array(await derive(pin, unb64(parts[1]), Number(parts[0])))
  const exp = unb64(parts[2])
  if (got.length !== exp.length) return false
  let diff = 0
  for (let i = 0; i < got.length; i++) diff |= got[i] ^ exp[i]
  return diff === 0
}

// ---------- Teclado de PIN ----------

function PinPad({ title, subtitle, error, onComplete, busy, footer }: { title: string; subtitle: string; error?: string; onComplete: (pin: string) => void; busy?: boolean; footer?: React.ReactNode }) {
  const [pin, setPin] = useState('')
  const [shake, setShake] = useState(false)
  useEffect(() => { if (error) { setShake(true); setPin(''); const t = setTimeout(() => setShake(false), 400); return () => clearTimeout(t) } }, [error])
  useEffect(() => {
    if (pin.length === 6) { const p = pin; setTimeout(() => { onComplete(p); setPin('') }, 120) }
  }, [pin]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if (/^\d$/.test(e.key)) setPin((p) => (p.length < 6 ? p + e.key : p))
      else if (e.key === 'Backspace') setPin((p) => p.slice(0, -1))
    }
    window.addEventListener('keydown', h)
    return () => window.removeEventListener('keydown', h)
  }, [])
  const press = (d: string) => { if (!busy) setPin((p) => (p.length < 6 ? p + d : p)) }
  const key = (d: string) => (
    <button key={d} onClick={() => press(d)} aria-label={d} style={{ width: 80, height: 80, borderRadius: '50%', background: 'var(--card)', boxShadow: 'var(--shadow)', fontSize: 28, fontWeight: 700 }}>{d}</button>
  )
  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', alignItems: 'center', padding: 'calc(var(--safe-top) + 64px) 24px calc(var(--safe-bottom) + 24px)', gap: 16, background: 'var(--bg)' }}>
      <div className="big-title" style={{ textAlign: 'center' }}>{title}</div>
      <div className="muted" style={{ textAlign: 'center' }}>{subtitle}</div>
      <div style={{ display: 'flex', gap: 16, margin: '24px 0', animation: shake ? 'shake 0.35s' : undefined }} aria-label={`${pin.length} de 6 cifras`}>
        {Array.from({ length: 6 }, (_, i) => (
          <span key={i} style={{ width: 16, height: 16, borderRadius: '50%', background: i < pin.length ? 'var(--ink)' : 'var(--line)', transition: 'background 0.1s' }} />
        ))}
      </div>
      <div style={{ minHeight: 20, color: 'var(--loss)', fontSize: 15, textAlign: 'center' }}>{error}</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 80px)', gap: 16 }}>
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map(key)}
        <span />
        {key('0')}
        <button onClick={() => setPin((p) => p.slice(0, -1))} aria-label="Borrar" style={{ width: 80, height: 80, display: 'grid', placeItems: 'center' }}>
          <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor"><path d="M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12 19 15.59z" /></svg>
        </button>
      </div>
      {footer}
      <style>{'@keyframes shake{0%,100%{transform:none}25%{transform:translateX(-8px)}75%{transform:translateX(8px)}}'}</style>
    </div>
  )
}

// ---------- Pantallas ----------

/** Primera vez: nombre → PIN → repetir PIN. */
export function Welcome({ onDone }: { onDone: () => void }) {
  const [step, setStep] = useState<'name' | 'pin' | 'repeat'>('name')
  const [name, setName] = useState('')
  const [first, setFirst] = useState('')
  const [error, setError] = useState<string>()

  if (step === 'pin') {
    return <PinPad title="Crea tu PIN" subtitle="6 cifras. Lo pedirá la app al abrirla." error={error} onComplete={(p) => { setFirst(p); setError(undefined); setStep('repeat') }}
      footer={<button className="link-btn" onClick={() => { db.setSettings({ userName: name.trim() }); onDone() }}>Seguir sin PIN</button>} />
  }
  if (step === 'repeat') {
    return <PinPad title="Repite el PIN" subtitle="Para asegurarnos de que lo recuerdas." error={error} onComplete={async (p) => {
      if (p !== first) { setError('No coincide. Vuelve a crearlo.'); setStep('pin'); return }
      db.setSettings({ userName: name.trim(), pinHash: await hashPin(p) })
      onDone()
    }} />
  }
  return (
    <div style={{ minHeight: '100dvh', background: 'var(--tinta)', color: 'var(--leche)', position: 'relative', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', inset: 0, opacity: 0.9 }}><Spots /></div>
      <div style={{ position: 'relative', maxWidth: 560, margin: '0 auto', padding: 'calc(var(--safe-top) + 64px) 24px calc(var(--safe-bottom) + 32px)', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <img src="icon.svg" alt="" width={72} height={72} style={{ borderRadius: 20 }} />
        <h1 style={{ fontSize: 48, fontWeight: 800, letterSpacing: '-0.045em', lineHeight: 1 }}>
          Ahorra,<br /><span style={{ color: 'var(--heno)' }}>vaca.</span>
        </h1>
        <p style={{ fontSize: 19, color: 'rgba(246,241,230,0.8)' }}>
          ¿Sabes cuánto tienes ahora mismo? Banco, ahorros en casa, cripto y acciones en un solo número.
        </p>
        <div className="field" style={{ background: 'rgba(246,241,230,0.1)', marginTop: 16 }}>
          <input aria-label="¿Cómo te llamas?" placeholder="¿Cómo te llamas?" value={name} onChange={(e) => setName(e.target.value)} maxLength={30}
            onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) setStep('pin') }} style={{ color: 'var(--leche)', fontSize: 18, minHeight: 36 }} />
        </div>
        <button className="btn" disabled={!name.trim()} onClick={() => setStep('pin')} style={{ background: 'var(--heno)', color: 'var(--tinta)' }}>Empezar</button>
        <div style={{ textAlign: 'center', fontSize: 14, color: 'rgba(246,241,230,0.6)' }}>
          <Icon name="lock" size={14} style={{ verticalAlign: '-2px' }} /> Tus datos se guardan solo en este móvil.
        </div>
      </div>
    </div>
  )
}

let failed = 0
let lockedUntil = 0
/** Pantalla de desbloqueo con PIN. Tras 5 fallos seguidos bloquea 30 s. */
export function Unlock({ onUnlock }: { onUnlock: () => void }) {
  const s = useSettings()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  return (
    <PinPad title={`Hola${s.userName ? ', ' + s.userName : ''}`} subtitle="Escribe tu PIN" error={error} busy={busy} onComplete={async (pin) => {
      const now = Date.now()
      if (now < lockedUntil) { setError(`Espera ${Math.ceil((lockedUntil - now) / 1000)} s`); return }
      setBusy(true)
      const ok = await verifyPin(pin, s.pinHash)
      setBusy(false)
      if (ok) { failed = 0; onUnlock(); return }
      failed++
      if (failed >= MAX_ATTEMPTS) { failed = 0; lockedUntil = now + LOCKOUT_MS; setError('Demasiados intentos. Espera 30 s.') }
      else setError(`PIN incorrecto. Te quedan ${MAX_ATTEMPTS - failed} intentos.`)
    }} />
  )
}

/** Cambiar o quitar el PIN desde Ajustes. */
export async function changePin(current: string, next: string | null): Promise<boolean> {
  const stored = getState().settings.pinHash
  if (stored && !(await verifyPin(current, stored))) return false
  db.setSettings({ pinHash: next ? await hashPin(next) : '' })
  return true
}
