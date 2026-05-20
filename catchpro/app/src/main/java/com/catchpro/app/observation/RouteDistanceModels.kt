package com.catchpro.app.observation

sealed interface RouteWaypoint {
    data class LatLng(
        val latitude: Double,
        val longitude: Double,
    ) : RouteWaypoint

    data class Address(
        val address: String,
    ) : RouteWaypoint
}

data class RouteDistanceOutcome(
    val distanceKm: Double? = null,
    val duration: String? = null,
    val path: List<RouteWaypoint.LatLng> = emptyList(),
    val failureReason: String? = null,
) {
    companion object {
        fun success(
            distanceKm: Double,
            duration: String? = null,
            path: List<RouteWaypoint.LatLng> = emptyList(),
        ): RouteDistanceOutcome = RouteDistanceOutcome(
            distanceKm = distanceKm,
            duration = duration,
            path = path,
        )

        fun failure(reason: String): RouteDistanceOutcome = RouteDistanceOutcome(
            failureReason = reason,
        )
    }
}
