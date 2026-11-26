// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/SecondFactorSignInTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sign-in of an account with the second factor: what the form is told, what the request carries, and the five calls of the screen against a fake server. */
@RunWith(RobolectricTestRunner::class)
class SecondFactorSignInTest {

    private class Store : AuthStore {
        var saved: UserProfile? = null
        override suspend fun loadAuth(): UserProfile = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) { saved = profile }
        override suspend fun clearAuth(wipePassword: Boolean) {}
    }

    private class Client(val answer: (String?) -> JSONObject) : LoginEndpoint {
        override var authToken: String? = null
        override var refreshToken: String? = null
        var sentCode: String? = "never called"
        override suspend fun refresh(token: String): JSONObject = throw HydraApiException("no", httpCode = 401)
        override suspend fun login(username: String, password: String): JSONObject = login(username, password, null)
        override suspend fun login(username: String, password: String, totp: String?): JSONObject { sentCode = totp; return answer(totp) }
        override suspend fun logout(token: String) {}
    }

    private fun needCode(): Nothing = throw HydraApiException("x", httpCode = 401, serverMessage = "A second-factor code is required", totpRequired = true)

    @Test
    fun anAccountWithTheSecondFactorAsksForTheCodeFirst() = runBlocking {
        val client = Client { code -> if (code == null) needCode() else JSONObject().put("success", true).put("token", "t") }
        val store = Store()
        assertEquals(SignInResult.NeedsCode, AuthManager(store).signIn(client, "ana", "pw", false, "", false))
        assertNull(client.sentCode)
        assertNull(store.saved)

        val ok = AuthManager(store).signIn(client, "ana", "pw", false, "", false, totp = " 123 456 ")
        assertTrue(ok is SignInResult.Success)
        assertEquals("123456", client.sentCode)
    }

    @Test
    fun aWrongCodeAndAWaitAreToldApartFromAWrongPassword() = runBlocking {
        val wrong = Client { needCode() }
        assertEquals(SignInResult.WrongCode, AuthManager(Store()).signIn(wrong, "ana", "pw", false, "", false, totp = "000000"))

        val slow = Client { throw HydraApiException("x", httpCode = 429, totpRequired = true, retryAfterSeconds = 17) }
        assertEquals(SignInResult.Wait(17), AuthManager(Store()).signIn(slow, "ana", "pw", false, "", false, totp = "000000"))

        val password = Client { throw HydraApiException("x", httpCode = 401, serverMessage = "Invalid credentials") }
        val failed = AuthManager(Store()).signIn(password, "ana", "bad", false, "", false)
        assertEquals("Invalid credentials", (failed as SignInResult.Failed).message)
    }

    @Test
    fun aBlankCodeSendsNone() = runBlocking {
        val client = Client { JSONObject().put("success", true).put("token", "t") }
        AuthManager(Store()).signIn(client, "ana", "pw", false, "", false, totp = "   ")
        assertNull(client.sentCode)
    }

    // ---- the real client against a fake server ----

    private lateinit var server: MockWebServer
    private lateinit var api: HydraApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HydraApiClient("127.0.0.1", server.port)
        api.authToken = "tok"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun theSignInCarriesTheCodeOnlyWhenThereIsOne() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"token":"t"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"token":"t"}"""))
        api.login("ana", "pw")
        api.login("ana", "pw", "123456")
        assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("totp"))
        assertEquals("123456", JSONObject(server.takeRequest().body.readUtf8()).getString("totp"))
    }

    @Test
    fun aRefusalKeepsTheCodeFlagAndTheWait() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "21").setBody("""{"error":"Too many","totpRequired":true}"""))
        try {
            runBlocking { api.login("ana", "pw", "000000") }
            fail("expected a refusal")
        } catch (e: HydraApiException) {
            assertEquals(429, e.httpCode)
            assertTrue(e.totpRequired)
            assertEquals(21, e.retryAfterSeconds)
        }
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Invalid credentials"}"""))
        try {
            runBlocking { api.login("ana", "bad") }
            fail("expected a refusal")
        } catch (e: HydraApiException) {
            assertFalse(e.totpRequired)
            assertNull(e.retryAfterSeconds)
        }
    }

    @Test
    fun theFiveCallsGoToTheirRoutesWithTheirBodies() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"enabled":true,"recoveryLeft":4}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"secret":"JBSWY3DPEHPK3PXP","uri":"otpauth://totp/HYDRA:ana?secret=JBSWY3DPEHPK3PXP"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"recoveryCodes":["0a1b2-c3d4e"]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"recoveryCodes":["99999-88888"]}"""))

        assertEquals(4, api.totpStatus().recoveryLeft)
        assertEquals("JBSWY3DPEHPK3PXP", api.totpBegin("pw").secret)
        assertEquals(listOf("0a1b2-c3d4e"), api.totpConfirm("123456").recoveryCodes)
        assertTrue(api.totpDisable("pw", "123456").success)
        assertEquals(listOf("99999-88888"), api.totpNewRecoveryCodes("pw", "123456").recoveryCodes)

        val sent = (1..5).map { server.takeRequest() }
        assertEquals(
            listOf("GET /api/totp/status", "POST /api/totp/begin", "POST /api/totp/confirm", "POST /api/totp/disable", "POST /api/totp/recovery-codes"),
            sent.map { "${it.method} ${it.path}" },
        )
        assertTrue(sent.all { it.getHeader("Authorization") == "Bearer tok" })
        assertEquals("pw", JSONObject(sent[1].body.readUtf8()).getString("password"))
        val renew = JSONObject(sent[4].body.readUtf8())
        assertEquals("pw", renew.getString("password"))
        assertEquals("123456", renew.getString("code"))
    }

    @Test
    fun anAnswerWithoutTheShapeOfTheContractIsARefusalNotACrash() {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        try {
            runBlocking { api.totpConfirm("123456") }
            fail("expected a refusal")
        } catch (e: HydraApiException) {
            assertNull(e.httpCode)
        }
    }
}
