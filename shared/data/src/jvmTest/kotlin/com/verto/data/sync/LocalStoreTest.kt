package com.verto.data.sync

import com.verto.data.db.InboxItemEntity
import com.verto.data.db.inMemoryVertoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalStoreTest {

    private val db = inMemoryVertoDatabase()
    private val store = LocalStore(db)

    @AfterTest
    fun close() = db.close()

    private fun inbox(id: String, text: String, updatedAt: Long) =
        InboxItemEntity(id, text, createdAt = 1_000, source = "app", state = "raw", updatedAt = updatedAt, deletedAt = null)

    @Test
    fun put_writesTheRowAndAnOutboxEntry() = runTest {
        store.write { put(inbox("i1", "comprar pão", 1_000)) }

        assertEquals("comprar pão", db.inboxDao().get("i1")?.text)
        val entry = db.outboxDao().all().single()
        assertEquals("inbox_item", entry.entityType)
        assertEquals("i1", entry.entityId)
        assertEquals(1_000, entry.clientUpdatedAt)
    }

    @Test
    fun rewritingARecord_coalescesIntoOneOutboxEntry() = runTest {
        store.write { put(inbox("i1", "antes", 1_000)) }
        store.write { put(inbox("i1", "depois", 2_000)) }

        val entry = db.outboxDao().all().single()
        assertTrue(entry.payload.contains("\"text\":\"depois\""))
        assertEquals(2_000, entry.clientUpdatedAt)
    }

    @Test
    fun failedWrite_rollsBackRowAndOutbox() = runTest {
        assertFailsWith<IllegalStateException> {
            store.write {
                put(inbox("i1", "some", 1_000))
                error("boom")
            }
        }

        assertNull(db.inboxDao().get("i1"))
        assertTrue(db.outboxDao().all().isEmpty())
    }
}
