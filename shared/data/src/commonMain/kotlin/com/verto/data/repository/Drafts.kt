package com.verto.data.repository

import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.DueKind
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.StepEnergy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class StepDraft(val id: String?, val title: String, val energy: StepEnergy)

data class TaskDraft(
    val id: String?,
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val dueKind: DueKind? = null,
    val pinnedToToday: Boolean = false,
    val steps: List<StepDraft> = emptyList(),
    /** Read-only on load; finishing a task goes through [TaskRepository.setDone]. */
    val done: Boolean = false,
)

data class AnchorDraft(val id: String?, val name: String, val windowStart: LocalTime, val windowEnd: LocalTime)

data class RoutineDraft(
    val id: String?,
    val name: String,
    val anchorId: String,
    val weekdays: Set<DayOfWeek>,
    val stepsA: List<StepDraft>,
    val stepsC: List<StepDraft>,
)

data class RoutineDay(
    val anchors: List<Anchor>,
    val routines: List<Routine>,
    val events: List<AnchorEvent>,
    val logs: List<RoutineLog>,
)
