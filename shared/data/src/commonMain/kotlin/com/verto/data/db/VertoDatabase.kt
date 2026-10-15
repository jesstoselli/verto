package com.verto.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        InboxItemEntity::class,
        TaskEntity::class,
        StepEntity::class,
        AnchorEntity::class,
        AnchorEventEntity::class,
        RoutineEntity::class,
        RoutineLogEntity::class,
        DayEnergyEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
    // Phones already hold real data in version 1: only additive auto-migrations, never a destructive fallback.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(VertoDatabaseConstructor::class)
abstract class VertoDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao
    abstract fun taskDao(): TaskDao
    abstract fun stepDao(): StepDao
    abstract fun anchorDao(): AnchorDao
    abstract fun routineDao(): RoutineDao
    abstract fun dayEnergyDao(): DayEnergyDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao
}

// Room's KSP processor generates the actual object for every target.
@Suppress("KotlinNoActualForExpect")
expect object VertoDatabaseConstructor : RoomDatabaseConstructor<VertoDatabase> {
    override fun initialize(): VertoDatabase
}
