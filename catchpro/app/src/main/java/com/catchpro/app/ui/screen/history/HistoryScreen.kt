package com.catchpro.app.ui.screen.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchpro.app.data.local.entity.OrderEventEntity
import com.catchpro.app.data.repository.OrderEventRepository
import com.catchpro.app.ui.components.ScreenScaffold
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    orderEventRepository: OrderEventRepository,
) {
    val factory = remember(orderEventRepository) {
        HistoryViewModel.factory(orderEventRepository)
    }
    val viewModel: HistoryViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "이력",
        subtitle = "오더 추적 중 추가오더가 실패/제외된 사유만 표시합니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HistoryListCard(events = uiState.events)
            HistorySummaryCard(uiState = uiState)
            HistoryActionsCard(
                onClearHistory = viewModel::clearHistory,
            )
        }
    }
}

@Composable
private fun HistorySummaryCard(
    uiState: HistoryUiState,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "실행 요약",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${uiState.events.size}건 · 실패 ${uiState.failedCount}건 · 제외 ${uiState.skippedCount}건",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "주소동기화/픽업/하차/성공 로그는 숨기고, 추적 실패 원인만 보여줍니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryActionsCard(
    onClearHistory: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "이력 관리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = onClearHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("이력 비우기")
            }
        }
    }
}

@Composable
private fun HistoryListCard(
    events: List<OrderEventEntity>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "최근 기록",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${events.size}건",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (events.isEmpty()) {
            Card {
                Text(
                    text = "아직 오더추적 실패/제외 이력이 없습니다.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            events.forEach { event ->
                HistoryEventCard(event = event)
            }
        }
    }
}

