// Port de domain/CryptoMoves.kt: avisos de movimientos fuertes de tus criptos.
import type { CryptoHolding } from '../data/models'
import { groupBy, ktNum, sumOf, toDoubleOrNull } from './kt'

/** Un movimiento fuerte de una cripto que tienes: cuánto ha cambiado y cuánto vale ahora lo tuyo. */
export interface CryptoMove { name: string; symbol: string; change: number; price: number; value: number; delta: number }

/**
 * Detecta subidas o bajadas fuertes (por defecto ±5 %) frente al último precio de referencia.
 * Tras avisar, la referencia pasa a ser el precio nuevo: así no avisa cada hora por el mismo movimiento.
 */
export const CryptoMoves = {
  /** Devuelve [movimientos, nuevas referencias] (Pair en Kotlin). */
  check(holdings: CryptoHolding[], prices: Record<string, number>, refs: Record<string, number>, threshold = 0.05): [CryptoMove[], Record<string, number>] {
    const newRefs: Record<string, number> = { ...refs }
    const moves: CryptoMove[] = []
    groupBy(holdings, (h) => h.coinId).forEach((list, id) => {
      const price = prices[id]
      if (price == null || !(price > 0)) return
      const ref = refs[id]
      if (ref == null || ref <= 0) { newRefs[id] = price; return }
      const change = (price - ref) / ref
      if (Math.abs(change) >= threshold) {
        const amount = sumOf(list, (h) => h.amount)
        moves.push({ name: list[0].name, symbol: list[0].symbol, change, price, value: amount * price, delta: amount * (price - ref) })
        newRefs[id] = price
      }
    })
    return [moves, newRefs]
  },

  encode(refs: Record<string, number>): string { return Object.entries(refs).map(([k, v]) => `${k}=${ktNum(v)}`).join(';') },
  decode(raw: string | null | undefined): Record<string, number> {
    const out: Record<string, number> = {}
    for (const e of (raw ?? '').split(';')) {
      const kv = e.split('=')
      if (kv.length !== 2) continue
      const v = toDoubleOrNull(kv[1])
      if (v != null) out[kv[0]] = v
    }
    return out
  },
}
