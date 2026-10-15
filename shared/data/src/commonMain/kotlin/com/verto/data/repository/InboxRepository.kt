package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.InboxItemEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.domain.VertoSettings
import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import com.verto.domain.rules.InboxRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class InboxRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = store.db.inboxDao()

    fun observe(): Flow<List<InboxItem>> = dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: String): InboxItem? = dao.get(id)?.takeIf { it.deletedAt == null }?.toDomain()

    /** Returns null for blank text: a mis-tap on "Guardar" must not leave an empty card behind. */
    suspend fun capture(text: String, source: InboxSource): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val now = clock.now().toEpochMilliseconds()
        val id = ids.newId()
        store.write {
            put(InboxItemEntity(id, trimmed, now, source.wire(), InboxState.Raw.wire(), now, null))
        }
        return id
    }

    suspend fun setState(id: String, state: InboxState) {
        val now = clock.now().toEpochMilliseconds()
        store.write {
            val row = dao.get(id) ?: return@write
            put(row.copy(state = state.wire(), updatedAt = now))
        }
    }

    suspend fun incubateStale(settings: VertoSettings) {
        val now = clock.now()
        store.write {
            val rows = dao.active()
            val stale = InboxRules.toIncubate(rows.map { it.toDomain() }, now, settings.incubationAfter)
                .map { it.id }
                .toSet()
            rows.filter { it.id in stale }.forEach {
                put(it.copy(state = InboxState.Incubated.wire(), updatedAt = now.toEpochMilliseconds()))
            }
        }
    }
}
