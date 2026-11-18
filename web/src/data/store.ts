// Almacén de la app: equivale a la base de datos Room + Prefs de Android.
// Todo se guarda en el móvil (localStorage), nada sale del dispositivo salvo las consultas de precios y noticias.
import { useSyncExternalStore } from 'react'
import type {
  Account, Category, CryptoHolding, Expense, Income, IncomeSource, NetWorthSnapshot, RecurringBill, Settings, Split,
  StockHolding, Transfer,
} from './models'
import { DEFAULT_SETTINGS } from './models'
import { todayEpoch } from './dates'

export interface DB {
  accounts: Account[]
  categories: Category[]
  incomeSources: IncomeSource[]
  incomes: Income[]
  expenses: Expense[]
  splits: Split[]
  bills: RecurringBill[]
  transfers: Transfer[]
  snapshots: NetWorthSnapshot[]
  crypto: CryptoHolding[]
  stocks: StockHolding[]
  settings: Settings
  nextId: number
}

const KEY = 'ahorra-vaca:v1'

/** Reparto 50/30/20 de partida; se puede cambiar desde Movimientos. */
function seed(): DB {
  return {
    accounts: [],
    categories: [
      { id: 1, name: 'Necesidades', percent: 50, isSavings: false, locked: false },
      { id: 2, name: 'Ocio y caprichos', percent: 30, isSavings: false, locked: false },
      { id: 3, name: 'Ahorro', percent: 20, isSavings: true, locked: false },
    ],
    incomeSources: [], incomes: [], expenses: [], splits: [], bills: [], transfers: [], snapshots: [], crypto: [], stocks: [],
    settings: { ...DEFAULT_SETTINGS },
    nextId: 100,
  }
}

function load(): DB {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return seed()
    const parsed = JSON.parse(raw) as Partial<DB>
    const base = seed()
    return { ...base, ...parsed, settings: { ...DEFAULT_SETTINGS, ...(parsed.settings ?? {}) } }
  } catch {
    return seed()
  }
}

let state: DB = load()
const listeners = new Set<() => void>()
let saveTimer: number | undefined

function persist() {
  window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    try { localStorage.setItem(KEY, JSON.stringify(state)) } catch { /* sin espacio o modo privado */ }
  }, 50)
}

export function getState(): DB { return state }
export function setState(next: DB) {
  state = next
  persist()
  listeners.forEach((l) => l())
}
export function update(fn: (s: DB) => DB) { setState(fn(state)) }
export function subscribe(l: () => void) { listeners.add(l); return () => listeners.delete(l) }

/** Lee una parte del estado y vuelve a pintar cuando cambia. El selector debe devolver algo estable (no objetos nuevos). */
export function useDB<T>(selector: (s: DB) => T): T {
  return useSyncExternalStore(subscribe, () => selector(state))
}
export function useSettings(): Settings { return useDB((s) => s.settings) }

/** Exportar / importar copia de seguridad. */
export function exportJson(): string { return JSON.stringify(state, null, 2) }
export function importJson(text: string) {
  const parsed = JSON.parse(text) as Partial<DB>
  if (!parsed || !Array.isArray(parsed.accounts)) throw new Error('No es una copia de Ahorra, vaca')
  setState({ ...seed(), ...parsed, settings: { ...DEFAULT_SETTINGS, ...(parsed.settings ?? {}) } })
}
export function resetAll() { setState(seed()) }

// ---------- Ayudas internas ----------

function newId(s: DB): [number, DB] { return [s.nextId, { ...s, nextId: s.nextId + 1 }] }
function upsert<T>(list: T[], item: T, same: (a: T) => boolean): T[] {
  return list.some(same) ? list.map((x) => (same(x) ? item : x)) : [...list, item]
}
function adjust(accounts: Account[], accountId: number | null | undefined, delta: number): Account[] {
  if (accountId == null || !delta) return accounts
  return accounts.map((a) => (a.id === accountId ? { ...a, balance: Math.round((a.balance + delta) * 100) / 100 } : a))
}

// ---------- Mensajes cortos (snackbar) ----------
type Toast = { id: number; text: string }
let toasts: Toast[] = []
const toastListeners = new Set<() => void>()
export function toast(text: string) {
  const t = { id: Date.now() + Math.random(), text }
  toasts = [...toasts, t]
  toastListeners.forEach((l) => l())
  window.setTimeout(() => { toasts = toasts.filter((x) => x.id !== t.id); toastListeners.forEach((l) => l()) }, 3200)
}
export function useToasts(): Toast[] {
  return useSyncExternalStore((l) => { toastListeners.add(l); return () => toastListeners.delete(l) }, () => toasts)
}

