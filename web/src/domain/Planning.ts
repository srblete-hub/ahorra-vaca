// Port de domain/Planning.kt: gastos fijos, previsión, "puedes gastar", reparto del banco, comparación y logros.
import type { Account, Category, Expense, Income, IncomeSource, RecurringBill, Transfer } from '../data/models'
import type { LocalDate, YearMonth } from '../data/dates'
import {
  atDay, atDayClamped, atEndOfMonth, clamp, ep, isAfter, isBefore, maxBy, minBy, minOf, plusD, range,
  sumOf, withDayOfMonth, ymEq, ymLen, ymMinus, ymOfDate, ymOfDay,
} from './kt'
import { plusMonths } from '../data/dates'
import { IncomeMath, isDebt, isSaving } from './FinanceMath'
import type { Wealth } from './FinanceMath'
import type { FinanceData } from './Insights'
import { Budgeting } from './Budget'

export const Bills = {
  /** Cuándo toca pagarlo en `month`, o null si es anual y no le toca ese mes. */
  dueDate(bill: RecurringBill, month: YearMonth): LocalDate | null {
    return bill.yearly && month.month !== bill.month ? null : atDayClamped(month, bill.day)
  },

  /** Pagado ese mes: hay un gasto enlazado, o uno con su nombre e importe parecido (±10 %). */
  isPaid(bill: RecurringBill, month: YearMonth, expenses: Expense[]): boolean {
    return expenses.some((e) => ymEq(ymOfDay(e.epochDay), month) && (
      e.billId === bill.id ||
      (e.note.toLowerCase().includes(bill.name.toLowerCase()) && Math.abs(e.amount - bill.amount) <= bill.amount * 0.1)
    ))
  },

  /** Lo que cuesta al mes (los anuales, repartidos en 12). */
  monthly(bill: RecurringBill): number { return bill.yearly ? bill.amount / 12 : bill.amount },
}

/** Algo que va a pasar en tu cuenta: un cobro (+) o un gasto fijo (−). */
export interface FlowEvent { date: LocalDate; label: string; amount: number; isIncome: boolean; overdue: boolean }

/** `lowest`, `end` y `firstNegative` (calculadas en Kotlin) se rellenan al crearla. */
export interface Forecast {
  points: Array<[LocalDate, number]>
  events: FlowEvent[]
  dailySpend: number
  lowest: [LocalDate, number]
  end: [LocalDate, number]
  /** Primer día en que el saldo previsto baja de cero. */
  firstNegative: [LocalDate, number] | null
}

export function makeForecast(points: Array<[LocalDate, number]>, events: FlowEvent[], dailySpend: number): Forecast {
  return {
    points, events, dailySpend,
    lowest: minBy(points, (p) => p[1])!,
    end: points[points.length - 1],
    firstNegative: points.find((p) => p[1] < 0) ?? null,
  }
}

