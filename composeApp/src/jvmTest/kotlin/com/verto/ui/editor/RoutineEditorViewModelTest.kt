package com.verto.ui.editor

import com.verto.data.repository.AnchorDraft
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import com.verto.domain.model.Plan
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutineEditorViewModelTest : ViewModelTest() {

    private suspend fun anchor() = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(6, 0), LocalTime(9, 0)))

    private fun RoutineEditorViewModel.step(plan: Plan, title: String) {
        addStep(plan)
        val key = (if (plan == Plan.A) state.value.stepsA else state.value.stepsC).last().key
        setStepTitle(plan, key, title)
    }

    @Test
    fun save_withoutMinimalPlan_explainsWhatIsMissing() = runTest {
        anchor()
        val vm = RoutineEditorViewModel(RoutineEditorArgs(null, null), graph.routines, graph.inbox)
        vm.state.first { it.loaded && it.anchorId != null }
        vm.setName("Exercício")
        vm.step(Plan.A, "treino de 30 min")

        vm.save()

        assertEquals("O plano mínimo precisa de pelo menos um passo.", vm.state.value.message)
        assertTrue(graph.routines.observeRoutines().first().isEmpty())
    }

    @Test
    fun save_fromInbox_prefillsTheName_andMarksTheItemProcessed() = runTest {
        anchor()
        val inboxId = graph.inbox.capture("alongar de manhã", InboxSource.App)!!
        val vm = RoutineEditorViewModel(RoutineEditorArgs(null, inboxId), graph.routines, graph.inbox)
        val loaded = vm.state.first { it.loaded && it.anchorId != null }
        assertEquals("alongar de manhã", loaded.name)
        vm.step(Plan.A, "10 min de alongamento")
        vm.step(Plan.C, "1 alongamento")

        vm.save()

        vm.state.first { it.closed }
        assertEquals("alongar de manhã", graph.routines.observeRoutines().first().single().name)
        assertEquals(InboxState.Processed, graph.inbox.get(inboxId)?.state)
    }
}
