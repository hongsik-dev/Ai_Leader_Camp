package com.catchpro.app.observation

import android.view.accessibility.AccessibilityNodeInfo
import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import java.text.NumberFormat
import java.util.Locale

private val ActiveDriveStatusKeywords = listOf(
    "수행",
    "수행중",
    "운행",
    "운행중",
    "진행",
    "진행중",
    "배송",
    "배송중",
    "이동",
    "출발",
    "상차",
    "상차중",
    "하차",
    "하차중",
)

private val CancelledStatusKeywords = listOf(
    "취소",
    "배차취소",
    "취소됨",
    "취소완료",
)

data class ParsedOrderDraft(
    val price: Int? = null,
    val vehicleType: String? = null,
    val paymentMode: String? = null,
    val clientText: String? = null,
    val requesterLocation: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val routeText: String? = null,
    val currentToPickupDistanceKm: Double? = null,
    val pickupToDropoffDistanceKm: Double? = null,
    val detailNote: String? = null,
    val flags: List<String> = emptyList(),
    val statusText: String? = null,
    val confirmActionLabel: String? = null,
    val cancelActionLabel: String? = null,
    val screenMode: String? = null,
) {
    val hasAnySignal: Boolean
        get() = price != null ||
            vehicleType != null ||
            paymentMode != null ||
            clientText != null ||
            requesterLocation != null ||
            origin != null ||
            destination != null ||
            routeText != null ||
            currentToPickupDistanceKm != null ||
            pickupToDropoffDistanceKm != null ||
            detailNote != null ||
            flags.isNotEmpty() ||
            statusText != null ||
            confirmActionLabel != null ||
            cancelActionLabel != null ||
            screenMode != null

    val isDetailedScreen: Boolean
        get() = requesterLocation != null ||
            origin != null ||
            destination != null ||
            currentToPickupDistanceKm != null ||
            pickupToDropoffDistanceKm != null ||
            detailNote != null

    val isConfirmableDetailScreen: Boolean
        get() = isDetailedScreen && confirmActionLabel?.startsWith("확정") == true

    val isActiveDriveScreen: Boolean
        get() = destination != null &&
            statusText != null &&
            ActiveDriveStatusKeywords.any { keyword -> statusText.contains(keyword) } &&
            CancelledStatusKeywords.none { keyword -> statusText.contains(keyword) }

    val isCancelledStatusScreen: Boolean
        get() = statusText != null &&
            CancelledStatusKeywords.any { keyword -> statusText.contains(keyword) }
}

object KoreanOrderDraftParser {
    private const val InsungQuickPackage = "insung.split.quick"

    private val priceRegex = Regex("""(\d{1,3}(?:,\d{3})+|\d{4,})\s*원""")
    private val numericRegex = Regex("""(\d{1,3}(?:,\d{3})+|\d{1,6})""")
    private val routeArrowRegex = Regex(
        """([가-힣A-Za-z0-9 .()]{2,40})\s*(?:->|→|⇒|▶|~)\s*([가-힣A-Za-z0-9 .()]{2,40})""",
    )
    private val pageIndicatorRegex = Regex("""^\d+\s*/\s*\d+$""")
    private val maskedOnlyRegex = Regex("""^[*@.\s-]+$""")
    private val timeMarkerRegex = Regex("""^(오늘|내일|낼)?\s*\d{1,2}(?::\d{2})?\s*(시|분)?.*$""")
    private val locationHintRegex = Regex("""(시|군|구|동|읍|면|리|가|로|길|역|터미널|공항|IC)$""")
    private val locationNoiseRegex = Regex("""(도우미|상하차|하차도우미|상차도우미|기사|운반|작업자)""")
    private val administrativeAreaRegex = Regex("""(특별시|광역시|특별자치시|특별자치도|도|시|군|구)$""")
    private val abbreviatedMetropolitanAreaRegex = Regex(
        """(?:서울|부산|대구|인천|광주|대전|울산)\s*[가-힣A-Za-z0-9]+구""",
    )
    private val townAreaRegex = Regex("""(동|읍|면|리|가)$""")
    private val embeddedTownAreaRegex = Regex("""[가-힣A-Za-z0-9]+(동|읍|면|리|가)(?:\s|$)""")
    private val roadAddressDetailRegex = Regex("""[가-힣A-Za-z0-9]+(로|길)\s*\d+""")
    private val personNameRegex = Regex("""(님|고객|담당)$""")
    private val clientHeaderPhoneRegex = Regex("""(?:\d{2,4}\s*-\s*)?\d{3,4}\s*-\s*\d{4}""")
    private val distanceMarkerRegex = Regex("""\(?\s*\d+(?:\.\d+)?\s*km\s*\)?""", RegexOption.IGNORE_CASE)
    private val currentToPickupDistanceRegex = Regex(
        """현위치\s*[→\-]\s*상차지(?:\(직선\))?\s*([0-9]+(?:\.[0-9]+)?)\s*KM""",
        RegexOption.IGNORE_CASE,
    )
    private val pickupToDropoffDistanceRegex = Regex(
        """상차지\s*(?:→|->|⇒|▶|~|-)\s*하차지(?:\(직선\))?\s*([0-9]+(?:\.[0-9]+)?)\s*KM""",
        RegexOption.IGNORE_CASE,
    )

