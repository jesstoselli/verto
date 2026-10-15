package com.verto.ui.common

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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

    @Test
    fun clockDigits_keepsUpToFourDigits() {
        assertEquals("1430", clockDigits("14:30"))
        assertEquals("1234", clockDigits("1a2b345"))
    }

    @Test
    fun parseClock_readsTheMaskedDigits() {
        assertEquals(LocalTime(14, 30), parseClock("1430"))
        assertEquals(LocalTime(7, 30), parseClock("0730"))
        assertEquals(LocalTime(12, 0), parseClock("12"))
        assertNull(parseClock("930"))
        assertNull(parseClock("2460"))
    }

    @Test
    fun clockMask_showsTheColonOnceTheMinutesStart() {
        fun shown(digits: String) = ClockMask.filter(AnnotatedString(digits)).text.text
        assertEquals("1", shown("1"))
        assertEquals("14", shown("14"))
        assertEquals("14:3", shown("143"))
        assertEquals("14:30", shown("1430"))
    }

    @Test
    fun clockMask_keepsTheCursorOnTheRightDigit() {
        val mapping = ClockMask.filter(AnnotatedString("1430")).offsetMapping
        assertEquals(2, mapping.originalToTransformed(2))
        assertEquals(4, mapping.originalToTransformed(3))
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(2, mapping.transformedToOriginal(3))
        assertEquals(4, mapping.transformedToOriginal(5))
    }
}
