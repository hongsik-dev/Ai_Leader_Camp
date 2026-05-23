package com.catchpro.app.ui.screen.tmap

import android.content.Context
import android.util.Log
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
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
import org.json.JSONObject

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

data class AdminAreaDistanceCandidateUiModel(
    val sourceIndex: Int,
    val label: String,
    val address: String,
    val distanceKm: Double,
)

data class AdminAreaDistanceResultUiModel(
    val query: String,
    val resolvedQuery: String,
    val candidates: List<AdminAreaDistanceCandidateUiModel>,
    val message: String? = null,
) {
    val nearest: AdminAreaDistanceCandidateUiModel?
        get() = candidates.firstOrNull()
}

data class RouteAddressRoutePointUiModel(
    val latitude: Double,
    val longitude: Double,
)

data class RouteAddressMapUiState(
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentAccuracyMeters: Float? = null,
    val routeCalculatedAtMillis: Long? = null,
    val routeOriginLatitude: Double? = null,
    val routeOriginLongitude: Double? = null,
    val routeOriginMovedKm: Double? = null,
    val points: List<RouteAddressMapPointUiModel> = emptyList(),
    val nearestStops: List<RouteAddressNearestStopUiModel> = emptyList(),
    val nearestTotalDistanceKm: Double? = null,
    val nearestTotalDurationText: String? = null,
    val message: String? = null,
)

