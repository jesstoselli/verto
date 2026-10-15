package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.DueKind
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRepositoryTest {

    private val graph = DataGraph()
    private val tasks = graph.tasks

    @AfterTest
    fun close() = graph.db.close()

    private fun step(title: String, id: String? = null) = StepDraft(id, title, StepEnergy.Low)

    private suspend fun task(id: String) = tasks.observeTasks().first().single { it.id == id }

    @Test
    fun save_newTask_storesStepsInOrder() = runTest {
        val id = tasks.save(TaskDraft(null, "Fazer IR", steps = listOf(step("abrir site"), step("baixar programa"))))

        val draft = tasks.draftFor(id)!!
        assertEquals(listOf("abrir site", "baixar programa"), draft.steps.map { it.title })
    }

    @Test
    fun save_withoutDate_dropsTheDueKind() = runTest {
        val id = tasks.save(TaskDraft(null, "Ligar", dueKind = DueKind.Hard))

        assertNull(task(id).dueKind)
    }

    @Test
    fun toggleStep_lastStep_finishesTask_andUndoReopensIt() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", steps = listOf(step("abrir site"))))
        val stepId = tasks.draftFor(id)!!.steps.single().id!!

        tasks.toggleStep(stepId)
        assertNotNull(task(id).doneAt)

        tasks.toggleStep(stepId)
        assertNull(task(id).doneAt)
    }

    @Test
    fun save_removingLastOpenStep_finishesTask() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", steps = listOf(step("abrir site"), step("enviar"))))
        val (first, _) = tasks.draftFor(id)!!.steps
        tasks.toggleStep(first.id!!)

        tasks.save(TaskDraft(id, "IR", steps = listOf(first)))

        assertNotNull(task(id).doneAt)
    }

    @Test
    fun save_addingStepToDoneTask_reopensIt() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", steps = listOf(step("abrir site"))))
        val only = tasks.draftFor(id)!!.steps.single()
        tasks.toggleStep(only.id!!)

        tasks.save(TaskDraft(id, "IR", steps = listOf(only, step("enviar"))))

        assertNull(task(id).doneAt)
    }

    @Test
    fun delete_softDeletesTaskAndSteps() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", dueDate = LocalDate(2026, 10, 20), steps = listOf(step("abrir site"))))

        tasks.delete(id)

        assertTrue(tasks.observeTasks().first().isEmpty())
        assertTrue(tasks.observeAllSteps().first().isEmpty())
        assertTrue(graph.db.outboxDao().all().all { it.deletedAt != null })
    }
}
