package com.caudal.finanzas

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.security.PinHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cuenta local, bloqueo (PIN / huella) y ajustes. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs.get(app)
    val settings: StateFlow<Settings> = prefs.settings

    private val _hasAccount = MutableStateFlow(prefs.hasAccount)
    val hasAccount: StateFlow<Boolean> = _hasAccount.asStateFlow()

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var failedAttempts = 0
    private var lockedOutUntil = 0L

    fun createAccount(name: String, pin: String) {
        prefs.createAccount(name.trim(), PinHasher.hash(pin))
        _hasAccount.value = true
        _unlocked.value = true
    }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class LockedOut(val seconds: Long) : PinResult
    }

    /** Tras 5 fallos seguidos bloquea 30 s. */
    fun unlockWithPin(pin: String): PinResult {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedOutUntil) return PinResult.LockedOut((lockedOutUntil - now) / 1000 + 1)
        if (PinHasher.verify(pin, prefs.pinHash ?: return PinResult.Wrong(0))) {
            failedAttempts = 0
            _unlocked.value = true
            return PinResult.Ok
        }
        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            failedAttempts = 0
            lockedOutUntil = now + LOCKOUT_MS
            return PinResult.LockedOut(LOCKOUT_MS / 1000)
        }
        return PinResult.Wrong(MAX_ATTEMPTS - failedAttempts)
    }

    fun unlockWithBiometric() {
        failedAttempts = 0
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }

    fun changePin(current: String, new: String): Boolean {
        val stored = prefs.pinHash ?: return false
        if (!PinHasher.verify(current, stored)) return false
        prefs.setPinHash(PinHasher.hash(new))
        return true
    }

    fun setBiometric(enabled: Boolean) = prefs.setBiometric(enabled)
    fun setHideAmounts(hide: Boolean) = prefs.setHideAmounts(hide)
    fun setBlockScreenshots(value: Boolean) = prefs.setBlockScreenshots(value)
    fun setName(name: String) = prefs.setName(name.trim())
    fun setCurrency(code: String) = prefs.setCurrency(code)
    fun setIncome(value: Double) = prefs.setIncome(value)
    fun setCardPayments(on: Boolean) = prefs.setCardPayments(on)
    fun setCardAccount(id: Long) = prefs.setCardAccount(id)
    fun setCardCategory(id: Long) = prefs.setCardCategory(id)
    fun setCardAnyApp(on: Boolean) = prefs.setCardAnyApp(on)
    fun setOnboardingDone(done: Boolean) = prefs.setOnboardingDone(done)
    fun setHandle(handle: String) = prefs.setHandle(handle)

    /** Guarda la foto elegida, recortada a 512 px, en los archivos privados de la app. */
    fun setAvatar(uri: android.net.Uri) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val source = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return@launch
        val side = minOf(source.width, source.height)
        val square = android.graphics.Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = android.graphics.Bitmap.createScaledBitmap(square, 512, 512, true)
        java.io.File(ctx.filesDir, com.caudal.finanzas.ui.AVATAR_FILE).outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        prefs.setAvatarVersion(System.currentTimeMillis())
    }

    fun removeAvatar() {
        java.io.File(getApplication<Application>().filesDir, com.caudal.finanzas.ui.AVATAR_FILE).delete()
        prefs.setAvatarVersion(0)
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L

        /** @handle: 3–20 letras minúsculas, números, punto o guion bajo. */
        val HANDLE_REGEX = Regex("^[a-z0-9._]{3,20}$")
    }
}