export const Forecasting = {
  /** Gasto variable medio por día de los últimos `window` días (sin gastos fijos ni aportaciones al ahorro). */
  dailyVariableSpend(expenses: Expense[], today: LocalDate, savingCategoryIds: Set<number>, window = 60): number {
    const from = ep(today) - window
    const t = ep(today)
    const relevant = expenses.filter((e) => e.billId == null && !savingCategoryIds.has(e.categoryId) && e.epochDay > from && e.epochDay <= t)
    if (relevant.length === 0) return 0
    // Si llevas poco tiempo usando la app, se divide entre los días que hay datos (mínimo 14).
    const firstDay = minOf(relevant, (e) => e.epochDay)!
    const days = Math.min(Math.max(14, t - firstDay + 1), window)
    return sumOf(relevant, (e) => e.amount) / days
  },

  /** Lo que se espera cobrar de `source` en `month`: lo fijo, o la media de lo variable. */
  expected(source: IncomeSource, incomes: Income[], _month: YearMonth, now: YearMonth): number {
    return source.variable
      ? IncomeMath.confirmedFor(source, now) ?? IncomeMath.variableAverage(source, incomes, now) ?? source.amount
      : source.amount
  },

  /** Cobros y gastos fijos previstos entre hoy y `until`; lo que ya debería haber pasado y no ha pasado va a hoy. */
  events(
    today: LocalDate, until: LocalDate,
    sources: IncomeSource[], incomes: Income[], bills: RecurringBill[], expenses: Expense[],
  ): FlowEvent[] {
    const out: FlowEvent[] = []
    let m = ymOfDate(today)
    const current = m
    // En Kotlin se usa YearMonth.now(); aquí, el mes de `today` (es lo mismo salvo en pruebas con otra fecha).
    while (!isAfter(atDay(m, 1), until)) {
      for (const s of sources) {
        const due = atDayClamped(m, s.dayFrom)
        if (ymEq(m, current)) {
          const received = incomes.some((it) => it.sourceId === s.id && ymEq(ymOfDay(it.epochDay), m))
          if (received) continue
        }
        const d = isBefore(due, today) ? today : due
        if (!isAfter(d, until)) out.push({ date: d, label: s.name, amount: Forecasting.expected(s, incomes, m, current), isIncome: true, overdue: isBefore(due, today) })
      }
      for (const b of bills) {
        const due = Bills.dueDate(b, m)
        if (!due) continue
        if (ymEq(m, current) && Bills.isPaid(b, m, expenses)) continue
        const d = isBefore(due, today) ? today : due
        if (!isAfter(d, until)) out.push({ date: d, label: b.name, amount: -b.amount, isIncome: false, overdue: isBefore(due, today) })
      }
      m = plusMonths(m, 1)
    }
    return out.sort((a, b) => ep(a.date) - ep(b.date) || Number(!a.isIncome) - Number(!b.isIncome))
  },

  /** Saldo previsto cada día: hoy, más cobros, menos gastos fijos y menos el gasto diario habitual. */
  forecast(
    today: LocalDate, startBalance: number, days: number,
    sources: IncomeSource[], incomes: Income[], bills: RecurringBill[], expenses: Expense[],
    savingCategoryIds: Set<number>,
  ): Forecast {
    const until = plusD(today, days)
    const events = Forecasting.events(today, until, sources, incomes, bills, expenses)
    const daily = Forecasting.dailyVariableSpend(expenses, today, savingCategoryIds)
    let balance = startBalance
    const points: Array<[LocalDate, number]> = range(0, days).map((i) => {
      const d = plusD(today, i)
      if (i > 0) balance -= daily
      const e = ep(d)
      balance += sumOf(events.filter((x) => ep(x.date) === e), (x) => x.amount)
      return [d, balance]
    })
    return makeForecast(points, events, daily)
  },
}

/**
 * Cuánto puedes gastar de verdad sin tocar lo que ya está comprometido hasta el próximo cobro.
 * `safe` y `perDay` (calculadas en Kotlin) se rellenan con makeSafeToSpend().
 */
export interface SafeToSpend {
  available: number
  pendingBills: number
  savingsReserve: number
  nextPayday: LocalDate | null
  daysToPayday: number
  /** Apartado para un plan (una escapada, un regalo…): no se cuenta como gastable. */
  plans: number
  safe: number
  perDay: number
}

export function makeSafeToSpend(
  available: number, pendingBills: number, savingsReserve: number, nextPayday: LocalDate | null, daysToPayday: number, plans = 0,
): SafeToSpend {
  const safe = available - pendingBills - savingsReserve - plans
  return { available, pendingBills, savingsReserve, nextPayday, daysToPayday, plans, safe, perDay: safe <= 0 ? 0 : safe / Math.max(1, daysToPayday) }
}

export const Spending = {
  safeToSpend(
    today: LocalDate, available: number, events: FlowEvent[],
    plannedSavings: number, savedThisMonth: number, plans = 0,
  ): SafeToSpend {
    const payday = events.find((e) => e.isIncome && isAfter(e.date, today))?.date ?? null
    const horizon = payday ?? plusD(atEndOfMonth(ymOfDate(today)), 1)
    const pending = sumOf(events.filter((e) => !e.isIncome && isBefore(e.date, horizon)), (e) => -e.amount)
    const reserve = Math.max(0, plannedSavings - savedThisMonth)
    const days = ep(horizon) - ep(today)
    return makeSafeToSpend(available, pending, reserve, payday, Math.max(1, days), plans)
  },
}

