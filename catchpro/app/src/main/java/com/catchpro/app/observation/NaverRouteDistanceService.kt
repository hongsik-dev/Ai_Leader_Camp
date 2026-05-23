package com.catchpro.app.observation

import com.catchpro.app.BuildConfig
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class NaverRouteDistanceService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    fun drivingDistanceKm(
        origin: RouteWaypoint,
        destination: RouteWaypoint,
        source: String = "navi_directions",
    ): RouteDistanceOutcome {
        if (!BuildConfig.IS_NAVI_APP) {
            return RouteDistanceOutcome.failure("네이버 길찾기는 CatchPro Navi에서만 사용할 수 있습니다.")
        }
        val proxyBaseUrl = naverProxyBaseUrl()
        if (proxyBaseUrl.isBlank()) {
            return RouteDistanceOutcome.failure("네이버 프록시 서버 주소가 비어 있습니다.")
        }

        val originPoint = resolvePoint(
            waypoint = origin,
            source = source,
        ) ?: return RouteDistanceOutcome.failure("네이버 Geocoding이 출발지 좌표를 찾지 못했습니다.")
        val destinationPoint = resolvePoint(
            waypoint = destination,
            source = source,
        ) ?: return RouteDistanceOutcome.failure("네이버 Geocoding이 목적지 좌표를 찾지 못했습니다.")

        val url = buildString {
            append(proxyBaseUrl)
            append("/directions")
            append("?start=${originPoint.longitude},${originPoint.latitude}")
            append("&goal=${destinationPoint.longitude},${destinationPoint.latitude}")
            append("&option=trafast")
            append("&source=${source.toNaverProxySourceParam()}")
        }
        val request = Request.Builder()
            .url(url)
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use RouteDistanceOutcome.failure(
                        "네이버 길찾기 요청 실패(${response.code}): ${body.naverErrorMessageOrBlank()}".trim(),
                    )
                }

                val json = JSONObject(body)
                val routeObject = json.optJSONObject("route")
                    ?: return@use RouteDistanceOutcome.failure("네이버 길찾기가 경로를 반환하지 않았습니다.")
                val route = listOf("trafast", "traoptimal", "tracomfort")
                    .firstNotNullOfOrNull { key ->
                        routeObject.optJSONArray(key)?.optJSONObject(0)
                    }
                    ?: return@use RouteDistanceOutcome.failure("네이버 길찾기가 사용 가능한 경로를 반환하지 않았습니다.")
                val summary = route.optJSONObject("summary")
                    ?: return@use RouteDistanceOutcome.failure("네이버 길찾기 응답에 요약 정보가 없습니다.")
                val distanceMeters = summary.optInt("distance", -1)
                if (distanceMeters < 0) {
                    return@use RouteDistanceOutcome.failure("네이버 길찾기 응답에 거리값이 없습니다.")
                }
                RouteDistanceOutcome.success(
                    distanceKm = distanceMeters / 1000.0,
                    duration = summary.optLong("duration", -1L)
                        .takeIf { it >= 0L }
                        ?.let { "${it / 1000}s" },
                    path = route.optJSONArray("path")
                        ?.let { path ->
                            (0 until path.length()).mapNotNull { index ->
                                val coordinate = path.optJSONArray(index) ?: return@mapNotNull null
                                val longitude = coordinate.optDouble(0, Double.NaN)
                                val latitude = coordinate.optDouble(1, Double.NaN)
                                if (longitude.isNaN() || latitude.isNaN()) {
                                    null
                                } else {
                                    RouteWaypoint.LatLng(
                                        latitude = latitude,
                                        longitude = longitude,
                                    )
                                }
                            }
                        }
                        .orEmpty(),
                )
            }
        }.getOrElse { error ->
            RouteDistanceOutcome.failure("네이버 길찾기 호출 중 오류: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun geocodeAddress(
        address: String,
        source: String = "navi_geocode",
    ): NaverPoint? {
        if (naverProxyBaseUrl().isBlank()) return null
        return address.naverSearchQueries()
            .firstNotNullOfOrNull { query ->
                searchAddress(
                    query = query,
                    source = source,
                )
            }
    }

    private fun resolvePoint(
        waypoint: RouteWaypoint,
        source: String,
    ): NaverPoint? {
        return when (waypoint) {
            is RouteWaypoint.LatLng -> NaverPoint(
                longitude = waypoint.longitude,
                latitude = waypoint.latitude,
            )
            is RouteWaypoint.Address -> geocodeAddress(
                address = waypoint.address,
                source = source,
            )
        }
    }

    private fun searchAddress(
        query: String,
        source: String,
    ): NaverPoint? {
        val proxyBaseUrl = naverProxyBaseUrl()
        if (proxyBaseUrl.isBlank()) return null
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$proxyBaseUrl/geocode?query=$encodedQuery&source=${source.toNaverProxySourceParam()}")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val address = json.optJSONArray("addresses")?.optJSONObject(0)
                    ?: return@use null
                val longitude = address.optString("x").toDoubleOrNull()
                    ?: return@use null
                val latitude = address.optString("y").toDoubleOrNull()
                    ?: return@use null
                NaverPoint(
                    longitude = longitude,
                    latitude = latitude,
                )
            }
        }.getOrNull()
    }

    private fun naverProxyBaseUrl(): String =
        BuildConfig.NAVER_PROXY_BASE_URL.trim().trimEnd('/')

    private fun String.toNaverProxySourceParam(): String =
        URLEncoder.encode(
            trim()
                .ifBlank { "unknown" }
                .replace(Regex("""[^\w.-]"""), "_")
                .take(40),
            Charsets.UTF_8.name(),
        )

    private fun String.naverSearchQueries(): List<String> {
        val normalized = trim()
            .replace(Regex("""\s+"""), " ")
        val parenthesizedQueries = Regex("""\(([^()]+)\)""")
            .findAll(normalized)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        val withoutParentheses = normalized
            .replace(Regex("""\([^()]+\)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return (
            listOf(normalized) +
                parenthesizedQueries +
                listOf(withoutParentheses) +
                normalized.extractKoreanAddressFragments()
            )
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun String.extractKoreanAddressFragments(): List<String> {
        val city = """(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주|[가-힣]+(?:특별시|광역시|특별자치시|특별자치도|도))"""
        val district = """[가-힣]+(?:시|군|구)"""
        val town = """[가-힣A-Za-z0-9]+(?:동|읍|면|리|가)"""
        val road = """[가-힣A-Za-z0-9]+(?:로|길)"""
        val number = """\d+(?:-\d+)?"""
        val roadAddressRegex = Regex("""$city?\s*$district\s+$road\s*$number""")
        val jibunAddressRegex = Regex("""$city?\s*$district\s+$town\s+$number""")
        return (roadAddressRegex.findAll(this) + jibunAddressRegex.findAll(this))
            .map { it.value.replace(Regex("""\s+"""), " ").trim() }
            .toList()
    }

    private fun String.naverErrorMessageOrBlank(): String {
        if (isBlank()) return ""
        return runCatching {
            val json = JSONObject(this)
            json.optString("errorMessage")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
        }.getOrDefault("")
    }

    data class NaverPoint(
        val longitude: Double,
        val latitude: Double,
    )
}
