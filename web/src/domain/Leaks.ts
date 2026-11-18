// Port de domain/Leaks.kt: dinero invisible, anomalías personales y simulador de compras.
import type { Expense } from '../data/models'
import type { LocalDate } from '../data/dates'
import {
  average, distinct, distinctBy, dropLast, ep, groupBy, maxBy, monthsBetweenDates, sortedBy, sortedByDesc, sumOf, takeLast,
  withDayOfMonth, ymLen, ymOfDay,
} from './kt'
import { Classifier, SPEND_TAG_INFO, spendTag } from './Spend'
import { byTag } from './Insights'
import type { FinanceData } from './Insights'
import { Bills } from './Planning'
import type { BankPlan } from './Planning'
import { Budgeting } from './Budget'
import type { GoalStatus } from './Goals'

// ---------- 3. Detector de dinero invisible ----------

export type LeakKind = 'SUBSCRIPTION' | 'FEE' | 'REPEATED' | 'PRICE_UP'
export const LEAK_KIND_VALUES: LeakKind[] = ['SUBSCRIPTION', 'FEE', 'REPEATED', 'PRICE_UP']
export const LEAK_KIND_INFO: Record<LeakKind, { label: string }> = {
  SUBSCRIPTION: { label: 'Suscripción' }, FEE: { label: 'Comisión' }, REPEATED: { label: 'Compra repetida' }, PRICE_UP: { label: 'Subida de precio' },
}

/** Una fuga pequeña con lo que cuesta al año; `expense` es un pago para revisarlo. */
export interface Leak { kind: LeakKind; name: string; detail: string; yearly: number; expense: Expense | null }

const FEE_WORDS = ['comision', 'comisiones', 'cuota tarjeta', 'cuota de mantenimiento', 'mantenimiento cuenta', 'descubierto', 'intereses tarjeta', 'fee', 'cargo por']

export const InvisibleMoney = {
  find(d: FinanceData, fmt: (v: number) => string): Leak[] {
    const since = ep(d.today) - 120
    const recent = d.spending.filter((e) => e.epochDay >= since)
    const out: Leak[] = []

    // Suscripciones: gastos fijos y pagos que lo parecen. Se listan para que decidas si aún las usas.
    d.bills.filter((b) => Classifier.guess(b.name) === 'SUBSCRIPTIONS').forEach((b) => {
      out.push({ kind: 'SUBSCRIPTION', name: b.name, detail: `¿La sigues usando? ${fmt(Bills.monthly(b))} al mes`, yearly: Bills.monthly(b) * 12, expense: null })
    })
    groupBy(recent.filter((e) => spendTag(e) === 'SUBSCRIPTIONS' && !Budgeting.isBillPayment(e, d.bills)), (e) => Classifier.normalize(e.note)).forEach((l) => {
      const last = maxBy(l, (e) => e.epochDay)!
      if (!out.some((x) => Classifier.normalize(x.name) === Classifier.normalize(last.note))) {
        out.push({ kind: 'SUBSCRIPTION', name: last.note, detail: `No está en tus gastos fijos · ${fmt(last.amount)} al mes`, yearly: last.amount * 12, expense: last })
      }
    })

    // Comisiones bancarias.
    groupBy(recent.filter((e) => { const n = Classifier.normalize(e.note); return FEE_WORDS.some((w) => n.includes(w)) }), (e) => Classifier.normalize(e.note)).forEach((l) => {
      const perYear = sumOf(l, (e) => e.amount) * 365 / 120
      out.push({ kind: 'FEE', name: l[0].note, detail: `${l.length} cargos en 4 meses: casi siempre se pueden quitar`, yearly: perYear, expense: maxBy(l, (e) => e.epochDay)! })
    })

    // Compras pequeñas repetidas (café, snacks, apps…): 4 o más en un mes y menos de 15 € cada una.
    groupBy(recent.filter((e) => e.amount < 15 && !Budgeting.isFixed(e, d.bills)), (e) => Classifier.normalize(e.note)).forEach((l, key) => {
      if (key === '') return
      const months = Math.max(distinct(l.map((e) => { const m = ymOfDay(e.epochDay); return m.year * 12 + m.month })).length, 1)
      if (l.length / months >= 4) {
        const perMonth = sumOf(l, (e) => e.amount) / months
        out.push({ kind: 'REPEATED', name: l[0].note, detail: `${Math.trunc(l.length / months)} veces al mes · ${fmt(perMonth)} al mes sin darte cuenta`, yearly: perMonth * 12, expense: maxBy(l, (e) => e.epochDay)! })
      }
    })

    // Subidas de precio: el mismo pago recurrente cuesta más que antes.
    groupBy(d.spending.filter((e) => e.epochDay >= ep(d.today) - 400), (e) => Classifier.normalize(e.note)).forEach((l, key) => {
      if (key === '' || l.length < 3) return
      const sorted = sortedBy(l, (e) => e.epochDay)
      const last = sorted[sorted.length - 1]
      const before = average(takeLast(dropLast(sorted, 1), 3).map((e) => e.amount))
      if (last.epochDay >= since && before > 0 && last.amount > before * 1.05 && last.amount - before >= 1 &&
        dropLast(sorted, 1).every((e) => Math.abs(e.amount - before) <= before * 0.1)) {
        out.push({ kind: 'PRICE_UP', name: last.note, detail: `Ha subido de ${fmt(before)} a ${fmt(last.amount)}`, yearly: (last.amount - before) * 12, expense: last })
      }
    })
    // Una suscripción que además ha subido de precio sale una sola vez, con las dos cosas.
    const merged = [...groupBy(distinctBy(out, (x) => `${x.kind}|${Classifier.normalize(x.name)}`), (x) => Classifier.normalize(x.name)).values()].map((g) => {
      const sub = g.find((x) => x.kind === 'SUBSCRIPTION')
      const up = g.find((x) => x.kind === 'PRICE_UP')
      return sub && up ? { ...sub, detail: `${up.detail} · ¿la sigues usando?` } : g[0]
    })
    return sortedByDesc(merged, (x) => x.yearly)
  },
}

