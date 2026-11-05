package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

// ---------- 3. Detector de dinero invisible ----------

enum class LeakKind(val label: String) {
    SUBSCRIPTION("Suscripción"), FEE("Comisión"), REPEATED("Compra repetida"), PRICE_UP("Subida de precio"),
}

/** Una fuga pequeña con lo que cuesta al año; [expense] es un pago para revisarlo. */
data class Leak(val kind: LeakKind, val name: String, val detail: String, val yearly: Double, val expense: Expense?)

object InvisibleMoney {
    private val FEE_WORDS = listOf("comision", "comisiones", "cuota tarjeta", "cuota de mantenimiento", "mantenimiento cuenta", "descubierto", "intereses tarjeta", "fee", "cargo por")

    fun find(d: FinanceData, fmt: (Double) -> String): List<Leak> {
        val since = d.today.minusDays(120).toEpochDay()
        val recent = d.spending.filter { it.epochDay >= since }
        val out = mutableListOf<Leak>()

        // Suscripciones: gastos fijos y pagos que lo parecen. Se listan para que decidas si aún las usas.
        d.bills.filter { Classifier.guess(it.name) == SpendTag.SUBSCRIPTIONS }.forEach { b ->
            out += Leak(LeakKind.SUBSCRIPTION, b.name, "¿La sigues usando? ${fmt(Bills.monthly(b))} al mes", Bills.monthly(b) * 12, null)
        }
        recent.filter { it.spendTag() == SpendTag.SUBSCRIPTIONS && !Budgeting.isBillPayment(it, d.bills) }
            .groupBy { Classifier.normalize(it.note) }.values.forEach { l ->
                val last = l.maxBy { it.epochDay }
                if (out.none { Classifier.normalize(it.name) == Classifier.normalize(last.note) })
                    out += Leak(LeakKind.SUBSCRIPTION, last.note, "No está en tus gastos fijos · ${fmt(last.amount)} al mes", last.amount * 12, last)
            }

        // Comisiones bancarias.
        recent.filter { e -> Classifier.normalize(e.note).let { n -> FEE_WORDS.any { n.contains(it) } } }
            .groupBy { Classifier.normalize(it.note) }.values.forEach { l ->
                val perYear = l.sumOf { it.amount } * 365 / 120
                out += Leak(LeakKind.FEE, l.first().note, "${l.size} cargos en 4 meses: casi siempre se pueden quitar", perYear, l.maxBy { it.epochDay })
            }

        // Compras pequeñas repetidas (café, snacks, apps…): 4 o más en un mes y menos de 15 € cada una.
        recent.filter { it.amount < 15 && !Budgeting.isBillPayment(it, d.bills) }
            .groupBy { Classifier.normalize(it.note) }.filter { it.key.isNotEmpty() }.values.forEach { l ->
                val months = l.map { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }.distinct().size.coerceAtLeast(1)
                if (l.size.toDouble() / months >= 4) {
                    val perMonth = l.sumOf { it.amount } / months
                    out += Leak(LeakKind.REPEATED, l.first().note, "${l.size / months} veces al mes · ${fmt(perMonth)} al mes sin darte cuenta", perMonth * 12, l.maxBy { it.epochDay })
                }
            }

        // Subidas de precio: el mismo pago recurrente cuesta más que antes.
        d.spending.filter { it.epochDay >= d.today.minusDays(400).toEpochDay() }
            .groupBy { Classifier.normalize(it.note) }.filter { it.key.isNotEmpty() && it.value.size >= 3 }.values.forEach { l ->
                val sorted = l.sortedBy { it.epochDay }
                val last = sorted.last()
                val before = sorted.dropLast(1).takeLast(3).map { it.amount }.average()
                if (last.epochDay >= since && before > 0 && last.amount > before * 1.05 && last.amount - before >= 1 &&
                    sorted.dropLast(1).all { abs(it.amount - before) <= before * 0.1 }) {
                    out += Leak(LeakKind.PRICE_UP, last.note, "Ha subido de ${fmt(before)} a ${fmt(last.amount)}", (last.amount - before) * 12, last)
                }
            }
        // Una suscripción que además ha subido de precio sale una sola vez, con las dos cosas.
        return out.distinctBy { it.kind to Classifier.normalize(it.name) }
            .groupBy { Classifier.normalize(it.name) }.values.map { g ->
                val sub = g.firstOrNull { it.kind == LeakKind.SUBSCRIPTION }
                val up = g.firstOrNull { it.kind == LeakKind.PRICE_UP }
                if (sub != null && up != null) sub.copy(detail = "${up.detail} · ¿la sigues usando?") else g.first()
            }.sortedByDescending { it.yearly }
    }
}

// ---------- 5. Detector de anomalías personales ----------

data class Anomaly(val title: String, val detail: String, val severe: Boolean, val expense: Expense? = null)

