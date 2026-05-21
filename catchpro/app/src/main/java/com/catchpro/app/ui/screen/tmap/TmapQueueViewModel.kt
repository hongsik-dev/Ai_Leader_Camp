package com.catchpro.app.ui.screen.tmap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.data.sync.RouteAddressCloudSyncManager
import com.catchpro.app.data.sync.RouteAddressCloudSyncStatus
import com.catchpro.app.observation.DeviceLocation
import com.catchpro.app.observation.DeviceLocationProvider
import com.catchpro.app.observation.NaverRouteDistanceService
import com.catchpro.app.observation.RouteDistanceOutcome
import com.catchpro.app.observation.RouteWaypoint
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TmapRouteStopUiModel(
    val sourceIndex: Int,
    val address: String,
    val legDistanceKm: Double?,
    val legDurationText: String?,
)

data class TmapRoutePlanUiModel(
    val stops: List<TmapRouteStopUiModel>,
    val totalDistanceText: String,
    val totalDurationText: String?,
    val calculationMode: String,
)

data class RouteAddressMapPointUiModel(
    val sourceIndex: Int,
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKmFromCurrentLocation: Double?,
)

data class RouteAddressNearestStopUiModel(
    val order: Int,
    val sourceIndex: Int,
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val fromLabel: String,
    val legDistanceKm: Double?,
    val legDurationSeconds: Int?,
    val legDurationText: String?,
    val legFailureReason: String? = null,
    val routePath: List<RouteAddressRoutePointUiModel> = emptyList(),
)

data class RouteAddressRoutePointUiModel(
    val latitude: Double,
    val longitude: Double,
)

data class RouteAddressMapUiState(
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentAccuracyMeters: Float? = null,
    val points: List<RouteAddressMapPointUiModel> = emptyList(),
    val nearestStops: List<RouteAddressNearestStopUiModel> = emptyList(),
    val nearestTotalDistanceKm: Double? = null,
    val nearestTotalDurationText: String? = null,
    val message: String? = null,
)

data class TmapQueueUiState(
    val isOptimizing: Boolean = false,
    val isRefreshingMap: Boolean = false,
    val manualRouteAddresses: List<String> = List(ManualRouteAddressSlotCount) { "" },
    val routeAddressMap: RouteAddressMapUiState = RouteAddressMapUiState(),
    val routeAddressCloudSyncEnabled: Boolean = false,
    val routeAddressCloudSyncRoomCode: String = "",
    val routeAddressCloudSyncStatus: RouteAddressCloudSyncStatus = RouteAddressCloudSyncStatus(),
    val routePlan: TmapRoutePlanUiModel? = null,
    val message: String? = null,
)