@Composable
private fun HistoryEventCard(
    event: OrderEventEntity,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = event.createdAtMillis.toTimestamp(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = event.orderTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusBadge(status = event.status)
            }
            Text(
                text = event.routeSummaryText(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${event.price.asCurrency()}원",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = event.status.toPriceColor(),
            )
            if (event.failureReason != null) {
                Text(
                    text = event.status.toReasonLabel(event.failureReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = event.status.toReasonColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: String,
) {
    val colors = status.toStatusBadgeColors()
    Surface(
        modifier = Modifier.widthIn(max = 148.dp),
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = status.toStatusLabel(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class StatusBadgeColors(
    val container: Color,
    val content: Color,
)

@Composable
private fun String.toStatusBadgeColors() = when (this) {
    HistoryViewModel.StatusConfirmed,
    "primary-auto-confirmed",
    "primary-manual-confirmed" -> StatusBadgeColors(
        container = Color(0xFF0B5CAD),
        content = Color.White,
    )
    "secondary-auto-confirmed",
    "secondary-manual-confirmed",
    "tracked-additional-auto-confirmed",
    "tracked-additional-manual-confirmed" -> StatusBadgeColors(
        container = Color(0xFF047857),
        content = Color.White,
    )
    "tracked-additional-manual-review" -> StatusBadgeColors(
        container = Color(0xFF7C3AED),
        content = Color.White,
    )
    "tracked-additional-special-manual-review" -> StatusBadgeColors(
        container = Color(0xFFC2410C),
        content = Color.White,
    )
    "order-list-auto-entry" -> StatusBadgeColors(
        container = Color(0xFFFFD54F),
        content = Color(0xFF2A1B00),
    )
    "tracked-additional-rejected",
    "tracked-additional-cancelled",
    "order-list-auto-entry-skipped",
    "order-list-auto-entry-list-excluded" -> StatusBadgeColors(
        container = Color(0xFF475569),
        content = Color.White,
    )
    "manual-review" -> StatusBadgeColors(
        container = Color(0xFF7C3AED),
        content = Color.White,
    )
    "special-manual-review" -> StatusBadgeColors(
        container = Color(0xFFC2410C),
        content = Color.White,
    )
    "active-drive-synced",
    "active-drive-manual-sync" -> StatusBadgeColors(
        container = Color(0xFF1D4ED8),
        content = Color.White,
    )
    "primary-detail-address-synced",
    "secondary-detail-address-synced",
    "primary-pickup-detail-address-synced",
    "primary-dropoff-detail-address-synced",
    "primary-route-detail-address-synced",
    "secondary-pickup-detail-address-synced",
    "secondary-dropoff-detail-address-synced",
    "secondary-route-detail-address-synced" -> StatusBadgeColors(
        container = Color(0xFF0369A1),
        content = Color.White,
    )
    in HistoryViewModel.FailedStatuses -> StatusBadgeColors(
        container = Color(0xFFB91C1C),
        content = Color.White,
    )
    in HistoryViewModel.SkippedStatuses,
    HistoryViewModel.StatusSkipped,
    "auto-cancelled" -> StatusBadgeColors(
        container = Color(0xFF334155),
        content = Color.White,
    )
    else -> StatusBadgeColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun String.toPriceColor() = when (this) {
    in HistoryViewModel.FailedStatuses -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun String.toReasonColor() = when (this) {
    in HistoryViewModel.FailedStatuses -> MaterialTheme.colorScheme.error
    "auto-cancelled" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun String.toStatusLabel(): String = when (this) {
    HistoryViewModel.StatusConfirmed -> "확정"
    "primary-auto-confirmed" -> "1차 자동확정"
    "secondary-auto-confirmed" -> "2차 자동확정"
    "tracked-additional-auto-confirmed" -> "추가오더 자동확정"
    "primary-manual-confirmed" -> "1차 수동확정"
    "secondary-manual-confirmed" -> "2차 수동확정"
    "tracked-additional-manual-confirmed" -> "추가오더 수동확정"
    "manual-review" -> "수동확인 알림"
    "tracked-additional-manual-review" -> "추가오더 수동확인"
    "special-manual-review" -> "특수오더 확인"
    "tracked-additional-special-manual-review" -> "추가 특수오더 확인"
    "tracked-additional-cancelled" -> "추가오더 취소"
    "tracking-reference-pickup-match-missed" -> "A 상차 매칭 실패"
    "tracked-additional-auto-confirm-click-failed" -> "B 자동확정 클릭실패"
    "tracked-additional-auto-confirm-unverified" -> "B 자동확정 미검증"
    "tracked-additional-manual-confirm-unverified" -> "B 수동확정 미검증"
    "order-tracking-auto-entry-blocked" -> "추적 자동진입 보류"
    "active-drive-synced" -> "메인오더 자동연동"
    "primary-detail-address-synced" -> "1차 상세주소 갱신"
    "secondary-detail-address-synced" -> "2차 상세주소 갱신"
    "primary-pickup-detail-address-synced" -> "1차 출발지 상세주소 갱신"
    "primary-dropoff-detail-address-synced" -> "1차 도착지 상세주소 갱신"
    "primary-route-detail-address-synced" -> "1차 출도착 상세주소 갱신"
    "secondary-pickup-detail-address-synced" -> "2차 출발지 상세주소 갱신"
    "secondary-dropoff-detail-address-synced" -> "2차 도착지 상세주소 갱신"
    "secondary-route-detail-address-synced" -> "2차 출도착 상세주소 갱신"
    "manual-skipped" -> "수동 보류"
    "active-drive-manual-sync" -> "메인오더 수동저장"
    "auto-cancelled" -> "자동확정 후 취소"
    "manual-confirm-failed" -> "수동확정 실패"
    "manual-input-required" -> "수동입력 필요"
    "tracked-additional-manual-input-required" -> "추가오더 수동입력"
    "tracked-additional-rejected" -> "추가오더 제외"
    "tracked-additional-detail-not-open" -> "추가오더 상세 미진입"
    "order-list-auto-entry" -> "오더리스트 자동진입"
    "order-list-auto-entry-failed" -> "자동진입 실패"
    "order-list-auto-entry-detail-not-open" -> "상세진입 미확인"
    "order-list-auto-entry-skipped" -> "자동진입 제외"
    "order-list-auto-entry-list-excluded" -> "리스트 자동진입 제외"
    "tmap-arrival-detected" -> "TMAP 도착 감지"
    "pickup-button-clicked" -> "픽업 버튼 감지"
    "pickup-complete-prompt-detected" -> "픽업완료 확인창"
    "pickup-completed-confirmed" -> "픽업완료 예 클릭"
    "dropoff-signature-button-clicked" -> "도착지 서명 감지"
    "dropoff-complete-prompt-detected" -> "하차완료 확인창"
    "dropoff-send-action-clicked" -> "하차 전송/저장 감지"
    "dropoff-completed-confirmed" -> "하차완료 예 클릭"
    HistoryViewModel.StatusFailed -> "실패"
    HistoryViewModel.StatusSkipped -> "취소"
    else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private fun String.toReasonLabel(reason: String): String = when (this) {
    "manual-review" -> "조건 일치 알림 근거 · $reason"
    "special-manual-review" -> "특수오더 확인 근거 · $reason"
    "active-drive-synced" -> "메인오더 목적지 자동기록 · $reason"
    "active-drive-manual-sync" -> "메인오더 목적지 기록 · $reason"
    "primary-detail-address-synced",
    "secondary-detail-address-synced",
    "primary-pickup-detail-address-synced",
    "primary-dropoff-detail-address-synced",
    "primary-route-detail-address-synced",
    "secondary-pickup-detail-address-synced",
    "secondary-dropoff-detail-address-synced",
    "secondary-route-detail-address-synced" -> "상세주소 갱신 · $reason"
    "primary-auto-confirmed",
    "secondary-auto-confirmed",
    "primary-manual-confirmed",
    "secondary-manual-confirmed",
    "tracked-additional-auto-confirmed",
    "tracked-additional-manual-confirmed" -> "확정 근거 · $reason"
    "tracked-additional-manual-review",
    "tracked-additional-special-manual-review" -> "추가오더 확인 근거 · $reason"
    "tracked-additional-cancelled" -> "추가오더 취소 기록 · $reason"
    "tracking-reference-pickup-match-missed" -> "A 상차완료 매칭 실패 · $reason"
    "tracked-additional-auto-confirm-click-failed" -> "B 자동확정 클릭 실패 · $reason"
    "tracked-additional-auto-confirm-unverified" -> "B 자동확정 검증 실패 · $reason"
    "tracked-additional-manual-confirm-unverified" -> "B 수동확정 검증 실패 · $reason"
    "order-tracking-auto-entry-blocked" -> "추적 자동진입 보류 · $reason"
    "manual-skipped" -> "보류 사유 · $reason"
    "auto-cancelled" -> "취소 기록 · $reason"
    "manual-input-required" -> "주소/거리 계산 실패 · $reason"
    "tracked-additional-manual-input-required" -> "추가오더 주소/거리 계산 실패 · $reason"
    "tracked-additional-rejected" -> "추가오더 제외 사유 · $reason"
    "tracked-additional-detail-not-open" -> "추가오더 상세진입 실패 · $reason"
    "order-list-auto-entry" -> "자동진입 기록 · $reason"
    "order-list-auto-entry-failed" -> "자동진입 실패 · $reason"
    "order-list-auto-entry-detail-not-open" -> "상세진입 미확인 · $reason"
    "order-list-auto-entry-skipped" -> "자동진입 제외 · $reason"
    "order-list-auto-entry-list-excluded" -> "리스트 제외 · $reason"
    "tmap-arrival-detected",
    "pickup-button-clicked",
    "pickup-complete-prompt-detected",
    "pickup-completed-confirmed",
    "dropoff-signature-button-clicked",
    "dropoff-complete-prompt-detected",
    "dropoff-send-action-clicked",
    "dropoff-completed-confirmed" -> "운행추적 로그 · $reason"
    "manual-confirm-failed",
    HistoryViewModel.StatusFailed -> "실패 사유 · $reason"
    else -> reason
}

private fun OrderEventEntity.routeSummaryText(): String =
    "${originSummary.toReadableOriginSummary()} → ${destinationSummary.toReadableDestinationSummary()}"

private fun String.toReadableOriginSummary(): String {
    val normalized = trim()
        .removePrefix("1차 ")
        .removePrefix("2차 ")
        .trim()

    return when (normalized) {
        "1차", "2차" -> "리스트 오더"
        "비공개" -> "출발지 비공개"
        "미확인" -> "출발지 미확인"
        else -> normalized.ifBlank { "출발지 미확인" }
    }
}

private fun String.toReadableDestinationSummary(): String {
    val normalized = trim()
    return when (normalized) {
        "미확인" -> "도착지 미확인"
        else -> normalized.ifBlank { "도착지 미확인" }
    }
}
private fun Int.asCurrency(): String = NumberFormat.getIntegerInstance().format(this)

private fun Long.toTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