object Anomalies {
    fun find(d: FinanceData, fmt: (Double) -> String): List<Anomaly> {
        val out = mutableListOf<Anomaly>()
        val month = d.thisMonth
        val day = d.today.dayOfMonth
        val len = month.lengthOfMonth()
        val closed = d.closedMonths(3)

        // Tipo de gasto muy por encima de tu mes normal (comparando al mismo ritmo del mes).
        if (closed.isNotEmpty() && day >= 5) {
            val avg = byTag(closed.flatMap { Budgeting.variable(d, it) }).associate { it.first to it.second / closed.size }
            byTag(Budgeting.variable(d, month)).forEach { (t, now) ->
                val usual = avg[t] ?: 0.0
                val expectedSoFar = usual * day / len
                if (usual > 20 && now > expectedSoFar * 2 && now - expectedSoFar > 30) out += Anomaly(
                    "${t.label}: el doble de lo normal",
                    "Llevas ${fmt(now)} y a estas alturas sueles llevar ${fmt(expectedSoFar)} (tu mes normal: ${fmt(usual)}).", true,
                )
            }
        }

        // Facturas que suben: el último pago de un gasto fijo cuesta más que lo apuntado o que los anteriores.
        d.bills.forEach { b ->
            val paid = d.expenses.filter { Budgeting.isBillPayment(it, listOf(b)) || it.billId == b.id }.sortedBy { it.epochDay }
            val last = paid.lastOrNull() ?: return@forEach
            val before = paid.dropLast(1).takeLast(3).map { it.amount }.ifEmpty { listOf(b.amount) }.average()
            if (last.amount > before * 1.1 && last.amount - before >= 3) out += Anomaly(
                "${b.name} ha subido", "Último pago ${fmt(last.amount)} frente a ${fmt(before)} de media (+${(((last.amount / before) - 1) * 100).toInt()} %).", false, last,
            )
        }

        // Pagos repetidos: mismo importe y comercio en menos de 3 días.
        val recent = d.expenses.filter { it.epochDay >= d.today.minusDays(30).toEpochDay() }.sortedBy { it.epochDay }
        recent.forEachIndexed { i, a ->
            recent.drop(i + 1).firstOrNull { b ->
                b.epochDay - a.epochDay <= 2 && abs(b.amount - a.amount) <= max(0.5, a.amount * 0.01) &&
                    Classifier.normalize(a.note) == Classifier.normalize(b.note) && a.amount >= 5
            }?.let { b -> out += Anomaly("¿Pago duplicado?", "${b.note.ifBlank { "Gasto" }}: dos cargos de ${fmt(b.amount)} en ${b.epochDay - a.epochDay + 1} días.", true, b) }
        }

        // Gastas más rápido de lo que cobras.
        val income = d.monthlyIncome
        val spent = d.spentIn(month)
        if (income > 0 && day >= 7) {
            val pace = spent / day * len
            if (pace > income) out += Anomaly(
                "Gastas más rápido de lo que cobras",
                "A este ritmo acabarías el mes con ${fmt(pace)} gastados frente a ${fmt(income)} que entran.", true,
            )
        }
        return out.distinctBy { it.title + it.detail }
    }
}

// ---------- 4. Simulador de compras ----------

data class PurchaseOption(val title: String, val cost: Double, val goalDelayDays: Int?, val perDayAfter: Double?, val note: String)

object PurchaseSimulator {
    /** Comprar ahora, esperar un mes (pagándolo con lo que apartes) o una alternativa más barata. */
    fun compare(price: Double, cheaper: Double, d: FinanceData, plan: BankPlan?, goals: List<GoalStatus>, fmt: (Double) -> String): List<PurchaseOption> {
        val goal = goals.firstOrNull { !it.done }
        // Lo que tardas en volver a ahorrar ese dinero a tu ritmo, en días.
        fun delay(cost: Double): Int? = if (goal == null || goal.pace <= 0) null else (cost / goal.pace * 30.4).toInt()
        fun perDay(cost: Double) = plan?.let { p -> (p.spendable - cost) / max(1, p.days) }
        return listOf(
            PurchaseOption("Comprarlo ahora", price, delay(price), perDay(price),
                perDay(price)?.let { if (it < 0) "Te pasarías ${fmt(-it * max(1, plan!!.days))} de lo que puedes gastar este mes." else "Te quedarían ${fmt(it)} al día hasta fin de mes." } ?: ""),
            PurchaseOption("Esperar tres meses", price, delay(price), plan?.let { p -> p.spendable / max(1, p.days) },
                "Apartas ${fmt(price / 3)} al mes y lo compras sin tocar tu ahorro" + (if (goal != null) "; tu objetivo se retrasa igual, pero sin quedarte justo." else ".")),
            PurchaseOption("Alternativa más barata", cheaper, delay(cheaper), perDay(cheaper), "Te ahorras ${fmt(price - cheaper)}."),
        )
    }

    /** Días que se retrasa la fecha del objetivo si gastas [cost] hoy. */
    fun goalDelayText(days: Int?, goalName: String?): String = when {
        days == null || goalName == null -> "Sin objetivo para comparar"
        days <= 0 -> "No retrasa \"$goalName\""
        days < 31 -> "\"$goalName\" llega $days días más tarde"
        else -> "\"$goalName\" llega ${days / 30} ${if (days / 30 == 1) "mes" else "meses"} más tarde"
    }
}

/** Fecha en la que se completaría cada objetivo, recalculada con tus ingresos y gastos reales. */
fun monthsBetween(a: LocalDate, b: LocalDate) = ChronoUnit.MONTHS.between(a.withDayOfMonth(1), b.withDayOfMonth(1)).toInt()
