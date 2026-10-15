package com.verto.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "inbox_items")
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val text: String,
    val createdAt: Long,
    val source: String,
    val state: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val dueKind: String?,
    val pinnedToToday: Boolean,
    val doneAt: Long?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "steps", indices = [Index("taskId"), Index("routineId")])
data class StepEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val routineId: String?,
    val plan: String?,
    // "order" is an SQL keyword.
    @ColumnInfo(name = "position") val order: Int,
    val title: String,
    val energy: String,
    val doneAt: Long?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "anchors")
data class AnchorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val windowStart: String,
    val windowEnd: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "anchor_events", indices = [Index(value = ["anchorId", "day"])])
data class AnchorEventEntity(
    @PrimaryKey val id: String,
    val anchorId: String,
    val day: String,
    val triggeredAt: Long,
    val source: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "routines", indices = [Index("anchorId")])
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val anchorId: String,
    val weekdays: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "routine_logs", indices = [Index(value = ["routineId", "day"])])
data class RoutineLogEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val day: String,
    val outcome: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "day_energy", indices = [Index("day")])
data class DayEnergyEntity(
    @PrimaryKey val id: String,
    val day: String,
    val level: String,
    val setAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** One pending row per record: rewriting a record replaces its entry, so a push sends only the latest state. */
@Entity(tableName = "outbox", indices = [Index(value = ["entityType", "entityId"], unique = true)])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entityType: String,
    val entityId: String,
    val payload: String,
    val deletedAt: Long?,
    val clientUpdatedAt: Long,
)
