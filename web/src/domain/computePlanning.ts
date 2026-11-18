// Port de la planificación de FinanceViewModel.kt (Planning, computePlanning, monthIncome y lo que el init guarda).
// Se llama computePlanning.ts (y no planning.ts) porque en Windows/macOS chocaría con Planning.ts.
import { useMemo } from 'react'
import type { DB } from '../data/store'
import { useDB } from '../data/store'
import type { Settings } from '../data/models'
import { NONE } from '../data/models'
import type { LocalDate, YearMonth } from '../data/dates'
import { today as todayDate } from '../data/dates'
import { ep, fixed, grouped0, lenOfMonth, range, sumOf, ymBefore, ymEq, ymMinus, ymOfDate, ymOfDay, ymLen } from './kt'
import { IncomeMath, Wealth } from './FinanceMath'
import type { MonthIncome, Wealth as WealthT } from './FinanceMath'
import {
  Achievements, BankPlanning, Bills, Comparison, Forecasting, makeSafeToSpend, Spending,
} from './Planning'
import type { Achievement, BankPlan, Forecast, Level, MonthComparison, SafeToSpend } from './Planning'
import { Insights, makeFinanceData } from './Insights'
import type { FinanceData } from './Insights'
import { Budgeting, SavingProfile } from './Budget'
import type { SpendingBudget, TagLimits } from './Budget'
import { FinancialAging, GoalList } from './Goals'
import type { FinancialAge, Goal, GoalStatus } from './Goals'
import { LimitSnapshot } from './Alerts'
import { PulseCalculator } from './Pulse'
import { FinancialCircles } from './Circles'
import type { SpendTag } from './Spend'

/** Todo lo calculado para las pantallas (FinanceViewModel.Planning). */
export interface Planning {
  wealth: WealthT
  forecast: Forecast
  safe: SafeToSpend
  comparison: MonthComparison
  achievements: Achievement[]
  /** Todo cargado, para los análisis y el chat. */
  data: FinanceData
  /** Reparto del saldo de la cuenta principal (null si no hay cuenta del día a día). */
  bankPlan: BankPlan | null
  goal: GoalStatus | null
  /** Todos los objetivos por prioridad (el primero es `goal`). */
  goals: GoalStatus[]
  goalList: Goal[]
  /** Edad financiera (null sin año de nacimiento). */
  age: FinancialAge | null
  /** Rueda: cuánto puedes gastar en cada tipo de gasto al mes. */
  budget: SpendingBudget | null
  limits: TagLimits
  /** Puntos de los logros conseguidos (calculado). */
  points: number
  /** Nivel según los puntos (calculado). */
  level: Level
}

/** Campos de Settings de Android que la web no tiene (pagos con tarjeta automáticos). */
type SettingsExt = Settings & { cardAccountId?: number; cardPayments?: boolean }

/** Ingreso del mes elegido: lo fijo más la media de lo variable, corregido con lo ya cobrado (monthIncome del ViewModel). */
export function computeMonthIncome(db: DB, month: YearMonth): MonthIncome {
  if (db.incomeSources.length === 0) {
    // Sin fuentes definidas, el ingreso único de antes.
    return { total: db.settings.monthlyIncome, received: 0, sources: [], extra: 0 }
  }
  IncomeMath.confirmed = db.settings.expectedIncome
  return IncomeMath.forMonth(db.incomeSources, db.incomes, month)
}

/**
 * Calcula todo lo de la planificación con los datos de la base. `today` solo se cambia en pruebas
 * (por defecto, hoy en la zona horaria del móvil).
 */
