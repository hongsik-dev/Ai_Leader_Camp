package com.catchpro.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catchpro.app.data.region.KoreaAdministrativeAreas
import com.catchpro.app.data.region.KoreaAdministrativeSelection
import com.catchpro.app.data.region.KoreaProvince

@Composable
fun NationwideDestinationPickerDialog(
    existingKeywordInput: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val initialSelectedKeywords = remember(existingKeywordInput) {
        existingKeywordInput.toKeywordTokens()
            .mapNotNull(KoreaAdministrativeAreas::canonicalKeywordOrNull)
            .toSet()
    }
    val selectedKeywords = remember(initialSelectedKeywords) {
        mutableStateListOf<String>().apply {
            addAll(KoreaAdministrativeAreas.sortKeywords(initialSelectedKeywords))
        }
    }
    var currentStep by rememberSaveable { mutableStateOf(PickerStep.Province) }
    var selectedProvinceName by rememberSaveable {
        mutableStateOf(
            KoreaAdministrativeAreas.provinceForKeyword(initialSelectedKeywords.firstOrNull().orEmpty())?.name
                ?: KoreaAdministrativeAreas.provinces.first().name,
        )
    }
    val currentProvince = KoreaAdministrativeAreas.findProvince(selectedProvinceName)
        ?: KoreaAdministrativeAreas.provinces.first()
    var selectedDistrictName by rememberSaveable {
        mutableStateOf(districtOptionsForProvince(currentProvince).firstOrNull()?.label.orEmpty())
    }
    val currentDistrictOptions = remember(currentProvince.name, currentProvince.districts) {
        districtOptionsForProvince(currentProvince)
    }
    LaunchedEffect(currentProvince.name, currentDistrictOptions) {
        if (selectedDistrictName !in currentDistrictOptions.map { it.label }) {
            selectedDistrictName = currentDistrictOptions.firstOrNull()?.label.orEmpty()
        }
    }
    val currentDistrictOption = currentDistrictOptions.firstOrNull { it.label == selectedDistrictName }
        ?: currentDistrictOptions.firstOrNull()
        ?: DistrictPickerOption(selectedDistrictName, emptyList())
    val currentTowns = remember(currentProvince.name, currentDistrictOption) {
        townOptionsForDistrictOption(currentProvince, currentDistrictOption)
    }

    val clearAdministrativeSelection = {
        selectedKeywords.clear()
        selectedProvinceName = KoreaAdministrativeAreas.provinces.first().name
        selectedDistrictName = districtOptionsForProvince(KoreaAdministrativeAreas.provinces.first())
            .firstOrNull()
            ?.label
            .orEmpty()
        onApply(
            mergeManualKeywordsWithAdministrativeSelection(
                existingInput = existingKeywordInput,
                selectedAdministrativeKeywords = emptySet(),
            ),
        )
    }

    BackHandler {
        currentStep = when (currentStep) {
            PickerStep.Town -> PickerStep.District
            PickerStep.District -> PickerStep.Province
            PickerStep.Province -> {
                onDismiss()
                PickerStep.Province
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF2F2F2F),
        ) {
            when (currentStep) {
                PickerStep.Province -> {
                    ProvinceSelectionStep(
                        provinces = KoreaAdministrativeAreas.provinces,
                        selectedProvinceName = selectedProvinceName,
                        selectedKeywords = selectedKeywords.toSet(),
                        onProvinceClick = { province ->
                            selectedProvinceName = province.name
                            selectedDistrictName = districtOptionsForProvince(province).firstOrNull()?.label.orEmpty()
                        },
                        onToggleFrequentDestinationPreset = { preset ->
                            toggleFrequentDestinationPresetSelection(
                                selectedKeywords = selectedKeywords,
                                preset = preset,
                            )
                        },
                        onConfirm = { currentStep = PickerStep.District },
                        onApplySelection = {
                            onApply(
                                mergeManualKeywordsWithAdministrativeSelection(
                                    existingInput = existingKeywordInput,
                                    selectedAdministrativeKeywords = selectedKeywords.toSet(),
                                ),
                            )
                        },
                        onReset = clearAdministrativeSelection,
                        onCancel = onDismiss,
                    )
                }

                PickerStep.District -> {
                    DistrictSelectionStep(
                        province = currentProvince,
                        districtOptions = currentDistrictOptions,
                        selectedDistrictName = selectedDistrictName,
                        selectedKeywords = selectedKeywords.toSet(),
                        provinceWideSelectionSupported = currentProvince.supportsWholeProvinceSelection(),
                        onBackToProvinces = { currentStep = PickerStep.Province },
                        onDistrictClick = { option ->
                            selectedDistrictName = option.label
                            if (currentProvince.opensTownSelectionOnDistrictClick()) {
                                currentStep = PickerStep.Town
                            }
                        },
                        onToggleWholeProvince = {
                            toggleWholeProvinceSelection(
                                selectedKeywords = selectedKeywords,
                                province = currentProvince,
                            )
                        },
                        onSetDistrictIncluded = { option, included ->
                            setWholeDistrictOptionSelection(
                                selectedKeywords = selectedKeywords,
                                provinceName = currentProvince.name,
                                option = option,
                                included = included,
                            )
                        },
                        onConfirm = { currentStep = PickerStep.Town },
                        onApplySelection = {
                            onApply(
                                mergeManualKeywordsWithAdministrativeSelection(
                                    existingInput = existingKeywordInput,
                                    selectedAdministrativeKeywords = selectedKeywords.toSet(),
                                ),
                            )
                        },
                        onReset = clearAdministrativeSelection,
                        onCancel = onDismiss,
                    )
                }

                PickerStep.Town -> {
                    TownSelectionStep(
                        province = currentProvince,
                        districtOption = currentDistrictOption,
                        towns = currentTowns,
                        selectedKeywords = selectedKeywords,
                        onBackToDistricts = { currentStep = PickerStep.District },
                        onToggleWholeDistrict = {
                            toggleWholeDistrictOptionSelection(
                                selectedKeywords = selectedKeywords,
                                provinceName = currentProvince.name,
                                option = currentDistrictOption,
                            )
                        },
                        onToggleTown = { town ->
                            toggleTownSelection(
                                selectedKeywords = selectedKeywords,
                                provinceName = currentProvince.name,
                                districtName = town.districtName,
                                townName = town.townName,
                            )
                        },
                        onAddAnotherRegion = { currentStep = PickerStep.District },
                        onAddAnotherProvince = { currentStep = PickerStep.Province },
                        onConfirm = {
                            onApply(
                                mergeManualKeywordsWithAdministrativeSelection(
                                    existingInput = existingKeywordInput,
                                    selectedAdministrativeKeywords = selectedKeywords.toSet(),
                                ),
                            )
                        },
                        onReset = clearAdministrativeSelection,
                        onCancel = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProvinceSelectionStep(
    provinces: List<KoreaProvince>,
    selectedProvinceName: String,
    selectedKeywords: Set<String>,
    onProvinceClick: (KoreaProvince) -> Unit,
    onToggleFrequentDestinationPreset: (FrequentDestinationPreset) -> Unit,
    onConfirm: () -> Unit,
    onApplySelection: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PickerHeader(
            title = "전국 시/도 선택",
            subtitle = "먼저 시/도를 고른 뒤, 시·군·구와 동/읍/면까지 내려가서 권역을 고릅니다.",
        )
        SelectedRegionSummary(selectedKeywords = selectedKeywords)
        FrequentDestinationPresetSection(
            selectedKeywords = selectedKeywords,
            onTogglePreset = onToggleFrequentDestinationPreset,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(provinces, key = { it.name }) { province ->
                val isSelected = province.name == selectedProvinceName
                val hasSelection = selectedKeywords.any { keyword ->
                    KoreaAdministrativeAreas.selectionForKeywordOrNull(keyword)?.province?.name == province.name
                }
                RegionRow(
                    label = province.name,
                    trailingText = if (hasSelection && !isSelected) "선택됨" else "",
                    isSelected = isSelected || hasSelection,
                    showCheckmark = isSelected,
                    onClick = { onProvinceClick(province) },
                )
            }
        }
        DistrictPickerFooter(
            detailLabel = "시/군/구 선택",
            detailEnabled = true,
            applyEnabled = selectedKeywords.isNotEmpty(),
            onDetail = onConfirm,
            onApply = onApplySelection,
            onReset = onReset,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun DistrictSelectionStep(
    province: KoreaProvince,
    districtOptions: List<DistrictPickerOption>,
    selectedDistrictName: String,
    selectedKeywords: Set<String>,
    provinceWideSelectionSupported: Boolean,
    onBackToProvinces: () -> Unit,
    onDistrictClick: (DistrictPickerOption) -> Unit,
    onToggleWholeProvince: () -> Unit,
    onSetDistrictIncluded: (DistrictPickerOption, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onApplySelection: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    var searchText by rememberSaveable(province.name) {
        mutableStateOf("")
    }
    val filteredDistrictOptions = remember(districtOptions, searchText) {
        districtOptions.filter { option ->
            matchesRegionSearch(
                query = searchText,
                values = listOf(option.label) + option.districtNames,
            )
        }
    }
    val provinceDistrictKeywords = remember(province.name, province.districts) {
        province.districts.map { district ->
            KoreaAdministrativeAreas.composeKeyword(province.name, district)
        }
    }
    val selectedProvinceDistrictCount = provinceDistrictKeywords.count { it in selectedKeywords }
    val selectedProvinceTownCount = selectedKeywords.count { keyword ->
        KoreaAdministrativeAreas.selectionForKeywordOrNull(keyword)?.province?.name == province.name &&
            KoreaAdministrativeAreas.selectionForKeywordOrNull(keyword)?.isWholeDistrict == false
    }
    val hasProvinceSelection = selectedProvinceDistrictCount > 0 || selectedProvinceTownCount > 0
    val wholeProvinceSelected = provinceDistrictKeywords.isNotEmpty() &&
        provinceDistrictKeywords.all { it in selectedKeywords }

    Column(modifier = Modifier.fillMaxSize()) {
        PickerHeader(
            title = province.fullName,
            subtitle = when {
                province.name == "서울" -> {
                    "구를 누르면 바로 동 선택으로 들어갑니다. 구 전체는 체크박스로 빠르게 선택할 수 있습니다."
                }
                province.name == "경기" -> {
                    "시·군을 누르면 바로 동/읍/면 선택으로 들어갑니다. 구가 있는 시는 하위 구 이름을 함께 표시합니다."
                }
                provinceWideSelectionSupported -> {
                    "${province.name} 전체를 한 번에 선택한 뒤, 원하지 않는 시·군·구만 체크 해제할 수 있습니다."
                }
                else -> {
                    "시·군·구를 고른 뒤, 다음 화면에서 전체 권역 또는 특정 동/읍/면을 선택하세요."
                }
            },
            actionLabel = "시/도 다시 선택",
            onAction = onBackToProvinces,
        )
        SelectedRegionSummary(selectedKeywords = selectedKeywords)
        RegionSearchField(
            value = searchText,
            onValueChange = { searchText = it },
            label = "시/군/구 검색",
            placeholder = "예: 수원시, 처인구",
        )
        if (provinceWideSelectionSupported) {
            WholeDistrictCard(
                label = "${province.name} 전체 선택",
                detail = if (wholeProvinceSelected) {
                    "${province.districts.size}개 시·군·구가 모두 포함되어 있습니다. 빼고 싶은 지역은 아래에서 체크 해제하세요."
                } else {
                    "한 번 누르면 ${province.districts.size}개 시·군·구를 모두 포함합니다."
                },
                checked = wholeProvinceSelected,
                onClick = onToggleWholeProvince,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (filteredDistrictOptions.isEmpty()) {
                item {
                    EmptySearchResult(
                        text = "검색된 시/군/구가 없습니다.",
                    )
                }
            }
            items(filteredDistrictOptions, key = { it.label }) { option ->
                val districtKeywords = option.districtNames.map { district ->
                    KoreaAdministrativeAreas.composeKeyword(province.name, district)
                }
                val selectedWholeDistrictCount = districtKeywords.count { it in selectedKeywords }
                val townSelectionCount = selectedKeywords.count { keyword ->
                    val selection = KoreaAdministrativeAreas.selectionForKeywordOrNull(keyword)
                    selection?.province?.name == province.name &&
                        selection.district in option.districtNames &&
                        !selection.isWholeDistrict
                }
                val wholeOptionSelected = districtKeywords.isNotEmpty() &&
                    districtKeywords.all { it in selectedKeywords }
                val trailingText = when {
                    wholeOptionSelected -> {
                        if (province.name == "경기" && option.districtNames.size > 1) {
                            "시 전체"
                        } else {
                            "권역 전체"
                        }
                    }
                    selectedWholeDistrictCount > 0 && townSelectionCount > 0 -> {
                        "${selectedWholeDistrictCount}개 구 전체 + ${townSelectionCount}개 동/읍/면"
                    }
                    selectedWholeDistrictCount > 0 -> {
                        if (option.districtNames.size > 1) "${selectedWholeDistrictCount}개 구 전체" else "권역 전체"
                    }
                    townSelectionCount > 0 -> "${townSelectionCount}개 동/읍/면 선택"
                    provinceWideSelectionSupported -> "제외"
                    else -> ""
                }
                DistrictRegionRow(
                    label = option.label,
                    trailingText = trailingText,
                    isSelected = option.label == selectedDistrictName || trailingText.isNotBlank(),
                    showCheckmark = option.label == selectedDistrictName,
                    showCheckbox = provinceWideSelectionSupported,
                    checked = selectedWholeDistrictCount > 0 || townSelectionCount > 0,
                    onCheckedChange = { checked ->
                        onSetDistrictIncluded(option, checked)
                    },
                    onClick = { onDistrictClick(option) },
                )
            }
        }
        if (provinceWideSelectionSupported) {
            DistrictPickerFooter(
                detailEnabled = selectedDistrictName.isNotBlank(),
                applyEnabled = hasProvinceSelection,
                onDetail = onConfirm,
                onApply = onApplySelection,
                onReset = onReset,
                onCancel = onCancel,
            )
        } else {
            PickerFooter(
                confirmEnabled = selectedDistrictName.isNotBlank(),
                confirmLabel = "다음",
                onConfirm = onConfirm,
                onReset = onReset,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun TownSelectionStep(
    province: KoreaProvince,
    districtOption: DistrictPickerOption,
    towns: List<TownPickerOption>,
    selectedKeywords: MutableList<String>,
    onBackToDistricts: () -> Unit,
    onToggleWholeDistrict: () -> Unit,
    onToggleTown: (TownPickerOption) -> Unit,
    onAddAnotherRegion: () -> Unit,
    onAddAnotherProvince: () -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    var searchText by rememberSaveable(province.name, districtOption.label) {
        mutableStateOf("")
    }
    val filteredTowns = remember(towns, searchText) {
        towns.filter { town ->
            matchesRegionSearch(
                query = searchText,
                values = listOf(town.label, town.districtName, town.townName),
            )
        }
    }
    val districtKeywords = remember(province.name, districtOption) {
        districtOption.districtNames.map { districtName ->
            KoreaAdministrativeAreas.composeKeyword(province.name, districtName)
        }
    }
    val wholeDistrictSelected = districtKeywords.isNotEmpty() &&
        districtKeywords.all { it in selectedKeywords }
    val selectedTownCount = selectedKeywords.count { keyword ->
        val selection = KoreaAdministrativeAreas.selectionForKeywordOrNull(keyword)
        selection?.province?.name == province.name &&
            selection.district in districtOption.districtNames &&
            !selection.isWholeDistrict
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PickerHeader(
            title = districtOption.label,
            subtitle = if (province.name == "경기" && districtOption.districtNames.size > 1) {
                "필요한 동/읍/면만 고릅니다. 구가 있는 시는 구 이름을 같이 표시합니다."
            } else {
                "권역 전체로 잡거나, 필요한 동/읍/면만 골라서 더 정확하게 설정할 수 있습니다."
            },
            actionLabel = "시/군/구 다시 선택",
            onAction = onBackToDistricts,
        )
        SelectedRegionSummary(selectedKeywords = selectedKeywords)
        RegionSearchField(
            value = searchText,
            onValueChange = { searchText = it },
            label = "동/읍/면 검색",
            placeholder = if (province.name == "경기" && districtOption.districtNames.size > 1) {
                "예: 권선구, 매탄동, 권선구 세류동"
            } else {
                "예: 역삼동, 남사읍"
            },
        )
        WholeDistrictCard(
            label = "${districtOption.label} 전체 선택",
            detail = if (districtOption.districtNames.size > 1) {
                "${districtOption.districtNames.size}개 구와 ${towns.size}개 동/읍/면이 자동 포함됩니다."
            } else {
                "${towns.size}개 동/읍/면이 자동 포함됩니다."
            },
            checked = wholeDistrictSelected,
            onClick = onToggleWholeDistrict,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (filteredTowns.isEmpty()) {
                item {
                    EmptySearchResult(
                        text = "검색된 동/읍/면이 없습니다.",
                    )
                }
            }
            items(filteredTowns, key = { "${it.districtName}|${it.townName}" }) { town ->
                val townKeyword = KoreaAdministrativeAreas.composeTownKeyword(
                    provinceName = province.name,
                    districtName = town.districtName,
                    townName = town.townName,
                )
                TownRow(
                    label = town.label,
                    checked = wholeDistrictSelected || townKeyword in selectedKeywords,
                    enabled = !wholeDistrictSelected,
                    onClick = { onToggleTown(town) },
                )
            }
        }
        TownPickerFooter(
            confirmEnabled = wholeDistrictSelected || selectedTownCount > 0,
            addAnotherEnabled = wholeDistrictSelected || selectedTownCount > 0,
            onAddAnotherRegion = onAddAnotherRegion,
            onAddAnotherProvince = onAddAnotherProvince,
            onConfirm = onConfirm,
            onReset = onReset,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun PickerHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFC9C9C9),
        )
    }
}

@Composable
private fun FrequentDestinationPresetSection(
    selectedKeywords: Set<String>,
    onTogglePreset: (FrequentDestinationPreset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "자주 가는 도착지 10km 권역",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFE5E5E5),
            fontWeight = FontWeight.SemiBold,
        )
        FrequentDestinationPresets.forEach { preset ->
            val selectedCount = preset.keywords.count { it in selectedKeywords }
            OutlinedButton(
                onClick = { onTogglePreset(preset) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (selectedCount == preset.keywords.size) {
                        Color(0xFFD392FF)
                    } else {
                        Color.White
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "${preset.title} (${selectedCount}/${preset.keywords.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = preset.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC9C9C9),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedRegionSummary(
    selectedKeywords: Collection<String>,
) {
    val orderedKeywords = remember(selectedKeywords) {
        KoreaAdministrativeAreas.sortKeywords(selectedKeywords)
    }
    var expanded by rememberSaveable(orderedKeywords) {
        mutableStateOf(false)
    }

    if (orderedKeywords.isEmpty()) return

    val previewKeywords = if (expanded) {
        orderedKeywords
    } else {
        orderedKeywords.take(3)
    }
    val hiddenCount = (orderedKeywords.size - previewKeywords.size).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "현재 선택된 권역 ${orderedKeywords.size}건",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFE5E5E5),
                fontWeight = FontWeight.SemiBold,
            )
            if (orderedKeywords.size > 1 || orderedKeywords.any {
                    KoreaAdministrativeAreas.coverageDetailsForKeyword(it).isNotEmpty()
                }
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "접기" else "상세 보기")
                }
            }
        }
        Column(
            modifier = if (expanded) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            previewKeywords.forEach { keyword ->
                Text(
                    text = KoreaAdministrativeAreas.coverageLabelForKeyword(keyword),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC9C9C9),
                )
                if (expanded) {
                    val details = KoreaAdministrativeAreas.coverageDetailsForKeyword(keyword)
                    if (details.isNotEmpty()) {
                        Text(
                            text = details.joinToString(separator = ", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9F9F9F),
                        )
                    }
                }
            }
            if (!expanded && hiddenCount > 0) {
                Text(
                    text = "외 ${hiddenCount}건은 상세 보기에 숨겨져 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9F9F9F),
                )
            }
        }
    }
}

@Composable
private fun RegionRow(
    label: String,
    trailingText: String,
    isSelected: Boolean,
    showCheckmark: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                if (trailingText.isNotBlank()) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC9C9C9),
                    )
                }
            }
            Text(
                text = when {
                    showCheckmark -> "✓"
                    isSelected -> "•"
                    else -> ""
                },
                style = MaterialTheme.typography.headlineMedium,
                color = if (showCheckmark) Color(0xFFD392FF) else Color(0xFF8E8E8E),
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(color = Color(0xFF4A4A4A))
    }
}

@Composable
private fun DistrictRegionRow(
    label: String,
    trailingText: String,
    isSelected: Boolean,
    showCheckmark: Boolean,
    showCheckbox: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                if (trailingText.isNotBlank()) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC9C9C9),
                    )
                }
            }
            if (showCheckbox) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            } else {
                Text(
                    text = when {
                        showCheckmark -> "✓"
                        isSelected -> "•"
                        else -> ""
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (showCheckmark) Color(0xFFD392FF) else Color(0xFF8E8E8E),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        HorizontalDivider(color = Color(0xFF4A4A4A))
    }
}

@Composable
private fun WholeDistrictCard(
    label: String,
    detail: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC9C9C9),
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = { onClick() },
            )
        }
    }
}

