package com.catchpro.app.ui.screen.tmap

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.data.sync.RouteAddressCloudSyncManager
import com.catchpro.app.data.sync.RouteAddressCloudSyncStatus
import com.catchpro.app.observation.DeviceLocationProvider
import com.catchpro.app.observation.NaverRouteDistanceService
import com.catchpro.app.ui.components.ScreenScaffold
import java.util.Locale

@Composable
fun TmapQueueScreen(
    settingsRepository: SettingsRepository,
    routeDistanceService: NaverRouteDistanceService,
    routeAddressCloudSyncManager: RouteAddressCloudSyncManager,
) {
    val factory = remember(settingsRepository, routeDistanceService, routeAddressCloudSyncManager) {
        TmapQueueViewModel.factory(
            settingsRepository = settingsRepository,
            routeDistanceService = routeDistanceService,
            routeAddressCloudSyncManager = routeAddressCloudSyncManager,
        )
    }
    val viewModel: TmapQueueViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationPermissionGranted by remember {
        mutableStateOf(DeviceLocationProvider.hasLocationPermission(context))
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        locationPermissionGranted = permissions.values.any { it } ||
            DeviceLocationProvider.hasLocationPermission(context)
        viewModel.refreshAddressMap(context, uiState.manualRouteAddresses)
    }

    LaunchedEffect(uiState.manualRouteAddresses, locationPermissionGranted) {
        viewModel.refreshAddressMap(context, uiState.manualRouteAddresses)
    }

    ScreenScaffold(
        title = "",
        subtitle = "",
        showHeader = false,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RouteAddressNaverMapCard(
                mapState = uiState.routeAddressMap,
                isRefreshing = uiState.isRefreshingMap,
                naverMapConfigured = BuildConfig.NAVER_MAP_NCP_KEY_ID.isNotBlank(),
                locationPermissionGranted = locationPermissionGranted,
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onRefreshMap = {
                    viewModel.refreshAddressMap(context, uiState.manualRouteAddresses)
                },
                onNavigate = { point ->
                    NaverMapNavigator.launchNavigation(
                        context = context,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        name = point.address,
                    )
                },
            )

            ManualRoutePlannerCard(
                addresses = uiState.manualRouteAddresses,
                mapPoints = uiState.routeAddressMap.points,
                isOptimizing = uiState.isOptimizing,
                locationPermissionGranted = locationPermissionGranted,
                onAddressChange = { index, value ->
                    viewModel.updateManualAddress(index, value)
                },
                onNaverNavigate = { address, point ->
                    if (point != null) {
                        NaverMapNavigator.launchNavigation(
                            context = context,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            name = point.address,
                        )
                    } else {
                        NaverMapNavigator.launchSearch(context, address)
                    }
                },
                onTmapNavigate = { address ->
                    TmapNavigator.launchForAddress(context, address)
                },
                onOptimizeRoute = {
                    if (DeviceLocationProvider.hasLocationPermission(context)) {
                        viewModel.optimizeRoute(context, uiState.manualRouteAddresses)
                    } else {
                        viewModel.showMessage("현재 위치 권한이 필요합니다. Android 설정에서 CatchPro 위치 권한을 허용해 주세요.")
                    }
                },
                onClear = {
                    viewModel.clearManualAddresses()
                },
            )

            RouteAddressCloudSyncCard(
                enabled = uiState.routeAddressCloudSyncEnabled,
                roomCode = uiState.routeAddressCloudSyncRoomCode,
                status = uiState.routeAddressCloudSyncStatus,
                onEnabledChange = viewModel::setRouteAddressCloudSyncEnabled,
                onRoomCodeChange = viewModel::setRouteAddressCloudSyncRoomCode,
            )

            uiState.message?.let { message ->
                RouteMessageCard(message = message)
            }

            uiState.routePlan?.let { plan ->
                OptimizedRouteCard(
                    plan = plan,
                    mapPoints = uiState.routeAddressMap.points,
                    onNaverNavigate = { address, point ->
                        if (point != null) {
                            NaverMapNavigator.launchNavigation(
                                context = context,
                                latitude = point.latitude,
                                longitude = point.longitude,
                                name = point.address,
                            )
                        } else {
                            NaverMapNavigator.launchSearch(context, address)
                        }
                    },
                    onTmapNavigate = { address ->
                        TmapNavigator.launchForAddress(context, address)
                    },
                )
            }
        }
    }
}

