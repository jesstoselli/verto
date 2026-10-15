package com.verto.domain.rules

import com.verto.domain.FourAm
import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.at
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.routine
import com.verto.domain.routineStep
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineRulesTest {

    private fun outcome(vararg steps: com.verto.domain.model.Step) =
        RoutineRules.completedOutcome("r", steps.toList(), Oct15, SaoPaulo, FourAm)

    @Test
    fun suggestedPlan_isMinimalOnlyOnLowEnergy() {
        assertEquals(Plan.C, RoutineRules.suggestedPlan(EnergyLevel.Low))
        assertEquals(Plan.A, RoutineRules.suggestedPlan(EnergyLevel.Medium))
        assertEquals(Plan.A, RoutineRules.suggestedPlan(EnergyLevel.High))
    }

    @Test
    fun isScheduledOn_followsWeekdays() {
        val weekends = routine("r", "a", weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))

        assertFalse(RoutineRules.isScheduledOn(weekends, Oct15))
        assertTrue(RoutineRules.isScheduledOn(routine("r", "a"), Oct15))
    }

    @Test
    fun completedOutcome_planAComplete_isA() {
        assertEquals(
            RoutineOutcome.A,
            outcome(routineStep("a1", "r", Plan.A, doneAt = at(15, 8)), routineStep("c1", "r", Plan.C)),
        )
    }

    @Test
    fun completedOutcome_onlyPlanCComplete_isC() {
        assertEquals(
            RoutineOutcome.C,
            outcome(routineStep("a1", "r", Plan.A), routineStep("c1", "r", Plan.C, doneAt = at(15, 8))),
        )
    }

    @Test
    fun completedOutcome_bothComplete_isA() {
        assertEquals(
            RoutineOutcome.A,
            outcome(routineStep("a1", "r", Plan.A, doneAt = at(15, 8)), routineStep("c1", "r", Plan.C, doneAt = at(15, 8))),
        )
    }

    @Test
    fun completedOutcome_stepsDoneYesterday_doNotCount() {
        assertNull(outcome(routineStep("a1", "r", Plan.A, doneAt = at(14, 20))))
    }

    @Test
    fun completedOutcome_emptyPlan_neverCompletes() {
        assertNull(outcome(routineStep("a1", "r", Plan.A)))
    }

    @Test
    fun isDoneOn_afterMidnight_countsForTheLogicalDay() {
        val step = routineStep("a1", "r", Plan.A, doneAt = at(16, 1))

        assertTrue(RoutineRules.isDoneOn(step, Oct15, SaoPaulo, FourAm))
    }
}