// ---------- 5. Detector de anomalías personales ----------

export interface Anomaly { title: string; detail: string; severe: boolean; expense: Expense | null }

export const Anomalies = {
  find(d: FinanceData, fmt: (v: number) => string): Anomaly[] {
    const out: Anomaly[] = []
    const month = d.thisMonth
    const day = d.today.day
    const len = ymLen(month)
    const closed = d.closedMonths(3)

    // Tipo de gasto muy por encima de tu mes normal (comparando al mismo ritmo del mes).
    if (closed.length > 0 && day >= 5) {
      const avg = new Map(byTag(closed.flatMap((m) => Budgeting.variable(d, m))).map(([t, v]) => [t, v / closed.length]))
      byTag(Budgeting.variable(d, month)).forEach(([t, now]) => {
        const usual = avg.get(t) ?? 0
        const expectedSoFar = usual * day / len
        if (usual > 20 && now > expectedSoFar * 2 && now - expectedSoFar > 30) out.push({
          title: `${SPEND_TAG_INFO[t].label}: el doble de lo normal`,
          detail: `Llevas ${fmt(now)} y a estas alturas sueles llevar ${fmt(expectedSoFar)} (tu mes normal: ${fmt(usual)}).`, severe: true, expense: null,
        })
      })
    }

    // Facturas que suben: el último pago de un gasto fijo cuesta más que lo apuntado o que los anteriores.
    d.bills.forEach((b) => {
      const paid = sortedBy(d.expenses.filter((e) => Budgeting.isBillPayment(e, [b]) || e.billId === b.id), (e) => e.epochDay)
      const last = paid[paid.length - 1]
      if (!last) return
      const prev = takeLast(dropLast(paid, 1), 3).map((e) => e.amount)
      const before = average(prev.length === 0 ? [b.amount] : prev)
      if (last.amount > before * 1.1 && last.amount - before >= 3) out.push({
        title: `${b.name} ha subido`, detail: `Último pago ${fmt(last.amount)} frente a ${fmt(before)} de media (+${Math.trunc(((last.amount / before) - 1) * 100)} %).`, severe: false, expense: last,
      })
    })

    // Pagos repetidos: mismo importe y comercio en menos de 3 días.
    const recent = sortedBy(d.expenses.filter((e) => e.epochDay >= ep(d.today) - 30), (e) => e.epochDay)
    recent.forEach((a, i) => {
      const b = recent.slice(i + 1).find((x) =>
        x.epochDay - a.epochDay <= 2 && Math.abs(x.amount - a.amount) <= Math.max(0.5, a.amount * 0.01) &&
        Classifier.normalize(a.note) === Classifier.normalize(x.note) && a.amount >= 5)
      if (b) out.push({ title: '¿Pago duplicado?', detail: `${b.note.trim() === '' ? 'Gasto' : b.note}: dos cargos de ${fmt(b.amount)} en ${b.epochDay - a.epochDay + 1} días.`, severe: true, expense: b })
    })

    // Gastas más rápido de lo que cobras.
    const income = d.monthlyIncome
    const spent = d.spentIn(month)
    if (income > 0 && day >= 7) {
      const pace = spent / day * len
      if (pace > income) out.push({
        title: 'Gastas más rápido de lo que cobras',
        detail: `A este ritmo acabarías el mes con ${fmt(pace)} gastados frente a ${fmt(income)} que entran.`, severe: true, expense: null,
      })
    }
    return distinctBy(out, (a) => a.title + a.detail)
  },
}