@Composable
private fun TownRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) Color.White else Color(0xFF9F9F9F),
            )
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { onClick() },
            )
        }
        HorizontalDivider(color = Color(0xFF4A4A4A))
    }
}

@Composable
private fun RegionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
    )
}

@Composable
private fun EmptySearchResult(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = Color(0xFFC9C9C9),
    )
}

@Composable
private fun PickerFooter(
    confirmEnabled: Boolean,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color(0xFF8C8C8C),
            ),
        ) {
            Text(confirmLabel)
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
            ),
        ) {
            Text("초기화")
        }
        Button(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A4A4A),
                contentColor = Color.White,
            ),
        ) {
            Text("취소")
        }
    }
}

@Composable
private fun DistrictPickerFooter(
    detailLabel: String = "동/읍/면 세부",
    detailEnabled: Boolean,
    applyEnabled: Boolean,
    onDetail: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDetail,
                enabled = detailEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color(0xFF8C8C8C),
                ),
            ) {
                Text(detailLabel)
            }
            Button(
                onClick = onApply,
                enabled = applyEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A4A4A),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF3A3A3A),
                    disabledContentColor = Color(0xFF8C8C8C),
                ),
            ) {
                Text("완료")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                ),
            ) {
                Text("초기화")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                ),
            ) {
                Text("취소")
            }
        }
    }
}

