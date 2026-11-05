package com.caudal.finanzas.social

import android.content.Context
import android.content.SharedPreferences
import com.caudal.finanzas.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Una petición HTTP y su respuesta (código, cuerpo). Separado para poder simularlo en pruebas. */
fun interface Http {
    fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?, contentType: String?): Pair<Int, String>
}

class OkHttpTransport : Http {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    override fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?, contentType: String?): Pair<Int, String> {
        val reqBody = if (method == "GET" || (method == "DELETE" && body == null)) null
        else (body ?: ByteArray(0)).toRequestBody((contentType ?: "application/json").toMediaType())
        val req = Request.Builder().url(url).method(method, reqBody)
        headers.forEach { (k, v) -> req.header(k, v) }
        client.newCall(req.build()).execute().use { return it.code to (it.body?.string() ?: "") }
    }
}

/**
 * Cliente mínimo de Supabase: Auth (código por email), PostgREST (tablas) y Storage (fotos).
 * La sesión se guarda en las preferencias privadas de la app.
 */
class SupabaseBackend(
    private val url: String,
    private val anonKey: String,
    private val http: Http,
    private val store: SharedPreferences,
) : SocialBackend {

    override val configured get() = url.isNotBlank() && anonKey.isNotBlank()
    override val userId: String? get() = store.getString(K_USER, null)
    override val email: String? get() = store.getString(K_EMAIL, null)
    private val access get() = store.getString(K_ACCESS, null)
    private val refresh get() = store.getString(K_REFRESH, null)

    // ---------- Sesión ----------

    override suspend fun sendCode(email: String) {
        val (code, body) = call("POST", "/auth/v1/otp", JSONObject().put("email", email.trim()).put("create_user", true), auth = false)
        if (code !in 200..299) throw SocialException(errorText(body, "No se ha podido enviar el código"))
    }

    override suspend fun verifyCode(email: String, code: String) {
        val (status, body) = call(
            "POST", "/auth/v1/verify",
            JSONObject().put("type", "email").put("email", email.trim()).put("token", code.trim()), auth = false,
        )
        if (status !in 200..299) throw SocialException("Código incorrecto o caducado")
        saveSession(JSONObject(body), email.trim())
    }

    override fun signOut() { store.edit().clear().apply() }

    private fun saveSession(json: JSONObject, email: String? = this.email) {
        store.edit()
            .putString(K_ACCESS, json.getString("access_token"))
            .putString(K_REFRESH, json.getString("refresh_token"))
            .putString(K_USER, json.getJSONObject("user").getString("id"))
            .putString(K_EMAIL, email)
            .apply()
    }

    private suspend fun refreshSession(): Boolean {
        val token = refresh ?: return false
        val (status, body) = call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", token), auth = false)
        if (status !in 200..299) return false
        saveSession(JSONObject(body))
        return true
    }

    // ---------- Perfil ----------

    override suspend fun myProfile(): RemoteProfile? {
        val me = userId ?: return null
        return array(get("/rest/v1/profiles?select=*&id=eq.$me")).firstOrNull()?.let(::profile)
    }

    override suspend fun saveProfile(profile: RemoteProfile): RemoteProfile {
        val me = userId ?: throw SocialException("Sin sesión")
        val json = JSONObject()
            .put("id", me).put("handle", profile.handle).put("name", profile.name)
            .put("level", profile.level).put("level_name", profile.levelName).put("points", profile.points)
        profile.avatarUrl?.let { json.put("avatar_url", it) }
        val (status, body) = authed("POST", "/rest/v1/profiles?on_conflict=id", json.toString().toByteArray(), prefer = "resolution=merge-duplicates,return=representation")
        if (status == 409 || body.contains("profiles_handle_key")) throw HandleTakenException()
        if (status !in 200..299) throw SocialException(errorText(body, "No se ha podido guardar tu perfil"))
        return profile(JSONArray(body).getJSONObject(0))
    }

    override suspend fun uploadAvatar(jpeg: ByteArray): String {
        val me = userId ?: throw SocialException("Sin sesión")
        val path = "avatars/$me/avatar.jpg"
        val (status, body) = authed("POST", "/storage/v1/object/$path", jpeg, contentType = "image/jpeg", extra = mapOf("x-upsert" to "true"))
        if (status !in 200..299) throw SocialException(errorText(body, "No se ha podido subir la foto"))
        // La caché del navegador se salta con la fecha: así los amigos ven la foto nueva.
        return "$url/storage/v1/object/public/$path?v=${System.currentTimeMillis()}"
    }

    // ---------- Amigos ----------

    override suspend fun search(query: String): List<RemoteProfile> {
        val q = query.trim().removePrefix("@").lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }
        if (q.length < 2) return emptyList()
        return array(get("/rest/v1/profiles?select=*&handle=ilike.${enc(q)}%2A&order=handle&limit=20"))
            .map(::profile).filter { it.id != userId }
    }

    override suspend fun links(): List<FriendLink> {
        val me = userId ?: return emptyList()
        val rows = array(get("/rest/v1/friendships?select=*&or=(requester.eq.$me,addressee.eq.$me)"))
        if (rows.isEmpty()) return emptyList()
        val otherIds = rows.map { if (it.getString("requester") == me) it.getString("addressee") else it.getString("requester") }
        val profiles = array(get("/rest/v1/profiles?select=*&id=in.(${otherIds.joinToString(",")})")).map(::profile).associateBy { it.id }
        return rows.mapNotNull { r ->
            val incoming = r.getString("addressee") == me
            val other = profiles[if (incoming) r.getString("requester") else r.getString("addressee")] ?: return@mapNotNull null
            FriendLink(other, if (r.getString("status") == "accepted") LinkStatus.ACCEPTED else LinkStatus.PENDING, incoming)
        }
    }

    override suspend fun sendRequest(userId: String) {
        val me = this.userId ?: throw SocialException("Sin sesión")
        val (status, body) = authed("POST", "/rest/v1/friendships", JSONObject().put("requester", me).put("addressee", userId).toString().toByteArray(), prefer = "return=minimal")
        if (status !in 200..299 && status != 409) throw SocialException(errorText(body, "No se ha podido enviar la solicitud"))
    }

    override suspend fun accept(userId: String) {
        val me = this.userId ?: throw SocialException("Sin sesión")
        val (status, body) = authed("PATCH", "/rest/v1/friendships?requester=eq.$userId&addressee=eq.$me", """{"status":"accepted"}""".toByteArray(), prefer = "return=minimal")
        if (status !in 200..299) throw SocialException(errorText(body, "No se ha podido aceptar"))
    }

    override suspend fun remove(userId: String) {
        val me = this.userId ?: throw SocialException("Sin sesión")
        val (status, body) = authed("DELETE", "/rest/v1/friendships?or=(and(requester.eq.$me,addressee.eq.$userId),and(requester.eq.$userId,addressee.eq.$me))", null)
        if (status !in 200..299) throw SocialException(errorText(body, "No se ha podido quitar"))
    }

    // ---------- Logros ----------

    override suspend fun publishAchievements(list: List<SharedAchievement>) {
        val me = userId ?: return
        if (list.isNotEmpty()) {
            val rows = JSONArray()
            list.forEach {
                rows.put(
                    JSONObject().put("user_id", me).put("achievement_id", it.id).put("title", it.title)
                        .put("icon", it.icon).put("unlocked_at", Instant.ofEpochMilli(it.unlockedAt).toString()),
                )
            }
            authed("POST", "/rest/v1/achievements?on_conflict=user_id,achievement_id", rows.toString().toByteArray(), prefer = "resolution=merge-duplicates,return=minimal")
        }
        // Los que ya no se comparten, fuera del servidor.
        val keep = list.joinToString(",") { "\"${it.id}\"" }
        val filter = if (list.isEmpty()) "" else "&achievement_id=not.in.($keep)"
        authed("DELETE", "/rest/v1/achievements?user_id=eq.$me$filter", null)
    }

    override suspend fun achievementsOf(userId: String): List<SharedAchievement> =
        array(get("/rest/v1/achievements?select=*&user_id=eq.$userId&order=unlocked_at.desc")).map {
            SharedAchievement(it.getString("achievement_id"), it.getString("title"), it.getString("icon"), parseTime(it.getString("unlocked_at")))
        }

    // ---------- Fontanería ----------

    private fun profile(o: JSONObject) = RemoteProfile(
        id = o.getString("id"),
        handle = o.getString("handle"),
        name = o.optString("name", ""),
        avatarUrl = o.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" },
        level = o.optInt("level", 1),
        levelName = o.optString("level_name", "Semilla"),
        points = o.optInt("points", 0),
        verified = o.optBoolean("verified", false),
    )

    private fun array(body: String): List<JSONObject> = JSONArray(body).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    private suspend fun get(path: String): String {
        val (status, body) = authed("GET", path, null)
        if (status !in 200..299) throw SocialException(errorText(body, "Error del servidor ($status)"))
        return body
    }

    private suspend fun call(method: String, path: String, json: JSONObject, auth: Boolean): Pair<Int, String> =
        if (auth) authed(method, path, json.toString().toByteArray())
        else withContext(Dispatchers.IO) { http.send(method, url + path, mapOf("apikey" to anonKey, "Content-Type" to "application/json"), json.toString().toByteArray(), "application/json") }

    /** Petición con la sesión; si ha caducado, la renueva una vez y repite. */
    private suspend fun authed(
        method: String, path: String, body: ByteArray?,
        prefer: String? = null, contentType: String = "application/json", extra: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        suspend fun once(): Pair<Int, String> = withContext(Dispatchers.IO) {
            val headers = buildMap {
                put("apikey", anonKey)
                put("Authorization", "Bearer ${access ?: anonKey}")
                if (prefer != null) put("Prefer", prefer)
                putAll(extra)
            }
            http.send(method, url + path, headers, body, contentType)
        }
        val first = once()
        return if (first.first == 401 && refreshSession()) once() else first
    }

    private fun errorText(body: String, fallback: String): String =
        runCatching { JSONObject(body).let { it.optString("msg").ifBlank { it.optString("message") }.ifBlank { it.optString("error_description") } } }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: fallback

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Postgres devuelve "2026-11-04T10:00:00+00:00" o con "Z". */
    private fun parseTime(s: String): Long = runCatching { java.time.OffsetDateTime.parse(s.replace(" ", "T")).toInstant().toEpochMilli() }
        .getOrElse { runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L) }

    companion object {
        private const val K_ACCESS = "access"
        private const val K_REFRESH = "refresh"
        private const val K_USER = "user"
        private const val K_EMAIL = "email"

        fun create(context: Context) = SupabaseBackend(
            BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, OkHttpTransport(),
            context.getSharedPreferences("caudal_social", Context.MODE_PRIVATE),
        )
    }
}
