package com.example.maps.data

// Esta clase representa el JSON completo que responde la API
data class RouteResponse(
    val routes: List<Route>
)
data class Route(
    val overview_polyline: PolylineData
)
data class PolylineData(
    val points: String
)