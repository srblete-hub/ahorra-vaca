package com.caudal.finanzas.social

import android.content.Context

/** Perfil público: lo único de ti que ve otra persona (nunca saldos ni importes). */
data class RemoteProfile(
    val id: String,
    val handle: String,
    val name: String,
    val avatarUrl: String? = null,
    val level: Int = 1,
    val levelName: String = "Semilla",
    val points: Int = 0,
    val verified: Boolean = false,
)

enum class LinkStatus { PENDING, ACCEPTED }

/** Relación con otra persona: [incoming] = te la ha mandado ella. */
data class FriendLink(val other: RemoteProfile, val status: LinkStatus, val incoming: Boolean)

data class SharedAchievement(val id: String, val title: String, val icon: String, val unlockedAt: Long)

class HandleTakenException : Exception("Ese @ ya está cogido")
class SocialException(message: String) : Exception(message)

/** Todo lo que la app necesita del servidor social. Hay una versión real (Supabase) y otra en memoria para las pruebas. */
interface SocialBackend {
    val configured: Boolean
    val userId: String?
    val email: String?

    suspend fun sendCode(email: String)
    suspend fun verifyCode(email: String, code: String)
    fun signOut()

    suspend fun myProfile(): RemoteProfile?
    suspend fun saveProfile(profile: RemoteProfile): RemoteProfile
    suspend fun uploadAvatar(jpeg: ByteArray): String

    suspend fun search(query: String): List<RemoteProfile>
    suspend fun links(): List<FriendLink>
    suspend fun sendRequest(userId: String)
    suspend fun accept(userId: String)
    suspend fun remove(userId: String)

    suspend fun publishAchievements(list: List<SharedAchievement>)
    suspend fun achievementsOf(userId: String): List<SharedAchievement>
}

object Social {
    @Volatile private var override: SocialBackend? = null
    @Volatile private var real: SocialBackend? = null

    fun backend(context: Context): SocialBackend = override ?: real ?: synchronized(this) {
        real ?: SupabaseBackend.create(context.applicationContext).also { real = it }
    }

    /** Para las pruebas: sustituye el servidor real por uno en memoria. */
    fun overrideForTests(backend: SocialBackend?) { override = backend }
}
