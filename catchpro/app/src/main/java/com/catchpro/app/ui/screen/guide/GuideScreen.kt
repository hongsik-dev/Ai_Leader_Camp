package com.catchpro.app.ui.screen.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catchpro.app.BuildConfig
import com.catchpro.app.feature.CatchProEdition
import com.catchpro.app.feature.CatchProFeatureGate
import com.catchpro.app.ui.components.EmphasisSegment
import com.catchpro.app.ui.components.EmphasisText
import com.catchpro.app.ui.components.ScreenScaffold
import com.catchpro.app.ui.util.openCatchProAccessibilitySettings
import com.catchpro.app.ui.util.openKakaoConsult
import com.catchpro.app.ui.util.openOverlayPermissionSettings

@Composable
fun GuideScreen() {
    val context = LocalContext.current
    val appRole = if (BuildConfig.IS_NAVI_APP) "CatchPro Navi" else "인성 CatchPro"
    val editionLabel = CatchProEdition.label
    val proNotice = CatchProFeatureGate.proEntitlementNotice(context)
    val guideProNotice = proNotice.takeUnless {
        BuildConfig.IS_NAVI_APP && BuildConfig.IS_PRO_EDITION
    }
    val autoConfirmAvailable = CatchProFeatureGate.autoConfirmAvailable(context)
    val routeAddressCloudSyncAvailable = CatchProFeatureGate.routeAddressCloudSyncAvailable(context)
    val naviOptimizationAvailable = CatchProFeatureGate.naviOptimizationAvailable(context)
    val guideAutoConfirmAvailable = if (!BuildConfig.IS_NAVI_APP && BuildConfig.IS_PRO_EDITION) {
        BuildConfig.FEATURE_AUTO_CONFIRM
    } else {
        autoConfirmAvailable
    }
    val guideRouteAddressCloudSyncAvailable = if (BuildConfig.IS_PRO_EDITION) {
        BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC
    } else {
        routeAddressCloudSyncAvailable
    }
    val guideNaviOptimizationAvailable = if (BuildConfig.IS_NAVI_APP && BuildConfig.IS_PRO_EDITION) {
        BuildConfig.FEATURE_NAVI_OPTIMIZATION
    } else {
        naviOptimizationAvailable
    }

    ScreenScaffold(
        title = "사용설명서",
        subtitle = "$appRole $editionLabel 버전에서 사용할 수 있는 기능 권한과 운행 전 확인할 항목입니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EditionSummaryCard(appRole = appRole, editionLabel = editionLabel)
            KakaoConsultCard(onClick = { context.openKakaoConsult() })
            guideProNotice?.let { ProEntitlementNoticeCard(message = it) }
            FeaturePermissionCard(
                title = "사용 가능 기능",
                rows = if (BuildConfig.IS_NAVI_APP) {
                    naviFeatureRows(
                        routeAddressCloudSyncAvailable = guideRouteAddressCloudSyncAvailable,
                        naviOptimizationAvailable = guideNaviOptimizationAvailable,
                    )
                } else {
                    insungFeatureRows(
                        autoConfirmAvailable = guideAutoConfirmAvailable,
                        routeAddressCloudSyncAvailable = guideRouteAddressCloudSyncAvailable,
                    )
                },
            )
            RequiredPermissionCard(
                rows = if (BuildConfig.IS_NAVI_APP) naviRequiredPermissionRows() else insungRequiredPermissionRows(),
            )
            InitialSetupGuideCard(
                title = "기본 설정 방법",
                steps = if (BuildConfig.IS_NAVI_APP) naviInitialSetupSteps() else insungInitialSetupSteps(),
                onAccessibilitySettingsClick = if (!BuildConfig.IS_NAVI_APP) {
                    { context.openCatchProAccessibilitySettings() }
                } else {
                    null
                },
                onOverlayPermissionClick = if (!BuildConfig.IS_NAVI_APP) {
                    { context.openOverlayPermissionSettings() }
                } else {
                    null
                },
            )
            UsageFlowCard(
                title = if (BuildConfig.IS_NAVI_APP) "Navi 사용 흐름" else "인성 CatchPro 사용 흐름",
                steps = if (BuildConfig.IS_NAVI_APP) naviUsageSteps() else insungUsageSteps(),
            )
            LimitedFeatureCard(
                rows = limitedFeatureRows(
                    autoConfirmAvailable = guideAutoConfirmAvailable,
                    routeAddressCloudSyncAvailable = guideRouteAddressCloudSyncAvailable,
                ),
            )
        }
    }
}

