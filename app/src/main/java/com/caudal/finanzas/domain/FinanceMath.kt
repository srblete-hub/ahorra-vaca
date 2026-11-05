package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.CryptoHolding
import com.caudal.finanzas.data.StockHolding
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/**
 * Lo que tienes ahora mismo, separado: disponible para el día a día, ahorro (lo guardado y lo que
 * da intereses) e invertido (cripto y acciones a precio de mercado).
 */
data class Wealth(val available: Double, val savings: Double, val invested: Double, val debts: Double = 0.0) {
    /** Patrimonio neto: lo que tienes menos lo que debes. */
    val total get() = available + savings + invested - debts
    /** Lo construido a largo plazo (ahorro + inversiones − deudas), frente a la liquidez del día a día. */
    val built get() = savings + invested - debts

    companion object {
        fun of(accounts: List<Account>, crypto: List<CryptoHolding>, stocks: List<StockHolding>) = Wealth(
            available = accounts.filter { !it.isSaving() && !it.isDebt() }.sumOf { it.balance },
            savings = accounts.filter { it.isSaving() }.sumOf { it.balance },
            invested = crypto.sumOf { it.amount * it.price } + stocks.sumOf { it.shares * it.price },
            debts = accounts.filter { it.isDebt() }.sumOf { it.balance },
        )
    }
}

private val SavingTypes = setOf(AccountType.BANK_SAVINGS, AccountType.HOME_SAVINGS, AccountType.FUND)

/** Es ahorro si da intereses o si es un sitio de guardar (cuenta de ahorro, hucha, fondo). */
fun Account.isSaving(): Boolean = !isDebt() && (interestRate > 0 || type in SavingTypes)

fun Account.isDebt(): Boolean = type == AccountType.DEBT

/** Cuánto suma al patrimonio: las deudas restan. */
fun Account.signedBalance(): Double = if (isDebt()) -balance else balance

/** Intereses de una cuenta remunerada. [annualRatePct] es la TAE en % (5.0 = 5 %). */
object InterestMath {
    /** Tipo mensual equivalente a la TAE: (1 + TAE)^(1/12) − 1. */
    fun monthlyRate(annualRatePct: Double): Double = (1 + annualRatePct / 100).pow(1.0 / 12) - 1

    /** Intereses de un mes si el saldo no se toca. */
    fun monthlyInterest(balance: Double, annualRatePct: Double): Double = balance * monthlyRate(annualRatePct)

    data class Projection(val months: Int, val finalBalance: Double, val contributed: Double, val interest: Double) {
        /** Lo que daría de intereses al mes con el saldo final. */
        fun monthlyInterestAtEnd(annualRatePct: Double) = monthlyInterest(finalBalance, annualRatePct)
    }

    /** Saldo tras [months] meses metiendo [monthly] € al final de cada mes, con interés compuesto mensual. */
    fun project(balance: Double, annualRatePct: Double, monthly: Double, months: Int): Projection {
        val r = monthlyRate(annualRatePct)
        val growth = (1 + r).pow(months)
        val fv = if (r == 0.0) balance + monthly * months else balance * growth + monthly * (growth - 1) / r
        val contributed = monthly * months
        return Projection(months, fv, contributed, fv - balance - contributed)
    }
}

/** Ingreso de un mes con lo fijo más la media de lo variable, corregido con lo ya cobrado. */
object IncomeMath {
    data class SourceMonth(
        val source: IncomeSource,
        /** Lo que se cuenta para el presupuesto: lo cobrado, o lo esperado si aún no ha llegado. */
        val amount: Double,
        val received: Double,
        val isEstimate: Boolean,
        /** Media de los últimos meses (solo variables con historial). */
        val average: Double?,
    ) {
        val isReceived get() = received > 0
        /** Importe que confirmaste para este mes (null = se estima). */
        var confirmed: Double? = null
    }

    /** Importes confirmados por ti: "idFuente@2026-11" → €. Lo rellena el ViewModel desde los ajustes. */
    @Volatile var confirmed: Map<String, Double> = emptyMap()
    fun key(sourceId: Long, month: YearMonth) = "$sourceId@$month"
    fun confirmedFor(source: IncomeSource, month: YearMonth): Double? = if (source.variable) confirmed[key(source.id, month)] else null

    data class MonthIncome(val total: Double, val received: Double, val sources: List<SourceMonth>, val extra: Double)

    private fun YearMonth.contains(epochDay: Long) = LocalDate.ofEpochDay(epochDay).let { YearMonth.from(it) == this }

    /** Media mensual de lo cobrado de [source] en los [lookback] meses anteriores a [month] que tengan cobros. */
    fun variableAverage(source: IncomeSource, incomes: List<Income>, month: YearMonth, lookback: Int = 3): Double? {
        val totals = (1..lookback).map { month.minusMonths(it.toLong()) }
            .map { m -> incomes.filter { it.sourceId == source.id && m.contains(it.epochDay) }.sumOf { it.amount } }
            .filter { it > 0 }
        return if (totals.isEmpty()) null else totals.average()
    }

    fun forMonth(sources: List<IncomeSource>, incomes: List<Income>, month: YearMonth): MonthIncome {
        val perSource = sources.map { s ->
            val received = incomes.filter { it.sourceId == s.id && month.contains(it.epochDay) }.sumOf { it.amount }
            val average = if (s.variable) variableAverage(s, incomes, month) else null
            val conf = confirmedFor(s, month)
            val expected = conf ?: if (s.variable) average ?: s.amount else s.amount
            SourceMonth(s, if (received > 0) received else expected, received, isEstimate = received <= 0 && s.variable && conf == null, average = average).also { it.confirmed = conf }
        }
        // Cobros sin fuente (intereses, extras): suman a lo cobrado pero no al presupuesto previsto.
        val extra = incomes.filter { it.sourceId == null && month.contains(it.epochDay) }.sumOf { it.amount }
        return MonthIncome(perSource.sumOf { it.amount }, perSource.sumOf { it.received } + extra, perSource, extra)
    }
}

