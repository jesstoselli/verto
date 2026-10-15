package com.verto.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.ResetRepository
import com.verto.data.repository.UsageTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Checking blocks the lists only on the first open: the spec puts "Que bom te ver" before any of them. */
enum class FreshStart { Checking, Offer, None }

class AppViewModel(private val usage: UsageTracker, private val reset: ResetRepository) : ViewModel() {

    private val _freshStart = MutableStateFlow(FreshStart.Checking)
    val freshStart: StateFlow<FreshStart> = _freshStart.asStateFlow()

    fun onForeground() {
        viewModelScope.launch {
            val offer = usage.checkIn()
            _freshStart.update { current ->
                when {
                    offer -> FreshStart.Offer
                    current == FreshStart.Offer -> current
                    else -> FreshStart.None
                }
            }
        }
    }

    fun startClean() {
        _freshStart.value = FreshStart.None
        viewModelScope.launch {
            reset.freshStart()
            usage.answered()
        }
    }

    fun keepAsItWas() {
        _freshStart.value = FreshStart.None
        viewModelScope.launch { usage.answered() }
    }
}
