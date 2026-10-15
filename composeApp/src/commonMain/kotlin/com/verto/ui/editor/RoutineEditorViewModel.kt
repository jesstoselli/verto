package com.verto.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.RoutineRepository
import com.verto.domain.model.Anchor
import com.verto.domain.model.InboxState
import com.verto.domain.model.Plan
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

data class RoutineEditorArgs(val routineId: String?, val fromInboxId: String?)

data class RoutineEditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val anchorId: String? = null,
    val anchors: List<Anchor> = emptyList(),
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val stepsA: List<EditableStep> = emptyList(),
    val stepsC: List<EditableStep> = emptyList(),
    val message: String? = null,
    val closed: Boolean = false,
)

class RoutineEditorViewModel(
    private val args: RoutineEditorArgs,
    private val routines: RoutineRepository,
    private val inbox: InboxRepository,
) : ViewModel() {

    private val keys = KeyFactory()
    private val _state = MutableStateFlow(RoutineEditorState())
    val state: StateFlow<RoutineEditorState> = _state.asStateFlow()

    // A second tap on \"Salvar\" before the first write lands must not create a second routine.
    private var saving = false

    init {
        viewModelScope.launch {
            routines.observeAnchors().collect { anchors ->
                // A single anchor is the obvious choice; preselecting it saves a tap.
                _state.update { it.copy(anchors = anchors, anchorId = it.anchorId ?: anchors.singleOrNull()?.id) }
            }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val routineId = args.routineId
        if (routineId != null) {
            val draft = routines.routineDraft(routineId)
            if (draft == null) {
                _state.update { it.copy(loaded = true, closed = true) }
                return
            }
            _state.update {
                it.copy(
                    loaded = true,
                    id = draft.id,
                    name = draft.name,
                    anchorId = draft.anchorId,
                    weekdays = draft.weekdays,
                    stepsA = draft.stepsA.map { s -> EditableStep(keys.next(), s.id, s.title, s.energy) },
                    stepsC = draft.stepsC.map { s -> EditableStep(keys.next(), s.id, s.title, s.energy) },
                )
            }
        } else {
            val prefill = args.fromInboxId?.let { inbox.get(it)?.text }.orEmpty()
            _state.update { it.copy(loaded = true, name = prefill) }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, message = null) }

    fun setAnchor(anchorId: String) = _state.update { it.copy(anchorId = anchorId, message = null) }

    fun toggleWeekday(day: DayOfWeek) =
        _state.update { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day, message = null) }

    fun addStep(plan: Plan) = updateSteps(plan) { StepListOps.add(it, keys.next()) }

    fun setStepTitle(plan: Plan, key: String, title: String) =
        updateSteps(plan) { StepListOps.update(it, key) { step -> step.copy(title = title) } }

    fun setStepEnergy(plan: Plan, key: String, energy: StepEnergy) =
        updateSteps(plan) { StepListOps.update(it, key) { step -> step.copy(energy = energy) } }

    fun moveStep(plan: Plan, key: String, delta: Int) = updateSteps(plan) { StepListOps.move(it, key, delta) }

    fun removeStep(plan: Plan, key: String) = updateSteps(plan) { StepListOps.remove(it, key) }

    fun save() {
        val current = _state.value
        val stepsA = StepListOps.toDrafts(current.stepsA)
        val stepsC = StepListOps.toDrafts(current.stepsC)
        val anchorId = current.anchorId
        val problem = when {
            current.name.isBlank() -> "Dá um nome pra rotina."
            anchorId == null -> "Escolhe a âncora: depois de quê essa rotina acontece?"
            current.weekdays.isEmpty() -> "Escolhe pelo menos um dia."
            stepsA.isEmpty() -> "O plano ideal precisa de pelo menos um passo."
            stepsC.isEmpty() -> "O plano mínimo precisa de pelo menos um passo."
            else -> null
        }
        if (problem != null || anchorId == null) {
            _state.update { it.copy(message = problem) }
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            routines.saveRoutine(RoutineDraft(current.id, current.name, anchorId, current.weekdays, stepsA, stepsC))
            args.fromInboxId?.let { inbox.setState(it, InboxState.Processed) }
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            routines.deleteRoutine(id)
            _state.update { it.copy(closed = true) }
        }
    }

    private fun updateSteps(plan: Plan, change: (List<EditableStep>) -> List<EditableStep>) = _state.update {
        if (plan == Plan.A) it.copy(stepsA = change(it.stepsA), message = null) else it.copy(stepsC = change(it.stepsC), message = null)
    }
}
