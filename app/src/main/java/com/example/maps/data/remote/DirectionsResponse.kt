package com.example.maps.data.remote

import com.google.gson.annotations.SerializedName

data class DirectionsResponse(
    @SerializedName("routes") val routes: List<Route>?
)

data class Route(
    @SerializedName("geometry") val geometry: Geometry?
)

data class Geometry(
    @SerializedName("coordinates") val coordinates: List<List<Double>>?
)
