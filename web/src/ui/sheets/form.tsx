// Piezas comunes de los formularios (FormSheet, Choice, FieldLabel, TagPicker… de Components.kt / Sheets.kt / SpendUi.kt).
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import './sheets.css'
import { Button, ConfirmDialog, Sheet, Switch, colorFor } from '../components'
import { Icon } from '../icons'
import type { Account, AccountType, Category } from '../../data/models'
import { SYMBOLS, num } from '../../data/format'
import { isoDate, parseIsoDate, type LocalDate } from '../../data/dates'
import { getState, useDB, useSettings } from '../../data/store'
import { TAG_COLOR, TAG_ICON, MerchantLogo, TagCircle, categoryIcon } from '../screens/budget/spend'
import { SPEND_TAG_INFO, SPEND_TAG_VALUES, type SpendTag } from '../../domain'

// ---------- Formato ----------

export function currencySymbol(code: string): string { return SYMBOLS[code] ?? code }
export function useCurrencySymbol(): string { return currencySymbol(useSettings().currency || 'EUR') }

/** Double.editable(): texto para un campo (sin miles, coma decimal, hasta 8 decimales); 0 = vacío. */
export function editable(v: number | null | undefined): string {
  if (!v || !Number.isFinite(v)) return ''
  return v.toFixed(8).replace(/\.?0+$/, '').replace('.', ',')
}
/** quantity(): 0,00012345 (hasta 8 decimales, con miles). */
export function quantity(v: number): string { return num(v, 8) }
/** pct() de Kotlin: "%.1f %" en español. */
export function pct1(v: number): string { return v.toFixed(1).replace('.', ',') + ' %' }
export function signedPct1(v: number): string { return (v > 0 ? '+' : '') + pct1(v) }

// ---------- Iconos y colores ----------

export function accountIcon(t: AccountType): string {
  switch (t) {
    case 'BANK': return 'bank'
    case 'BANK_SAVINGS': return 'savings'
    case 'CASH': return 'payments'
    case 'HOME_SAVINGS': return 'home'
    case 'WALLET': return 'digitalWallet'
    case 'FUND': return 'spa'
    case 'OTHER': return 'wallet'
    case 'DEBT': return 'creditCard'
  }
}

/** symbolColor() de InvestmentsScreen.kt. */
export function symbolColor(symbol: string): string {
  switch (symbol.toUpperCase()) {
    case 'BTC': return '#F7931A'
    case 'ETH': return '#627EEA'
    case 'SOL': return '#9945FF'
    case 'ADA': return '#0033AD'
    case 'XRP': return '#23292F'
    case 'DOGE': return '#C2A633'
    case 'USDT': case 'USDC': return '#26A17B'
    case 'AAPL': return '#1C1C1E'
  }
  const palette = ['#1E9D57', '#3B82F6', '#8B5CF6', '#EF6C4A', '#14B8A6', '#E5578A']
  let h = 0
  for (let i = 0; i < symbol.length; i++) h = (Math.imul(31, h) + symbol.charCodeAt(i)) | 0
  return palette[((h % 6) + 6) % 6]
}

// ---------- Cuenta por defecto ----------

/**
 * Cuenta de la que salen los pagos por defecto. En Android es la de los pagos con tarjeta (cardAccountId)
 * o la primera cuenta bancaria; en la web no hay lector de tarjeta, así que se usa la principal del reparto.
 */
export function defaultAccountId(accounts: Account[] = getState().accounts, mainId = getState().settings.bankMainId): number | null {
  return accounts.find((a) => a.id === mainId)?.id ?? accounts.find((a) => a.type === 'BANK')?.id ?? null
}
export function useDefaultAccountId(): number | null {
  const accounts = useDB((s) => s.accounts)
  const mainId = useDB((s) => s.settings.bankMainId)
  return defaultAccountId(accounts, mainId)
}

// ---------- Teclado de iPhone ----------

