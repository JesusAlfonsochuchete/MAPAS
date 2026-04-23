package com.example.maps.network

import com.example.maps.data.RouteResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface RouteApiService {
    // Se concatena a la base URL para obtener la ruta
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String
    ): RouteResponse
}

object RetrofitInstance {
    private const val BASE_URL = "https://maps.googleapis.com/"

    val api: RouteApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RouteApiService::class.java)
    }
}