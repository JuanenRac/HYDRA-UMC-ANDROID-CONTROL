// =============================================================================
// HYDRA-UMC CONTROL - REST API client for communication with HYDRA-UMC STUDIO
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Talks the exact contract in HYDRA-UMC-STUDIO/docs/REMOTE_API.md (that
// document itself admits it can drift from server.ts, the real source of
// truth - verify against the server's own code before trusting it blindly):
//   - GET  /api/hydra-info          - discovery/identity, 404 if
//     SystemSettings.remoteAccess.enabled is explicitly false
//   - GET  /api/settings            - full current state
//   - POST /api/settings            - overwrite the whole state,
//     read-modify-write, no granular per-field PATCH exists
//   - POST /api/robot/:id/command   - atomic per-robot command (stop/play/
//     pause/jog/tool/valve/pump/speed/vision) - server.ts computes
//     affectedIds (self + combinedWith) itself, persists to disk, and
//     broadcasts a WS "delta" to every OTHER client on its own. This is
//     what every mutation in this app (RobotViewModel.kt's own
//     sendAtomicCommand()) actually uses as of 2026-08-19 - a much smaller
//     payload than a full POST /api/settings for a single jog tick, and it
//     used to sit here completely unused (postRobotCommand() was defined
//     but never called anywhere, while every mutation instead did a full
//     whole-object POST /api/settings via HydraState.toJson(), same as
//     HYDRA-UMC-STUDIO's own browser UI's updateRobot() and SUITE's own
//     push_state() - both since fixed the same day, see those projects' own
//     SONNET/ tracking files). postSettings() below is still used for the
//     one-time full sync on connect() and for factory-reset-shaped writes,
//     just no longer for every single robot command.
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
         */
        val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
