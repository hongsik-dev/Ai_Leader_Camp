package com.catchpro.app.ui.screen.destinations

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchpro.app.data.region.KoreaAdministrativeAreas
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.ui.components.ScreenScaffold
import com.catchpro.app.ui.screen.settings.NationwideDestinationPickerDialog

@Composable
fun DestinationsScreen(
    settingsRepository: SettingsRepository,
) {
    val factory = remember(settingsRepository) {
        DestinationsViewModel.factory(settingsRepository)
    }
    val viewModel: DestinationsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var destinationKeywordsDraft by remember(uiState.destinationKeywords) {
        mutableStateOf(uiState.destinationKeywords)
    }
    var orderListAutoEntryMaxChecksDraft by remember(uiState.orderListAutoEntryMaxChecksText) {
        mutableStateOf(uiState.orderListAutoEntryMaxChecksText)
    }
    var minimumPriceDraft by remember(uiState.minimumPriceText) {
        mutableStateOf(uiState.minimumPriceText)
    }
    var showNationwidePicker by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "오더 조건",
        subtitle = "처음 잡는 기준 오더를 설정하는 탭입니다. 인성 리스트에서 오더를 직접 누르면 CatchPro가 상세 화면에서 조건을 판정하고 자동확정 흐름으로 연결합니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryAutoOrderCard(
                enabled = uiState.enabled,
                destinationKeywords = destinationKeywordsDraft,
                minimumPriceText = minimumPriceDraft,
                onEnabledChange = viewModel::setEnabled,
                onDestinationKeywordsChange = { destinationKeywordsDraft = it },
                onMinimumPriceTextChange = { minimumPriceDraft = it },
                onOpenNationwidePicker = { showNationwidePicker = true },
                onSave = {
                    viewModel.savePrimaryRules(
                        destinationKeywords = destinationKeywordsDraft,
                        minimumPriceText = minimumPriceDraft,
                    )
                },
                isDirty =
                    destinationKeywordsDraft.trim() != uiState.destinationKeywords.trim() ||
                        minimumPriceDraft.trim() != uiState.minimumPriceText.trim(),
            )
            OrderCaptureBehaviorCard(
                autoEntryEnabled = uiState.orderListAutoEntryEnabled,
                maxChecksText = orderListAutoEntryMaxChecksDraft,
                onAutoEntryEnabledChange = viewModel::setOrderListAutoEntryEnabled,
                onMaxChecksTextChange = { value ->
                    orderListAutoEntryMaxChecksDraft = value.filter { it.isDigit() }.take(2)
                },
                onSaveMaxChecks = {
                    viewModel.setOrderListAutoEntryMaxChecksText(orderListAutoEntryMaxChecksDraft)
                },
                maxChecksDirty =
                    orderListAutoEntryMaxChecksDraft.trim() != uiState.orderListAutoEntryMaxChecksText.trim(),
            )
            PrimaryCoverageGuideCard(destinationKeywords = destinationKeywordsDraft)
        }
    }

    if (showNationwidePicker) {
        NationwideDestinationPickerDialog(
            existingKeywordInput = destinationKeywordsDraft,
            onDismiss = { showNationwidePicker = false },
            onApply = { updatedKeywords ->
                destinationKeywordsDraft = updatedKeywords
                showNationwidePicker = false
            },
        )
    }
}

@Composable
private fun OrderCaptureBehaviorCard(
    autoEntryEnabled: Boolean,
    maxChecksText: String,
    onAutoEntryEnabledChange: (Boolean) -> Unit,
    onMaxChecksTextChange: (String) -> Unit,
    onSaveMaxChecks: () -> Unit,
    maxChecksDirty: Boolean,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "확정/상세진입 동작",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "자동확정이 켜져 있으면 CatchPro가 들어간 상세화면과 사용자가 직접 들어간 상세화면 모두 조건 통과 시 즉시 확정합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow(
                title = "자동상세확정",
                subtitle = "인성 신규 오더리스트에서 후보 행을 자동으로 엽니다. 확정은 메인 오더 자동확정 스위치와 조건으로 판단합니다.",
                checked = autoEntryEnabled,
                onCheckedChange = onAutoEntryEnabledChange,
            )
            OutlinedTextField(
                value = maxChecksText,
                onValueChange = onMaxChecksTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("한 사이클 최대 상세 확인 수") },
                supportingText = { Text("1~30 사이로 저장됩니다. 기본 10개입니다.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Button(
                onClick = onSaveMaxChecks,
                enabled = maxChecksDirty,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("자동상세확정 확인 수 저장")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PrimaryAutoOrderCard(
    enabled: Boolean,
    destinationKeywords: String,
    minimumPriceText: String,
    onEnabledChange: (Boolean) -> Unit,
    onDestinationKeywordsChange: (String) -> Unit,
    onMinimumPriceTextChange: (String) -> Unit,
    onOpenNationwidePicker: () -> Unit,
    onSave: () -> Unit,
    isDirty: Boolean,
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
                        text = "메인 오더 자동확정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "도착지 선택과 요금 조건만 만족하면 확정 대상으로 봅니다.",
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
                value = destinationKeywords,
                onValueChange = onDestinationKeywordsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("권역 행정구역 / 도착지 조건") },
                supportingText = {
                    Text("전국 지역 선택에서 서울은 구 밑 동까지, 경기도는 시·군 밑 동/읍/면까지 선택할 수 있습니다.")
                },
                minLines = 3,
            )
            OutlinedButton(
                onClick = onOpenNationwidePicker,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("전국 지역에서 도착지 선택")
            }
            OutlinedTextField(
                value = minimumPriceText,
                onValueChange = onMinimumPriceTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("요금 조건") },
                supportingText = { Text("상세화면 요금이 이 금액 이상일 때만 확정합니다.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Button(
                onClick = onSave,
                enabled = isDirty,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("기준 오더 조건 저장")
            }
        }
    }
}

@Composable
private fun PrimaryCoverageGuideCard(
    destinationKeywords: String,
) {
    val selectedAreas = remember(destinationKeywords) {
        destinationKeywords
            .split(",", "\n")
            .map(String::trim)
            .mapNotNull(KoreaAdministrativeAreas::canonicalKeywordOrNull)
            .distinct()
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "권역 적용 방식",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (selectedAreas.isEmpty()) {
                Text(
                    text = "아직 전국 지역으로 선택한 권역이 없습니다. 서울은 구 아래 동까지, 경기도는 시·군 아래 동/읍/면까지 내려가서 도착지 조건을 좁힐 수 있습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = "현재 선택된 권역",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KoreaAdministrativeAreas.sortKeywords(selectedAreas).forEach { keyword ->
                    Text(
                        text = KoreaAdministrativeAreas.coverageLabelForKeyword(keyword),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val details = KoreaAdministrativeAreas.coverageDetailsForKeyword(keyword)
                    if (details.isNotEmpty()) {
                        Text(
                            text = details.joinToString(separator = ", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "위 권역들은 정식 행정구역 경로로 저장되며, 같은 동 이름이라도 시·도와 시·군·구가 다르면 다른 지역으로 처리합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