// ---------- Acciones (mismas reglas que FinanceDao / FinanceViewModel) ----------

type New<T extends { id: number }> = Omit<T, 'id'> & { id?: number }

export const db = {
  // Ajustes
  setSettings(patch: Partial<Settings>) { update((s) => ({ ...s, settings: { ...s.settings, ...patch } })) },

  // Cuentas
  saveAccount(a: New<Account>): number {
    let id = a.id ?? 0
    update((s0) => {
      let s = s0
      if (!id) [id, s] = newId(s)
      const start = a.autoInterest && a.interestRate > 0 && !a.interestFromMonth
      const now = new Date()
      const acc: Account = { ...(a as Account), id, interestFromMonth: start ? now.getFullYear() * 12 + now.getMonth() : a.interestFromMonth }
      return { ...s, accounts: upsert(s.accounts, acc, (x) => x.id === id) }
    })
    return id
  },
  deleteAccount(id: number) { update((s) => ({ ...s, accounts: s.accounts.filter((a) => a.id !== id) })) },
  /** Cambia el saldo a mano (para cuadrarlo con lo que dice el banco). */
  setBalance(id: number, balance: number) {
    update((s) => ({ ...s, accounts: s.accounts.map((a) => (a.id === id ? { ...a, balance } : a)) }))
  },

  // Categorías (reparto)
  /** Guarda el reparto completo; las categorías que ya no están se borran junto con sus gastos. */
  saveCategories(edited: New<Category>[]) {
    update((s0) => {
      let s = s0
      const list: Category[] = edited.map((c) => {
        if (c.id) return c as Category
        let id: number;[id, s] = newId(s)
        return { ...(c as Category), id }
      })
      const kept = new Set(list.map((c) => c.id))
      const removed = s.categories.filter((c) => !kept.has(c.id)).map((c) => c.id)
      let accounts = s.accounts
      const expenses = s.expenses.filter((e) => {
        if (removed.includes(e.categoryId)) { accounts = adjust(accounts, e.accountId, e.amount); return false }
        return true
      })
      return { ...s, categories: list, expenses, accounts }
    })
  },

  // Gastos
  /** Guarda el gasto y mueve el saldo: devuelve el importe antiguo a su cuenta y descuenta el nuevo. */
  saveExpense(e: Partial<Expense> & Pick<Expense, 'categoryId' | 'amount' | 'epochDay'>): number {
    let id = e.id ?? 0
    update((s0) => {
      let s = s0
      const old = id ? s.expenses.find((x) => x.id === id) : undefined
      if (!id) [id, s] = newId(s)
      const exp: Expense = {
        note: '', accountId: null, fromCard: false, sourceApp: '', createdAt: Date.now(), billId: null, tag: '', rawText: '',
        ...e, id,
      } as Expense
      let accounts = s.accounts
      if (old) accounts = adjust(accounts, old.accountId, old.amount)
      accounts = adjust(accounts, exp.accountId, -exp.amount)
      return { ...s, accounts, expenses: upsert(s.expenses, exp, (x) => x.id === id) }
    })
    return id
  },
  /** Borra el gasto y devuelve el dinero a la cuenta de la que salió. */
  deleteExpense(id: number) {
    update((s) => {
      const e = s.expenses.find((x) => x.id === id)
      if (!e) return s
      return {
        ...s,
        accounts: adjust(s.accounts, e.accountId, e.amount),
        expenses: s.expenses.filter((x) => x.id !== id),
        splits: s.splits.filter((x) => x.expenseId !== id),
        settings: { ...s.settings, refundIds: s.settings.refundIds.filter((r) => r !== id) },
      }
    })
  },

  // Ingresos
  saveIncomeSource(src: New<IncomeSource>): number {
    let id = src.id ?? 0
    update((s0) => {
      let s = s0
      if (!id) [id, s] = newId(s)
      return { ...s, incomeSources: upsert(s.incomeSources, { ...(src as IncomeSource), id }, (x) => x.id === id) }
    })
    return id
  },
  deleteIncomeSource(id: number) { update((s) => ({ ...s, incomeSources: s.incomeSources.filter((x) => x.id !== id) })) },
  /** Guarda el cobro y lo suma a su cuenta (devolviendo antes el importe antiguo si se edita). */
  saveIncome(inc: Partial<Income> & Pick<Income, 'amount' | 'epochDay'>): number {
    let id = inc.id ?? 0
    update((s0) => {
      let s = s0
      const old = id ? s.incomes.find((x) => x.id === id) : undefined
      if (!id) [id, s] = newId(s)
      const item: Income = { sourceId: null, note: '', accountId: null, createdAt: Date.now(), ...inc, id } as Income
      let accounts = s.accounts
      if (old) accounts = adjust(accounts, old.accountId, -old.amount)
      accounts = adjust(accounts, item.accountId, item.amount)
      return { ...s, accounts, incomes: upsert(s.incomes, item, (x) => x.id === id) }
    })
    return id
  },
  /** Apunta un cobro sin tocar el saldo (p. ej. la nómina ya incluida en el saldo puesto a mano). */
  upsertIncomeRaw(inc: Partial<Income> & Pick<Income, 'amount' | 'epochDay'>) {
    update((s0) => {
      let s = s0
      let id = inc.id ?? 0
      if (!id) [id, s] = newId(s)
      const item: Income = { sourceId: null, note: '', accountId: null, createdAt: Date.now(), ...inc, id } as Income
      return { ...s, incomes: upsert(s.incomes, item, (x) => x.id === id) }
    })
  },
  deleteIncome(id: number) {
    update((s) => {
      const i = s.incomes.find((x) => x.id === id)
      if (!i) return s
      return { ...s, accounts: adjust(s.accounts, i.accountId, -i.amount), incomes: s.incomes.filter((x) => x.id !== id) }
    })
  },

  // Gastos fijos
  saveBill(b: New<RecurringBill>): number {
    let id = b.id ?? 0
    update((s0) => {
      let s = s0
      if (!id) [id, s] = newId(s)
      return { ...s, bills: upsert(s.bills, { ...(b as RecurringBill), id }, (x) => x.id === id) }
    })
    return id
  },
  deleteBill(id: number) { update((s) => ({ ...s, bills: s.bills.filter((x) => x.id !== id) })) },
  /** Apunta el pago de este mes de un gasto fijo (y lo descuenta de su cuenta). */
  payBill(bill: RecurringBill) {
    db.saveExpense({ categoryId: bill.categoryId, amount: bill.amount, note: bill.name, epochDay: todayEpoch(), accountId: bill.accountId, billId: bill.id })
  },

  // Transferencias
  /** Mueve el dinero: resta de una cuenta y suma a la otra. */
  saveTransfer(t: New<Transfer>): number {
    let id = t.id ?? 0
    update((s0) => {
      let s = s0
      const old = id ? s.transfers.find((x) => x.id === id) : undefined
      if (!id) [id, s] = newId(s)
      const item = { note: '', createdAt: Date.now(), ...(t as Partial<Transfer>), id } as Transfer
      let accounts = s.accounts
      if (old) { accounts = adjust(accounts, old.fromAccountId, old.amount); accounts = adjust(accounts, old.toAccountId, -old.amount) }
      accounts = adjust(accounts, item.fromAccountId, -item.amount)
      accounts = adjust(accounts, item.toAccountId, item.amount)
      return { ...s, accounts, transfers: upsert(s.transfers, item, (x) => x.id === id) }
    })
    return id
  },
  deleteTransfer(id: number) {
    update((s) => {
      const t = s.transfers.find((x) => x.id === id)
      if (!t) return s
      let accounts = adjust(s.accounts, t.fromAccountId, t.amount)
      accounts = adjust(accounts, t.toAccountId, -t.amount)
      return { ...s, accounts, transfers: s.transfers.filter((x) => x.id !== id) }
    })
  },
  /** Un gasto que era un traspaso: se quita el gasto (devolviendo el dinero) y se apunta la transferencia. */
  convertToTransfer(expense: Expense, toAccountId: number) {
    if (expense.accountId == null) return
    db.deleteExpense(expense.id)
    db.saveTransfer({ fromAccountId: expense.accountId, toAccountId, amount: expense.amount, note: expense.note || 'Traspaso', epochDay: expense.epochDay, createdAt: Date.now() })
    toast('Convertido en transferencia')
  },

  // Repartos
  saveSplit(sp: Split) { update((s) => ({ ...s, splits: upsert(s.splits, sp, (x) => x.expenseId === sp.expenseId) })) },
  deleteSplit(expenseId: number) { update((s) => ({ ...s, splits: s.splits.filter((x) => x.expenseId !== expenseId) })) },
  setRefund(expenseId: number, pending: boolean) {
    update((s) => {
      const set = new Set(s.settings.refundIds)
      if (pending) set.add(expenseId); else set.delete(expenseId)
      return { ...s, settings: { ...s.settings, refundIds: [...set] } }
    })
  },

  // Patrimonio diario
  recordNetWorth(total: number) {
    const day = todayEpoch()
    update((s) => {
      const rounded = Math.round(total * 100) / 100
      const cur = s.snapshots.find((x) => x.epochDay === day)
      if (cur && cur.total === rounded) return s
      const list = upsert(s.snapshots, { epochDay: day, total: rounded }, (x) => x.epochDay === day).sort((a, b) => a.epochDay - b.epochDay)
      return { ...s, snapshots: list }
    })
  },

  // Inversiones
  saveCrypto(h: New<CryptoHolding>): number {
    let id = h.id ?? 0
    update((s0) => {
      let s = s0
      if (!id) [id, s] = newId(s)
      return { ...s, crypto: upsert(s.crypto, { price: 0, updatedAt: 0, ...(h as Partial<CryptoHolding>), id } as CryptoHolding, (x) => x.id === id) }
    })
    return id
  },
  deleteCrypto(id: number) { update((s) => ({ ...s, crypto: s.crypto.filter((x) => x.id !== id) })) },
  saveStock(h: New<StockHolding>): number {
    let id = h.id ?? 0
    update((s0) => {
      let s = s0
      if (!id) [id, s] = newId(s)
      return { ...s, stocks: upsert(s.stocks, { price: 0, updatedAt: 0, ...(h as Partial<StockHolding>), id } as StockHolding, (x) => x.id === id) }
    })
    return id
  },
  deleteStock(id: number) { update((s) => ({ ...s, stocks: s.stocks.filter((x) => x.id !== id) })) },
  setCryptoPrices(prices: Record<string, number>) {
    const now = Date.now()
    update((s) => ({ ...s, crypto: s.crypto.map((h) => (prices[h.coinId] != null ? { ...h, price: prices[h.coinId], updatedAt: now } : h)) }))
  },
  setStockPrice(ticker: string, price: number) {
    const now = Date.now()
    update((s) => ({ ...s, stocks: s.stocks.map((h) => (h.ticker === ticker ? { ...h, price, updatedAt: now } : h)) }))
  },
  setDayChanges(map: Record<string, number>) {
    update((s) => ({ ...s, settings: { ...s.settings, dayChanges: { ...s.settings.dayChanges, ...map } } }))
  },
}

