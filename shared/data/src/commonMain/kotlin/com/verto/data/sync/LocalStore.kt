package com.verto.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.OutboxEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.db.VertoDatabase
import kotlinx.serialization.encodeToString

class LocalStore(val db: VertoDatabase) {

    /** Row and outbox entry commit together, so the sync engine can never miss a local change. */
    suspend fun <T> write(block: suspend WriteScope.() -> T): T =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction { WriteScope(db).block() }
        }
}

class WriteScope internal constructor(private val db: VertoDatabase) {

    suspend fun put(entity: InboxItemEntity) {
        db.inboxDao().upsert(entity)
        enqueue("inbox_item", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: TaskEntity) {
        db.taskDao().upsert(entity)
        enqueue("task", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: StepEntity) {
        db.stepDao().upsert(entity)
        enqueue("step", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: AnchorEntity) {
        db.anchorDao().upsertAnchor(entity)
        enqueue("anchor", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: AnchorEventEntity) {
        db.anchorDao().upsertEvent(entity)
        enqueue("anchor_event", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: RoutineEntity) {
        db.routineDao().upsertRoutine(entity)
        enqueue("routine", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: RoutineLogEntity) {
        db.routineDao().upsertLog(entity)
        enqueue("routine_log", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: DayEnergyEntity) {
        db.dayEnergyDao().upsert(entity)
        enqueue("day_energy", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    private suspend fun enqueue(type: String, id: String, payload: String, deletedAt: Long?, updatedAt: Long) =
        db.outboxDao().enqueue(
            OutboxEntity(entityType = type, entityId = id, payload = payload, deletedAt = deletedAt, clientUpdatedAt = updatedAt),
        )
}
