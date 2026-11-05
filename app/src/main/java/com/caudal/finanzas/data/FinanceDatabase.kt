package com.caudal.finanzas.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY type, name")
    fun accounts(): Flow<List<Account>>

    @Upsert suspend fun upsertAccount(account: Account)
    @Delete suspend fun deleteAccount(account: Account)

    @Query("SELECT * FROM categories ORDER BY isSavings, id")
    fun categories(): Flow<List<Category>>

    @Upsert suspend fun upsertCategory(category: Category)
    @Delete suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM expenses WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, id DESC")
    fun expenses(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY epochDay DESC, createdAt DESC LIMIT :limit")
    fun recentExpenses(limit: Int): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: Long): Expense?

    @Upsert suspend fun upsertExpense(expense: Expense)
    @Delete suspend fun deleteExpense(expense: Expense)

    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, delta: Double)

    /** Guarda el gasto y mueve el saldo: devuelve el importe antiguo a su cuenta y descuenta el nuevo. */
    @Transaction
    suspend fun saveExpense(expense: Expense) {
        val old = if (expense.id != 0L) expense(expense.id) else null
        if (old?.accountId != null) adjustBalance(old.accountId, old.amount)
        upsertExpense(expense)
        if (expense.accountId != null) adjustBalance(expense.accountId, -expense.amount)
    }

    /** Borra el gasto y devuelve el dinero a la cuenta de la que salió. */
    @Transaction
    suspend fun removeExpense(expense: Expense) {
        if (expense.accountId != null) adjustBalance(expense.accountId, expense.amount)
        deleteSplitOf(expense.id)
        deleteExpense(expense)
    }

    /** Para no apuntar dos veces el mismo pago cuando avisan la cartera y el banco. */
    @Query("SELECT COUNT(*) FROM expenses WHERE fromCard = 1 AND ABS(amount - :amount) < 0.005 AND createdAt > :since")
    suspend fun recentCardPayments(amount: Double, since: Long): Int

    @Query("DELETE FROM expenses WHERE categoryId = :categoryId")
    suspend fun deleteExpensesOf(categoryId: Long)

    @Query("SELECT * FROM expenses ORDER BY epochDay")
    fun allExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM transfers ORDER BY epochDay DESC, createdAt DESC")
    fun transfers(): Flow<List<Transfer>>

    @Upsert suspend fun upsertTransfer(t: Transfer)
    @Delete suspend fun deleteTransfer(t: Transfer)

    /** Mueve el dinero: resta de una cuenta y suma a la otra. */
    @Transaction
    suspend fun saveTransfer(t: Transfer) {
        upsertTransfer(t)
        adjustBalance(t.fromAccountId, -t.amount)
        adjustBalance(t.toAccountId, t.amount)
    }

    @Transaction
    suspend fun removeTransfer(t: Transfer) {
        adjustBalance(t.fromAccountId, t.amount)
        adjustBalance(t.toAccountId, -t.amount)
        deleteTransfer(t)
    }

    @Query("SELECT * FROM bills ORDER BY day, id")
    fun bills(): Flow<List<RecurringBill>>

    @Upsert suspend fun upsertBill(bill: RecurringBill)
    @Delete suspend fun deleteBill(bill: RecurringBill)

    @Query("SELECT * FROM income_sources ORDER BY variable, dayFrom, id")
    fun incomeSources(): Flow<List<IncomeSource>>

    @Upsert suspend fun upsertIncomeSource(source: IncomeSource)
    @Delete suspend fun deleteIncomeSource(source: IncomeSource)

    @Query("SELECT * FROM incomes ORDER BY epochDay DESC, createdAt DESC")
    fun incomes(): Flow<List<Income>>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun income(id: Long): Income?

    @Upsert suspend fun upsertIncome(income: Income)
    @Delete suspend fun deleteIncome(income: Income)

    /** Guarda el cobro y lo suma a su cuenta (devolviendo antes el importe antiguo si se edita). */
    @Transaction
    suspend fun saveIncome(income: Income) {
        val old = if (income.id != 0L) income(income.id) else null
        if (old?.accountId != null) adjustBalance(old.accountId, -old.amount)
        upsertIncome(income)
        if (income.accountId != null) adjustBalance(income.accountId, income.amount)
    }

    @Transaction
    suspend fun removeIncome(income: Income) {
        if (income.accountId != null) adjustBalance(income.accountId, -income.amount)
        deleteIncome(income)
    }

    @Query("SELECT * FROM accounts WHERE autoInterest = 1 AND interestRate > 0")
    suspend fun interestAccounts(): List<Account>

    @Query("SELECT * FROM snapshots ORDER BY epochDay")
    fun snapshots(): Flow<List<NetWorthSnapshot>>

    @Upsert suspend fun upsertSnapshot(snapshot: NetWorthSnapshot)

    @Query("DELETE FROM splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitOf(expenseId: Long)

    @Query("SELECT * FROM splits")
    fun splits(): Flow<List<Split>>

    @Upsert suspend fun upsertSplit(split: Split)
    @Delete suspend fun deleteSplit(split: Split)

    @Query("SELECT * FROM crypto ORDER BY name")
    fun crypto(): Flow<List<CryptoHolding>>

    @Upsert suspend fun upsertCrypto(holding: CryptoHolding)
    @Delete suspend fun deleteCrypto(holding: CryptoHolding)

    @Query("SELECT * FROM stocks ORDER BY ticker")
    fun stocks(): Flow<List<StockHolding>>

    @Upsert suspend fun upsertStock(holding: StockHolding)
    @Delete suspend fun deleteStock(holding: StockHolding)
}

