package com.verto.ui

import com.verto.data.repository.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppViewModelTest : ViewModelTest() {

    private fun viewModel() = AppViewModel(graph.usage, graph.reset)

    @Test
    fun startsChecking_soNoListShowsBeforeTheAnswer() {
        assertEquals(FreshStart.Checking, viewModel().freshStart.value)
    }

    @Test
    fun firstLaunch_goesStraightToTheApp() = runTest {
        val vm = viewModel()

        vm.onForeground()

        vm.freshStart.first { it == FreshStart.None }
    }

    @Test
    fun afterDaysAway_offersAFreshStart_andResetClearsToday() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.usage.checkIn()
        graph.tasks.save(TaskDraft(null, "Fazer IR", pinnedToToday = true))
        graph.clock.now = uiAt(15, 10)
        val vm = viewModel()

        vm.onForeground()
        vm.freshStart.first { it == FreshStart.Offer }
        vm.startClean()

        assertEquals(FreshStart.None, vm.freshStart.value)
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
        vm.freshStart.first { it == FreshStart.Offer }

        vm.keepAsItWas()

        assertEquals(FreshStart.None, vm.freshStart.value)
        graph.tasks.observeTasks().first { tasks -> tasks.single().pinnedToToday }
    }

    @Test
    fun theOffer_survivesTheAppBeingKilledBeforeAnswering() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.usage.checkIn()
        graph.clock.now = uiAt(15, 10)
        val before = viewModel()
        before.onForeground()
        before.freshStart.first { it == FreshStart.Offer }

        val after = viewModel()
        after.onForeground()

        after.freshStart.first { it == FreshStart.Offer }
    }
}
