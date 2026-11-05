package com.caudal.finanzas.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Dónde está el dinero. [inBank] separa "en el banco" de "guardado en otro sitio". */
enum class AccountType(val label: String, val inBank: Boolean) {
    BANK("Cuenta bancaria", true),
    BANK_SAVINGS("Ahorro en el banco", true),
    CASH("Efectivo", false),
    HOME_SAVINGS("Hucha / ahorro en casa", false),
    WALLET("PayPal / monedero digital", false),
    FUND("Fondo o plan de pensiones", false),
    OTHER("Otro sitio", false),
    /** Lo que debes (préstamo, tarjeta de crédito…): el saldo es lo pendiente y resta del patrimonio. */
    DEBT("Préstamo o deuda", false),
}

/**
 * [interestRate] es la TAE en % (0 = no da intereses). Con [autoInterest], cada mes se suman los
 * intereses al saldo; [interestFromMonth] es el último mes ya abonado (año·12 + mes − 1).
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val balance: Double,
    val note: String = "",
    @ColumnInfo(defaultValue = "0") val interestRate: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val autoInterest: Boolean = false,
    @ColumnInfo(defaultValue = "0") val interestFromMonth: Int = 0,
)

/** Parte del ingreso mensual que se asigna a algo. Las de ahorro no cuentan como "gastable". */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val percent: Double,
    val isSavings: Boolean = false,
    /** Con candado no cambia cuando se mueve otro porcentaje. */
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
)

/** Algo que te paga cada mes: nómina (fija) o autónomo / YouTube (variable), entre los días [dayFrom] y [dayTo]. */
@Entity(tableName = "income_sources")
data class IncomeSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val variable: Boolean,
    /** Fija: lo que cobras. Variable: tu estimación hasta que haya meses cobrados para hacer media. */
    val amount: Double,
    val dayFrom: Int,
    val dayTo: Int,
    val accountId: Long? = null,
)

/** Un cobro. Si tiene [accountId], está sumado al saldo de esa cuenta. Sin [sourceId]: intereses o extras. */
@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long?,
    val amount: Double,
    val note: String,
    val epochDay: Long,
    val accountId: Long?,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Un pago. Si tiene [accountId], su importe está descontado del saldo de esa cuenta.
 * [fromCard] marca los apuntados solos a partir de la notificación de [sourceApp].
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amount: Double,
    val note: String = "",
    val epochDay: Long,
    val accountId: Long? = null,
    @ColumnInfo(defaultValue = "0") val fromCard: Boolean = false,
    @ColumnInfo(defaultValue = "") val sourceApp: String = "",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    /** Si es el pago de un gasto fijo, cuál. */
    val billId: Long? = null,
    /** Tipo de gasto (nombre de [com.caudal.finanzas.domain.SpendTag]); vacío = se deduce de la descripción. */
    @ColumnInfo(defaultValue = "") val tag: String = "",
    /** Texto de la notificación de la que se apuntó solo (vacío si lo apuntaste tú). */
    @ColumnInfo(defaultValue = "") val rawText: String = "",
)

/** Un gasto repartido entre [people] personas (tú incluido); [settled] de los demás ya te han pagado. */
@Entity(tableName = "splits")
data class Split(
    @PrimaryKey val expenseId: Long,
    val people: Int,
    val settled: Int = 0,
    val note: String = "",
)

/**
 * Gasto fijo que se repite: alquiler, suscripciones, seguros… Mensual el día [day], o anual
 * el día [day] del mes [month] (1–12). Un mes está pagado si hay un gasto con su [id].
 */
@Entity(tableName = "bills")
data class RecurringBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val day: Int,
    val yearly: Boolean = false,
    val month: Int = 1,
    val categoryId: Long,
    val accountId: Long? = null,
)

/** Dinero movido de una cuenta a otra (no es gasto ni ingreso: el patrimonio no cambia). */
@Entity(tableName = "transfers")
data class Transfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Double,
    val note: String = "",
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Patrimonio neto de cada día, para el gráfico de evolución. */
@Entity(tableName = "snapshots")
data class NetWorthSnapshot(
    @PrimaryKey val epochDay: Long,
    val total: Double,
)

/** [coinId] es el id de CoinGecko (p. ej. "bitcoin"); los precios van en la moneda de la app. */
@Entity(tableName = "crypto")
data class CryptoHolding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coinId: String,
    val symbol: String,
    val name: String,
    val amount: Double,
    val avgBuyPrice: Double,
    val price: Double = 0.0,
    val updatedAt: Long = 0,
)

/** [ticker] con el formato de Yahoo: AAPL, SAN.MC, VWCE.DE… */
@Entity(tableName = "stocks")
data class StockHolding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val name: String,
    val shares: Double,
    val avgBuyPrice: Double,
    val price: Double = 0.0,
    val updatedAt: Long = 0,
)
