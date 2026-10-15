package com.verto.domain.rules

import com.verto.domain.FourAm
import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.at
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LogicalDayTest {

    @Test
    fun beforeDayStart_belongsToPreviousDay() {
        assertEquals(LocalDate(2026, 10, 15), LogicalDay.of(at(16, 3, 59), SaoPaulo, FourAm))
    }

    @Test
    fun atDayStart_belongsToSameDay() {
        assertEquals(Oct15, LogicalDay.of(at(15, 4, 0), SaoPaulo, FourAm))
    }

    @Test
    fun lateNight_belongsToSameDay() {
        assertEquals(Oct15, LogicalDay.of(at(15, 23, 30), SaoPaulo, FourAm))
    }

    @Test
    fun startOf_isDayStartInTheZone() {
        assertEquals(at(15, 4, 0), LogicalDay.startOf(Oct15, SaoPaulo, FourAm))
    }
}
