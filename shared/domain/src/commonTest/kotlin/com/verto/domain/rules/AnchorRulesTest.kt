package com.verto.domain.rules

import com.verto.domain.FourAm
import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.anchor
import com.verto.domain.at
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.AnchorTrigger
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchorRulesTest {

    private val breakfast = anchor("breakfast", start = 6)
    private val lunch = anchor("lunch", start = 12)
    private val dinner = anchor("dinner", start = 19)

    private fun tapped(anchorId: String) =
        AnchorEvent("e-$anchorId", anchorId, Oct15, at(15, 7), AnchorTrigger.Tap)

    @Test
    fun windowOpensAt_isWindowStartOnTheLogicalDay() {
        assertEquals(at(15, 12), AnchorRules.windowOpensAt(lunch, Oct15, SaoPaulo, FourAm))
    }

    @Test
    fun windowOpensAt_beforeDayStart_fallsOnTheNextCalendarDate() {
        val lateNight = anchor("late", start = 1)

        assertEquals(at(16, 1), AnchorRules.windowOpensAt(lateNight, Oct15, SaoPaulo, FourAm))
    }

    @Test
    fun windowsToRecord_returnsOpenedWindowsNotYetTriggered() {
        val result = AnchorRules.windowsToRecord(
            anchors = listOf(breakfast, lunch, dinner),
            day = Oct15,
            events = listOf(tapped("breakfast")),
            now = at(15, 12, 30),
            zone = SaoPaulo,
            dayStartsAt = FourAm,
        )

        assertEquals(listOf(lunch), result)
    }

    @Test
    fun canTap_onlyBeforeAnythingTriggeredTheAnchorToday() {
        assertTrue(AnchorRules.canTap(lunch, Oct15, emptyList()))
        assertFalse(AnchorRules.canTap(breakfast, Oct15, listOf(tapped("breakfast"))))
    }

    @Test
    fun isTriggered_ignoresEventsFromOtherDays() {
        val yesterday = AnchorEvent("old", "lunch", LocalDate(2026, 10, 14), at(14, 12), AnchorTrigger.Window)

        assertFalse(AnchorRules.isTriggered(lunch, Oct15, listOf(yesterday)))
    }
}
