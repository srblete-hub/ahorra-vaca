// Constructores con los mismos valores por defecto que los data class de Kotlin, para las pruebas.
import type {
  Account, AccountType, Category, CryptoHolding, Expense, Income, IncomeSource, RecurringBill, Split, Transfer,
} from '../../data/models'
import type { LocalDate, YearMonth } from '../../data/dates'
import { toEpochDay } from '../../data/dates'
import { fixedRoot } from '../kt'

export const fmt = (v: number) => `${fixedRoot(v, 2)} €`
export const ed = (d: LocalDate) => toEpochDay(d)
export const atDay = (m: YearMonth, d: number): LocalDate => ({ year: m.year, month: m.month, day: d })

export function acc(id: number, name: string, type: AccountType, balance: number, o: Partial<Account> = {}): Account {
  return { id, name, type, balance, note: '', interestRate: 0, autoInterest: false, interestFromMonth: 0, ...o }
}
export function cat(id: number, name: string, percent: number, o: Partial<Category> = {}): Category {
  return { id, name, percent, isSavings: false, locked: false, ...o }
}
export function src(id: number, name: string, variable: boolean, amount: number, dayFrom: number, dayTo: number, o: Partial<IncomeSource> = {}): IncomeSource {
  return { id, name, variable, amount, dayFrom, dayTo, accountId: null, ...o }
}
export function inc(id: number, sourceId: number | null, amount: number, note: string, epochDay: number, accountId: number | null = null): Income {
  return { id, sourceId, amount, note, epochDay, accountId, createdAt: 0 }
}
export function exp(id: number, categoryId: number, amount: number, note: string, epochDay: number, o: Partial<Expense> = {}): Expense {
  return { id, categoryId, amount, note, epochDay, accountId: null, fromCard: false, sourceApp: '', createdAt: 0, billId: null, tag: '', rawText: '', ...o }
}
export function bill(id: number, name: string, amount: number, day: number, o: Partial<RecurringBill> = {}): RecurringBill {
  return { id, name, amount, day, yearly: false, month: 1, categoryId: 1, accountId: null, ...o }
}
export function transfer(id: number, fromAccountId: number, toAccountId: number, amount: number, epochDay: number, note = ''): Transfer {
  return { id, fromAccountId, toAccountId, amount, note, epochDay, createdAt: 0 }
}
export function split(expenseId: number, people: number, settled = 0): Split {
  return { expenseId, people, settled, note: '' }
}
export function crypto(id: number, coinId: string, symbol: string, name: string, amount: number, avgBuyPrice: number, price = 0): CryptoHolding {
  return { id, coinId, symbol, name, amount, avgBuyPrice, price, updatedAt: 0 }
}
