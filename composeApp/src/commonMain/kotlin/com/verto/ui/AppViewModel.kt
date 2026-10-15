package com.verto.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.ResetRepository
import com.verto.data.repository.UsageTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(private val usage: UsageTracker, private val reset: ResetRepository) : ViewModel() {

    private val _offerFreshStart = MutableStateFlow(false)
    val offerFreshStart: StateFlow<Boolean> = _offerFreshStart.asStateFlow()

    fun onForeground() {
        viewModelScope.launch {
            if (usage.checkIn()) _offerFreshStart.value = true
        }
    }

    fun startClean() {
        _offerFreshStart.value = false
        viewModelScope.launch { reset.freshStart() }
    }

    fun keepAsItWas() {
        _offerFreshStart.value = false
    }
}
