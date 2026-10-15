package com.verto.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.ZoneProvider
import com.verto.data.repository.DayRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.StepRepository
import com.verto.data.repository.TaskRepository
import com.verto.domain.SettingsSource
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.rules.LogicalDay
import com.verto.domain.rules.Today
import com.verto.domain.rules.TodayBuilder
import com.verto.domain.rules.TodayInput
import com.verto.domain.rules.TunnelItem
import com.verto.domain.rules.TunnelRules
import com.verto.ui.common.Ticker
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Content(
        val today: Today,
        val shownPlans: Map<String, Plan>,
        val expandedIds: Set<String>,
        /** "Só 1 coisa": what to do now first, then the rest in order. */
        val tunnel: List<TunnelItem> = emptyList(),
        /** Counts skipped items too: after "Outro" the list is shorter, but there is still another one. */
        val canSkipTunnel: Boolean = false,
    ) : TodayUiState
}

class TodayViewModel(
    private val routines: RoutineRepository,
    private val tasks: TaskRepository,
    private val days: DayRepository,
    ticker: Ticker,
    private val zones: ZoneProvider,
    private val settings: SettingsSource,
    private val clock: Clock,
    private val steps: StepRepository,
) : ViewModel() {

    private val shownPlans = MutableStateFlow<Map<String, Plan>>(emptyMap())
    private val expandedOverride = MutableStateFlow<Set<String>?>(null)

    // "Outro" only hides an item for now; nothing about it is recorded anywhere.
    private val tunnelSkipped = MutableStateFlow<Set<String>>(emptySet())

    // The ticker's delay pauses while the phone sleeps, so a wake-up or a tap must not wait for its next tick.
    private val nudges = MutableSharedFlow<Instant>(extraBufferCapacity = 1)
    private val lastTick = MutableStateFlow(clock.now())

    private val currentDay: StateFlow<LocalDate?> = merge(ticker.ticks(), nudges)
        .onEach {
            lastTick.value = it
            routines.recordOpenedWindows(dayOf(it))
        }
        .map { dayOf(it) }
        .distinctUntilChanged()
        .onEach {
            // Plan choices and opened sections belong to one day; yesterday's must not leak into today.
            shownPlans.value = emptyMap()
            expandedOverride.value = null
            tunnelSkipped.value = emptySet()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<TodayUiState> = currentDay.filterNotNull().flatMapLatest { day ->
        combine(
            routines.observeDay(day),
            tasks.observeTasks(),
            tasks.observeAllSteps(),
            days.observeEnergy(day),
            combine(shownPlans, expandedOverride, lastTick, tunnelSkipped, ::ScreenInputs),
        ) { routineDay, taskList, steps, energy, (plans, expanded, now, skipped) ->
            val today = TodayBuilder.build(
                TodayInput(
                    day = day,
                    zone = zones.current(),
                    settings = settings.current(),
                    energy = energy ?: EnergyLevel.Medium,
                    anchors = routineDay.anchors,
                    routines = routineDay.routines,
                    steps = steps,
                    logs = routineDay.logs,
                    events = routineDay.events,
                    tasks = taskList,
                    now = now,
                ),
            )
            val candidates = TunnelRules.candidates(today, plans)
            // Once everything was skipped, the list starts over instead of going empty.
            val tunnel = candidates.filter { it.key !in skipped }.ifEmpty { candidates }
            TodayUiState.Content(
                today = today,
                shownPlans = plans,
                expandedIds = expanded ?: setOfNotNull(today.expandedAnchorId),
                tunnel = tunnel,
                canSkipTunnel = candidates.size > 1,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    fun setEnergy(level: EnergyLevel) = withDay { days.setEnergy(it, level) }

    fun onResume() {
        nudges.tryEmit(clock.now())
    }

    fun tapAnchor(anchorId: String) = withDay { routines.tapAnchor(anchorId, it) }

    fun toggleRoutineStep(stepId: String) = withDay { routines.toggleStep(stepId, it) }

    fun skip(routineId: String) = withDay { routines.skip(routineId, it) }

    fun undo(routineId: String) = withDay { routines.clearLog(routineId, it) }

    fun showPlan(routineId: String, plan: Plan) {
        shownPlans.update { it + (routineId to plan) }
    }

    fun toggleSection(anchorId: String) {
        val content = state.value as? TodayUiState.Content ?: return
        val current = content.expandedIds
        expandedOverride.value = if (anchorId in current) current - anchorId else current + anchorId
    }

    fun toggleTaskStep(stepId: String) {
        viewModelScope.launch { tasks.toggleStep(stepId) }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch { tasks.setDone(taskId, true) }
    }

    // "Feito" is a big button pressed in a hurry: it only checks, a second tap must not undo it.
    fun tunnelDone(item: TunnelItem) {
        when (item) {
            is TunnelItem.RoutineStep -> withDay { routines.checkStep(item.step.id, it) }
            is TunnelItem.TaskStep -> {
                val step = item.step
                if (step == null) completeTask(item.task.id) else viewModelScope.launch { tasks.checkStep(step.id) }
            }
        }
    }

    fun tunnelOther() {
        val content = state.value as? TodayUiState.Content ?: return
        val current = content.tunnel.firstOrNull() ?: return
        tunnelSkipped.update { skipped ->
            // The list already wrapped around: start a new round from here.
            if (content.tunnel.all { it.key in skipped }) setOf(current.key) else skipped + current.key
        }
    }

    fun splitStep(stepId: String, titles: List<String>) {
        viewModelScope.launch { steps.split(stepId, titles) }
    }

    private fun dayOf(instant: Instant): LocalDate = LogicalDay.of(instant, zones.current(), settings.current().dayStartsAt)

    private fun withDay(action: suspend (LocalDate) -> Unit) {
        val now = clock.now()
        nudges.tryEmit(now)
        viewModelScope.launch { action(dayOf(now)) }
    }
}

private data class ScreenInputs(
    val plans: Map<String, Plan>,
    val expanded: Set<String>?,
    val now: Instant,
    val skipped: Set<String>,
)
