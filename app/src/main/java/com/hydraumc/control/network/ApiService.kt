package com.hydraumc.control.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class CommandRequest(val command: String)
data class CommandResponse(val success: Boolean, val error: String?)

data class JogRequest(val target: String, val axis: String, val amount: Double)
data class SpeedRequest(val speed: Double?, val acceleration: Double?)
data class AtcRequest(val slot: Int)

interface ApiService {
    @POST("api/robots/{id}/command")
    suspend fun sendCommand(@Path("id") robotId: Int, @Body request: CommandRequest): CommandResponse

    @POST("api/robots/{id}/jog")
    suspend fun jogRobot(@Path("id") robotId: Int, @Body request: JogRequest): CommandResponse

    @POST("api/robots/{id}/speed")
    suspend fun setSpeed(@Path("id") robotId: Int, @Body request: SpeedRequest): CommandResponse

    @POST("api/robots/{id}/atc")
    suspend fun changeTool(@Path("id") robotId: Int, @Body request: AtcRequest): CommandResponse

    companion object {
        fun create(baseUrl: String): ApiService {
            var url = baseUrl
            if (!url.endsWith("/")) url += "/"
            if (!url.startsWith("http")) url = "http://$url"
            
            return Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
