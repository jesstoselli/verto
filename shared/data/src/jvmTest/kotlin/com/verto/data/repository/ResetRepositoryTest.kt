package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.DueKind
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResetRepositoryTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun freshStart_unpinsAndIncubates_withoutDeletingAnything() = runTest {
        val due = LocalDate(2026, 10, 12)
        graph.tasks.save(TaskDraft(null, "IR", dueDate = due, dueKind = DueKind.Hard, pinnedToToday = true))
        graph.tasks.save(TaskDraft(null, "Ligar", pinnedToToday = true))
        val raw = graph.inbox.capture("ideia solta", InboxSource.App)!!
        val processed = graph.inbox.capture("já resolvida", InboxSource.App)!!
        graph.inbox.setState(processed, InboxState.Processed)
        graph.db.outboxDao().deleteSent(graph.db.outboxDao().all().map { it.seq })

        graph.reset.freshStart()

        val tasks = graph.tasks.observeTasks().first()
        assertEquals(2, tasks.size)
        assertTrue(tasks.none { it.pinnedToToday })
        assertEquals(due, tasks.single { it.title == "IR" }.dueDate)
        assertEquals(InboxState.Incubated, graph.inbox.get(raw)?.state)
        assertEquals(InboxState.Processed, graph.inbox.get(processed)?.state)
        val queued = graph.db.outboxDao().all()
        assertEquals(3, queued.size)
        assertTrue(queued.all { it.deletedAt == null })
    }
}