/**
 * El mes de tu cuenta principal: el saldo, menos los gastos fijos que aún salen (alquiler…), más lo
 * que aún te tiene que llegar (por ejemplo, la parte del sueldo que cobras más tarde). De ahí se
 * reserva lo que sueles gastar hasta fin de mes y un margen para llegar sobrado; lo que sobra va
 * al ahorro (Robinhood…). Lo que aún no ha llegado se mueve cuando llegue.
 *
 * Las propiedades calculadas de Kotlin (pendingTotal … perDay) se rellenan con makeBankPlan().
 */
export interface BankPlan {
  main: Account
  target: Account | null
  /** Gastos fijos pendientes que salen de la cuenta principal: [nombre, importe]. */
  pending: Array<[string, number]>
  /** Cobros fijos que aún faltan este mes en esta cuenta: [nombre, importe, día]. */
  incoming: Array<[string, number, LocalDate]>
  /** Lo que sueles gastar al día (sin fijos ni ahorro). */
  dailyHabit: number
  /** Lo que llevas gastado hoy (ya descontado del saldo). */
  spentToday: number
  /** Con cuánto quieres llegar a fin de mes después de ahorrar. */
  buffer: number
  /** Lo ya movido este mes de la principal al ahorro. */
  movedThisMonth: number
  nextPayday: LocalDate | null
  /** Días que quedan del mes, hoy incluido. */
  days: number
  /** Lo que quieres ahorrar al mes según tus objetivos y tu perfil (tope del ahorro). Number.MAX_VALUE = sin tope. */
  targetSaving: number
  /** Lo mínimo para vivir al día según tu perfil: se reserva aunque tu gasto apuntado sea menor. */
  floorDaily: number
  // ----- calculadas -----
  pendingTotal: number
  incomingTotal: number
  /** Lo que queda en la cuenta tras los fijos. */
  remainder: number
  /** Todo el dinero de lo que queda de mes: lo que hay tras los fijos más lo que va a llegar. */
  monthMoney: number
  /** Lo que sueles gastar de aquí a fin de mes. */
  expectedSpend: number
  /** Lo que sobra tras vivir hasta fin de mes y el margen, sin pasar de lo que quieres ahorrar al mes. */
  surplus: number
  /** Ahorro de este mes (incluye lo ya movido), en múltiplos de 5. */
  saveGoal: number
  toSave: number
  /** Lo que se puede mover ya. Si ya metiste casi todo lo del mes (90 %), no se insiste. */
  moveNow: number
  moveLater: number
  /** Lo que puedes gastar hasta fin de mes sin tocar el ahorro ni el margen. */
  spendable: number
  perDay: number
}

export type BankPlanInput = Omit<BankPlan,
  'pendingTotal' | 'incomingTotal' | 'remainder' | 'monthMoney' | 'expectedSpend' | 'surplus' | 'saveGoal' | 'toSave' | 'moveNow' | 'moveLater' | 'spendable' | 'perDay'>

export function makeBankPlan(i: BankPlanInput): BankPlan {
  const pendingTotal = sumOf(i.pending, (p) => p[1])
  const incomingTotal = sumOf(i.incoming, (p) => p[1])
  const remainder = i.main.balance - pendingTotal
  const monthMoney = remainder + incomingTotal
  const expectedSpend = Math.max(0, Math.max(i.dailyHabit, i.floorDaily) * i.days - i.spentToday)
  const surplus = Math.max(0, monthMoney + i.movedThisMonth - expectedSpend - i.buffer)
  const saveGoal = Math.floor(Math.min(surplus, i.targetSaving) / 5) * 5
  const toSave = Math.max(0, saveGoal - i.movedThisMonth)
  const moveNow = saveGoal > 0 && i.movedThisMonth >= saveGoal * 0.9 ? 0 : Math.floor(Math.max(0, toSave - incomingTotal) / 5) * 5
  const moveLater = Math.max(0, toSave - moveNow)
  const spendable = monthMoney - toSave - i.buffer
  const perDay = spendable <= 0 ? 0 : spendable / Math.max(1, i.days)
  return {
    main: i.main, target: i.target, pending: i.pending, incoming: i.incoming, dailyHabit: i.dailyHabit, spentToday: i.spentToday,
    buffer: i.buffer, movedThisMonth: i.movedThisMonth, nextPayday: i.nextPayday, days: i.days, targetSaving: i.targetSaving, floorDaily: i.floorDaily,
    pendingTotal, incomingTotal, remainder, monthMoney, expectedSpend, surplus, saveGoal, toSave, moveNow, moveLater, spendable, perDay,
  }
}

