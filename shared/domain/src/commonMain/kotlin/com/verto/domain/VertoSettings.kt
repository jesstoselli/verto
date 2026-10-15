package com.verto.domain

import kotlinx.datetime.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Values the spec calls configurable; they become editable with the Ajustes screen (Plan 4). */
data class VertoSettings(
    val dayStartsAt: LocalTime = LocalTime(4, 0),
    val absenceThreshold: Duration = 4.days,
    val incubationAfter: Duration = 3.days,
    val pinnedSoftLimit: Int = 3,
    val deadlineHorizonDays: Int = 3,
)
