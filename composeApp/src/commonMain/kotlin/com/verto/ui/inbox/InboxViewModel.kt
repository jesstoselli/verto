package com.verto.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.TaskDraft
import com.verto.data.repository.TaskRepository
import com.verto.domain.VertoSettings
import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val draft: String = "",
    val raw: List<InboxItem> = emptyList(),
    val incubated: List<InboxItem> = emptyList(),
    val showIncubated: Boolean = false,
)

class InboxViewModel(
    private val inbox: InboxRepository,
    private val tasks: TaskRepository,
    settings: VertoSettings,
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val showIncubated = MutableStateFlow(false)

    // The card stays on screen until the write lands; a second tap must not make a second task.
    private val converting = mutableSetOf<String>()

    val state: StateFlow<InboxUiState> = combine(inbox.observe(), draft, showIncubated) { items, text, show ->
        InboxUiState(
            draft = text,
            raw = items.filter { it.state == InboxState.Raw },
            incubated = items.filter { it.state == InboxState.Incubated },
            showIncubated = show,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    init {
        viewModelScope.launch { inbox.incubateStale(settings) }
    }

    fun onDraftChange(text: String) {
        draft.value = text
    }

    fun capture() {
        val text = draft.value
        draft.value = ""
        viewModelScope.launch { inbox.capture(text, InboxSource.App) }
    }

    fun toTask(item: InboxItem) {
        if (!converting.add(item.id)) return
        viewModelScope.launch {
            tasks.save(TaskDraft(id = null, title = item.text))
            inbox.setState(item.id, InboxState.Processed)
        }
    }

    fun incubate(id: String) = setState(id, InboxState.Incubated)

    fun discard(id: String) = setState(id, InboxState.Discarded)

    fun restore(id: String) = setState(id, InboxState.Raw)

    fun toggleIncubated() {
        showIncubated.update { !it }
    }

    private fun setState(id: String, state: InboxState) {
        viewModelScope.launch { inbox.setState(id, state) }
    }
}
