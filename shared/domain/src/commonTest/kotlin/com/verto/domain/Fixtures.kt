package com.verto.domain

import com.verto.domain.model.Anchor
import com.verto.domain.model.DueKind
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.Step
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

val SaoPaulo: TimeZone = TimeZone.of("America/Sao_Paulo")
val FourAm = LocalTime(4, 0)

/** Thursday. */
val Oct15 = LocalDate(2026, 10, 15)

fun at(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime(2026, 10, day, hour, minute).toInstant(SaoPaulo)

fun anchor(id: String, start: Int, end: Int = start + 2, name: String = id) =
    Anchor(id, name, LocalTime(start, 0), LocalTime(end, 0))

fun routine(id: String, anchorId: String, weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(), name: String = id) =
    Routine(id, name, anchorId, weekdays)

fun routineStep(id: String, routineId: String, plan: Plan, order: Int = 0, doneAt: Instant? = null, energy: StepEnergy = StepEnergy.Low) =
    Step(id, StepOwner.OfRoutine(routineId, plan), order, id, energy, doneAt)

fun taskStep(id: String, taskId: String, order: Int = 0, doneAt: Instant? = null) =
    Step(id, StepOwner.OfTask(taskId), order, id, StepEnergy.Low, doneAt)

fun task(
    id: String,
    pinned: Boolean = false,
    dueDate: LocalDate? = null,
    dueKind: DueKind? = null,
    doneAt: Instant? = null,
) = Task(id, id, null, dueDate, dueKind, pinned, doneAt)
