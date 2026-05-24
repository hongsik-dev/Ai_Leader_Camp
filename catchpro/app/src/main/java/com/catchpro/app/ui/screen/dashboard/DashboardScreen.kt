package com.catchpro.app.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.region.KoreaAdministrativeAreas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.feature.CatchProEdition
import com.catchpro.app.ui.components.EmphasisSegment
import com.catchpro.app.ui.components.EmphasisText
import com.catchpro.app.ui.components.ScreenScaffold

@Composable
fun DashboardScreen(
    settingsRepository: SettingsRepository,
    onOpenDestinations: () -> Unit,
    onReviewLatestMatch: () -> Unit,
    onOpenTmapQueue: () -> Unit,
) {
    val settings = settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null).value
    val isInsungFree = BuildConfig.IS_FREE_EDITION && !BuildConfig.IS_NAVI_APP

    ScreenScaffold(
        title = "대시보드",
        subtitle = if (isInsungFree) {
            "Insung Free는 권한 확인과 Pro 체험 신청용입니다. 실제 오더확정 보조는 Insung Pro에서 제공합니다."
        } else {
            "메인 오더를 잡고, 비슷한 목적지의 추가 오더를 빠르게 이어 붙이는 흐름을 한 화면에서 확인합니다."
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DashboardGoalCard(isInsungFree = isInsungFree)
            if (isInsungFree) {
                FreeEditionSummaryCard()
            } else {
                LiveConditionSummaryCard(
                    primaryEnabled = settings?.primaryAutoConfirmEnabled == true,
                    primaryDestinationKeywords = settings?.primaryDestinationKeywords.orEmpty(),
                    primaryMinimumPrice = settings?.primaryMinimumPriceText.orEmpty(),
                )
            }
            DashboardActionCard(
                isInsungFree = isInsungFree,
                onOpenDestinations = onOpenDestinations,
                onReviewLatestMatch = onReviewLatestMatch,
                onOpenTmapQueue = onOpenTmapQueue,
            )
        }
    }
}

@Composable
private fun DashboardGoalCard(isInsungFree: Boolean) {
    val firstGoal = when {
        isInsungFree ->
            "1. 설치 전 접근성/알림/위치 권한 흐름을 확인"
        CatchProEdition.experimentalAutoDetailConfirmAvailable ->
            "1. 인성 리스트에서 오더가 보이면 자동상세확정으로 빠르게 상세화면 진입"
        CatchProEdition.autoConfirmAvailable ->
            "1. 사용자가 인성 리스트에서 직접 연 상세화면을 빠르게 판정"
        else ->
            "1. 오더 조건을 저장하고 운행 전 기준을 확인"
    }
    val secondGoal = when {
        isInsungFree -> "2. 오더 조건과 오더확정 보조는 Pro에서 사용"
        CatchProEdition.autoConfirmAvailable -> "2. 도착지 선택과 요금 조건이 맞으면 확인 팝업 없이 즉시 확정"
        else -> "2. 자동확정은 Pro 또는 개인 운행판에서 사용"
    }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "운영 목표",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            EmphasisText(
                segments = firstGoal.toDashboardEmphasisSegments(),
                style = MaterialTheme.typography.bodyLarge,
            )
            EmphasisText(
                segments = secondGoal.toDashboardEmphasisSegments(),
                style = MaterialTheme.typography.bodyLarge,
            )
            EmphasisText(
                segments = if (isInsungFree) {
                    "3. 지도/네비 운행은 Navi Free, 오더확정 보조는 Insung Pro로 분리"
                } else {
                    "3. 상세주소는 TMAP 연결 탭에 저장해 TMAP 실행과 운행 후 주소 분석에 사용"
                }.toDashboardEmphasisSegments(),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun FreeEditionSummaryCard() {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Free 기능 범위",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SummaryRow("오더 조건", "Pro 기능")
            SummaryRow("오더확정 보조", "Pro 기능")
            SummaryRow("Free 용도", "권한 확인 / Pro 체험 신청")
        }
    }
}

@Composable
private fun LiveConditionSummaryCard(
    primaryEnabled: Boolean,
    primaryDestinationKeywords: String,
    primaryMinimumPrice: String,
) {
    val primaryDestinationSummary = KoreaAdministrativeAreas.coverageLabelsForKeywords(
        primaryDestinationKeywords.split(',', '\n').map(String::trim).filter(String::isNotBlank),
    ).ifEmpty {
        listOf(primaryDestinationKeywords.ifBlank { "조건 없음" })
    }.joinToString(separator = ", ")

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "현재 자동확정 기준",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (primaryEnabled) "기준 오더 자동확정 사용 중" else "기준 오더 자동확정 대기 중",
                style = MaterialTheme.typography.bodyLarge,
                color = if (primaryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryRow("기준 도착지 조건", primaryDestinationSummary)
            SummaryRow("기준 요금", primaryMinimumPrice.ifBlank { "조건 없음" }.let { if (it == "조건 없음") it else "$it 원 이상" })
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DashboardActionCard(
    isInsungFree: Boolean,
    onOpenDestinations: () -> Unit,
    onReviewLatestMatch: () -> Unit,
    onOpenTmapQueue: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "빠른 이동",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            EmphasisText(
                segments = if (isInsungFree) {
                    "Free에서는 오더 조건을 실행하지 않습니다. Pro 체험 신청을 확인하거나 주소/네비 화면을 점검하세요."
                } else {
                    "오더 조건에서 자동확정 규칙을 관리하고, TMAP 연결에서 상세주소와 네비 연결을 관리하세요."
                }.toDashboardEmphasisSegments(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenDestinations,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isInsungFree) "Pro 체험하기" else "오더 조건 열기")
            }
            OutlinedButton(
                onClick = onOpenTmapQueue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("TMAP 연결 열기")
            }
            OutlinedButton(
                onClick = onReviewLatestMatch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("샘플 오더 확인")
            }
        }
    }
}

private fun String.toDashboardEmphasisSegments(): List<EmphasisSegment> {
    val keywords = listOf(
        "자동으로 확정",
        "자동상세확정",
        "자동확정",
        "오더확정 보조",
        "오더 조건",
        "Pro 체험 신청",
        "Insung Pro",
        "Navi Free",
        "도착지 선택",
        "요금 조건",
        "TMAP 연결",
        "상세주소",
        "네비 연결",
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