export function computePlanning(db: DB, today: LocalDate = todayDate()): Planning {
  const s = db.settings as SettingsExt
  IncomeMath.confirmed = s.expectedIncome
  const thisMonth = ymOfDate(today)
  const savingCats = db.categories.filter((c) => c.isSavings)
  const savingIds = new Set(savingCats.map((c) => c.id))
  const savingsPct = sumOf(savingCats, (c) => c.percent)
  const spendablePct = sumOf(db.categories.filter((c) => !c.isSavings), (c) => c.percent)
  const wealth = Wealth.of(db.accounts, db.crypto, db.stocks)
  const forecast = Forecasting.forecast(today, wealth.available, 90, db.incomeSources, db.incomes, db.bills, db.expenses, savingIds)
  const income = db.incomeSources.length === 0 ? s.monthlyIncome : IncomeMath.forMonth(db.incomeSources, db.incomes, thisMonth).total
  const inMonth = (day: number, m: YearMonth) => ymEq(ymOfDay(day), m)
  const savedThisMonth = sumOf(db.expenses.filter((e) => savingIds.has(e.categoryId) && inMonth(e.epochDay, thisMonth)), (e) => e.amount)
  const main = db.accounts.find((a) => a.id === s.bankMainId) ?? BankPlanning.defaultMain(db.accounts, s.cardAccountId ?? NONE)
  const target = db.accounts.find((a) => a.id === s.bankTargetId && a.id !== main?.id) ?? BankPlanning.defaultTarget(db.accounts)
  const data = makeFinanceData({
    today, accounts: db.accounts, crypto: db.crypto, stocks: db.stocks, categories: db.categories, expenses: db.expenses,
    incomes: db.incomes, sources: db.incomeSources, bills: db.bills, snapshots: db.snapshots, splits: db.splits, planReserve: s.planReserve,
  })
  const spentToday = sumOf(data.spending.filter((e) => e.epochDay === ep(today) && !Budgeting.isBillPayment(e, db.bills)), (e) => e.amount)
  // Cuánto ahorrar al mes: lo que pide tu perfil (o el reparto) y, si es más, tu primer objetivo.
  const monthlyIncome = data.monthlyIncome
  const billsMonthly = sumOf(db.bills, (b) => Bills.monthly(b))
  const profilePct = s.savingStyle >= 0 ? SavingProfile.split(s.savingStyle)[2] : savingsPct
  const goalNeed = GoalList.plan(GoalList.parse(s.goalsRaw), data, null, (v) => String(v)).find((g) => !g.done)?.requiredMonthly ?? 0
  const targetSaving = monthlyIncome > 0 ? Math.max(monthlyIncome * profilePct / 100, goalNeed) : Number.MAX_VALUE
  // Lo mínimo para vivir al día: lo que te deja tu perfil tras fijos y ahorro (nunca se manda eso al ahorro).
  const floorDaily = monthlyIncome > 0 ? Math.max(0, monthlyIncome - billsMonthly - monthlyIncome * profilePct / 100) / ymLen(thisMonth) : 0
  const factor = SavingProfile.spendFactor(s.savingStyle)
  const bankPlan = main ? BankPlanning.plan(
    today, main, target, s.bankBuffer, db.bills, db.expenses, db.transfers, db.incomeSources, db.incomes,
    BankPlanning.dailyHabit(data) * factor, spentToday, targetSaving, floorDaily * factor,
  ) : null
  // Con reparto, "puedes gastar" sale de la cuenta principal, igual que en Inicio (el margen de fin de mes cuenta como apartado).
  const safe = bankPlan != null && target != null
    ? makeSafeToSpend(bankPlan.main.balance + bankPlan.incomingTotal, bankPlan.pendingTotal, bankPlan.toSave, bankPlan.nextPayday, bankPlan.days, s.planReserve + bankPlan.buffer)
    : Spending.safeToSpend(today, wealth.available, forecast.events, income * savingsPct / 100, savedThisMonth, s.planReserve)
  const health = Insights.health(data, (v) => fixed(v, 2))
  const fmt = (v: number) => `${grouped0(v)} €`
  const goalList = GoalList.parse(s.goalsRaw)
  const goals = GoalList.plan(goalList, data, bankPlan, fmt)
  const goal = goals[0] ?? null
  const limits = Budgeting.parseLimits(s.tagLimitsRaw)
  const budget = Budgeting.plan(data, bankPlan?.saveGoal ?? (income * savingsPct / 100), limits, bankPlan, SavingProfile.weights(s.savingStyle, s.lifeGoals))
  const age = s.birthYear > 1900 ? FinancialAging.evaluate(today.year - s.birthYear, data, goal) : null

  // Meses cerrados desde que hay datos, para los logros de constancia.
  const days = [...db.expenses.map((e) => e.epochDay), ...db.incomes.map((i) => i.epochDay)]
  const firstDay = days.length === 0 ? null : Math.min(...days)
  let closed: Array<[number, number, number]> = []
  if (firstDay != null) {
    const first = ymOfDay(firstDay)
    closed = range(1, 24).map((i) => ymMinus(thisMonth, i)).filter((m) => !ymBefore(m, first)).reverse().map((m): [number, number, number] => {
      const received = sumOf(db.incomes.filter((i) => inMonth(i.epochDay, m)), (i) => i.amount)
      return [received, sumOf(db.expenses.filter((e) => inMonth(e.epochDay, m)), (e) => e.amount), received * spendablePct / 100]
    })
  }
  const achievements = Achievements.evaluate({
    wealth, accounts: db.accounts.length, investments: db.crypto.length + db.stocks.length,
    hasInterest: db.accounts.some((a) => a.interestRate > 0), cardPayments: s.cardPayments ?? false,
    monthlyEssentials: billsMonthly + forecast.dailySpend * 30,
    closedMonths: closed, bills: db.bills.length,
    splits: db.splits.length, healthGood: health.good,
  })
  const points = sumOf(achievements.filter((a) => a.unlocked), (a) => a.points)
  return {
    wealth, forecast, safe, comparison: Comparison.compare(today, db.expenses, db.incomes, db.categories), achievements, data, bankPlan,
    goal, goals, goalList, age, budget, limits, points, level: Achievements.level(points),
  }
}

