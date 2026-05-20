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
import com.catchpro.app.data.region.KoreaAdministrativeAreas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.ui.components.ScreenScaffold

@Composable
fun DashboardScreen(
    settingsRepository: SettingsRepository,
    onOpenDestinations: () -> Unit,
    onReviewLatestMatch: () -> Unit,
    onOpenTmapQueue: () -> Unit,
) {
    val settings = settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null).value

    ScreenScaffold(
        title = "대시보드",
        subtitle = "메인 오더를 잡고, 비슷한 목적지의 추가 오더를 빠르게 이어 붙이는 흐름을 한 화면에서 확인합니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DashboardGoalCard()
            LiveConditionSummaryCard(
                primaryEnabled = settings?.primaryAutoConfirmEnabled == true,
                primaryDestinationKeywords = settings?.primaryDestinationKeywords.orEmpty(),
                primaryMinimumPrice = settings?.primaryMinimumPriceText.orEmpty(),
            )
            DashboardActionCard(
                onOpenDestinations = onOpenDestinations,
                onReviewLatestMatch = onReviewLatestMatch,
                onOpenTmapQueue = onOpenTmapQueue,
            )
        }
    }
}

@Composable
private fun DashboardGoalCard() {
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
            Text(
                text = "1. 인성 리스트에서 오더가 보이면 자동상세확정으로 빠르게 상세화면 진입",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "2. 도착지 선택과 요금 조건이 맞으면 확인 팝업 없이 즉시 확정",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "3. 상세주소는 TMAP 연결 탭에 저장해 TMAP 실행과 운행 후 주소 분석에 사용",
                style = MaterialTheme.typography.bodyLarge,
            )
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
            Text(
                text = "오더 조건에서 자동확정 규칙을 관리하고, TMAP 연결에서 상세주소와 네비 연결을 관리하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenDestinations,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("오더 조건 열기")
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
