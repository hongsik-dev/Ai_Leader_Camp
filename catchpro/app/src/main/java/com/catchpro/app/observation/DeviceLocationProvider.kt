package com.catchpro.app.observation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class DeviceLocationProvider(
    private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(): DeviceLocationResult {
        if (!hasLocationPermission(context)) {
            return DeviceLocationResult(
                location = null,
                failureReason = "위치 권한이 없어 현재 위치 기준 주행거리 계산을 할 수 없습니다.",
            )
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locations = runCatching {
            locationManager.getProviders(true)
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
        }.getOrDefault(emptyList())

        val bestLocation = locations
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .sortedWith(
                compareByDescending<Location> { it.time }
                    .thenBy { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE },
            )
            .firstOrNull()
            ?: return DeviceLocationResult(
                location = null,
                failureReason = "최근 현재 위치를 가져오지 못했습니다. 위치 권한과 GPS를 확인한 뒤 다시 시도해 주세요.",
            )

        val ageMillis = System.currentTimeMillis() - bestLocation.time
        if (ageMillis > MaxLocationAgeMillis) {
            return DeviceLocationResult(
                location = null,
                failureReason = "현재 위치 정보가 오래되어 주행거리 계산에 쓰지 않았습니다. 지도 앱이나 위치 서비스를 한 번 열어 현재 위치를 갱신해 주세요.",
            )
        }

        return DeviceLocationResult(
            location = DeviceLocation(
                latitude = bestLocation.latitude,
                longitude = bestLocation.longitude,
                accuracyMeters = bestLocation.accuracy.takeIf { bestLocation.hasAccuracy() },
                capturedAtMillis = bestLocation.time,
            ),
            failureReason = null,
        )
    }

    companion object {
        private const val MaxLocationAgeMillis = 10 * 60 * 1000L

        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtMillis: Long,
)

data class DeviceLocationResult(
    val location: DeviceLocation?,
    val failureReason: String?,
)
