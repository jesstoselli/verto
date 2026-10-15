package com.verto.domain.rules

import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * An anchor fires once per logical day: whichever comes first, the user's tap
 * ("Almocei") or its time window opening, which is the safety net for forgetful days.
 */
object AnchorRules {

    fun windowOpensAt(anchor: Anchor, day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Instant {
        // A window before the day start (e.g. 01:00) belongs to the end of the logical day.
        val date = if (anchor.windowStart < dayStartsAt) day.plus(1, DateTimeUnit.DAY) else day
        return date.atTime(anchor.windowStart).toInstant(zone)
    }

    fun isTriggered(anchor: Anchor, day: LocalDate, events: List<AnchorEvent>): Boolean =
        events.any { it.anchorId == anchor.id && it.day == day }

    fun canTap(anchor: Anchor, day: LocalDate, events: List<AnchorEvent>): Boolean =
        !isTriggered(anchor, day, events)

    fun windowsToRecord(
        anchors: List<Anchor>,
        day: LocalDate,
        events: List<AnchorEvent>,
        now: Instant,
        zone: TimeZone,
        dayStartsAt: LocalTime,
    ): List<Anchor> = anchors.filter { anchor ->
        !isTriggered(anchor, day, events) && now >= windowOpensAt(anchor, day, zone, dayStartsAt)
    }
}
