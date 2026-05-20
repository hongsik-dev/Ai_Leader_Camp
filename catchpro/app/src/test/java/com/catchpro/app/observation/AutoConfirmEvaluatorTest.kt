package com.catchpro.app.observation

import com.catchpro.app.data.model.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConfirmEvaluatorTest {
    @Test
    fun primaryConfirmsOnlyWithDestinationAndMinimumPrice() {
        val settings = AppSettings(
            primaryAutoConfirmEnabled = true,
            primaryDestinationKeywords = "경기도 화성시",
            primaryMinimumPriceText = "10000",
        )
        val draft = ParsedOrderDraft(
            clientText = "오산드림퀵-1566-2652",
            destination = "경기 화성시 반송동",
            detailNote = "왕복 / @18:10픽",
            pickupToDropoffDistanceKm = 0.0,
            price = 11000,
        )

        val decision = AutoConfirmEvaluator.evaluatePrimary(
            settings = settings,
            draft = draft,
        )

        assertTrue(decision.shouldConfirm)
        assertFalse(decision.manualInputRequired)
        assertFalse(decision.manualReviewRequired)
        assertTrue(decision.reasons.any { it.contains("1차 도착지 매치") })
        assertTrue(decision.reasons.any { it.contains("1차 요금") })
    }

    @Test
    fun primaryRejectsWhenDestinationDoesNotMatch() {
        val settings = AppSettings(
            primaryAutoConfirmEnabled = true,
            primaryDestinationKeywords = "경기도 화성시",
            primaryMinimumPriceText = "10000",
        )
        val draft = ParsedOrderDraft(
            destination = "경기 오산시 서동",
            price = 13000,
        )

        val decision = AutoConfirmEvaluator.evaluatePrimary(
            settings = settings,
            draft = draft,
        )

        assertFalse(decision.shouldConfirm)
        assertTrue(decision.reasons.any { it.contains("도착지 조건 불일치") })
    }

    @Test
    fun primaryRejectsWhenPriceIsBelowMinimum() {
        val settings = AppSettings(
            primaryAutoConfirmEnabled = true,
            primaryDestinationKeywords = "경기도 화성시",
            primaryMinimumPriceText = "10000",
        )
        val draft = ParsedOrderDraft(
            destination = "경기 화성시 반송동",
            price = 9200,
        )

        val decision = AutoConfirmEvaluator.evaluatePrimary(
            settings = settings,
            draft = draft,
        )

        assertFalse(decision.shouldConfirm)
        assertTrue(decision.reasons.any { it.contains("1차 요금") && it.contains("<") })
    }

    @Test
    fun primaryAllowsAllDestinationsWhenDestinationConditionIsMissing() {
        val settings = AppSettings(
            primaryAutoConfirmEnabled = true,
            primaryDestinationKeywords = "",
            primaryMinimumPriceText = "10000",
        )
        val draft = ParsedOrderDraft(
            destination = "경기 화성시 반송동",
            price = 11000,
        )

        val decision = AutoConfirmEvaluator.evaluatePrimary(
            settings = settings,
            draft = draft,
        )

        assertTrue(decision.shouldConfirm)
        assertTrue(decision.reasons.any { it.contains("모든 도착지 허용") })
        assertTrue(decision.reasons.any { it.contains("1차 요금") })
    }

    @Test
    fun primaryRejectsWhenMinimumPriceConditionIsMissing() {
        val settings = AppSettings(
            primaryAutoConfirmEnabled = true,
            primaryDestinationKeywords = "경기도 화성시",
            primaryMinimumPriceText = "",
        )
        val draft = ParsedOrderDraft(
            destination = "경기 화성시 반송동",
            price = 11000,
        )

        val decision = AutoConfirmEvaluator.evaluatePrimary(
            settings = settings,
            draft = draft,
        )

        assertFalse(decision.shouldConfirm)
        assertTrue(decision.reasons.any { it.contains("요금 조건이 설정되지 않았습니다") })
    }

    @Test
    fun secondaryStillRejectsBlacklistedClientBeforeMatchingConditions() {
        val settings = AppSettings(
            secondaryAutoConfirmEnabled = true,
            secondaryMaximumPickupDistanceKmText = "50",
        )
        val draft = ParsedOrderDraft(
            clientText = "오마이퀵서비스-1566-5912",
            currentToPickupDistanceKm = 3.0,
            origin = "서울특별시 강남구 역삼동 123",
            destination = "서울특별시 송파구 문정동 456",
        )

        val decision = AutoConfirmEvaluator.evaluateSecondary(
            settings = settings,
            draft = draft,
            pickupRoadDistanceKm = 8.0,
        )

        assertFalse(decision.shouldConfirm)
        assertFalse(decision.manualInputRequired)
        assertTrue(decision.reasons.any { it.contains("거래처 블랙리스트") })
    }

    @Test
    fun trackedAdditionalStillRequiresRouteAndDestinationMatch() {
        val settings = AppSettings(
            orderTrackingModeEnabled = true,
            activeDriveDestinationText = "서울 강남구 역삼동 707-5 메트라이프타워 로비",
        )
        val draft = ParsedOrderDraft(
            currentToPickupDistanceKm = 2.0,
            pickupToDropoffDistanceKm = 5.5,
            destination = "서울 강남구 역삼동",
            price = 18000,
        )

        val decision = AutoConfirmEvaluator.evaluateTrackedAdditional(
            settings = settings,
            draft = draft,
            pickupRoadDistanceKm = 3.0,
            destinationMatchReason = "서울 같은 구(강남구)",
        )

        assertTrue(decision.shouldConfirm)
        assertFalse(decision.manualInputRequired)
        assertTrue(decision.reasons.any { it.contains("추적 하차지 조건 통과") })
    }
}
