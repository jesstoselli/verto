package com.verto.data

import com.verto.data.db.inMemoryVertoDatabase
import com.verto.data.repository.DayRepository
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.data.sync.LocalStore
import com.verto.domain.VertoSettings
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

class TestClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

val TestZone: TimeZone = TimeZone.of("America/Sao_Paulo")

fun localAt(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime(2026, 10, day, hour, minute).toInstant(TestZone)

class DataGraph(start: Instant = localAt(15, 10)) {
    val clock = TestClock(start)
    val ids = SequentialIds()
    val settings = VertoSettings()
    val db = inMemoryVertoDatabase()
    val store = LocalStore(db)
    val inbox = InboxRepository(store, clock, ids)
    val tasks = TaskRepository(store, clock, ids)
    val routines = RoutineRepository(store, clock, ids, { TestZone }, settings)
    val days = DayRepository(store, clock, ids)
}
