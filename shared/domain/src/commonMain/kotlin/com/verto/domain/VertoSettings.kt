package com.verto.domain

import kotlinx.datetime.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Values the spec calls configurable. Ajustes edits the day start, the absence threshold and the quiet hours. */
data class VertoSettings(
    val dayStartsAt: LocalTime = LocalTime(4, 0),
    val absenceThreshold: Duration = 4.days,
    val incubationAfter: Duration = 3.days,
    val pinnedSoftLimit: Int = 3,
    val deadlineHorizonDays: Int = 3,
    val recentlyDoneWindow: Duration = 48.hours,
    val quietStart: LocalTime = LocalTime(22, 0),
    val quietEnd: LocalTime = LocalTime(8, 0),
)

/** Read on every use, like the time zone: Ajustes can change a value while a screen is open. */
fun interface SettingsSource {
    fun current(): VertoSettings
}
