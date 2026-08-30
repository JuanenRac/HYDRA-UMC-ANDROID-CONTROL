// =============================================================================
// HYDRA-UMC CONTROL - REST API client for communication with the HYDRA-UMC-SERVER backend
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Talks the exact contract in HYDRA-UMC-SERVER/docs/REMOTE_API.md (that
// document itself admits it can drift from server.ts, the real source of
// truth - verify against the server's own code before trusting it blindly).
// HYDRA-UMC-SERVER is the headless Node/Express+WebSocket backend split out
// of HYDRA-UMC STUDIO's own process - this client talks to that backend
// directly, not to STUDIO's (now frontend-only) web app:
//   - GET  /api/hydra-info          - discovery/identity, 404 if
//     SystemSettings.remoteAccess.enabled is explicitly false
//   - GET  /api/settings            - full current state
//   - POST /api/settings            - overwrite the whole state,
//     read-modify-write, no granular per-field PATCH exists
//   - POST /api/robot/:id/command   - atomic per-robot command (stop/play/
//     pause/jog/tool/valve/pump/speed/vision) - server.ts computes
//     affectedIds (self + combinedWith) itself, persists to disk, and
//     broadcasts a WS "delta" to every OTHER client on its own. This is what
//     every mutation in this app (RobotViewModel.kt's own
//     sendAtomicCommand()) uses: a much smaller payload than a full
//     POST /api/settings for a single jog tick, and it lets the server (not
//     every client independently) own the combinedWith fan-out logic.
//     postSettings() below is reserved for the one-time full sync on
//     connect() and for factory-reset-shaped writes, not for individual
//     robot commands.
//
// Deliberately built on plain OkHttp + org.json rather than Retrofit/Gson.
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.hydraumc.control.wear.WatchAssistantReply
import com.hydraumc.control.wear.WatchSystemStatus
import com.hydraumc.control.wear.WatchVoiceTurn
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Media type for JSON request bodies. */
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** 
 * Custom exception for errors occurring during API communication.
 * @param message Human-readable error description.
 * @param cause The underlying cause of the exception.
 */
class HydraApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Client class responsible for making HTTP requests to a HYDRA-UMC server.
 * @property host The target host address.
 * @property port The target port number.
 * @property client The OkHttpClient instance to use.
 */
class HydraApiClient(host: String, port: Int, private val client: OkHttpClient = sharedHttpClient) {

    /** Base URL of the target HYDRA-UMC server. */
    val baseUrl: String = "http://$host:$port"
    
    /** Current authentication token. */
    var authToken: String? = null

    suspend fun login(username: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("username", username).put("password", password)
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("$baseUrl/api/login").post(body).build()
        executeExpectingJson(request)
    }

    /** 
     * Performs a one-shot probe to identify a HYDRA-UMC server. 
     * @return The JSON response if successful and identified, null otherwise.
     */
    suspend fun getHydraInfo(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/hydra-info").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                // Check if it's a valid HYDRA-UMC server by looking for the remote API version
                if (!json.has("remoteApiVersion")) return@withContext null
                json
            }
        } catch (e: Exception) {
            // Network error / malformed JSON / timeout - same "not found" bucket.
            null
        }
    }

    /** 
     * Fetches the full system settings from the server.
     * @return The current state as a JSONObject.
     * @throws HydraApiException if the request fails.
     */
    suspend fun getSettings(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/settings")
            .header("Authorization", "Bearer ${authToken ?: ""}")
            .get()
            .build()
        executeExpectingJson(request)
    }

    /** 
     * Overwrites the full system settings on the server.
     */
    suspend fun postSettings(payload: JSONObject): Unit = withContext(Dispatchers.IO) {
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/settings")
            .header("Authorization", "Bearer ${authToken ?: ""}")
            .post(body)
            .build()
        executeExpectingJson(request)
        Unit
    }

    suspend fun postRobotCommand(robotId: Int, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/robot/$robotId/command")
            .header("Authorization", "Bearer ${authToken ?: ""}")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeExpectingJson(request)
    }

    /**
     * Fetches real-time system metrics from the CM5.
     */
    suspend fun getSystemMetrics(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/system/metrics")
            .header("Authorization", "Bearer ${authToken ?: ""}")
            .build()
        executeExpectingJson(request)
    }

    /**
     * Fetches the server's own real V0 ecosystem-status scan - see
     * server.ts's own getEcosystemStatus() header comment for exactly what
     * this is (sibling repos' own project manifests on the SAME machine
     * the server is running from) and isn't (not a live health check of
     * every ecosystem project as a deployed network service). Same trust
     * tier as getSystemMetrics() above.
     */
    suspend fun getEcosystemStatus(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/ecosystem/status")
            .header("Authorization", "Bearer ${authToken ?: ""}")
            .build()
        executeExpectingJson(request)
    }

    /**
     * Relays a recognised Watch voice turn through the authenticated Server
     * boundary. Server owns the Voice UI credential; Android and the watch
     * never receive it and this route cannot actuate a robot.
     */
    suspend fun postWatchVoiceTurn(turn: WatchVoiceTurn): WatchAssistantReply = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/voice/turn")
            .header("Authorization", "Bearer ${authToken ?: ""}")
            // Real, distinct client identity for this relay call - see
            // sharedHttpClient's own comment for why this must be set here
            // rather than left to the interceptor's "android" default:
            // server.ts's Config > Remote Access "Watch" toggle can only
            // gate this specific call, independent of this same phone's
            // own direct access, if it's actually labeled "watch".
            .header("X-Hydra-Client", "watch")
            .post(turn.toJson().toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        WatchAssistantReply.fromJson(executeExpectingJson(request))
    }

    /** Fetches the small authenticated health card intended for Wear surfaces. */
    suspend fun getWatchSystemStatus(): WatchSystemStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/watch/system-status")
            .header("Authorization", "Bearer ${authToken ?: ""}")
            .header("X-Hydra-Client", "watch") // see postWatchVoiceTurn()'s own comment above
            .get()
            .build()
        WatchSystemStatus.fromJson(executeExpectingJson(request))
    }

    /**
     * Executes a network request and expects a JSON object.
     * @param request The OkHttp Request to execute.
     * @return The response body parsed as a JSONObject.
     */
    private fun executeExpectingJson(request: Request): JSONObject {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw HydraApiException("No se pudo contactar con $baseUrl: ${e.message}", e)
        }
        response.use {
            if (!it.isSuccessful) {
                throw HydraApiException("HTTP ${it.code} de $baseUrl${request.url.encodedPath}")
            }
            val bodyString = it.body?.string()
                ?: throw HydraApiException("Respuesta vacía de $baseUrl${request.url.encodedPath}")
            return try {
                JSONObject(bodyString)
            } catch (e: Exception) {
                throw HydraApiException("Respuesta no es JSON válido: ${e.message}", e)
            }
        }
    }

    companion object {
        /**
         * Shared OkHttpClient instance with optimized timeouts.
         *
         * The interceptor self-identifies every request to server.ts's own
         * per-client remote-access toggles (Config > Remote Access in the
         * browser UI) - lets the project owner disable this app's own access
         * without also blocking SUITE/iOS, or vice versa. Only GET
         * /api/hydra-info actually checks this header server-side; sending
         * it on every request is simpler than special-casing just that one
         * call, and harmless everywhere else.
         *
         * Defaults to "android", but never overrides a value the request
         * itself already set - postWatchVoiceTurn()/getWatchSystemStatus()
         * below set "watch" explicitly, since those 2 calls relay a
         * request on the paired Watch's behalf rather than this app's own
         * traffic, and server.ts's own remoteAccess.watch toggle needs a
         * real, distinct header to gate on to be anything but cosmetic.
         */
        val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val clientType = original.header("X-Hydra-Client") ?: "android"
                chain.proceed(original.newBuilder().header("X-Hydra-Client", clientType).build())
            }
            .build()
    }
}