/** Tareas al abrir la app (intereses del mes y nómina ya cobrada), como el init del ViewModel. */
export function startupTasks() {
  applyMonthlyInterest()
  markPaidSalary()
}

/** Interés de un mes con TAE `rate` (%) sobre `balance`. */
export function monthlyInterest(balance: number, rate: number): number {
  if (balance <= 0 || rate <= 0) return 0
  const monthly = Math.pow(1 + rate / 100, 1 / 12) - 1
  return Math.round(balance * monthly * 100) / 100
}

function applyMonthlyInterest() {
  const n = new Date()
  const current = n.getFullYear() * 12 + n.getMonth()
  for (const account of getState().accounts.filter((a) => a.autoInterest && a.interestRate > 0)) {
    let balance = account.balance
    let last = account.interestFromMonth
    if (last === 0) { db.saveAccount({ ...account, interestFromMonth: current }); continue }
    while (last < current) {
      last++
      const interest = monthlyInterest(balance, account.interestRate)
      if (interest <= 0) continue
      const y = Math.floor(last / 12), m = last % 12
      db.upsertIncomeRaw({ sourceId: null, amount: interest, note: `Intereses · ${account.name}`, epochDay: Math.round(Date.UTC(y, m, 1) / 86_400_000), accountId: account.id })
      balance += interest
    }
    update((s) => ({ ...s, accounts: s.accounts.map((a) => (a.id === account.id ? { ...a, balance: Math.round(balance * 100) / 100, interestFromMonth: last } : a)) }))
  }
}

/** Nóminas fijas cuyo día ya ha pasado: se apuntan como cobradas sin sumar al saldo (una vez al mes). */
function markPaidSalary() {
  const n = new Date()
  const idx = n.getFullYear() * 12 + n.getMonth()
  const s = getState()
  if (s.settings.autoPaidMonth === idx) return
  for (const src of s.incomeSources.filter((x) => !x.variable && x.dayFrom <= 5 && x.dayFrom <= n.getDate())) {
    const got = s.incomes.some((i) => {
      if (i.sourceId !== src.id) return false
      const d = new Date(i.epochDay * 86_400_000)
      return d.getUTCFullYear() === n.getFullYear() && d.getUTCMonth() === n.getMonth()
    })
    if (!got) db.upsertIncomeRaw({ sourceId: src.id, amount: src.amount, note: src.name, epochDay: Math.round(Date.UTC(n.getFullYear(), n.getMonth(), src.dayFrom) / 86_400_000), accountId: null })
  }
  db.setSettings({ autoPaidMonth: idx })
}
