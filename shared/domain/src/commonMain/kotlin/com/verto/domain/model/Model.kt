package com.verto.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class StepEnergy { Low, Medium, Hyperfocus }

enum class EnergyLevel { Low, Medium, High }

enum class InboxSource { Widget, Tile, Share, App }

enum class InboxState { Raw, Processed, Incubated, Discarded }

enum class DueKind { Hard, Soft }

enum class Plan { A, C }

enum class RoutineOutcome { A, C, Skipped }

enum class AnchorTrigger { Tap, Window }

data class InboxItem(
    val id: String,
    val text: String,
    val createdAt: Instant,
    val source: InboxSource,
    val state: InboxState,
    /** When it last became raw: a restored item starts its incubation clock over. */
    val rawSince: Instant = createdAt,
)

data class Task(
    val id: String,
    val title: String,
    val notes: String?,
    val dueDate: LocalDate?,
    val dueKind: DueKind?,
    val pinnedToToday: Boolean,
    val doneAt: Instant?,
)

sealed interface StepOwner {
    data class OfTask(val taskId: String) : StepOwner
    data class OfRoutine(val routineId: String, val plan: Plan) : StepOwner
}

data class Step(
    val id: String,
    val owner: StepOwner,
    val order: Int,
    val title: String,
    val energy: StepEnergy,
    val doneAt: Instant?,
)

data class Anchor(
    val id: String,
    val name: String,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
)

data class AnchorEvent(
    val id: String,
    val anchorId: String,
    val day: LocalDate,
    val triggeredAt: Instant,
    val source: AnchorTrigger,
)

data class Routine(
    val id: String,
    val name: String,
    val anchorId: String,
    val weekdays: Set<DayOfWeek>,
)

data class RoutineLog(
    val id: String,
    val routineId: String,
    val day: LocalDate,
    val outcome: RoutineOutcome,
)

data class DayEnergy(
    val id: String,
    val day: LocalDate,
    val level: EnergyLevel,
    val setAt: Instant,
)
