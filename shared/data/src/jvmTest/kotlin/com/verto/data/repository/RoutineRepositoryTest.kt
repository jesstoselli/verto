package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.data.localAt
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.Plan
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.StepOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineRepositoryTest {

    private val graph = DataGraph()
    private val routines = graph.routines
    private val oct15 = LocalDate(2026, 10, 15)

    @AfterTest
    fun close() = graph.db.close()

    private suspend fun anchor(name: String, start: Int) =
        routines.saveAnchor(AnchorDraft(null, name, LocalTime(start, 0), LocalTime(start + 2, 0)))

    private suspend fun exercise(anchorId: String) = routines.saveRoutine(
        RoutineDraft(
            id = null,
            name = "Exercício",
            anchorId = anchorId,
            weekdays = DayOfWeek.entries.toSet(),
            stepsA = listOf(StepDraft(null, "treino", StepEnergy.Medium)),
            stepsC = listOf(StepDraft(null, "alongar", StepEnergy.Low)),
        ),
    )

    private suspend fun stepId(routineId: String, plan: Plan) = graph.tasks.observeAllSteps().first()
        .single { it.owner == StepOwner.OfRoutine(routineId, plan) }.id

    private suspend fun outcome(routineId: String) =
        routines.observeDay(oct15).first().logs.singleOrNull { it.routineId == routineId }?.outcome

    @Test
    fun saveRoutine_roundTripsThroughTheDraft() = runTest {
        val routineId = exercise(anchor("Acordei", 6))

        val draft = routines.routineDraft(routineId)!!
        assertEquals(listOf("treino"), draft.stepsA.map { it.title })
        assertEquals(listOf("alongar"), draft.stepsC.map { it.title })
    }

    @Test
    fun tapAnchor_recordsOnlyOneEventPerDay() = runTest {
        val anchorId = anchor("Almocei", 12)

        routines.tapAnchor(anchorId, oct15)
        routines.tapAnchor(anchorId, oct15)

        val event = routines.observeDay(oct15).first().events.single()
        assertEquals(AnchorTrigger.Tap, event.source)
    }

    @Test
    fun recordOpenedWindows_recordsOnlyOpenedUntriggeredWindows() = runTest {
        val breakfast = anchor("Acordei", 6)
        anchor("Jantei", 19)
        graph.clock.now = localAt(15, 12, 30)

        routines.recordOpenedWindows(oct15)

        val events = routines.observeDay(oct15).first().events
        assertEquals(listOf(breakfast), events.map { it.anchorId })
        assertEquals(AnchorTrigger.Window, events.single().source)
    }

    @Test
    fun toggleStep_completingPlanC_logsC_thenPlanA_upgradesToA() = runTest {
        val routineId = exercise(anchor("Acordei", 6))

        routines.toggleStep(stepId(routineId, Plan.C), oct15)
        assertEquals(RoutineOutcome.C, outcome(routineId))

        routines.toggleStep(stepId(routineId, Plan.A), oct15)
        assertEquals(RoutineOutcome.A, outcome(routineId))
    }

    @Test
    fun toggleStep_uncheckingTheOnlyCompletePlan_removesTheLog() = runTest {
        val routineId = exercise(anchor("Acordei", 6))
        val minimal = stepId(routineId, Plan.C)

        routines.toggleStep(minimal, oct15)
        routines.toggleStep(minimal, oct15)

        assertNull(outcome(routineId))
    }

    @Test
    fun toggleStep_afterMidnight_countsForTheLogicalDay() = runTest {
        val routineId = exercise(anchor("Jantei", 19))
        graph.clock.now = localAt(16, 1)

        routines.toggleStep(stepId(routineId, Plan.C), oct15)

        assertEquals(RoutineOutcome.C, outcome(routineId))
    }

    @Test
    fun skip_logsSkipped_andClearLogUndoesIt() = runTest {
        val routineId = exercise(anchor("Acordei", 6))

        routines.skip(routineId, oct15)
        assertEquals(RoutineOutcome.Skipped, outcome(routineId))

        routines.clearLog(routineId, oct15)
        assertNull(outcome(routineId))
    }

    @Test
    fun deleteAnchor_inUse_isRefused() = runTest {
        val anchorId = anchor("Acordei", 6)
        exercise(anchorId)

        assertFalse(routines.deleteAnchor(anchorId))
        assertEquals(1, routines.observeAnchors().first().size)
    }

    @Test
    fun deleteAnchor_unused_removesIt() = runTest {
        val anchorId = anchor("Acordei", 6)

        assertTrue(routines.deleteAnchor(anchorId))
        assertTrue(routines.observeAnchors().first().isEmpty())
    }
}
