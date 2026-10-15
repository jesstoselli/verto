package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.data.localAt
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboxRepositoryTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun capture_trimsAndStoresARawItem() = runTest {
        graph.inbox.capture("  comprar pão  ", InboxSource.App)

        val item = graph.inbox.observe().first().single()
        assertEquals("comprar pão", item.text)
        assertEquals(InboxState.Raw, item.state)
    }

    @Test
    fun capture_blank_isIgnored() = runTest {
        assertNull(graph.inbox.capture("   ", InboxSource.App))

        assertTrue(graph.inbox.observe().first().isEmpty())
        assertTrue(graph.db.outboxDao().all().isEmpty())
    }

    @Test
    fun setState_keepsTheItemWithItsNewState() = runTest {
        val id = graph.inbox.capture("ideia", InboxSource.App)!!

        graph.inbox.setState(id, InboxState.Discarded)

        assertEquals(InboxState.Discarded, graph.inbox.get(id)?.state)
    }

    @Test
    fun incubateStale_movesOnlyOldRawItems() = runTest {
        graph.clock.now = localAt(11, 10)
        val old = graph.inbox.capture("velho", InboxSource.App)!!
        graph.clock.now = localAt(14, 9)
        val fresh = graph.inbox.capture("novo", InboxSource.App)!!
        graph.clock.now = localAt(14, 10)

        graph.inbox.incubateStale(graph.settings)

        assertEquals(InboxState.Incubated, graph.inbox.get(old)?.state)
        assertEquals(InboxState.Raw, graph.inbox.get(fresh)?.state)
    }

    @Test
    fun restoredItem_isNotIncubatedAgainRightAway() = runTest {
        graph.clock.now = localAt(10, 10)
        val id = graph.inbox.capture("ideia", InboxSource.App)!!
        graph.clock.now = localAt(14, 10)
        graph.inbox.incubateStale(graph.settings)
        graph.inbox.setState(id, InboxState.Raw)
        graph.clock.now = localAt(15, 10)

        graph.inbox.incubateStale(graph.settings)

        assertEquals(InboxState.Raw, graph.inbox.get(id)?.state)
    }
}