/**
 * Lo que el init del ViewModel guarda tras cada cálculo: la foto de límites (para avisar tras un pago),
 * la puntuación de hoy de los tres círculos y los logros nuevos. Puro: devuelve el parche de ajustes
 * (vacío si no cambia nada) y los logros recién conseguidos para celebrarlos. Aplícalo con db.setSettings(patch).
 */
export function planningUpdates(p: Planning, settings: Settings, nowMs: number = Date.now()): { patch: Partial<Settings>; fresh: Achievement[] } {
  const patch: Partial<Settings> = {}
  const today = p.data.today
  // Guarda los límites para poder avisar tras un pago con tarjeta aunque la app esté cerrada.
  if (p.budget) {
    const len = lenOfMonth(today)
    const tagMonthly: Partial<Record<SpendTag, number>> = {}
    for (const it of p.budget.items) if (it.monthly >= 20) tagMonthly[it.tag] = it.monthly
    const snap = LimitSnapshot.encode({ perDay: p.bankPlan?.perDay ?? p.safe.perDay, weekOptimal: p.budget.base / len * 7, tagMonthly })
    if (snap !== settings.limitSnapshot) patch.limitSnapshot = snap
  }
  // Guarda la puntuación de hoy de los tres círculos (para "qué ha cambiado desde la semana pasada").
  try {
    const fmt = (v: number) => `${fixed(v, 0)} €`
    const pulse = PulseCalculator.of(p.data, p.budget, p.bankPlan, fmt)
    const circles = FinancialCircles.of(p.data, p.bankPlan, p.forecast, p.goals, pulse.margin, fmt)
    const history = FinancialCircles.decodeHistory(settings.circleHistory)
    history.set(ep(today), circles.map((c) => c.score))
    const enc = FinancialCircles.encodeHistory(history)
    if (enc !== settings.circleHistory) patch.circleHistory = enc
  } catch { /* como runCatching: si falla, no se guarda */ }
  const known = settings.achievements
  const fresh = p.achievements.filter((a) => a.unlocked && !(a.id in known))
  if (fresh.length > 0) {
    const merged = { ...known }
    for (const a of fresh) merged[a.id] = nowMs
    patch.achievements = merged
  }
  return { patch, fresh }
}

/** Planificación que se recalcula solo cuando cambia el estado de la base. */
// Un solo cálculo compartido por todas las pantallas: se repite solo cuando cambia el estado o el día.
let cache: { state: DB; day: string; plan: Planning } | null = null
function sharedPlanning(state: DB): Planning {
  const t = todayDate()
  const day = `${t.year}-${t.month}-${t.day}`
  if (!cache || cache.state !== state || cache.day !== day) cache = { state, day, plan: computePlanning(state) }
  return cache.plan
}

export function usePlanning(): Planning {
  const state = useDB((s) => s)
  return useMemo(() => sharedPlanning(state), [state])
}

/** Ingreso del mes elegido (lo fijo + la media de lo variable, corregido con lo cobrado). */
export function useMonthIncome(month: YearMonth): MonthIncome {
  const state = useDB((s) => s)
  return useMemo(() => computeMonthIncome(state, month), [state, month.year, month.month])
}