export const BankPlanning = {
  /** Cuenta principal por defecto: la de los pagos con tarjeta, o la cuenta bancaria con más saldo. */
  defaultMain(accounts: Account[], cardAccountId: number): Account | null {
    return accounts.find((a) => a.id === cardAccountId && !isSaving(a) && !isDebt(a))
      ?? maxBy(accounts.filter((a) => a.type === 'BANK' && !isSaving(a)), (a) => a.balance)
      ?? maxBy(accounts.filter((a) => !isSaving(a) && !isDebt(a)), (a) => a.balance)
      ?? null
  },

  /** Destino por defecto: la cuenta de ahorro que más interés da. */
  defaultTarget(accounts: Account[]): Account | null {
    const saving = accounts.filter((a) => isSaving(a))
    let best: Account | null = null
    for (const a of saving) {
      // maxWithOrNull(compareBy({ interestRate }, { balance })): en empate exacto se queda el primero.
      if (!best || a.interestRate > best.interestRate || (a.interestRate === best.interestRate && a.balance > best.balance)) best = a
    }
    return best
  },

  plan(
    today: LocalDate,
    main: Account,
    target: Account | null,
    buffer: number,
    bills: RecurringBill[],
    expenses: Expense[],
    transfers: Transfer[],
    sources: IncomeSource[],
    incomes: Income[],
    dailyHabit: number,
    spentToday: number,
    targetSaving = Number.MAX_VALUE,
    floorDaily = 0,
  ): BankPlan {
    const thisMonth = ymOfDate(today)
    const pending: Array<[string, number]> = bills.filter((b) => b.accountId === main.id || b.accountId == null)
      .filter((b) => Bills.dueDate(b, thisMonth) != null && !Bills.isPaid(b, thisMonth, expenses))
      .map((b) => [b.name, b.amount])
    // Solo cobros fijos (lo variable no es seguro) que llegan a esta cuenta y aún no han llegado este mes.
    const incoming: Array<[string, number, LocalDate]> = sources.filter((s) => s.accountId === main.id || s.accountId == null)
      .filter((s) => !incomes.some((it) => it.sourceId === s.id && ymEq(ymOfDay(it.epochDay), thisMonth)))
      .map((s): [string, number, LocalDate] => {
        const conf = IncomeMath.confirmedFor(s, thisMonth)
        const amount = conf ?? (s.variable ? 0.7 * (IncomeMath.variableAverage(s, incomes, thisMonth) ?? s.amount) : s.amount)
        const day = atDay(thisMonth, Math.min(s.dayFrom, ymLen(thisMonth)))
        return [
          conf != null ? `${s.name} (confirmado)` : s.variable ? `${s.name} (estimado)` : s.name,
          Math.floor(amount / 5) * 5,
          isBefore(day, today) ? today : day,
        ]
      })
      .filter((x) => x[1] > 0)
      .sort((a, b) => ep(a[2]) - ep(b[2]))
    // Lo que ya entró este mes en la cuenta de ahorro, venga de donde venga (Santander → Revolut → Robinhood).
    const moved = target == null ? 0 : sumOf(transfers.filter((t) =>
      t.toAccountId === target.id && t.fromAccountId !== target.id && ymEq(ymOfDay(t.epochDay), thisMonth)), (t) => t.amount)
    const days = Math.max(1, ymLen(thisMonth) - today.day + 1)
    return makeBankPlan({
      main, target, pending, incoming, dailyHabit, spentToday, buffer, movedThisMonth: moved,
      nextPayday: incoming[0]?.[2] ?? null, days, targetSaving, floorDaily,
    })
  },

  /** Gasto habitual por día: la media de los meses cerrados (sin fijos ni ahorro), o la de los últimos días. */
  dailyHabit(d: FinanceData): number {
    const closed = d.closedMonths(3)
    if (closed.length > 0) return sumOf(closed, (m) => sumOf(Budgeting.variable(d, m), (e) => e.amount)) / sumOf(closed, (m) => ymLen(m))
    return Forecasting.dailyVariableSpend(d.expenses.filter((e) => !Budgeting.isFixed(e, d.bills)), d.today, d.savingIds)
  },
}

