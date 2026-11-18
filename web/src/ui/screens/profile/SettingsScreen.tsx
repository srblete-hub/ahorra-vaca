// Port de ui/screens/SettingsScreen.kt para la web.
// Se quitan los ajustes que solo existen en Android (huella, bloquear capturas, leer avisos de pagos con tarjeta)
// y se añaden tema, copia de seguridad, borrar datos e "Instalar en el iPhone".
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { db, exportJson, importJson, resetAll, toast, useSettings } from '../../../data/store'
import { today, isoDate } from '../../../data/dates'
import { refreshCrypto, refreshStocks } from '../../../data/services/actions'
import { Button, ConfirmDialog, IconTile, Overlay, SERIES_COLORS, SectionLabel, Segmented, Sheet } from '../../components'
import { Icon } from '../../icons'
import { useNav, type OverlayRoute } from '../../nav'
import { changePin } from '../../auth'
import { EditProfileSheet, InfoDialog } from './common'
import './profile.css'

const CURRENCIES = ['EUR', 'USD', 'GBP', 'CHF', 'MXN']
type Theme = 'system' | 'light' | 'dark'
const THEME_LABEL: Record<Theme, string> = { system: 'Sistema', light: 'Claro', dark: 'Oscuro' }

function isStandalone(): boolean {
  try {
    return (navigator as Navigator & { standalone?: boolean }).standalone === true || window.matchMedia('(display-mode: standalone)').matches
  } catch { return false }
}

function Row({ icon, color, title, subtitle, onClick, trailing, chevron, danger }: {
  icon: string; color: string; title: ReactNode; subtitle?: ReactNode; onClick?: () => void; trailing?: ReactNode; chevron?: boolean; danger?: boolean
}) {
  const content = (
    <>
      <IconTile icon={icon} color={color} size={40} />
      <div className="grow">
        <div className={`t ${danger ? 'danger' : ''}`}>{title}</div>
        {subtitle && <div className="s">{subtitle}</div>}
      </div>
      {trailing}
      {chevron && <Icon name="chevronRight" size={24} style={{ color: 'var(--muted)', flexShrink: 0 }} />}
    </>
  )
  return onClick
    ? <button className="pf-row clickable" onClick={onClick}>{content}</button>
    : <div className="pf-row">{content}</div>
}

function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button role="switch" aria-checked={checked} aria-label={label} onClick={() => onChange(!checked)} style={{ flexShrink: 0 }}>
      <div className={`switch ${checked ? 'on' : ''}`} />
    </button>
  )
}

/** Comparte el archivo con la hoja de iOS si se puede; si no, lo descarga. */
async function shareOrDownload(file: File) {
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
  if (nav.canShare && nav.canShare({ files: [file] })) {
    try { await nav.share({ files: [file], title: file.name }); return } catch (e) {
      if ((e as DOMException)?.name === 'AbortError') return
    }
  }
  const url = URL.createObjectURL(file)
  const a = document.createElement('a')
  a.href = url
  a.download = file.name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 4000)
}

