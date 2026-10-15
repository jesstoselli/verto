package com.verto.data.repository

import com.verto.data.sync.LocalStore
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.domain.model.InboxState
import com.verto.domain.rules.ResetRules
import kotlin.time.Clock

class ResetRepository(private val store: LocalStore, private val clock: Clock) {

    /** A clean day in one transaction: unpin everything, put raw braindumps aside. Nothing is deleted. */
    suspend fun freshStart() {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val tasks = store.db.taskDao().active()
            val inbox = store.db.inboxDao().active()
            val plan = ResetRules.plan(tasks.map { it.toDomain() }, inbox.map { it.toDomain() })
            val unpin = plan.tasksToUnpin.map { it.id }.toSet()
            val incubate = plan.inboxToIncubate.map { it.id }.toSet()
            tasks.filter { it.id in unpin }.forEach { put(it.copy(pinnedToToday = false, updatedAt = nowMs)) }
            inbox.filter { it.id in incubate }.forEach { put(it.copy(state = InboxState.Incubated.wire(), updatedAt = nowMs)) }
        }
    }
}
