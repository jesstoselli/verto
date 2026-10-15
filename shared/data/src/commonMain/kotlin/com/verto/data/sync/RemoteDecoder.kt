package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import kotlinx.serialization.SerializationException
import kotlin.time.Instant

/** Turns a pulled record back into the row it came from. */
internal object RemoteDecoder {

    /**
     * Returns the entity, or null when the type is unknown (a newer client's entity) or the payload
     * cannot be read: one bad record is skipped instead of stopping the whole pull.
     */
    fun decode(entityType: String, id: String, payload: String, deletedAt: Long?, receivedAt: Long): Any? = try {
        when (entityType) {
            "inbox_item" -> SyncJson.decodeFromString(InboxPayload.serializer(), payload).run {
                InboxItemEntity(id, text, createdAt.ms(), source, state, updatedAt.msOr(receivedAt), deletedAt)
            }
            "task" -> SyncJson.decodeFromString(TaskPayload.serializer(), payload).run {
                TaskEntity(id, title, notes, dueDate, dueKind, pinnedToToday, doneAt?.ms(), updatedAt.msOr(receivedAt), deletedAt)
            }
            "step" -> SyncJson.decodeFromString(StepPayload.serializer(), payload).run {
                StepEntity(id, taskId, routineId, plan, order, title, energy, doneAt?.ms(), updatedAt.msOr(receivedAt), deletedAt)
            }
            "anchor" -> SyncJson.decodeFromString(AnchorPayload.serializer(), payload).run {
                AnchorEntity(id, name, windowStart, windowEnd, updatedAt.msOr(receivedAt), deletedAt)
            }
            "anchor_event" -> SyncJson.decodeFromString(AnchorEventPayload.serializer(), payload).run {
                AnchorEventEntity(id, anchorId, day, triggeredAt.ms(), source, updatedAt.msOr(receivedAt), deletedAt)
            }
            "routine" -> SyncJson.decodeFromString(RoutinePayload.serializer(), payload).run {
                RoutineEntity(id, name, anchorId, weekdays.joinToString(","), updatedAt.msOr(receivedAt), deletedAt)
            }
            "routine_log" -> SyncJson.decodeFromString(RoutineLogPayload.serializer(), payload).run {
                RoutineLogEntity(id, routineId, day, outcome, updatedAt.msOr(receivedAt), deletedAt)
            }
            "day_energy" -> SyncJson.decodeFromString(DayEnergyPayload.serializer(), payload).run {
                DayEnergyEntity(id, day, level, setAt.ms(), updatedAt.msOr(receivedAt), deletedAt)
            }
            else -> null
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun String.ms(): Long = Instant.parse(this).toEpochMilliseconds()

    private fun String?.msOr(fallback: Long): Long = this?.ms() ?: fallback
}