@Composable
private fun RouteAddressNaverMapCard(
    mapState: RouteAddressMapUiState,
    isRefreshing: Boolean,
    naverMapConfigured: Boolean,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRefreshMap: () -> Unit,
    onNavigate: (RouteAddressMapPointUiModel) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (naverMapConfigured) {
                NaverRouteAddressMap(
                    mapState = mapState,
                    modifier = Modifier.height(480.dp),
                )
            } else {
                Card {
                    Text(
                        text = "네이버 지도 키가 필요합니다. local.properties에 naver.map.ncp.key.id 값을 넣으면 지도가 표시됩니다.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            mapState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (locationPermissionGranted) {
                    "현재 위치: 사용 가능"
                } else {
                    "현재 위치: 권한 필요"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NearestNaverRouteSummary(
                mapState = mapState,
                onNavigate = onNavigate,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onRefreshMap,
                    modifier = Modifier.weight(1f),
                    enabled = !isRefreshing,
                ) {
                    Text(if (isRefreshing) "갱신 중..." else "지도 갱신")
                }
                OutlinedButton(
                    onClick = onRequestLocationPermission,
                    modifier = Modifier.weight(1f),
                    enabled = !locationPermissionGranted,
                ) {
                    Text("위치 권한")
                }
            }
        }
    }
}

@Composable
private fun NearestNaverRouteSummary(
    mapState: RouteAddressMapUiState,
    onNavigate: (RouteAddressMapPointUiModel) -> Unit,
) {
    if (mapState.nearestStops.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildString {
                    append("거리순: ")
                    append("현위치 → ")
                    append(
                        mapState.nearestStops.joinToString(" → ") {
                            (it.sourceIndex + 1).toString()
                        },
                    )
                    mapState.nearestTotalDistanceKm?.let {
                        append(" · 누적 주행 ")
                        append(it.formatDistanceKm())
                        append("km")
                    }
                    mapState.nearestTotalDurationText?.let {
                        append(" · 예상 ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            mapState.nearestStops.forEach { to ->
                val toPoint = mapState.points.firstOrNull { it.sourceIndex == to.sourceIndex }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = to.order.toString(),
                        modifier = Modifier.width(48.dp),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val fromLabel = if (to.order == 1) "현위치" else to.fromLabel
                        Text(
                            text = buildString {
                                append("주소 ")
                                append(to.sourceIndex + 1)
                                append(" / ")
                                if (to.legDistanceKm != null) {
                                    append("거리 ")
                                    append(to.legDistanceKm.formatDistanceKm())
                                    append("km")
                                } else {
                                    append("거리 확인 실패")
                                }
                                if (to.legDurationText != null) {
                                    append(" / 예상 ")
                                    append(to.legDurationText)
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$fromLabel → 주소 ${to.sourceIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        to.legFailureReason?.let { reason ->
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            text = to.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            toPoint?.let(onNavigate)
                        },
                        enabled = toPoint != null,
                    ) {
                        Text("길안내")
                    }
                }
            }
        }
    } else {
        mapState.points.forEach { point ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = buildString {
                            append(point.label)
                            point.distanceKmFromCurrentLocation?.let {
                                append(" · 직선 ")
                                append(it.formatDistanceKm())
                                append("km")
                            }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = point.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { onNavigate(point) }) {
                    Text("길안내")
                }
            }
        }
    }
}

@Composable
private fun RouteAddressCloudSyncCard(
    enabled: Boolean,
    roomCode: String,
    status: RouteAddressCloudSyncStatus,
    onEnabledChange: (Boolean) -> Unit,
    onRoomCodeChange: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "AWS 실시간 주소 동기화",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (status.connected) {
                            "연결됨 · 방 ${status.roomCode}"
                        } else {
                            status.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            OutlinedTextField(
                value = roomCode,
                onValueChange = onRoomCodeChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !enabled,
                label = { Text("동기화 방 코드") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
            )
            Text(
                text = "두 휴대폰에 같은 6자리 코드를 입력하고 스위치를 켜면, 이 화면의 주소 1~6이 자동으로 동기화됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualRoutePlannerCard(
    addresses: List<String>,
    mapPoints: List<RouteAddressMapPointUiModel>,
    isOptimizing: Boolean,
    locationPermissionGranted: Boolean,
    onAddressChange: (Int, String) -> Unit,
    onNaverNavigate: (String, RouteAddressMapPointUiModel?) -> Unit,
    onTmapNavigate: (String) -> Unit,
    onOptimizeRoute: () -> Unit,
    onClear: () -> Unit,
) {
    val filledAddressCount = addresses.count { it.isNotBlank() }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "주소/네비게이션",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "인성 상세/지도앱에서 확인된 주소를 순서대로 저장합니다. 각 주소는 네이버 내비 또는 TMAP 중 선택해서 실행할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            addresses.forEachIndexed { index, address ->
                ManualAddressBlock(
                    index = index,
                    label = manualRouteAddressLabel(index),
                    address = address,
                    onAddressChange = { onAddressChange(index, it) },
                    onNaverNavigate = {
                        onNaverNavigate(
                            address,
                            mapPoints.firstOrNull { it.sourceIndex == index },
                        )
                    },
                    onTmapNavigate = { onTmapNavigate(address) },
                )
            }
            Text(
                text = if (locationPermissionGranted) {
                    "현재 위치: 사용 가능"
                } else {
                    "현재 위치: 권한 필요"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOptimizeRoute,
                enabled = filledAddressCount >= 1 && !isOptimizing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isOptimizing) "계산 중..." else "최적 순서 계산")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = addresses.any { it.isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("주소 초기화")
            }
        }
    }
}

@Composable
private fun ManualAddressBlock(
    index: Int,
    label: String,
    address: String,
    onAddressChange: (String) -> Unit,
    onNaverNavigate: () -> Unit,
    onTmapNavigate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            minLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onNaverNavigate,
                enabled = address.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("네이버 내비")
            }
            Button(
                onClick = onTmapNavigate,
                enabled = address.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("TMAP")
            }
        }
    }
}

