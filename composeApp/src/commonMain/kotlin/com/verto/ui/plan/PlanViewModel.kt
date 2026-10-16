package com.verto.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.ZoneProvider
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.domain.SettingsSource
import com.verto.domain.model.Anchor
import com.verto.domain.model.Routine
import com.verto.domain.model.Task
import com.verto.domain.rules.LogicalDay
import com.verto.ui.common.Ticker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class RoutineRow(val routine: Routine, val anchorName: String?)

data class PlanUiState(
    val openTasks: List<Task> = emptyList(),
    val doneTasks: List<Task> = emptyList(),
    val showDone: Boolean = false,
    val routines: List<RoutineRow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val today: LocalDate? = null,
    val selectedTab: PlanTab = PlanTab.Tasks,
) {
    val taskCount: Int get() = openTasks.size + doneTasks.size
    val routineCount: Int get() = routines.size
    val anchorCount: Int get() = anchors.size
}

class PlanViewModel(
    private val tasks: TaskRepository,
    routines: RoutineRepository,
    ticker: Ticker,
    zones: ZoneProvider,
    settings: SettingsSource,
) : ViewModel() {

    private val showDone = MutableStateFlow(false)
    private val selectedTab = MutableStateFlow(PlanTab.Tasks)

    val state: StateFlow<PlanUiState> = combine(
        tasks.observeTasks(),
        routines.observeRoutines(),
        routines.observeAnchors(),
        combine(showDone, selectedTab, ::Pair),
        ticker.ticks().map { LogicalDay.of(it, zones.current(), settings.current().dayStartsAt) }.distinctUntilChanged(),
    ) { taskList, routineList, anchors, (show, tab), today ->
        val anchorNames = anchors.associate { it.id to it.name }
        PlanUiState(
            openTasks = taskList.filter { it.doneAt == null },
            doneTasks = taskList.filter { it.doneAt != null },
            showDone = show,
            routines = routineList.map { RoutineRow(it, anchorNames[it.anchorId]) },
            anchors = anchors,
            today = today,
            selectedTab = tab,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    /** Pinning happens in the editor; the list only offers taking a task off today. */
    fun unpin(task: Task) {
        viewModelScope.launch { tasks.setPinned(task.id, false) }
    }

    fun selectTab(tab: PlanTab) {
        selectedTab.value = tab
    }

    fun toggleShowDone() {
        showDone.update { !it }
    }
}
