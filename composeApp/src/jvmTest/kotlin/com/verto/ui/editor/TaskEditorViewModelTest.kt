package com.verto.ui.editor

import com.verto.data.repository.StepDraft
import com.verto.data.repository.TaskDraft
import com.verto.domain.model.StepEnergy
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TaskEditorViewModelTest : ViewModelTest() {

    @Test
    fun newTask_save_storesNonBlankStepsInTheChosenOrder() = runTest {
        val vm = TaskEditorViewModel(null, graph.tasks)
        vm.setTitle("Fazer IR")
        repeat(3) { vm.addStep() }
        val (first, second, third) = vm.state.value.steps.map { it.key }
        vm.setStepTitle(first, "baixar programa")
        vm.setStepTitle(second, "   ")
        vm.setStepTitle(third, "abrir site")
        vm.moveStep(third, -2)

        vm.save()

        vm.state.first { it.closed }
        val id = graph.tasks.observeTasks().first().single().id
        assertEquals(listOf("abrir site", "baixar programa"), graph.tasks.draftFor(id)!!.steps.map { it.title })
    }

    @Test
    fun existingTask_loadsItsDraft() = runTest {
        val id = graph.tasks.save(TaskDraft(null, "Ligar pro dentista", steps = listOf(StepDraft(null, "achar número", StepEnergy.Low))))

        val state = TaskEditorViewModel(id, graph.tasks).state.first { it.loaded }

        assertEquals("Ligar pro dentista", state.title)
        assertEquals(listOf("achar número"), state.steps.map { it.title })
    }

    @Test
    fun blankTitle_cannotBeSaved() = runTest {
        val vm = TaskEditorViewModel(null, graph.tasks)

        vm.setTitle("   ")
        vm.save()

        assertFalse(vm.state.value.canSave)
        assertEquals(emptyList(), graph.tasks.observeTasks().first())
    }

    @Test
    fun save_tappedTwice_createsOneTask() = runTest {
        val vm = TaskEditorViewModel(null, graph.tasks)
        vm.setTitle("Fazer IR")

        vm.save()
        vm.save()

        vm.state.first { it.closed }
        // Writes are serialized, so once this one lands any second save has landed too.
        graph.tasks.save(TaskDraft(null, "sentinela"))
        assertEquals(1, graph.tasks.observeTasks().first().count { it.title == "Fazer IR" })
    }
}
