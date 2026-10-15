package com.verto.ui.today

import com.verto.data.ZoneProvider
import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.StepDraft
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.StepEnergy
import com.verto.ui.TestZone
import com.verto.ui.ViewModelTest
import com.verto.ui.uiAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayViewModelTest : ViewModelTest() {

    private fun viewModel() = TodayViewModel(
        graph.routines,
        graph.tasks,
        graph.days,
        graph.ticker,
        ZoneProvider { TestZone },
        graph.settings,
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
    fun cycleEnergy_movesFromTheNeutralDefault() = runTest {
        val vm = viewModel()
        assertEquals(EnergyLevel.Medium, vm.content().today.energy)

        vm.cycleEnergy()

        vm.content { it.today.energy == EnergyLevel.High }
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
}
