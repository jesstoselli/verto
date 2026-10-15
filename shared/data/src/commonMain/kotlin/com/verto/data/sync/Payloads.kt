package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal val SyncJson = Json { encodeDefaults = true }

// Key names are the sync contract with the API (SyncEntityCatalog.cs); rename nothing here without the server.

@Serializable
internal data class InboxPayload(val text: String, val createdAt: String, val source: String, val state: String)

@Serializable
internal data class TaskPayload(
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val dueKind: String?,
    val pinnedToToday: Boolean,
    val doneAt: String?,
)

@Serializable
internal data class StepPayload(
    val taskId: String?,
    val routineId: String?,
    val plan: String?,
    val order: Int,
    val title: String,
    val energy: String,
    val doneAt: String?,
)

@Serializable
internal data class AnchorPayload(val name: String, val windowStart: String, val windowEnd: String)

@Serializable
internal data class AnchorEventPayload(val anchorId: String, val day: String, val triggeredAt: String, val source: String)

@Serializable
internal data class RoutinePayload(val name: String, val anchorId: String, val weekdays: List<Int>)

@Serializable
internal data class RoutineLogPayload(val routineId: String, val day: String, val outcome: String)

@Serializable
internal data class DayEnergyPayload(val day: String, val level: String, val setAt: String)

private fun Long.iso(): String = Instant.fromEpochMilliseconds(this).toString()

internal fun InboxItemEntity.toPayload() = InboxPayload(text, createdAt.iso(), source, state)

internal fun TaskEntity.toPayload() = TaskPayload(title, notes, dueDate, dueKind, pinnedToToday, doneAt?.iso())

internal fun StepEntity.toPayload() = StepPayload(taskId, routineId, plan, order, title, energy, doneAt?.iso())

internal fun AnchorEntity.toPayload() = AnchorPayload(name, windowStart, windowEnd)

internal fun AnchorEventEntity.toPayload() = AnchorEventPayload(anchorId, day, triggeredAt.iso(), source)

internal fun RoutineEntity.toPayload() =
    RoutinePayload(name, anchorId, weekdays.split(',').filter { it.isNotBlank() }.map { it.toInt() })

internal fun RoutineLogEntity.toPayload() = RoutineLogPayload(routineId, day, outcome)

internal fun DayEnergyEntity.toPayload() = DayEnergyPayload(day, level, setAt.iso())
