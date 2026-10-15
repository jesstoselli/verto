package com.verto.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.TaskDraft
import com.verto.data.repository.TaskRepository
import com.verto.domain.model.DueKind
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TaskEditorArgs(val taskId: String?)

data class TaskEditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val title: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueKind: DueKind = DueKind.Soft,
    val pinned: Boolean = false,
    val done: Boolean = false,
    val steps: List<EditableStep> = emptyList(),
    val closed: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

class TaskEditorViewModel(taskId: String?, private val tasks: TaskRepository) : ViewModel() {

    private val keys = KeyFactory()
    private val _state = MutableStateFlow(TaskEditorState(loaded = taskId == null))
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    // A second tap on "Salvar" before the first write lands must not create a second task.
    private var saving = false

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val draft = tasks.draftFor(taskId)
                _state.value = if (draft == null) {
                    TaskEditorState(loaded = true, closed = true)
                } else {
                    TaskEditorState(
                        loaded = true,
                        id = draft.id,
                        title = draft.title,
                        notes = draft.notes.orEmpty(),
                        dueDate = draft.dueDate,
                        dueKind = draft.dueKind ?: DueKind.Soft,
                        pinned = draft.pinnedToToday,
                        done = draft.done,
                        steps = draft.steps.map { EditableStep(keys.next(), it.id, it.title, it.energy) },
                    )
                }
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun setDueDate(value: LocalDate?) = _state.update { it.copy(dueDate = value) }

    fun setDueKind(value: DueKind) = _state.update { it.copy(dueKind = value) }

    fun setPinned(value: Boolean) = _state.update { it.copy(pinned = value) }

    fun addStep() = _state.update { it.copy(steps = StepListOps.add(it.steps, keys.next())) }

    fun setStepTitle(key: String, title: String) =
        _state.update { it.copy(steps = StepListOps.update(it.steps, key) { step -> step.copy(title = title) }) }

    fun setStepEnergy(key: String, energy: StepEnergy) =
        _state.update { it.copy(steps = StepListOps.update(it.steps, key) { step -> step.copy(energy = energy) }) }

    fun moveStep(key: String, delta: Int) = _state.update { it.copy(steps = StepListOps.move(it.steps, key, delta)) }

    fun removeStep(key: String) = _state.update { it.copy(steps = StepListOps.remove(it.steps, key)) }

    fun save() {
        val current = _state.value
        if (!current.canSave || saving) return
        saving = true
        viewModelScope.launch {
            tasks.save(
                TaskDraft(
                    id = current.id,
                    title = current.title,
                    notes = current.notes,
                    dueDate = current.dueDate,
                    dueKind = current.dueKind.takeIf { current.dueDate != null },
                    pinnedToToday = current.pinned,
                    steps = StepListOps.toDrafts(current.steps),
                ),
            )
            _state.update { it.copy(closed = true) }
        }
    }

    fun toggleDone() {
        val id = _state.value.id ?: return
        val done = !_state.value.done
        viewModelScope.launch {
            tasks.setDone(id, done)
            _state.update { it.copy(done = done) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            tasks.delete(id)
            _state.update { it.copy(closed = true) }
        }
    }
}
