package com.catchpro.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualRouteAddressSlotsTest {
    @Test
    fun manualRouteAddressSlotsPreserveShortOrPartialUserInput() {
        val text = """
            주소1 임시입력
            주소2 임시입력
        """.trimIndent()

        val slots = text.manualRouteAddressSlots()

        assertEquals("주소1 임시입력", slots[0])
        assertEquals("주소2 임시입력", slots[1])
        assertEquals(6, slots.size)
    }

    @Test
    fun normalizeManualRouteAddressesTextKeepsSlotOrder() {
        val normalized = "경기 오산시\n\n용인 처인구".normalizeManualRouteAddressesText()

        assertEquals(
            listOf(
                "경기 오산시",
                "",
                "용인 처인구",
                "",
                "",
                "",
            ).joinToString("\n"),
            normalized,
        )
    }
}