    private val vehiclePatterns = linkedMapOf(
        Regex("""1\.4\s*톤|1\.4\s*ton""", RegexOption.IGNORE_CASE) to "1.4톤",
        Regex("""2\.5\s*톤|2\.5\s*ton""", RegexOption.IGNORE_CASE) to "2.5톤",
        Regex("""3\.5\s*톤|3\.5\s*ton""", RegexOption.IGNORE_CASE) to "3.5톤",
        Regex("""5\s*톤|5\s*ton""", RegexOption.IGNORE_CASE) to "5톤",
        Regex("""1\s*톤|1\s*ton""", RegexOption.IGNORE_CASE) to "1톤",
        Regex("""다마스|damas""", RegexOption.IGNORE_CASE) to "다마스",
        Regex("""라보|labo""", RegexOption.IGNORE_CASE) to "라보",
        Regex("""오토바이|바이크|motorbike|bike""", RegexOption.IGNORE_CASE) to "오토바이",
    )

    private val paymentPatterns = linkedMapOf(
        Regex("""현금|cash""", RegexOption.IGNORE_CASE) to "현금",
        Regex("""카드|card""", RegexOption.IGNORE_CASE) to "카드",
        Regex("""착불""", RegexOption.IGNORE_CASE) to "착불",
        Regex("""선불""", RegexOption.IGNORE_CASE) to "선불",
        Regex("""혼합|mixed""", RegexOption.IGNORE_CASE) to "혼합",
    )

    private val flagPatterns = linkedMapOf(
        Regex("""편도""", RegexOption.IGNORE_CASE) to "편도",
        Regex("""왕복|복귀""", RegexOption.IGNORE_CASE) to "왕복",
        Regex("""독차""", RegexOption.IGNORE_CASE) to "독차",
        Regex("""혼적""", RegexOption.IGNORE_CASE) to "혼적",
        Regex("""사다리""", RegexOption.IGNORE_CASE) to "사다리",
        Regex("""계단""", RegexOption.IGNORE_CASE) to "계단",
        Regex("""엘리베이터 없음|엘베 없음|no elevator""", RegexOption.IGNORE_CASE) to "엘베 없음",
        Regex("""지게차""", RegexOption.IGNORE_CASE) to "지게차",
        Regex("""냉동""", RegexOption.IGNORE_CASE) to "냉동",
        Regex("""냉장""", RegexOption.IGNORE_CASE) to "냉장",
    )

    private val originLabels = listOf("상차지", "상차", "출발지", "출발", "픽업")
    private val destinationLabels = listOf("하차지", "하차", "도착지", "도착", "드랍", "배송지")
    private val sharedNoiseTokens = setOf(
        "거리",
        "차종",
        "요금",
        "원터치",
        "잠금",
        "메시지",
        "문자",
        "위치",
        "gps",
    )
    private val insungHeaderTokens = setOf(
        "원터치",
        "잠금",
        "거리",
        "출발지",
        "도착지",
        "차종",
        "요금",
    )
    private val genericButtonTokens = setOf(
        "픽업",
        "서명",
        "탁송",
        "취소",
        "완료",
        "적요상세",
    )

    fun supportsPackage(packageName: String): Boolean {
        return packageName.equals(InsungQuickPackage, ignoreCase = true) ||
            packageName.contains("insung", ignoreCase = true)
    }

