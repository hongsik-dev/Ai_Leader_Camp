package com.catchpro.app.ui.screen.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import com.catchpro.app.data.repository.AccessibilityCaptureRepository
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.observation.DeviceLocationProvider
import com.catchpro.app.service.CatchProAccessibilityService
import com.catchpro.app.ui.components.ScreenScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    captureRepository: AccessibilityCaptureRepository,
    settingsRepository: SettingsRepository,
) {
    val factory = remember(settingsRepository) {
        SettingsViewModel.factory(settingsRepository = settingsRepository)
    }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestCapture by captureRepository.latestCapture.collectAsStateWithLifecycle(initialValue = null)
    var packageFilterDraft by remember(uiState.observationPackageFilters) {
        mutableStateOf(uiState.observationPackageFilters)
    }
    var kakaoRestApiKeyDraft by remember(uiState.kakaoRestApiKey) {
        mutableStateOf(uiState.kakaoRestApiKey)
    }
    var showAdvancedDiagnostics by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityEnabled by remember {
        mutableStateOf(CatchProAccessibilityService.isEnabled(context))
    }
    var locationPermissionGranted by remember {
        mutableStateOf(DeviceLocationProvider.hasLocationPermission(context))
    }
    var notificationRuntimePermissionGranted by remember {
        mutableStateOf(context.hasPostNotificationPermission())
    }
    var notificationsEnabledBySystem by remember {
        mutableStateOf(context.areAppNotificationsEnabled())
    }
    var alertChannelStatus by remember {
        mutableStateOf(context.catchProAlertChannelStatus())
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locationPermissionGranted = DeviceLocationProvider.hasLocationPermission(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationRuntimePermissionGranted = context.hasPostNotificationPermission()
        notificationsEnabledBySystem = context.areAppNotificationsEnabled()
        alertChannelStatus = context.catchProAlertChannelStatus()
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = CatchProAccessibilityService.isEnabled(context)
                locationPermissionGranted = DeviceLocationProvider.hasLocationPermission(context)
                notificationRuntimePermissionGranted = context.hasPostNotificationPermission()
                notificationsEnabledBySystem = context.areAppNotificationsEnabled()
                alertChannelStatus = context.catchProAlertChannelStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ScreenScaffold(
        title = "설정",
        subtitle = "접근성, 알림, 위치 권한, 내부 분석 로그처럼 앱이 안정적으로 동작하기 위한 시스템 설정만 다룹니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsToggleCard(
                title = "알림/화면 설정",
                rows = listOf(
                    SettingToggleRowState(
                        title = "화면 켜짐 유지",
                        subtitle = "접근성 서비스가 동작하는 동안 화면이 꺼지지 않도록 유지합니다.",
                        checked = uiState.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                    ),
                ),
            )
            SettingsToggleCard(
                title = "오더 알림",
                rows = listOf(
                    SettingToggleRowState(
                        title = "알림 사용",
                        subtitle = "진동, 음성, 상단 알림의 전체 스위치입니다.",
                        checked = uiState.alertsEnabled,
                        onCheckedChange = viewModel::setAlertsEnabled,
                    ),
                    SettingToggleRowState(
                        title = "진동 알림",
                        subtitle = "빠른 오더 대응을 위한 진동 알림입니다.",
                        checked = uiState.vibrationEnabled,
                        enabled = uiState.alertsEnabled,
                        onCheckedChange = viewModel::setVibrationEnabled,
                    ),
                    SettingToggleRowState(
                        title = "자동확정/자동상세확정 음성 안내",
                        subtitle = "자동확정, 주소 부족, TMAP 연결 필요를 짧은 음성으로 알려 줍니다.",
                        checked = uiState.voiceAlertsEnabled,
                        enabled = uiState.alertsEnabled,
                        onCheckedChange = viewModel::setVoiceAlertsEnabled,
                    ),
                ),
            )
            OrderAlertReadinessCard(
                alertsEnabled = uiState.alertsEnabled,
                runtimePermissionGranted = notificationRuntimePermissionGranted,
                notificationsEnabledBySystem = notificationsEnabledBySystem,
                alertChannelStatus = alertChannelStatus,
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenAppNotificationSettings = {
                    context.openCatchProNotificationSettings()
                },
            )
            AccessibilityReadinessCard(
                accessibilityEnabled = accessibilityEnabled,
                latestCapture = latestCapture,
                observationPackageFilters = uiState.observationPackageFilters,
                onOpenAccessibilitySettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
            )
            RoadDistanceApiCard(
                kakaoApiKey = kakaoRestApiKeyDraft,
                locationPermissionGranted = locationPermissionGranted,
                builtInKakaoApiKeyAvailable = BuildConfig.KAKAO_REST_API_KEY.isNotBlank(),
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            )
            InternalLogNoticeCard()
            AdvancedDiagnosticsCard(
                expanded = showAdvancedDiagnostics,
                observationPackageFilters = packageFilterDraft,
                kakaoApiKey = kakaoRestApiKeyDraft,
                builtInKakaoApiKeyAvailable = BuildConfig.KAKAO_REST_API_KEY.isNotBlank(),
                packageFilterDirty = packageFilterDraft.trim() != uiState.observationPackageFilters.trim(),
                kakaoApiKeyDirty = kakaoRestApiKeyDraft.trim() != uiState.kakaoRestApiKey.trim(),
                onExpandedChange = { showAdvancedDiagnostics = it },
                onObservationPackageFiltersChange = { packageFilterDraft = it },
                onSaveObservationPackageFilters = {
                    viewModel.setObservationPackageFilters(packageFilterDraft)
                },
                onKakaoApiKeyChange = { kakaoRestApiKeyDraft = it },
                onSaveKakaoApiKey = { viewModel.setKakaoRestApiKey(kakaoRestApiKeyDraft) },
            )
            SettingsScopeCard()
        }
    }
}

