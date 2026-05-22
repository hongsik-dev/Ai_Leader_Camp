package com.catchpro.app.observation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

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

    @SuppressLint("MissingPermission")
    suspend fun currentOrLastKnownLocation(): DeviceLocationResult {
        val lastKnown = lastKnownLocation()
        if (!hasLocationPermission(context)) {
            return lastKnown
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val freshLocation = locationManager.currentLocationFromEnabledProviders()
            ?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }

        return freshLocation?.let { location ->
            DeviceLocationResult(
                location = DeviceLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                    capturedAtMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                ),
                failureReason = null,
            )
        } ?: lastKnown
    }

    @SuppressLint("MissingPermission")
    private suspend fun LocationManager.awaitCurrentLocation(provider: String): Location? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                getCurrentLocation(
                    provider,
                    cancellationSignal,
                    DirectExecutor,
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
            }
        } else {
            @Suppress("DEPRECATION")
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
                requestSingleUpdate(provider, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { removeUpdates(listener) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun LocationManager.currentLocationFromEnabledProviders(): Location? {
        return enabledProvidersForCurrentRequest()
            .firstNotNullOfOrNull { provider ->
                withTimeoutOrNull(CurrentLocationTimeoutMillis) {
                    awaitCurrentLocation(provider)
                }?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
            }
    }

    private fun LocationManager.enabledProvidersForCurrentRequest(): List<String> {
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { isProviderEnabled(provider) }.getOrDefault(false)
            }
    }

    companion object {
        private const val MaxLocationAgeMillis = 10 * 60 * 1000L
        private const val CurrentLocationTimeoutMillis = 2500L
        private val DirectExecutor = Executor { command -> command.run() }

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
