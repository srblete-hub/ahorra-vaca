package com.caudal.finanzas.domain

import kotlin.math.abs

/**
 * Dinero entre amigos. [amount] > 0: te lo deben; < 0: lo debes tú.
 * [inTricount]: también está apuntado en Tricount (para no contarlo dos veces).
 */
data class Debt(
    val id: Long,
    val person: String,
    val amount: Double,
    val note: String,
    val epochDay: Long,
    val expenseId: Long? = null,
    val inTricount: Boolean = false,
    val settled: Boolean = false,
)

/** Lo que dice Tricount con esa persona (> 0: te debe), y cuándo lo pusiste. */
data class TricountBalance(val amount: Double, val epochDay: Long)

data class FriendBalance(val person: String, val net: Double, val tricount: TricountBalance?, val open: List<Debt>)

object Debts {
    private fun clean(s: String) = s.replace("|", "/").replace("\n", " ").replace(";", ",")

    fun parse(raw: String): List<Debt> = raw.lines().mapNotNull { l ->
        val p = l.split('|')
        if (p.size < 8) null else runCatching {
            Debt(p[0].toLong(), p[1], p[2].toDouble(), p[3], p[4].toLong(), p[5].toLongOrNull(), p[6] == "1", p[7] == "1")
        }.getOrNull()
    }

    fun format(list: List<Debt>): String = list.joinToString("\n") {
        listOf(it.id, clean(it.person), it.amount, clean(it.note), it.epochDay, it.expenseId ?: "", if (it.inTricount) 1 else 0, if (it.settled) 1 else 0).joinToString("|")
    }

    fun parseTricount(raw: String): Map<String, TricountBalance> = raw.split(';').mapNotNull { e ->
        val p = e.split('|')
        if (p.size < 3) null else p[1].toDoubleOrNull()?.let { a -> p[2].toLongOrNull()?.let { d -> p[0] to TricountBalance(a, d) } }
    }.toMap()

    fun formatTricount(m: Map<String, TricountBalance>): String = m.entries.joinToString(";") { "${clean(it.key)}|${it.value.amount}|${it.value.epochDay}" }

    /**
     * Saldo con cada persona. Si hay saldo de Tricount, manda Tricount: los apuntes marcados "en Tricount"
     * de antes de ese saldo ya están dentro y no se suman otra vez; los de fuera de Tricount sí se suman.
     */
    fun balances(debts: List<Debt>, tricount: Map<String, TricountBalance>): List<FriendBalance> {
        val open = debts.filter { !it.settled }
        val people = (open.map { it.person } + tricount.filter { abs(it.value.amount) > 0.005 }.keys).distinctBy { it.lowercase() }
        return people.map { name ->
            val t = tricount.entries.firstOrNull { it.key.equals(name, true) }?.value
            val mine = open.filter { it.person.equals(name, true) }.sortedByDescending { it.epochDay }
            val counted = mine.filter { !(it.inTricount && t != null && it.epochDay <= t.epochDay) }
            FriendBalance(name, (t?.amount ?: 0.0) + counted.sumOf { it.amount }, t, mine)
        }.sortedByDescending { abs(it.net) }
    }

    /** Personas con las que ya tienes cuentas, la más reciente primero. */
    fun people(debts: List<Debt>, tricount: Map<String, TricountBalance>): List<String> =
        (debts.sortedByDescending { it.epochDay }.map { it.person } + tricount.keys).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
}
