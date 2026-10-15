package com.verto.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class SyncPhase { Idle, Running, Offline, SignedOut, SessionExpired }

data class SyncStatus(val phase: SyncPhase, val lastSyncedAt: Instant?, val someRejected: Boolean = false)

/**
 * Decides when to sync: right away, a few quiet seconds after local changes, and again with
 * backoff while offline. Runs one sync at a time; requests made meanwhile collapse into one.
 */
class SyncScheduler(
    private val sync: suspend () -> SyncOutcome,
    private val pendingChanges: Flow<Int>,
    private val lastSyncedAt: suspend () -> Instant?,
    private val scope: CoroutineScope,
    private val debounce: Duration = 3.seconds,
    private val backoff: List<Duration> = listOf(10.seconds, 30.seconds, 2.minutes, 10.minutes),
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val _status = MutableStateFlow(SyncStatus(SyncPhase.Idle, null))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var started = false
    private var retry: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val saved = lastSyncedAt()
            _status.update { if (it.lastSyncedAt == null) it.copy(lastSyncedAt = saved) else it }
        }
        scope.launch {
            // drop(1): the first value is what is already pending, covered by the sync at start.
            pendingChanges.drop(1).filter { it > 0 }.debounce(debounce).collect { requests.trySend(Unit) }
        }
        scope.launch {
            var failures = 0
            requests.trySend(Unit)
            for (request in requests) {
                retry?.cancel()
                _status.update { it.copy(phase = SyncPhase.Running) }
                when (val outcome = sync()) {
                    is SyncOutcome.Synced -> {
                        failures = 0
                        _status.value = SyncStatus(SyncPhase.Idle, outcome.at, outcome.someRejected)
                    }
                    SyncOutcome.SignedOut -> _status.update { it.copy(phase = SyncPhase.SignedOut) }
                    SyncOutcome.SessionExpired -> _status.update { it.copy(phase = SyncPhase.SessionExpired) }
                    is SyncOutcome.Failed -> {
                        _status.update { it.copy(phase = SyncPhase.Offline) }
                        val wait = backoff[minOf(failures, backoff.lastIndex)]
                        failures++
                        retry = scope.launch {
                            delay(wait)
                            requests.trySend(Unit)
                        }
                    }
                }
            }
        }
    }

    /** Asks for a sync; starts the scheduler if nothing has yet (e.g. right after login). */
    fun syncNow() {
        if (!started) start() else requests.trySend(Unit)
    }

    companion object {
        fun of(engine: SyncEngine, store: LocalStore, scope: CoroutineScope) =
            SyncScheduler({ engine.sync() }, store.observePendingCount(), { store.lastSyncedAt() }, scope)
    }
}
