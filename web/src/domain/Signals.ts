// Port de domain/Signals.kt: pagos recurrentes, dinero recuperable, aviso semanal, recibos, nómina,
// tendencias y precio real de una compra.
import type { Expense } from '../data/models'
import type { LocalDate } from '../data/dates'
import {
  average, date, daysBetween, distinct, dropLast, ep, fixed, fmtDayMonthShort, groupBy, isAfter, isBefore, maxBy, minusWeeks,
  monday, monthNameShort, plusD, plusM, range, sortedBy, sortedByDesc, sumOf, takeLast, ymEq, ymOfDate, ymOfDay,
} from './kt'
import { plusMonths } from '../data/dates'
import { Classifier, SPEND_TAG_INFO, SPEND_TAG_VALUES, spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { Insights } from './Insights'
import type { FinanceData } from './Insights'
import { Bills } from './Planning'
import type { BankPlan } from './Planning'
import { Advisor, Budgeting } from './Budget'
import { Goals } from './Goals'
import type { GoalStatus } from './Goals'

const FEE_WORDS = ['comision', 'comisiones', 'cuota tarjeta', 'mantenimiento', 'descubierto', 'cargo por']
const isFee = (note: string) => { const n = Classifier.normalize(note); return FEE_WORDS.some((w) => n.includes(w)) }
const ifBlank = (s: string, other: string) => (s.trim() === '' ? other : s)

// ---------- Detector de gastos recurrentes ----------

/** Un pago que se repite. `annual` (calculada) = monthly × 12. */
export interface Recurring {
  name: string
  /** Suscripción, recibo, cuota o comisión. */
  kind: string
  monthly: number
  last: number
  lastDate: LocalDate
  /** Cambio del último pago frente a los anteriores (0 = igual). */
  change: number
  times: number
  fromBills: boolean
  annual: number
}

function recurring(name: string, kind: string, monthly: number, last: number, lastDate: LocalDate, change: number, times: number, fromBills: boolean): Recurring {
  return { name, kind, monthly, last, lastDate, change, times, fromBills, annual: monthly * 12 }
}

function kindOf(e: Expense): string {
  if (isFee(e.note)) return 'Comisión'
  const t = spendTag(e)
  if (t === 'SUBSCRIPTIONS') return 'Suscripción'
  if (t === 'HOME') return 'Recibo'
  return 'Pago recurrente'
}

export const RecurringDetector = {
  /** Pagos que se repiten cada mes (por comercio), más tus gastos fijos. Solo describe: no decide si sobran. */
  find(d: FinanceData): Recurring[] {
    const since = ep(d.today) - 400
    const found: Recurring[] = []
    groupBy(d.spending.filter((e) => e.epochDay >= since), (e) => Classifier.normalize(e.note)).forEach((l, key) => {
      if (key === '') return
      const sorted = sortedBy(l, (e) => e.epochDay)
      const months = distinct(sorted.map((e) => { const m = ymOfDay(e.epochDay); return m.year * 12 + m.month }))
      if (months.length < 3 || sorted.length > months.length * 2) return
      const gaps: number[] = []
      for (let i = 0; i + 1 < sorted.length; i++) gaps.push(sorted[i + 1].epochDay - sorted[i].epochDay)
      gaps.sort((a, b) => a - b)
      const median = gaps[Math.trunc(gaps.length / 2)]
      if (median < 24 || median > 38) return
      const last = sorted[sorted.length - 1]
      if (last.epochDay < ep(d.today) - 45) return
      const before = average(takeLast(dropLast(sorted, 1), 3).map((e) => e.amount))
      found.push(recurring(last.note, kindOf(last), average(takeLast(sorted, 3).map((e) => e.amount)), last.amount, date(last.epochDay), last.amount - before, sorted.length, false))
    })
    const names = new Set(found.map((r) => Classifier.normalize(r.name)))
    const bills = d.bills.filter((b) => !names.has(Classifier.normalize(b.name))).map((b) => {
      const paid = sortedBy(d.expenses.filter((e) => Budgeting.isBillPayment(e, [b])), (e) => e.epochDay)
      const last = paid[paid.length - 1]
      const prevAmounts = takeLast(dropLast(paid, 1), 3).map((e) => e.amount)
      const before = average(prevAmounts.length === 0 ? [b.amount] : prevAmounts)
      return recurring(
        b.name, Classifier.guess(b.name) === 'SUBSCRIPTIONS' ? 'Suscripción' : b.yearly ? 'Pago anual' : 'Recibo',
        Bills.monthly(b), last?.amount ?? b.amount, last ? date(last.epochDay) : d.today, (last?.amount ?? b.amount) - before, paid.length, true,
      )
    })
    return sortedByDesc([...found, ...bills], (r) => r.annual)
  },
}

// ---------- Dinero recuperable ----------

/** Una posible recuperación: cuánto, por qué lo pensamos (evidencia) y qué puedes hacer. Nunca afirma un error. */
export interface Recoverable { title: string; amount: number; evidence: string; nextStep: string; expense: Expense | null }

export const RecoverableMoney = {
  find(d: FinanceData, refunds: Iterable<number>, fmt: (v: number) => string): Recoverable[] {
    const refundSet = new Set(refunds)
    const out: Recoverable[] = []
    // Posibles duplicados: mismo comercio e importe en menos de 3 días.
    const recent = sortedBy(d.expenses.filter((e) => e.epochDay >= ep(d.today) - 60), (e) => e.epochDay)
    recent.forEach((a, i) => {
      const b = recent.slice(i + 1).find((x) =>
        x.epochDay - a.epochDay <= 2 && Math.abs(x.amount - a.amount) <= Math.max(0.5, a.amount * 0.01) && a.amount >= 5 &&
        Classifier.normalize(a.note) === Classifier.normalize(x.note))
      if (b) {
        out.push({
          title: `Posible cargo duplicado · ${ifBlank(b.note, 'Gasto')}`, amount: b.amount,
          evidence: `Dos cargos de ${fmt(b.amount)} el ${fmtDayMonthShort(date(a.epochDay))} y el ${fmtDayMonthShort(date(b.epochDay))}.`,
          nextStep: 'Comprueba en la app de tu banco si son dos cargos reales. Si es así, pide la devolución al comercio; si fue una compra doble a propósito, ignóralo.', expense: b,
        })
      }
    })
    // Subidas de precio en pagos recurrentes.
    const rec = RecurringDetector.find(d)
    rec.filter((r) => r.change >= 1 && r.change >= r.last * 0.04).forEach((r) => {
      out.push({
        title: `${r.name} ha subido`, amount: r.change * 12,
        evidence: `Último pago ${fmt(r.last)}; antes pagabas unos ${fmt(r.last - r.change)} (+${fmt(r.change)} al mes).`,
        nextStep: `Pregunta si hay una tarifa o promoción más barata, o compara con otra compañía. Son ${fmt(r.change * 12)} al año.`, expense: null,
      })
    })
    // Comisiones: casi siempre se pueden negociar o evitar.
    groupBy(d.spending.filter((e) => e.epochDay >= ep(d.today) - 90 && isFee(e.note)), (e) => Classifier.normalize(e.note)).forEach((l) => {
      const total = sumOf(l, (e) => e.amount)
      out.push({
        title: `Comisiones · ${l[0].note}`, amount: total,
        evidence: `${l.length} ${l.length === 1 ? 'cargo' : 'cargos'} en los últimos 3 meses (${fmt(total)}).`,
        nextStep: 'Pide a tu banco que la anule o qué condiciones evitan cobrarla (nómina domiciliada, recibos…).', expense: maxBy(l, (e) => e.epochDay)!,
      })
    })
    // Suscripciones a revisar (sin dar por hecho que no se usan).
    RecurringDetector.find(d).filter((r) => r.kind === 'Suscripción').forEach((r) => {
      out.push({
        title: `Revisa ${r.name}`, amount: r.annual,
        evidence: `${fmt(r.monthly)} al mes · ${fmt(r.annual)} al año.`,
        nextStep: 'Si la usas, perfecto. Si la usas poco, mira un plan más barato o compartirla.', expense: null,
      })
    })
    // Reembolsos que tú has marcado como pendientes.
    d.expenses.filter((e) => refundSet.has(e.id)).forEach((e) => {
      const days = ep(d.today) - e.epochDay
      out.push({
        title: `Reembolso pendiente · ${ifBlank(e.note, 'Gasto')}`, amount: e.amount,
        evidence: `Lo marcaste como pendiente; pagado hace ${days} ${days === 1 ? 'día' : 'días'}.`,
        nextStep: days > 14 ? 'Han pasado más de dos semanas: reclámalo al comercio o a la persona.' : 'Aún está en plazo; si llega, márcalo como cobrado.', expense: e,
      })
    })
    return sortedByDesc(out, (r) => r.amount)
  },
}

// ---------- Aviso semanal por comercio ----------

export const WeeklyNudge = {
  /** "Pablo, esta semana has gastado 12 € en Monster Energy…": el capricho que más se repite esta semana. */
  find(d: FinanceData, name: string, fmt: (v: number) => string): string | null {
    const mon = ep(monday(d.today))
    const week = d.spending.filter((e) => e.epochDay >= mon && !Budgeting.isFixed(e, d.bills) && !SPEND_TAG_INFO[spendTag(e)].need && e.amount < 25)
    const groups = [...groupBy(week, (e) => Classifier.normalize(e.note)).entries()].filter(([k, v]) => k !== '' && v.length >= 2)
    const top = maxBy(groups, ([, v]) => sumOf(v, (e) => e.amount))?.[1]
    if (!top) return null
    const spent = sumOf(top, (e) => e.amount)
    const monthly = spent * 52 / 12
    const who = name.trim() !== '' ? `${name}, esta` : 'Esta'
    return `${who} semana has gastado ${fmt(spent)} en ${top[0].note} (${top.length} veces). Si gastas la mitad, ahorrarías unos ${fmt(monthly / 2)} al mes.`
  },
}

// ---------- Reorganizador de recibos ----------

export interface BillCalendar {
  payday: LocalDate | null
  /** Recibos de aquí al próximo cobro: [nombre, importe, fecha]. */
  beforePayday: Array<[string, number, LocalDate]>
  /** Totales por semana en las próximas 5 semanas: [etiqueta, total]. */
  weeks: Array<[string, number]>
  crunch: boolean
  suggestions: string[]
}

export const PaymentOrganizer = {
  analyze(d: FinanceData, plan: BankPlan | null, fmt: (v: number) => string): BillCalendar | null {
    if (d.bills.length === 0) return null
    const payday = Advisor.nextFixedPayday(d)
    const until = plusD(d.today, 35)
    const due: Array<[string, number, LocalDate]> = []
    let m = ymOfDate(d.today)
    const cur = m
    while (!isAfter({ year: m.year, month: m.month, day: 1 }, until)) {
      for (const b of d.bills) {
        const dt = Bills.dueDate(b, m)
        if (!dt) continue
        if (!isBefore(dt, d.today) && !isAfter(dt, until) && !(ymEq(m, cur) && Bills.isPaid(b, m, d.expenses))) due.push([b.name, b.amount, dt])
      }
      m = plusMonths(m, 1)
    }
    due.sort((a, b) => ep(a[2]) - ep(b[2]))
    const weeks: Array<[string, number]> = range(0, 4).map((w) => {
      const start = plusD(d.today, w * 7)
      const end = plusD(start, 6)
      return [`${fmtDayMonthShort(start)}–${fmtDayMonthShort(end)}`, sumOf(due.filter((x) => !isBefore(x[2], start) && !isAfter(x[2], end)), (x) => x[1])]
    })
    const before = payday == null ? due.filter((x) => ymEq(ymOfDate(x[2]), cur)) : due.filter((x) => isBefore(x[2], payday))
    const beforeTotal = sumOf(before, (x) => x[1])
    const available = plan ? plan.main.balance - plan.buffer : d.wealth.available
    const monthBills = sumOf(d.bills, (b) => Bills.monthly(b))
    const peak = maxBy(weeks, (w) => w[1])
    const crunch = beforeTotal > available * 0.8 || (peak != null && monthBills > 0 && peak[1] > monthBills * 0.6 && weeks.filter((w) => w[1] > 0).length > 1)
    const suggestions: string[] = []
    if (crunch && payday != null) {
      sortedByDesc(before.filter((x) => x[1] >= 30), (x) => x[1]).slice(0, 2).forEach(([name, amount, dt]) => {
        suggestions.push(`Pide cambiar ${name} (${fmt(amount)}, el ${fmtDayMonthShort(dt)}) al ${plusD(payday, 1).day} de cada mes: no tendrías que pagarlo antes de cobrar. Se suele pedir en el área de cliente o llamando al proveedor.`)
      })
      suggestions.push(`O reserva ${fmt(beforeTotal)} de cada nómina para los recibos que llegan antes de la siguiente.`)
    }
    if (peak != null && peak[1] > 0 && monthBills > 0 && peak[1] > monthBills * 0.6) suggestions.push(`La semana del ${peak[0]} se concentra el ${Math.trunc(peak[1] / monthBills * 100)} % de tus recibos del mes.`)
    if (suggestions.length === 0) suggestions.push('Tus recibos están bien repartidos: no hay semanas apretadas antes de tu próximo cobro.')
    return { payday, beforePayday: before, weeks, crunch, suggestions }
  },
}

// ---------- Distribuidor de la nómina ----------

export interface PaycheckSplit {
  income: number
  label: string
  parts: Array<[string, number]>
  notes: string[]
  /** Los recibos que caen entre esta nómina y la siguiente. */
  bills: Array<[string, number]>
  /** Lo libre: la última parte (calculada). */
  free: number
}

export const PaycheckDistributor = {
  /** Propuesta para la próxima nómina (o la última): recibos que vienen, lo básico, colchón, objetivos y dinero libre. */
  split(d: FinanceData, goal: GoalStatus | null, fmt: (v: number) => string): PaycheckSplit | null {
    const source = maxBy(d.sources.filter((s) => !s.variable), (s) => s.amount)
    if (!source) return null
    const income = source.amount
    const next = Advisor.nextFixedPayday(d)
    if (!next) return null
    const after = plusM(next, 1)
    // Recibos entre esta nómina y la siguiente, incluidos los anuales que caen en ese tramo.
    const bills: Array<[string, number]> = []
    const months = [ymOfDate(next), ymOfDate(after)]
    const uniq = ymEq(months[0], months[1]) ? [months[0]] : months
    for (const m of uniq) {
      for (const b of d.bills) {
        const dt = Bills.dueDate(b, m)
        if (dt && !isBefore(dt, next) && isBefore(dt, after)) bills.push([b.name, b.amount])
      }
    }
    const billsTotal = sumOf(bills, (x) => x[1])
    const closed = d.closedMonths(3)
    const essentials = closed.length === 0 ? income * 0.2
      : sumOf(closed, (m) => sumOf(Budgeting.variable(d, m).filter((e) => SPEND_TAG_INFO[spendTag(e)].need), (e) => e.amount)) / closed.length
    let left = income - billsTotal - essentials
    const cost = Insights.costOfLiving(d).monthly
    const cushionGap = Math.max(0, cost * 3 - d.wealth.savings)
    const cushion = Math.trunc(Math.min(Math.max(0, left) * 0.3, Math.min(cushionGap, income * 0.1)) / 5) * 5.0
    left -= cushion
    const goals = Math.trunc(Math.min(Math.max(0, left) * 0.6, goal?.requiredMonthly ?? Math.max(0, left) * 0.3) / 5) * 5.0
    left -= goals
    const notes: string[] = []
    const yearly = d.bills.filter((b) => b.yearly).filter((b) => bills.some((x) => x[0] === b.name))
    yearly.forEach((b) => notes.push(`Ojo: este tramo incluye ${b.name} (${fmt(b.amount)}), que solo pagas una vez al año. Por eso queda menos libre.`))
    if (left < 0) notes.push('No llega para todo: lo libre sale en negativo. Baja el colchón o el objetivo este mes.')
    if (cushionGap <= 0) notes.push('Tu colchón ya cubre 3 meses: no hace falta apartar para él.')
    const parts: Array<[string, number]> = [
      ['Recibos hasta la siguiente', billsTotal], ['Lo básico (súper, transporte…)', essentials], ['Colchón de emergencia', cushion],
      [goal?.goal?.name ?? 'Objetivos', goals], ['Dinero libre', left],
    ]
    return { income, label: `${source.name} del ${fmtDayMonthShort(next)}`, parts, notes, bills, free: parts[parts.length - 1][1] }
  },
}

// ---------- Cambios en tu vida financiera ----------

export interface Trend { title: string; explanation: string }

export const TrendDetector = {
  /** Solo cambios que se mantienen varias semanas o meses, con lo que los explica. */
  find(d: FinanceData, fmt: (v: number) => string): Trend[] {
    const out: Trend[] = []
    const mon = monday(d.today)
    const weekStarts = range(1, 12).map((w) => minusWeeks(mon, w)) // semanas completas, de la más reciente a la más antigua
    const variable = d.spending.filter((e) => !Budgeting.isFixed(e, d.bills))
    if (variable.length > 0 && Math.min(...variable.map((e) => e.epochDay)) <= ep(weekStarts[weekStarts.length - 1])) {
      const weekTotal = (start: LocalDate, tag: SpendTag) => {
        const a = ep(start); const b = ep(plusD(start, 6))
        return sumOf(variable.filter((e) => spendTag(e) === tag && e.epochDay >= a && e.epochDay <= b), (e) => e.amount)
      }
      for (const tag of SPEND_TAG_VALUES) {
        const recent = weekStarts.slice(0, 4).map((w) => weekTotal(w, tag))
        const base = average(weekStarts.slice(4).map((w) => weekTotal(w, tag)))
        const above = recent.filter((v) => v > base * 1.25 && v - base >= 3).length
        const diff = average(recent) - base
        if (above >= 3 && diff >= 5) {
          const groups = [...groupBy(variable.filter((e) => spendTag(e) === tag && e.epochDay >= ep(weekStarts[3])), (e) => Classifier.normalize(e.note)).values()]
          const cause = maxBy(groups, (l) => sumOf(l, (e) => e.amount))
          out.push({
            title: `${SPEND_TAG_INFO[tag].label}: sube desde hace semanas`,
            explanation: `Llevas ${above} de las últimas 4 semanas por encima de lo normal: ${fmt(average(recent))} a la semana frente a ${fmt(base)} antes (unos ${fmt(diff * 52 / 12)} más al mes).` +
              (cause ? ` Lo explica sobre todo ${cause[0].note} (${cause.length} pagos).` : ''),
          })
        }
      }
    }
    // Ingresos más variables.
    const months = d.closedMonths(6).map((m) => d.receivedIn(m))
    if (months.length >= 6) {
      const cv = (l: number[]) => { const m = average(l); return m <= 0 ? 0 : Math.sqrt(sumOf(l, (x) => (x - m) * (x - m)) / l.length) / m }
      const now = cv(months.slice(0, 3)); const before = cv(months.slice(3))
      if (now > before + 0.1 && now > 0.15) out.push({ title: 'Tus ingresos son más variables', explanation: 'En los últimos 3 meses lo que cobras cambia más de un mes a otro que antes. Ten algo más de colchón en la cuenta del día a día.' })
    }
    // Margen mensual que se va estrechando.
    const nets = [...d.closedMonths(3)].reverse().map((m) => d.receivedIn(m) - d.spentIn(m))
    if (nets.length === 3 && nets[0] > 0 && nets[1] < nets[0] && nets[2] < nets[1] && nets[2] < nets[0] * 0.7) {
      out.push({ title: 'Tu margen se está estrechando', explanation: `Lo que te sobra cada mes ha bajado tres meses seguidos: ${nets.map((n) => fmt(n)).join(' → ')}.` })
    }
    return out
  },
}

// ---------- Precio real de una compra ----------

export interface RealPrice {
  price: number
  incomeDays: number
  goalDelayDays: number | null
  cushionMonthsBefore: number | null
  cushionMonthsAfter: number | null
  /** Ahorro previsto en 1, 2 y 3 meses: [mes, sin comprar, comprando]. */
  months: Array<[string, number, number]>
  waitNote: string
}

export const RealPriceCalculator = {
  of(price: number, d: FinanceData, goal: GoalStatus | null, fmt: (v: number) => string): RealPrice {
    const dailyIncome = d.monthlyIncome / 30.4
    const pace = Goals.paceOf(d, null)
    const cost = Insights.costOfLiving(d).monthly
    const savings = d.wealth.savings
    const names = range(1, 3).map((i) => monthNameShort(plusM(d.today, i).month))
    const months = names.map((n, i): [string, number, number] => [n, savings + pace * (i + 1), savings + pace * (i + 1) - price])
    let wait: string
    if (pace > 0) {
      const m = price / pace
      wait = m <= 3 ? `Si esperas 3 meses y lo apartas poco a poco (${fmt(price / 3)} al mes), no tocas tu ahorro.` : `Aunque esperes 3 meses, a tu ritmo necesitarías ${fixed(m, 1)} meses de ahorro para pagarlo.`
    } else wait = 'Ahora mismo no te sobra dinero al mes: esperar no lo hace más fácil, salvo que recortes algo.'
    return {
      price, incomeDays: dailyIncome > 0 ? price / dailyIncome : 0,
      goalDelayDays: goal && goal.pace > 0 && !goal.done ? Math.trunc(price / goal.pace * 30.4) : null,
      cushionMonthsBefore: cost > 0 ? savings / cost : null, cushionMonthsAfter: cost > 0 ? (savings - price) / cost : null, months, waitNote: wait,
    }
  },
}

/** Días entre dos fechas (para mostrar "en 5 días"). */
export function daysUntil(from: LocalDate, to: LocalDate): number { return daysBetween(from, to) }
