package com.example.maps.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface OsrmApi {
    @GET("route/v1/driving/{coordinates}?overview=full&geometries=geojson")
    suspend fun getDirections(
        @Path("coordinates") coordinates: String // "{lon},{lat};{lon},{lat}"
    ): DirectionsResponse
}
