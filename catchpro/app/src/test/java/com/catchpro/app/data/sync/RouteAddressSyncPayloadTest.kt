package com.catchpro.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RouteAddressSyncPayloadTest {
    @Test
    fun shareTextRoundTripsSixRouteAddressSlots() {
        val addresses = listOf(
            "경기 용인시 처인구 남사읍 한숲로 123",
            "경기 평택시 지산동 1113",
            "경기 오산시 가장동 1",
            "경기 화성시 동탄대로 2",
            "경기 이천시 호법면 중부대로798번길 125",
            "경기도 오산시 경기동로 33",
        )

        val decoded = RouteAddressSyncPayload.decode(
            RouteAddressSyncPayload.shareText(addresses),
        )

        assertEquals(addresses, decoded)
    }

    @Test
    fun deepLinkInsideMessageCanBeDecoded() {
        val shareText = RouteAddressSyncPayload.shareText(
            listOf("경기 평택시 지산동 1113"),
        )
        val deepLink = shareText
            .lineSequence()
            .first { it.startsWith("catchpro://") }

        val decoded = RouteAddressSyncPayload.decode("주소 받기 $deepLink")

        assertNotNull(decoded)
        assertEquals("경기 평택시 지산동 1113", decoded?.first())
    }
}
