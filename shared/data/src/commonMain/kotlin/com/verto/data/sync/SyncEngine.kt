package com.verto.data.sync

import com.verto.data.db.OutboxEntity
import com.verto.data.remote.AuthSession
import com.verto.data.remote.ChangeIn
import com.verto.data.remote.ChangeOut
import com.verto.data.remote.SessionExpiredException
import com.verto.data.remote.SignedOutException
import com.verto.data.remote.VertoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface SyncOutcome {
    data class Synced(val at: Instant, val someRejected: Boolean) : SyncOutcome
    data object SignedOut : SyncOutcome
    data object SessionExpired : SyncOutcome
    data class Failed(val cause: Throwable) : SyncOutcome
}

/**
 * Push first, then pull: a local change is on the server before the pull runs, so the
 * "last write to reach the server wins" rule never undoes something done on this phone.
 */
class SyncEngine(
    private val store: LocalStore,
    private val api: VertoApi,
    private val session: AuthSession,
    private val clock: Clock,
    private val pushBatch: Int = 200,
    private val pullPage: Int = 500,
) {
    private val running = Mutex()

    suspend fun sync(): SyncOutcome = running.withLock {
        try {
            val someRejected = push()
            pull()
            val now = clock.now()
            store.setLastSyncedAt(now)
            SyncOutcome.Synced(now, someRejected)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SignedOutException) {
            SyncOutcome.SignedOut
        } catch (_: SessionExpiredException) {
            SyncOutcome.SessionExpired
        } catch (e: Exception) {
            SyncOutcome.Failed(e)
        }
    }

    private suspend fun push(): Boolean {
        var someRejected = false
        while (true) {
            val batch = store.pendingBatch(pushBatch)
            if (batch.isEmpty()) return someRejected
            val result = session.authorized { token -> api.push(token, batch.map { it.toChangeIn() }) }
            someRejected = someRejected || result.rejected.isNotEmpty()
            // Refused entries leave too: the server would refuse them the same way forever and block
            // the rest. The record itself stays on this phone untouched.
            store.markSent(batch.map { it.seq })
        }
    }

    private suspend fun pull() {
        var since = store.cursor()
        do {
            val page = session.authorized { token -> api.pull(token, since, pullPage) }
            store.applyRemote(page.changes.map { it.toRemoteChange() }, page.cursor, clock.now().toEpochMilliseconds())
            since = page.cursor
        } while (page.hasMore)
    }

    private fun OutboxEntity.toChangeIn() = ChangeIn(
        entityType = entityType,
        id = entityId,
        payload = Json.parseToJsonElement(payload).withUpdatedAt(clientUpdatedAt),
        deletedAt = deletedAt?.iso(),
        clientUpdatedAt = clientUpdatedAt.iso(),
    )

    // Entries queued before payloads carried updatedAt (DB v1) take it from the outbox row,
    // or the first pull-back would rewrite every old record's edit time to the moment of the sync.
    private fun JsonElement.withUpdatedAt(clientUpdatedAt: Long): JsonElement {
        if (this !is JsonObject || "updatedAt" in this) return this
        return JsonObject(this + ("updatedAt" to JsonPrimitive(clientUpdatedAt.iso())))
    }

    private fun ChangeOut.toRemoteChange() = RemoteChange(
        entityType = entityType,
        id = id,
        payload = payload.toString(),
        deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilliseconds() },
    )
}
