// =============================================================================
// HYDRA-UMC Android Control - network/HydraApiClient.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Talks the exact contract in HYDRA-UMC-STUDIO/docs/REMOTE_API.md - the same
// one HYDRA-UMC SUITE's own hydra_suite/net/discovery.py and
// hydra_suite/net/client.py implement in Python:
//   - GET  /api/hydra-info  - discovery/identity (section 1), 404 if
//     SystemSettings.remoteAccess.enabled is explicitly false
//   - GET  /api/settings    - full current state (section 2)
//   - POST /api/settings    - overwrite the whole state, read-modify-write,
//     no granular per-field PATCH exists
//
// Deliberately built on plain OkHttp + org.json rather than Retrofit/Gson -
// this app used to (wrongly) define its own REST surface
// (POST /api/robots/{id}/command|jog|speed|atc) that does not exist on any
// real HYDRA-UMC STUDIO server; the actual server only ever exposes the 2
// endpoints above plus the /ws WebSocket (see HydraWebSocket.kt). Every
// mutation in this app (jog, speed, tool change, play/pause/stop, enable)
// goes through HydraState (model/HydraState.kt) and comes back here as a
// whole-object POST /api/settings, exactly like HYDRA-UMC-STUDIO's own
// browser UI (src/components/RobotDetail.tsx's updateRobot()) and SUITE's
// own HydraConnection.push_state().
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Thrown for anything that isn't a clean 2xx/JSON round-trip - the ViewModel
 * turns this into a real, user-visible error state instead of a swallowed
 * printStackTrace() (the old behaviour this class replaces). */
class HydraApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

class HydraApiClient(host: String, port: Int, private val client: OkHttpClient = sharedHttpClient) {

    val baseUrl: String = "http://$host:$port"

    /** GET /api/hydra-info (REMOTE_API.md section 1). Returns null - not an
     * exception - for a 404 (remote access disabled, mirrors the server's
     * own "indistinguishable from not running HYDRA-UMC STUDIO" behaviour)
     * or for anything that doesn't look like a real HYDRA-UMC STUDIO server,
     * exactly like HYDRA-UMC-SUITE's own discovery.py probe_host(): a closed
     * port, a different service, and a disabled remote-access gate all look
     * the same from here ("not a usable server"), not an error worth
     * surfacing per-host during a scan. */
    suspend fun getHydraInfo(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/hydra-info").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optString("product") != "HYDRA-UMC STUDIO") return@withContext null
                json
            }
        } catch (e: Exception) {
            // Network error / malformed JSON / timeout - same "not found" bucket.
            null
        }
    }

    /** GET /api/settings - one-shot full-state read (REMOTE_API.md section 2).
     * Used for the initial load before the WebSocket connects, and as a
     * manual refresh independent of live sync (mirrors SUITE's
     * HydraConnection.fetch_state()). Throws HydraApiException on anything
     * that isn't a clean 2xx JSON object - unreachable host, wrong port,
     * malformed body - so the caller can surface a real error instead of
     * silently hanging on "Conectando...". */
    suspend fun getSettings(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/settings").get().build()
        executeExpectingJson(request)
    }

    /** POST /api/settings - overwrites the whole state (REMOTE_API.md section 2).
     * Mirrors SUITE's HydraConnection.push_state() REST fallback path (used
     * here whenever the WebSocket isn't open; HydraWebSocket.send() is used
     * instead when it is, to avoid the extra round trip). */
    suspend fun postSettings(payload: JSONObject): Unit = withContext(Dispatchers.IO) {
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("$baseUrl/api/settings").post(body).build()
        executeExpectingJson(request)
        Unit
    }

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
        /** One shared client (connection pool + dispatcher threads) for the whole
         * app instead of a new OkHttpClient() per connect() attempt - the old
         * WebSocketManager/RobotViewModel created a fresh client on every
         * "Guardar y Conectar" tap, which is wasteful (each OkHttpClient owns
         * its own thread pool) and non-idiomatic. Shared by HydraApiClient and
         * HydraWebSocket alike. */
        val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
