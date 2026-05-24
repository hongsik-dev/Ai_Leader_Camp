package com.catchpro.app.ui.screen.destinations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.region.KoreaAdministrativeAreas
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.ui.components.ScreenScaffold
import com.catchpro.app.ui.screen.settings.NationwideDestinationPickerDialog
import com.catchpro.app.ui.theme.PretendardVariable

private val ProPreviewInk = Color(0xFF15191F)
private val ProPreviewMuted = Color(0xFF5D6472)
private val ProPreviewLine = Color(0xFFDFE3EA)
private val ProPreviewSoft = Color(0xFFF7F8FB)
private val ProPreviewViolet = Color(0xFF6F73FF)
private val ProPreviewVioletDark = Color(0xFF5459E8)
private val ProPreviewVioletSoft = Color(0xFFF2F3FF)
private val ProPreviewFont = PretendardVariable

@Composable
private fun conditionCardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun conditionTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun DestinationsScreen(
    settingsRepository: SettingsRepository,
) {
    if (BuildConfig.IS_FREE_EDITION && !BuildConfig.IS_NAVI_APP) {
        InsungFreeProPreviewScreen()
        return
    }

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
                autoConfirmFeatureAvailable = uiState.autoConfirmFeatureAvailable,
                editionLabel = uiState.editionLabel,
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
            if (uiState.autoDetailFeatureAvailable) {
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
            } else {
                AutoDetailExcludedCard(editionLabel = uiState.editionLabel)
            }
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
private fun InsungFreeProPreviewScreen() {
    val uriHandler = LocalUriHandler.current

    ScreenScaffold(
        title = "",
        subtitle = "",
        showHeader = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ProPreviewHero(
                onApplyClick = { uriHandler.openUri("https://hongsik.blog/catchpro-pro-apply/") },
            )
            ProPreviewSectionTitle("서비스 구성")
            ProPreviewFeatureCard(
                title = "오더 조건",
                segments = listOf(
                    ProPreviewTextSegment("전국 도착지 선택"),
                    ProPreviewTextSegment("과 "),
                    ProPreviewTextSegment("최소요금 기준", highlighted = true),
                    ProPreviewTextSegment("을 저장해 필요한 오더만 판단합니다."),
                ),
            )
            ProPreviewFeatureCard(
                title = "오더확정 보조",
                segments = listOf(
                    ProPreviewTextSegment("조건 통과", highlighted = true),
                    ProPreviewTextSegment("된 인성 상세 오더를 "),
                    ProPreviewTextSegment("자동으로 확정", highlighted = true),
                    ProPreviewTextSegment("하도록 보조합니다."),
                ),
            )
            ProPreviewFeatureCard(
                title = "주소 연동",
                segments = listOf(
                    ProPreviewTextSegment("확정 후 저장된 주소를 "),
                    ProPreviewTextSegment("Navi 운행 흐름", highlighted = true),
                    ProPreviewTextSegment("에 연결해 방문 순서를 빠르게 확인합니다."),
                ),
            )
            ProPreviewSectionTitle("체험 흐름")
            ProPreviewStepCard()
        }
    }
}

private data class ProPreviewTextSegment(
    val text: String,
    val highlighted: Boolean = false,
)

@Composable
private fun ProPreviewHero(
    onApplyClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, ProPreviewLine),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8E90FF),
                                    ProPreviewViolet,
                                    Color(0xFFB4A7FF),
                                ),
                            ),
                            shape = MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "C",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ProPreviewFont,
                    )
                }
                Text(
                    text = "CatchPro Pro",
                    color = ProPreviewViolet,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    fontFamily = ProPreviewFont,
                )
            }
            Text(
                text = "첫 달은 무료체험으로 시작하고, 계속 사용할 때만 월 9,900원 구독으로 전환합니다.",
                color = ProPreviewInk,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                fontFamily = ProPreviewFont,
                lineHeight = 31.sp,
            )
            ProPreviewRichText(
                segments = listOf(
                    ProPreviewTextSegment("Insung Pro는 "),
                    ProPreviewTextSegment("오더 조건"),
                    ProPreviewTextSegment("과 "),
                    ProPreviewTextSegment("오더확정 보조", highlighted = true),
                    ProPreviewTextSegment("를 실제 운행 흐름에 맞춰 제공하는 버전입니다."),
                ),
                fontSize = 15.sp,
                lineHeight = 24.sp,
            )
            Button(
                onClick = onApplyClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProPreviewViolet,
                    contentColor = Color.White,
                ),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "무료체험 신청하기",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ProPreviewFont,
                )
            }
        }
    }
}

