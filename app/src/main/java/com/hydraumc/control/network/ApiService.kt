package com.hydraumc.control.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class StatusResponse(val status: String)

interface ApiService {
    @GET("api/status")
    suspend fun getStatus(): StatusResponse

    companion object {
        // REPLACE with your server IP/hostname
        private const val BASE_URL = "http://192.168.1.100:3000/"

        fun create(): ApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
