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
        var kept: String? = null
        override fun load(): String? = kept
        override fun save(token: String?) { kept = token }
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
        assertEquals(PushResult.REGISTERED, registration.register(endpoint(), "es"))
        val sent = server.takeRequest()
        assertEquals("POST /api/push/devices", "${sent.method} ${sent.path}")
        assertEquals("Bearer session", sent.getHeader("Authorization"))
        val body = org.json.JSONObject(sent.body.readUtf8())
        assertEquals("android", body.getString("platform"))
        assertEquals("es", body.getString("locale"))
        assertEquals(phone.current, store.kept)

        server.enqueue(MockResponse().setBody("{\"removed\":true}"))
        registration.remove(endpoint())
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
        assertEquals(PushResult.SERVER_OFF, PushRegistration(Phone(), store).register(endpoint(), "en"))
        assertNull(store.kept)
    }

    @Test
    fun aBuildWithoutTheConfigurationAsksTheServerNothing() = runBlocking {
        assertEquals(PushResult.FAILED, PushRegistration(Phone(available = false), Store()).register(endpoint(), "en"))
        assertEquals(PushResult.FAILED, PushRegistration(Phone(current = null), Store()).register(endpoint(), "en"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun theTokenIsDroppedAtThePushServiceEvenWithNoServerToAsk() = runBlocking {
        val phone = Phone()
        val store = Store().apply { kept = "fid:APA91b-token-of-the-phone-0001" }
        PushRegistration(phone, store).remove(null)
        assertNull(store.kept)
        assertEquals(1, phone.forgotten)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aLanguageTheServerDoesNotSpeakBecomesEnglish() {
        assertEquals("pt", pushLocale("pt"))
        assertEquals("en", pushLocale("ru"))
    }
}