    fun parse(capture: AccessibilityCaptureEntity): ParsedOrderDraft {
        val tokens = buildTokens(capture)
        val combinedText = tokens.joinToString(" | ")
        val rawText = buildString {
            appendLine(capture.screenTitle.orEmpty())
            appendLine(capture.summaryText)
            appendLine(capture.rawHierarchy)
            appendLine(combinedText)
        }

        val price = priceRegex.find(rawText)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toIntOrNull()

        val vehicleType = vehiclePatterns.entries
            .firstOrNull { it.key.containsMatchIn(rawText) }
            ?.value

        val paymentMode = paymentPatterns.entries
            .firstOrNull { it.key.containsMatchIn(rawText) }
            ?.value

        val labeledOrigin = findTokenAfterLabel(tokens, originLabels)
        val labeledDestination = findTokenAfterLabel(tokens, destinationLabels)
        val insungDraft = parseInsungQuickDraft(capture)

        val routeMatch = routeArrowRegex.find(rawText)
        val routeOrigin = routeMatch?.groupValues?.getOrNull(1)?.cleanRouteValue()
        val routeDestination = routeMatch?.groupValues?.getOrNull(2)?.cleanRouteValue()

        val origin = insungDraft.origin ?: labeledOrigin ?: routeOrigin
        val destination = insungDraft.destination ?: labeledDestination ?: routeDestination
        val routeText = when {
            origin != null && destination != null -> "$origin -> $destination"
            routeMatch != null -> routeMatch.value.cleanRouteValue()
            !insungDraft.routeText.isNullOrBlank() -> insungDraft.routeText
            else -> null
        }

        val flags = (
            insungDraft.flags +
                flagPatterns.entries
            .filter { it.key.containsMatchIn(rawText) }
            .map { it.value }
            )
            .distinct()

        return ParsedOrderDraft(
            price = insungDraft.price ?: price,
            vehicleType = insungDraft.vehicleType ?: vehicleType,
            paymentMode = insungDraft.paymentMode ?: paymentMode,
            clientText = insungDraft.clientText,
            requesterLocation = insungDraft.requesterLocation,
            origin = origin,
            destination = destination,
            routeText = routeText,
            currentToPickupDistanceKm = insungDraft.currentToPickupDistanceKm,
            pickupToDropoffDistanceKm = insungDraft.pickupToDropoffDistanceKm,
            detailNote = insungDraft.detailNote,
            flags = flags,
            statusText = insungDraft.statusText,
            confirmActionLabel = insungDraft.confirmActionLabel,
            cancelActionLabel = insungDraft.cancelActionLabel,
            screenMode = insungDraft.screenMode,
        )
    }

    fun formatPrice(price: Int?): String? {
        return price?.let { "${NumberFormat.getIntegerInstance(Locale.getDefault()).format(it)}원" }
    }

    fun parseInsungQuick(
        root: AccessibilityNodeInfo,
        packageName: String,
    ): ParsedOrderDraft {
        if (!supportsPackage(packageName)) {
            return ParsedOrderDraft()
        }

        val genericDraft = parseInsungGenericDetail(root)
        return buildInsungQuickDraft(
            moneyText = findNodeTextForAnyId(root, packageName, "q_tvMoney", "kor_tvMoney") ?: genericDraft.moneyText,
            sendMoneyText = findNodeTextForAnyId(root, packageName, "q_tvSendMoney", "kor_tvSendMoney"),
            statusText = findNodeTextForAnyId(root, packageName, "q_tvStatus", "kor_tvStatus") ?: genericDraft.statusText,
            clientText = findNodeTextForAnyId(root, packageName, "q_CustTitle", "kor_CustTitle")
                ?.takeIf { it.isClientHeaderCandidate() }
                ?: genericDraft.clientText,
            requesterText = findNodeTextForAnyId(root, packageName, "q_tvCustPosition", "kor_tvCustPosition")
                ?: genericDraft.requesterText,
            startText = findNodeTextForAnyId(root, packageName, "q_tvStart", "kor_tvStart") ?: genericDraft.startText,
            destText = findNodeTextForAnyId(root, packageName, "q_tvDest", "kor_tvDest") ?: genericDraft.destText,
            divisionText = findNodeTextForAnyId(root, packageName, "q_tvDivision", "kor_tvDivision")
                ?: genericDraft.divisionText,
            carText = findNodeTextForAnyId(root, packageName, "q_tvCar", "kor_tvCar") ?: genericDraft.carText,
            detailText = findNodeTextForAnyId(
                root,
                packageName,
                "q_etPositionDetail",
                "kor_etPositionDetail",
                "q_tvJukyo",
                "kor_tvJukyo",
            ) ?: genericDraft.detailText,
            confirmText = findNodeTextForAnyId(root, packageName, "q_btnClose", "kor_btnClose") ?: genericDraft.confirmText,
            cancelText = findNodeTextForAnyId(root, packageName, "q_btnCard", "kor_btnCard") ?: genericDraft.cancelText,
        )
    }

