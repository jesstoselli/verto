package com.verto.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.domain.model.Anchor
import com.verto.domain.model.Routine
import com.verto.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineRow(val routine: Routine, val anchorName: String?)

data class PlanUiState(
    val openTasks: List<Task> = emptyList(),
    val doneTasks: List<Task> = emptyList(),
    val showDone: Boolean = false,
    val routines: List<RoutineRow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
)

class PlanViewModel(private val tasks: TaskRepository, routines: RoutineRepository) : ViewModel() {

    private val showDone = MutableStateFlow(false)

    val state: StateFlow<PlanUiState> = combine(
        tasks.observeTasks(),
        routines.observeRoutines(),
        routines.observeAnchors(),
        showDone,
    ) { taskList, routineList, anchors, show ->
        val anchorNames = anchors.associate { it.id to it.name }
        PlanUiState(
            openTasks = taskList.filter { it.doneAt == null },
            doneTasks = taskList.filter { it.doneAt != null },
            showDone = show,
            routines = routineList.map { RoutineRow(it, anchorNames[it.anchorId]) },
            anchors = anchors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun togglePinned(task: Task) {
        viewModelScope.launch { tasks.setPinned(task.id, !task.pinnedToToday) }
    }

    fun toggleShowDone() {
        showDone.update { !it }
    }
}
