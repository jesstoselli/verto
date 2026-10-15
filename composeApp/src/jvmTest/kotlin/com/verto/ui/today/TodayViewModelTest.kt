package com.verto.ui.today

import com.verto.data.ZoneProvider
import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.StepDraft
import com.verto.data.repository.TaskDraft
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.StepEnergy
import com.verto.ui.TestZone
import com.verto.ui.ViewModelTest
import com.verto.ui.uiAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class TodayViewModelTest : ViewModelTest() {

    private fun viewModel() = TodayViewModel(
        graph.routines,
        graph.tasks,
        graph.days,
        graph.ticker,
        ZoneProvider { TestZone },
        graph.settings,
        graph.clock,
    )

    private suspend fun lunchRoutine(): String {
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Almocei", LocalTime(12, 0), LocalTime(14, 0)))
        graph.routines.saveRoutine(
            RoutineDraft(
                id = null,
                name = "Caminhada",
                anchorId = anchorId,
                weekdays = DayOfWeek.entries.toSet(),
                stepsA = listOf(StepDraft(null, "20 min de caminhada", StepEnergy.Medium)),
                stepsC = listOf(StepDraft(null, "ir até a esquina", StepEnergy.Low)),
            ),
        )
        return anchorId
    }

    private suspend fun TodayViewModel.content(predicate: (TodayUiState.Content) -> Boolean = { true }) =
        state.first { it is TodayUiState.Content && predicate(it) } as TodayUiState.Content

    @Test
    fun lowEnergy_suggestsTheMinimalPlan() = runTest {
        lunchRoutine()
        graph.days.setEnergy(LocalDate(2026, 10, 15), EnergyLevel.Low)

        val content = viewModel().content { it.today.sections.isNotEmpty() }

        assertEquals(Plan.C, content.today.sections.single().routines.single().suggestedPlan)
    }

    @Test
    fun setEnergy_storesTheChosenLevel() = runTest {
        val vm = viewModel()
        assertEquals(EnergyLevel.Medium, vm.content().today.energy)

        vm.setEnergy(EnergyLevel.Low)

        vm.content { it.today.energy == EnergyLevel.Low }
    }

    @Test
    fun tapAnchor_marksItTriggered() = runTest {
        val anchorId = lunchRoutine()
        val vm = viewModel()
        assertTrue(vm.content { it.today.sections.isNotEmpty() }.today.sections.none { it.triggered })

        vm.tapAnchor(anchorId)

        vm.content { it.today.sections.single().triggered }
    }

    @Test
    fun dayRollover_movesToNewDay_andResetsPlanChoices() = runTest {
        lunchRoutine()
        graph.advanceTo(uiAt(16, 3, 59))
        val vm = viewModel()
        val before = vm.content { it.today.sections.isNotEmpty() }
        assertEquals(LocalDate(2026, 10, 15), before.today.day)
        vm.showPlan(before.today.sections.single().routines.single().routine.id, Plan.C)
        vm.content { it.shownPlans.isNotEmpty() }

        graph.advanceTo(uiAt(16, 4, 1))

        val after = vm.content { it.today.day == LocalDate(2026, 10, 16) }
        assertTrue(after.shownPlans.isEmpty())
    }

    @Test
    fun actingRightAfterWake_countsForTheNewDay_andLeavesYesterdayAlone() = runTest {
        lunchRoutine()
        val vm = viewModel()
        val card = vm.content { it.today.sections.isNotEmpty() }.today.sections.single().routines.single()
        val stepId = card.stepsC.single().step.id
        vm.toggleRoutineStep(stepId)
        graph.routines.observeDay(LocalDate(2026, 10, 15)).first { it.logs.isNotEmpty() }

        // The phone slept through the night: the clock moved but the ticker has not fired yet.
        graph.clock.now = uiAt(16, 7, 30)
        vm.toggleRoutineStep(stepId)

        graph.routines.observeDay(LocalDate(2026, 10, 16)).first { it.logs.isNotEmpty() }
        assertEquals(1, graph.routines.observeDay(LocalDate(2026, 10, 15)).first().logs.size)
        vm.content { it.today.day == LocalDate(2026, 10, 16) }
    }

    @Test
    fun resume_movesTheScreenToTheNewDay() = runTest {
        val vm = viewModel()
        vm.content()

        graph.clock.now = uiAt(16, 7, 30)
        vm.onResume()

        vm.content { it.today.day == LocalDate(2026, 10, 16) }
    }

    @Test
    fun completeTask_withOpenSteps_movesItToRecentlyDone() = runTest {
        val id = graph.tasks.save(
            TaskDraft(
                null,
                "encher lava-louças",
                pinnedToToday = true,
                steps = listOf(StepDraft(null, "copos", StepEnergy.Low), StepDraft(null, "pratos", StepEnergy.Low)),
            ),
        )
        val vm = viewModel()
        vm.content { it.today.pinned.isNotEmpty() }

        vm.completeTask(id)

        val after = vm.content { it.today.pinned.isEmpty() }
        assertEquals(listOf(id), after.today.recentlyDone.map { it.id })
    }

    @Test
    fun changingTheDayStart_movesHojeOnResume() = runTest {
        graph.advanceTo(uiAt(16, 2))
        val vm = viewModel()
        assertEquals(LocalDate(2026, 10, 15), vm.content().today.day)

        graph.settings.update { it.copy(dayStartsAt = LocalTime(0, 0)) }
        vm.onResume()

        vm.content { it.today.day == LocalDate(2026, 10, 16) }
    }
}
