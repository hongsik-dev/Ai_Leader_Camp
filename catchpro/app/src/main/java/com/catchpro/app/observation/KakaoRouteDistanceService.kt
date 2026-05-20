package com.catchpro.app.observation

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class KakaoRouteDistanceService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    fun drivingDistanceKm(
        apiKey: String,
        origin: RouteWaypoint,
        destination: RouteWaypoint,
    ): RouteDistanceOutcome {
        if (apiKey.isBlank()) {
            return RouteDistanceOutcome.failure("카카오 REST API 키가 비어 있습니다.")
        }

        val originPoint = resolvePoint(apiKey, origin)
            ?: return RouteDistanceOutcome.failure("카카오 Local API가 출발지 좌표를 찾지 못했습니다.")
        val destinationPoint = resolvePoint(apiKey, destination)
            ?: return RouteDistanceOutcome.failure("카카오 Local API가 목적지 좌표를 찾지 못했습니다.")

        val url = buildString {
            append(KakaoDirectionsUrl)
            append("?origin=${originPoint.longitude},${originPoint.latitude}")
            append("&destination=${destinationPoint.longitude},${destinationPoint.latitude}")
            append("&priority=RECOMMEND")
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "KakaoAK $apiKey")
            .header("Content-Type", "application/json")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use RouteDistanceOutcome.failure(
                        "카카오 길찾기 요청 실패(${response.code}): ${body.kakaoErrorMessageOrBlank()}".trim(),
                    )
                }

                val json = JSONObject(body)
                val route = json.optJSONArray("routes")
                    ?.optJSONObject(0)
                    ?: return@use RouteDistanceOutcome.failure("카카오 길찾기가 경로를 반환하지 않았습니다.")
                val resultCode = route.optInt("result_code", 0)
                if (resultCode != 0) {
                    return@use RouteDistanceOutcome.failure(
                        "카카오 길찾기 실패($resultCode): ${route.optString("result_msg").ifBlank { "원인 미확인" }}",
                    )
                }
                val summary = route.optJSONObject("summary")
                    ?: return@use RouteDistanceOutcome.failure("카카오 길찾기 응답에 요약 정보가 없습니다.")
                val distanceMeters = summary.optInt("distance", -1)
                if (distanceMeters < 0) {
                    return@use RouteDistanceOutcome.failure("카카오 길찾기 응답에 거리값이 없습니다.")
                }

                RouteDistanceOutcome.success(
                    distanceKm = distanceMeters / 1000.0,
                    duration = summary.optInt("duration", -1)
                        .takeIf { it >= 0 }
                        ?.let { "${it}s" },
                )
            }
        }.getOrElse { error ->
            RouteDistanceOutcome.failure("카카오 길찾기 호출 중 오류: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun resolvePoint(
        apiKey: String,
        waypoint: RouteWaypoint,
    ): KakaoPoint? {
        return when (waypoint) {
            is RouteWaypoint.LatLng -> KakaoPoint(
                longitude = waypoint.longitude,
                latitude = waypoint.latitude,
            )
            is RouteWaypoint.Address -> geocodeAddress(apiKey, waypoint.address)
        }
    }

    private fun geocodeAddress(
        apiKey: String,
        address: String,
    ): KakaoPoint? {
        return address.kakaoSearchQueries()
            .firstNotNullOfOrNull { query ->
                searchAddress(apiKey = apiKey, query = query)
                    ?: searchKeyword(apiKey = apiKey, query = query)
            }
    }

    private fun searchAddress(
        apiKey: String,
        query: String,
    ): KakaoPoint? {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$KakaoAddressSearchUrl?query=$encodedQuery")
            .header("Authorization", "KakaoAK $apiKey")
            .build()

        return searchPoint(request)
    }

    private fun searchKeyword(
        apiKey: String,
        query: String,
    ): KakaoPoint? {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$KakaoKeywordSearchUrl?query=$encodedQuery")
            .header("Authorization", "KakaoAK $apiKey")
            .build()

        return searchPoint(request)
    }

    private fun searchPoint(request: Request): KakaoPoint? {
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val document = json.optJSONArray("documents")?.optJSONObject(0)
                    ?: return@use null
                val longitude = document.optString("x").toDoubleOrNull()
                    ?: return@use null
                val latitude = document.optString("y").toDoubleOrNull()
                    ?: return@use null
                KakaoPoint(longitude = longitude, latitude = latitude)
            }
        }.getOrNull()
    }

    private fun String.kakaoSearchQueries(): List<String> {
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

    private fun String.kakaoErrorMessageOrBlank(): String {
        if (isBlank()) return ""
        return runCatching {
            val json = JSONObject(this)
            json.optString("msg")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
        }.getOrDefault("")
    }

    private data class KakaoPoint(
        val longitude: Double,
        val latitude: Double,
    )

    private companion object {
        const val KakaoAddressSearchUrl = "https://dapi.kakao.com/v2/local/search/address.json"
        const val KakaoKeywordSearchUrl = "https://dapi.kakao.com/v2/local/search/keyword.json"
        const val KakaoDirectionsUrl = "https://apis-navi.kakaomobility.com/v1/directions"
    }
}
