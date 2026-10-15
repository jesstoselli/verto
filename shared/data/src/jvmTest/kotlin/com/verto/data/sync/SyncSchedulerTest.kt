package com.verto.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncSchedulerTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val pending = MutableStateFlow(0)
    private val outcomes = ArrayDeque<SyncOutcome>()
    private var calls = 0

    private suspend fun fakeSync(): SyncOutcome {
        calls++
        return outcomes.removeFirstOrNull() ?: SyncOutcome.Synced(at, someRejected = false)
    }

    private fun kotlinx.coroutines.test.TestScope.scheduler() =
        SyncScheduler(::fakeSync, pending, { null }, backgroundScope)

    @Test
    fun start_syncsRightAway() = runTest {
        val scheduler = scheduler()

        scheduler.start()
        runCurrent()

        assertEquals(1, calls)
        assertEquals(SyncStatus(SyncPhase.Idle, at), scheduler.status.value)
    }

    @Test
    fun aLocalChange_syncsAfterTheQuietPeriod() = runTest {
        scheduler().start()
        runCurrent()

        pending.value = 1
        advanceTimeBy(2.9.seconds)
        runCurrent()
        assertEquals(1, calls)

        advanceTimeBy(0.2.seconds)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun failures_retryWithBackoff_untilItWorks() = runTest {
        outcomes += SyncOutcome.Failed(IOException("sem rede"))
        outcomes += SyncOutcome.Failed(IOException("sem rede"))
        val scheduler = scheduler()

        scheduler.start()
        runCurrent()
        assertEquals(SyncPhase.Offline, scheduler.status.value.phase)

        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(2, calls)

        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(3, calls)
        assertEquals(SyncPhase.Idle, scheduler.status.value.phase)
    }

    @Test
    fun signedOut_waitsForHerInsteadOfRetrying() = runTest {
        outcomes += SyncOutcome.SignedOut
        val scheduler = scheduler()

        scheduler.start()
        advanceTimeBy(20.seconds)
        runCurrent()

        assertEquals(1, calls)
        assertEquals(SyncPhase.SignedOut, scheduler.status.value.phase)
    }

    @Test
    fun syncNow_beforeStart_startsIt() = runTest {
        scheduler().syncNow()
        runCurrent()

        assertEquals(1, calls)
    }

    @Test
    fun syncNow_runsAnotherRound() = runTest {
        val scheduler = scheduler()
        scheduler.start()
        runCurrent()

        scheduler.syncNow()
        runCurrent()

        assertEquals(2, calls)
    }
}
