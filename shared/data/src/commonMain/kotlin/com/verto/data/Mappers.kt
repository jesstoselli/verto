package com.verto.data

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.DueKind
import com.verto.domain.model.InboxItem
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.Step
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.time.Instant

private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

internal fun InboxItemEntity.toDomain() =
    InboxItem(id, text, createdAt.toInstant(), source.toEnum(), state.toEnum(), rawSince = updatedAt.toInstant())

internal fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    notes = notes,
    dueDate = dueDate?.let { LocalDate.parse(it) },
    dueKind = dueKind?.toEnum<DueKind>(),
    pinnedToToday = pinnedToToday,
    doneAt = doneAt?.toInstant(),
)

internal fun Task.toEntity(updatedAt: Long, deletedAt: Long? = null) = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    dueDate = dueDate?.toString(),
    dueKind = dueKind?.wire(),
    pinnedToToday = pinnedToToday,
    doneAt = doneAt?.toEpochMilliseconds(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun StepEntity.toDomain(): Step {
    val owner = if (taskId != null) {
        StepOwner.OfTask(taskId)
    } else {
        StepOwner.OfRoutine(checkNotNull(routineId), checkNotNull(plan).toEnum())
    }
    return Step(id, owner, order, title, energy.toEnum(), doneAt?.toInstant())
}

internal fun AnchorEntity.toDomain() =
    Anchor(id, name, LocalTime.parse(windowStart), LocalTime.parse(windowEnd))

internal fun AnchorEventEntity.toDomain() =
    AnchorEvent(id, anchorId, LocalDate.parse(day), triggeredAt.toInstant(), source.toEnum())

internal fun RoutineEntity.toDomain() = Routine(id, name, anchorId, weekdaysOf(weekdays))

internal fun RoutineLogEntity.toDomain() = RoutineLog(id, routineId, LocalDate.parse(day), outcome.toEnum())

internal fun Set<DayOfWeek>.toWire(): String = map { it.isoDayNumber }.sorted().joinToString(",")

internal fun weekdaysOf(wire: String): Set<DayOfWeek> =
    wire.split(',').filter { it.isNotBlank() }.map { DayOfWeek(it.toInt()) }.toSet()
