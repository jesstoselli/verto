package com.verto.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_items WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items WHERE deletedAt IS NULL")
    suspend fun active(): List<InboxItemEntity>

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun get(id: String): InboxItemEntity?

    @Upsert
    suspend fun upsert(entity: InboxItemEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(entity: TaskEntity)
}

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE deletedAt IS NULL ORDER BY position")
    fun observeActive(): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE id = :id")
    suspend fun get(id: String): StepEntity?

    @Query("SELECT * FROM steps WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY position")
    suspend fun forTask(taskId: String): List<StepEntity>

    @Query("SELECT * FROM steps WHERE routineId = :routineId AND deletedAt IS NULL ORDER BY plan, position")
    suspend fun forRoutine(routineId: String): List<StepEntity>

    @Upsert
    suspend fun upsert(entity: StepEntity)
}

@Dao
interface AnchorDao {
    @Query("SELECT * FROM anchors WHERE deletedAt IS NULL ORDER BY windowStart, name")
    fun observeAnchors(): Flow<List<AnchorEntity>>

    @Query("SELECT * FROM anchors WHERE deletedAt IS NULL")
    suspend fun activeAnchors(): List<AnchorEntity>

    @Query("SELECT * FROM anchors WHERE id = :id")
    suspend fun getAnchor(id: String): AnchorEntity?

    @Query("SELECT * FROM anchor_events WHERE day = :day AND deletedAt IS NULL")
    fun observeEventsOn(day: String): Flow<List<AnchorEventEntity>>

    @Query("SELECT * FROM anchor_events WHERE day = :day AND deletedAt IS NULL")
    suspend fun eventsOn(day: String): List<AnchorEventEntity>

    @Upsert
    suspend fun upsertAnchor(entity: AnchorEntity)

    @Upsert
    suspend fun upsertEvent(entity: AnchorEventEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE anchorId = :anchorId AND deletedAt IS NULL")
    suspend fun forAnchor(anchorId: String): List<RoutineEntity>

    @Query("SELECT * FROM routine_logs WHERE day = :day AND deletedAt IS NULL")
    fun observeLogsOn(day: String): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM routine_logs WHERE routineId = :routineId AND day = :day AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun logFor(routineId: String, day: String): RoutineLogEntity?

    @Upsert
    suspend fun upsertRoutine(entity: RoutineEntity)

    @Upsert
    suspend fun upsertLog(entity: RoutineLogEntity)
}

@Dao
interface DayEnergyDao {
    @Query("SELECT * FROM day_energy WHERE day = :day AND deletedAt IS NULL ORDER BY setAt DESC LIMIT 1")
    fun observeLatest(day: String): Flow<DayEnergyEntity?>

    @Query("SELECT * FROM day_energy WHERE day = :day AND deletedAt IS NULL ORDER BY setAt DESC LIMIT 1")
    suspend fun latest(day: String): DayEnergyEntity?

    @Upsert
    suspend fun upsert(entity: DayEnergyEntity)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY seq")
    suspend fun all(): List<OutboxEntity>
}