@Database(
    entities = [
        Account::class, Category::class, Expense::class, CryptoHolding::class, StockHolding::class,
        NetWorthSnapshot::class, IncomeSource::class, Income::class, RecurringBill::class, Transfer::class, Split::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        @Volatile private var instance: FinanceDatabase? = null

        /** v2: los gastos pueden salir de una cuenta y venir de un pago con tarjeta. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN accountId INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN fromCard INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN sourceApp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: una foto diaria del patrimonio neto. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `snapshots` (`epochDay` INTEGER NOT NULL, `total` REAL NOT NULL, PRIMARY KEY(`epochDay`))")
            }
        }

        /** v4: intereses en cuentas, candado en categorías, fuentes de ingreso y cobros. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN interestRate REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE accounts ADD COLUMN autoInterest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE accounts ADD COLUMN interestFromMonth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `income_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `variable` INTEGER NOT NULL, `amount` REAL NOT NULL, `dayFrom` INTEGER NOT NULL, " +
                        "`dayTo` INTEGER NOT NULL, `accountId` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER, " +
                        "`amount` REAL NOT NULL, `note` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `accountId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v5: gastos fijos y el enlace de cada pago con su gasto fijo. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN billId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`amount` REAL NOT NULL, `day` INTEGER NOT NULL, `yearly` INTEGER NOT NULL, `month` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, `accountId` INTEGER)"
                )
            }
        }

        /** v6: transferencias entre cuentas. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromAccountId` INTEGER NOT NULL, " +
                        "`toAccountId` INTEGER NOT NULL, `amount` REAL NOT NULL, `note` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v7: tipo de gasto (clasificación) y gastos repartidos con otros. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN tag TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `splits` (`expenseId` INTEGER NOT NULL, `people` INTEGER NOT NULL, " +
                        "`settled` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`expenseId`))"
                )
            }
        }

        /** v8: el texto de la notificación de cada pago apuntado solo. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN rawText TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "caudal.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .addCallback(SeedCategories)
                .build()
                .also { instance = it }
        }
    }

    /** Reparto 50/30/20 de partida; se puede cambiar desde Presupuesto. */
    private object SeedCategories : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO categories (name, percent, isSavings) VALUES " +
                    "('Necesidades', 50, 0), ('Ocio y caprichos', 30, 0), ('Ahorro', 20, 1)"
            )
        }
    }
}
