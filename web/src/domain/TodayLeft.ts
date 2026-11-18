// Port de domain/TodayLeft.kt: lo que te queda hoy, texto del aviso tras un pago y recibos que vienen.
import type { LocalDate } from '../data/dates'
import { daysBetween, dow, ep, isAfter, isBefore, plusD, sortedBy, sumOf, weekdayName, ymLen, ymOfDate } from './kt'
import { lengthOfMonth } from '../data/dates'
import { Classifier, SPEND_TAG_INFO } from './Spend'
import type { SpendTag } from './Spend'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'
import type { CashPlan } from './CashPlan'

/** Lo que te queda por gastar hoy: lo que toca al día menos lo gastado hoy (sin alquiler ni fijos). */
export const TodayLeft = {
  spentToday(d: FinanceData): number {
    return sumOf(d.spending.filter((e) => e.epochDay === ep(d.today) && !Budgeting.isFixed(e, d.bills)), (e) => e.amount)
  },

  /** [lo que toca hoy, lo que queda] o null sin plan del banco. */
  of(d: FinanceData, cash: CashPlan | null): [number, number] | null {
    const perDay = cash?.perDayBefore
    if (perDay == null || !(perDay > 0)) return null
    return [perDay, perDay - TodayLeft.spentToday(d)]
  },

  /** Texto de la notificación tras un pago apuntado solo: [título, texto]. */
  receipt(note: string, amount: number, tag: SpendTag | null, spentToday: number, perDay: number, fmt: (v: number) => string): [string, string] {
    const left = perDay - spentToday
    const title = `Apuntado: ${note} · ${fmt(amount)}`
    const text = (tag != null && tag !== 'OTHER' ? `${SPEND_TAG_INFO[tag].label}. ` : '') +
      (left >= 0 ? `Hoy te quedan ${fmt(left)} de ${fmt(perDay)}.` : `Hoy te has pasado ${fmt(-left)}: se descuenta de los próximos días.`)
    return [title, text]
  },
}

/** Un recibo que pasa en los próximos días. */
export interface UpcomingBill { name: string; amount: number; date: LocalDate }

/** UpcomingBill.whenText(today): "hoy", "mañana" o "el viernes 20". */
export function upcomingWhenText(b: UpcomingBill, today: LocalDate): string {
  switch (daysBetween(today, b.date)) {
    case 0: return 'hoy'
    case 1: return 'mañana'
    default: return 'el ' + weekdayName(dow(b.date)) + ` ${b.date.day}`
  }
}

/** Recibos fijos que se cobran en los próximos `days` días y que aún no has pagado este mes. */
export const UpcomingBills = {
  of(d: FinanceData, days = 3): UpcomingBill[] {
    const today = d.today
    const m = ymOfDate(today)
    const paid = d.spendingIn(m).map((e) => Classifier.normalize(e.note))
    const out: UpcomingBill[] = []
    for (const b of d.bills) {
      let date: LocalDate | null
      if (b.yearly) {
        date = b.month >= 1 && b.month <= 12 && b.day >= 1 ? { year: today.year, month: b.month, day: Math.min(b.day, lengthOfMonth(today.year, b.month)) } : null
      } else {
        const day = Math.min(b.day, ymLen(m))
        date = day >= 1 ? { year: today.year, month: today.month, day } : null
      }
      const n = Classifier.normalize(b.name)
      if (date == null || isBefore(date, today) || isAfter(date, plusD(today, days))) continue
      if (paid.some((p) => n.trim() !== '' && (p.includes(n) || n.includes(p.length >= 4 ? p : '\u0000')))) continue
      out.push({ name: b.name, amount: b.amount, date })
    }
    return sortedBy(out, (b) => ep(b.date))
  },
}
