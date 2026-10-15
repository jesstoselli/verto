package com.verto.ui

import com.verto.data.repository.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse

class AppViewModelTest : ViewModelTest() {

    private fun viewModel() = AppViewModel(graph.usage, graph.reset)

    @Test
    fun firstLaunch_goesStraightToTheApp() = runTest {
        val vm = viewModel()

        vm.onForeground()
        // Wait for the visit to be recorded: a write still running at tearDown would hit a closed database.
        while (graph.store.lastOpenedAt() == null) yield()

        assertFalse(vm.offerFreshStart.value)
    }

    @Test
    fun afterDaysAway_offersAFreshStart_andResetClearsToday() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.usage.checkIn()
        graph.tasks.save(TaskDraft(null, "Fazer IR", pinnedToToday = true))
        graph.clock.now = uiAt(15, 10)
        val vm = viewModel()

        vm.onForeground()
        vm.offerFreshStart.first { it }
        vm.startClean()

        assertFalse(vm.offerFreshStart.value)
        graph.tasks.observeTasks().first { tasks -> tasks.none { it.pinnedToToday } }
    }

    @Test
    fun seeingHowItWas_keepsEverything() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.usage.checkIn()
        graph.tasks.save(TaskDraft(null, "Fazer IR", pinnedToToday = true))
        graph.clock.now = uiAt(15, 10)
        val vm = viewModel()
        vm.onForeground()
        vm.offerFreshStart.first { it }

        vm.keepAsItWas()

        assertFalse(vm.offerFreshStart.value)
        graph.tasks.observeTasks().first { tasks -> tasks.single().pinnedToToday }
    }
}