@Composable
private fun guideCardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun ProEntitlementNoticeCard(message: String) {
    Card(border = guideCardBorder()) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun KakaoConsultCard(onClick: () -> Unit) {
    Card(border = guideCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "카카오톡 상담",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "설치, 라이선스, Navi 연동 문의는 카카오톡 상담으로 바로 연결할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("카카오톡 상담하기")
            }
        }
    }
}

@Composable
private fun EditionSummaryCard(
    appRole: String,
    editionLabel: String,
) {
    Card(border = guideCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$appRole · $editionLabel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    BuildConfig.IS_NAVI_APP && BuildConfig.IS_FREE_EDITION ->
                        "지도에서 주소 1~6을 확인하고, 필요할 때만 방문순서와 예상시간을 계산하는 버전입니다."
                    BuildConfig.IS_NAVI_APP && BuildConfig.IS_PRO_EDITION ->
                        "인성 CatchPro와 주소를 동기화하고, 지도/네비 중심으로 운행 흐름을 확인하는 버전입니다."
                    !BuildConfig.IS_NAVI_APP && BuildConfig.IS_FREE_EDITION ->
                        "권한 확인과 Pro 체험 신청을 위한 버전입니다. 오더 조건과 오더확정 보조는 포함되지 않습니다."
                    !BuildConfig.IS_NAVI_APP && BuildConfig.IS_PRO_EDITION ->
                        "사용자가 직접 들어간 인성 상세화면에서 조건이 맞으면 자동확정까지 연결하는 버전입니다."
                    else -> "개인 운행판 기능을 확인하는 화면입니다."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeaturePermissionCard(
    title: String,
    rows: List<GuideRow>,
) {
    Card(border = guideCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            rows.forEach { row ->
                GuideStatusRow(row = row)
            }
        }
    }
}

@Composable
private fun RequiredPermissionCard(rows: List<GuideRow>) {
    Card(border = guideCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "필요 권한",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            rows.forEach { row ->
                GuideStatusRow(row = row)
            }
        }
    }
}

