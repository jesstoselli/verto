package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepRepositoryTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    private fun step(title: String) = StepDraft(null, title, StepEnergy.Medium)

    @Test
    fun split_taskStepInTheMiddle_keepsTheOrder() = runTest {
        val taskId = graph.tasks.save(TaskDraft(null, "Fazer IR", steps = listOf(step("abrir site"), step("declarar tudo"), step("enviar"))))
        val middle = graph.tasks.draftFor(taskId)!!.steps[1].id!!
        graph.db.outboxDao().deleteSent(graph.db.outboxDao().all().map { it.seq })

        graph.steps.split(middle, listOf(" baixar programa ", "", "importar recibos"))

        assertEquals(
            listOf("abrir site", "baixar programa", "importar recibos", "enviar"),
            graph.tasks.draftFor(taskId)!!.steps.map { it.title },
        )
        assertNull(graph.tasks.observeTasks().first().single().doneAt)
        assertTrue(graph.db.outboxDao().all().any { it.entityId == middle && it.deletedAt != null })
    }

    @Test
    fun split_routineStep_staysInItsPlan() = runTest {
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(7, 0), LocalTime(9, 0)))
        val routineId = graph.routines.saveRoutine(
            RoutineDraft(null, "Alongar", anchorId, DayOfWeek.entries.toSet(), listOf(step("20 min")), listOf(step("1 min"), step("respirar"))),
        )
        val firstC = graph.db.stepDao().forRoutine(routineId).first { it.plan == "c" }.id

        val created = graph.steps.split(firstC, listOf("levantar os braços"))

        val rows = graph.db.stepDao().forRoutine(routineId)
        assertEquals(listOf("levantar os braços", "respirar"), rows.filter { it.plan == "c" }.map { it.title })
        assertEquals(listOf("20 min"), rows.filter { it.plan == "a" }.map { it.title })
        assertEquals("low", rows.single { it.id == created.single() }.energy)
    }

    @Test
    fun split_withOnlyBlankPieces_changesNothing() = runTest {
        val taskId = graph.tasks.save(TaskDraft(null, "Ligar", steps = listOf(step("achar número"))))
        val only = graph.tasks.draftFor(taskId)!!.steps.single().id!!

        assertTrue(graph.steps.split(only, listOf(" ", "")).isEmpty())
        assertEquals(listOf("achar número"), graph.tasks.draftFor(taskId)!!.steps.map { it.title })
    }
}