// ---------- 4. Simulador de compras ----------

export interface PurchaseOption { title: string; cost: number; goalDelayDays: number | null; perDayAfter: number | null; note: string }

export const PurchaseSimulator = {
  /** Comprar ahora, esperar un mes (pagándolo con lo que apartes) o una alternativa más barata. */
  compare(price: number, cheaper: number, d: FinanceData, plan: BankPlan | null, goals: GoalStatus[], fmt: (v: number) => string): PurchaseOption[] {
    const goal = goals.find((g) => !g.done)
    // Lo que tardas en volver a ahorrar ese dinero a tu ritmo, en días.
    const delay = (cost: number): number | null => (goal == null || goal.pace <= 0 ? null : Math.trunc(cost / goal.pace * 30.4))
    const perDay = (cost: number): number | null => (plan ? (plan.spendable - cost) / Math.max(1, plan.days) : null)
    const pNow = perDay(price)
    return [
      {
        title: 'Comprarlo ahora', cost: price, goalDelayDays: delay(price), perDayAfter: pNow,
        note: pNow == null ? '' : pNow < 0 ? `Te pasarías ${fmt(-pNow * Math.max(1, plan!.days))} de lo que puedes gastar este mes.` : `Te quedarían ${fmt(pNow)} al día hasta fin de mes.`,
      },
      {
        title: 'Esperar tres meses', cost: price, goalDelayDays: delay(price), perDayAfter: plan ? plan.spendable / Math.max(1, plan.days) : null,
        note: `Apartas ${fmt(price / 3)} al mes y lo compras sin tocar tu ahorro` + (goal != null ? '; tu objetivo se retrasa igual, pero sin quedarte justo.' : '.'),
      },
      { title: 'Alternativa más barata', cost: cheaper, goalDelayDays: delay(cheaper), perDayAfter: perDay(cheaper), note: `Te ahorras ${fmt(price - cheaper)}.` },
    ]
  },

  /** Días que se retrasa la fecha del objetivo si gastas `cost` hoy. */
  goalDelayText(days: number | null, goalName: string | null): string {
    if (days == null || goalName == null) return 'Sin objetivo para comparar'
    if (days <= 0) return `No retrasa "${goalName}"`
    if (days < 31) return `"${goalName}" llega ${days} días más tarde`
    const m = Math.trunc(days / 30)
    return `"${goalName}" llega ${m} ${m === 1 ? 'mes' : 'meses'} más tarde`
  },
}

/** Fecha en la que se completaría cada objetivo, recalculada con tus ingresos y gastos reales. */
export function monthsBetween(a: LocalDate, b: LocalDate): number { return monthsBetweenDates(withDayOfMonth(a, 1), withDayOfMonth(b, 1)) }
