package com.catchpro.app.ui.screen.observation

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import com.catchpro.app.observation.KoreanOrderDraftParser
import com.catchpro.app.observation.ParsedOrderDraft
import com.catchpro.app.data.repository.AccessibilityCaptureRepository
import com.catchpro.app.ui.components.ScreenScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ObservationLogScreen(
    captureRepository: AccessibilityCaptureRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val captures by captureRepository.recentCaptures().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val exportText = pendingExportText
        if (uri == null || exportText == null) {
            pendingExportText = null
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val saved = writeObservationExport(
                context = context,
                uri = uri,
                exportText = exportText,
            )
            pendingExportText = null
            Toast.makeText(
                context,
                if (saved) "관찰 로그 파일을 저장했습니다." else "관찰 로그 파일 저장에 실패했습니다.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    ScreenScaffold(
        title = "관찰 로그",
        subtitle = "인성데이터를 전면에 띄우면 최근 텍스트 트리 캡처가 이 화면에 쌓입니다. 필요하면 파일로도 저장할 수 있습니다.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ObservationSummaryCard(captures = captures)
            ObservationActionsCard(
                onBack = onBack,
                onExport = {
                    if (captures.isEmpty()) {
                        Toast.makeText(context, "아직 저장할 캡처 로그가 없습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingExportText = buildObservationExport(captures)
                        exportLauncher.launch(defaultObservationExportFileName())
                    }
                },
                onClear = {
                    scope.launch {
                        captureRepository.clearAll()
                    }
                },
            )
            ObservationListCard(captures = captures)
        }
    }
}

@Composable
private fun ObservationSummaryCard(
    captures: List<AccessibilityCaptureEntity>,
) {
    val latest = captures.firstOrNull()
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "캡처 요약",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${captures.size}개 캡처가 로컬에 저장됨",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = latest?.let {
                    "최근 캡처: ${it.packageName} · ${it.eventType.toObservationEventLabel()} · ${it.capturedAtMillis.toCaptureTimestamp()}"
                } ?: "아직 캡처가 없습니다. 먼저 접근성을 켜고 대상 앱을 열어 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ObservationActionsCard(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) {
                    Text("설정으로 돌아가기")
                }
                TextButton(onClick = onExport) {
                    Text("로그 파일 저장")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClear) {
                    Text("캡처 로그 지우기")
                }
            }
        }
    }
}

@Composable
private fun ObservationListCard(
    captures: List<AccessibilityCaptureEntity>,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "최근 캡처",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (captures.isEmpty()) {
                Text(
                    text = "아직 전경 앱 캡처가 없습니다. 접근성을 켠 뒤 대상 오더 화면을 열고 다시 돌아와 보세요.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                captures.forEach { capture ->
                    ObservationCaptureCard(capture = capture)
                }
            }
        }
    }
}

@Composable
private fun ObservationCaptureCard(
    capture: AccessibilityCaptureEntity,
) {
    var expanded by remember(capture.id) { mutableStateOf(false) }
    val parsedDraft = remember(capture.id, capture.rawHierarchy, capture.summaryText) {
        KoreanOrderDraftParser.parse(capture)
    }
    val rawPreview = remember(capture.rawHierarchy, expanded) {
        if (expanded) {
            capture.rawHierarchy
        } else {
            capture.rawHierarchy.lineSequence().take(14).joinToString("\n")
        }
    }

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
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = capture.screenTitle ?: capture.packageName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = capture.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = capture.eventType.toObservationEventLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = capture.summaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${capture.nodeCount}개 노드 · ${capture.capturedAtMillis.toCaptureTimestamp()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ParsedOrderDraftSection(parsedDraft = parsedDraft)
            SelectionContainer {
                Text(
                    text = rawPreview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "원본 계층 숨기기" else "원본 계층 보기")
            }
        }
    }
}