    private fun buildTokens(capture: AccessibilityCaptureEntity): List<String> {
        val tokenRegex = Regex("""(?:text|desc)="([^"]+)"""")
        val rawTokens = tokenRegex.findAll(capture.rawHierarchy)
            .map { it.groupValues[1].trim() }
            .filter { it.isMeaningfulToken(capture.packageName) }
            .toMutableList()

        capture.screenTitle
            ?.trim()
            ?.takeIf { it.isMeaningfulToken(capture.packageName) }
            ?.let(rawTokens::add)

        capture.summaryText
            .split('|')
            .map(String::trim)
            .filter { it.isMeaningfulToken(capture.packageName) }
            .forEach(rawTokens::add)

        return rawTokens.distinct()
    }

    private fun findTokenAfterLabel(
        tokens: List<String>,
        labels: List<String>,
    ): String? {
        tokens.forEachIndexed { index, token ->
            if (labels.any(token::contains)) {
                for (offset in 1..3) {
                    val candidate = tokens.getOrNull(index + offset)?.cleanRouteValue() ?: continue
                    if (
                        candidate.isProbablyLocationCandidate(packageName = null) &&
                        labels.none(candidate::contains)
                    ) {
                        return candidate
                    }
                }
            }
        }
        return null
    }

    private fun String.cleanRouteValue(): String =
        replace("\n", " ")
            .replace("\"", "")
            .trim()
            .trim('|')
            .take(160)

    private fun String.isProbablyLocationCandidate(packageName: String?): Boolean {
        if (length < 2) return false
        if (!isMeaningfulToken(packageName)) return false
        if (originLabels.any { it.equals(this, ignoreCase = true) }) return false
        if (destinationLabels.any { it.equals(this, ignoreCase = true) }) return false
        if (contains("원")) return false
        if (vehiclePatterns.keys.any { it.containsMatchIn(this) }) return false
        if (paymentPatterns.keys.any { it.containsMatchIn(this) }) return false
        if (contains("클릭") || contains("button", ignoreCase = true)) return false
        return true
    }

    private fun String.isMeaningfulToken(packageName: String?): Boolean {
        val normalized = cleanRouteValue()
        if (normalized.isBlank()) return false
        if (pageIndicatorRegex.matches(normalized)) return false
        if (normalized.length == 1 && !normalized[0].isLetterOrDigit()) return false

        val lower = normalized.lowercase()
        if (sharedNoiseTokens.contains(lower)) return false
        if (packageName == "insung.split.quick" && insungHeaderTokens.contains(normalized)) return false

        return true
    }

    private fun parseInsungQuickDraft(
        capture: AccessibilityCaptureEntity,
    ): ParsedOrderDraft {
        if (!supportsPackage(capture.packageName)) {
            return ParsedOrderDraft()
        }

        val rawHierarchy = capture.rawHierarchy
        return buildInsungQuickDraft(
            moneyText = findTextForAnyId(rawHierarchy, "q_tvMoney", "kor_tvMoney"),
            sendMoneyText = findTextForAnyId(rawHierarchy, "q_tvSendMoney", "kor_tvSendMoney"),
            statusText = findTextForAnyId(rawHierarchy, "q_tvStatus", "kor_tvStatus"),
            clientText = (
                findTextForAnyId(rawHierarchy, "q_CustTitle", "kor_CustTitle")
                    ?: capture.screenTitle
                )?.takeIf { it.isClientHeaderCandidate() },
            requesterText = findTextForAnyId(rawHierarchy, "q_tvCustPosition", "kor_tvCustPosition"),
            startText = findTextForAnyId(rawHierarchy, "q_tvStart", "kor_tvStart"),
            destText = findTextForAnyId(rawHierarchy, "q_tvDest", "kor_tvDest"),
            divisionText = findTextForAnyId(rawHierarchy, "q_tvDivision", "kor_tvDivision"),
            carText = findTextForAnyId(rawHierarchy, "q_tvCar", "kor_tvCar"),
            detailText = findTextForAnyId(
                rawHierarchy,
                "q_etPositionDetail",
                "kor_etPositionDetail",
                "q_tvJukyo",
                "kor_tvJukyo",
            ),
            confirmText = findTextForAnyId(rawHierarchy, "q_btnClose", "kor_btnClose"),
            cancelText = findTextForAnyId(rawHierarchy, "q_btnCard", "kor_btnCard"),
        )
    }

    private fun buildInsungQuickDraft(
        moneyText: String?,
        sendMoneyText: String?,
        statusText: String?,
        clientText: String?,
        requesterText: String?,
        startText: String?,
        destText: String?,
        divisionText: String?,
        carText: String?,
        detailText: String?,
        confirmText: String?,
        cancelText: String?,
    ): ParsedOrderDraft {
        val price = listOfNotNull(moneyText, sendMoneyText)
            .firstNotNullOfOrNull(::extractPriceFromInsungMoney)

        val payment = listOfNotNull(moneyText, sendMoneyText)
            .firstNotNullOfOrNull(::extractPaymentFromInsungMoney)

        val status = normalizeInsungStatus(statusText)
        val client = normalizeInsungClientText(clientText)
        val requesterLocation = normalizeInsungRequesterLocation(requesterText)
        val origin = normalizeInsungLocation(startText)
        val destination = normalizeInsungLocation(destText)
        val routeText = if (origin != null && destination != null) "$origin -> $destination" else null
        val currentToPickupDistanceKm = detailText
            ?.let(::extractCurrentToPickupDistanceKm)
        val pickupToDropoffDistanceKm = detailText
            ?.let(::extractPickupToDropoffDistanceKm)
        val confirmActionLabel = confirmText
            ?.cleanRouteValue()
            ?.takeIf { it.startsWith("확정") }
        val cancelActionLabel = cancelText?.cleanRouteValue()?.takeIf { it.isNotBlank() }

        val flags = buildList {
            divisionText
                ?.cleanRouteValue()
                ?.takeIf { it == "편도" || it == "왕복" }
                ?.let(::add)
        }

        val screenMode = when {
            status?.let(::isCancelledStatusText) == true -> "cancelled"
            status?.let(::isActiveDriveStatusText) == true -> "active-drive"
            confirmActionLabel?.startsWith("확정") == true -> "confirm-detail"
            requesterLocation != null || origin != null || destination != null -> "detail"
            else -> null
        }

        return ParsedOrderDraft(
            price = price,
            vehicleType = normalizeInsungVehicle(carText),
            paymentMode = payment,
            clientText = client,
            requesterLocation = requesterLocation,
            origin = origin,
            destination = destination,
            routeText = routeText,
            currentToPickupDistanceKm = currentToPickupDistanceKm,
            pickupToDropoffDistanceKm = pickupToDropoffDistanceKm,
            detailNote = detailText?.cleanRouteValue(),
            flags = flags,
            statusText = status,
            confirmActionLabel = confirmActionLabel,
            cancelActionLabel = cancelActionLabel,
            screenMode = screenMode,
        )
    }

    private fun findTextForAnyId(
        rawHierarchy: String,
        vararg idSuffixes: String,
    ): String? {
        return idSuffixes.firstNotNullOfOrNull { idSuffix ->
            val pattern = Regex("""text="([^"]*)"[^\\n]*id="[^"]*${Regex.escape(idSuffix)}"""")
            pattern.find(rawHierarchy)
                ?.groupValues
                ?.getOrNull(1)
                ?.cleanRouteValue()
                ?.takeIf(String::isNotBlank)
        }
    }

    private fun findNodeTextForAnyId(
        root: AccessibilityNodeInfo,
        packageName: String,
        vararg idSuffixes: String,
    ): String? {
        idSuffixes.firstNotNullOfOrNull { idSuffix ->
            val exactViewId = "$packageName:id/$idSuffix"
            root.findAccessibilityNodeInfosByViewId(exactViewId)
                .firstNotNullOfOrNull { node ->
                    node.readNodeText()
                        ?.cleanRouteValue()
                        ?.takeIf(String::isNotBlank)
                }
        }?.let { return it }

        return findNode(root) { node ->
            val viewId = node.viewIdResourceName.orEmpty()
            idSuffixes.any { idSuffix ->
                viewId.endsWith("/$idSuffix") || viewId.endsWith(":id/$idSuffix")
            }
        }?.readNodeText()
            ?.cleanRouteValue()
            ?.takeIf(String::isNotBlank)
    }

    private fun parseInsungGenericDetail(root: AccessibilityNodeInfo): GenericDetailTexts {
        val tokens = collectVisibleNodeTexts(root)
        val isPickupAddressDetailScreen = tokens.any { it.isPickupAddressDetailTitle() }
        return GenericDetailTexts(
            moneyText = tokens.firstOrNull { priceRegex.containsMatchIn(it) },
            statusText = findSimpleValueAfterLabel(tokens, "상태"),
            clientText = tokens.firstOrNull { it.isClientHeaderCandidate() },
            requesterText = findLocationValueAfterLabel(tokens, listOf("의뢰지")),
            startText = findLocationValueAfterLabel(tokens, originLabels),
            destText = findLocationValueAfterLabel(tokens, destinationLabels)
                ?: findStandaloneDetailedLocation(tokens)
                    ?.takeUnless { isPickupAddressDetailScreen },
            divisionText = findSimpleValueAfterLabel(tokens, "구분"),
            carText = findSimpleValueAfterLabel(tokens, "차량", "차종"),
            detailText = findDetailValueAfterLabel(tokens, "적요상세")
                ?: findDetailedLocationAfterLabel(tokens, "위치")
                ?: findStandaloneDetailedLocation(tokens),
            confirmText = tokens.firstOrNull { it.startsWith("확정") },
            cancelText = tokens.firstOrNull { it == "취소" || it.contains("취소") },
        )
    }

    private fun collectVisibleNodeTexts(root: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.readNodeText()
                ?.cleanRouteValue()
                ?.takeIf { it.isNotBlank() }
                ?.let(result::add)
            repeat(node.childCount) { index ->
                walk(node.getChild(index))
            }
        }

        walk(root)
        return result.distinct()
    }

    private fun findSimpleValueAfterLabel(
        tokens: List<String>,
        vararg labels: String,
    ): String? {
        tokens.forEachIndexed { index, token ->
            if (labels.any { label -> token.contains(label) }) {
                token.extractInlineValueAfterLabel(labels.toList())
                    ?.takeIf { it.isGenericValueCandidate() }
                    ?.let { return it }
                for (offset in 1..4) {
                    val candidate = tokens.getOrNull(index + offset)
                        ?.cleanRouteValue()
                        ?.takeIf { it.isGenericValueCandidate() }
                        ?: continue
                    return candidate
                }
            }
        }
        return null
    }

    private fun findLocationValueAfterLabel(
        tokens: List<String>,
        labels: List<String>,
    ): String? {
        tokens.forEachIndexed { index, token ->
            if (labels.any { label -> token.contains(label) }) {
                token.extractInlineValueAfterLabel(labels)
                    ?.takeIf { it.isGenericLocationCandidate() }
                    ?.let { return it }
                for (offset in 1..6) {
                    val candidate = tokens.getOrNull(index + offset)
                        ?.cleanRouteValue()
                        ?.takeIf { it.isGenericLocationCandidate() }
                        ?: continue
                    return candidate
                }
            }
        }
        return null
    }

    private fun findDetailValueAfterLabel(
        tokens: List<String>,
        label: String,
    ): String? {
        tokens.forEachIndexed { index, token ->
            if (token.contains(label)) {
                token.extractInlineValueAfterLabel(listOf(label))
                    ?.takeIf { it.isGenericValueCandidate() }
                    ?.let { return it }
                for (offset in 1..3) {
                    val candidate = tokens.getOrNull(index + offset)
                        ?.cleanRouteValue()
                        ?.takeIf { it.isGenericValueCandidate() }
                        ?: continue
                    return candidate
                }
            }
        }
        return null
    }

    private fun findDetailedLocationAfterLabel(
        tokens: List<String>,
        label: String,
    ): String? {
        tokens.forEachIndexed { index, token ->
            if (token.contains(label)) {
                token.extractInlineValueAfterLabel(listOf(label))
                    ?.let(::normalizeInsungLocation)
                    ?.takeIf { it.isDetailedLocationCandidate() }
                    ?.let { return it }

                val nearbyValues = (1..8)
                    .mapNotNull { offset -> tokens.getOrNull(index + offset)?.cleanRouteValue() }
                    .filter { it.isGenericValueCandidate() }
                nearbyValues
                    .asSequence()
                    .mapNotNull(::normalizeInsungLocation)
                    .firstOrNull { it.isDetailedLocationCandidate() }
                    ?.let { return it }

                nearbyValues
                    .joinToString(" ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                    .let(::normalizeInsungLocation)
                    ?.takeIf { it.isDetailedLocationCandidate() }
                    ?.let { return it }
            }
        }
        return null
    }

    private fun String.extractInlineValueAfterLabel(labels: List<String>): String? {
        val matchedLabel = labels.firstOrNull { contains(it) } ?: return null
        return substringAfter(matchedLabel, missingDelimiterValue = "")
            .trim()
            .trim(':', '：', '-', ' ', '\t')
            .takeIf { it.isNotBlank() }
            ?.cleanRouteValue()
    }

    private fun String.isGenericValueCandidate(): Boolean {
        if (isBlank()) return false
        if (this in genericButtonTokens) return false
        if (startsWith("확정")) return false
        if (originLabels.any { it == this } || destinationLabels.any { it == this }) return false
        if (contains(":") && length <= 4) return false
        return true
    }

    private fun String.isGenericLocationCandidate(): Boolean {
        if (!isGenericValueCandidate()) return false
        if (paymentPatterns.keys.any { it.containsMatchIn(this) }) return false
        if (vehiclePatterns.keys.any { it.containsMatchIn(this) }) return false
        if (priceRegex.containsMatchIn(this)) return false
        return normalizeInsungLocation(this) != null
    }

    private fun findStandaloneDetailedLocation(tokens: List<String>): String? {
        return tokens
            .asSequence()
            .mapNotNull(::normalizeInsungLocation)
            .firstOrNull { candidate ->
                candidate.isDetailedLocationCandidate()
            }
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        repeat(node.childCount) { index ->
            val child = node.getChild(index) ?: return@repeat
            val match = findNode(child, predicate)
            if (match != null) return match
        }
        return null
    }

    private fun AccessibilityNodeInfo.readNodeText(): String? {
        return text?.toString()
            ?: contentDescription?.toString()
    }

    private fun extractPriceFromInsungMoney(value: String): Int? {
        return numericRegex.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toIntOrNull()
    }

    private fun extractPaymentFromInsungMoney(value: String): String? {
        return when {
            value.contains("신용") || value.contains("카드") -> "카드"
            value.contains("현금") -> "현금"
            value.contains("착불") -> "착불"
            value.contains("선불") -> "선불"
            else -> null
        }
    }

    private fun extractCurrentToPickupDistanceKm(value: String): Double? {
        return currentToPickupDistanceRegex.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
    }

    private fun extractPickupToDropoffDistanceKm(value: String): Double? {
        return pickupToDropoffDistanceRegex.find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
    }

    private fun normalizeInsungVehicle(value: String?): String? {
        val normalized = value?.cleanRouteValue().orEmpty()
        if (normalized.length < 2) return null

        return vehiclePatterns.entries
            .firstOrNull { it.key.containsMatchIn(normalized) }
            ?.value
            ?: normalized.takeIf { it.isMeaningfulToken(packageName = "insung.split.quick") }
    }

    private fun normalizeInsungRequesterLocation(value: String?): String? {
        val normalized = value?.cleanRouteValue().orEmpty()
        if (normalized.isBlank()) return null
        if (maskedOnlyRegex.matches(normalized)) return "비공개"
        return normalized
    }

    private fun normalizeInsungStatus(value: String?): String? {
        return value?.cleanRouteValue()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeInsungClientText(value: String?): String? {
        return value
            ?.cleanRouteValue()
            ?.takeIf { it.isClientHeaderCandidate() }
    }

    private fun normalizeInsungLocation(value: String?): String? {
        val normalized = value?.cleanRouteValue().orEmpty()
        if (normalized.isBlank()) return null

        val candidates = normalized
            .split('/')
            .map { segment ->
                segment
                    .replace("@", "")
                    .replace(distanceMarkerRegex, "")
                    .trim()
                    .trim('*')
                    .replace("**", "")
                    .trim()
            }
            .filter { candidate ->
                candidate.isUsableLocationSegment()
            }

        val joinedCandidate = candidates
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { candidate ->
                candidate.length >= 4 &&
                    isAdministrativeAreaCandidate(candidate) &&
                    embeddedTownAreaRegex.containsMatchIn(candidate)
            }
        if (joinedCandidate != null) return joinedCandidate

        val locationCandidates = candidates
            .filter { candidate ->
                locationHintRegex.containsMatchIn(candidate) ||
                    candidate.contains(' ') ||
                    candidate.contains("상차") ||
                    candidate.contains("하차")
            }

        combineAdministrativeLocationSegments(candidates)?.let { return it }

        val bestCandidate = locationCandidates.maxByOrNull(::scoreInsungLocationCandidate)
            ?: candidates.maxByOrNull(::scoreInsungLocationCandidate)

        return bestCandidate
            ?.takeIf { scoreInsungLocationCandidate(it) > 10 }
    }

    private fun combineAdministrativeLocationSegments(candidates: List<String>): String? {
        val districtIndex = candidates.indexOfFirst(::isAdministrativeAreaCandidate)
        if (districtIndex < 0) return null

        val district = candidates[districtIndex]
        val townIndex = candidates
            .drop(districtIndex + 1)
            .indexOfFirst(::isTownAreaCandidate)
            .takeIf { it >= 0 }
            ?.let { districtIndex + 1 + it }
            ?: return null
        val town = candidates[townIndex]

        val baseAddress = if (district.contains(town) || town.contains(district)) {
            district
        } else {
            "$district $town"
        }
        val detailSegments = candidates
            .drop(townIndex + 1)
            .filter(::isAddressDetailCandidate)
            .take(2)
        return (listOf(baseAddress) + detailSegments)
            .joinToString(" ")
            .trim()
    }

    private fun isAdministrativeAreaCandidate(value: String): Boolean {
        if (value.length < 2) return false
        if (personNameRegex.containsMatchIn(value)) return false
        return administrativeAreaRegex.containsMatchIn(value) ||
            abbreviatedMetropolitanAreaRegex.containsMatchIn(value) ||
            value.contains("특별시") ||
            value.contains("광역시") ||
            value.contains("시") && value.contains("구")
    }

    private fun isTownAreaCandidate(value: String): Boolean {
        if (value.length < 2) return false
        if (personNameRegex.containsMatchIn(value)) return false
        if (locationNoiseRegex.containsMatchIn(value)) return false
        return townAreaRegex.containsMatchIn(value)
    }

    private fun isAddressDetailCandidate(value: String): Boolean {
        if (!value.isUsableLocationSegment()) return false
        if (personNameRegex.containsMatchIn(value)) return false
        if (priceRegex.containsMatchIn(value)) return false
        if (paymentPatterns.keys.any { it.containsMatchIn(value) }) return false
        if (vehiclePatterns.keys.any { it.containsMatchIn(value) }) return false
        if (flagPatterns.keys.any { it.containsMatchIn(value) }) return false
        return true
    }

    private fun String.isUsableLocationSegment(): Boolean {
        if (length < 2) return false
        if (maskedOnlyRegex.matches(this)) return false
        if (timeMarkerRegex.matches(this)) return false
        if (pageIndicatorRegex.matches(this)) return false
        if (locationNoiseRegex.containsMatchIn(this)) return false
        if (distanceMarkerRegex.containsMatchIn(this)) return false
        if (sharedNoiseTokens.contains(lowercase())) return false
        if (this in genericButtonTokens) return false
        if (originLabels.any { it == this } || destinationLabels.any { it == this }) return false
        return true
    }

    private fun String.isClientHeaderCandidate(): Boolean {
        val normalized = cleanRouteValue()
        if (normalized.isBlank()) return false
        if (normalized.contains("상세")) return false
        if (normalized.contains("상태")) return false
        return clientHeaderPhoneRegex.containsMatchIn(normalized)
    }

    private fun String.isPickupAddressDetailTitle(): Boolean {
        val normalized = cleanRouteValue().replace(Regex("""\s+"""), "")
        return normalized.contains("출발지상세") ||
            normalized.contains("상차지상세") ||
            normalized.contains("출발상세") ||
            normalized.contains("상차상세")
    }

    private fun String.isDetailedLocationCandidate(): Boolean {
        val normalized = replace(Regex("""\s+"""), " ").trim()
        if (normalized.length < 8) return false
        if (!isAdministrativeAreaCandidate(normalized)) return false
        if (roadAddressDetailRegex.containsMatchIn(normalized)) return true

        val words = normalized.split(Regex("""\s+""")).filter(String::isNotBlank)
        return words.indices.any { index ->
            isTownAreaCandidate(words[index]) &&
                words.drop(index + 1).any { detail ->
                    detail.length >= 2 &&
                        !isAdministrativeAreaCandidate(detail) &&
                        !isTownAreaCandidate(detail) &&
                        !locationNoiseRegex.containsMatchIn(detail)
                }
        }
    }

    private fun scoreInsungLocationCandidate(value: String): Int {
        var score = value.length
        if (locationHintRegex.containsMatchIn(value)) score += 20
        if (value.contains(' ')) score += 5
        if (value.contains("상차") || value.contains("하차")) score += 3
        if (locationNoiseRegex.containsMatchIn(value)) score -= 50
        if (value.contains("박스") || value.contains("첫차")) score -= 20
        return score
    }

    private fun isActiveDriveStatusText(value: String): Boolean {
        return ActiveDriveStatusKeywords.any { keyword -> value.contains(keyword) } &&
            CancelledStatusKeywords.none { keyword -> value.contains(keyword) } &&
            value.contains("배차").not()
    }

    private fun isCancelledStatusText(value: String): Boolean {
        return CancelledStatusKeywords.any { keyword -> value.contains(keyword) }
    }

    private data class GenericDetailTexts(
        val moneyText: String? = null,
        val statusText: String? = null,
        val clientText: String? = null,
        val requesterText: String? = null,
        val startText: String? = null,
        val destText: String? = null,
        val divisionText: String? = null,
        val carText: String? = null,
        val detailText: String? = null,
        val confirmText: String? = null,
        val cancelText: String? = null,
    )
}