@Composable
private fun OrderAlertReadinessCard(
    alertsEnabled: Boolean,
    runtimePermissionGranted: Boolean,
    notificationsEnabledBySystem: Boolean,
    alertChannelStatus: AlertChannelStatus,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
) {
    val systemReady = runtimePermissionGranted &&
        notificationsEnabledBySystem &&
        alertChannelStatus != AlertChannelStatus.Blocked
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "오더 알림 상태",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    !alertsEnabled -> "앱 알림: 꺼짐"
                    systemReady -> "상단 알림: 표시 가능"
                    !runtimePermissionGranted -> "상단 알림: 권한 필요"
                    !notificationsEnabledBySystem -> "상단 알림: 시스템에서 차단됨"
                    alertChannelStatus == AlertChannelStatus.Blocked -> "상단 알림: CatchPro 채널 차단됨"
                    else -> "상단 알림: 서비스 실행 후 확인 가능"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (alertsEnabled && !systemReady) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = "채널 상태: ${alertChannelStatus.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRequestNotificationPermission,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !runtimePermissionGranted,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (runtimePermissionGranted) "권한 허용됨" else "알림 권한 허용")
                }
                OutlinedButton(
                    onClick = onOpenAppNotificationSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("알림 설정 열기")
                }
            }
        }
    }
}

@Composable
private fun RoadDistanceApiCard(
    kakaoApiKey: String,
    locationPermissionGranted: Boolean,
    builtInKakaoApiKeyAvailable: Boolean,
    onRequestLocationPermission: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "실제 주행거리 계산",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "기준 오더에서 상세 직선거리가 없을 때만 Kakao 주행거리 API를 보조로 사용합니다. 추적 오더는 기준오더 상차완료 후 추가 1건만, 경로상 우회 직선 4km 이하와 상차→하차 직선 8km 이하로 판단합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    kakaoApiKey.isNotBlank() && builtInKakaoApiKeyAvailable -> "Kakao API: 내장 키 사용 가능"
                    kakaoApiKey.isNotBlank() -> "Kakao API: 직접 입력 키 사용 중"
                    else -> "Kakao API: 미설정, 직선거리 기반 보정만 사용"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (locationPermissionGranted) {
                    "위치 권한: 허용됨"
                } else {
                    "위치 권한: 필요함. 현재 위치에서 상차지까지의 주행거리를 계산하려면 허용해 주세요."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (locationPermissionGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Button(
                onClick = onRequestLocationPermission,
                enabled = !locationPermissionGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (locationPermissionGranted) "위치 권한 허용됨" else "위치 권한 허용")
            }
        }
    }
}

