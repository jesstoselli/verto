package com.verto.domain.rules

import com.verto.domain.VertoSettings
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.Step
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

data class TodayInput(
    val day: LocalDate,
    val zone: TimeZone,
    val settings: VertoSettings,
    val energy: EnergyLevel,
    val anchors: List<Anchor>,
    val routines: List<Routine>,
    val steps: List<Step>,
    val logs: List<RoutineLog>,
    val events: List<AnchorEvent>,
    val tasks: List<Task>,
    val now: Instant,
)

data class StepState(val step: Step, val done: Boolean)

data class RoutineCard(
    val routine: Routine,
    val suggestedPlan: Plan,
    val stepsA: List<StepState>,
    val stepsC: List<StepState>,
    val outcome: RoutineOutcome?,
) {
    fun steps(plan: Plan): List<StepState> = if (plan == Plan.A) stepsA else stepsC
}

data class AnchorSection(
    val anchor: Anchor,
    val triggered: Boolean,
    val routines: List<RoutineCard>,
)

data class TaskCard(
    val task: Task,
    val nextStep: Step?,
    val doneSteps: Int,
    val totalSteps: Int,
)

data class Today(
    val day: LocalDate,
    val energy: EnergyLevel,
    val sections: List<AnchorSection>,
    val expandedAnchorId: String?,
    val pinned: List<TaskCard>,
    val pinnedOverSoftLimit: Boolean,
    val deadlines: List<TaskCard>,
    /** Finished lately, newest first: a quiet "you did these", never a tally. */
    val recentlyDone: List<Task>,
)

/** "Hoje" is derived on every change, never stored, so there is no backlog to pile up. */
object TodayBuilder {

    fun build(input: TodayInput): Today {
        val scheduled = input.routines.filter { RoutineRules.isScheduledOn(it, input.day) }
        val logs = input.logs.filter { it.day == input.day }.associateBy { it.routineId }
        val suggested = RoutineRules.suggestedPlan(input.energy)

        val sections = input.anchors
            .sortedWith(
                compareBy<Anchor> { AnchorRules.windowOpensAt(it, input.day, input.zone, input.settings.dayStartsAt) }
                    .thenBy { it.name },
            )
            .mapNotNull { anchor ->
                val routines = scheduled.filter { it.anchorId == anchor.id }.sortedBy { it.name }
                if (routines.isEmpty()) {
                    null
                } else {
                    AnchorSection(
                        anchor = anchor,
                        triggered = AnchorRules.isTriggered(anchor, input.day, input.events),
                        routines = routines.map { routine ->
                            RoutineCard(
                                routine = routine,
                                suggestedPlan = suggested,
                                stepsA = stepStates(routine.id, Plan.A, input),
                                stepsC = stepStates(routine.id, Plan.C, input),
                                outcome = logs[routine.id]?.outcome,
                            )
                        },
                    )
                }
            }

        val open = input.tasks.filter { it.doneAt == null }
        val pinned = open
            .filter { it.pinnedToToday }
            .sortedWith(compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }.thenBy { it.title })
        val horizon = input.day.plus(input.settings.deadlineHorizonDays, DateTimeUnit.DAY)
        val deadlines = open
            .filter { task ->
                !task.pinnedToToday && task.dueKind == DueKind.Hard && task.dueDate?.let { it <= horizon } == true
            }
            .sortedBy { it.dueDate }
        val recentlyDone = input.tasks
            .filter { task -> task.doneAt?.let { input.now - it <= input.settings.recentlyDoneWindow } == true }
            .sortedByDescending { it.doneAt }

        return Today(
            day = input.day,
            energy = input.energy,
            sections = sections,
            expandedAnchorId = sections.firstOrNull { section -> section.routines.any { it.outcome == null } }?.anchor?.id,
            pinned = pinned.map { card(it, input.steps) },
            pinnedOverSoftLimit = pinned.size > input.settings.pinnedSoftLimit,
            deadlines = deadlines.map { card(it, input.steps) },
            recentlyDone = recentlyDone,
        )
    }

    private fun stepStates(routineId: String, plan: Plan, input: TodayInput): List<StepState> =
        RoutineRules.stepsOf(routineId, plan, input.steps).map {
            StepState(it, RoutineRules.isDoneOn(it, input.day, input.zone, input.settings.dayStartsAt))
        }

    private fun card(task: Task, steps: List<Step>): TaskCard {
        val own = steps.filter { it.owner == StepOwner.OfTask(task.id) }.sortedBy { it.order }
        return TaskCard(
            task = task,
            nextStep = own.firstOrNull { it.doneAt == null },
            doneSteps = own.count { it.doneAt != null },
            totalSteps = own.size,
        )
    }
}
