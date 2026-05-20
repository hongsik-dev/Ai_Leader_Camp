package com.catchpro.app.observation

import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanOrderDraftParserTest {
    @Test
    fun clientHeaderIsParsedFromOrderDetailTitle() {
        val draft = KoreanOrderDraftParser.parse(
            AccessibilityCaptureEntity(
                packageName = "insung.split.quick",
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                screenTitle = "오마이퀵서비스-1566-5912",
                summaryText = "오마이퀵서비스-1566-5912 | 완료 | 상태 : | 완료",
                nodeCount = 6,
                rawHierarchy = """
                    package=insung.split.quick
                    - TextView text="오마이퀵서비스-1566-5912" id="insung.split.quick:id/q_CustTitle"
                    - TextView text="완료" id="insung.split.quick:id/q_btnClose"
                    - TextView text="상태 :" id="insung.split.quick:id/q_tvStatusLabel"
                    - TextView text="완료" id="insung.split.quick:id/q_tvStatus"
                """.trimIndent(),
            ),
        )

        assertEquals("오마이퀵서비스-1566-5912", draft.clientText)
    }

    @Test
    fun closeButtonOnAddressDetailIsNotParsedAsConfirmAction() {
        val draft = KoreanOrderDraftParser.parse(
            AccessibilityCaptureEntity(
                packageName = "insung.split.quick",
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                screenTitle = "도착지 상세",
                summaryText = "도착지 상세 | 도착 | 서울 영등포구 | 위치 | 여의도동 서울 영등포구 여의도동 1 국회경비대 1층 | 닫기",
                nodeCount = 6,
                rawHierarchy = """
                    package=insung.split.quick
                    - TextView text="도착지 상세"
                    - TextView text="서울 영등포구" id="insung.split.quick:id/q_tvDest"
                    - TextView text="여의도동 서울 영등포구 여의도동 1 (서울 영등포구 의사당대로 1) 국회경비대 1층" id="insung.split.quick:id/q_tvJukyo"
                    - Button text="닫기" id="insung.split.quick:id/q_btnClose"
                """.trimIndent(),
            ),
        )

        assertNull(draft.confirmActionLabel)
        assertTrue(draft.detailNote.orEmpty().contains("국회경비대 1층"))
    }

    @Test
    fun positionDetailEditTextIsParsedAsDetailNote() {
        val draft = KoreanOrderDraftParser.parse(
            AccessibilityCaptureEntity(
                packageName = "insung.split.quick",
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                screenTitle = "도착지 상세",
                summaryText = "도착지 상세 | 고객 | 신희건 님 | 도착 | 서울 강남구 | 위치",
                nodeCount = 24,
                rawHierarchy = """
                    package=insung.split.quick
                    - TextView text="도착지 상세" id="insung.split.quick:id/q_CustTitle"
                    - TextView text="도착" id="insung.split.quick:id/q_TextView04"
                    - TextView text="서울 강남구" id="insung.split.quick:id/q_tvCDongName"
                    - TextView text="위치" id="insung.split.quick:id/q_TextView11"
                    - EditText text="역삼동 서울 강남구 역삼동 707-5 (서울 강남구 테헤란로 316) 메트라이프타워 로비" id="insung.split.quick:id/q_etPositionDetail"
                    - Button id="insung.split.quick:id/q_Button04"
                    - Button id="insung.split.quick:id/q_Button03"
                """.trimIndent(),
            ),
        )

        assertTrue(draft.detailNote.orEmpty().contains("서울 강남구 역삼동 707-5"))
        assertTrue(draft.detailNote.orEmpty().contains("테헤란로 316"))
        assertNull(draft.confirmActionLabel)
    }

    @Test
    fun pickupAddressDetailDoesNotParseLocationAsDestination() {
        val draft = KoreanOrderDraftParser.parse(
            AccessibilityCaptureEntity(
                packageName = "insung.split.quick",
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                screenTitle = "출발지 상세",
                summaryText = "출발지 상세 | 고객 | 별주부떡방 | 출발 | 서울 강남구 | 위치",
                nodeCount = 24,
                rawHierarchy = """
                    package=insung.split.quick
                    - TextView text="출발지 상세" id="insung.split.quick:id/q_CustTitle"
                    - TextView text="출발" id="insung.split.quick:id/q_TextView04"
                    - TextView text="서울 강남구" id="insung.split.quick:id/q_tvStart"
                    - TextView text="위치" id="insung.split.quick:id/q_TextView11"
                    - EditText text="월8/개포동 서울 강남구 개포동 1218-1 (서울 강남구 개포로24길 10), 1층" id="insung.split.quick:id/q_etPositionDetail"
                    - Button text="닫기" id="insung.split.quick:id/q_btnClose"
                """.trimIndent(),
            ),
        )

        assertEquals("서울 강남구", draft.origin)
        assertNull(draft.destination)
        assertTrue(draft.detailNote.orEmpty().contains("서울 강남구 개포동 1218-1"))
        assertNull(draft.confirmActionLabel)
    }

    @Test
    fun confirmButtonIsStillParsedAsConfirmAction() {
        val draft = KoreanOrderDraftParser.parse(
            AccessibilityCaptureEntity(
                packageName = "insung.split.quick",
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                screenTitle = "오더 상세",
                summaryText = "오더 상세 | 확정",
                nodeCount = 3,
                rawHierarchy = """
                    package=insung.split.quick
                    - TextView text="서울 영등포구" id="insung.split.quick:id/q_tvDest"
                    - Button text="확정" id="insung.split.quick:id/q_btnClose"
                """.trimIndent(),
            ),
        )

        assertEquals("확정", draft.confirmActionLabel)
    }

    @Test
    fun pickupToDropoffStraightDistanceIsParsedFromDetailNote() {
        val draft = KoreanOrderDraftParser.parse(
            AccessibilityCaptureEntity(
                packageName = "insung.split.quick",
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                screenTitle = "오더 상세",
                summaryText = "오더 상세 | 확정",
                nodeCount = 3,
                rawHierarchy = """
                    package=insung.split.quick
                    - TextView text="쇼핑백 현위치 → 상차지(직선)3.0KM 상차지 → 하차지(직선)40.1KM" id="insung.split.quick:id/q_tvJukyo"
                    - TextView text="서울 강남구" id="insung.split.quick:id/q_tvStart"
                    - TextView text="서울 강서구" id="insung.split.quick:id/q_tvDest"
                    - Button text="확정" id="insung.split.quick:id/q_btnClose"
                """.trimIndent(),
            ),
        )

        assertEquals(3.0, draft.currentToPickupDistanceKm ?: -1.0, 0.001)
        assertEquals(40.1, draft.pickupToDropoffDistanceKm ?: -1.0, 0.001)
    }
}
