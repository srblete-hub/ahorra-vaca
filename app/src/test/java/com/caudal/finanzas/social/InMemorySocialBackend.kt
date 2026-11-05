package com.caudal.finanzas.social

/** Servidor social de mentira, en memoria, para recorrer la app sin internet. */
class InMemorySocialBackend : SocialBackend {
    override val configured = true
    override var userId: String? = null
    override var email: String? = null

    private val people = mutableMapOf(
        "u-laura" to RemoteProfile("u-laura", "laura.ahorra", "Laura", level = 5, levelName = "Estratega", points = 270),
        "u-marcos" to RemoteProfile("u-marcos", "marcos", "Marcos", level = 3, levelName = "Ahorrador", points = 95),
        "u-ana" to RemoteProfile("u-ana", "ana.invierte", "Ana", level = 7, levelName = "Maestro", points = 640, verified = true),
    )
    private val links = mutableListOf<Triple<String, String, LinkStatus>>()
    private val achievements = mutableMapOf(
        "u-ana" to listOf(
            SharedAchievement("racha-3", "Racha de 3", "fire", 1L), SharedAchievement("inversor", "Inversor", "trending", 1L),
            SharedAchievement("intereses", "Dinero trabajando", "savings", 1L), SharedAchievement("mes-verde", "Mes en verde", "leaf", 1L),
        ),
    )

    override suspend fun sendCode(email: String) {}
    override suspend fun verifyCode(email: String, code: String) {
        this.email = email; userId = "u-me"
        // Marcos ya te había mandado solicitud y Ana es tu amiga.
        links += Triple("u-marcos", "u-me", LinkStatus.PENDING)
        links += Triple("u-me", "u-ana", LinkStatus.ACCEPTED)
    }
    override fun signOut() { userId = null; email = null }

    override suspend fun myProfile() = people["u-me"]
    // Como si el administrador ya te hubiera dado el verificado.
    override suspend fun saveProfile(profile: RemoteProfile) = profile.copy(verified = true).also { people["u-me"] = it }
    override suspend fun uploadAvatar(jpeg: ByteArray) = "memoria://avatar"

    override suspend fun search(query: String) = people.values.filter { it.id != userId && it.handle.startsWith(query.removePrefix("@").lowercase()) }
    override suspend fun links(): List<FriendLink> = links.mapNotNull { (from, to, status) ->
        when (userId) {
            from -> FriendLink(people.getValue(to), status, incoming = false)
            to -> FriendLink(people.getValue(from), status, incoming = true)
            else -> null
        }
    }
    override suspend fun sendRequest(userId: String) { links += Triple(this.userId!!, userId, LinkStatus.PENDING) }
    override suspend fun accept(userId: String) {
        val i = links.indexOfFirst { it.first == userId && it.second == this.userId }
        links[i] = links[i].copy(third = LinkStatus.ACCEPTED)
    }
    override suspend fun remove(userId: String) { links.removeAll { (it.first == userId && it.second == this.userId) || (it.second == userId && it.first == this.userId) } }

    override suspend fun publishAchievements(list: List<SharedAchievement>) { achievements["u-me"] = list }
    override suspend fun achievementsOf(userId: String) = achievements[userId].orEmpty()
}