/**
 * En Safari de iPhone el teclado no encoge la ventana: la hoja (fija abajo) quedaría debajo.
 * Se sube la hoja por encima del teclado con visualViewport y se lleva a la vista el campo con foco.
 */
function useKeyboardInset(ref: React.RefObject<HTMLElement>) {
  useEffect(() => {
    const inner = ref.current
    const sheet = inner?.closest('.sheet') as HTMLElement | null
    const vv = window.visualViewport
    if (!inner || !sheet) return
    const apply = () => {
      if (!vv) return
      const layout = document.documentElement.clientHeight
      const inset = Math.max(0, Math.round(layout - (vv.offsetTop + vv.height)))
      if (inset > 40) {
        sheet.style.bottom = `${inset}px`
        sheet.style.maxHeight = `${Math.max(240, vv.height - 16)}px`
      } else {
        sheet.style.bottom = ''
        sheet.style.maxHeight = ''
      }
    }
    let t = 0
    const onFocus = (e: FocusEvent) => {
      const el = e.target as HTMLElement
      if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement)) return
      window.clearTimeout(t)
      t = window.setTimeout(() => el.scrollIntoView({ block: 'nearest' }), 320)
    }
    vv?.addEventListener('resize', apply)
    vv?.addEventListener('scroll', apply)
    inner.addEventListener('focusin', onFocus)
    apply()
    return () => {
      vv?.removeEventListener('resize', apply)
      vv?.removeEventListener('scroll', apply)
      inner.removeEventListener('focusin', onFocus)
      window.clearTimeout(t)
      sheet.style.bottom = ''
      sheet.style.maxHeight = ''
    }
  }, [ref])
}

// ---------- Hoja de formulario ----------

/** Hoja con título, campos y botón principal ancho (FormSheet de Components.kt). */
export function FormSheet({ title, onClose, primary = 'Guardar', onPrimary, primaryEnabled = true, onDelete, onSubmit, children }: {
  title: ReactNode
  onClose: () => void
  primary?: string | null
  onPrimary?: () => void
  primaryEnabled?: boolean
  onDelete?: (() => void) | null
  /** Qué hace la tecla "Ir" del teclado si no hay botón principal (p. ej. buscar). */
  onSubmit?: () => void
  children: ReactNode
}) {
  const ref = useRef<HTMLFormElement>(null)
  useKeyboardInset(ref)
  return (
    <Sheet
      title={title}
      onClose={onClose}
      action={onDelete ? <button type="button" className="sh-delete" onClick={onDelete}>Borrar</button> : undefined}
    >
      <form
        ref={ref}
        className="sh-form"
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          if (onSubmit) onSubmit()
          else if (primary != null && primaryEnabled) onPrimary?.()
        }}
      >
        {children}
        {primary != null && (
          <div className="sh-foot">
            <Button type="submit" disabled={!primaryEnabled}>{primary}</Button>
          </div>
        )}
      </form>
    </Sheet>
  )
}

/** Confirmación de borrado ("¿Borrar este gasto?"), como ConfirmDeleteDialog. */
export function useConfirmDelete() {
  const [pending, setPending] = useState<{ what: string; body?: string; action: () => void } | null>(null)
  const dialog = pending
    ? <ConfirmDialog title={`¿Borrar ${pending.what}?`} body={pending.body} onConfirm={pending.action} onClose={() => setPending(null)} />
    : null
  return { ask: (what: string, action: () => void, body?: string) => setPending({ what, action, body }), dialog }
}

// ---------- Campos ----------

export function FieldLabel({ children }: { children: ReactNode }) {
  return <div className="sh-label">{children}</div>
}

/** Opción de formulario: verde si está elegida, gris si no. */
export function Choice({ text, selected, onClick, icon }: { text: string; selected: boolean; onClick: () => void; icon?: string }) {
  return (
    <button type="button" className={`sh-choice ${selected ? 'on' : ''}`} aria-pressed={selected} onClick={onClick}>
      {icon && <Icon name={icon} size={16} />}
      <span>{text}</span>
    </button>
  )
}
export function Chips({ children }: { children: ReactNode }) { return <div className="sh-chips">{children}</div> }