@Composable
private fun ParsedOrderDraftSection(
    parsedDraft: ParsedOrderDraft,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "파싱 결과",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!parsedDraft.hasAnySignal) {
                Text(
                    text = "아직 요금, 차종, 결제, 경로, 플래그를 신뢰할 만큼 추출하지 못했습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ParsedValueRow("상태", parsedDraft.statusText)
                ParsedValueRow("화면 모드", parsedDraft.screenMode)
                ParsedValueRow("요금", parsedDraft.price?.let { NumberFormat.getIntegerInstance().format(it) + "원" })
                ParsedValueRow("차종", parsedDraft.vehicleType)
                ParsedValueRow("결제", parsedDraft.paymentMode)
                ParsedValueRow("의뢰지", parsedDraft.requesterLocation)
                ParsedValueRow("출발지", parsedDraft.origin)
                ParsedValueRow("도착지", parsedDraft.destination)
                ParsedValueRow("경로", parsedDraft.routeText)
                ParsedValueRow("상차 거리", parsedDraft.currentToPickupDistanceKm?.let { "${it}km" })
                ParsedValueRow("상차-하차 거리", parsedDraft.pickupToDropoffDistanceKm?.let { "${it}km" })
                ParsedValueRow("적요상세", parsedDraft.detailNote)
                ParsedValueRow("확정 버튼", parsedDraft.confirmActionLabel)
                ParsedValueRow("취소 버튼", parsedDraft.cancelActionLabel)
                ParsedValueRow(
                    "플래그",
                    parsedDraft.flags.takeIf { it.isNotEmpty() }?.joinToString(),
                )
            }
        }
    }
}

@Composable
private fun ParsedValueRow(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun Long.toCaptureTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm:ss", Locale.getDefault())
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private suspend fun writeObservationExport(
    context: Context,
    uri: Uri,
    exportText: String,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(exportText)
        } ?: error("Unable to open output stream.")
    }.isSuccess
}

private fun buildObservationExport(
    captures: List<AccessibilityCaptureEntity>,
): String {
    val exportTime = System.currentTimeMillis().toExportTimestamp()
    return buildString {
        appendLine("CatchPro Observation Export")
        appendLine("exportedAt=$exportTime")
        appendLine("captureCount=${captures.size}")
        appendLine()

        captures.forEachIndexed { index, capture ->
            val parsedDraft = KoreanOrderDraftParser.parse(capture)
            appendLine("===== Capture ${index + 1} =====")
            appendLine("capturedAt=${capture.capturedAtMillis.toExportTimestamp()}")
            appendLine("package=${capture.packageName}")
            appendLine("event=${capture.eventType}")
            appendLine("screenTitle=${capture.screenTitle.orEmpty()}")
            appendLine("summary=${capture.summaryText}")
            appendLine("nodeCount=${capture.nodeCount}")
            appendLine("parsed.price=${KoreanOrderDraftParser.formatPrice(parsedDraft.price).orEmpty()}")
            appendLine("parsed.vehicle=${parsedDraft.vehicleType.orEmpty()}")
            appendLine("parsed.payment=${parsedDraft.paymentMode.orEmpty()}")
            appendLine("parsed.status=${parsedDraft.statusText.orEmpty()}")
            appendLine("parsed.screenMode=${parsedDraft.screenMode.orEmpty()}")
            appendLine("parsed.requester=${parsedDraft.requesterLocation.orEmpty()}")
            appendLine("parsed.origin=${parsedDraft.origin.orEmpty()}")
            appendLine("parsed.destination=${parsedDraft.destination.orEmpty()}")
            appendLine("parsed.route=${parsedDraft.routeText.orEmpty()}")
            appendLine("parsed.pickupDistanceKm=${parsedDraft.currentToPickupDistanceKm?.toString().orEmpty()}")
            appendLine("parsed.pickupToDropoffDistanceKm=${parsedDraft.pickupToDropoffDistanceKm?.toString().orEmpty()}")
            appendLine("parsed.detailNote=${parsedDraft.detailNote.orEmpty()}")
            appendLine("parsed.confirmAction=${parsedDraft.confirmActionLabel.orEmpty()}")
            appendLine("parsed.cancelAction=${parsedDraft.cancelActionLabel.orEmpty()}")
            appendLine("parsed.flags=${parsedDraft.flags.joinToString()}")
            appendLine("--- RAW HIERARCHY START ---")
            appendLine(capture.rawHierarchy)
            appendLine("--- RAW HIERARCHY END ---")
            appendLine()
        }
    }
}

private fun defaultObservationExportFileName(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.getDefault())
    val timestamp = Instant.now()
        .atZone(ZoneId.systemDefault())
        .format(formatter)
    return "catchpro-observation-$timestamp.txt"
}

private fun Long.toExportTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun String.toObservationEventLabel(): String = when (this) {
    "Content changed" -> "내용 변경"
    "Window changed" -> "창 변경"
    "List scrolled" -> "목록 스크롤"
    else -> this
}