@Composable
private fun RouteMessageCard(message: String) {
    Card {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptimizedRouteCard(
    plan: TmapRoutePlanUiModel,
    mapPoints: List<RouteAddressMapPointUiModel>,
    onNaverNavigate: (String, RouteAddressMapPointUiModel?) -> Unit,
    onTmapNavigate: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "추천 방문 순서",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append(plan.calculationMode)
                    append(" · 총 ")
                    append(plan.totalDistanceText)
                    plan.totalDurationText?.let {
                        append(" · 약 ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            plan.stops.forEachIndexed { orderIndex, stop ->
                OptimizedRouteStop(
                    orderIndex = orderIndex,
                    stop = stop,
                    mapPoint = mapPoints.firstOrNull { it.sourceIndex == stop.sourceIndex },
                    onNaverNavigate = onNaverNavigate,
                    onTmapNavigate = onTmapNavigate,
                )
            }
        }
    }
}

@Composable
private fun OptimizedRouteStop(
    orderIndex: Int,
    stop: TmapRouteStopUiModel,
    mapPoint: RouteAddressMapPointUiModel?,
    onNaverNavigate: (String, RouteAddressMapPointUiModel?) -> Unit,
    onTmapNavigate: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${orderIndex + 1}. ${manualRouteAddressLabel(stop.sourceIndex)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stop.address,
            style = MaterialTheme.typography.bodyLarge,
        )
        stop.legDistanceKm?.let { distanceKm ->
            Text(
                text = buildString {
                    append("이전 위치에서 ")
                    append(distanceKm.formatDistanceKm())
                    append("km")
                    stop.legDurationText?.let {
                        append(" · 약 ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { onNaverNavigate(stop.address, mapPoint) },
                modifier = Modifier.weight(1f),
            ) {
                Text("네이버 내비")
            }
            Button(
                onClick = { onTmapNavigate(stop.address) },
                modifier = Modifier.weight(1f),
            ) {
                Text("TMAP")
            }
        }
    }
}

private fun Double.formatDistanceKm(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        "%.1f".format(Locale.getDefault(), this)
    }
}

private fun manualRouteAddressLabel(index: Int): String {
    return "주소 ${index + 1}"
}
