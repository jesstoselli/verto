package com.verto.domain.rules

import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.VertoSettings
import com.verto.domain.anchor
import com.verto.domain.at
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Step
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import com.verto.domain.routine
import com.verto.domain.routineStep
import com.verto.domain.task
import com.verto.domain.taskStep
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelRulesTest {

    private val lunchFired = AnchorEvent("e1", "lunch", Oct15, at(15, 12), AnchorTrigger.Tap)

    private fun today(
        energy: EnergyLevel = EnergyLevel.Medium,
        steps: List<Step> = emptyList(),
        tasks: List<Task> = emptyList(),
        events: List<AnchorEvent> = listOf(lunchFired),
    ) = TodayBuilder.build(
        TodayInput(
            day = Oct15,
            zone = SaoPaulo,
            settings = VertoSettings(),
            energy = energy,
            anchors = listOf(anchor("lunch", 12), anchor("dinner", 19)),
            routines = listOf(routine("walk", "lunch"), routine("dishes", "dinner")),
            steps = steps,
            logs = emptyList(),
            events = events,
            tasks = tasks,
            now = at(15, 13),
        ),
    )

    private fun ids(items: List<TunnelItem>) = items.map { it.key }

    private fun stepOf(id: String, taskId: String, energy: StepEnergy, order: Int = 0) =
        Step(id, StepOwner.OfTask(taskId), order, id, energy, null)

    @Test
    fun onlyAnchorsThatFired_giveRoutineSteps() {
        val items = TunnelRules.candidates(
            today(steps = listOf(routineStep("walkA", "walk", Plan.A), routineStep("dishesA", "dishes", Plan.A))),
        )

        assertEquals(listOf("step:walkA"), ids(items))
    }

    @Test
    fun lowEnergy_takesThePlanCStep_andOnlyLightSteps() {
        val items = TunnelRules.candidates(
            today(
                energy = EnergyLevel.Low,
                steps = listOf(
                    routineStep("walkA", "walk", Plan.A, energy = StepEnergy.Medium),
                    routineStep("walkC", "walk", Plan.C),
                    stepOf("heavy", "ir", StepEnergy.Medium),
                ),
                tasks = listOf(task("ir", pinned = true)),
            ),
        )

        assertEquals(listOf("step:walkC"), ids(items))
    }

    @Test
    fun theChosenPlan_winsOverTheSuggestion() {
        val items = TunnelRules.candidates(
            today(
                energy = EnergyLevel.Low,
                steps = listOf(routineStep("walkA", "walk", Plan.A), routineStep("walkC", "walk", Plan.C)),
            ),
            shownPlans = mapOf("walk" to Plan.A),
        )

        assertEquals(listOf("step:walkA"), ids(items))
    }

    @Test
    fun order_isRoutineThenHardDeadlinesThenPinned() {
        val items = TunnelRules.candidates(
            today(
                steps = listOf(
                    routineStep("walkA", "walk", Plan.A),
                    taskStep("pinnedStep", "pinned"),
                    taskStep("soonStep", "soon"),
                    taskStep("laterStep", "later"),
                ),
                tasks = listOf(
                    task("pinned", pinned = true),
                    task("soon", dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Hard),
                    task("later", pinned = true, dueDate = LocalDate(2026, 10, 25), dueKind = DueKind.Hard),
                ),
            ),
        )

        assertEquals(listOf("step:walkA", "step:soonStep", "step:laterStep", "step:pinnedStep"), ids(items))
    }

    @Test
    fun aNextStepTooHeavyForToday_makesThatTaskWait() {
        val items = TunnelRules.candidates(
            today(
                energy = EnergyLevel.Medium,
                steps = listOf(stepOf("deep", "ir", StepEnergy.Hyperfocus, 0), stepOf("light", "ir", StepEnergy.Low, 1)),
                tasks = listOf(task("ir", pinned = true)),
                events = emptyList(),
            ),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun aTaskWithoutSteps_isItsOwnItem() {
        val items = TunnelRules.candidates(today(tasks = listOf(task("ligar", pinned = true)), events = emptyList()))

        assertEquals(listOf("task:ligar"), ids(items))
    }

    @Test
    fun highEnergy_takesHyperfocusToo() {
        assertTrue(TunnelRules.fits(StepEnergy.Hyperfocus, EnergyLevel.High))
        assertTrue(TunnelRules.fits(StepEnergy.Medium, EnergyLevel.Medium))
        assertTrue(!TunnelRules.fits(StepEnergy.Medium, EnergyLevel.Low))
    }
}
