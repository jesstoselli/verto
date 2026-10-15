package com.verto.ui.inbox

import com.verto.domain.model.InboxSource
import com.verto.ui.ViewModelTest
import com.verto.ui.uiAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InboxViewModelTest : ViewModelTest() {

    private fun viewModel() = InboxViewModel(graph.inbox, graph.tasks, graph.settings)

    @Test
    fun capture_showsTheItemAndClearsTheField() = runTest {
        val vm = viewModel()

        vm.onDraftChange("comprar pão")
        vm.capture()

        val state = vm.state.first { it.raw.isNotEmpty() }
        assertEquals("comprar pão", state.raw.single().text)
        assertEquals("", state.draft)
    }

    @Test
    fun toTask_createsATaskAndLeavesTheRawList() = runTest {
        graph.inbox.capture("fazer IR", InboxSource.App)
        val vm = viewModel()
        val item = vm.state.first { it.raw.isNotEmpty() }.raw.single()

        vm.toTask(item)

        vm.state.first { it.raw.isEmpty() }
        assertEquals("fazer IR", graph.tasks.observeTasks().first().single().title)
    }

    @Test
    fun opening_incubatesStaleItemsSilently() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.inbox.capture("ideia antiga", InboxSource.App)
        graph.clock.now = uiAt(15, 10)

        val state = viewModel().state.first { it.incubated.isNotEmpty() }

        assertEquals("ideia antiga", state.incubated.single().text)
        assertEquals(emptyList(), state.raw)
    }
}
