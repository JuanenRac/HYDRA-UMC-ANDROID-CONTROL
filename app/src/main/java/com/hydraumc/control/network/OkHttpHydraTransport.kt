// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - the transport that carries the requests of the typed server client over the HTTP client of the app
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import hydra.umc.client.HydraReply
import hydra.umc.client.HydraRequest
import hydra.umc.client.HydraTransport
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Carries the requests of `HydraServerClient` (the client the SDK generates from the server contract) over the HTTP client of the app.
 *
 * Everything the hand-written calls of [HydraApiClient] rely on stays in force, because the request goes through the same code:
 * the client of the user's TLS settings (pinned fingerprint, own CA, time limits), the interceptors that name the client and guard plain remotes
 * and Listen only, the `Authorization` bearer header (left out for a route whose access is `public`), and the distinguished errors: a refusal is a
 * [HydraApiException] with its status, its stable `code`, its text and its revision, and a network failure is one without a status.
 *
 * The answer is handed back as the decoded JSON model the generated client reads: `Map`, `List`, `String`, `Long`, `Double`, `Boolean` or null
 * (an empty answer is null). A route that declares response headers gets a [HydraReply] with those headers under their declared names.
 *
 * [exchange] sends one request with the given client and returns the text of the answer with its headers; it throws [HydraApiException].
 */
internal class OkHttpHydraTransport(
    private val baseUrl: () -> String,
    private val authToken: () -> String?,
    private val client: () -> OkHttpClient,
    private val exchange: (Request, OkHttpClient) -> Pair<String, Headers>,
) : HydraTransport {

    override suspend fun send(request: HydraRequest): Any? {
        val url = (baseUrl() + request.path).toHttpUrlOrNull()
            ?: throw HydraApiException("Not a usable address: ${baseUrl()}${request.path}")
        val builder = Request.Builder().url(url.newBuilder().apply { request.query.forEach { (name, value) -> addQueryParameter(name, value) } }.build())
        if (request.access != "public") builder.header("Authorization", "Bearer ${authToken() ?: ""}")
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        builder.method(request.method, bodyOf(request))
        val (text, headers) = exchange(builder.build(), client())
        val body = when (request.responseType) {
            null -> decode(text)
            "text" -> text
            else -> throw HydraApiException("The app does not read a ${request.responseType} answer from ${request.path}")
        }
        if (request.replyHeaders.isEmpty()) return body
        return HydraReply(body, request.replyHeaders.mapNotNull { name -> headers[name]?.let { name to it } }.toMap())
    }

    private fun bodyOf(request: HydraRequest): okhttp3.RequestBody? {
        val needsBody = request.method in setOf("POST", "PUT", "PATCH")
        val body = request.body
        if (body == null) return if (needsBody) "{}".toRequestBody(JSON) else null
        val type = request.contentType?.toMediaType() ?: JSON
        val text = if (request.contentType != null && body is String) body else encode(body).toString()
        return text.toRequestBody(type)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The JSON text of an answer as the model the generated client reads; an empty answer is null, text that is not JSON is a [HydraApiException]. */
        fun decode(text: String): Any? {
            if (text.isBlank()) return null
            return try {
                model(JSONTokener(text).nextValue())
            } catch (e: org.json.JSONException) {
                throw HydraApiException("The answer is not valid JSON: ${e.message}", e)
            }
        }

        private fun model(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> value.keys().asSequence().associateWithTo(LinkedHashMap<String, Any?>()) { model(value.get(it)) }
            is JSONArray -> (0 until value.length()).map { model(value.get(it)) }
            is Int -> value.toLong()
            else -> value
        }

        /** The JSON value of a model the generated client writes (the other way of [decode]). */
        fun encode(value: Any?): Any = when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().also { json -> value.forEach { (key, item) -> json.put(key as String, encode(item)) } }
            is Iterable<*> -> JSONArray().also { array -> value.forEach { array.put(encode(it)) } }
            else -> value as Any
        }
    }
}