/** Elegir una cuenta (o ninguna). */
export function AccountChoices({ accounts, value, onChange, none }: { accounts: Account[]; value: number | null; onChange: (id: number | null) => void; none?: string }) {
  return (
    <Chips>
      {none != null && <Choice text={none} selected={value == null} onClick={() => onChange(null)} />}
      {accounts.map((a) => <Choice key={a.id} text={a.name} selected={a.id === value} onClick={() => onChange(a.id)} icon={accountIcon(a.type)} />)}
    </Chips>
  )
}

export function CategoryChoices({ categories, value, onChange }: { categories: Category[]; value: number; onChange: (id: number) => void }) {
  return (
    <Chips>
      {categories.map((c) => <Choice key={c.id} text={c.name} selected={c.id === value} onClick={() => onChange(c.id)} icon={categoryIcon(c)} />)}
    </Chips>
  )
}

/** Botones de tipo de gasto (TagPicker). */
export function TagPicker({ selected, onSelect }: { selected: SpendTag; onSelect: (t: SpendTag) => void }) {
  return (
    <Chips>
      {SPEND_TAG_VALUES.map((t) => {
        const on = t === selected
        return (
          <button key={t} type="button" aria-pressed={on} className={`sh-choice sh-tag ${on ? 'on' : ''}`} style={on ? { background: TAG_COLOR[t] } : undefined} onClick={() => onSelect(t)}>
            <Icon name={TAG_ICON[t]} size={16} style={{ color: on ? '#fff' : TAG_COLOR[t] }} />
            <span>{SPEND_TAG_INFO[t].label}</span>
          </button>
        )
      })}
    </Chips>
  )
}

/** Selector de dos opciones (Segmented). Botones type="button" para no enviar el formulario. */
export function Seg<T extends string | number | boolean>({ options, value, onChange, label }: { options: T[]; value: T; onChange: (v: T) => void; label: (v: T) => string }) {
  return (
    <div className="segmented" role="tablist">
      {options.map((o) => (
        <button key={String(o)} type="button" role="tab" aria-selected={o === value} className={o === value ? 'on' : ''} onClick={() => onChange(o)}>{label(o)}</button>
      ))}
    </div>
  )
}

/** Fila con interruptor sobre fondo gris (SwitchRow). */
export function SwitchBox({ title, sub, checked, onChange }: { title: string; sub?: string; checked: boolean; onChange: (v: boolean) => void }) {
  return <div className="sh-switch"><Switch checked={checked} onChange={onChange} label={title} sub={sub} /></div>
}

/** Texto de un solo número entero (día, año…). */
export function NumberField({ label, value, onChange, maxLength }: { label: string; value: string; onChange: (v: string) => void; maxLength: number }) {
  const id = useId()
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} inputMode="numeric" pattern="[0-9]*" value={value} onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, maxLength))} />
    </div>
  )
}

/** Fecha con el selector nativo del iPhone. */
export function DateField({ label, value, onChange, min, max }: { label: string; value: LocalDate; onChange: (d: LocalDate) => void; min?: LocalDate; max?: LocalDate }) {
  const id = useId()
  return (
    <div className="field sh-date">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="date"
        value={isoDate(value)}
        min={min ? isoDate(min) : undefined}
        max={max ? isoDate(max) : undefined}
        onChange={(e) => { const d = parseIsoDate(e.target.value); if (d) onChange(d) }}
      />
    </div>
  )
}

export function Spinner({ small }: { small?: boolean }) { return <span className={`sh-spinner ${small ? 'sm' : ''}`} role="status" aria-label="Cargando" /> }

export { colorFor, TAG_COLOR, TAG_ICON, MerchantLogo, TagCircle, categoryIcon }
