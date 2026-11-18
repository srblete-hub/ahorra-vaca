// Port de domain/Debts.kt: dinero entre amigos y saldo de Tricount.
import { distinctBy, ktNum, sortedByDesc, sumOf, toDoubleOrNull, toLongOrNull } from './kt'

/**
 * Dinero entre amigos. `amount` > 0: te lo deben; < 0: lo debes tú.
 * `inTricount`: también está apuntado en Tricount (para no contarlo dos veces).
 */
export interface Debt {
  id: number
  person: string
  amount: number
  note: string
  epochDay: number
  expenseId: number | null
  inTricount: boolean
  settled: boolean
}

/** Crea una deuda con los mismos valores por defecto que en Kotlin. */
export function makeDebt(id: number, person: string, amount: number, note: string, epochDay: number, expenseId: number | null = null, inTricount = false, settled = false): Debt {
  return { id, person, amount, note, epochDay, expenseId, inTricount, settled }
}

/** Lo que dice Tricount con esa persona (> 0: te debe), y cuándo lo pusiste. */
export interface TricountBalance { amount: number; epochDay: number }

export interface FriendBalance { person: string; net: number; tricount: TricountBalance | null; open: Debt[] }

/** Saldos de Tricount por persona (Map<String, TricountBalance> en Kotlin; se conserva el orden). */
export type TricountMap = Record<string, TricountBalance>

const clean = (s: string) => s.replace(/\|/g, '/').replace(/\n/g, ' ').replace(/;/g, ',')
const eqIgnoreCase = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()

export const Debts = {
  parse(raw: string): Debt[] {
    const out: Debt[] = []
    for (const l of raw.split(/\r\n|\n|\r/)) {
      const p = l.split('|')
      if (p.length < 8) continue
      const id = toLongOrNull(p[0]); const amount = toDoubleOrNull(p[2]); const day = toLongOrNull(p[4])
      if (id == null || amount == null || day == null) continue
      out.push({ id, person: p[1], amount, note: p[3], epochDay: day, expenseId: toLongOrNull(p[5]), inTricount: p[6] === '1', settled: p[7] === '1' })
    }
    return out
  },

  format(list: Debt[]): string {
    return list.map((d) => [d.id, clean(d.person), ktNum(d.amount), clean(d.note), d.epochDay, d.expenseId ?? '', d.inTricount ? 1 : 0, d.settled ? 1 : 0].join('|')).join('\n')
  },

  parseTricount(raw: string): TricountMap {
    const out: TricountMap = {}
    for (const e of raw.split(';')) {
      const p = e.split('|')
      if (p.length < 3) continue
      const a = toDoubleOrNull(p[1]); const d = toLongOrNull(p[2])
      if (a != null && d != null) out[p[0]] = { amount: a, epochDay: d }
    }
    return out
  },

  formatTricount(m: TricountMap): string {
    return Object.entries(m).map(([k, v]) => `${clean(k)}|${ktNum(v.amount)}|${v.epochDay}`).join(';')
  },

  /**
   * Saldo con cada persona. Si hay saldo de Tricount, manda Tricount: los apuntes marcados "en Tricount"
   * de antes de ese saldo ya están dentro y no se suman otra vez; los de fuera de Tricount sí se suman.
   */
  balances(debts: Debt[], tricount: TricountMap): FriendBalance[] {
    const open = debts.filter((d) => !d.settled)
    const people = distinctBy([...open.map((d) => d.person), ...Object.entries(tricount).filter(([, v]) => Math.abs(v.amount) > 0.005).map(([k]) => k)], (s) => s.toLowerCase())
    return sortedByDesc(people.map((name) => {
      const t = Object.entries(tricount).find(([k]) => eqIgnoreCase(k, name))?.[1] ?? null
      const mine = sortedByDesc(open.filter((d) => eqIgnoreCase(d.person, name)), (d) => d.epochDay)
      const counted = mine.filter((d) => !(d.inTricount && t != null && d.epochDay <= t.epochDay))
      return { person: name, net: (t?.amount ?? 0) + sumOf(counted, (d) => d.amount), tricount: t, open: mine }
    }), (b) => Math.abs(b.net))
  },

  /** Personas con las que ya tienes cuentas, la más reciente primero. */
  people(debts: Debt[], tricount: TricountMap): string[] {
    return distinctBy([...sortedByDesc(debts, (d) => d.epochDay).map((d) => d.person), ...Object.keys(tricount)].filter((s) => s.trim() !== ''), (s) => s.toLowerCase())
  },
}
