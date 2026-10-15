package com.verto.ui.common

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatsTest {

    private val oct15 = LocalDate(2026, 10, 15)

    @Test
    fun longPt_readsLikeACalendar() {
        assertEquals("quinta, 15 de outubro", oct15.longPt())
    }

    @Test
    fun deadlineLabel_isNeutralForEveryCase() {
        assertEquals("prazo era dia 12/10", deadlineLabel(LocalDate(2026, 10, 12), oct15))
        assertEquals("prazo é hoje", deadlineLabel(oct15, oct15))
        assertEquals("prazo dia 18/10", deadlineLabel(LocalDate(2026, 10, 18), oct15))
    }

    @Test
    fun parseClock_acceptsShortForms() {
        assertEquals(LocalTime(7, 0), parseClock("7"))
        assertEquals(LocalTime(7, 30), parseClock(" 07:30 "))
        assertEquals(LocalTime(12, 5), parseClock("12:5"))
    }

    @Test
    fun parseClock_rejectsImpossibleTimes() {
        assertNull(parseClock("24:00"))
        assertNull(parseClock("12:60"))
        assertNull(parseClock("almoço"))
        assertNull(parseClock(""))
    }

    @Test
    fun weekdaysLabel_namesEveryDayOnlyWhenNeeded() {
        assertEquals("todo dia", DayOfWeek.entries.toSet().label())
        assertEquals("seg · qua · sex", setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY).label())
    }
}