@Composable
private fun UsageFlowCard(
    title: String,
    steps: List<String>,
) {
    Card(border = guideCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    EmphasisText(
                        segments = step.toGuideEmphasisSegments(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InitialSetupGuideCard(
    title: String,
    steps: List<String>,
    onAccessibilitySettingsClick: (() -> Unit)? = null,
    onOverlayPermissionClick: (() -> Unit)? = null,
) {
    Card(border = guideCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            steps.forEachIndexed { index, step ->
                val onClick = when {
                    onAccessibilitySettingsClick != null && step.contains("접근성 설정") ->
                        onAccessibilitySettingsClick
                    onOverlayPermissionClick != null && step.contains("다른 앱 위에 표시") ->
                        onOverlayPermissionClick
                    else -> null
                }
                val rowModifier = Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                Row(
                    modifier = rowModifier,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    EmphasisText(
                        segments = step.toGuideEmphasisSegments(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitedFeatureCard(rows: List<GuideRow>) {
    Card(border = guideCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "제한 또는 제외 기능",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            rows.forEach { row ->
                GuideStatusRow(row = row)
            }
        }
    }
}

@Composable
private fun GuideStatusRow(row: GuideRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = if (row.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (row.enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = if (row.enabled) "가능" else "제한",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            EmphasisText(
                segments = row.description.toGuideEmphasisSegments(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun insungFeatureRows(
    autoConfirmAvailable: Boolean,
    routeAddressCloudSyncAvailable: Boolean,
): List<GuideRow> {
    if (BuildConfig.IS_FREE_EDITION) {
        return listOf(
            GuideRow(
                title = "권한 확인",
                description = "접근성, 알림, 위치 권한 흐름을 설치 전에 확인합니다.",
                enabled = true,
            ),
            GuideRow(
                title = "Pro 체험하기",
                description = "첫 달 무료체험과 월 9,900원 구독 신청 흐름을 확인합니다.",
                enabled = true,
            ),
        )
    }
    return listOf(
        GuideRow(
            title = "오더 조건 저장",
            description = "전국 도착지 선택과 최소요금 조건을 저장합니다.",
            enabled = true,
        ),
        GuideRow(
            title = "직접 상세 진입 후 자동확정",
            description = "사용자가 인성 오더 상세화면을 직접 열었을 때 조건 통과 시 확정합니다.",
            enabled = autoConfirmAvailable,
        ),
        GuideRow(
            title = "AWS 주소 동기화",
            description = "인성폰에서 저장한 주소를 Navi폰과 같은 방 코드로 동기화합니다.",
            enabled = routeAddressCloudSyncAvailable,
        ),
        GuideRow(
            title = "주소/네비 연동",
            description = "상세주소 1~6을 관리하고 TMAP 또는 네이버 내비로 실행합니다.",
            enabled = true,
        ),
    )
}

private fun naviFeatureRows(
    routeAddressCloudSyncAvailable: Boolean,
    naviOptimizationAvailable: Boolean,
): List<GuideRow> = listOfNotNull(
    GuideRow(
        title = "지도에서 주소 1~6 표시",
        description = "현재 위치와 입력된 주소를 지도 위에 표시합니다.",
        enabled = true,
    ),
    GuideRow(
        title = "주소 직접 입력",
        description = if (BuildConfig.IS_FREE_EDITION) {
            "Navi Free 단독 사용 시 주소 편집에서 주소 1~6을 직접 입력해 지도에 표시합니다."
        } else {
            "Navi Pro도 AWS 동기화가 안 되거나 임시 경유지를 넣을 때 주소 편집으로 직접 보정할 수 있습니다."
        },
        enabled = true,
    ),
    GuideRow(
        title = "네이버/TMAP 내비 실행",
        description = "도착지를 눌러 원하는 내비 앱으로 바로 이동합니다.",
        enabled = true,
    ),
    GuideRow(
        title = "방문순서/예상시간 계산",
        description = if (BuildConfig.IS_FREE_EDITION) {
            "주소를 확인한 뒤 필요할 때 버튼을 눌러 방문순서와 예상시간을 계산합니다."
        } else {
            "주소 동기화와 함께 운행 경로를 계산합니다."
        },
        enabled = true,
    ),
    if (routeAddressCloudSyncAvailable) {
        GuideRow(
            title = "AWS 주소 동기화",
            description = "인성 CatchPro와 주소 완료/초기화 상태를 주고받습니다.",
            enabled = true,
        )
    } else {
        null
    },
    if (naviOptimizationAvailable) {
        GuideRow(
            title = "행정동 거리 확인",
            description = "추가오더 후보 행정동을 입력해 현재 방문 흐름과 대략 거리를 확인합니다.",
            enabled = true,
        )
    } else {
        null
    },
)

private fun insungRequiredPermissionRows(): List<GuideRow> = listOf(
    GuideRow(
        title = "접근성 서비스",
        description = "인성 화면을 읽고 상세화면 조건을 판정하려면 필요합니다.",
        enabled = true,
    ),
    GuideRow(
        title = "알림 권한",
        description = "자동확정 결과와 운행 알림을 받으려면 허용합니다.",
        enabled = true,
    ),
    GuideRow(
        title = "위치 권한",
        description = "주소 거리 계산이나 지도 기능을 사용할 때 필요합니다.",
        enabled = true,
    ),
)

private fun naviRequiredPermissionRows(): List<GuideRow> = listOf(
    GuideRow(
        title = "위치 권한",
        description = "현재 위치 출발점과 가까운 방문순서를 표시하려면 필요합니다.",
        enabled = true,
    ),
    GuideRow(
        title = "인터넷 연결",
        description = "네이버 지도, 네이버 Directions, AWS 주소 동기화에 필요합니다.",
        enabled = true,
    ),
    GuideRow(
        title = "네이버 Maps 인증",
        description = "Navi Free/Pro 패키지를 네이버 Cloud Console에 등록해야 지도가 표시됩니다.",
        enabled = true,
    ),
)

private fun insungUsageSteps(): List<String> = when {
    BuildConfig.IS_FREE_EDITION -> listOf(
        "대시보드에서 Free와 Pro 체험 신청 흐름을 확인합니다.",
        "설정에서 접근성, 알림, 위치 권한을 점검합니다.",
        "실제 오더 조건과 오더확정 보조가 필요하면 Pro 체험하기에서 무료체험을 신청합니다.",
    )
    BuildConfig.IS_PRO_EDITION -> listOf(
        "오더 조건 탭에서 도착지와 최소요금 기준을 저장합니다.",
        "인성앱에서 사용자가 직접 오더 상세로 들어가면 CatchPro가 조건을 판정합니다.",
        "조건이 맞으면 자동확정하고, 저장된 주소는 Navi와 동기화해 운행에 사용합니다.",
    )
    else -> listOf(
        "오더 조건을 저장합니다.",
        "인성앱 상세화면에서 조건을 판정합니다.",
        "주소를 저장하고 네비로 연결합니다.",
    )
}

private fun naviUsageSteps(): List<String> = listOf(
    if (BuildConfig.IS_FREE_EDITION) {
        "주소 편집을 열어 주소 1~6을 직접 입력하고 지도 반영을 누릅니다."
    } else {
        "인성 CatchPro와 같은 방 코드로 동기화하거나, 필요하면 주소 편집으로 주소 1~6을 직접 보정합니다."
    },
    "지도에서 현재 위치와 방문 후보 주소를 확인합니다.",
    "필요할 때 방문순서 계산 또는 예상시간 갱신을 눌러 경로를 확인합니다.",
    "방문 완료한 주소는 완료 처리해 지도와 주소 목록에서 제거합니다.",
)

private fun insungInitialSetupSteps(): List<String> = listOf(
    "접근성 설정: 접근성 -> 설치된 앱 -> CatchPro Observation -> 사용 중 ON",
    "화면 오버레이: 인성화면분할 앱을 켜면 화면 상단에 보이는 작은 패널입니다. 자동확정 ON은 사용자가 인성 오더 상세로 들어갔을 때 조건 통과 오더를 자동으로 확정합니다. AWS ON은 저장된 주소 1~6을 CatchPro Navi와 실시간 동기화합니다.",
    "다른 앱 위에 표시: 보통은 필요 없습니다. 접근성 설정을 켰는데도 오버레이가 안 보일 때만 열어서 CatchPro 항목이 보이면 허용합니다.",
    "알림 권한을 허용하면 오더확정 결과와 운행 상태를 놓치지 않고 확인할 수 있습니다.",
    "화면 켜짐 유지를 켜면 운행 중 화면 꺼짐으로 접근성 동작이 끊기는 일을 줄일 수 있습니다.",
)

private fun naviInitialSetupSteps(): List<String> = listOf(
    "위치 권한을 허용해 현재 위치 출발점을 지도에 표시합니다.",
    "주소 편집에서 주소 1~6을 입력하거나, Navi Pro는 인성 CatchPro와 같은 동기화 방 코드를 사용합니다.",
    "방문순서와 예상시간은 필요한 시점에 버튼을 눌러 계산합니다.",
    "행정동 입력 또는 음성 입력으로 추가오더 후보와 현재 방문 흐름의 대략 거리를 확인합니다.",
)

private fun limitedFeatureRows(
    autoConfirmAvailable: Boolean,
    routeAddressCloudSyncAvailable: Boolean,
): List<GuideRow> {
    val autoConfirmLimited = !autoConfirmAvailable && !BuildConfig.IS_NAVI_APP
    val syncLimited = !routeAddressCloudSyncAvailable
    return listOfNotNull(
        if (autoConfirmLimited) {
            GuideRow(
                title = "자동확정",
                description = "Free 버전에서는 자동확정을 사용할 수 없습니다.",
                enabled = false,
            )
        } else null,
        if (syncLimited) {
            GuideRow(
                title = "AWS 주소 동기화",
                description = "현재 버전에서는 주소 동기화가 비활성화되어 있습니다.",
                enabled = false,
            )
        } else null,
    ).ifEmpty {
        listOf(
            GuideRow(
                title = "현재 제한 없음",
                description = "이 버전에서 켜진 기능 범위 안에서 사용할 수 있습니다.",
                enabled = true,
            ),
        )
    }
}

private data class GuideRow(
    val title: String,
    val description: String,
    val enabled: Boolean,
)

private fun String.toGuideEmphasisSegments(): List<EmphasisSegment> {
    val keywords = listOf(
        "자동으로 확정",
        "자동확정",
        "오더확정",
        "오더확정 보조",
        "오더 조건",
        "조건 통과",
        "전국 도착지 선택",
        "최소요금",
        "방문순서",
        "예상시간",
        "주소 1~6",
        "AWS 주소 동기화",
        "네이버/TMAP",
        "Navi Free",
        "Navi Pro",
        "Insung Pro",
        "Pro 체험하기",
        "월 9,900원",
        "무료체험",
        "접근성 설정",
        "접근성",
        "설치된 앱",
        "CatchPro Observation",
        "사용 중 ON",
        "화면 오버레이",
        "인성화면분할",
        "자동확정 ON",
        "조건 통과 오더",
        "AWS ON",
        "실시간 동기화",
        "다른 앱 위에 표시",
        "보통은 필요 없습니다",
        "오버레이가 안 보일 때만",
        "알림 권한",
        "화면 켜짐 유지",
        "위치 권한",
        "동기화 방 코드",
        "행정동",
        "음성 입력",
    )
    val segments = mutableListOf<EmphasisSegment>()
    var cursor = 0
    while (cursor < length) {
        val next = keywords
            .mapNotNull { keyword ->
                val index = indexOf(keyword, startIndex = cursor)
                if (index >= 0) index to keyword else null
            }
            .minByOrNull { it.first }
        if (next == null) {
            segments += EmphasisSegment(substring(cursor))
            break
        }
        val (index, keyword) = next
        if (index > cursor) {
            segments += EmphasisSegment(substring(cursor, index))
        }
        segments += EmphasisSegment(keyword, highlighted = true)
        cursor = index + keyword.length
    }
    return segments
}