class TmapQueueViewModel(
    private val settingsRepository: SettingsRepository,
    private val routeDistanceService: NaverRouteDistanceService,
    private val routeAddressCloudSyncManager: RouteAddressCloudSyncManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TmapQueueUiState())
    val uiState: StateFlow<TmapQueueUiState> = _uiState.asStateFlow()
    private var optimizeRouteJob: Job? = null
    private var mapRefreshJob: Job? = null
    private var optimizeRouteRequestId = 0
    private var mapRefreshRequestId = 0

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        manualRouteAddresses = settings.tmapManualRouteAddressesText.toManualRouteAddressSlots(),
                        routeAddressCloudSyncEnabled = settings.routeAddressCloudSyncEnabled,
                        routeAddressCloudSyncRoomCode = settings.routeAddressCloudSyncRoomCode,
                    )
                }
            }
        }
        viewModelScope.launch {
            routeAddressCloudSyncManager.status.collect { status ->
                _uiState.update {
                    it.copy(routeAddressCloudSyncStatus = status)
                }
            }
        }
    }

    fun clearRoutePlan() {
        _uiState.update { it.copy(routePlan = null, message = null) }
    }

    fun updateManualAddress(
        index: Int,
        address: String,
    ) {
        if (index !in 0 until ManualRouteAddressSlotCount) return
        val updated = _uiState.value.manualRouteAddresses
            .toManualRouteAddressSlots()
            .updated(index, address)
        _uiState.update {
            it.copy(
                manualRouteAddresses = updated,
                routePlan = null,
                message = null,
            )
        }
        viewModelScope.launch {
            settingsRepository.setTmapManualRouteAddressesText(updated.joinToString("\n"))
        }
    }

    fun completeManualAddress(index: Int) {
        if (index !in 0 until ManualRouteAddressSlotCount) return
        val updated = _uiState.value.manualRouteAddresses
            .toManualRouteAddressSlots()
            .updated(index, "")
        _uiState.update { current ->
            current.copy(
                manualRouteAddresses = updated,
                routePlan = null,
                routeAddressMap = current.routeAddressMap.copy(
                    points = current.routeAddressMap.points.filterNot { it.sourceIndex == index },
                    nearestStops = emptyList(),
                    nearestTotalDistanceKm = null,
                    nearestTotalDurationText = null,
                    message = "방문 완료 주소를 삭제했습니다. 지도를 갱신합니다.",
                ),
                message = "주소 ${index + 1} 방문 완료로 삭제했습니다.",
            )
        }
        viewModelScope.launch {
            settingsRepository.setTmapManualRouteAddressesText(updated.joinToString("\n"))
        }
    }

    fun clearManualAddresses() {
        _uiState.update {
            it.copy(
                manualRouteAddresses = List(ManualRouteAddressSlotCount) { "" },
                routePlan = null,
                message = null,
            )
        }
        viewModelScope.launch {
            settingsRepository.setTmapManualRouteAddressesText("")
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun setRouteAddressCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRouteAddressCloudSyncEnabled(enabled)
        }
    }

    fun setRouteAddressCloudSyncRoomCode(value: String) {
        viewModelScope.launch {
            settingsRepository.setRouteAddressCloudSyncRoomCode(value)
        }
    }

    fun optimizeRoute(
        context: Context,
        addresses: List<String>,
    ) {
        val appContext = context.applicationContext
        val requestId = ++optimizeRouteRequestId
        optimizeRouteJob?.cancel()
        optimizeRouteJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOptimizing = true,
                    message = null,
                )
            }

            val result = withContext(Dispatchers.IO) {
                buildOptimizedRoutePlan(
                    context = appContext,
                    addresses = addresses,
                )
            }
            if (requestId != optimizeRouteRequestId) return@launch

            _uiState.update {
                it.copy(
                    isOptimizing = false,
                    routePlan = result.plan,
                    message = result.message,
                )
            }
        }
    }

    fun refreshAddressMap(
        context: Context,
        addresses: List<String> = _uiState.value.manualRouteAddresses,
    ) {
        val appContext = context.applicationContext
        val requestId = ++mapRefreshRequestId
        val requestedAddresses = addresses.toManualRouteAddressSlots()
        mapRefreshJob?.cancel()
        mapRefreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingMap = true) }
            delay(MapRefreshDebounceMillis)
            val mapState = withContext(Dispatchers.IO) {
                buildRouteAddressMapState(
                    context = appContext,
                    addresses = requestedAddresses,
                )
            }
            if (requestId != mapRefreshRequestId) return@launch
            _uiState.update {
                it.copy(
                    isRefreshingMap = false,
                    routeAddressMap = mapState,
                )
            }
        }
    }

    private suspend fun buildOptimizedRoutePlan(
        context: Context,
        addresses: List<String>,
    ): RouteOptimizationResult {
        val inputs = addresses
            .mapIndexedNotNull { index, value ->
                value.cleanAddressInput()
                    ?.let { ManualRouteAddressInput(sourceIndex = index, address = it) }
            }
            .distinctBy { it.address.normalizeAddressKey() }

        if (inputs.isEmpty()) {
            return RouteOptimizationResult(message = "주소를 1개 이상 붙여넣어 주세요.")
        }

        val locationResult = DeviceLocationProvider(context).lastKnownLocation()
        val currentLocation = locationResult.location
            ?: return RouteOptimizationResult(
                message = locationResult.failureReason ?: "현재 위치를 가져오지 못했습니다.",
            )

        if (inputs.size == 1) {
            return RouteOptimizationResult(
                plan = TmapRoutePlanUiModel(
                    stops = listOf(
                        TmapRouteStopUiModel(
                            sourceIndex = inputs.first().sourceIndex,
                            address = inputs.first().address,
                            legDistanceKm = null,
                            legDurationText = null,
                        ),
                    ),
                    totalDistanceText = "주소 1개",
                    totalDurationText = null,
                    calculationMode = "입력 순서",
                ),
            )
        }

        val naverClientId = BuildConfig.NAVER_MAP_NCP_KEY_ID.trim()
        val naverClientSecret = BuildConfig.NAVER_MAP_NCP_KEY.trim()
        var naverFailureReason: String? = null
        if (naverClientId.isNotBlank() && naverClientSecret.isNotBlank()) {
            val naverResult = buildNaverRoutePlan(
                clientId = naverClientId,
                clientSecret = naverClientSecret,
                currentLocation = currentLocation,
                inputs = inputs,
            )
            if (naverResult.plan != null) {
                return RouteOptimizationResult(plan = naverResult.plan)
            }
            naverFailureReason = naverResult.failureReason
        }

        return RouteOptimizationResult(
            message = when {
                naverClientId.isBlank() || naverClientSecret.isBlank() -> "네이버 Maps API 키가 필요합니다."
                naverFailureReason.isNullOrBlank() -> "네이버 주행거리 계산이 실패했습니다. 네이버 Maps API 권한/인증 정보를 확인해 주세요."
                else -> "네이버 주행거리 계산 실패: $naverFailureReason"
            },
        )
    }

    private fun buildNaverRoutePlan(
        clientId: String,
        clientSecret: String,
        currentLocation: DeviceLocation,
        inputs: List<ManualRouteAddressInput>,
    ): NaverRoutePlanResult {
        val currentWaypoint = RouteWaypoint.LatLng(
            latitude = currentLocation.latitude,
            longitude = currentLocation.longitude,
        )
        val failureReasons = mutableListOf<String>()

        val resolvedInputs = inputs.mapNotNull { input ->
            val point = routeDistanceService.geocodeAddress(
                clientId = clientId,
                clientSecret = clientSecret,
                address = input.address,
            )?.let {
                GeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            }
            if (point == null) {
                failureReasons += "주소 ${input.sourceIndex + 1}: 네이버 Geocoding 좌표 없음"
                null
            } else {
                ResolvedManualRouteAddressInput(input = input, point = point)
            }
        }
        if (resolvedInputs.size != inputs.size) {
            return NaverRoutePlanResult(
                failureReason = failureReasons
                    .distinct()
                    .take(3)
                    .joinToString(" / ")
                    .ifBlank { "일부 주소의 좌표를 찾지 못했습니다." },
            )
        }

        val remaining = resolvedInputs.toMutableList()
        val stops = mutableListOf<TmapRouteStopUiModel>()
        var previousWaypoint: RouteWaypoint = currentWaypoint
        var previousLabel = "현재 위치"
        var totalDistanceKm = 0.0
        var totalDurationSeconds: Int? = 0

        while (remaining.isNotEmpty()) {
            val selected = remaining
                .mapNotNull { input ->
                    val outcome = routeDistanceService.drivingDistanceKm(
                        clientId = clientId,
                        clientSecret = clientSecret,
                        origin = previousWaypoint,
                        destination = input.point.toRouteWaypoint(),
                    )
                    val distanceKm = outcome.distanceKm
                    if (distanceKm == null) {
                        val reason = outcome.failureReason.orEmpty().ifBlank { "원인 미확인" }
                        failureReasons += "$previousLabel → 주소 ${input.input.sourceIndex + 1}: $reason"
                        null
                    } else {
                        val durationSeconds = outcome.duration?.toDurationSeconds()
                        GreedyRouteCandidate(
                            input = input,
                            distanceKm = distanceKm,
                            durationSeconds = durationSeconds,
                        )
                    }
                }
                .minWithOrNull(
                    compareBy<GreedyRouteCandidate> { it.distanceKm }
                        .thenBy { it.durationSeconds ?: Int.MAX_VALUE },
                )
                ?: return NaverRoutePlanResult(
                    failureReason = failureReasons
                        .distinct()
                        .take(3)
                        .joinToString(" / ")
                        .ifBlank { "$previousLabel 기준으로 다음 방문지 길찾기가 실패했습니다." },
                )

            val input = selected.input.input
            totalDistanceKm += selected.distanceKm
            totalDurationSeconds = totalDurationSeconds
                ?.let { total -> selected.durationSeconds?.let { total + it } }
            stops += TmapRouteStopUiModel(
                sourceIndex = input.sourceIndex,
                address = input.address,
                legDistanceKm = selected.distanceKm,
                legDurationText = selected.durationSeconds?.formatDurationText(),
            )
            previousWaypoint = selected.input.point.toRouteWaypoint()
            previousLabel = "주소 ${input.sourceIndex + 1}"
            remaining.remove(selected.input)
        }

        val plan = CandidateRoutePlan(
            stops = stops,
            totalDistanceKm = totalDistanceKm,
            totalDurationSeconds = totalDurationSeconds,
            calculationMode = "네이버 순차 근거리",
        ).toUiModel()

        return NaverRoutePlanResult(
            plan = plan,
            failureReason = failureReasons
                .distinct()
                .take(3)
                .joinToString(" / ")
                .ifBlank { "모든 방문 순서의 길찾기 조합이 실패했습니다." },
        )
    }

    private fun buildRouteAddressMapState(
        context: Context,
        addresses: List<String>,
    ): RouteAddressMapUiState {
        val inputs = addresses
            .mapIndexedNotNull { index, value ->
                value.cleanAddressInput()
                    ?.let { ManualRouteAddressInput(sourceIndex = index, address = it) }
            }
            .distinctBy { it.address.normalizeAddressKey() }

        val locationResult = DeviceLocationProvider(context).lastKnownLocation()
        val currentLocation = locationResult.location
        val currentPoint = currentLocation?.let {
            GeoPoint(latitude = it.latitude, longitude = it.longitude)
        }
        val points = inputs.mapNotNull { input ->
            val point = geocodeAddressWithNaver(input.address) ?: return@mapNotNull null
            RouteAddressMapPointUiModel(
                sourceIndex = input.sourceIndex,
                label = "주소 ${input.sourceIndex + 1}",
                address = input.address,
                latitude = point.latitude,
                longitude = point.longitude,
                distanceKmFromCurrentLocation = currentPoint?.let { haversineDistanceKm(it, point) },
            )
        }
        val nearestStops = if (currentPoint != null) {
            buildNearestNaverRouteStops(
                currentPoint = currentPoint,
                points = points,
                routeDistanceService = routeDistanceService,
                clientId = BuildConfig.NAVER_MAP_NCP_KEY_ID.trim(),
                clientSecret = BuildConfig.NAVER_MAP_NCP_KEY.trim(),
            )
        } else {
            emptyList()
        }
        val nearestTotalDistanceKm = nearestStops
            .takeIf { stops -> stops.isNotEmpty() && stops.all { it.legDistanceKm != null } }
            ?.sumOf { it.legDistanceKm ?: 0.0 }
        val nearestTotalDurationText = nearestStops
            .takeIf { stops -> stops.isNotEmpty() && stops.all { it.legDurationSeconds != null } }
            ?.sumOf { it.legDurationSeconds ?: 0 }
            ?.formatDurationText()

        val message = when {
            inputs.isEmpty() -> "주소를 붙여넣으면 지도에 표시됩니다."
            points.isEmpty() -> "네이버 Geocoding이 주소 좌표를 찾지 못했습니다. API 권한/인증 정보와 주소 형식을 확인해 주세요."
            currentLocation == null -> locationResult.failureReason ?: "현재 위치를 가져오지 못해 거리 계산은 생략했습니다."
            points.size < inputs.size -> "일부 주소는 네이버 Geocoding 좌표를 찾지 못했습니다."
            else -> null
        }

        return RouteAddressMapUiState(
            currentLatitude = currentLocation?.latitude,
            currentLongitude = currentLocation?.longitude,
            currentAccuracyMeters = currentLocation?.accuracyMeters,
            points = points,
            nearestStops = nearestStops,
            nearestTotalDistanceKm = nearestTotalDistanceKm,
            nearestTotalDurationText = nearestTotalDurationText,
            message = message,
        )
    }

    private fun geocodeAddressWithNaver(address: String): GeoPoint? {
        val naverClientId = BuildConfig.NAVER_MAP_NCP_KEY_ID.trim()
        val naverClientSecret = BuildConfig.NAVER_MAP_NCP_KEY.trim()
        if (naverClientId.isBlank() || naverClientSecret.isBlank()) return null
        return routeDistanceService.geocodeAddress(
            clientId = naverClientId,
            clientSecret = naverClientSecret,
            address = address,
        )?.let {
            GeoPoint(
                latitude = it.latitude,
                longitude = it.longitude,
            )
        }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            routeDistanceService: NaverRouteDistanceService,
            routeAddressCloudSyncManager: RouteAddressCloudSyncManager,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TmapQueueViewModel::class.java)) {
                        return TmapQueueViewModel(
                            settingsRepository = settingsRepository,
                            routeDistanceService = routeDistanceService,
                            routeAddressCloudSyncManager = routeAddressCloudSyncManager,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

private data class RouteOptimizationResult(
    val plan: TmapRoutePlanUiModel? = null,
    val message: String? = null,
)

private data class NaverRoutePlanResult(
    val plan: TmapRoutePlanUiModel? = null,
    val failureReason: String? = null,
)

private data class ManualRouteAddressInput(
    val sourceIndex: Int,
    val address: String,
)

private data class ResolvedManualRouteAddressInput(
    val input: ManualRouteAddressInput,
    val point: GeoPoint,
)

private data class GreedyRouteCandidate(
    val input: ResolvedManualRouteAddressInput,
    val distanceKm: Double,
    val durationSeconds: Int?,
)

private const val ManualRouteAddressSlotCount = 6
private const val MaxRoutePermutationSize = 5
private const val NearestRouteAddressLimit = 6
private const val MapRefreshDebounceMillis = 180L

private fun String.toManualRouteAddressSlots(): List<String> =
    split('\n')
        .map { it.trim() }
        .take(ManualRouteAddressSlotCount)
        .toList()
        .padManualRouteAddressSlots()

private fun List<String>.toManualRouteAddressSlots(): List<String> =
    map(String::trim)
        .take(ManualRouteAddressSlotCount)
        .padManualRouteAddressSlots()

private fun List<String>.padManualRouteAddressSlots(): List<String> =
    take(ManualRouteAddressSlotCount) + List((ManualRouteAddressSlotCount - size).coerceAtLeast(0)) { "" }

private fun List<String>.updated(
    index: Int,
    value: String,
): List<String> =
    mapIndexed { currentIndex, currentValue ->
        if (currentIndex == index) value else currentValue
    }

private data class CandidateRoutePlan(
    val stops: List<TmapRouteStopUiModel>,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Int?,
    val calculationMode: String,
) {
    fun toUiModel(): TmapRoutePlanUiModel {
        return TmapRoutePlanUiModel(
            stops = stops,
            totalDistanceText = "${totalDistanceKm.formatDistanceKm()}km",
            totalDurationText = totalDurationSeconds?.formatDurationText(),
            calculationMode = calculationMode,
        )
    }
}

private data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

private data class NearestRouteCandidate(
    val point: RouteAddressMapPointUiModel,
    val pointGeo: GeoPoint,
    val routeOutcome: RouteDistanceOutcome?,
    val durationSeconds: Int?,
)

private fun buildNearestNaverRouteStops(
    currentPoint: GeoPoint,
    points: List<RouteAddressMapPointUiModel>,
    routeDistanceService: NaverRouteDistanceService,
    clientId: String,
    clientSecret: String,
): List<RouteAddressNearestStopUiModel> {
    val remainingPoints = points
        .filter { it.sourceIndex < NearestRouteAddressLimit }
        .toMutableList()
    val stops = mutableListOf<RouteAddressNearestStopUiModel>()
    var previousPoint = currentPoint
    var previousLabel = "현재 위치"

    while (remainingPoints.isNotEmpty()) {
        val candidates = remainingPoints.map { point ->
            val pointGeo = GeoPoint(point.latitude, point.longitude)
            val outcome = if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                routeDistanceService.drivingDistanceKm(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    origin = previousPoint.toRouteWaypoint(),
                    destination = pointGeo.toRouteWaypoint(),
                )
            } else {
                null
            }
            NearestRouteCandidate(
                point = point,
                pointGeo = pointGeo,
                routeOutcome = outcome,
                durationSeconds = outcome?.duration?.toDurationSeconds(),
            )
        }
        val selected = candidates
            .filter { it.routeOutcome?.distanceKm != null }
            .minWithOrNull(
                compareBy<NearestRouteCandidate> { it.routeOutcome?.distanceKm ?: Double.MAX_VALUE }
                    .thenBy { it.durationSeconds ?: Int.MAX_VALUE },
            )
            ?: candidates.minByOrNull { haversineDistanceKm(previousPoint, it.pointGeo) }
            ?: break

        stops += selected.toNearestStop(
            order = stops.size + 1,
            fromLabel = previousLabel,
            clientId = clientId,
            clientSecret = clientSecret,
        )
        remainingPoints.remove(selected.point)
        previousPoint = selected.pointGeo
        previousLabel = "주소 ${selected.point.sourceIndex + 1}"
    }

    return stops
}

private fun NearestRouteCandidate.toNearestStop(
    order: Int,
    fromLabel: String,
    clientId: String,
    clientSecret: String,
): RouteAddressNearestStopUiModel {
    return RouteAddressNearestStopUiModel(
        order = order,
        sourceIndex = point.sourceIndex,
        label = point.label,
        address = point.address,
        latitude = point.latitude,
        longitude = point.longitude,
        fromLabel = fromLabel,
        legDistanceKm = routeOutcome?.distanceKm,
        legDurationSeconds = durationSeconds,
        legDurationText = durationSeconds?.formatDurationText(),
        legFailureReason = when {
            clientId.isBlank() || clientSecret.isBlank() -> "네이버 Maps API 키가 필요합니다."
            routeOutcome?.distanceKm == null -> routeOutcome
                ?.failureReason
                ?.ifBlank { null }
                ?: "네이버 길찾기 결과가 없습니다."
            else -> null
        },
        routePath = routeOutcome?.path
            ?.map {
                RouteAddressRoutePointUiModel(
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            }
            .orEmpty(),
    )
}

private fun GeoPoint.toRouteWaypoint(): RouteWaypoint.LatLng =
    RouteWaypoint.LatLng(
        latitude = latitude,
        longitude = longitude,
    )

private fun String.cleanAddressInput(): String? {
    return trim()
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }
}

private fun String.normalizeAddressKey(): String {
    return lowercase(Locale.KOREA)
        .replace(Regex("""[\s/(),._-]+"""), "")
        .trim()
}

private fun String.toDurationSeconds(): Int? {
    return removeSuffix("s")
        .trim()
        .toIntOrNull()
}

private fun Int.formatDurationText(): String {
    val minutes = this / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}시간 ${remainingMinutes}분"
        hours > 0 -> "${hours}시간"
        minutes > 0 -> "${minutes}분"
        else -> "1분 미만"
    }
}

private fun Double.formatDistanceKm(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        "%.1f".format(Locale.getDefault(), this)
    }
}

private fun <T> List<T>.permutations(): List<List<T>> {
    if (size <= 1) return listOf(this)
    return flatMapIndexed { index, item ->
        (take(index) + drop(index + 1))
            .permutations()
            .map { listOf(item) + it }
    }
}

private fun List<ManualRouteAddressInput>.routePermutations(): List<List<ManualRouteAddressInput>> =
    if (size <= MaxRoutePermutationSize) permutations() else listOf(this)

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