export function SettingsScreen({ route: _route }: { route: OverlayRoute }) {
  const s = useSettings()
  const nav = useNav()
  const [editProfile, setEditProfile] = useState(false)
  const [pinMode, setPinMode] = useState<'create' | 'change' | 'remove' | null>(null)
  const [pendingImport, setPendingImport] = useState<string | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)
  const [installHelp, setInstallHelp] = useState(false)
  const importRef = useRef<HTMLInputElement>(null)
  const standalone = isStandalone()

  const setCurrency = (c: string) => {
    if (c === s.currency) return
    db.setSettings({ currency: c })
    refreshCrypto(true)
    refreshStocks(true)
  }
  const exportBackup = () => {
    const file = new File([exportJson()], `ahorra-vaca-${isoDate(today())}.json`, { type: 'application/json' })
    shareOrDownload(file)
  }
  const repeatSetup = () => {
    // Sustituye Ajustes por el asistente (así el "atrás" del historial sigue cuadrando).
    nav.replace({ name: 'onboarding' })
    db.setSettings({ onboardingDone: false })
  }

  return (
    <Overlay title="Ajustes" onClose={nav.back}>
      {/* ---------- Seguridad ---------- */}
      <SectionLabel>Seguridad</SectionLabel>
      <div className="pf-group">
        {s.pinHash ? (
          <>
            <Row icon="password" color={SERIES_COLORS[1]} title="Cambiar PIN" onClick={() => setPinMode('change')} chevron />
            <Row icon="lockOpen" color={SERIES_COLORS[4]} title="Quitar PIN" subtitle="La app se abrirá sin pedir nada" onClick={() => setPinMode('remove')} chevron />
            <Row icon="shield" color={SERIES_COLORS[3]} title="Bloquear ahora" subtitle="Pide el PIN para volver a entrar" onClick={() => window.location.reload()} />
          </>
        ) : (
          <Row icon="password" color={SERIES_COLORS[1]} title="Crear PIN" subtitle="6 cifras. Lo pedirá la app al abrirla y tras 1 minuto fuera" onClick={() => setPinMode('create')} chevron />
        )}
        <Row icon={s.hideAmounts ? 'eyeOff' : 'eye'} color={SERIES_COLORS[0]} title="Ocultar importes" subtitle="Muestra •••• en lugar de las cantidades"
          trailing={<Toggle checked={s.hideAmounts} onChange={(v) => db.setSettings({ hideAmounts: v })} label="Ocultar importes" />} />
      </div>

      {/* ---------- Perfil ---------- */}
      <SectionLabel>Perfil</SectionLabel>
      <div className="pf-group">
        <Row icon="person" color={SERIES_COLORS[3]} title="Nombre" subtitle={s.userName || '—'} onClick={() => setEditProfile(true)} chevron />
        <Row icon="verified" color="#1D9BF0" title="Tu @" subtitle={s.handle ? `@${s.handle}` : 'Elige tu @'} onClick={() => setEditProfile(true)} chevron />
        <Row icon="apps" color={SERIES_COLORS[0]} title="Repetir configuración inicial" subtitle="Vuelve a pasar por cuentas, ahorro, inversiones e ingresos" onClick={repeatSetup} chevron />
        <Row icon="euro" color={SERIES_COLORS[2]} title="Moneda" subtitle="Los precios de cripto y acciones se convierten; los saldos de cuentas no" />
        <div className="pf-row-below">
          <div className="pf-currencies" role="radiogroup" aria-label="Moneda">
            {CURRENCIES.map((c) => (
              <button key={c} role="radio" aria-checked={s.currency === c} className={s.currency === c ? 'on' : ''} onClick={() => setCurrency(c)}>{c}</button>
            ))}
          </div>
        </div>
      </div>

      {/* ---------- Apariencia ---------- */}
      <SectionLabel>Apariencia</SectionLabel>
      <div className="pf-group">
        <Row icon="brightness" color={SERIES_COLORS[4]} title="Tema" subtitle="Sistema sigue el modo claro u oscuro del iPhone" />
        <div className="pf-row-below">
          <Segmented<Theme> options={['system', 'light', 'dark']} value={s.theme} onChange={(t) => db.setSettings({ theme: t })} label={(t) => THEME_LABEL[t]} />
        </div>
      </div>

      {/* ---------- Ahorro ---------- */}
      <SectionLabel>Ahorro</SectionLabel>
      <div className="pf-group">
        <Row icon="pig" color={SERIES_COLORS[0]} title="Ahorro semanal" subtitle="Pepe elige una cantidad segura cada semana"
          trailing={<Toggle checked={s.weeklyTip} onChange={(v) => db.setSettings({ weeklyTip: v })} label="Ahorro semanal" />} />
        <Row icon={s.savingsLocked ? 'lock' : 'lockOpen'} color={SERIES_COLORS[6]} title="Bloquear el ahorro" subtitle="Mientras esté bloqueado no se puede retirar dinero del ahorro"
          trailing={<Toggle checked={s.savingsLocked} onChange={(v) => db.setSettings({ savingsLocked: v })} label="Bloquear el ahorro" />} />
      </div>

      {/* ---------- Amigos y privacidad ---------- */}
      <SectionLabel>Amigos y privacidad</SectionLabel>
      <div className="pf-group">
        <Row icon="shield" color={SERIES_COLORS[1]} title="Compartir logros de cantidades"
          subtitle={'"1.000 € ahorrados", "Patrimonio de 50.000 €", "Colchón"… Dan pistas de lo que tienes, así que por defecto no se comparten'}
          trailing={<Toggle checked={s.shareMoneyAchievements} onChange={(v) => db.setSettings({ shareMoneyAchievements: v })} label="Compartir logros de cantidades" />} />
        <Row icon="shield" color={SERIES_COLORS[0]} title="Lo que ven tus amigos"
          subtitle="Tu @, nombre, foto, nivel y los logros que compartes. Nunca tus cuentas, movimientos ni importes. Lo demás no sale del móvil: a internet solo se piden precios de cripto (CoinGecko) y acciones (Yahoo Finance)." />
      </div>

      {/* ---------- Copia de seguridad ---------- */}
      <SectionLabel>Copia de seguridad</SectionLabel>
      <div className="pf-group">
        <Row icon="download" color={SERIES_COLORS[0]} title="Exportar mis datos" subtitle="Guarda un archivo .json con todo. Tus datos solo viven en este móvil: haz copia de vez en cuando" onClick={exportBackup} chevron />
        <Row icon="upload" color={SERIES_COLORS[3]} title="Importar una copia" subtitle="Sustituye lo que hay ahora por lo del archivo" onClick={() => importRef.current?.click()} chevron />
        <Row icon="delete" color="var(--loss)" title="Borrar todos los datos" danger subtitle="Cuentas, movimientos, ajustes… todo. No se puede deshacer" onClick={() => setConfirmReset(true)} />
        <input ref={importRef} type="file" accept="application/json,.json" hidden onChange={async (e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (f) setPendingImport(await f.text())
        }} />
      </div>

      {/* ---------- Instalar ---------- */}
      {!standalone && (
        <>
          <SectionLabel>App</SectionLabel>
          <div className="pf-group">
            <Row icon="addBox" color={SERIES_COLORS[1]} title="Instalar en el iPhone" subtitle="Safari → Compartir → Añadir a pantalla de inicio" onClick={() => setInstallHelp(true)} chevron />
          </div>
        </>
      )}

      {editProfile && <EditProfileSheet onClose={() => setEditProfile(false)} />}
      {pinMode && <PinSheet mode={pinMode} onClose={() => setPinMode(null)} />}
      {pendingImport != null && (
        <ConfirmDialog title="¿Importar esta copia?" body="Se sustituyen todos tus datos actuales por los del archivo." confirm="Importar"
          onClose={() => setPendingImport(null)}
          onConfirm={() => {
            try { importJson(pendingImport); toast('Copia importada') } catch (err) { toast(err instanceof Error && err.message ? err.message : 'Ese archivo no es una copia válida') }
          }} />
      )}
      {confirmReset && (
        <ConfirmDialog title="¿Borrar todos los datos?" body="Se borran tus cuentas, movimientos, objetivos y ajustes de este móvil. No se puede deshacer: exporta una copia antes si la quieres."
          confirm="Borrar todo" onClose={() => setConfirmReset(false)} onConfirm={() => { resetAll(); toast('Datos borrados') }} />
      )}
      {installHelp && (
        <InfoDialog icon={<IconTile icon="iosShare" color="var(--brand)" size={56} />} title="Instalar en el iPhone" onClose={() => setInstallHelp(false)}>
          <ol className="pf-install-steps" style={{ textAlign: 'left' }}>
            <li>Abre esta página en <b>Safari</b>.</li>
            <li>Toca <b>Compartir</b> <Icon name="iosShare" size={16} style={{ verticalAlign: '-2px' }} /> (abajo, en el centro).</li>
            <li>Elige <b>Añadir a pantalla de inicio</b> y toca <b>Añadir</b>.</li>
          </ol>
          <div className="small muted">Se abrirá a pantalla completa, como una app.</div>
        </InfoDialog>
      )}
    </Overlay>
  )
}

