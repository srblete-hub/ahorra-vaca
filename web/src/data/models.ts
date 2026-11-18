// Modelos de datos: copia de data/Models.kt de la app Android.
// Los ids son números; las fechas, "epochDay" (días desde 1970-01-01 en hora local, ver dates.ts).

export type AccountType = 'BANK' | 'BANK_SAVINGS' | 'CASH' | 'HOME_SAVINGS' | 'WALLET' | 'FUND' | 'OTHER' | 'DEBT'

export const ACCOUNT_TYPES: Record<AccountType, { label: string; inBank: boolean }> = {
  BANK: { label: 'Cuenta bancaria', inBank: true },
  BANK_SAVINGS: { label: 'Ahorro en el banco', inBank: true },
  CASH: { label: 'Efectivo', inBank: false },
  HOME_SAVINGS: { label: 'Hucha / ahorro en casa', inBank: false },
  WALLET: { label: 'PayPal / monedero digital', inBank: false },
  FUND: { label: 'Fondo o plan de pensiones', inBank: false },
  OTHER: { label: 'Otro sitio', inBank: false },
  /** Lo que debes (préstamo, tarjeta…): el saldo es lo pendiente y resta del patrimonio. */
  DEBT: { label: 'Préstamo o deuda', inBank: false },
}
export const ACCOUNT_TYPE_ORDER: AccountType[] = ['BANK', 'BANK_SAVINGS', 'CASH', 'HOME_SAVINGS', 'WALLET', 'FUND', 'OTHER', 'DEBT']

export interface Account {
  id: number
  name: string
  type: AccountType
  balance: number
  note: string
  /** TAE en % (0 = no da intereses). */
  interestRate: number
  /** Cada mes se suman los intereses al saldo. */
  autoInterest: boolean
  /** Último mes ya abonado (año·12 + mes − 1). */
  interestFromMonth: number
}

/** Parte del ingreso mensual que se asigna a algo. Las de ahorro no cuentan como "gastable". */
export interface Category {
  id: number
  name: string
  percent: number
  isSavings: boolean
  locked: boolean
}

/** Algo que te paga cada mes: nómina (fija) o autónomo / YouTube (variable), entre los días dayFrom y dayTo. */
export interface IncomeSource {
  id: number
  name: string
  variable: boolean
  amount: number
  dayFrom: number
  dayTo: number
  accountId: number | null
}

/** Un cobro. Si tiene accountId, está sumado al saldo de esa cuenta. Sin sourceId: intereses o extras. */
export interface Income {
  id: number
  sourceId: number | null
  amount: number
  note: string
  epochDay: number
  accountId: number | null
  createdAt: number
}

/** Un pago. Si tiene accountId, su importe está descontado del saldo de esa cuenta. */
export interface Expense {
  id: number
  categoryId: number
  amount: number
  note: string
  epochDay: number
  accountId: number | null
  fromCard: boolean
  sourceApp: string
  createdAt: number
  /** Si es el pago de un gasto fijo, cuál. */
  billId: number | null
  /** Tipo de gasto (clave de SpendTag); vacío = se deduce de la descripción. */
  tag: string
  rawText: string
}

/** Un gasto repartido entre `people` personas (tú incluido); `settled` de los demás ya te han pagado. */
export interface Split {
  expenseId: number
  people: number
  settled: number
  note: string
}

/** Gasto fijo: mensual el día `day`, o anual el día `day` del mes `month` (1–12). */
export interface RecurringBill {
  id: number
  name: string
  amount: number
  day: number
  yearly: boolean
  month: number
  categoryId: number
  accountId: number | null
}

/** Dinero movido de una cuenta a otra (no es gasto ni ingreso). */
export interface Transfer {
  id: number
  fromAccountId: number
  toAccountId: number
  amount: number
  note: string
  epochDay: number
  createdAt: number
}

export interface NetWorthSnapshot {
  epochDay: number
  total: number
}

/** coinId es el id de CoinGecko (p. ej. "bitcoin"); los precios van en la moneda de la app. */
export interface CryptoHolding {
  id: number
  coinId: string
  symbol: string
  name: string
  amount: number
  avgBuyPrice: number
  price: number
  updatedAt: number
}

/** ticker con el formato de Yahoo: AAPL, SAN.MC, VWCE.DE… */
export interface StockHolding {
  id: number
  ticker: string
  name: string
  shares: number
  avgBuyPrice: number
  price: number
  updatedAt: number
}

export const NONE = -1

/** Ajustes (equivale a Settings de Prefs.kt). Los campos "Raw" mantienen el mismo formato de texto que en Android. */
export interface Settings {
  userName: string
  currency: string
  monthlyIncome: number
  hideAmounts: boolean
  onboardingDone: boolean
  handle: string
  /** Foto de perfil como data URL (vacío = sin foto). */
  avatar: string
  achievements: Record<string, number>
  shareMoneyAchievements: boolean
  planReserve: number
  planName: string
  bankMainId: number
  bankTargetId: number
  bankSavePct: number
  bankBuffer: number
  autoPaidMonth: number
  birthYear: number
  /** Límites por tipo de gasto ("TRANSPORT:30:W;…"); null = nunca tocado. */
  tagLimitsRaw: string | null
  /** Objetivos en orden de prioridad: "nombre|objetivo|inicio|fecha" por línea. */
  goalsRaw: string
  refundIds: number[]
  dayChanges: Record<string, number>
  /** 0 relajado … 3 muy ahorrador; −1 = aún no lo has dicho. */
  savingStyle: number
  lifeGoals: string[]
  limitSnapshot: string | null
  circleHistory: string
  ignoredPayments: string[]
  /** Importe confirmado de un ingreso variable en un mes: "idFuente@2026-11" → €. */
  expectedIncome: Record<string, number>
  debtsRaw: string
  tricountRaw: string
  pepeTipHiddenWeek: string
  savingsLocked: boolean
  weeklyTip: boolean
  reviewedSubs: string[]
  seenNews: string[]
  recentTickers: string[]
  /** Hash del PIN (SHA-256 con sal), vacío = sin PIN. */
  pinHash: string
  pinSalt: string
  /** Tema: 'system' | 'light' | 'dark'. */
  theme: 'system' | 'light' | 'dark'
}

export const DEFAULT_SETTINGS: Settings = {
  userName: '',
  currency: 'EUR',
  monthlyIncome: 0,
  hideAmounts: false,
  onboardingDone: false,
  handle: '',
  avatar: '',
  achievements: {},
  shareMoneyAchievements: false,
  planReserve: 0,
  planName: '',
  bankMainId: NONE,
  bankTargetId: NONE,
  bankSavePct: 20,
  bankBuffer: 100,
  autoPaidMonth: 0,
  birthYear: 0,
  tagLimitsRaw: null,
  goalsRaw: '',
  refundIds: [],
  dayChanges: {},
  savingStyle: -1,
  lifeGoals: [],
  limitSnapshot: null,
  circleHistory: '',
  ignoredPayments: [],
  expectedIncome: {},
  debtsRaw: '',
  tricountRaw: '',
  pepeTipHiddenWeek: '',
  savingsLocked: false,
  weeklyTip: true,
  reviewedSubs: [],
  seenNews: [],
  recentTickers: [],
  pinHash: '',
  pinSalt: '',
  theme: 'system',
}
