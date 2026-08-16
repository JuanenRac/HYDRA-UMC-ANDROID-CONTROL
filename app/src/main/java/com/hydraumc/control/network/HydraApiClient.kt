// =============================================================================
// HYDRA-UMC CONTROL - REST API client for communication with HYDRA-UMC STUDIO
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
                if (json.optString("product") != "HYDRA-UMC STUDIO") return@withContext null
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
        val request = Request.Builder().url("$baseUrl/api/settings").get().build()
        executeExpectingJson(request)
    }

    /** 
     * Overwrites the full system settings on the server.
     * @param payload The new state to upload.
     * @throws HydraApiException if the request fails.
     */
    suspend fun postSettings(payload: JSONObject): Unit = withContext(Dispatchers.IO) {
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("$baseUrl/api/settings").post(body).build()
        executeExpectingJson(request)
        Unit
    }

    /** 
     * Internal helper to execute a request and ensure the response is a JSON object.
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
         */
        val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