@Composable
private fun ProPreviewSectionTitle(text: String) {
    Text(
        text = text,
        color = ProPreviewInk,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        fontFamily = ProPreviewFont,
    )
}

@Composable
private fun ProPreviewFeatureCard(
    title: String,
    segments: List<ProPreviewTextSegment>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProPreviewSoft),
        border = BorderStroke(1.dp, ProPreviewLine),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = ProPreviewInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ProPreviewFont,
            )
            ProPreviewRichText(
                segments = segments,
                fontSize = 15.sp,
                lineHeight = 24.sp,
            )
        }
    }
}

@Composable
private fun ProPreviewStepCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ProPreviewLine),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProPreviewStepRow(
                number = "1",
                segments = listOf(
                    ProPreviewTextSegment("무료체험 신청", highlighted = true),
                    ProPreviewTextSegment(" 후 사용 환경을 확인합니다."),
                ),
            )
            ProPreviewStepRow(
                number = "2",
                segments = listOf(
                    ProPreviewTextSegment("Insung Pro 설치"),
                    ProPreviewTextSegment("와 라이선스 안내를 받습니다."),
                ),
            )
            ProPreviewStepRow(
                number = "3",
                segments = listOf(
                    ProPreviewTextSegment("한 달 체험 후 "),
                    ProPreviewTextSegment("월 9,900원", highlighted = true),
                    ProPreviewTextSegment("으로 계속 사용할 수 있습니다."),
                ),
            )
        }
    }
}

@Composable
private fun ProPreviewStepRow(
    number: String,
    segments: List<ProPreviewTextSegment>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(ProPreviewVioletSoft, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = ProPreviewVioletDark,
                fontWeight = FontWeight.Black,
                fontFamily = ProPreviewFont,
            )
        }
        ProPreviewRichText(
            modifier = Modifier.weight(1f),
            segments = segments,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )
    }
}

@Composable
private fun ProPreviewRichText(
    segments: List<ProPreviewTextSegment>,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) {
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            segments.forEach { segment ->
                if (segment.highlighted) {
                    withStyle(
                        SpanStyle(
                            color = ProPreviewViolet,
                            fontWeight = FontWeight.Black,
                        ),
                    ) {
                        append(segment.text)
                    }
                } else {
                    append(segment.text)
                }
            }
        },
        color = ProPreviewMuted,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Medium,
        fontFamily = ProPreviewFont,
    )
}

@Composable
private fun AutoDetailExcludedCard(editionLabel: String) {
    Card(border = conditionCardBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "확정/상세진입 동작",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$editionLabel 배포판에서는 자동상세확정을 제외했습니다. 조건 저장과 직접 진입 상세화면의 자동확정만 버전별로 관리합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    Card(border = conditionCardBorder()) {
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
                colors = conditionTextFieldColors(),
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
    autoConfirmFeatureAvailable: Boolean,
    editionLabel: String,
    onEnabledChange: (Boolean) -> Unit,
    onDestinationKeywordsChange: (String) -> Unit,
    onMinimumPriceTextChange: (String) -> Unit,
    onOpenNationwidePicker: () -> Unit,
    onSave: () -> Unit,
    isDirty: Boolean,
) {
    Card(border = conditionCardBorder()) {
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
                        text = if (autoConfirmFeatureAvailable) {
                            "도착지 선택과 요금 조건만 만족하면 확정 대상으로 봅니다."
                        } else {
                            "$editionLabel 에서는 조건만 저장하고 자동확정은 Pro에서 사용할 수 있습니다."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = autoConfirmFeatureAvailable,
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
                colors = conditionTextFieldColors(),
            )
            OutlinedButton(
                onClick = onOpenNationwidePicker,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                colors = conditionTextFieldColors(),
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

    Card(border = conditionCardBorder()) {
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
