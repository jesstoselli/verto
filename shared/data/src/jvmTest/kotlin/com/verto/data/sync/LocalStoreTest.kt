package com.verto.data.sync

import com.verto.data.db.InboxItemEntity
import com.verto.data.db.SyncStateEntity
import com.verto.data.db.TaskEntity
import com.verto.data.db.inMemoryVertoDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString

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

    private fun remoteInbox(id: String, text: String, deletedAt: Long? = null) = RemoteChange(
        entityType = "inbox_item",
        id = id,
        payload = """{"text":"$text","createdAt":"2026-10-15T12:00:00Z","source":"app","state":"raw","updatedAt":"2026-10-15T12:00:00Z"}""",
        deletedAt = deletedAt,
    )

    @Test
    fun markSent_keepsAnEntryRewrittenAfterItWasRead() = runTest {
        store.write { put(inbox("i1", "antes", 1_000)) }
        val sent = store.pendingBatch(10)

        store.write { put(inbox("i1", "depois", 2_000)) }
        store.markSent(sent.map { it.seq })

        val left = db.outboxDao().all().single()
        assertTrue("depois" in left.payload)
    }

    @Test
    fun applyRemote_writesRowsWithoutQueueingThem() = runTest {
        store.applyRemote(listOf(remoteInbox("r1", "do outro celular")), cursor = 7, receivedAt = 0)

        assertEquals("do outro celular", db.inboxDao().get("r1")?.text)
        assertTrue(db.outboxDao().all().isEmpty())
        assertEquals(7, store.cursor())
    }

    @Test
    fun applyRemote_skipsARecordWithALocalChangePending() = runTest {
        store.write { put(inbox("i1", "versão daqui", 1_000)) }

        store.applyRemote(listOf(remoteInbox("i1", "versão do servidor")), cursor = 3, receivedAt = 0)

        assertEquals("versão daqui", db.inboxDao().get("i1")?.text)
        assertEquals(3, store.cursor())
    }

    @Test
    fun applyRemote_appliesTombstones() = runTest {
        store.applyRemote(listOf(remoteInbox("r1", "vai sumir")), cursor = 1, receivedAt = 0)

        store.applyRemote(listOf(remoteInbox("r1", "vai sumir", deletedAt = 5_000)), cursor = 2, receivedAt = 0)

        assertEquals(5_000, db.inboxDao().get("r1")?.deletedAt)
        assertTrue(db.inboxDao().active().isEmpty())
    }

    @Test
    fun applyRemote_skipsWhatItCannotRead_andStillMovesTheCursor() = runTest {
        store.applyRemote(listOf(RemoteChange("habit", "h1", "{}", null)), cursor = 9, receivedAt = 0)

        assertEquals(9, store.cursor())
    }

    @Test
    fun lastSyncedAt_isKeptAcrossReads() = runTest {
        assertEquals(null, store.lastSyncedAt())

        store.setLastSyncedAt(Instant.fromEpochMilliseconds(1_700_000_000_000))

        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000), store.lastSyncedAt())
    }

    @Test
    fun observePendingCount_followsTheOutbox() = runTest {
        assertEquals(0, store.observePendingCount().first())

        store.write { put(TaskEntity("t1", "IR", null, null, null, false, null, 1_000, null)) }

        assertEquals(1, store.observePendingCount().first())
    }

    @Test
    fun applyRemote_minimalTombstone_deletesById() = runTest {
        store.applyRemote(listOf(remoteInbox("r1", "vai sumir")), cursor = 1, receivedAt = 0)

        store.applyRemote(listOf(RemoteChange("inbox_item", "r1", "{}", 5_000)), cursor = 2, receivedAt = 0)

        assertEquals(5_000, db.inboxDao().get("r1")?.deletedAt)
    }

    @Test
    fun applyRemote_unreadableRecord_isParkedNotLost() = runTest {
        store.applyRemote(listOf(RemoteChange("habit", "h1", "{}", null)), cursor = 1, receivedAt = 0)
        store.applyRemote(emptyList(), cursor = 2, receivedAt = 0)

        assertEquals(listOf("h1"), store.parked().map { it.id })
    }

    @Test
    fun applyRemote_retriesParkedRecords_onceTheyCanBeRead() = runTest {
        // As if an older build had parked a record it could not read yet.
        db.syncStateDao().put(SyncStateEntity(LocalStore.PARKED, SyncJson.encodeToString(listOf(remoteInbox("r1", "voltou")))))

        store.applyRemote(emptyList(), cursor = 3, receivedAt = 0)

        assertEquals("voltou", db.inboxDao().get("r1")?.text)
        assertTrue(store.parked().isEmpty())
    }
}
