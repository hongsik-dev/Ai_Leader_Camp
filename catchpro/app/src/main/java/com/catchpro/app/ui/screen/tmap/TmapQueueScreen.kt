package com.catchpro.app.ui.screen.tmap

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay

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
    var naviLocationPermissionRequested by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        locationPermissionGranted = permissions.values.any { it } ||
            DeviceLocationProvider.hasLocationPermission(context)
        if (BuildConfig.IS_NAVI_APP) {
            viewModel.refreshAddressMap(context, uiState.manualRouteAddresses)
        }
    }

    LaunchedEffect(locationPermissionGranted, BuildConfig.IS_FREE_EDITION) {
        if (BuildConfig.IS_NAVI_APP && !BuildConfig.IS_FREE_EDITION) {
            viewModel.refreshAddressMap(context, uiState.manualRouteAddresses)
        }
    }

    if (BuildConfig.IS_NAVI_APP) {
        LaunchedEffect(locationPermissionGranted) {
            if (!locationPermissionGranted && !naviLocationPermissionRequested) {
                naviLocationPermissionRequested = true
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
        LaunchedEffect(locationPermissionGranted) {
            if (!locationPermissionGranted) return@LaunchedEffect
            while (true) {
                viewModel.refreshCurrentLocation(context)
                delay(NaviCurrentLocationRefreshMillis)
            }
        }
        NaviRouteMapContent(
            uiState = uiState,
            naverMapConfigured = BuildConfig.NAVER_MAP_NCP_KEY_ID.isNotBlank(),
            locationPermissionGranted = locationPermissionGranted,
            onRequestLocationPermission = {
                naviLocationPermissionRequested = true
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
            onCalculateVisitOrder = {
                viewModel.calculateNaviFreeVisitOrder(context, uiState.manualRouteAddresses)
            },
            onRefreshRouteEta = {
                viewModel.refreshNaviFreeRouteEta(context, uiState.manualRouteAddresses)
            },
            onAddressesApply = { addresses ->
                viewModel.replaceManualAddresses(addresses)
                viewModel.refreshAddressMap(context, addresses)
            },
            onClearAddresses = viewModel::clearManualAddresses,
            onNaverNavigate = { point ->
                NaverMapNavigator.launchNavigation(
                    context = context,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    name = point.address,
                )
            },
            onTmapNavigate = { address ->
                TmapNavigator.launchForAddress(context, address)
            },
            onCompleteAddress = viewModel::completeManualAddress,
            onCloudEnabledChange = viewModel::setRouteAddressCloudSyncEnabled,
            onAdminAreaQueryChange = viewModel::updateAdminAreaQueryText,
            onResolveAdminAreaDistance = { viewModel.resolveAdminAreaDistance() },
            onResolveAdminAreaVoiceQuery = { query -> viewModel.resolveAdminAreaDistance(query) },
        )
        return
    }

    ScreenScaffold(
        title = "",
        subtitle = "",
        showHeader = false,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ManualRoutePlannerCard(
                addresses = uiState.manualRouteAddresses,
                mapPoints = uiState.routeAddressMap.points,
                isOptimizing = uiState.isOptimizing,
                locationPermissionGranted = locationPermissionGranted,
                routeManagementEnabled = false,
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

            if (BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC) {
                RouteAddressCloudSyncCard(
                    available = uiState.routeAddressCloudSyncFeatureAvailable,
                    enabled = uiState.routeAddressCloudSyncEnabled,
                    roomCode = uiState.routeAddressCloudSyncRoomCode,
                    status = uiState.routeAddressCloudSyncStatus,
                    onEnabledChange = viewModel::setRouteAddressCloudSyncEnabled,
                    onRoomCodeChange = viewModel::setRouteAddressCloudSyncRoomCode,
                )
            }

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
private fun NaviRouteMapContent(
    uiState: TmapQueueUiState,
    naverMapConfigured: Boolean,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRefreshMap: () -> Unit,
    onCalculateVisitOrder: () -> Unit,
    onRefreshRouteEta: () -> Unit,
    onAddressesApply: (List<String>) -> Unit,
    onClearAddresses: () -> Unit,
    onNaverNavigate: (RouteAddressMapPointUiModel) -> Unit,
    onTmapNavigate: (String) -> Unit,
    onCompleteAddress: (Int) -> Unit,
    onCloudEnabledChange: (Boolean) -> Unit,
    onAdminAreaQueryChange: (String) -> Unit,
    onResolveAdminAreaDistance: () -> Unit,
    onResolveAdminAreaVoiceQuery: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedPoint by remember { mutableStateOf<RouteAddressMapPointUiModel?>(null) }
    var showAddressEditor by remember { mutableStateOf(false) }
    var launchVoiceAfterPermission by remember { mutableStateOf(false) }
    val voiceRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.normalizeSpokenAdminAreaQuery()
            .orEmpty()
        if (spoken.isNotBlank()) {
            onResolveAdminAreaVoiceQuery(spoken)
        }
    }
    fun launchVoiceRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "행정동을 말해 주세요")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            voiceRecognizerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "이 휴대폰에서 음성 인식을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && launchVoiceAfterPermission) {
            launchVoiceAfterPermission = false
            launchVoiceRecognizer()
        } else if (!granted) {
            launchVoiceAfterPermission = false
            Toast.makeText(context, "마이크 권한을 허용해야 행정동 음성입력을 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    fun requestAdminAreaVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchVoiceRecognizer()
        } else {
            launchVoiceAfterPermission = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.routeAddressCloudSyncEnabled) {
        if (BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC && !uiState.routeAddressCloudSyncEnabled) {
            onCloudEnabledChange(true)
        }
    }
    LaunchedEffect(uiState.routeAddressMap.points) {
        selectedPoint = selectedPoint?.let { selected ->
            uiState.routeAddressMap.points.firstOrNull {
                it.sourceIndex == selected.sourceIndex && it.address == selected.address
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (naverMapConfigured) {
            NaverRouteAddressMap(
                mapState = uiState.routeAddressMap,
                modifier = Modifier.fillMaxSize(),
                onPointClick = { selectedPoint = it },
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.Center),
            ) {
                Text(
                    text = "네이버 지도 키가 필요합니다. local.properties에 naver.map.ncp.key.id 값을 넣어 주세요.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (BuildConfig.FEATURE_NAVI_OPTIMIZATION) {
                NaviAdminAreaDistancePanel(
                    query = uiState.adminAreaQueryText,
                    isResolving = uiState.isResolvingAdminAreaDistance,
                    result = uiState.adminAreaDistanceResult,
                    onQueryChange = onAdminAreaQueryChange,
                    onResolve = onResolveAdminAreaDistance,
                    onVoiceInput = ::requestAdminAreaVoiceInput,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (BuildConfig.IS_FREE_EDITION) {
                NaviFreeMapControlPanel(
                    isCalculatingRoute = uiState.isCalculatingRoute,
                    onCalculateVisitOrder = onCalculateVisitOrder,
                    onEditAddresses = {
                        selectedPoint = null
                        showAddressEditor = true
                    },
                )
            } else {
                NaviProMapControlPanel(
                    isRefreshing = uiState.isRefreshingMap,
                    onRefreshMap = onRefreshMap,
                    onEditAddresses = {
                        selectedPoint = null
                        showAddressEditor = true
                    },
                )
            }
        }

        if (!locationPermissionGranted) {
            Button(
                onClick = onRequestLocationPermission,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            ) {
                Text("현재 위치 표시 권한 허용")
            }
        }

        if (showAddressEditor) {
            NaviAddressEditorPanel(
                addresses = uiState.manualRouteAddresses,
                onApply = { addresses ->
                    onAddressesApply(addresses)
                    showAddressEditor = false
                },
                onClear = onClearAddresses,
                onClose = { showAddressEditor = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(10.dp),
            )
        } else {
            selectedPoint?.let { point ->
                NaviSelectedAddressPanel(
                    point = point,
                    nearestStop = uiState.routeAddressMap.nearestStops.firstOrNull {
                        it.sourceIndex == point.sourceIndex
                    },
                    onNaverNavigate = { onNaverNavigate(point) },
                    onTmapNavigate = { onTmapNavigate(point.address) },
                    onComplete = { onCompleteAddress(point.sourceIndex) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(10.dp),
                )
            } ?: if (uiState.routeAddressMap.nearestStops.isNotEmpty()) {
                NaviRouteOrderPanel(
                    mapState = uiState.routeAddressMap,
                    onSelectPoint = { selectedPoint = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(10.dp),
                )
            } else Unit
        }
    }
}

@Composable
private fun NaviAdminAreaDistancePanel(
    query: String,
    isResolving: Boolean,
    result: AdminAreaDistanceResultUiModel?,
    onQueryChange: (String) -> Unit,
    onResolve: () -> Unit,
    onVoiceInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("행정동") },
                    placeholder = { Text("인계동") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                    ),
                )
                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "행정동 음성 입력",
                    )
                }
                Button(
                    onClick = onResolve,
                    enabled = !isResolving,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(if (isResolving) "확인중" else "확인")
                }
            }

            result?.let { distanceResult ->
                val nearest = distanceResult.nearest
                Text(
                    text = when {
                        nearest != null -> buildString {
                            append("사용자 위치 기준 방문순서")
                            append(" · ")
                            append(distanceResult.query)
                            append(" 참고거리")
                        }

                        distanceResult.message != null -> distanceResult.message
                        else -> "거리 확인 결과가 없습니다."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (nearest != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (nearest != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        distanceResult.candidates.take(4).forEachIndexed { orderIndex, candidate ->
                            Text(
                                text = buildString {
                                    append(orderIndex + 1)
                                    append("방문 · 주소 ")
                                    append(candidate.sourceIndex + 1)
                                    append(" · 행정동 직선 ")
                                    append(candidate.distanceKm.formatDistanceKm())
                                    append("km")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (distanceResult.resolvedQuery != distanceResult.query) {
                        Text(
                            text = "기준: ${distanceResult.resolvedQuery}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun String.normalizeSpokenAdminAreaQuery(): String {
    return trim()
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""([가-힣])\s+(동|읍|면|구|시|군)$"""), "$1$2")
}

@Composable
private fun NaviFreeMapControlPanel(
    isCalculatingRoute: Boolean,
    onCalculateVisitOrder: () -> Unit,
    onEditAddresses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.width(176.dp)) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onCalculateVisitOrder,
                enabled = !isCalculatingRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = if (isCalculatingRoute) "계산 중" else "방문순서/예상시간",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedButton(
                onClick = onEditAddresses,
                enabled = !isCalculatingRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = "주소 편집",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun NaviProMapControlPanel(
    isRefreshing: Boolean,
    onRefreshMap: () -> Unit,
    onEditAddresses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.width(132.dp)) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = onRefreshMap,
                enabled = !isRefreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = if (isRefreshing) "갱신 중" else "지도 갱신",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Button(
                onClick = onEditAddresses,
                enabled = !isRefreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = "주소 편집",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun NaviAddressEditorPanel(
    addresses: List<String>,
    onApply: (List<String>) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftAddresses by remember(addresses) { mutableStateOf(addresses.take(6).padNaviAddressSlots()) }
    val hasAddress = draftAddresses.any { it.isNotBlank() }
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "주소 직접 입력",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (BuildConfig.IS_FREE_EDITION) {
                            "Navi Free 단독 사용 시 주소 1~6을 직접 입력합니다."
                        } else {
                            "AWS 동기화 주소를 임시로 보정하거나 추가 경유지를 직접 입력합니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) {
                    Text("닫기")
                }
            }
            draftAddresses.take(6).forEachIndexed { index, address ->
                OutlinedTextField(
                    value = address,
                    onValueChange = { value ->
                        draftAddresses = draftAddresses.mapIndexed { currentIndex, currentValue ->
                            if (currentIndex == index) value else currentValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("주소 ${index + 1}") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Text,
                    ),
                    minLines = 1,
                    maxLines = 2,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        draftAddresses = List(6) { "" }
                        onClear()
                    },
                    enabled = hasAddress,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("전체 삭제")
                }
                Button(
                    onClick = { onApply(draftAddresses) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("지도 반영")
                }
            }
        }
    }
}

private fun List<String>.padNaviAddressSlots(): List<String> =
    take(6) + List((6 - size).coerceAtLeast(0)) { "" }

@Composable
private fun NaviSelectedAddressPanel(
    point: RouteAddressMapPointUiModel,
    nearestStop: RouteAddressNearestStopUiModel?,
    onNaverNavigate: () -> Unit,
    onTmapNavigate: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = buildString {
                    append(point.label)
                    nearestStop?.let {
                        append(" · 방문 ")
                        append(it.order)
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    nearestStop?.legDistanceKm?.let {
                        append("이전 위치에서 ")
                        append(it.formatDistanceKm())
                        append("km")
                    }
                    nearestStop?.legDurationText?.let {
                        if (isNotBlank()) append(" · ")
                        append("예상 ")
                        append(it)
                    }
                    if (isBlank()) {
                        point.distanceKmFromCurrentLocation?.let {
                            append("출발점 직선 ")
                            append(it.formatDistanceKm())
                            append("km")
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = point.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onNaverNavigate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("네이버")
                }
                Button(
                    onClick = onTmapNavigate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("TMAP")
                }
                OutlinedButton(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("완료")
                }
            }
        }
    }
}

@Composable
private fun NaviRouteOrderPanel(
    mapState: RouteAddressMapUiState,
    onSelectPoint: (RouteAddressMapPointUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (mapState.nearestStops.isNotEmpty()) {
                    buildString {
                        append("방문순서: 출발점 → ")
                        append(mapState.nearestStops.joinToString(" → ") { (it.sourceIndex + 1).toString() })
                    }
                } else {
                    "방문순서: 출발점 확인 중"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append("출발점: ")
                    if (mapState.currentLatitude != null && mapState.currentLongitude != null) {
                        append("현재 위치 표시 중")
                        mapState.currentAccuracyMeters?.let {
                            append(" · GPS ±")
                            append(it.toInt())
                            append("m")
                        }
                    } else {
                        append(mapState.message ?: "현재 위치를 가져오는 중입니다")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mapState.currentLatitude != null && mapState.currentLongitude != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            mapState.nearestTotalDistanceKm?.let { totalDistance ->
                Text(
                    text = buildString {
                        append("누적 ")
                        append(totalDistance.formatDistanceKm())
                        append("km")
                        mapState.nearestTotalDurationText?.let {
                            append(" · 예상 ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mapState.nearestStops.isNotEmpty()) {
                Text(
                    text = buildString {
                        val movedKm = mapState.routeOriginMovedKm ?: 0.0
                        if (movedKm >= 0.3) {
                            append("계산 후 현재 위치가 ")
                            append(movedKm.formatDistanceKm())
                            append("km 이동했습니다. 최신 예상시간은 다시 계산하세요.")
                        } else {
                            append("예상시간 기준: 계산 시점의 출발점")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((mapState.routeOriginMovedKm ?: 0.0) >= 0.3) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            mapState.nearestStops.forEach { stop ->
                val point = mapState.points.firstOrNull { it.sourceIndex == stop.sourceIndex }
                OutlinedButton(
                    onClick = { point?.let(onSelectPoint) },
                    enabled = point != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildString {
                            append(stop.order)
                            append(". 주소 ")
                            append(stop.sourceIndex + 1)
                            stop.legDistanceKm?.let {
                                append(" · ")
                                append(it.formatDistanceKm())
                                append("km")
                            }
                            stop.legDurationText?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                    )
                }
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
                    append("출발점 → ")
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
                        val fromLabel = if (to.order == 1) "출발점" else to.fromLabel
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
    available: Boolean,
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
                        } else if (!available) {
                            "Pro 라이선스 인증 후 사용할 수 있습니다."
                        } else {
                            status.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = available && enabled,
                    enabled = available,
                    onCheckedChange = onEnabledChange,
                )
            }
            OutlinedTextField(
                value = roomCode,
                onValueChange = onRoomCodeChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = available && !enabled,
                label = { Text("동기화 방 코드") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
            )
            Text(
                text = if (available) {
                    "두 휴대폰에 같은 6자리 코드를 입력하고 스위치를 켜면, 이 화면의 주소 1~6이 자동으로 동기화됩니다."
                } else {
                    "설정 탭에서 Pro 라이선스를 인증한 뒤 다시 켜 주세요."
                },
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
    routeManagementEnabled: Boolean,
    onAddressChange: (Int, String) -> Unit,
    onNaverNavigate: (String, RouteAddressMapPointUiModel?) -> Unit,
    onTmapNavigate: (String) -> Unit,
    onCompleteAddress: ((Int) -> Unit)? = null,
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
                    onComplete = onCompleteAddress
                        ?.takeIf { routeManagementEnabled }
                        ?.let { complete -> { complete(index) } },
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
            if (routeManagementEnabled) {
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
}

@Composable
private fun ManualAddressBlock(
    index: Int,
    label: String,
    address: String,
    onAddressChange: (String) -> Unit,
    onNaverNavigate: () -> Unit,
    onTmapNavigate: () -> Unit,
    onComplete: (() -> Unit)?,
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
                Text("네이버")
            }
            Button(
                onClick = onTmapNavigate,
                enabled = address.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("TMAP")
            }
            if (onComplete != null) {
                OutlinedButton(
                    onClick = onComplete,
                    enabled = address.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("완료")
                }
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

private const val NaviCurrentLocationRefreshMillis = 10_000L