@Composable
private fun InternalLogNoticeCard() {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "분석 로그",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "운행 중 화면이 무거워지는 문제를 줄이기 위해 이력/캡처 로그 화면은 설정에서 숨겼습니다. 정밀로그는 내부 DB에 계속 저장되고, 운행 종료 후 원인 분석용으로 사용합니다.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AdvancedDiagnosticsCard(
    expanded: Boolean,
    observationPackageFilters: String,
    kakaoApiKey: String,
    builtInKakaoApiKeyAvailable: Boolean,
    packageFilterDirty: Boolean,
    kakaoApiKeyDirty: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onObservationPackageFiltersChange: (String) -> Unit,
    onSaveObservationPackageFilters: () -> Unit,
    onKakaoApiKeyChange: (String) -> Unit,
    onSaveKakaoApiKey: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "고급 진단 설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "평소 운행 중에는 건드리지 않는 개발/분석용 항목입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (expanded) "고급 설정 접기" else "고급 설정 보기")
            }
            if (expanded) {
                ObservationPackageFilterFields(
                    value = observationPackageFilters,
                    onValueChange = onObservationPackageFiltersChange,
                    onSave = onSaveObservationPackageFilters,
                    isDirty = packageFilterDirty,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = kakaoApiKey,
                    onValueChange = onKakaoApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kakao REST API 키") },
                    supportingText = {
                        Text(
                            if (builtInKakaoApiKeyAvailable) {
                                "내장 키가 있으면 보통 수정할 필요가 없습니다."
                            } else {
                                "카카오 Local API와 카카오모빌리티 길찾기에 사용할 REST API 키를 입력합니다."
                            },
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
                Button(
                    onClick = onSaveKakaoApiKey,
                    enabled = kakaoApiKeyDirty,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Kakao API 키 저장")
                }
            }
        }
    }
}

@Composable
private fun SettingsScopeCard() {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "화면 역할 정리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "오더 조건 화면에서 기준 오더 규칙을, 오더 추적 화면에서 수행 중 추가 오더 규칙과 기준 도착지 상세주소를 관리합니다. 설정은 시스템 옵션만 남겼습니다.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AccessibilityReadinessCard(
    accessibilityEnabled: Boolean,
    latestCapture: AccessibilityCaptureEntity?,
    observationPackageFilters: String,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "관찰/연동 상태",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (accessibilityEnabled) {
                    "접근성 캡처가 켜져 있습니다. 인성 화면 구조를 읽고, 상세주소 복사 오버레이도 이 설정을 사용합니다."
                } else {
                    "접근성 캡처가 꺼져 있습니다. 실제 오더와 상세주소 복사 기능을 쓰려면 먼저 켜 주세요."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = latestCapture?.let {
                    "최근 캡처: ${it.packageName} · ${it.eventType.toObservationEventLabel()} · ${it.capturedAtMillis.toSettingsTimestamp()}"
                } ?: "아직 캡처가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (observationPackageFilters.isBlank()) {
                    "현재 패키지 범위: 모든 외부 앱"
                } else {
                    "현재 패키지 범위: ${observationPackageFilters.replace('\n', ',')}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("접근성 설정")
            }
        }
    }
}

@Composable
private fun ObservationPackageFilterFields(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    isDirty: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "관찰 패키지 필터",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "패키지명을 고정해 두면 홈 화면이나 다른 앱이 섞여 들어오는 잡음을 줄일 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("패키지명 또는 접두사") },
            supportingText = { Text("예: insung.split.quick") },
            minLines = 2,
        )
        Button(
            onClick = onSave,
            enabled = isDirty,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("패키지 필터 저장")
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    rows: List<SettingToggleRowState>,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            rows.forEach { row ->
                SettingToggleRow(row = row)
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    row: SettingToggleRowState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = row.checked,
            onCheckedChange = row.onCheckedChange,
            enabled = row.enabled,
        )
    }
}

private data class SettingToggleRowState(
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val enabled: Boolean = true,
    val onCheckedChange: (Boolean) -> Unit,
)

private enum class AlertChannelStatus(val label: String) {
    Available("표시 가능"),
    Blocked("차단됨"),
    NotCreated("서비스 실행 후 생성됨"),
}

private const val CatchProAlertChannelId = "catchpro_alerts"

private fun Context.hasPostNotificationPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.areAppNotificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.catchProAlertChannelStatus(): AlertChannelStatus {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return AlertChannelStatus.Available
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = notificationManager.getNotificationChannel(CatchProAlertChannelId)
        ?: return AlertChannelStatus.NotCreated
    return if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
        AlertChannelStatus.Blocked
    } else {
        AlertChannelStatus.Available
    }
}

private fun Context.openCatchProNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun String.toObservationEventLabel(): String = when (this) {
    "Content changed" -> "내용 변경"
    "Window changed" -> "창 변경"
    "List scrolled" -> "목록 스크롤"
    "View clicked" -> "버튼 클릭"
    else -> this
}

private fun Long.toSettingsTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm:ss", Locale.getDefault())
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
