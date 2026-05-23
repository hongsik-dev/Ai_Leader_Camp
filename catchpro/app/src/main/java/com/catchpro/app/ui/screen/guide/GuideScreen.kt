package com.catchpro.app.ui.screen.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.catchpro.app.ui.components.ScreenScaffold

@Composable
fun GuideScreen() {
    val context = LocalContext.current
    val appRole = if (BuildConfig.IS_NAVI_APP) "CatchPro Navi" else "인성 CatchPro"
    val editionLabel = CatchProEdition.label
    val proNotice = CatchProFeatureGate.proEntitlementNotice(context)
    val autoConfirmAvailable = CatchProFeatureGate.autoConfirmAvailable(context)
    val routeAddressCloudSyncAvailable = CatchProFeatureGate.routeAddressCloudSyncAvailable(context)
    val naviOptimizationAvailable = CatchProFeatureGate.naviOptimizationAvailable(context)

    ScreenScaffold(
        title = "사용설명서",
        subtitle = "$appRole $editionLabel 버전에서 사용할 수 있는 기능 권한과 운행 전 확인할 항목입니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EditionSummaryCard(appRole = appRole, editionLabel = editionLabel)
            proNotice?.let { ProEntitlementNoticeCard(message = it) }
            FeaturePermissionCard(
                title = "사용 가능 기능",
                rows = if (BuildConfig.IS_NAVI_APP) {
                    naviFeatureRows(
                        routeAddressCloudSyncAvailable = routeAddressCloudSyncAvailable,
                        naviOptimizationAvailable = naviOptimizationAvailable,
                    )
                } else {
                    insungFeatureRows(
                        autoConfirmAvailable = autoConfirmAvailable,
                        routeAddressCloudSyncAvailable = routeAddressCloudSyncAvailable,
                    )
                },
            )
            RequiredPermissionCard(
                rows = if (BuildConfig.IS_NAVI_APP) naviRequiredPermissionRows() else insungRequiredPermissionRows(),
            )
            UsageFlowCard(
                title = if (BuildConfig.IS_NAVI_APP) "Navi 사용 흐름" else "인성 CatchPro 사용 흐름",
                steps = if (BuildConfig.IS_NAVI_APP) naviUsageSteps() else insungUsageSteps(),
            )
            LimitedFeatureCard(
                rows = limitedFeatureRows(
                    autoConfirmAvailable = autoConfirmAvailable,
                    routeAddressCloudSyncAvailable = routeAddressCloudSyncAvailable,
                ),
            )
        }
    }
}

@Composable
private fun ProEntitlementNoticeCard(message: String) {
    Card {
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
private fun EditionSummaryCard(
    appRole: String,
    editionLabel: String,
) {
    Card {
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
                        "오더 조건을 저장하고 기능 범위를 확인하는 기본 버전입니다. 자동확정은 포함되지 않습니다."
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
    Card {
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
    Card {
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
    Card {
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
                    Text(
                        text = step,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitedFeatureCard(rows: List<GuideRow>) {
    Card {
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
            Text(
                text = row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun insungFeatureRows(
    autoConfirmAvailable: Boolean,
    routeAddressCloudSyncAvailable: Boolean,
): List<GuideRow> = listOf(
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

private fun naviFeatureRows(
    routeAddressCloudSyncAvailable: Boolean,
    naviOptimizationAvailable: Boolean,
): List<GuideRow> = listOf(
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
    GuideRow(
        title = "AWS 주소 동기화",
        description = "인성 CatchPro와 주소 완료/초기화 상태를 주고받습니다.",
        enabled = routeAddressCloudSyncAvailable,
    ),
    GuideRow(
        title = "행정동 거리 확인",
        description = "추가오더 후보 행정동을 입력해 현재 방문 흐름과 대략 거리를 확인합니다.",
        enabled = naviOptimizationAvailable,
    ),
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
        "오더 조건 탭에서 도착지와 최소요금 기준을 저장합니다.",
        "인성앱에서 오더를 직접 확인하고 수동으로 운행 판단합니다.",
        "주소/네비가 필요하면 TMAP 연결 탭에서 주소를 직접 입력해 사용합니다.",
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