@Composable
private fun TownPickerFooter(
    confirmEnabled: Boolean,
    addAnotherEnabled: Boolean,
    onAddAnotherRegion: () -> Unit,
    onAddAnotherProvince: () -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onAddAnotherRegion,
                enabled = addAnotherEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color(0xFF8C8C8C),
                ),
            ) {
                Text("같은 시/도 추가")
            }
            OutlinedButton(
                onClick = onAddAnotherProvince,
                enabled = addAnotherEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color(0xFF8C8C8C),
                ),
            ) {
                Text("다른 시/도 추가")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A4A4A),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF3A3A3A),
                    disabledContentColor = Color(0xFF8C8C8C),
                ),
            ) {
                Text("완료")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A4A4A),
                    contentColor = Color.White,
                ),
            ) {
                Text("초기화")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                ),
            ) {
                Text("취소")
            }
        }
    }
}

private fun toggleWholeProvinceSelection(
    selectedKeywords: MutableList<String>,
    province: KoreaProvince,
) {
    val districtKeywords = province.districts.map { district ->
        KoreaAdministrativeAreas.composeKeyword(province.name, district)
    }
    val townKeywords = province.districts.flatMap { district ->
        KoreaAdministrativeAreas.townsForDistrict(province.name, district).map { town ->
            KoreaAdministrativeAreas.composeTownKeyword(province.name, district, town)
        }
    }
    val wholeProvinceSelected = districtKeywords.isNotEmpty() &&
        districtKeywords.all { it in selectedKeywords }

    selectedKeywords.removeAll(districtKeywords.toSet())
    selectedKeywords.removeAll(townKeywords.toSet())
    if (!wholeProvinceSelected) {
        selectedKeywords.addAll(districtKeywords)
    }
}