/** "¿Dónde se ha ido mi dinero?": este mes hasta hoy frente al mismo tramo del mes pasado. */
export interface MonthComparison {
  incomeNow: number
  incomePrev: number
  spentNow: number
  spentPrev: number
  /** Diferencia por categoría (positivo = gastas más), de mayor a menor. */
  byCategory: Array<[Category, number]>
}

export const Comparison = {
  compare(today: LocalDate, expenses: Expense[], incomes: Income[], categories: Category[]): MonthComparison {
    const thisStart = ep(withDayOfMonth(today, 1))
    const prevMonth = ymMinus(ymOfDate(today), 1)
    const prevStart = ep(atDay(prevMonth, 1))
    const prevEnd = ep(atDayClamped(prevMonth, today.day))
    const t = ep(today)
    const inNow = (d: number) => d >= thisStart && d <= t
    const inPrev = (d: number) => d >= prevStart && d <= prevEnd
    const diffs: Array<[Category, number]> = categories.map((c): [Category, number] => {
      const now = sumOf(expenses.filter((e) => e.categoryId === c.id && inNow(e.epochDay)), (e) => e.amount)
      const prev = sumOf(expenses.filter((e) => e.categoryId === c.id && inPrev(e.epochDay)), (e) => e.amount)
      return [c, now - prev]
    }).sort((a, b) => b[1] - a[1])
    return {
      incomeNow: sumOf(incomes.filter((i) => inNow(i.epochDay)), (i) => i.amount),
      incomePrev: sumOf(incomes.filter((i) => inPrev(i.epochDay)), (i) => i.amount),
      spentNow: sumOf(expenses.filter((e) => inNow(e.epochDay)), (e) => e.amount),
      spentPrev: sumOf(expenses.filter((e) => inPrev(e.epochDay)), (e) => e.amount),
      byCategory: diffs,
    }
  },
}

// ---------- Logros ----------

/** `unlocked` (calculada en Kotlin) = progress >= 1. */
export interface Achievement {
  id: string
  title: string
  description: string
  icon: string
  points: number
  progress: number
  unlocked: boolean
}

function achievement(id: string, title: string, description: string, icon: string, points: number, progress: number): Achievement {
  return { id, title, description, icon, points, progress, unlocked: progress >= 1.0 }
}

/** Nivel: `to` = null en el último. Usa levelProgress(level, points) para Level.progress(points). */
export interface Level { number: number; name: string; from: number; to: number | null }
export function levelProgress(l: Level, points: number): number { return l.to == null ? 1.0 : (points - l.from) / (l.to - l.from) }

/** Lo que se necesita para calcular los logros, ya resumido. */
export interface AchievementInput {
  wealth: Wealth
  accounts: number
  investments: number
  hasInterest: boolean
  cardPayments: boolean
  /** Gastos esenciales al mes (fijos + gasto habitual), para el fondo de emergencia. */
  monthlyEssentials: number
  /** Meses ya cerrados, del más antiguo al más reciente: [cobrado, gastado, límite de gasto]. */
  closedMonths: Array<[number, number, number]>
  bills: number
  /** Gastos repartidos con otros. */
  splits?: number
  /** Indicadores del informe de salud en buen estado. */
  healthGood?: number
}

