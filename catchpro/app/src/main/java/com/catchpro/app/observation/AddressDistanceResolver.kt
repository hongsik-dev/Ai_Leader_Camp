package com.catchpro.app.observation

import android.content.Context
import android.location.Geocoder
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AddressDistanceResolver(
    context: Context,
) {
    private val geocoder = Geocoder(context.applicationContext, Locale.KOREA)
    private val coordinateCache = object : LinkedHashMap<String, GeoPoint?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GeoPoint?>?): Boolean {
            return size > 64
        }
    }
    private val administrativeLabelCache = object : LinkedHashMap<String, String?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean {
            return size > 64
        }
    }

    fun warm(address: String?) {
        if (address.isNullOrBlank()) return
        resolve(address)
    }

    fun distanceKm(fromAddress: String?, toAddress: String?): Double? {
        if (fromAddress.isNullOrBlank() || toAddress.isNullOrBlank()) return null
        val fromPoint = resolve(fromAddress) ?: return null
        val toPoint = resolve(toAddress) ?: return null
        return haversineDistanceKm(fromPoint, toPoint)
    }

    fun distanceKmFrom(
        latitude: Double,
        longitude: Double,
        toAddress: String?,
    ): Double? {
        if (toAddress.isNullOrBlank()) return null
        val toPoint = resolve(toAddress) ?: return null
        return haversineDistanceKm(
            from = GeoPoint(latitude = latitude, longitude = longitude),
            to = toPoint,
        )
    }

    fun administrativeLabel(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val key = address.normalizeAddressKey()
        if (administrativeLabelCache.containsKey(key)) {
            return administrativeLabelCache[key]
        }

        val resolvedLabel = runCatching {
            if (!Geocoder.isPresent()) {
                null
            } else {
                geocoder.getFromLocationName(address, 1)
                    ?.firstOrNull()
                    ?.toAdministrativeLabel()
            }
        }.getOrNull()

        administrativeLabelCache[key] = resolvedLabel
        return resolvedLabel
    }

    @Suppress("DEPRECATION")
    private fun resolve(address: String): GeoPoint? {
        val key = address.normalizeAddressKey()
        if (coordinateCache.containsKey(key)) {
            return coordinateCache[key]
        }

        val resolvedPoint = runCatching {
            if (!Geocoder.isPresent()) {
                null
            } else {
                geocoder.getFromLocationName(address, 1)
                    ?.firstOrNull()
                    ?.let { GeoPoint(latitude = it.latitude, longitude = it.longitude) }
            }
        }.getOrNull()

        coordinateCache[key] = resolvedPoint
        return resolvedPoint
    }

    private fun haversineDistanceKm(
        from: GeoPoint,
        to: GeoPoint,
    ): Double {
        val earthRadiusKm = 6371.0
        val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val fromLatitude = Math.toRadians(from.latitude)
        val toLatitude = Math.toRadians(to.latitude)

        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(fromLatitude) * cos(toLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    private fun String.normalizeAddressKey(): String {
        return lowercase(Locale.KOREA)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun android.location.Address.toAdministrativeLabel(): String? {
        val parts = listOfNotNull(
            adminArea?.trim(),
            subAdminArea?.trim(),
            locality?.trim(),
            subLocality?.trim(),
            thoroughfare?.trim(),
            featureName?.trim(),
        )
            .filter { it.isNotBlank() }
            .distinct()
        return parts.joinToString(" ").trim().ifBlank { null }
    }

    private data class GeoPoint(
        val latitude: Double,
        val longitude: Double,
    )
}