/** Reparto del ingreso: cambiar un % reajusta el resto para que siempre sume 100. */
object Allocation {
    private fun r1(v: Double) = round(v * 10) / 10

    /**
     * Pone [id] a [newPercent] y reparte la diferencia entre las demás categorías sin candado,
     * en proporción a su peso. Si las demás no pueden absorberla, [newPercent] se recorta.
     */
    fun rebalance(categories: List<Category>, id: Long, newPercent: Double): List<Category> {
        val target = categories.firstOrNull { it.id == id } ?: return categories
        val others = categories.filter { it.id != id && !it.locked }
        val lockedSum = categories.filter { it.id != id && it.locked }.sumOf { it.percent }
        val available = (100 - lockedSum).coerceAtLeast(0.0)
        val value = r1(newPercent.coerceIn(0.0, if (others.isEmpty()) target.percent else available))
        val rest = available - value
        val othersSum = others.sumOf { it.percent }
        val newOthers = others.associate { c ->
            c.id to if (othersSum > 0) rest * c.percent / othersSum else rest / others.size
        }.mapValues { r1(it.value) }.toMutableMap()
        // Corrige el redondeo para que el total sea exactamente 100.
        val drift = 100 - lockedSum - value - newOthers.values.sum()
        if (newOthers.isNotEmpty() && abs(drift) > 0.001) {
            val biggest = newOthers.maxBy { it.value }.key
            newOthers[biggest] = r1(newOthers.getValue(biggest) + drift)
        }
        return categories.map { c ->
            when {
                c.id == id -> c.copy(percent = value)
                c.id in newOthers -> c.copy(percent = newOthers.getValue(c.id))
                else -> c
            }
        }
    }

    enum class Preset(val label: String, val needs: Double, val wants: Double, val savings: Double) {
        BALANCED("Equilibrado", 50.0, 30.0, 20.0),
        SAVER("Priorizar ahorro", 50.0, 15.0, 35.0),
        MAX("Ahorro máximo", 45.0, 5.0, 50.0),
    }

    /** Aplica un atajo: la primera categoría de gasto son las "necesidades", el resto de gasto es ocio. */
    fun applyPreset(categories: List<Category>, preset: Preset): List<Category> {
        val savings = categories.filter { it.isSavings }
        val spending = categories.filter { !it.isSavings }
        val needs = spending.firstOrNull { it.name.contains("necesidad", true) } ?: spending.firstOrNull()
        val wants = spending.filter { it.id != needs?.id }
        fun split(list: List<Category>, total: Double): Map<Long, Double> {
            if (list.isEmpty()) return emptyMap()
            val sum = list.sumOf { it.percent }
            return list.associate { it.id to r1(if (sum > 0) total * it.percent / sum else total / list.size) }
        }
        val values = buildMap {
            needs?.let { put(it.id, if (wants.isEmpty()) preset.needs + preset.wants else preset.needs) }
            putAll(split(wants, preset.wants))
            putAll(split(savings, if (savings.isEmpty()) 0.0 else preset.savings))
        }
        val result = categories.map { c -> values[c.id]?.let { c.copy(percent = it) } ?: c }
        val drift = 100 - result.sumOf { it.percent }
        return if (abs(drift) > 0.001 && result.isNotEmpty()) {
            val biggest = result.maxBy { it.percent }
            result.map { if (it.id == biggest.id) it.copy(percent = r1(it.percent + drift)) else it }
        } else result
    }
}

/**
 * Intereses ganados de verdad por una cuenta remunerada, con interés compuesto mensual:
 * - si se abonan solos, la suma de los abonos + lo que lleva acumulado este mes;
 * - si no, cada ingreso (transferencia) cuenta desde su fecha: 200 € el primer mes y 250 € el segundo
 *   generan intereses distintos. El dinero que ya había al empezar cuenta desde que usas la app.
 */
object InterestEarned {
    data class Result(val earned: Double, val estimated: Boolean, val monthlyNow: Double)

    fun of(
        account: Account,
        incomes: List<Income>,
        transfers: List<com.caudal.finanzas.data.Transfer>,
        today: LocalDate,
        since: LocalDate,
    ): Result {
        val r = InterestMath.monthlyRate(account.interestRate)
        val monthlyNow = account.balance * r
        if (account.interestRate <= 0) return Result(0.0, false, 0.0)
        val partOfMonth = (today.dayOfMonth - 1).toDouble() / today.lengthOfMonth()
        if (account.autoInterest) {
            val credited = incomes.filter { it.accountId == account.id && it.sourceId == null && it.note.startsWith("Intereses") }.sumOf { it.amount }
            return Result(credited + monthlyNow * partOfMonth, false, monthlyNow)
        }
        val ins = transfers.filter { it.toAccountId == account.id }
        val outs = transfers.filter { it.fromAccountId == account.id }
        fun months(day: Long) = max(0.0, (today.toEpochDay() - day) / 30.4375)
        fun grown(amount: Double, day: Long) = amount * ((1 + r).pow(months(day)) - 1)
        // Lo que había antes de los movimientos apuntados, desde que empezaste a usar la app.
        val start = max(0.0, account.balance - ins.sumOf { it.amount } + outs.sumOf { it.amount })
        val earned = grown(start, since.toEpochDay()) + ins.sumOf { grown(it.amount, it.epochDay) } - outs.sumOf { grown(it.amount, it.epochDay) }
        return Result(max(0.0, earned), true, monthlyNow)
    }
}
