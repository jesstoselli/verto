package com.verto.ui

import com.verto.data.IdGenerator
import com.verto.data.db.inMemoryVertoDatabase
import com.verto.data.repository.DayRepository
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.data.sync.LocalStore
import com.verto.data.settings.InMemorySettingsStore
import com.verto.data.settings.SettingsRepository
import com.verto.ui.common.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
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

fun uiAt(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime(2026, 10, day, hour, minute).toInstant(TestZone)

class ManualTicker(start: Instant) : Ticker {
    val now = MutableStateFlow(start)
    override fun ticks(): Flow<Instant> = now
}

class UiTestGraph(start: Instant = uiAt(15, 10)) {
    val clock = TestClock(start)
    val ids = SequentialIds()
    val settings = SettingsRepository(InMemorySettingsStore())
    val ticker = ManualTicker(start)
    val db = inMemoryVertoDatabase()
    val store = LocalStore(db)
    val inbox = InboxRepository(store, clock, ids)
    val tasks = TaskRepository(store, clock, ids)
    val routines = RoutineRepository(store, clock, ids, { TestZone }, settings)
    val days = DayRepository(store, clock, ids)

    /** Moves both the repositories' clock and the screen's ticker. */
    fun advanceTo(instant: Instant) {
        clock.now = instant
        ticker.now.value = instant
    }
}

abstract class ViewModelTest {
    protected val graph = UiTestGraph()

    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        graph.db.close()
    }
}
