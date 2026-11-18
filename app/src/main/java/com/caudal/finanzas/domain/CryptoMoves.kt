package com.caudal.finanzas.domain

import com.caudal.finanzas.data.CryptoHolding
import kotlin.math.abs

/** Un movimiento fuerte de una cripto que tienes: cuánto ha cambiado y cuánto vale ahora lo tuyo. */
data class CryptoMove(val name: String, val symbol: String, val change: Double, val price: Double, val value: Double, val delta: Double)

/**
 * Detecta subidas o bajadas fuertes (por defecto ±5 %) frente al último precio de referencia.
 * Tras avisar, la referencia pasa a ser el precio nuevo: así no avisa cada hora por el mismo movimiento.
 */
object CryptoMoves {
    fun check(
        holdings: List<CryptoHolding>, prices: Map<String, Double>, refs: Map<String, Double>, threshold: Double = 0.05,
    ): Pair<List<CryptoMove>, Map<String, Double>> {
        val newRefs = refs.toMutableMap()
        val moves = mutableListOf<CryptoMove>()
        holdings.groupBy { it.coinId }.forEach { (id, list) ->
            val price = prices[id]?.takeIf { it > 0 } ?: return@forEach
            val ref = refs[id]
            if (ref == null || ref <= 0) { newRefs[id] = price; return@forEach }
            val change = (price - ref) / ref
            if (abs(change) >= threshold) {
                val amount = list.sumOf { it.amount }
                moves += CryptoMove(list.first().name, list.first().symbol, change, price, amount * price, amount * (price - ref))
                newRefs[id] = price
            }
        }
        return moves to newRefs
    }

    fun encode(refs: Map<String, Double>) = refs.entries.joinToString(";") { "${it.key}=${it.value}" }
    fun decode(raw: String?): Map<String, Double> =
        raw.orEmpty().split(';').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toDoubleOrNull()?.let { k to it } } }.toMap()
}
