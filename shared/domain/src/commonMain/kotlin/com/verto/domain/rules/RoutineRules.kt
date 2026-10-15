package com.verto.domain.rules

import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.Step
import com.verto.domain.model.StepOwner
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

object RoutineRules {

    fun suggestedPlan(energy: EnergyLevel): Plan = if (energy == EnergyLevel.Low) Plan.C else Plan.A

    fun isScheduledOn(routine: Routine, day: LocalDate): Boolean = day.dayOfWeek in routine.weekdays

    fun stepsOf(routineId: String, plan: Plan, steps: List<Step>): List<Step> =
        steps.filter { it.owner == StepOwner.OfRoutine(routineId, plan) }.sortedBy { it.order }

    /** Routine steps repeat daily: a step counts as done only if it was checked on this logical day. */
    fun isDoneOn(step: Step, day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Boolean =
        step.doneAt?.let { LogicalDay.of(it, zone, dayStartsAt) == day } ?: false

    /** A plan counts once every one of its steps is done; A wins when both are complete. */
    fun completedOutcome(
        routineId: String,
        steps: List<Step>,
        day: LocalDate,
        zone: TimeZone,
        dayStartsAt: LocalTime,
    ): RoutineOutcome? {
        fun complete(plan: Plan): Boolean {
            val planSteps = stepsOf(routineId, plan, steps)
            return planSteps.isNotEmpty() && planSteps.all { isDoneOn(it, day, zone, dayStartsAt) }
        }
        return when {
            complete(Plan.A) -> RoutineOutcome.A
            complete(Plan.C) -> RoutineOutcome.C
            else -> null
        }
    }
}