const LEVELS: Array<[number, string]> = [
  [0, 'Semilla'], [30, 'Brote'], [80, 'Ahorrador'], [150, 'Constante'],
  [250, 'Estratega'], [400, 'Inversor'], [600, 'Maestro'], [900, 'Leyenda'],
]

const ratio = (value: number, target: number) => (target <= 0 ? 0 : clamp(value / target, 0, 1))

/** Racha más larga de meses cerrados con más cobrado que gastado. */
function bestGreenStreak(months: Array<[number, number, number]>): number {
  let best = 0; let run = 0
  for (const [inc, spent] of months) { if (inc > spent && inc > 0) { run++; best = Math.max(best, run) } else run = 0 }
  return best
}

const fmtK = (v: number) => (v >= 1000 ? `${Math.trunc(v / 1000)}.${String(v % 1000).padStart(3, '0')} €` : `${v} €`)

export const Achievements = {
  level(points: number): Level {
    let i = -1
    LEVELS.forEach((l, idx) => { if (points >= l[0]) i = idx })
    return { number: i + 1, name: LEVELS[i][1], from: LEVELS[i][0], to: LEVELS[i + 1]?.[0] ?? null }
  },

  evaluate(i: AchievementInput): Achievement[] {
    const saved = i.wealth.savings + i.wealth.invested
    const green = bestGreenStreak(i.closedMonths)
    const withinLimit = i.closedMonths.some(([inc, spent, limit]) => inc > 0 && limit > 0 && spent <= limit)
    const emergency = i.monthlyEssentials > 0 ? i.monthlyEssentials * 3 : 0
    const splits = i.splits ?? 0
    const healthGood = i.healthGood ?? 0
    const list: Achievement[] = [
      achievement('primer-paso', 'Primer paso', 'Pon el dinero que tienes ahora', 'flag', 10, i.accounts > 0 ? 1 : 0),
      achievement('piloto', 'Piloto automático', 'Activa los pagos con tarjeta automáticos', 'card', 15, i.cardPayments ? 1 : 0),
      achievement('fijos', 'Sin sorpresas', 'Apunta tus gastos fijos', 'calendar', 10, i.bills > 0 ? 1 : 0),
      achievement('intereses', 'Dinero trabajando', 'Ten una cuenta que te dé intereses', 'savings', 20, i.hasInterest ? 1 : 0),
      achievement('inversor', 'Inversor', 'Añade tu primera inversión', 'trending', 20, i.investments > 0 ? 1 : 0),
      achievement('colchon', 'Colchón', 'Ahorra 3 meses de gastos esenciales' + (emergency > 0 ? '' : ' (apunta gastos para calcularlo)'), 'shield', 60, ratio(i.wealth.savings, emergency)),
      achievement('mes-verde', 'Mes en verde', 'Cierra un mes cobrando más de lo que gastas', 'leaf', 25, green >= 1 ? 1 : 0),
      achievement('racha-3', 'Racha de 3', 'Tres meses seguidos en verde', 'fire', 50, ratio(green, 3)),
      achievement('limite', 'Dentro del límite', 'Cierra un mes sin pasarte del límite de gasto', 'target', 25, withinLimit ? 1 : 0),
      achievement('compartir', 'Cuentas claras', 'Reparte un gasto con otras personas', 'people', 15, splits > 0 ? 1 : 0),
      achievement('salud', 'Salud de hierro', 'Ten 4 de 5 indicadores de salud financiera en verde', 'heart', 40, ratio(healthGood, 4)),
    ]
    ;[500, 1_000, 5_000, 10_000, 25_000, 50_000, 100_000].forEach((target, n) => {
      list.push(achievement(`ahorro-${target}`, `${fmtK(target)} ahorrados`, `Junta ${fmtK(target)} entre ahorro e inversiones`, 'piggy', 15 + n * 15, ratio(saved, target)))
    })
    ;[10_000, 50_000, 100_000, 250_000].forEach((target, n) => {
      list.push(achievement(`patrimonio-${target}`, `Patrimonio de ${fmtK(target)}`, `Llega a ${fmtK(target)} de patrimonio total`, 'crown', 40 + n * 40, ratio(i.wealth.total, target)))
    })
    return list
  },
}
