package com.caudal.finanzas.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val userName: String = "",
    val currency: String = "EUR",
    val monthlyIncome: Double = 0.0,
    val hideAmounts: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** Pagos con tarjeta leídos de las notificaciones. */
    val cardPayments: Boolean = false,
    val cardAccountId: Long = NONE,
    val cardCategoryId: Long = NONE,
    /** Además de carteras y bancos conocidos, mirar las notificaciones de cualquier app. */
    val cardAnyApp: Boolean = false,
    /** Ya se ha pasado por el asistente inicial (cuentas, ahorro, inversiones, ingresos). */
    val onboardingDone: Boolean = false,
    /** Nombre de usuario público, sin la @. */
    val handle: String = "",
    /** Cambia cada vez que se pone una foto nueva, para recargarla. 0 = sin foto. */
    val avatarVersion: Long = 0,
    /** Logros desbloqueados: id → fecha (milisegundos). */
    val achievements: Map<String, Long> = emptyMap(),
    /** Compartir con amigos también los logros que dan pistas de cantidades (ahorros, patrimonio). */
    val shareMoneyAchievements: Boolean = false,
    /** Dinero apartado para un plan (escapada, regalo…), que no cuenta como gastable. */
    val planReserve: Double = 0.0,
    val planName: String = "",
    /** Reparto del saldo: cuenta principal (donde cobras y pagas el alquiler), adónde va el ahorro y qué %. */
    val bankMainId: Long = NONE,
    val bankTargetId: Long = NONE,
    val bankSavePct: Double = 20.0,
    /** Con cuánto quieres llegar a fin de mes después de ahorrar. */
    val bankBuffer: Double = 100.0,
    /** Último mes (año·12+mes−1) en que se marcó sola la nómina ya cobrada. */
    val autoPaidMonth: Int = 0,
    /** Objetivo de ahorro (target 0 = sin objetivo); fechas en días desde 1970. */
    val goalName: String = "",
    val goalTarget: Double = 0.0,
    val goalStart: Long = 0,
    val goalDeadline: Long = 0,
    /** Año de nacimiento, para la edad financiera (0 = sin poner). */
    val birthYear: Int = 0,
    /** Límites por tipo de gasto ("TRANSPORT:30:W;…"); null = nunca tocado (gasolina 30 €/semana). */
    val tagLimitsRaw: String? = null,
    /** Bloquear capturas y ocultar la app en "recientes". Apagado: se pueden hacer capturas. */
    val blockScreenshots: Boolean = false,
    /** Con cuántos días de antelación avisar de cada cobro (0 = no avisar). */
    val billRemindDays: Int = 2,
    /** Aviso de las 21:00 con el resumen del día. */
    val nightSummary: Boolean = true,
    /** Viajes ("nombre|presupuesto|inicio|fin;…"). */
    val tripsRaw: String = "",
    /** Reto en marcha ("id|día de inicio") y retos cumplidos ("id|día;…"). */
    val challengeRaw: String = "",
    val challengesDone: String = "",
    /** Semana (lunes, epochDay) de la hucha de redondeo ya pasada o descartada. */
    val roundUpWeek: Long = 0,
    /** Objetivos en orden de prioridad: "nombre|objetivo|inicio|fecha" por línea. */
    val goalsRaw: String = "",
    /** Gastos que esperas que te devuelvan. */
    val refundIds: Set<Long> = emptySet(),
    /** Cambio en 24 h (%) de cada inversión por símbolo, de la última actualización de precios. */
    val dayChanges: Map<String, Double> = emptyMap(),
    /** Cuán ahorrador quieres ser: 0 relajado … 3 muy ahorrador; −1 = aún no lo has dicho. */
    val savingStyle: Int = -1,
    /** Objetivos elegidos en la ventana inicial (claves de [com.caudal.finanzas.domain.LifeGoal]). */
    val lifeGoals: Set<String> = emptySet(),
    /** Límites de la última vez que se abrió la app (para avisar en segundo plano). */
    val limitSnapshot: String? = null,
    /** Puntuaciones diarias de los tres círculos, para ver el cambio semanal. */
    val circleHistory: String = "",
    /** Firmas de avisos marcados como "no era un pago". */
    val ignoredPayments: Set<String> = emptySet(),
    /** Importe confirmado de un ingreso variable en un mes: "idFuente@2026-11" → €. */
    val expectedIncome: Map<String, Double> = emptyMap(),
    /** Cuentas con amigos (ver domain/Debts.kt) y saldos de Tricount. */
    val debtsRaw: String = "",
    val tricountRaw: String = "",
    /** Semana ("2026-47") en la que dijiste "Ahora no" a la recomendación de Pepe. */
    val pepeTipHiddenWeek: String = "",
    /** Ahorro bloqueado: la app no propone sacar dinero de ahí. */
    val savingsLocked: Boolean = false,
    /** Mostrar cada semana la propuesta de Pepe para el ahorro. */
    val weeklyTip: Boolean = true,
    /** Acciones de la revisión de suscripciones que ya marcaste. */
    val reviewedSubs: Set<String> = emptySet(),
) {
    companion object { const val NONE = -1L }
}

