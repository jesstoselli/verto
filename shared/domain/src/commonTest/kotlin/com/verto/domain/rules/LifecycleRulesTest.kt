package com.verto.domain.rules

import com.verto.domain.at
import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import com.verto.domain.task
import com.verto.domain.taskStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class LifecycleRulesTest {

    private fun item(id: String, createdDay: Int, state: InboxState = InboxState.Raw) =
        InboxItem(id, id, at(createdDay, 10), InboxSource.App, state)

    @Test
    fun taskWithSteps_finishesWhenAllStepsAreDone() {
        val now = at(15, 10)

        assertEquals(now, TaskRules.doneAtAfterStepChange(task("t"), listOf(taskStep("s", "t", doneAt = at(15, 9))), now))
    }

    @Test
    fun taskWithSteps_reopensWhenAStepIsUndone() {
        val done = task("t", doneAt = at(15, 9))

        assertNull(TaskRules.doneAtAfterStepChange(done, listOf(taskStep("s", "t")), at(15, 10)))
    }

    @Test
    fun taskWithSteps_keepsItsOriginalFinishTime() {
        val done = task("t", doneAt = at(15, 9))

        assertEquals(at(15, 9), TaskRules.doneAtAfterStepChange(done, listOf(taskStep("s", "t", doneAt = at(15, 9))), at(15, 11)))
    }

    @Test
    fun taskWithoutSteps_isOnlyFinishedByHand() {
        assertNull(TaskRules.doneAtAfterStepChange(task("t"), emptyList(), at(15, 10)))
        assertEquals(at(15, 9), TaskRules.doneAtAfterStepChange(task("t", doneAt = at(15, 9)), emptyList(), at(15, 10)))
    }

    @Test
    fun incubation_takesOnlyRawItemsOlderThanTheThreshold() {
        val items = listOf(item("old", 11), item("fresh", 13), item("oldKept", 10, InboxState.Processed))

        assertEquals(listOf("old"), InboxRules.toIncubate(items, at(14, 10), 3.days).map { it.id })
    }

    @Test
    fun reset_unpinsEveryPinnedTask_andIncubatesRawItems() {
        val plan = ResetRules.plan(
            tasks = listOf(task("pinned", pinned = true), task("free")),
            inbox = listOf(item("raw", 15), item("done", 15, InboxState.Processed)),
        )

        assertEquals(listOf("pinned"), plan.tasksToUnpin.map { it.id })
        assertEquals(listOf("raw"), plan.inboxToIncubate.map { it.id })
    }

    @Test
    fun absence_offersFreshStartOnlyAfterTheThreshold() {
        assertTrue(AbsenceRules.shouldOfferFreshStart(at(10, 10), at(14, 10), 4.days))
        assertFalse(AbsenceRules.shouldOfferFreshStart(at(11, 10), at(14, 10), 4.days))
        assertFalse(AbsenceRules.shouldOfferFreshStart(null, at(14, 10), 4.days))
    }
}
