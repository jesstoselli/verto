package com.verto.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalTime

/** Values the spec calls configurable; they become editable with the Ajustes screen (Plan 4). */
data class VertoSettings(
    val dayStartsAt: LocalTime = LocalTime(4, 0),
    val absenceThreshold: Duration = 4.days,
    val incubationAfter: Duration = 3.days,
    val pinnedSoftLimit: Int = 3,
    val deadlineHorizonDays: Int = 3,
    val recentlyDoneWindow: Duration = 48.hours,
)