private fun setWholeDistrictOptionSelection(
    selectedKeywords: MutableList<String>,
    provinceName: String,
    option: DistrictPickerOption,
    included: Boolean,
) {
    val districtKeywords = option.districtNames.map { districtName ->
        KoreaAdministrativeAreas.composeKeyword(provinceName, districtName)
    }
    val townKeywords = option.districtNames.flatMap { districtName ->
        KoreaAdministrativeAreas.townsForDistrict(provinceName, districtName).map { town ->
            KoreaAdministrativeAreas.composeTownKeyword(provinceName, districtName, town)
        }
    }
    selectedKeywords.removeAll(districtKeywords.toSet())
    selectedKeywords.removeAll(townKeywords.toSet())
    if (included) {
        selectedKeywords.addAll(districtKeywords)
    }
}

private fun toggleWholeDistrictOptionSelection(
    selectedKeywords: MutableList<String>,
    provinceName: String,
    option: DistrictPickerOption,
) {
    val districtKeywords = option.districtNames.map { districtName ->
        KoreaAdministrativeAreas.composeKeyword(provinceName, districtName)
    }
    val townKeywords = option.districtNames.flatMap { districtName ->
        KoreaAdministrativeAreas.townsForDistrict(provinceName, districtName).map { town ->
            KoreaAdministrativeAreas.composeTownKeyword(provinceName, districtName, town)
        }
    }
    val wholeOptionSelected = districtKeywords.isNotEmpty() &&
        districtKeywords.all { it in selectedKeywords }
    selectedKeywords.removeAll(districtKeywords.toSet())
    selectedKeywords.removeAll(townKeywords.toSet())
    if (!wholeOptionSelected) {
        selectedKeywords.addAll(districtKeywords)
    }
}

