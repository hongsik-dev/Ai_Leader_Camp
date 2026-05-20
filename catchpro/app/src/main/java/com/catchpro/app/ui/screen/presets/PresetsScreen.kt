package com.catchpro.app.ui.screen.presets

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchpro.app.data.local.entity.OrderEventEntity
import com.catchpro.app.data.repository.OrderEventRepository
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.data.repository.isOperationalDestinationAddress
import com.catchpro.app.ui.components.ScreenScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PresetsScreen(
    settingsRepository: SettingsRepository,
    orderEventRepository: OrderEventRepository,
) {
    val factory = remember(settingsRepository, orderEventRepository) {
        PresetsViewModel.factory(
            settingsRepository = settingsRepository,
            orderEventRepository = orderEventRepository,
        )
    }
    val viewModel: PresetsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeDriveDestinationDraft by remember(uiState.activeDriveDestinationText) {
        mutableStateOf(uiState.activeDriveDestinationText)
    }
    var trackingExcludedKeywordsDraft by remember(uiState.trackingExcludedKeywordsText) {
        mutableStateOf(uiState.trackingExcludedKeywordsText)
    }

    ScreenScaffold(
        title = "오더 추적",
        subtitle = "기준 오더의 도착지 상세주소를 저장한 뒤, 수행 중 조건이 맞는 추가 오더를 계속 추적합니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ActiveDriveCard(
                activeDriveDestinationText = uiState.activeDriveDestinationText,
                activeDriveDestinationDraft = activeDriveDestinationDraft,
                onActiveDriveDestinationDraftChange = { activeDriveDestinationDraft = it },
                onSave = {
                    viewModel.saveActiveDriveDestination(activeDriveDestinationDraft)
                },
                onClear = viewModel::clearActiveDriveDestination,
            )
            SecondaryAutoOrderCard(
                enabled = uiState.trackingModeEnabled,
                activeDriveDestinationText = uiState.activeDriveDestinationText,
                excludedKeywordsText = trackingExcludedKeywordsDraft,
                onEnabledChange = viewModel::setTrackingModeEnabled,
                onExcludedKeywordsChange = { trackingExcludedKeywordsDraft = it },
                onSaveExcludedKeywords = {
                    viewModel.saveTrackingExcludedKeywords(trackingExcludedKeywordsDraft)
                },
                excludedKeywordsDirty =
                    trackingExcludedKeywordsDraft.trim() != uiState.trackingExcludedKeywordsText.trim(),
            )
            TrackingFailureCard(events = uiState.recentTrackingFailures)
        }
    }
}

@Composable
private fun ActiveDriveCard(
    activeDriveDestinationText: String,
    activeDriveDestinationDraft: String,
    onActiveDriveDestinationDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val trimmedDraft = activeDriveDestinationDraft.trim()
    val canSave = trimmedDraft.isOperationalDestinationAddress() &&
        trimmedDraft != activeDriveDestinationText.trim()
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "기준 오더 도착지 상세주소",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (activeDriveDestinationText.isBlank()) {
                Text(
                    text = "아직 기준 오더 도착지 상세주소가 저장되지 않았습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                        text = "저장된 기준 상세주소",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer {
                    Text(
                        text = activeDriveDestinationText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Text(
                text = "인성 상세화면 또는 TMAP 연결 탭에서 저장된 상세주소는 주소 1~6 순서대로 보관됩니다. 필요하면 붙여넣기로 한 번 더 검증하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = activeDriveDestinationDraft,
                onValueChange = onActiveDriveDestinationDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("기준 도착지 상세주소") },
                supportingText = {
                    Text("모든 추가오더 하차지 판단은 이 상세주소 기준으로만 수행됩니다.")
                },
                minLines = 2,
            )
            OutlinedButton(
                onClick = {
                    context.readClipboardText()
                        ?.let(onActiveDriveDestinationDraftChange)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("클립보드 붙여넣기")
            }
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("기준 상세주소 저장")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = activeDriveDestinationText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("기준 상세주소 초기화")
            }
        }
    }
}

private fun Context.readClipboardText(): String? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

@Composable
private fun TrackingFailureCard(
    events: List<OrderEventEntity>,
) {
    var expandedEventIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "최근 추적 실패",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "항목을 누르면 전체 내용을 볼 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (events.isEmpty()) {
                Text(
                    text = "최근 오더추적 실패/제외 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                events.forEach { event ->
                    TrackingFailureItem(
                        event = event,
                        expanded = event.id in expandedEventIds,
                        onToggleExpanded = {
                            expandedEventIds = if (event.id in expandedEventIds) {
                                expandedEventIds - event.id
                            } else {
                                expandedEventIds + event.id
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingFailureItem(
    event: OrderEventEntity,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val detailText = event.trackingFailureDetailText()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${event.createdAtMillis.toTrackingFailureTimestamp()} · ${event.status.toTrackingFailureLabel()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = detailText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SecondaryAutoOrderCard(
    enabled: Boolean,
    activeDriveDestinationText: String,
    excludedKeywordsText: String,
    onEnabledChange: (Boolean) -> Unit,
    onExcludedKeywordsChange: (String) -> Unit,
    onSaveExcludedKeywords: () -> Unit,
    excludedKeywordsDirty: Boolean,
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "추적 모드",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "기준오더 상차완료 후, 같은 생활권의 추가 오더 1건만 보수적으로 이어 붙입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Text(
                text = "자동확정 고정 기준",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FixedTrackingRuleRow("자동확정 시작", "기준오더 상차완료 후")
            FixedTrackingRuleRow("추가오더 확정 수", "1개 고정")
            FixedTrackingRuleRow("상차 판단", "경로상 우회 직선거리 4km 이하")
            FixedTrackingRuleRow("상차→하차", "직선거리 8km 이하")
            FixedTrackingRuleRow("하차지 판단", "기준 상세주소와 생활권 일치")
            Text(
                text = if (activeDriveDestinationText.isBlank()) {
                    "기준 도착지 상세주소가 저장되어야 추적 모드가 판단을 시작합니다."
                } else {
                    "위 기준은 운행 중 수정되지 않도록 고정되어 있습니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = excludedKeywordsText,
                onValueChange = onExcludedKeywordsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("오더추적 제외 키워드") },
                supportingText = {
                    Text("추가오더 상세 전체 텍스트에 포함되면 확인 팝업 없이 바로 스킵합니다.")
                },
                minLines = 1,
                maxLines = 2,
            )
            Button(
                onClick = onSaveExcludedKeywords,
                enabled = excludedKeywordsDirty,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("오더추적 제외 키워드 저장")
            }
        }
    }
}

@Composable
private fun FixedTrackingRuleRow(
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

private fun String.toTrackingFailureLabel(): String = when (this) {
    "tracking-reference-pickup-match-missed" -> "A 상차 매칭 실패"
    "tracked-additional-auto-confirm-click-failed" -> "B 자동확정 클릭실패"
    "tracked-additional-auto-confirm-unverified" -> "B 자동확정 미검증"
    "tracked-additional-manual-confirm-unverified" -> "B 수동확정 미검증"
    "tracked-additional-cancelled" -> "B 추가오더 취소"
    "tracked-additional-rejected" -> "B 추가오더 제외"
    "tracked-additional-detail-not-open" -> "B 상세 미진입"
    "tracked-additional-manual-input-required" -> "B 주소/거리 계산 실패"
    "order-tracking-auto-entry-blocked" -> "추적 자동진입 보류"
    else -> this
}

private fun OrderEventEntity.trackingFailureDetailText(): String =
    failureReason ?: "${originSummary} → ${destinationSummary}"

private fun Long.toTrackingFailureTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