/** Ajustes y credenciales locales. El PIN nunca se guarda, solo su hash PBKDF2 con sal. */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("caudal", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val hasAccount: Boolean get() = sp.contains(PIN_HASH)
    val pinHash: String? get() = sp.getString(PIN_HASH, null)

    private fun read() = Settings(
        userName = sp.getString(NAME, "") ?: "",
        currency = sp.getString(CURRENCY, "EUR") ?: "EUR",
        monthlyIncome = sp.getFloat(INCOME, 0f).toDouble(),
        hideAmounts = sp.getBoolean(HIDE, false),
        biometricEnabled = sp.getBoolean(BIOMETRIC, false),
        cardPayments = sp.getBoolean(CARD, false),
        cardAccountId = sp.getLong(CARD_ACCOUNT, Settings.NONE),
        cardCategoryId = sp.getLong(CARD_CATEGORY, Settings.NONE),
        cardAnyApp = sp.getBoolean(CARD_ANY_APP, false),
        onboardingDone = sp.getBoolean(ONBOARDING, false),
        handle = sp.getString(HANDLE, "") ?: "",
        avatarVersion = sp.getLong(AVATAR, 0),
        achievements = (sp.getStringSet(ACHIEVEMENTS, emptySet()) ?: emptySet()).mapNotNull {
            val parts = it.split("@")
            if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
        }.toMap(),
        shareMoneyAchievements = sp.getBoolean(SHARE_MONEY, false),
        planReserve = sp.getFloat(PLAN_RESERVE, 0f).toDouble(),
        planName = sp.getString(PLAN_NAME, "") ?: "",
        bankMainId = sp.getLong(BANK_MAIN, Settings.NONE),
        bankTargetId = sp.getLong(BANK_TARGET, Settings.NONE),
        bankSavePct = sp.getFloat(BANK_PCT, 20f).toDouble(),
        bankBuffer = sp.getFloat(BANK_BUFFER, 100f).toDouble(),
        autoPaidMonth = sp.getInt(AUTO_PAID, 0),
        goalName = sp.getString(GOAL_NAME, "") ?: "",
        goalTarget = sp.getFloat(GOAL_TARGET, 0f).toDouble(),
        goalStart = sp.getLong(GOAL_START, 0),
        goalDeadline = sp.getLong(GOAL_DEADLINE, 0),
        birthYear = sp.getInt(BIRTH_YEAR, 0),
        tagLimitsRaw = sp.getString(TAG_LIMITS, null),
        blockScreenshots = sp.getBoolean(BLOCK_SHOTS, false),
        billRemindDays = sp.getInt(BILL_REMIND, 2),
        nightSummary = sp.getBoolean(NIGHT, true),
        tripsRaw = (sp.getString(TRIPS, "") ?: "").also { com.caudal.finanzas.domain.Trips.all = com.caudal.finanzas.domain.Trip.parseAll(it) },
        challengeRaw = sp.getString(CHALLENGE, "") ?: "",
        challengesDone = sp.getString(CHALLENGES_DONE, "") ?: "",
        roundUpWeek = sp.getLong(ROUND_UP, 0),
        refundIds = (sp.getStringSet(REFUNDS, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet(),
        dayChanges = (sp.getString(DAY_CHANGES, "") ?: "").split(';').mapNotNull { p ->
            p.split(':').takeIf { it.size == 2 }?.let { (k, v) -> v.toDoubleOrNull()?.let { k to it } }
        }.toMap(),
        savingStyle = sp.getInt(SAVING_STYLE, -1),
        lifeGoals = sp.getStringSet(LIFE_GOALS, emptySet()) ?: emptySet(),
        limitSnapshot = sp.getString(LIMITS, null),
        circleHistory = sp.getString(CIRCLES, "") ?: "",
        ignoredPayments = sp.getStringSet(IGNORED, emptySet()) ?: emptySet(),
        expectedIncome = (sp.getString(EXPECTED, "") ?: "").split(';').mapNotNull { e ->
            e.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toDoubleOrNull()?.let { k to it } }
        }.toMap(),
        debtsRaw = sp.getString(DEBTS, "") ?: "",
        tricountRaw = sp.getString(TRICOUNT, "") ?: "",
        pepeTipHiddenWeek = sp.getString(PEPE_HIDDEN, "") ?: "",
        savingsLocked = sp.getBoolean(SAVINGS_LOCKED, false),
        weeklyTip = sp.getBoolean(WEEKLY_TIP, true),
        reviewedSubs = sp.getStringSet(REVIEWED_SUBS, emptySet()) ?: emptySet(),
        goalsRaw = sp.getString(GOALS, null) ?: legacyGoal(),
    )

    /** El objetivo único de antes pasa a ser el primero de la lista. */
    private fun legacyGoal(): String {
        val target = sp.getFloat(GOAL_TARGET, 0f)
        if (target <= 0f) return ""
        return listOf(sp.getString(GOAL_NAME, "") ?: "", target.toString(), sp.getLong(GOAL_START, 0).toString(), sp.getLong(GOAL_DEADLINE, 0).toString()).joinToString("|")
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _settings.value = read()
    }

    fun createAccount(name: String, pinHash: String) = edit {
        putString(NAME, name)
        putString(PIN_HASH, pinHash)
    }

    fun setPinHash(hash: String) = edit { putString(PIN_HASH, hash) }
    fun setName(name: String) = edit { putString(NAME, name) }
    fun setCurrency(code: String) = edit { putString(CURRENCY, code) }
    fun setIncome(value: Double) = edit { putFloat(INCOME, value.toFloat()) }
    fun setHideAmounts(value: Boolean) = edit { putBoolean(HIDE, value) }
    fun setBiometric(value: Boolean) = edit { putBoolean(BIOMETRIC, value) }
    fun setCardPayments(value: Boolean) = edit { putBoolean(CARD, value) }
    fun setCardAccount(id: Long) = edit { putLong(CARD_ACCOUNT, id) }
    fun setCardCategory(id: Long) = edit { putLong(CARD_CATEGORY, id) }
    fun setCardAnyApp(value: Boolean) = edit { putBoolean(CARD_ANY_APP, value) }
    fun setOnboardingDone(value: Boolean) = edit { putBoolean(ONBOARDING, value) }
    fun setHandle(value: String) = edit { putString(HANDLE, value) }
    fun setAvatarVersion(value: Long) = edit { putLong(AVATAR, value) }
    fun setShareMoneyAchievements(value: Boolean) = edit { putBoolean(SHARE_MONEY, value) }
    fun setBankPlan(mainId: Long, targetId: Long, pct: Double) = edit {
        putLong(BANK_MAIN, mainId); putLong(BANK_TARGET, targetId); putFloat(BANK_PCT, pct.toFloat())
    }
    fun setGoal(name: String, target: Double, start: Long, deadline: Long) = edit {
        putString(GOAL_NAME, name); putFloat(GOAL_TARGET, target.toFloat()); putLong(GOAL_START, start); putLong(GOAL_DEADLINE, deadline)
    }
    fun setBirthYear(year: Int) = edit { putInt(BIRTH_YEAR, year) }
    fun setBankBuffer(value: Double) = edit { putFloat(BANK_BUFFER, value.toFloat()) }
    fun setAutoPaidMonth(value: Int) = edit { putInt(AUTO_PAID, value) }
    fun setTagLimits(raw: String) = edit { putString(TAG_LIMITS, raw) }
    fun setBlockScreenshots(value: Boolean) = edit { putBoolean(BLOCK_SHOTS, value) }
    fun setGoals(raw: String) = edit { putString(GOALS, raw) }
    fun setDayChanges(map: Map<String, Double>) = edit {
        val merged = settings.value.dayChanges + map
        putString(DAY_CHANGES, merged.entries.joinToString(";") { "${it.key}:${it.value}" })
    }
    fun ignorePayment(signature: String) = edit { putStringSet(IGNORED, (sp.getStringSet(IGNORED, emptySet()) ?: emptySet()) + signature) }
    fun setExpectedIncome(key: String, amount: Double?) = edit {
        val m = settings.value.expectedIncome.toMutableMap()
        if (amount == null) m.remove(key) else m[key] = amount
        putString(EXPECTED, m.entries.joinToString(";") { "${it.key}=${it.value}" })
    }
    /** Acciones que has mirado hace poco (la más reciente primero). */
    fun recentTickers(): List<String> = (sp.getString(RECENT_TICKERS, "") ?: "").split(',').filter { it.isNotBlank() }
    fun setRecentTickers(list: List<String>) = sp.edit().putString(RECENT_TICKERS, list.joinToString(",")).apply()
    /** Noticias ya vistas (hoy y ayer), para no repetirlas al volver a entrar. */
    fun seenNews(): Set<String> {
        val keep = setOf(java.time.LocalDate.now().toString(), java.time.LocalDate.now().minusDays(1).toString())
        return (sp.getStringSet(SEEN_NEWS, emptySet()) ?: emptySet()).filter { it.substringBefore('|') in keep }.map { it.substringAfter('|') }.toSet()
    }
    fun markNewsSeen(id: String) {
        val keep = setOf(java.time.LocalDate.now().toString(), java.time.LocalDate.now().minusDays(1).toString())
        val now = (sp.getStringSet(SEEN_NEWS, emptySet()) ?: emptySet()).filter { it.substringBefore('|') in keep }.toSet()
        if (now.none { it.endsWith("|$id") }) sp.edit().putStringSet(SEEN_NEWS, now + "${java.time.LocalDate.now()}|$id").apply()
    }
    fun hidePepeTip(week: String) = edit { putString(PEPE_HIDDEN, week) }
    fun setSavingsLocked(v: Boolean) = edit { putBoolean(SAVINGS_LOCKED, v) }
    fun setWeeklyTip(v: Boolean) = edit { putBoolean(WEEKLY_TIP, v) }
    fun setBillRemindDays(v: Int) = edit { putInt(BILL_REMIND, v) }
    fun setNightSummary(v: Boolean) = edit { putBoolean(NIGHT, v) }
    fun setTrips(raw: String) = edit { putString(TRIPS, raw) }
    fun setChallenge(raw: String) = edit { putString(CHALLENGE, raw) }
    fun addChallengeDone(id: String, day: Long) = edit {
        putString(CHALLENGES_DONE, listOfNotNull(sp.getString(CHALLENGES_DONE, "")?.takeIf { it.isNotBlank() }, "$id|$day").joinToString(";"))
        putString(CHALLENGE, "")
    }
    fun setRoundUpWeek(v: Long) = edit { putLong(ROUND_UP, v) }
    fun toggleReviewedSub(key: String) {
        val now = sp.getStringSet(REVIEWED_SUBS, emptySet()) ?: emptySet()
        edit { putStringSet(REVIEWED_SUBS, if (key in now) now - key else now + key) }
    }
    fun setDebts(raw: String) = edit { putString(DEBTS, raw) }
    fun setTricount(raw: String) = edit { putString(TRICOUNT, raw) }
    fun setCircleHistory(raw: String) { if (sp.getString(CIRCLES, "") != raw) edit { putString(CIRCLES, raw) } }
    fun setLimitSnapshot(raw: String) { if (sp.getString(LIMITS, null) != raw) edit { putString(LIMITS, raw) } }

    /** Avisos ya enviados hoy, para no repetirlos. */
    fun wasAlerted(key: String, day: java.time.LocalDate) = "$key@${day.toEpochDay()}" in (sp.getStringSet(ALERTED, emptySet()) ?: emptySet())
    fun markAlerted(key: String, day: java.time.LocalDate) {
        val keep = (sp.getStringSet(ALERTED, emptySet()) ?: emptySet()).filter { it.substringAfter('@').toLongOrNull() == day.toEpochDay() }.toSet()
        sp.edit().putStringSet(ALERTED, keep + "$key@${day.toEpochDay()}").apply()
    }

    fun setSavingProfile(style: Int, goals: Set<String>) = edit { putInt(SAVING_STYLE, style); putStringSet(LIFE_GOALS, goals) }
    fun setRefund(expenseId: Long, pending: Boolean) = edit {
        val now = settings.value.refundIds
        putStringSet(REFUNDS, (if (pending) now + expenseId else now - expenseId).map { it.toString() }.toSet())
    }
    fun setPlan(name: String, amount: Double) = edit { putString(PLAN_NAME, name); putFloat(PLAN_RESERVE, amount.toFloat()) }

    /** Guarda los logros nuevos con la fecha de hoy; los que ya estaban no cambian. */
    fun unlock(ids: Collection<String>, now: Long = System.currentTimeMillis()) = edit {
        val current = settings.value.achievements
        val merged = current + ids.filter { it !in current }.associateWith { now }
        putStringSet(ACHIEVEMENTS, merged.map { (id, t) -> "$id@$t" }.toSet())
    }

    companion object {
        private const val NAME = "name"
        private const val PIN_HASH = "pin_hash"
        private const val CURRENCY = "currency"
        private const val INCOME = "monthly_income"
        private const val HIDE = "hide_amounts"
        private const val BIOMETRIC = "biometric"
        private const val CARD = "card_payments"
        private const val CARD_ACCOUNT = "card_account"
        private const val CARD_CATEGORY = "card_category"
        private const val CARD_ANY_APP = "card_any_app"
        private const val ONBOARDING = "onboarding_done"
        private const val HANDLE = "handle"
        private const val AVATAR = "avatar_version"
        private const val ACHIEVEMENTS = "achievements"
        private const val SHARE_MONEY = "share_money_achievements"
        private const val PLAN_RESERVE = "plan_reserve"
        private const val PLAN_NAME = "plan_name"
        private const val BANK_MAIN = "bank_main"
        private const val BANK_TARGET = "bank_target"
        private const val BANK_PCT = "bank_save_pct"
        private const val GOAL_NAME = "goal_name"
        private const val GOAL_TARGET = "goal_target"
        private const val GOAL_START = "goal_start"
        private const val GOAL_DEADLINE = "goal_deadline"
        private const val BIRTH_YEAR = "birth_year"
        private const val BANK_BUFFER = "bank_buffer"
        private const val AUTO_PAID = "auto_paid_month"
        private const val TAG_LIMITS = "tag_limits"
        private const val BLOCK_SHOTS = "block_screenshots"
        private const val GOALS = "goals"
        private const val REFUNDS = "refunds"
        private const val DAY_CHANGES = "day_changes"
        private const val SAVING_STYLE = "saving_style"
        private const val LIFE_GOALS = "life_goals"
        private const val LIMITS = "limit_snapshot"
        private const val CIRCLES = "circle_history"
        private const val IGNORED = "ignored_payments"
        private const val EXPECTED = "expected_income"
        private const val DEBTS = "debts"
        private const val PEPE_HIDDEN = "pepe_hidden_week"
        private const val SAVINGS_LOCKED = "savings_locked"
        private const val WEEKLY_TIP = "weekly_tip"
        private const val REVIEWED_SUBS = "reviewed_subs"
        private const val BILL_REMIND = "bill_remind_days"
        private const val NIGHT = "night_summary"
        private const val TRIPS = "trips"
        private const val CHALLENGE = "challenge"
        private const val CHALLENGES_DONE = "challenges_done"
        private const val ROUND_UP = "round_up_week"
        private const val SEEN_NEWS = "seen_news"
        private const val RECENT_TICKERS = "recent_tickers"
        private const val TRICOUNT = "tricount"
        private const val ALERTED = "alerted"

        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context.applicationContext).also { instance = it }
        }
    }
}