private fun toggleTownSelection(
    selectedKeywords: MutableList<String>,
    provinceName: String,
    districtName: String,
    townName: String,
) {
    val districtKeyword = KoreaAdministrativeAreas.composeKeyword(provinceName, districtName)
    val townKeyword = KoreaAdministrativeAreas.composeTownKeyword(provinceName, districtName, townName)
    selectedKeywords.remove(districtKeyword)
    if (townKeyword in selectedKeywords) {
        selectedKeywords.remove(townKeyword)
    } else {
        selectedKeywords.add(townKeyword)
    }
}

private fun toggleFrequentDestinationPresetSelection(
    selectedKeywords: MutableList<String>,
    preset: FrequentDestinationPreset,
) {
    val presetKeywords = preset.keywords.toSet()
    val alreadySelected = presetKeywords.all { it in selectedKeywords }
    selectedKeywords.removeAll(presetKeywords)
    if (!alreadySelected) {
        selectedKeywords.addAll(KoreaAdministrativeAreas.sortKeywords(presetKeywords))
    }
}

private fun mergeManualKeywordsWithAdministrativeSelection(
    existingInput: String,
    selectedAdministrativeKeywords: Set<String>,
): String {
    val manualKeywords = existingInput.toKeywordTokens()
        .filterNot(KoreaAdministrativeAreas::isAdministrativeKeyword)
    val merged = manualKeywords + KoreaAdministrativeAreas.sortKeywords(selectedAdministrativeKeywords)
    return merged.joinToString(separator = "\n")
}