data class TmapQueueUiState(
    val isOptimizing: Boolean = false,
    val isRefreshingMap: Boolean = false,
    val isCalculatingRoute: Boolean = false,
    val manualRouteAddresses: List<String> = List(ManualRouteAddressSlotCount) { "" },
    val routeAddressMap: RouteAddressMapUiState = RouteAddressMapUiState(),
    val routeAddressCloudSyncEnabled: Boolean = false,
    val routeAddressCloudSyncRoomCode: String = "",
    val routeAddressCloudSyncStatus: RouteAddressCloudSyncStatus = RouteAddressCloudSyncStatus(),
    val routePlan: TmapRoutePlanUiModel? = null,
    val adminAreaQueryText: String = "",
    val isResolvingAdminAreaDistance: Boolean = false,
    val adminAreaDistanceResult: AdminAreaDistanceResultUiModel? = null,
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
    private var manualRouteAddressSaveJob: Job? = null
    private var mapRefreshJob: Job? = null
    private var currentLocationRefreshJob: Job? = null
    private var routeCalculationJob: Job? = null
    private var adminAreaDistanceJob: Job? = null
    private var optimizeRouteRequestId = 0
    private var mapRefreshRequestId = 0
    private var routeCalculationRequestId = 0
    private val naverGeocodeCache = ConcurrentHashMap<String, GeoPoint>()
    private val naverRouteOutcomeCache = ConcurrentHashMap<String, RouteDistanceOutcome>()
    private var naverRouteDiskCache: NaverRouteDiskCache? = null

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
        val sanitizedAddress = address.normalizeManualAddressFieldInput()
        val updated = _uiState.value.manualRouteAddresses
            .toManualRouteAddressSlots()
            .updated(index, sanitizedAddress)
        _uiState.update {
            it.copy(
                manualRouteAddresses = updated,
                routePlan = null,
                message = null,
            )
        }
        manualRouteAddressSaveJob?.cancel()
        manualRouteAddressSaveJob = viewModelScope.launch {
            delay(ManualRouteAddressSaveDebounceMillis)
            settingsRepository.setTmapManualRouteAddressesText(updated.joinToString("\n"))
        }
    }

    fun replaceManualAddresses(addresses: List<String>) {
        val updated = addresses
            .toManualRouteAddressSlots()
            .map { it.normalizeManualAddressFieldInput() }
        _uiState.update {
            it.copy(
                manualRouteAddresses = updated,
                routePlan = null,
                message = null,
            )
        }
        manualRouteAddressSaveJob?.cancel()
        manualRouteAddressSaveJob = viewModelScope.launch {
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
                isResolvingAdminAreaDistance = false,
                adminAreaDistanceResult = null,
                message = "주소 ${index + 1} 방문 완료로 삭제했습니다.",
            )
        }
        viewModelScope.launch {
            settingsRepository.completeTmapManualRouteAddressSlot(index)
        }
    }

    fun clearManualAddresses() {
        _uiState.update {
            it.copy(
                manualRouteAddresses = List(ManualRouteAddressSlotCount) { "" },
                routePlan = null,
                routeAddressMap = it.routeAddressMap.copy(
                    points = emptyList(),
                    nearestStops = emptyList(),
                    nearestTotalDistanceKm = null,
                    nearestTotalDurationText = null,
                    routeCalculatedAtMillis = null,
                    routeOriginLatitude = null,
                    routeOriginLongitude = null,
                    routeOriginMovedKm = null,
                    message = "주소를 모두 삭제했습니다.",
                ),
                adminAreaDistanceResult = null,
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
        if (!BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC) return
        viewModelScope.launch {
            settingsRepository.setRouteAddressCloudSyncEnabled(enabled)
        }
    }

    fun setRouteAddressCloudSyncRoomCode(value: String) {
        viewModelScope.launch {
            settingsRepository.setRouteAddressCloudSyncRoomCode(value)
        }
    }

    fun updateAdminAreaQueryText(value: String) {
        _uiState.update {
            it.copy(
                adminAreaQueryText = value,
                adminAreaDistanceResult = if (value.isBlank()) null else it.adminAreaDistanceResult,
            )
        }
    }

    fun resolveAdminAreaDistance(queryOverride: String? = null) {
        if (!BuildConfig.FEATURE_NAVI_OPTIMIZATION) return
        val query = queryOverride?.trim().orEmpty().ifBlank {
            _uiState.value.adminAreaQueryText.trim()
        }
        if (queryOverride != null) {
            _uiState.update {
                it.copy(
                    adminAreaQueryText = query,
                    adminAreaDistanceResult = null,
                )
            }
        }
        adminAreaDistanceJob?.cancel()
        adminAreaDistanceJob = viewModelScope.launch {
            val points = _uiState.value.routeAddressMap.points
            when {
                query.isBlank() -> {
                    _uiState.update {
                        it.copy(
                            isResolvingAdminAreaDistance = false,
                            adminAreaDistanceResult = AdminAreaDistanceResultUiModel(
                                query = "",
                                resolvedQuery = "",
                                candidates = emptyList(),
                                message = "행정동을 입력해 주세요.",
                            ),
                        )
                    }
                    return@launch
                }

                points.isEmpty() -> {
                    _uiState.update {
                        it.copy(
                            isResolvingAdminAreaDistance = false,
                            adminAreaDistanceResult = AdminAreaDistanceResultUiModel(
                                query = query,
                                resolvedQuery = query,
                                candidates = emptyList(),
                                message = "비교할 주소가 없습니다. 주소 동기화 후 지도 갱신을 눌러 주세요.",
                            ),
                        )
                    }
                    return@launch
                }
            }

            _uiState.update {
                it.copy(
                    isResolvingAdminAreaDistance = true,
                    adminAreaDistanceResult = null,
                )
            }

            val resolved = withContext(Dispatchers.IO) {
                resolveAdminAreaQueryPoint(query)
            }
            if (resolved == null) {
                _uiState.update {
                    it.copy(
                        isResolvingAdminAreaDistance = false,
                        adminAreaDistanceResult = AdminAreaDistanceResultUiModel(
                            query = query,
                            resolvedQuery = query,
                            candidates = emptyList(),
                            message = "네이버 Geocoding이 '$query' 좌표를 찾지 못했습니다.",
                        ),
                    )
                }
                return@launch
            }

            val visitOrderBySourceIndex = _uiState.value.routeAddressMap.nearestStops
                .withIndex()
                .associate { it.value.sourceIndex to it.index }
            val currentDistanceBySourceIndex = points
                .associate { it.sourceIndex to it.distanceKmFromCurrentLocation }
            val candidates = points
                .map { point ->
                    AdminAreaDistanceCandidateUiModel(
                        sourceIndex = point.sourceIndex,
                        label = point.label,
                        address = point.address,
                        distanceKm = haversineDistanceKm(
                            from = resolved.second,
                            to = GeoPoint(
                                latitude = point.latitude,
                                longitude = point.longitude,
                            ),
                        ),
                    )
                }
                .sortedWith(
                    when {
                        visitOrderBySourceIndex.isNotEmpty() -> {
                            compareBy<AdminAreaDistanceCandidateUiModel> {
                                visitOrderBySourceIndex[it.sourceIndex] ?: Int.MAX_VALUE
                            }.thenBy { it.sourceIndex }
                        }

                        else -> {
                            compareBy<AdminAreaDistanceCandidateUiModel> {
                                currentDistanceBySourceIndex[it.sourceIndex] ?: Double.MAX_VALUE
                            }.thenBy { it.sourceIndex }
                        }
                    },
                )

            _uiState.update {
                it.copy(
                    isResolvingAdminAreaDistance = false,
                    adminAreaDistanceResult = AdminAreaDistanceResultUiModel(
                        query = query,
                        resolvedQuery = resolved.first,
                        candidates = candidates,
                    ),
                )
            }
        }
    }

    fun optimizeRoute(
        context: Context,
        addresses: List<String>,
    ) {
        if (!BuildConfig.IS_NAVI_APP) {
            _uiState.update { it.copy(message = "방문순서 계산은 CatchPro Navi에서만 사용할 수 있습니다.") }
            return
        }
        if (!BuildConfig.FEATURE_NAVI_OPTIMIZATION) {
            _uiState.update { it.copy(message = "최적 순서 계산은 Pro 또는 개인 운행판에서 사용할 수 있습니다.") }
            return
        }
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
                    includeNearestRoutes = shouldAutoCalculateMapRoutes(),
                )
            }
            if (requestId != mapRefreshRequestId) return@launch
            _uiState.update {
                it.copy(
                    isRefreshingMap = false,
                    routeAddressMap = mapState,
                    isResolvingAdminAreaDistance = false,
                    adminAreaDistanceResult = null,
                )
            }
        }
    }

    fun refreshCurrentLocation(context: Context) {
        if (!BuildConfig.IS_NAVI_APP) return
        if (currentLocationRefreshJob?.isActive == true) return
        val appContext = context.applicationContext
        currentLocationRefreshJob = viewModelScope.launch {
            val locationResult = withContext(Dispatchers.IO) {
                DeviceLocationProvider(appContext).currentOrLastKnownLocation()
            }
            val location = locationResult.location
            _uiState.update { current ->
                val currentPoint = location?.let {
                    GeoPoint(latitude = it.latitude, longitude = it.longitude)
                }
                current.copy(
                    routeAddressMap = current.routeAddressMap.copy(
                        currentLatitude = location?.latitude ?: current.routeAddressMap.currentLatitude,
                        currentLongitude = location?.longitude ?: current.routeAddressMap.currentLongitude,
                        currentAccuracyMeters = location?.accuracyMeters ?: current.routeAddressMap.currentAccuracyMeters,
                        routeOriginMovedKm = current.routeAddressMap.routeOriginPoint()?.let { origin ->
                            currentPoint?.let { haversineDistanceKm(origin, it) }
                        } ?: current.routeAddressMap.routeOriginMovedKm,
                        points = current.routeAddressMap.points.map { point ->
                            point.copy(
                                distanceKmFromCurrentLocation = currentPoint?.let {
                                    haversineDistanceKm(
                                        from = it,
                                        to = GeoPoint(point.latitude, point.longitude),
                                    )
                                } ?: point.distanceKmFromCurrentLocation,
                            )
                        },
                        message = when {
                            location != null &&
                                current.routeAddressMap.nearestStops.isNotEmpty() &&
                                current.routeAddressMap.routeOriginMovedKmFrom(currentPoint) >= RouteEtaStaleMoveThresholdKm ->
                                "현재 위치가 계산 시점보다 이동했습니다. 방문순서/예상시간을 다시 눌러 최신 기준으로 확인하세요."
                            location != null -> current.routeAddressMap.message
                            current.routeAddressMap.currentLatitude != null -> current.routeAddressMap.message
                            else -> locationResult.failureReason ?: current.routeAddressMap.message
                        },
                    ),
                )
            }
        }
    }

    fun calculateNaviFreeVisitOrder(
        context: Context,
        addresses: List<String> = _uiState.value.manualRouteAddresses,
    ) {
        calculateNaviFreeRoutes(
            context = context,
            addresses = addresses,
            successMessage = "방문순서와 예상시간을 네이버 길찾기로 계산했습니다.",
        )
    }

    fun refreshNaviFreeRouteEta(
        context: Context,
        addresses: List<String> = _uiState.value.manualRouteAddresses,
    ) {
        if (!isNaviFreeBuild()) return
        val existingOrder = _uiState.value.routeAddressMap.nearestStops
            .sortedBy { it.order }
            .map { it.sourceIndex }
        if (existingOrder.isEmpty()) {
            _uiState.update {
                it.copy(
                    routeAddressMap = it.routeAddressMap.copy(
                        message = "방문순서 계산을 먼저 눌러 주세요.",
                    ),
                )
            }
            return
        }
        val appContext = context.applicationContext
        ensureNaverRouteDiskCache(appContext)
        val requestId = ++routeCalculationRequestId
        val requestedAddresses = addresses.toManualRouteAddressSlots()
        routeCalculationJob?.cancel()
        routeCalculationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCalculatingRoute = true,
                    routeAddressMap = it.routeAddressMap.copy(message = "예상시간을 다시 계산 중입니다."),
                )
            }
            val mapState = withContext(Dispatchers.IO) {
                buildRouteAddressMapStateForExistingOrder(
                    context = appContext,
                    addresses = requestedAddresses,
                    sourceOrder = existingOrder,
                )
            }
            if (requestId != routeCalculationRequestId) return@launch
            _uiState.update {
                it.copy(
                    isCalculatingRoute = false,
                    routeAddressMap = mapState.copy(
                        message = when {
                            mapState.nearestStops.isNotEmpty() -> "예상시간을 네이버 길찾기로 다시 계산했습니다."
                            else -> mapState.message ?: "예상시간 계산 결과가 없습니다."
                        },
                    ),
                    isResolvingAdminAreaDistance = false,
                    adminAreaDistanceResult = null,
                )
            }
        }
    }

    private fun calculateNaviFreeRoutes(
        context: Context,
        addresses: List<String>,
        successMessage: String,
    ) {
        if (!isNaviFreeBuild()) return
        val appContext = context.applicationContext
        ensureNaverRouteDiskCache(appContext)
        val requestId = ++routeCalculationRequestId
        val requestedAddresses = addresses.toManualRouteAddressSlots()
        routeCalculationJob?.cancel()
        routeCalculationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCalculatingRoute = true,
                    message = null,
                    routeAddressMap = it.routeAddressMap.copy(message = "네이버 길찾기 계산 중입니다."),
                )
            }
            val mapState = withContext(Dispatchers.IO) {
                buildRouteAddressMapState(
                    context = appContext,
                    addresses = requestedAddresses,
                    includeNearestRoutes = true,
                )
            }
            if (requestId != routeCalculationRequestId) return@launch
            _uiState.update {
                it.copy(
                    isCalculatingRoute = false,
                    routeAddressMap = mapState.copy(
                        message = when {
                            mapState.nearestStops.isNotEmpty() -> successMessage
                            else -> mapState.message ?: "네이버 길찾기 계산 결과가 없습니다."
                        },
                    ),
                    isResolvingAdminAreaDistance = false,
                    adminAreaDistanceResult = null,
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

        val locationResult = DeviceLocationProvider(context).currentOrLastKnownLocation()
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

        val naverProxyBaseUrl = BuildConfig.NAVER_PROXY_BASE_URL.trim()
        var naverFailureReason: String? = null
        if (naverProxyBaseUrl.isNotBlank()) {
            val naverResult = buildNaverRoutePlan(
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
                naverProxyBaseUrl.isBlank() -> "네이버 프록시 서버 주소가 필요합니다."
                naverFailureReason.isNullOrBlank() -> "네이버 주행거리 계산이 실패했습니다. 네이버 프록시 서버 상태를 확인해 주세요."
                else -> "네이버 주행거리 계산 실패: $naverFailureReason"
            },
        )
    }

    private fun buildNaverRoutePlan(
        currentLocation: DeviceLocation,
        inputs: List<ManualRouteAddressInput>,
    ): NaverRoutePlanResult {
        if (!BuildConfig.IS_NAVI_APP) {
            return NaverRoutePlanResult(failureReason = "방문순서 계산은 CatchPro Navi에서만 사용할 수 있습니다.")
        }
        val currentWaypoint = RouteWaypoint.LatLng(
            latitude = currentLocation.latitude,
            longitude = currentLocation.longitude,
        )
        val failureReasons = mutableListOf<String>()

        val resolvedInputs = inputs.mapNotNull { input ->
            val point = routeDistanceService.geocodeAddress(
                address = input.address,
                source = "route_optimize_geocode",
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
        var previousLabel = "출발점"
        var totalDistanceKm = 0.0
        var totalDurationSeconds: Int? = 0

        while (remaining.isNotEmpty()) {
            val selected = remaining
                .mapNotNull { input ->
                    val destinationWaypoint = input.point.toRouteWaypoint()
                    val outcome = if (previousWaypoint is RouteWaypoint.LatLng) {
                        cachedDrivingDistanceKm(
                            cache = naverRouteOutcomeCache,
                            routeDistanceService = routeDistanceService,
                            origin = previousWaypoint,
                            destination = destinationWaypoint,
                            source = "route_optimize",
                        )
                    } else {
                        routeDistanceService.drivingDistanceKm(
                            origin = previousWaypoint,
                            destination = destinationWaypoint,
                            source = "route_optimize",
                        )
                    }
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

    private suspend fun buildRouteAddressMapState(
        context: Context,
        addresses: List<String>,
        includeNearestRoutes: Boolean,
    ): RouteAddressMapUiState {
        val inputs = addresses
            .mapIndexedNotNull { index, value ->
                value.cleanAddressInput()
                    ?.let { ManualRouteAddressInput(sourceIndex = index, address = it) }
            }
            .distinctBy { it.address.normalizeAddressKey() }

        val locationResult = DeviceLocationProvider(context).currentOrLastKnownLocation()
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
        val nearestStops = if (includeNearestRoutes && currentPoint != null) {
            buildNearestNaverRouteStops(
                currentPoint = currentPoint,
                points = points,
                routeDistanceService = routeDistanceService,
                routeOutcomeCache = naverRouteOutcomeCache,
                routeDiskCache = naverRouteDiskCache,
                proxyConfigured = BuildConfig.NAVER_PROXY_BASE_URL.trim().isNotBlank(),
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
            !includeNearestRoutes && isNaviFreeBuild() -> "지도 표시 완료. 방문순서/예상시간은 버튼을 눌러 계산하세요."
            else -> null
        }

        return RouteAddressMapUiState(
            currentLatitude = currentLocation?.latitude,
            currentLongitude = currentLocation?.longitude,
            currentAccuracyMeters = currentLocation?.accuracyMeters,
            routeCalculatedAtMillis = nearestStops
                .takeIf { it.isNotEmpty() }
                ?.let { System.currentTimeMillis() },
            routeOriginLatitude = nearestStops
                .takeIf { it.isNotEmpty() }
                ?.let { currentLocation?.latitude },
            routeOriginLongitude = nearestStops
                .takeIf { it.isNotEmpty() }
                ?.let { currentLocation?.longitude },
            routeOriginMovedKm = nearestStops
                .takeIf { it.isNotEmpty() }
                ?.let { 0.0 },
            points = points,
            nearestStops = nearestStops,
            nearestTotalDistanceKm = nearestTotalDistanceKm,
            nearestTotalDurationText = nearestTotalDurationText,
            message = message,
        )
    }

    private suspend fun buildRouteAddressMapStateForExistingOrder(
        context: Context,
        addresses: List<String>,
        sourceOrder: List<Int>,
    ): RouteAddressMapUiState {
        if (!BuildConfig.IS_NAVI_APP) {
            return buildRouteAddressMapState(
                context = context,
                addresses = addresses,
                includeNearestRoutes = false,
            )
        }
        val baseState = buildRouteAddressMapState(
            context = context,
            addresses = addresses,
            includeNearestRoutes = false,
        )
        val currentPoint = if (baseState.currentLatitude != null && baseState.currentLongitude != null) {
            GeoPoint(
                latitude = baseState.currentLatitude,
                longitude = baseState.currentLongitude,
            )
        } else {
            null
        } ?: return baseState

        val pointBySourceIndex = baseState.points.associateBy { it.sourceIndex }
        val orderedPoints = sourceOrder
            .distinct()
            .mapNotNull { pointBySourceIndex[it] }
        if (orderedPoints.isEmpty()) return baseState.copy(message = "계산할 방문순서가 없습니다.")

        val proxyConfigured = BuildConfig.NAVER_PROXY_BASE_URL.trim().isNotBlank()
        val stops = mutableListOf<RouteAddressNearestStopUiModel>()
        var previousPoint = currentPoint
        var previousLabel = "출발점"
        orderedPoints.forEach { point ->
            val pointGeo = GeoPoint(point.latitude, point.longitude)
            val outcome = if (proxyConfigured) {
                cachedDrivingDistanceKm(
                    cache = naverRouteOutcomeCache,
                    routeDistanceService = routeDistanceService,
                    origin = previousPoint.toRouteWaypoint(),
                    destination = pointGeo.toRouteWaypoint(),
                    source = "navi_eta_refresh",
                    diskCache = naverRouteDiskCache,
                )
            } else {
                null
            }
            val candidate = NearestRouteCandidate(
                point = point,
                pointGeo = pointGeo,
                routeOutcome = outcome,
                durationSeconds = outcome?.duration?.toDurationSeconds(),
            )
            stops += candidate.toNearestStop(
                order = stops.size + 1,
                fromLabel = previousLabel,
                proxyConfigured = proxyConfigured,
            )
            previousPoint = pointGeo
            previousLabel = "주소 ${point.sourceIndex + 1}"
        }
        return baseState.copy(
            nearestStops = stops,
            routeCalculatedAtMillis = stops
                .takeIf { it.isNotEmpty() }
                ?.let { System.currentTimeMillis() },
            routeOriginLatitude = stops
                .takeIf { it.isNotEmpty() }
                ?.let { currentPoint.latitude },
            routeOriginLongitude = stops
                .takeIf { it.isNotEmpty() }
                ?.let { currentPoint.longitude },
            routeOriginMovedKm = stops
                .takeIf { it.isNotEmpty() }
                ?.let { 0.0 },
            nearestTotalDistanceKm = stops
                .takeIf { values -> values.isNotEmpty() && values.all { it.legDistanceKm != null } }
                ?.sumOf { it.legDistanceKm ?: 0.0 },
            nearestTotalDurationText = stops
                .takeIf { values -> values.isNotEmpty() && values.all { it.legDurationSeconds != null } }
                ?.sumOf { it.legDurationSeconds ?: 0 }
                ?.formatDurationText(),
        )
    }

    private fun geocodeAddressWithNaver(address: String): GeoPoint? {
        val naverProxyBaseUrl = BuildConfig.NAVER_PROXY_BASE_URL.trim()
        if (naverProxyBaseUrl.isBlank()) return null
        val cacheKey = address.normalizeAddressKey()
        naverGeocodeCache[cacheKey]?.let { return it }
        val point = routeDistanceService.geocodeAddress(
            address = address,
            source = "navi_marker",
        )?.let {
            GeoPoint(
                latitude = it.latitude,
                longitude = it.longitude,
            )
        }
        if (point != null) {
            if (naverGeocodeCache.size > MaxNaverGeocodeCacheEntries) {
                naverGeocodeCache.clear()
            }
            naverGeocodeCache[cacheKey] = point
        }
        return point
    }

    private fun ensureNaverRouteDiskCache(context: Context) {
        if (!isNaviFreeBuild() || naverRouteDiskCache != null) return
        naverRouteDiskCache = NaverRouteDiskCache(
            file = File(context.cacheDir, "naver_route_outcome_cache_v1.json"),
            ttlMillis = NaverRouteDiskCacheTtlMillis,
        )
    }

    private fun resolveAdminAreaQueryPoint(query: String): Pair<String, GeoPoint>? {
        val normalized = query.trim().replace(Regex("""\s+"""), " ")
        val candidates = listOf(
            normalized,
            "$normalized 행정복지센터",
            "$normalized 주민센터",
            "$normalized 동주민센터",
        )
            .filter { it.isNotBlank() }
            .distinct()
        return candidates.firstNotNullOfOrNull { candidate ->
            geocodeAddressWithNaver(candidate)?.let { point -> candidate to point }
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
private const val ManualRouteAddressSaveDebounceMillis = 120L
private const val MaxNaverGeocodeCacheEntries = 128
private const val MaxNaverRouteOutcomeCacheEntries = 256
private const val MaxNaverRouteDiskCacheEntries = 512
private const val NaverRouteDiskCacheTtlMillis = 30 * 60 * 1000L
private const val NaviDirectionsLogTag = "CatchProNaviDirections"
private const val RouteEtaStaleMoveThresholdKm = 0.3

private fun isNaviFreeBuild(): Boolean = BuildConfig.IS_NAVI_APP && BuildConfig.IS_FREE_EDITION

private fun shouldAutoCalculateMapRoutes(): Boolean = BuildConfig.IS_NAVI_APP && !isNaviFreeBuild()

private fun RouteAddressMapUiState.routeOriginPoint(): GeoPoint? {
    val latitude = routeOriginLatitude ?: return null
    val longitude = routeOriginLongitude ?: return null
    return GeoPoint(latitude = latitude, longitude = longitude)
}

private fun RouteAddressMapUiState.routeOriginMovedKmFrom(currentPoint: GeoPoint?): Double {
    val origin = routeOriginPoint() ?: return routeOriginMovedKm ?: 0.0
    val current = currentPoint ?: return routeOriginMovedKm ?: 0.0
    return haversineDistanceKm(origin, current)
}

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

private fun String.normalizeManualAddressFieldInput(): String =
    replace(Regex("""[\r\n]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

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

private class NaverRouteDiskCache(
    private val file: File,
    private val ttlMillis: Long,
) {
    private val entries = LinkedHashMap<String, DiskCachedRouteOutcome>()
    private var loaded = false

    fun get(key: String): RouteDistanceOutcome? = synchronized(this) {
        loadIfNeeded()
        val entry = entries[key] ?: return@synchronized null
        if (System.currentTimeMillis() - entry.createdAtMillis > ttlMillis) {
            entries.remove(key)
            save()
            return@synchronized null
        }
        entry.toOutcome()
    }

    fun put(
        key: String,
        outcome: RouteDistanceOutcome,
    ) = synchronized(this) {
        val distanceKm = outcome.distanceKm ?: return@synchronized
        loadIfNeeded()
        entries[key] = DiskCachedRouteOutcome(
            distanceKm = distanceKm,
            duration = outcome.duration,
            failureReason = outcome.failureReason,
            path = outcome.path.map {
                RouteAddressRoutePointUiModel(
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            },
            createdAtMillis = System.currentTimeMillis(),
        )
        while (entries.size > MaxNaverRouteDiskCacheEntries) {
            val firstKey = entries.keys.firstOrNull() ?: break
            entries.remove(firstKey)
        }
        save()
    }

    private fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            root.keys().forEach { key ->
                val value = root.optJSONObject(key) ?: return@forEach
                val path = value.optJSONArray("path")
                    ?.let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            val item = array.optJSONObject(index) ?: return@mapNotNull null
                            val latitude = item.optDouble("latitude", Double.NaN)
                            val longitude = item.optDouble("longitude", Double.NaN)
                            if (latitude.isNaN() || longitude.isNaN()) {
                                null
                            } else {
                                RouteAddressRoutePointUiModel(latitude = latitude, longitude = longitude)
                            }
                        }
                    }
                    .orEmpty()
                entries[key] = DiskCachedRouteOutcome(
                    distanceKm = value.optDouble("distanceKm"),
                    duration = value.optString("duration").takeIf { it.isNotBlank() },
                    failureReason = value.optString("failureReason").takeIf { it.isNotBlank() },
                    path = path,
                    createdAtMillis = value.optLong("createdAtMillis"),
                )
            }
        }.onFailure {
            entries.clear()
        }
    }

    private fun save() {
        runCatching {
            val root = JSONObject()
            entries.forEach { (key, value) ->
                root.put(key, value.toJson())
            }
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        }
    }
}

private data class DiskCachedRouteOutcome(
    val distanceKm: Double,
    val duration: String?,
    val failureReason: String?,
    val path: List<RouteAddressRoutePointUiModel>,
    val createdAtMillis: Long,
) {
    fun toOutcome(): RouteDistanceOutcome {
        return RouteDistanceOutcome(
            distanceKm = distanceKm,
            duration = duration,
            failureReason = failureReason,
            path = path.map {
                RouteWaypoint.LatLng(
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            },
        )
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put("distanceKm", distanceKm)
            .put("duration", duration.orEmpty())
            .put("failureReason", failureReason.orEmpty())
            .put("createdAtMillis", createdAtMillis)
            .put(
                "path",
                path.fold(org.json.JSONArray()) { array, point ->
                    array.put(
                        JSONObject()
                            .put("latitude", point.latitude)
                            .put("longitude", point.longitude),
                    )
                    array
                },
            )
    }
}

private fun buildNearestNaverRouteStops(
    currentPoint: GeoPoint,
    points: List<RouteAddressMapPointUiModel>,
    routeDistanceService: NaverRouteDistanceService,
    routeOutcomeCache: ConcurrentHashMap<String, RouteDistanceOutcome>,
    routeDiskCache: NaverRouteDiskCache?,
    proxyConfigured: Boolean,
): List<RouteAddressNearestStopUiModel> {
    if (!BuildConfig.IS_NAVI_APP) return emptyList()
    val remainingPoints = points
        .filter { it.sourceIndex < NearestRouteAddressLimit }
        .toMutableList()
    val stops = mutableListOf<RouteAddressNearestStopUiModel>()
    var previousPoint = currentPoint
    var previousLabel = "출발점"

    while (remainingPoints.isNotEmpty()) {
        val candidates = remainingPoints.map { point ->
            val pointGeo = GeoPoint(point.latitude, point.longitude)
            val outcome = if (proxyConfigured) {
                cachedDrivingDistanceKm(
                    cache = routeOutcomeCache,
                    routeDistanceService = routeDistanceService,
                    origin = previousPoint.toRouteWaypoint(),
                    destination = pointGeo.toRouteWaypoint(),
                    source = "navi_visit_order",
                    diskCache = routeDiskCache,
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
            proxyConfigured = proxyConfigured,
        )
        remainingPoints.remove(selected.point)
        previousPoint = selected.pointGeo
        previousLabel = "주소 ${selected.point.sourceIndex + 1}"
    }

    return stops
}

private fun cachedDrivingDistanceKm(
    cache: ConcurrentHashMap<String, RouteDistanceOutcome>,
    routeDistanceService: NaverRouteDistanceService,
    origin: RouteWaypoint.LatLng,
    destination: RouteWaypoint.LatLng,
    source: String,
    diskCache: NaverRouteDiskCache? = null,
): RouteDistanceOutcome {
    val cacheKey = routeDistanceCacheKey(origin, destination)
    cache[cacheKey]?.let {
        Log.i(
            NaviDirectionsLogTag,
            "NAVER_DIRECTIONS_CACHE_HIT source=$source from=${origin.routeCacheCoordinate()} to=${destination.routeCacheCoordinate()} result=${it.distanceKm ?: "unknown"}km/${it.duration ?: "unknown"}",
        )
        return it
    }
    diskCache?.get(cacheKey)?.let {
        cache[cacheKey] = it
        Log.i(
            NaviDirectionsLogTag,
            "NAVER_DIRECTIONS_DISK_CACHE_HIT source=$source from=${origin.routeCacheCoordinate()} to=${destination.routeCacheCoordinate()} result=${it.distanceKm ?: "unknown"}km/${it.duration ?: "unknown"}",
        )
        return it
    }
    Log.i(
        NaviDirectionsLogTag,
        "NAVER_DIRECTIONS_CALL source=$source from=${origin.routeCacheCoordinate()} to=${destination.routeCacheCoordinate()} cache=miss",
    )
    val outcome = routeDistanceService.drivingDistanceKm(
        origin = origin,
        destination = destination,
        source = source,
    )
    if (outcome.distanceKm == null) {
        Log.w(
            NaviDirectionsLogTag,
            "NAVER_DIRECTIONS_FAILED source=$source from=${origin.routeCacheCoordinate()} to=${destination.routeCacheCoordinate()} reason=${outcome.failureReason.orEmpty().ifBlank { "unknown" }}",
        )
    }
    if (outcome.distanceKm != null) {
        if (cache.size > MaxNaverRouteOutcomeCacheEntries) {
            cache.clear()
        }
        cache[cacheKey] = outcome
        diskCache?.put(cacheKey, outcome)
        Log.i(
            NaviDirectionsLogTag,
            "NAVER_DIRECTIONS_SUCCESS source=$source from=${origin.routeCacheCoordinate()} to=${destination.routeCacheCoordinate()} result=${outcome.distanceKm}km/${outcome.duration ?: "unknown"}",
        )
    }
    return outcome
}

private fun routeDistanceCacheKey(
    origin: RouteWaypoint.LatLng,
    destination: RouteWaypoint.LatLng,
): String =
    buildString {
        append(origin.routeCacheCoordinate())
        append('|')
        append(destination.routeCacheCoordinate())
    }

private fun RouteWaypoint.LatLng.routeCacheCoordinate(): String =
    String.format(Locale.US, "%.5f,%.5f", latitude, longitude)

private fun NearestRouteCandidate.toNearestStop(
    order: Int,
    fromLabel: String,
    proxyConfigured: Boolean,
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
            !proxyConfigured -> "네이버 프록시 서버 주소가 필요합니다."
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
