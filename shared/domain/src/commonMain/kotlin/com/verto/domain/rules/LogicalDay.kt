package com.verto.domain.rules

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The planner's day turns at [dayStartsAt] (04:00 by default), so 01:00 still belongs to "yesterday". */
object LogicalDay {

    fun of(instant: Instant, zone: TimeZone, dayStartsAt: LocalTime): LocalDate {
        val local = instant.toLocalDateTime(zone)
        return if (local.time < dayStartsAt) local.date.minus(1, DateTimeUnit.DAY) else local.date
    }

    fun startOf(day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Instant =
        day.atTime(dayStartsAt).toInstant(zone)
}
