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
import com.verto.data.db.SyncStateEntity
import com.verto.data.db.TaskEntity
import com.verto.data.db.VertoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.Instant

/** A change pulled from the server, payload still as JSON text. */
@Serializable
data class RemoteChange(
    val entityType: String,
    val id: String,
    val payload: String,
    val deletedAt: Long?,
)

class LocalStore(val db: VertoDatabase) {

    /** Row and outbox entry commit together, so the sync engine can never miss a local change. */
    suspend fun <T> write(block: suspend WriteScope.() -> T): T =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction { WriteScope(db).block() }
        }

    suspend fun pendingBatch(limit: Int): List<OutboxEntity> = db.outboxDao().oldest(limit)

    suspend fun markSent(seqs: List<Long>) {
        if (seqs.isNotEmpty()) db.outboxDao().deleteSent(seqs)
    }

    fun observePendingCount(): Flow<Int> = db.outboxDao().observeSize()

    /**
     * Writes pulled records straight to their tables (they must not go back out through the outbox)
     * and moves the cursor in the same transaction, so a crash never skips or half-applies a page.
     * A record this build cannot read (a newer client's type or field) is parked instead of dropped,
     * since the cursor moves past it, and retried on every pull until an update can read it.
     */
    suspend fun applyRemote(changes: List<RemoteChange>, cursor: Long, receivedAt: Long) {
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val stillParked = linkedMapOf<Pair<String, String>, RemoteChange>()
                for (change in readParked() + changes) {
                    // A local edit still waiting in the outbox wins: it reaches the server on the next push.
                    if (db.outboxDao().hasPending(change.entityType, change.id)) continue
                    val key = change.entityType to change.id
                    val row = RemoteDecoder.decode(change.entityType, change.id, change.payload, change.deletedAt, receivedAt)
                    when {
                        row != null -> {
                            upsertQuietly(row)
                            stillParked.remove(key)
                        }
                        // The server accepts tombstones with a minimal payload: delete by id.
                        change.deletedAt != null -> {
                            markDeleted(change.entityType, change.id, change.deletedAt)
                            stillParked.remove(key)
                        }
                        else -> stillParked[key] = change
                    }
                }
                db.syncStateDao().put(SyncStateEntity(PARKED, SyncJson.encodeToString(stillParked.values.toList())))
                db.syncStateDao().put(SyncStateEntity(CURSOR, cursor.toString()))
            }
        }
    }

    suspend fun cursor(): Long = db.syncStateDao().get(CURSOR)?.toLongOrNull() ?: 0L

    suspend fun lastSyncedAt(): Instant? = db.syncStateDao().get(LAST_SYNCED_AT)?.let { Instant.parse(it) }

    suspend fun setLastSyncedAt(at: Instant) = db.syncStateDao().put(SyncStateEntity(LAST_SYNCED_AT, at.toString()))

    private suspend fun upsertQuietly(row: Any?) {
        when (row) {
            is InboxItemEntity -> db.inboxDao().upsert(row)
            is TaskEntity -> db.taskDao().upsert(row)
            is StepEntity -> db.stepDao().upsert(row)
            is AnchorEntity -> db.anchorDao().upsertAnchor(row)
            is AnchorEventEntity -> db.anchorDao().upsertEvent(row)
            is RoutineEntity -> db.routineDao().upsertRoutine(row)
            is RoutineLogEntity -> db.routineDao().upsertLog(row)
            is DayEnergyEntity -> db.dayEnergyDao().upsert(row)
            else -> Unit
        }
    }

    internal suspend fun parked(): List<RemoteChange> = readParked()

    private suspend fun readParked(): List<RemoteChange> =
        db.syncStateDao().get(PARKED)?.let { SyncJson.decodeFromString<List<RemoteChange>>(it) } ?: emptyList()

    private suspend fun markDeleted(entityType: String, id: String, deletedAt: Long) {
        when (entityType) {
            "inbox_item" -> db.inboxDao().get(id)?.let { db.inboxDao().upsert(it.copy(deletedAt = deletedAt, updatedAt = deletedAt)) }
            "task" -> db.taskDao().get(id)?.let { db.taskDao().upsert(it.copy(deletedAt = deletedAt, updatedAt = deletedAt)) }
            "step" -> db.stepDao().get(id)?.let { db.stepDao().upsert(it.copy(deletedAt = deletedAt, updatedAt = deletedAt)) }
            "anchor" -> db.anchorDao().getAnchor(id)?.let { db.anchorDao().upsertAnchor(it.copy(deletedAt = deletedAt, updatedAt = deletedAt)) }
            "routine" -> db.routineDao().getRoutine(id)?.let { db.routineDao().upsertRoutine(it.copy(deletedAt = deletedAt, updatedAt = deletedAt)) }
            // Events, logs and energy have no lookup by id; their tombstones always come from this app, with a full payload.
            else -> Unit
        }
    }

    internal companion object {
        const val CURSOR = "pull_cursor"
        const val LAST_SYNCED_AT = "last_synced_at"
        const val PARKED = "parked_changes"
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
