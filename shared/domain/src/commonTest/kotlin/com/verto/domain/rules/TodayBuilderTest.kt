package com.verto.domain.rules

import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.VertoSettings
import com.verto.domain.anchor
import com.verto.domain.at
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.Step
import com.verto.domain.model.Task
import com.verto.domain.routine
import com.verto.domain.routineStep
import com.verto.domain.task
import com.verto.domain.taskStep
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayBuilderTest {

    private fun build(
        energy: EnergyLevel = EnergyLevel.Medium,
        anchors: List<Anchor> = emptyList(),
        routines: List<Routine> = emptyList(),
        steps: List<Step> = emptyList(),
        logs: List<RoutineLog> = emptyList(),
        events: List<AnchorEvent> = emptyList(),
        tasks: List<Task> = emptyList(),
    ) = TodayBuilder.build(
        TodayInput(Oct15, SaoPaulo, VertoSettings(), energy, anchors, routines, steps, logs, events, tasks),
    )

    @Test
    fun sections_followWindowOrder_andSkipAnchorsWithoutRoutinesToday() {
        val today = build(
            anchors = listOf(anchor("dinner", 19), anchor("breakfast", 6), anchor("lunch", 12)),
            routines = listOf(
                routine("stretch", "breakfast"),
                routine("dishes", "dinner"),
                routine("weekend", "lunch", weekdays = setOf(DayOfWeek.SATURDAY)),
            ),
        )

        assertEquals(listOf("breakfast", "dinner"), today.sections.map { it.anchor.id })
    }

    @Test
    fun lowEnergy_suggestsPlanC() {
        val today = build(
            energy = EnergyLevel.Low,
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
        )

        assertEquals(Plan.C, today.sections.single().routines.single().suggestedPlan)
    }

    @Test
    fun routineCard_carriesTodaysOutcomeAndStepState() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
            steps = listOf(
                routineStep("a1", "stretch", Plan.A, order = 1),
                routineStep("a0", "stretch", Plan.A, order = 0, doneAt = at(15, 7)),
                routineStep("c0", "stretch", Plan.C),
            ),
            logs = listOf(RoutineLog("l", "stretch", Oct15, RoutineOutcome.C)),
        )
        val card = today.sections.single().routines.single()

        assertEquals(RoutineOutcome.C, card.outcome)
        assertEquals(listOf("a0" to true, "a1" to false), card.stepsA.map { it.step.id to it.done })
        assertEquals(listOf("c0"), card.steps(Plan.C).map { it.step.id })
    }

    @Test
    fun expandedAnchor_isFirstSectionWithAnUnloggedRoutine() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6), anchor("lunch", 12)),
            routines = listOf(routine("stretch", "breakfast"), routine("walk", "lunch")),
            logs = listOf(RoutineLog("l", "stretch", Oct15, RoutineOutcome.Skipped)),
        )

        assertEquals("lunch", today.expandedAnchorId)
    }

    @Test
    fun expandedAnchor_isNullWhenEverythingIsLogged() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
            logs = listOf(RoutineLog("l", "stretch", Oct15, RoutineOutcome.A)),
        )

        assertNull(today.expandedAnchorId)
    }

    @Test
    fun triggeredFlag_comesFromTodaysEvents() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
            events = listOf(AnchorEvent("e", "breakfast", Oct15, at(15, 7), AnchorTrigger.Tap)),
        )

        assertTrue(today.sections.single().triggered)
    }

    @Test
    fun pinned_overSoftLimit_isFlaggedButStillListed() {
        val today = build(tasks = (1..4).map { task("t$it", pinned = true) })

        assertEquals(4, today.pinned.size)
        assertTrue(today.pinnedOverSoftLimit)
    }

    @Test
    fun pinned_excludesFinishedTasks() {
        val today = build(tasks = listOf(task("open", pinned = true), task("done", pinned = true, doneAt = at(15, 9))))

        assertEquals(listOf("open"), today.pinned.map { it.task.id })
        assertFalse(today.pinnedOverSoftLimit)
    }

    @Test
    fun deadlines_areOpenUnpinnedHardDueWithinThreeDaysIncludingPast() {
        val today = build(
            tasks = listOf(
                task("past", dueDate = LocalDate(2026, 10, 12), dueKind = DueKind.Hard),
                task("soon", dueDate = LocalDate(2026, 10, 18), dueKind = DueKind.Hard),
                task("far", dueDate = LocalDate(2026, 10, 19), dueKind = DueKind.Hard),
                task("soft", dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Soft),
                task("pinned", pinned = true, dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Hard),
                task("done", dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Hard, doneAt = at(15, 9)),
            ),
        )

        assertEquals(listOf("past", "soon"), today.deadlines.map { it.task.id })
    }

    @Test
    fun taskCard_nextStepIsFirstUndoneByOrder() {
        val today = build(
            tasks = listOf(task("ir", pinned = true)),
            steps = listOf(
                taskStep("s2", "ir", order = 2),
                taskStep("s0", "ir", order = 0, doneAt = at(15, 9)),
                taskStep("s1", "ir", order = 1),
            ),
        )
        val card = today.pinned.single()

        assertEquals("s1", card.nextStep?.id)
        assertEquals(1, card.doneSteps)
        assertEquals(3, card.totalSteps)
    }
}
