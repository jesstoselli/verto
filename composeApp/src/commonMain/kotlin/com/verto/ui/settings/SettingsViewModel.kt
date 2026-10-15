package com.verto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.ResetRepository
import com.verto.data.settings.SettingsRepository
import com.verto.ui.common.clockDigits
import com.verto.ui.common.hhmm
import com.verto.ui.common.parseClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.days

data class SettingsUiState(
    val dayStartHour: Int,
    val absenceDays: Int,
    val quietStart: String,
    val quietEnd: String,
    val message: String? = null,
    val cleaned: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val reset: ResetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        settings.current().let {
            SettingsUiState(
                dayStartHour = it.dayStartsAt.hour,
                absenceDays = it.absenceThreshold.inWholeDays.toInt(),
                quietStart = clockDigits(it.quietStart.hhmm()),
                quietEnd = clockDigits(it.quietEnd.hhmm()),
            )
        },
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setDayStartHour(hour: Int) {
        settings.update { it.copy(dayStartsAt = LocalTime(hour, 0)) }
        _state.update { it.copy(dayStartHour = hour) }
    }

    fun changeAbsenceDays(delta: Int) {
        val days = (_state.value.absenceDays + delta).coerceIn(MIN_ABSENCE_DAYS, MAX_ABSENCE_DAYS)
        settings.update { it.copy(absenceThreshold = days.days) }
        _state.update { it.copy(absenceDays = days) }
    }

    fun setQuietStart(text: String) = _state.update { it.copy(quietStart = clockDigits(text), message = null) }

    fun setQuietEnd(text: String) = _state.update { it.copy(quietEnd = clockDigits(text), message = null) }

    fun saveQuietHours() {
        val start = parseClock(_state.value.quietStart)
        val end = parseClock(_state.value.quietEnd)
        val message = when {
            start == null || end == null -> "Use horários como 22:00."
            start == end -> "O silêncio precisa começar e terminar em horários diferentes."
            else -> {
                settings.update { it.copy(quietStart = start, quietEnd = end) }
                "Horário de silêncio salvo."
            }
        }
        _state.update { it.copy(message = message) }
    }

    fun freshStart() {
        viewModelScope.launch {
            reset.freshStart()
            _state.update { it.copy(cleaned = true) }
        }
    }

    private companion object {
        const val MIN_ABSENCE_DAYS = 2
        const val MAX_ABSENCE_DAYS = 14
    }
}
