// =============================================================================
// HYDRA-UMC CONTROL - Tests of the registration for push messages
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.push.PushEndpoint
import com.hydraumc.control.push.PushPlatform
import com.hydraumc.control.push.PushRegistration
import com.hydraumc.control.push.PushResult
import com.hydraumc.control.push.PushTokenStore
import com.hydraumc.control.push.pushLocale
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushRegistrationTest {
    private val server = MockWebServer()
    private lateinit var client: HydraApiClient

    private class Phone(override val available: Boolean = true, var current: String? = "fid:APA91b-token-of-the-phone-0001") : PushPlatform {
        var forgotten = 0
        override suspend fun token(): String? = current
        override fun forget() { forgotten++ }
    }

    private class Store : PushTokenStore {
        val tokens = HashMap<String, String>()
        var kept: String?
            get() = tokens[A]
            set(value) { if (value == null) tokens.remove(A) else tokens[A] = value }
        override fun load(server: String): String? = tokens[server]
        override fun save(server: String, token: String?) { if (token == null) tokens.remove(server) else tokens[server] = token }
        override fun servers(): Set<String> = tokens.keys.toSet()
        override fun clear() = tokens.clear()
    }

    private fun endpoint(): PushEndpoint = object : PushEndpoint {
        override suspend fun registerPush(token: String, locale: String) = client.postPushDevice(token, locale)
        override suspend fun removePush(token: String) = client.deletePushDevice(token)
    }

    @Before
    fun start() {
        server.start()
        client = HydraApiClient("127.0.0.1", server.port)
        client.authToken = "session"
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun theTokenIsRegisteredWithTheSessionAndTakenBack() = runBlocking {
        val phone = Phone()
        val store = Store()
        val registration = PushRegistration(phone, store)
        server.enqueue(MockResponse().setBody("{\"registered\":true}"))
        assertEquals(PushResult.REGISTERED, registration.register(endpoint(), A, "es"))
        val sent = server.takeRequest()
        assertEquals("POST /api/push/devices", "${sent.method} ${sent.path}")
        assertEquals("Bearer session", sent.getHeader("Authorization"))
        val body = org.json.JSONObject(sent.body.readUtf8())
        assertEquals("android", body.getString("platform"))
        assertEquals("es", body.getString("locale"))
        assertEquals(phone.current, store.kept)

        server.enqueue(MockResponse().setBody("{\"removed\":true}"))
        registration.remove(endpoint(), A)
        val removed = server.takeRequest()
        assertEquals("DELETE", removed.method)
        assertEquals("/api/push/devices/fid%3AAPA91b-token-of-the-phone-0001", removed.path?.replace("fid:", "fid%3A"))
        assertNull(store.kept)
        assertEquals(1, phone.forgotten)
    }

    @Test
    fun aServerWithPushOffIsSaidAndNothingIsKept() = runBlocking {
        val store = Store()
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(PushResult.SERVER_OFF, PushRegistration(Phone(), store).register(endpoint(), A, "en"))
        assertNull(store.kept)
    }

    @Test
    fun aBuildWithoutTheConfigurationAsksTheServerNothing() = runBlocking {
        assertEquals(PushResult.FAILED, PushRegistration(Phone(available = false), Store()).register(endpoint(), A, "en"))
        assertEquals(PushResult.FAILED, PushRegistration(Phone(current = null), Store()).register(endpoint(), A, "en"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun theTokenIsDroppedAtThePushServiceEvenWithNoServerToAsk() = runBlocking {
        val phone = Phone()
        val store = Store().apply { kept = "fid:APA91b-token-of-the-phone-0001" }
        PushRegistration(phone, store).remove(null, A)
        assertNull(store.kept)
        assertEquals(1, phone.forgotten)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun eachServerKeepsItsOwnRegistrationAndLeavingOneDoesNotTouchTheOther() = runBlocking {
        val phone = Phone()
        val store = Store().apply { tokens[A] = "token-for-a"; tokens[B] = "token-for-b" }
        server.enqueue(MockResponse().setBody("{\"removed\":true}"))
        PushRegistration(phone, store).remove(endpoint(), A)
        assertEquals(setOf(B), store.servers())
        assertEquals("the token of the phone stays while another server keeps it", 0, phone.forgotten)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aServerThatCannotBeToldMakesThePhoneDropItsToken() = runBlocking {
        val phone = Phone()
        val store = Store().apply { tokens[A] = "token-for-a"; tokens[B] = "token-for-b" }
        PushRegistration(phone, store).remove(null, A)
        assertEquals(emptySet<String>(), store.servers())
        assertEquals(1, phone.forgotten)
    }

    @Test
    fun switchingTheMessagesOffTellsTheServerInUseAndDropsThemAll() = runBlocking {
        val phone = Phone()
        val store = Store().apply { tokens[A] = "token-for-a"; tokens[B] = "token-for-b" }
        server.enqueue(MockResponse().setBody("{\"removed\":true}"))
        PushRegistration(phone, store).removeAll(endpoint(), B)
        assertEquals("/api/push/devices/token-for-b", server.takeRequest().path)
        assertEquals(1, server.requestCount)
        assertEquals(emptySet<String>(), store.servers())
        assertEquals(1, phone.forgotten)
    }

    private companion object {
        const val A = "192.168.1.10:3000"
        const val B = "192.168.1.20:3000"
    }

    @Test
    fun aLanguageTheServerDoesNotSpeakBecomesEnglish() {
        assertEquals("pt", pushLocale("pt"))
        assertEquals("en", pushLocale("ru"))
    }
}
