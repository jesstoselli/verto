package com.verto.ui.plan

import com.verto.data.ZoneProvider
import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.StepDraft
import com.verto.data.repository.TaskDraft
import com.verto.domain.model.StepEnergy
import com.verto.ui.TestZone
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanViewModelTest : ViewModelTest() {

    private fun viewModel() = PlanViewModel(graph.tasks, graph.routines, graph.ticker, ZoneProvider { TestZone }, graph.settings)

    private suspend fun oneOfEach() {
        graph.tasks.save(TaskDraft(null, "Comprar ração"))
        val done = graph.tasks.save(TaskDraft(null, "Fazer IR"))
        graph.tasks.setDone(done, true)
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(7, 0), LocalTime(9, 0)))
        graph.routines.saveRoutine(
            RoutineDraft(
                null,
                "Alongar",
                anchorId,
                DayOfWeek.entries.toSet(),
                listOf(StepDraft(null, "5 min", StepEnergy.Low)),
                listOf(StepDraft(null, "1 min", StepEnergy.Low)),
            ),
        )
    }

    @Test
    fun initialTab_isTasks() = runTest {
        // Wait for a state built from the database: ending on the initial value leaves queries running at tearDown.
        assertEquals(PlanTab.Tasks, viewModel().state.first { it.today != null }.selectedTab)
    }

    @Test
    fun selectTab_updatesOnlyPresentationState() = runTest {
        oneOfEach()
        val vm = viewModel()
        vm.state.first { it.taskCount == 2 }
        val queued = graph.db.outboxDao().all().size

        vm.selectTab(PlanTab.Routines)

        vm.state.first { it.selectedTab == PlanTab.Routines }
        assertEquals(queued, graph.db.outboxDao().all().size)
    }

    @Test
    fun counts_includeOpenAndDoneTasks_routinesAndAnchors() = runTest {
        oneOfEach()

        val state = viewModel().state.first { it.taskCount == 2 && it.routineCount == 1 && it.anchorCount == 1 }

        assertEquals(1, state.openTasks.size)
        assertEquals(1, state.doneTasks.size)
    }

    @Test
    fun togglingDone_keepsTheSelectedTab() = runTest {
        val vm = viewModel()
        vm.selectTab(PlanTab.Anchors)

        vm.toggleShowDone()

        assertEquals(PlanTab.Anchors, vm.state.first { it.showDone }.selectedTab)
    }

    @Test
    fun refreshedRepositoryContent_keepsTheSelectedTab() = runTest {
        val vm = viewModel()
        vm.selectTab(PlanTab.Anchors)
        graph.tasks.save(TaskDraft(null, "Comprar ração"))

        vm.state.first { it.taskCount == 1 }
        assertEquals(PlanTab.Anchors, vm.state.value.selectedTab)
    }
}
