// Qué componente pinta cada pestaña, pantalla superpuesta y hoja.
// Cada módulo se carga al usarse (lazy) para que la app abra rápido en el móvil.
import { lazy, type ComponentType, type LazyExoticComponent } from 'react'
import type { OverlayRoute, SheetRoute, Tab } from './nav'

type Named<M> = { [K in keyof M]: M[K] extends ComponentType<any> ? K : never }[keyof M]
function pick<M, K extends Named<M>>(load: () => Promise<M>, key: K): LazyExoticComponent<ComponentType<any>> {
  return lazy(() => load().then((m) => ({ default: m[key] as unknown as ComponentType<any> })))
}

const home = () => import('./screens/home')
const wealth = () => import('./screens/wealth')
const insights = () => import('./screens/insights')
const budget = () => import('./screens/budget')
const invest = () => import('./screens/invest')
const profile = () => import('./screens/profile')
const sheets = () => import('./sheets')

/** Pestañas: sin props (usan useNav / useDB / usePlanning). */
export const TABS: Record<Tab, LazyExoticComponent<ComponentType<any>>> = {
  HOME: pick(home, 'HomeScreen'),
  WEALTH: pick(wealth, 'WealthScreen'),
  BUDGET: pick(budget, 'BudgetScreen'),
  INVEST: pick(invest, 'InvestmentsScreen'),
  PROFILE: pick(profile, 'ProfileScreen'),
}

/** Pantallas superpuestas: reciben `{ route }` y se cierran con useNav().back(). */
export const OVERLAYS: Record<OverlayRoute['name'], LazyExoticComponent<ComponentType<any>>> = {
  settings: pick(profile, 'SettingsScreen'),
  wrapped: pick(profile, 'WrappedScreen'),
  future: pick(wealth, 'FutureScreen'),
  account: pick(wealth, 'AccountScreen'),
  age: pick(wealth, 'FinancialAgeScreen'),
  insights: pick(insights, 'InsightsScreen'),
  simulator: pick(insights, 'SimulatorScreen'),
  onboarding: pick(insights, 'OnboardingScreen'),
  purchase: pick(home, 'PurchaseScreen'),
  chat: pick(home, 'ChatScreen'),
  holding: pick(invest, 'HoldingDetailScreen'),
  news: pick(invest, 'NewsScreen'),
  friends: pick(budget, 'FriendsScreen'),
}

/** Hojas: reciben `{ route, onClose }`. Todas viven en ui/sheets/ y se exportan desde ui/sheets/index.tsx. */
export const SHEETS: Record<SheetRoute['name'], LazyExoticComponent<ComponentType<any>>> = {
  chooser: pick(sheets, 'AddChooserSheet'),
  expense: pick(sheets, 'ExpenseSheet'),
  account: pick(sheets, 'AccountSheet'),
  coinSearch: pick(sheets, 'CoinSearchSheet'),
  crypto: pick(sheets, 'CryptoSheet'),
  stock: pick(sheets, 'StockSheet'),
  monthlyIncome: pick(sheets, 'MonthlyIncomeSheet'),
  categories: pick(sheets, 'CategoriesSheet'),
  incomeSource: pick(sheets, 'IncomeSourceSheet'),
  income: pick(sheets, 'IncomeSheet'),
  bill: pick(sheets, 'BillSheet'),
  transfer: pick(sheets, 'TransferSheet'),
  balance: pick(sheets, 'BalanceSheet'),
  quickEntry: pick(sheets, 'QuickEntrySheet'),
  split: pick(sheets, 'SplitSheet'),
  bankPlan: pick(sheets, 'BankPlanSheet'),
  plan: pick(sheets, 'PlanSheet'),
  goal: pick(sheets, 'GoalSheet'),
  ageForm: pick(sheets, 'AgeSheet'),
  profileForm: pick(sheets, 'SavingProfileSheet'),
  convertTransfer: pick(sheets, 'ConvertTransferSheet'),
  tagDetail: pick(sheets, 'TagDetailSheet'),
  tagLimit: pick(sheets, 'TagLimitSheet'),
  debt: pick(sheets, 'DebtSheet'),
  tricount: pick(sheets, 'TricountSheet'),
  expectedIncome: pick(sheets, 'ExpectedIncomeSheet'),
  pastePayment: pick(sheets, 'PastePaymentSheet'),
}