/** Crear, cambiar o quitar el PIN (ChangePinDialog). */
function PinSheet({ mode, onClose }: { mode: 'create' | 'change' | 'remove'; onClose: () => void }) {
  const steps = mode === 'create' ? ['fresh', 'repeat'] : mode === 'change' ? ['current', 'fresh', 'repeat'] : ['current']
  const [step, setStep] = useState(0)
  const [value, setValue] = useState('')
  const [current, setCurrent] = useState('')
  const [fresh, setFresh] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const kind = steps[step]
  const title = kind === 'current' ? 'PIN actual' : kind === 'fresh' ? 'PIN nuevo (6 cifras)' : 'Repite el PIN nuevo'
  useEffect(() => { input.current?.focus() }, [step])

  const submit = async (pin: string) => {
    setValue('')
    if (kind === 'current') {
      setCurrent(pin)
      if (mode === 'remove') {
        setBusy(true)
        const ok = await changePin(pin, null)
        setBusy(false)
        if (ok) { toast('PIN quitado'); onClose() } else setError('El PIN actual no es correcto')
        return
      }
      setError(null); setStep(step + 1); return
    }
    if (kind === 'fresh') { setFresh(pin); setError(null); setStep(step + 1); return }
    if (pin !== fresh) { setError('No coincide'); setStep(steps.indexOf('fresh')); return }
    setBusy(true)
    const ok = await changePin(current, fresh)
    setBusy(false)
    if (ok) { toast(mode === 'create' ? 'PIN creado' : 'PIN cambiado'); onClose() } else { setError('El PIN actual no es correcto'); setStep(0) }
  }

  return (
    <Sheet title={title} onClose={onClose}>
      <div className="form" style={{ alignItems: 'stretch' }}>
        <div className="field">
          <label htmlFor="pf-pin">{title}</label>
          <input id="pf-pin" ref={input} className="pf-pin-input" type="password" inputMode="numeric" pattern="[0-9]*" autoComplete="off" maxLength={6}
            value={value} disabled={busy}
            onChange={(e) => {
              const v = e.target.value.replace(/\D/g, '').slice(0, 6)
              setValue(v)
              if (v.length === 6) submit(v)
            }} />
        </div>
        <div style={{ minHeight: 20, color: 'var(--loss)', fontSize: 14, textAlign: 'center' }}>{error}</div>
        <Button variant="secondary" onClick={onClose}>Cancelar</Button>
      </div>
    </Sheet>
  )
}
