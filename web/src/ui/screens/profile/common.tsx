// Piezas compartidas del perfil: medallas de logros, diálogos, editar perfil y recorte de la foto.
import { useEffect, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { Achievement } from '../../../domain'
import { db, toast, useSettings } from '../../../data/store'
import { Button, SERIES_COLORS, Sheet, TextField } from '../../components'
import { Icon } from '../../icons'
import './profile.css'

/** Icono y color de cada logro (iconFor de ProfileScreen.kt). */
export function achievementIcon(key: string): [string, string] {
  const c = SERIES_COLORS
  switch (key) {
    case 'flag': return ['flag', c[1]]
    case 'card': return ['card', c[5]]
    case 'calendar': return ['calendar', c[4]]
    case 'savings': return ['savings', c[0]]
    case 'trending': return ['trendingUp', c[3]]
    case 'shield': return ['shield', c[1]]
    case 'leaf': return ['eco', c[0]]
    case 'fire': return ['fire', c[4]]
    case 'target': return ['target', c[6]]
    case 'piggy': return ['savings', c[6]]
    case 'crown': return ['trophy', c[7]]
    case 'people': return ['groups', c[5]]
    case 'heart': return ['heart', c[6]]
    default: return ['trophy', c[2]]
  }
}

/** Medalla de un logro: a color si está conseguido, gris con su progreso si no. */
export function AchievementBadge({ a, size = 64 }: { a: Achievement; size?: number }) {
  const [icon, color] = achievementIcon(a.icon)
  const stroke = 4
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const inner = size * 0.82
  return (
    <div style={{ position: 'relative', width: size, height: size, display: 'grid', placeItems: 'center', flexShrink: 0 }} aria-label={a.title}>
      {!a.unlocked && (
        <svg width={size} height={size} style={{ position: 'absolute', inset: 0, transform: 'rotate(-90deg)' }} aria-hidden="true">
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--chip)" strokeWidth={stroke} />
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round"
            strokeDasharray={`${Math.max(0, Math.min(1, a.progress)) * c} ${c}`} />
        </svg>
      )}
      <div style={{ width: inner, height: inner, borderRadius: '50%', display: 'grid', placeItems: 'center', background: a.unlocked ? color : 'var(--chip)', color: a.unlocked ? '#fff' : 'var(--muted)' }}>
        <Icon name={a.unlocked ? icon : 'lock'} size={Math.round(size * 0.38)} />
      </div>
    </div>
  )
}

/** Diálogo centrado sencillo (AlertDialog de Compose) con un botón. */
export function InfoDialog({ icon, title, children, button = 'Vale', buttonColor = 'var(--ink)', onClose }: {
  icon?: ReactNode; title?: ReactNode; children?: ReactNode; button?: string; buttonColor?: string; onClose: () => void
}) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', h)
    return () => window.removeEventListener('keydown', h)
  }, [onClose])
  return createPortal(
    <>
      <div className="scrim dialog-scrim" onClick={onClose} />
      <div className="dialog" role="alertdialog" aria-modal="true" style={{ textAlign: 'center', alignItems: 'center' }}>
        {icon && <div className="pf-dialog-badge">{icon}</div>}
        {title && <div className="title" style={{ fontWeight: 800 }}>{title}</div>}
        {children}
        <div className="row" style={{ justifyContent: 'flex-end', width: '100%' }}>
          <Button variant="ghost" small onClick={onClose} style={{ color: buttonColor }}>{button}</Button>
        </div>
      </div>
    </>,
    document.body,
  )
}

/** Detalle de un logro al tocarlo en el perfil. */
export function AchievementDialog({ a, onClose }: { a: Achievement; onClose: () => void }) {
  const pctDone = Math.floor(a.progress * 100)
  return (
    <InfoDialog icon={<AchievementBadge a={a} size={72} />} title={a.title} onClose={onClose}>
      <div style={{ whiteSpace: 'pre-line' }}>
        {a.description + '\n\n' + (a.unlocked ? `Conseguido · +${a.points} pts` : `Llevas el ${pctDone} % · vale ${a.points} pts`)}
      </div>
    </InfoDialog>
  )
}

