package com.verto.domain.rules

import com.verto.domain.model.Step
import com.verto.domain.model.Task
import kotlin.time.Instant

object TaskRules {

    /**
     * A task with steps finishes itself when its last step is checked and reopens when
     * one is unchecked or added; a task without steps only finishes by hand.
     */
    fun doneAtAfterStepChange(task: Task, taskSteps: List<Step>, now: Instant): Instant? = when {
        taskSteps.isEmpty() -> task.doneAt
        taskSteps.all { it.doneAt != null } -> task.doneAt ?: now
        else -> null
    }
}
