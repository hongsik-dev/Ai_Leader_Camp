package com.catchpro.app.observation

import com.catchpro.app.data.model.AppSettings
import com.catchpro.app.data.region.KoreaAdministrativeAreas
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Locale

data class AutoConfirmDecision(
    val shouldConfirm: Boolean,
    val reasons: List<String> = emptyList(),
    val manualInputRequired: Boolean = false,
    val manualReviewRequired: Boolean = false,
)

object AutoConfirmEvaluator {
    private const val TrackingPickupRouteDetourLimitKm = 4.0
    private const val TrackingPickupToDropoffLimitKm = 8.0

    fun evaluatePrimary(
        settings: AppSettings,
        draft: ParsedOrderDraft,
        pickupRoadDistanceKm: Double? = null,
        pickupRoadDistanceFailureReason: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): AutoConfirmDecision {
        if (!settings.primaryAutoConfirmEnabled) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("1차 자동확정이 꺼져 있습니다."),
            )
        }
        if (!draft.isDetailedScreen) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("1차 상세화면으로 판단되지 않았습니다."),
            )
        }

        val destinationKeywords = primaryDestinationKeywords(settings)
        val minimumPrice = settings.primaryMinimumPriceText
            .replace(",", "")
            .trim()
            .toIntOrNull()

        if (minimumPrice == null) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("1차 요금 조건이 설정되지 않았습니다."),
            )
        }

        val reasons = mutableListOf<String>()
        if (destinationKeywords.isEmpty()) {
            reasons += "1차 도착지 조건 미설정: 모든 도착지 허용"
        } else {
            val destinationHaystacks = listOfNotNull(
                draft.effectiveDestination(),
            )

            val matchedKeyword = destinationKeywords.firstOrNull { keyword ->
                destinationHaystacks.any { haystack ->
                    KoreaAdministrativeAreas.matchesKeyword(keyword, haystack)
                }
            } ?: return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("1차 도착지 조건 불일치: ${destinationKeywords.joinToString(", ")}"),
            )
            reasons += "1차 도착지 매치: $matchedKeyword"
        }

        val price = draft.price ?: return AutoConfirmDecision(
            shouldConfirm = false,
            reasons = reasons + "1차 요금 인식 실패",
        )
        if (price < minimumPrice) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = reasons + "1차 요금 ${price.formatPrice()} < ${minimumPrice.formatPrice()}",
            )
        }
        reasons += "1차 요금 ${price.formatPrice()} >= ${minimumPrice.formatPrice()}"

        return AutoConfirmDecision(
            shouldConfirm = true,
            reasons = reasons,
        )
    }

    fun evaluateSecondary(
        settings: AppSettings,
        draft: ParsedOrderDraft,
        destinationRadiusDistanceKm: Double? = null,
        pickupRoadDistanceKm: Double? = null,
        pickupRoadDistanceFailureReason: String? = null,
        destinationRadiusDistanceFailureReason: String? = null,
        pickupDistanceLabel: String = "주행거리",
        destinationRadiusDistanceLabel: String = "주행반경",
        hasReferenceDestination: Boolean = false,
        now: LocalDateTime = LocalDateTime.now(),
    ): AutoConfirmDecision {
        draft.blacklistedClientReason(settings)?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }
        draft.zeroPickupToDropoffDistanceReason()?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }
        draft.autoConfirmExcludedKeywordReason(
            keywords = settings.trackingAutoConfirmExcludedKeywords(),
            stageLabel = "오더추적",
        )?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }
        if (!settings.secondaryAutoConfirmEnabled) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("2차 자동확정이 꺼져 있습니다."),
            )
        }
        if (!draft.isDetailedScreen) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("2차 상세화면으로 판단되지 않았습니다."),
            )
        }
        draft.scheduledOrderExclusionReason(now)?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }

        val maximumPickupDistanceKm = settings.secondaryMaximumPickupDistanceKmText
            .trim()
            .toDoubleOrNull()
        val destinationRadiusKm = settings.secondaryDestinationRadiusKmText
            .trim()
            .toDoubleOrNull()

        if (maximumPickupDistanceKm == null && destinationRadiusKm == null) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("2차 상차거리/목적지 반경 조건이 설정되지 않았습니다."),
            )
        }

        val reasons = mutableListOf<String>()
        val manualInputReasons = mutableListOf<String>()

        if (destinationRadiusKm != null) {
            if (!hasReferenceDestination) {
                return AutoConfirmDecision(
                    shouldConfirm = false,
                    reasons = listOf("2차 메인오더 목적지가 비어 있습니다."),
                )
            }
            val radiusDistance = destinationRadiusDistanceKm
            if (radiusDistance == null) {
                manualInputReasons += destinationRadiusDistanceFailureReason
                    ?: "메인 목적지 반경 조건은 요약주소 직선거리 추정 결과가 필요합니다."
            } else {
                if (radiusDistance > destinationRadiusKm) {
                    return AutoConfirmDecision(
                        shouldConfirm = false,
                        reasons = reasons + "메인 목적지 $destinationRadiusDistanceLabel ${radiusDistance.formatDistanceKm()}km > ${destinationRadiusKm.formatDistanceKm()}km",
                    )
                }
                reasons += "메인 목적지 $destinationRadiusDistanceLabel ${radiusDistance.formatDistanceKm()}km <= ${destinationRadiusKm.formatDistanceKm()}km"
            }
        }

        if (maximumPickupDistanceKm != null) {
            val pickupDistance = pickupRoadDistanceKm
            if (pickupDistance == null) {
                manualInputReasons += pickupRoadDistanceFailureReason
                    ?: "추가 상차거리 조건은 상세 화면의 현위치→상차지 직선거리 추정 결과가 필요합니다."
            } else {
                if (pickupDistance > maximumPickupDistanceKm) {
                    return AutoConfirmDecision(
                        shouldConfirm = false,
                        reasons = reasons + "추가 상차 $pickupDistanceLabel ${pickupDistance.formatDistanceKm()}km > ${maximumPickupDistanceKm.formatDistanceKm()}km",
                    )
                }
                reasons += "추가 상차 $pickupDistanceLabel ${pickupDistance.formatDistanceKm()}km <= ${maximumPickupDistanceKm.formatDistanceKm()}km"
            }
        }

        if (manualInputReasons.isNotEmpty()) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = reasons + manualInputReasons,
                manualInputRequired = true,
            )
        }

        draft.specialManualReviewReason()?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = true,
                reasons = reasons + reason,
                manualReviewRequired = true,
            )
        }

        return AutoConfirmDecision(
            shouldConfirm = true,
            reasons = reasons,
        )
    }

    fun evaluateTrackedAdditional(
        settings: AppSettings,
        draft: ParsedOrderDraft,
        pickupRoadDistanceKm: Double? = null,
        pickupRoadDistanceFailureReason: String? = null,
        pickupDistanceLabel: String = "추정거리",
        destinationMatchReason: String? = null,
        destinationMatchFailureReason: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): AutoConfirmDecision {
        draft.blacklistedClientReason(settings)?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }
        draft.zeroPickupToDropoffDistanceReason()?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }
        draft.autoConfirmExcludedKeywordReason(
            keywords = settings.trackingAutoConfirmExcludedKeywords(),
            stageLabel = "오더추적",
        )?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }
        if (!settings.orderTrackingModeEnabled) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("오더 추적 모드가 꺼져 있습니다."),
            )
        }
        if (!draft.isDetailedScreen) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("추적 오더 상세화면으로 판단되지 않았습니다."),
            )
        }
        if (settings.activeDriveDestinationText.trim().isBlank()) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf("기준 오더 도착지 상세주소가 저장되지 않았습니다."),
                manualInputRequired = true,
            )
        }
        draft.scheduledOrderExclusionReason(now)?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(reason),
            )
        }

        val pickupDistance = pickupRoadDistanceKm
        if (pickupDistance == null) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(
                    pickupRoadDistanceFailureReason
                        ?: "추적 경로상 상차 우회거리 계산 결과가 필요합니다.",
                ),
            )
        }
        if (pickupDistance > TrackingPickupRouteDetourLimitKm) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(
                    "추적 경로상 상차 $pickupDistanceLabel ${pickupDistance.formatDistanceKm()}km > ${TrackingPickupRouteDetourLimitKm.formatDistanceKm()}km",
                ),
            )
        }

        val reasons = mutableListOf<String>()
        reasons += "추적 경로상 상차 $pickupDistanceLabel ${pickupDistance.formatDistanceKm()}km <= ${TrackingPickupRouteDetourLimitKm.formatDistanceKm()}km"

        val routeDistanceKm = draft.pickupToDropoffDistanceKm
        if (routeDistanceKm == null) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = reasons + "추적 상차→하차 거리 조건은 적요상세의 상차지→하차지 직선거리 값이 필요합니다.",
            )
        }
        if (routeDistanceKm > TrackingPickupToDropoffLimitKm) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = reasons + "추적 상차→하차 직선거리 ${routeDistanceKm.formatDistanceKm()}km > ${TrackingPickupToDropoffLimitKm.formatDistanceKm()}km",
            )
        }
        reasons += "추적 상차→하차 직선거리 ${routeDistanceKm.formatDistanceKm()}km <= ${TrackingPickupToDropoffLimitKm.formatDistanceKm()}km"

        val matchReason = destinationMatchReason
        if (matchReason.isNullOrBlank()) {
            return AutoConfirmDecision(
                shouldConfirm = false,
                reasons = reasons + (destinationMatchFailureReason
                    ?: "추적 하차지 조건 불일치: 기준 상세주소와 후보 도착지의 서울 구 / 경기 동·리·읍·면 / 검증 권역이 일치하지 않습니다."),
            )
        }
        reasons += "추적 하차지 조건 통과: $matchReason"

        draft.specialManualReviewReason()?.let { reason ->
            return AutoConfirmDecision(
                shouldConfirm = true,
                reasons = reasons + reason,
                manualReviewRequired = true,
            )
        }

        return AutoConfirmDecision(
            shouldConfirm = true,
            reasons = reasons,
        )
    }

    private fun ParsedOrderDraft.primaryLongDistanceRuleOutcome(settings: AppSettings): LongDistanceRuleOutcome? {
        if (!settings.primaryLongDistanceRuleEnabled) return null
        val thresholdKm = settings.primaryLongDistanceThresholdKmText
            .trim()
            .toDoubleOrNull()
            ?: return null
        val minimumPrice = settings.primaryLongDistanceMinimumPriceText
            .replace(",", "")
            .trim()
            .toIntOrNull()
            ?: return null
        val distanceKm = pickupToDropoffDistanceKm ?: return null
        if (distanceKm <= thresholdKm) return null

        val currentPrice = price
        return if (currentPrice != null && currentPrice >= minimumPrice) {
            LongDistanceRuleOutcome(
                passed = true,
                reason = "1차 장거리 조건 통과: 상차→하차 직선 ${distanceKm.formatDistanceKm()}km > ${thresholdKm.formatDistanceKm()}km, 요금 ${currentPrice.formatPrice()} >= ${minimumPrice.formatPrice()}",
            )
        } else {
            LongDistanceRuleOutcome(
                passed = false,
                reason = "1차 장거리 조건 제외: 상차→하차 직선 ${distanceKm.formatDistanceKm()}km > ${thresholdKm.formatDistanceKm()}km, 요금 ${currentPrice?.formatPrice() ?: "미확인"} < ${minimumPrice.formatPrice()}",
            )
        }
    }

    private data class LongDistanceRuleOutcome(
        val passed: Boolean,
        val reason: String,
    )

    fun needsRoadDistanceEvaluation(
        settings: AppSettings,
        draft: ParsedOrderDraft,
        isSecondary: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        // 기준오더 확정 판단은 도착지와 요금만 사용합니다.
        if (!isSecondary) return false

        if (draft.blacklistedClientReason(settings) != null) return false
        if (draft.zeroPickupToDropoffDistanceReason() != null) return false
        if (draft.scheduledOrderExclusionReason(now) != null) return false
        val excludedKeywords = if (isSecondary) {
            settings.trackingAutoConfirmExcludedKeywords()
        } else {
            settings.primaryAutoConfirmExcludedKeywords()
        }
        if (draft.autoConfirmExcludedKeywordReason(excludedKeywords, stageLabel = null) != null) return false
        if (isSecondary) {
            if (settings.orderTrackingModeEnabled && settings.activeDriveDestinationText.trim().isNotBlank()) {
                if (!draft.isDetailedScreen) return false
                return true
            }
            if (!settings.secondaryAutoConfirmEnabled || !draft.isDetailedScreen) {
                return false
            }
            val needsPickupRoadDistance =
                settings.secondaryMaximumPickupDistanceKmText.trim().toDoubleOrNull() != null
            return needsPickupRoadDistance ||
                settings.secondaryDestinationRadiusKmText.trim().toDoubleOrNull() != null
        }
        return false
    }

    fun scheduledOrderExclusionReason(
        text: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): String? {
        if (text.isNullOrBlank()) return null
        return ScheduledOrderTimeRegex.findAll(text)
            .mapNotNull { match -> match.toScheduledDateTime(now)?.let { match.value.trim() to it } }
            .firstNotNullOfOrNull { (rawText, scheduledAt) ->
                val minutes = Duration.between(now, scheduledAt).toMinutes()
                if (minutes >= ScheduledOrderExcludeMinutes) {
                    "시간예약 제외: $rawText, 현재보다 ${minutes.toFutureDurationText()} 뒤"
                } else {
                    null
                }
            }
    }

    private fun ParsedOrderDraft.pickupDistanceForEvaluation(roadDistanceKm: Double?): PickupDistance? {
        currentToPickupDistanceKm?.let { return PickupDistance(km = it, label = "직선거리") }
        roadDistanceKm?.let { return PickupDistance(km = it, label = "주행거리") }
        return null
    }

    private data class PickupDistance(
        val km: Double,
        val label: String,
    )

    private fun Int.formatPrice(): String = "%,d".format(Locale.getDefault(), this)

    private fun primaryDestinationKeywords(settings: AppSettings): List<String> {
        return settings.primaryDestinationKeywords
            .split(',', '\n')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun ParsedOrderDraft.blacklistedClientReason(settings: AppSettings): String? {
        val clientKey = clientText.normalizeClientKey()
        if (clientKey.isBlank()) return null
        val matchedEntry = settings.clientBlacklistText
            .blacklistEntries()
            .firstOrNull { entry ->
                val entryKey = entry.normalizeClientKey()
                entryKey.isNotBlank() && clientKey.contains(entryKey)
            }
            ?: return null
        return "거래처 블랙리스트: $matchedEntry"
    }

    private fun ParsedOrderDraft.autoConfirmExcludedKeywordReason(
        keywords: List<String>,
        stageLabel: String?,
    ): String? {
        val text = decisionText().normalizeAutoConfirmKeywordText()
        if (text.isBlank()) return null
        val matched = keywords.firstOrNull { keyword ->
            text.contains(keyword)
        } ?: return null
        val prefix = stageLabel?.let { "$it " }.orEmpty()
        return "${prefix}자동확정 제외 키워드: $matched"
    }

    private fun ParsedOrderDraft.zeroPickupToDropoffDistanceReason(): String? {
        val distanceKm = pickupToDropoffDistanceKm ?: return null
        return if (distanceKm <= 0.0) {
            "하차지거리 0.0km 제외: 상차→하차 직선거리 ${distanceKm.formatDistanceKm()}km"
        } else {
            null
        }
    }

    private fun ParsedOrderDraft.scheduledOrderExclusionReason(now: LocalDateTime): String? {
        return scheduledOrderExclusionReason(decisionText(), now)
    }

    private fun ParsedOrderDraft.specialManualReviewReason(): String? {
        val text = decisionText().normalizeSpecialOrderText()
        if (text.isBlank()) return null
        val matched = SpecialManualReviewKeywords.firstOrNull { keyword ->
            keyword.keys.any(text::contains)
        } ?: return null
        return "특수오더 수동확인: ${matched.label}"
    }

    private fun ParsedOrderDraft.decisionText(): String {
        return listOfNotNull(
            clientText,
            requesterLocation,
            origin,
            destination,
            routeText,
            detailNote,
            flags.joinToString(" ").takeIf { it.isNotBlank() },
            statusText,
        )
            .joinToString(" ")
    }

    private fun String.blacklistEntries(): List<String> {
        return split('\n', ',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun String?.normalizeClientKey(): String {
        return orEmpty()
            .lowercase(Locale.KOREAN)
            .replace(Regex("""[\s\-_:：/().]+"""), "")
            .trim()
    }

    private fun AppSettings.primaryAutoConfirmExcludedKeywords(): List<String> =
        primaryAutoConfirmExcludedKeywordsText.keywordEntriesForAutoConfirm()

    private fun AppSettings.trackingAutoConfirmExcludedKeywords(): List<String> =
        trackingAutoConfirmExcludedKeywordsText.keywordEntriesForAutoConfirm()

    private fun String.keywordEntriesForAutoConfirm(): List<String> =
        split('\n', ',', '·')
            .map { keyword -> keyword.normalizeAutoConfirmKeywordText() }
            .filter(String::isNotBlank)
            .distinct()

    private fun String.normalizeAutoConfirmKeywordText(): String =
        lowercase(Locale.KOREAN)
            .replace(Regex("""[\s/\\\-_.()·:：]+"""), "")
            .trim()

    private val SpecialManualReviewKeywords = listOf(
        SpecialManualReviewKeyword("사다주기", "사다주기", "물건사다", "물건사다주기", "사서전달"),
        SpecialManualReviewKeyword("AS센터/방문", "as센터", "as방문", "에이에스센터", "방문후", "방문하고"),
        SpecialManualReviewKeyword("대기", "대기", "대기시간", "대기비"),
        SpecialManualReviewKeyword("법원/집행/증인", "법원", "집행", "증인"),
        SpecialManualReviewKeyword("심부름", "심부름"),
    )

    private class SpecialManualReviewKeyword(
        val label: String,
        vararg keys: String,
    ) {
        val keys: Set<String> = keys.toSet()
    }

    private fun String.normalizeSpecialOrderText(): String =
        lowercase(Locale.KOREAN)
            .replace(Regex("""[\s/\\\-_.()·:：]+"""), "")

    private fun MatchResult.toScheduledDateTime(now: LocalDateTime): LocalDateTime? {
        val dayKeyword = groupValues.getOrNull(1).orEmpty()
        val dayOfMonth = groupValues.getOrNull(2)?.toIntOrNull()
        val amPm = groupValues.getOrNull(3).orEmpty()
        var hour = groupValues.getOrNull(4)?.toIntOrNull() ?: return null
        val minute = groupValues.getOrNull(5)?.toIntOrNull()
            ?: groupValues.getOrNull(6)?.toIntOrNull()
            ?: 0
        if (minute !in 0..59) return null
        if (hour !in 0..23) return null

        when (amPm) {
            "오전" -> if (hour == 12) hour = 0
            "오후" -> if (hour < 12) hour += 12
        }

        val date = when {
            dayKeyword == "내일" || dayKeyword == "낼" -> now.toLocalDate().plusDays(1)
            dayOfMonth != null -> now.toLocalDate().dateWithNextMatchingDay(dayOfMonth) ?: return null
            else -> now.toLocalDate()
        }
        return date.atTime(hour, minute)
    }

    private fun LocalDate.dateWithNextMatchingDay(dayOfMonth: Int): LocalDate? {
        if (dayOfMonth !in 1..31) return null
        val thisMonth = YearMonth.from(this)
        if (thisMonth.isValidDay(dayOfMonth)) {
            val candidate = withDayOfMonth(dayOfMonth)
            if (!candidate.isBefore(this)) return candidate
        }
        val nextMonth = thisMonth.plusMonths(1)
        return if (nextMonth.isValidDay(dayOfMonth)) {
            nextMonth.atDay(dayOfMonth)
        } else {
            null
        }
    }

    private fun Long.toFutureDurationText(): String {
        val hours = this / 60
        val minutes = this % 60
        return if (hours > 0) {
            "${hours}시간 ${minutes}분"
        } else {
            "${minutes}분"
        }
    }

    private fun Double.formatDistanceKm(): String =
        if (this % 1.0 == 0.0) {
            toInt().toString()
        } else {
            "%.1f".format(Locale.getDefault(), this)
        }

    private const val ScheduledOrderExcludeMinutes = 30L

    private val ScheduledOrderTimeRegex = Regex(
        """@\s*(?:(오늘|내일|낼)\s*)?(?:(\d{1,2})\s*일\s*)?(?:(오전|오후)\s*)?(\d{1,2})\s*(?::\s*(\d{1,2})|시\s*(?:(\d{1,2})\s*분?)?)""",
        RegexOption.IGNORE_CASE,
    )
}
