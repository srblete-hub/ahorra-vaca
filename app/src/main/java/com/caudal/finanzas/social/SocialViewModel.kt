package com.caudal.finanzas.social

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caudal.finanzas.FinanceViewModel
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.data.Settings
import com.caudal.finanzas.ui.AVATAR_FILE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Logros que dan pistas de cuánto dinero tienes: solo se comparten si lo activas. */
fun isMoneyAchievement(id: String) = id.startsWith("ahorro-") || id.startsWith("patrimonio-") || id == "colchon"

class SocialViewModel(app: Application) : AndroidViewModel(app) {
    private val backend = Social.backend(app)
    private val prefs = Prefs.get(app)
    private val meta = app.getSharedPreferences("caudal_social_meta", android.content.Context.MODE_PRIVATE)

    data class State(
        val configured: Boolean = false,
        val signedIn: Boolean = false,
        val email: String? = null,
        val me: RemoteProfile? = null,
        val links: List<FriendLink> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    ) {
        val friends get() = links.filter { it.status == LinkStatus.ACCEPTED }.map { it.other }
        val incoming get() = links.filter { it.status == LinkStatus.PENDING && it.incoming }.map { it.other }
        val outgoing get() = links.filter { it.status == LinkStatus.PENDING && !it.incoming }.map { it.other }
    }

    private val _state = MutableStateFlow(State(backend.configured, backend.userId != null, backend.email))
    val state: StateFlow<State> = _state.asStateFlow()

    init { if (backend.userId != null) refresh() }

    private fun fail(e: Throwable) {
        _state.update { it.copy(loading = false, error = if (e is HandleTakenException) "Ese @ ya lo tiene otra persona. Elige otro en \"Editar perfil\"." else e.message ?: "Sin conexión") }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun refresh() = viewModelScope.launch {
        if (backend.userId == null) return@launch
        _state.update { it.copy(loading = true) }
        runCatching {
            val me = backend.myProfile()
            val links = backend.links()
            _state.update { it.copy(me = me ?: it.me, links = links, loading = false, signedIn = true, email = backend.email) }
        }.onFailure(::fail)
    }

    // ---------- Sesión ----------

    fun sendCode(email: String, onSent: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { backend.sendCode(email) }
            .onSuccess { _state.update { it.copy(loading = false) }; onSent() }
            .onFailure(::fail)
    }

    fun verify(email: String, code: String, onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { backend.verifyCode(email, code) }
            .onSuccess {
                meta.edit().remove(K_AVATAR_SENT).apply()
                _state.update { it.copy(signedIn = true, email = backend.email, loading = false) }
                refresh()
                onDone()
            }
            .onFailure(::fail)
    }

    fun signOut() {
        backend.signOut()
        meta.edit().clear().apply()
        _state.value = State(backend.configured, false, null)
    }

    // ---------- Publicar tu perfil y tus logros ----------

    /** Sube a tu perfil público el nombre, @, foto, nivel y los logros que compartes. Nunca importes. */
    fun publish(planning: FinanceViewModel.Planning, settings: Settings) = viewModelScope.launch {
        if (backend.userId == null || settings.handle.isBlank()) return@launch
        runCatching {
            var avatarUrl = _state.value.me?.avatarUrl ?: meta.getString(K_AVATAR_URL, null)
            if (settings.avatarVersion != 0L && meta.getLong(K_AVATAR_SENT, 0) != settings.avatarVersion) {
                val bytes = withContext(Dispatchers.IO) { File(getApplication<Application>().filesDir, AVATAR_FILE).takeIf { it.exists() }?.readBytes() }
                if (bytes != null) {
                    avatarUrl = backend.uploadAvatar(bytes)
                    meta.edit().putLong(K_AVATAR_SENT, settings.avatarVersion).putString(K_AVATAR_URL, avatarUrl).apply()
                }
            }
            val level = planning.level
            val me = backend.saveProfile(
                RemoteProfile(
                    id = backend.userId!!, handle = settings.handle, name = settings.userName, avatarUrl = avatarUrl,
                    level = level.number, levelName = level.name, points = planning.points,
                ),
            )
            val shared = planning.achievements
                .filter { it.unlocked && (settings.shareMoneyAchievements || !isMoneyAchievement(it.id)) }
                .map { SharedAchievement(it.id, it.title, it.icon, settings.achievements[it.id] ?: System.currentTimeMillis()) }
            backend.publishAchievements(shared)
            _state.update { it.copy(me = me, error = null) }
        }.onFailure(::fail)
    }

    // ---------- Amigos ----------

    suspend fun search(query: String): List<RemoteProfile> = runCatching { backend.search(query) }.getOrElse { fail(it); emptyList() }
    suspend fun achievementsOf(userId: String): List<SharedAchievement> = runCatching { backend.achievementsOf(userId) }.getOrElse { emptyList() }

    fun sendRequest(p: RemoteProfile) = act { backend.sendRequest(p.id) }
    fun accept(p: RemoteProfile) = act { backend.accept(p.id) }
    fun remove(p: RemoteProfile) = act { backend.remove(p.id) }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onSuccess { refresh() }.onFailure(::fail)
    }

    private companion object {
        const val K_AVATAR_SENT = "avatar_sent"
        const val K_AVATAR_URL = "avatar_url"
    }
}
