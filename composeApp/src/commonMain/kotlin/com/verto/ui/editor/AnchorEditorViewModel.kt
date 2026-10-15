package com.verto.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineRepository
import com.verto.ui.common.hhmm
import com.verto.ui.common.parseClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnchorEditorArgs(val anchorId: String?)

data class AnchorEditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val start: String = "",
    val end: String = "",
    val message: String? = null,
    val closed: Boolean = false,
)

class AnchorEditorViewModel(anchorId: String?, private val routines: RoutineRepository) : ViewModel() {

    private val _state = MutableStateFlow(AnchorEditorState(loaded = anchorId == null))
    val state: StateFlow<AnchorEditorState> = _state.asStateFlow()

    init {
        if (anchorId != null) {
            viewModelScope.launch {
                val draft = routines.anchorDraft(anchorId)
                _state.value = if (draft == null) {
                    AnchorEditorState(loaded = true, closed = true)
                } else {
                    AnchorEditorState(true, draft.id, draft.name, draft.windowStart.hhmm(), draft.windowEnd.hhmm())
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, message = null) }

    fun setStart(value: String) = _state.update { it.copy(start = value, message = null) }

    fun setEnd(value: String) = _state.update { it.copy(end = value, message = null) }

    fun save() {
        val current = _state.value
        val start = parseClock(current.start)
        val end = parseClock(current.end)
        val problem = when {
            current.name.isBlank() -> "Dá um nome pra âncora, tipo \"Almocei\"."
            start == null || end == null -> "Use horários como 12:00."
            start >= end -> "A janela precisa terminar depois de começar."
            else -> null
        }
        if (problem != null || start == null || end == null) {
            _state.update { it.copy(message = problem) }
            return
        }
        viewModelScope.launch {
            routines.saveAnchor(AnchorDraft(current.id, current.name, start, end))
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            if (routines.deleteAnchor(id)) {
                _state.update { it.copy(closed = true) }
            } else {
                _state.update { it.copy(message = "Essa âncora ainda tem rotinas. Mude ou apague as rotinas antes.") }
            }
        }
    }
}
