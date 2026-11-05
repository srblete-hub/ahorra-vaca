package com.caudal.finanzas.social

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comprueba qué se le pide a Supabase, con un servidor simulado que apunta cada petición. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SupabaseBackendTest {
    data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private val calls = mutableListOf<Call>()
    private val replies = ArrayDeque<Pair<Int, String>>()
    private lateinit var backend: SupabaseBackend

    private val session = """{"access_token":"A1","refresh_token":"R1","user":{"id":"me-1"}}"""

    @Before fun setUp() {
        val store = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("t", Context.MODE_PRIVATE)
        store.edit().clear().apply()
        backend = SupabaseBackend("https://x.supabase.co", "ANON", { m, u, h, b, _ ->
            calls += Call(m, u, h, b?.toString(Charsets.UTF_8))
            replies.removeFirstOrNull() ?: (200 to "[]")
        }, store)
    }

    private fun signIn() = runBlocking {
        replies += 200 to session
        backend.verifyCode("yo@mail.com", "123456")
        calls.clear()
    }

    @Test fun codeLoginStoresSession() = runBlocking {
        replies += 200 to "{}"
        backend.sendCode(" yo@mail.com ")
        assertEquals("https://x.supabase.co/auth/v1/otp", calls[0].url)
        assertTrue(calls[0].body!!.contains("\"email\":\"yo@mail.com\""))
        replies += 200 to session
        backend.verifyCode("yo@mail.com", "123456")
        assertEquals("me-1", backend.userId)
        assertEquals("yo@mail.com", backend.email)
        assertTrue(calls[1].body!!.contains("\"token\":\"123456\""))
    }

    @Test fun saveProfileUpsertsWithoutVerified() = runBlocking {
        signIn()
        replies += 201 to """[{"id":"me-1","handle":"alex","name":"Alex","level":3,"level_name":"Ahorrador","points":90,"verified":true}]"""
        val p = backend.saveProfile(RemoteProfile("me-1", "alex", "Alex", level = 3, levelName = "Ahorrador", points = 90))
        val c = calls.single()
        assertEquals("POST", c.method)
        assertTrue(c.url.endsWith("/rest/v1/profiles?on_conflict=id"))
        assertEquals("Bearer A1", c.headers["Authorization"])
        assertTrue(c.headers["Prefer"]!!.contains("merge-duplicates"))
        // La app nunca manda "verified": eso solo se pone desde el panel.
        assertTrue(!c.body!!.contains("verified"))
        assertTrue(p.verified)
    }

    @Test fun takenHandleIsReported() {
        signIn()
        replies += 409 to """{"message":"duplicate key value violates unique constraint \"profiles_handle_key\""}"""
        val error = runCatching { runBlocking { backend.saveProfile(RemoteProfile("me-1", "alex", "Alex")) } }.exceptionOrNull()
        assertTrue(error is HandleTakenException)
    }

    @Test fun searchByHandlePrefixSkipsMe() = runBlocking {
        signIn()
        replies += 200 to """[{"id":"me-1","handle":"alex"},{"id":"u2","handle":"alexia","verified":true}]"""
        val r = backend.search("@Alex ")
        assertTrue(calls.single().url.contains("handle=ilike.alex%2A"))
        assertEquals(listOf("alexia"), r.map { it.handle })
        assertTrue(r.first().verified)
    }

    @Test fun linksAreSplitIntoIncomingAndAccepted() = runBlocking {
        signIn()
        replies += 200 to """[{"requester":"u2","addressee":"me-1","status":"pending"},{"requester":"me-1","addressee":"u3","status":"accepted"}]"""
        replies += 200 to """[{"id":"u2","handle":"laura"},{"id":"u3","handle":"marcos"}]"""
        val links = backend.links()
        assertTrue(calls[1].url.contains("id=in.(u2,u3)"))
        assertEquals(LinkStatus.PENDING, links.first { it.other.handle == "laura" }.status)
        assertTrue(links.first { it.other.handle == "laura" }.incoming)
        assertEquals(LinkStatus.ACCEPTED, links.first { it.other.handle == "marcos" }.status)
    }

    @Test fun requestAcceptRemove() = runBlocking {
        signIn()
        backend.sendRequest("u2")
        backend.accept("u3")
        backend.remove("u4")
        assertTrue(calls[0].body!!.contains("\"requester\":\"me-1\"") && calls[0].body!!.contains("\"addressee\":\"u2\""))
        assertEquals("PATCH", calls[1].method)
        assertTrue(calls[1].url.contains("requester=eq.u3&addressee=eq.me-1"))
        assertEquals("DELETE", calls[2].method)
    }

    @Test fun expiredSessionIsRenewedOnce() = runBlocking {
        signIn()
        replies += 401 to "{}"
        replies += 200 to """{"access_token":"A2","refresh_token":"R2","user":{"id":"me-1"}}"""
        replies += 200 to "[]"
        backend.search("lau")
        assertTrue(calls[1].url.contains("grant_type=refresh_token"))
        assertEquals("Bearer A2", calls[2].headers["Authorization"])
    }

    @Test fun achievementsArePublishedAndRead() = runBlocking {
        signIn()
        backend.publishAchievements(listOf(SharedAchievement("mes-verde", "Mes en verde", "leaf", 0L)))
        assertTrue(calls[0].body!!.contains("\"achievement_id\":\"mes-verde\""))
        assertTrue(calls[1].url.contains("achievement_id=not.in.(\"mes-verde\")"))
        replies += 200 to """[{"achievement_id":"racha-3","title":"Racha de 3","icon":"fire","unlocked_at":"2026-11-04T10:00:00+00:00"}]"""
        val list = backend.achievementsOf("u2")
        assertEquals("Racha de 3", list.single().title)
        assertTrue(list.single().unlockedAt > 0)
    }

    @Test fun moneyAchievementsAreDetected() {
        assertTrue(isMoneyAchievement("ahorro-1000"))
        assertTrue(isMoneyAchievement("patrimonio-50000"))
        assertTrue(isMoneyAchievement("colchon"))
        assertTrue(!isMoneyAchievement("racha-3"))
    }
}
