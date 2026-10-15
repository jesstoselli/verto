package com.verto.domain.rules

import com.verto.domain.model.Anchor
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.Step
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.Task

/** One thing to do now, in "Só 1 coisa". */
sealed interface TunnelItem {
    val key: String

    data class RoutineStep(val anchor: Anchor, val routine: Routine, val step: Step) : TunnelItem {
        override val key: String get() = "step:${step.id}"
    }

    /** [step] is null for a task with no steps: the task itself is the thing to do. */
    data class TaskStep(val task: Task, val step: Step?) : TunnelItem {
        override val key: String get() = step?.let { "step:${it.id}" } ?: "task:${task.id}"
    }
}

object TunnelRules {

    fun fits(step: StepEnergy, energy: EnergyLevel): Boolean = when (energy) {
        EnergyLevel.Low -> step == StepEnergy.Low
        EnergyLevel.Medium -> step != StepEnergy.Hyperfocus
        EnergyLevel.High -> true
    }

    /**
     * Routine steps of anchors that already fired, then hard deadlines (soonest first), then
     * pinned tasks. Steps are taken in order: when the next one is too heavy for today's
     * energy, that routine or task waits instead of jumping ahead.
     */
    fun candidates(today: Today, shownPlans: Map<String, Plan> = emptyMap()): List<TunnelItem> {
        val routineSteps = today.sections.filter { it.triggered }.flatMap { section ->
            section.routines.filter { it.outcome == null }.mapNotNull { card ->
                val plan = shownPlans[card.routine.id] ?: card.suggestedPlan
                card.steps(plan).firstOrNull { !it.done }?.step
                    ?.takeIf { fits(it.energy, today.energy) }
                    ?.let { TunnelItem.RoutineStep(section.anchor, card.routine, it) }
            }
        }
        val open = (today.deadlines + today.pinned).distinctBy { it.task.id }
        val hard = open.filter { it.task.dueKind == DueKind.Hard && it.task.dueDate != null }.sortedBy { it.task.dueDate }
        val pinned = today.pinned.filter { card -> hard.none { it.task.id == card.task.id } }
        return routineSteps + (hard + pinned).mapNotNull { taskItem(it, today.energy) }
    }

    private fun taskItem(card: TaskCard, energy: EnergyLevel): TunnelItem? {
        val next = card.nextStep
        return when {
            next != null -> next.takeIf { fits(it.energy, energy) }?.let { TunnelItem.TaskStep(card.task, it) }
            card.totalSteps == 0 -> TunnelItem.TaskStep(card.task, null)
            else -> null
        }
    }
}
