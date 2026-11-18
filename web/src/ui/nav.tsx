// Navegación: pestañas, pantallas superpuestas (Overlay) y hojas (Editing), como MainScreen.kt.
// El botón/gesto "atrás" de iPhone cierra primero la hoja y luego la pantalla superpuesta.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { Account, CryptoHolding, Expense, Income, IncomeSource, RecurringBill, StockHolding } from '../data/models'
import type { YearMonth } from '../data/dates'

export type Tab = 'HOME' | 'WEALTH' | 'BUDGET' | 'INVEST' | 'PROFILE'
export type InvestKind = 'CRYPTO' | 'STOCKS'

/** Pantallas que se abren por encima de las pestañas (con sus parámetros). */
export type OverlayRoute =
  | { name: 'settings' }
  | { name: 'future' }
  | { name: 'account'; accountId: number }
  | { name: 'holding'; key: string /* "c:<coinId>" o "s:<ticker>" */ }
  | { name: 'insights' }
  | { name: 'chat' }
  | { name: 'simulator' }
  | { name: 'purchase' }
  | { name: 'age' }
  | { name: 'wrapped' }
  | { name: 'news' }
  | { name: 'onboarding' }
  | { name: 'friends' }

/** Hojas (formularios) — mismos casos que Editing en Sheets.kt. */
export type SheetRoute =
  | { name: 'chooser' }
  | { name: 'expense'; expense?: Partial<Expense> }
  | { name: 'account'; account?: Partial<Account> }
  | { name: 'coinSearch' }
  | { name: 'crypto'; holding: Partial<CryptoHolding> }
  | { name: 'stock'; holding?: Partial<StockHolding> }
  | { name: 'monthlyIncome' }
  | { name: 'categories' }
  | { name: 'incomeSource'; source?: Partial<IncomeSource> }
  | { name: 'income'; income?: Partial<Income> }
  | { name: 'bill'; bill?: Partial<RecurringBill> }
  | { name: 'transfer'; fromAccountId?: number }
  | { name: 'balance'; accountId: number }
  | { name: 'quickEntry'; text?: string }
  | { name: 'split'; expenseId: number }
  | { name: 'bankPlan' }
  | { name: 'plan' }
  | { name: 'goal'; index?: number }
  | { name: 'ageForm' }
  | { name: 'profileForm' }
  | { name: 'convertTransfer'; expenseId: number }
  | { name: 'tagDetail'; tag: string; month?: YearMonth }
  | { name: 'tagLimit'; tag: string }
  | { name: 'debt'; debtId?: number; person?: string; total?: number; note?: string; expenseId?: number; epochDay?: number }
  | { name: 'tricount'; person: string }
  | { name: 'expectedIncome'; sourceId: number; month: YearMonth }
  | { name: 'pastePayment' }

interface Nav {
  tab: Tab
  setTab: (t: Tab) => void
  investKind: InvestKind
  openInvest: (k: InvestKind) => void
  overlays: OverlayRoute[]
  /** Abre una pantalla encima de la actual. */
  open: (r: OverlayRoute) => void
  /** Cierra la pantalla de arriba. */
  back: () => void
  /** Sustituye la pantalla de arriba (p. ej. del simulador vuelve al análisis). */
  replace: (r: OverlayRoute) => void
  closeAllOverlays: () => void
  sheet: SheetRoute | null
  /** Abre un formulario (cierra el que hubiera). */
  edit: (r: SheetRoute) => void
  closeSheet: () => void
}

const Ctx = createContext<Nav | null>(null)

export function NavProvider({ children }: { children: ReactNode }) {
  const [tab, setTabState] = useState<Tab>(() => (sessionStorageGet('tab') as Tab) || 'HOME')
  const [investKind, setInvestKind] = useState<InvestKind>('CRYPTO')
  const [overlays, setOverlays] = useState<OverlayRoute[]>([])
  const [sheet, setSheet] = useState<SheetRoute | null>(null)
  // Pila síncrona de capas (pantallas + hoja). Cada capa tiene una entrada en el historial, así el gesto
  // "atrás" de iOS la cierra. `pending` cuenta los history.back() pedidos y aún no recibidos, para que dos
  // cierres seguidos (p. ej. borrar una cuenta desde su hoja) no cierren una capa de más: la capa se quita
  // al momento y el popstate que llega después solo se descuenta.
  const stack = useRef<{ overlays: OverlayRoute[]; sheet: SheetRoute | null }>({ overlays: [], sheet: null })
  const pending = useRef(0)
  const commit = () => { setOverlays(stack.current.overlays); setSheet(stack.current.sheet) }
  const layers = () => stack.current.overlays.length + (stack.current.sheet ? 1 : 0)
  const popTop = () => {
    const s = stack.current
    stack.current = s.sheet ? { ...s, sheet: null } : { ...s, overlays: s.overlays.slice(0, -1) }
  }
  /** Cierra ya la capa de arriba y retira su entrada del historial. */
  const requestPop = () => {
    if (layers() === 0) return
    popTop()
    commit()
    pending.current++
    history.back()
  }

  useEffect(() => {
    // Un popstate que pedimos nosotros ya está aplicado; uno del usuario (gesto atrás) cierra la capa de arriba.
    const onPop = () => {
      if (pending.current > 0) { pending.current--; return }
      if (layers() === 0) return
      popTop()
      commit()
    }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const setTab = useCallback((t: Tab) => {
    setTabState(t)
    sessionStorageSet('tab', t)
    window.scrollTo({ top: 0 })
  }, [])
  // Si había una hoja abierta, su entrada del historial pasa a ser la de la nueva pantalla.
  const open = useCallback((r: OverlayRoute) => {
    const s = stack.current
    if (!s.sheet) history.pushState({ av: true }, '')
    stack.current = { overlays: [...s.overlays, r], sheet: null }
    commit()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const back = useCallback(() => requestPop(), []) // eslint-disable-line react-hooks/exhaustive-deps
  const replace = useCallback((r: OverlayRoute) => {
    stack.current = { ...stack.current, overlays: [...stack.current.overlays.slice(0, -1), r] }
    commit()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const closeAllOverlays = useCallback(() => {
    while (layers() > 0) requestPop()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const edit = useCallback((r: SheetRoute) => {
    if (!stack.current.sheet) history.pushState({ av: true }, '')
    stack.current = { ...stack.current, sheet: r }
    commit()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const closeSheet = useCallback(() => {
    if (stack.current.sheet) requestPop()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const openInvest = useCallback((k: InvestKind) => { setInvestKind(k); setTab('INVEST') }, [setTab])

  const value = useMemo<Nav>(() => ({ tab, setTab, investKind, openInvest, overlays, open, back, replace, closeAllOverlays, sheet, edit, closeSheet }),
    [tab, setTab, investKind, openInvest, overlays, open, back, replace, closeAllOverlays, sheet, edit, closeSheet])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useNav(): Nav {
  const n = useContext(Ctx)
  if (!n) throw new Error('useNav fuera de NavProvider')
  return n
}

function sessionStorageGet(k: string) { try { return sessionStorage.getItem('av:' + k) } catch { return null } }
function sessionStorageSet(k: string, v: string) { try { sessionStorage.setItem('av:' + k, v) } catch { /* nada */ } }
