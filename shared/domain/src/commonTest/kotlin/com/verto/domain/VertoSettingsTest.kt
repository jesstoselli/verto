package com.verto.domain

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class VertoSettingsTest {

    @Test
    fun defaultsMatchTheSpec() {
        val settings = VertoSettings()

        assertEquals(LocalTime(4, 0), settings.dayStartsAt)
        assertEquals(4.days, settings.absenceThreshold)
        assertEquals(3.days, settings.incubationAfter)
        assertEquals(3, settings.pinnedSoftLimit)
        assertEquals(3, settings.deadlineHorizonDays)
    }
}