/** Celebración cuando se consigue un logro nuevo (AchievementDialog de MainScreen.kt). */
export function CelebrationDialog({ list, onClose }: { list: Achievement[]; onClose: () => void }) {
  return (
    <InfoDialog icon={<AchievementBadge a={list[0]} size={72} />} title={list.length === 1 ? '¡Nuevo logro!' : `¡${list.length} logros nuevos!`}
      button="¡Genial!" buttonColor="var(--gain)" onClose={onClose}>
      <div className="col" style={{ gap: 8, alignItems: 'center' }}>
        {list.slice(0, 4).map((a) => (
          <div key={a.id}>
            <div style={{ fontWeight: 700, fontSize: 17 }}>{a.title}</div>
            <div className="small muted">{a.description} · +{a.points} pts</div>
          </div>
        ))}
        {list.length > 4 && <div className="muted">y {list.length - 4} más en tu perfil</div>}
      </div>
    </InfoDialog>
  )
}

/** @handle: 3–20 letras minúsculas, números, punto o guion bajo (AppViewModel.HANDLE_REGEX). */
export const HANDLE_REGEX = /^[a-z0-9._]{3,20}$/

/** Editar nombre y @ (EditProfileSheet). "Borrar" quita la foto. */
export function EditProfileSheet({ onClose }: { onClose: () => void }) {
  const s = useSettings()
  const [name, setName] = useState(s.userName)
  const [handle, setHandle] = useState(s.handle)
  const clean = handle.trim().replace(/^@/, '').toLowerCase()
  const valid = clean === '' || HANDLE_REGEX.test(clean)
  return (
    <Sheet title="Editar perfil" onClose={onClose}>
      <div className="form">
        <TextField label="Nombre" value={name} onChange={setName} maxLength={30} />
        <TextField label="Tu @ (por ejemplo: alex.ahorra)" value={handle} onChange={(v) => setHandle(v.replace(/ /g, ''))} maxLength={21} />
        <div className="small" style={{ color: valid ? 'var(--muted)' : 'var(--loss)', padding: '0 4px' }}>
          {valid ? `Así te encontrarán tus amigos: @${clean || 'tu_nombre'}` : 'Entre 3 y 20 caracteres: letras minúsculas, números, punto o guion bajo.'}
        </div>
        {s.avatar && <div className="small muted" style={{ padding: '0 4px' }}>"Borrar" quita tu foto de perfil.</div>}
        <Button disabled={!name.trim() || !valid} onClick={() => { db.setSettings({ userName: name.trim(), handle: clean }); onClose() }}>Guardar</Button>
        {s.avatar && <Button variant="danger" onClick={() => { db.setSettings({ avatar: '' }); onClose() }}>Borrar</Button>}
      </div>
    </Sheet>
  )
}

/** Recorta la foto al cuadrado central, la escala a 512 px y la devuelve como JPEG (data URL). */
export async function cropAvatar(file: File): Promise<string> {
  const url = URL.createObjectURL(file)
  try {
    const img = await new Promise<HTMLImageElement>((ok, ko) => {
      const i = new Image()
      i.onload = () => ok(i)
      i.onerror = () => ko(new Error('No se pudo leer la foto'))
      i.src = url
    })
    const side = Math.min(img.naturalWidth, img.naturalHeight)
    const canvas = document.createElement('canvas')
    canvas.width = 512
    canvas.height = 512
    const ctx = canvas.getContext('2d')
    if (!ctx || side <= 0) throw new Error('No se pudo leer la foto')
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, 512, 512)
    return canvas.toDataURL('image/jpeg', 0.88)
  } finally {
    URL.revokeObjectURL(url)
  }
}

/** Guarda la foto elegida (de un <input type="file">) como foto de perfil. */
export async function saveAvatar(file: File | undefined | null) {
  if (!file) return
  try { db.setSettings({ avatar: await cropAvatar(file) }) } catch { toast('No se pudo usar esa foto') }
}
