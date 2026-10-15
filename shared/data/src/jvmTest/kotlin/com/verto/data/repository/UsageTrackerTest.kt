package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.data.localAt
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageTrackerTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun firstLaunch_offersNothing() = runTest {
        assertFalse(graph.usage.checkIn())
    }

    @Test
    fun aShortBreak_offersNothing() = runTest {
        graph.clock.now = localAt(10, 10)
        graph.usage.checkIn()
        graph.clock.now = localAt(13, 9)

        assertFalse(graph.usage.checkIn())
    }

    @Test
    fun returningAfterTheThreshold_offersUntilAnswered() = runTest {
        graph.clock.now = localAt(10, 10)
        graph.usage.checkIn()
        graph.clock.now = localAt(15, 10)

        assertTrue(graph.usage.checkIn())
        // Killed or backed out before answering: the next open still offers it.
        assertTrue(graph.usage.checkIn())

        graph.usage.answered()

        assertFalse(graph.usage.checkIn())
    }
}
