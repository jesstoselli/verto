package com.verto.domain.rules

import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxState
import com.verto.domain.model.Task

data class ResetPlan(
    val tasksToUnpin: List<Task>,
    val inboxToIncubate: List<InboxItem>,
)

/** A fresh start archives nothing and marks nothing late: it only clears today's commitments. */
object ResetRules {

    fun plan(tasks: List<Task>, inbox: List<InboxItem>): ResetPlan = ResetPlan(
        tasksToUnpin = tasks.filter { it.pinnedToToday },
        inboxToIncubate = inbox.filter { it.state == InboxState.Raw },
    )
}