private fun String.toKeywordTokens(): List<String> {
    return split(",", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun matchesRegionSearch(
    query: String,
    values: List<String>,
): Boolean {
    val normalizedQuery = query.normalizeSearchValue()
    if (normalizedQuery.isBlank()) return true

    val terms = query
        .split(Regex("\\s+"))
        .map(String::normalizeSearchValue)
        .filter(String::isNotBlank)

    val haystack = values.joinToString(separator = " ").normalizeSearchValue()
    return if (terms.isEmpty()) {
        haystack.contains(normalizedQuery)
    } else {
        terms.all { term -> haystack.contains(term) }
    }
}

private fun String.normalizeSearchValue(): String {
    return replace(Regex("\\s+"), "")
        .trim()
        .lowercase()
}

private enum class PickerStep {
    Province,
    District,
    Town,
}

private fun KoreaProvince.supportsWholeProvinceSelection(): Boolean {
    return name == "서울" || name == "경기"
}

private fun KoreaProvince.opensTownSelectionOnDistrictClick(): Boolean {
    return name == "서울" || name == "경기"
}

private data class DistrictPickerOption(
    val label: String,
    val districtNames: List<String>,
)

private data class TownPickerOption(
    val districtName: String,
    val townName: String,
    val label: String,
)

private fun districtOptionsForProvince(province: KoreaProvince): List<DistrictPickerOption> {
    if (province.name != "경기") {
        return province.districts.map { district ->
            DistrictPickerOption(
                label = district,
                districtNames = listOf(district),
            )
        }
    }

    val groupedDistricts = linkedMapOf<String, MutableList<String>>()
    province.districts.forEach { district ->
        groupedDistricts.getOrPut(district.gyeonggiCityOrCountyLabel()) { mutableListOf() }
            .add(district)
    }
    return groupedDistricts.map { (label, districtNames) ->
        DistrictPickerOption(
            label = label,
            districtNames = districtNames,
        )
    }
}

private fun townOptionsForDistrictOption(
    province: KoreaProvince,
    option: DistrictPickerOption,
): List<TownPickerOption> {
    return option.districtNames.flatMap { districtName ->
        KoreaAdministrativeAreas.townsForDistrict(province.name, districtName).map { townName ->
            TownPickerOption(
                districtName = districtName,
                townName = townName,
                label = townLabelForDistrictOption(option, districtName, townName),
            )
        }
    }
}

private fun String.gyeonggiCityOrCountyLabel(): String {
    return substringBefore(" ").trim().ifBlank { this }
}

private fun townLabelForDistrictOption(
    option: DistrictPickerOption,
    districtName: String,
    townName: String,
): String {
    if (option.districtNames.size == 1) return townName

    val districtSuffix = districtName
        .removePrefix(option.label)
        .trim()
    return if (districtSuffix.isBlank()) {
        townName
    } else {
        "$districtSuffix $townName"
    }
}

private data class FrequentDestinationPreset(
    val title: String,
    val detail: String,
    val keywords: List<String>,
)

private val FrequentDestinationPresets = listOf(
    FrequentDestinationPreset(
        title = "이천 호법 퇴근권",
        detail = "이천시 호법면 기준 직선 10km 안쪽: 이천 중심·부발·마장·대월·모가",
        keywords = listOf(
            townKeyword("이천시", "호법면"),
            townKeyword("이천시", "마장면"),
            townKeyword("이천시", "대월면"),
            townKeyword("이천시", "부발읍"),
            townKeyword("이천시", "신둔면"),
            townKeyword("이천시", "모가면"),
            townKeyword("이천시", "창전동"),
            townKeyword("이천시", "관고동"),
            townKeyword("이천시", "중리동"),
            townKeyword("이천시", "증일동"),
            townKeyword("이천시", "율현동"),
            townKeyword("이천시", "진리동"),
            townKeyword("이천시", "안흥동"),
            townKeyword("이천시", "갈산동"),
            townKeyword("이천시", "증포동"),
            townKeyword("이천시", "송정동"),
            townKeyword("이천시", "사음동"),
            townKeyword("이천시", "단월동"),
            townKeyword("이천시", "대포동"),
            townKeyword("이천시", "고담동"),
            townKeyword("이천시", "장록동"),
        ),
    ),
    FrequentDestinationPreset(
        title = "오산 경기동로 퇴근권",
        detail = "오산시 전체 + 화성 동탄/병점 남부 + 평택 진위·서탄 + 용인 남사",
        keywords = listOf(
            KoreaAdministrativeAreas.composeKeyword("경기", "오산시"),
            townKeyword("화성시", "진안동"),
            townKeyword("화성시", "병점동"),
            townKeyword("화성시", "능동"),
            townKeyword("화성시", "반송동"),
            townKeyword("화성시", "석우동"),
            townKeyword("화성시", "오산동"),
            townKeyword("화성시", "청계동"),
            townKeyword("화성시", "영천동"),
            townKeyword("화성시", "목동"),
            townKeyword("화성시", "산척동"),
            townKeyword("화성시", "장지동"),
            townKeyword("화성시", "송동"),
            townKeyword("화성시", "방교동"),
            townKeyword("화성시", "금곡동"),
            townKeyword("화성시", "안녕동"),
            townKeyword("화성시", "정남면"),
            townKeyword("평택시", "진위면"),
            townKeyword("평택시", "서탄면"),
            townKeyword("용인시 처인구", "남사읍"),
        ),
    ),
    FrequentDestinationPreset(
        title = "용인 한숲 퇴근권",
        detail = "처인구 남사·이동 + 화성 동탄 남부 + 오산 동부 + 평택 진위·서탄",
        keywords = listOf(
            townKeyword("용인시 처인구", "남사읍"),
            townKeyword("용인시 처인구", "이동읍"),
            townKeyword("화성시", "오산동"),
            townKeyword("화성시", "청계동"),
            townKeyword("화성시", "영천동"),
            townKeyword("화성시", "중동"),
            townKeyword("화성시", "신동"),
            townKeyword("화성시", "목동"),
            townKeyword("화성시", "산척동"),
            townKeyword("화성시", "장지동"),
            townKeyword("화성시", "송동"),
            townKeyword("화성시", "방교동"),
            townKeyword("화성시", "금곡동"),
            townKeyword("오산시", "부산동"),
            townKeyword("오산시", "오산동"),
            townKeyword("오산시", "원동"),
            townKeyword("오산시", "청학동"),
            townKeyword("오산시", "가장동"),
            townKeyword("오산시", "금암동"),
            townKeyword("오산시", "수청동"),
            townKeyword("오산시", "은계동"),
            townKeyword("오산시", "내삼미동"),
            townKeyword("오산시", "외삼미동"),
            townKeyword("오산시", "서동"),
            townKeyword("오산시", "벌음동"),
            townKeyword("오산시", "두곡동"),
            townKeyword("오산시", "탑동"),
            townKeyword("오산시", "가수동"),
            townKeyword("평택시", "진위면"),
            townKeyword("평택시", "서탄면"),
        ),
    ),
)

private fun townKeyword(
    districtName: String,
    townName: String,
): String = KoreaAdministrativeAreas.composeTownKeyword(
    provinceName = "경기",
    districtName = districtName,
    townName = townName,
)
