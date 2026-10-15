package com.verto.data.settings

import com.verto.domain.SettingsSource
import com.verto.domain.VertoSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-device settings, read synchronously: they are needed the moment a screen opens. */
interface SettingsStore {
    fun load(): VertoSettings?
    fun save(settings: VertoSettings)
}

class InMemorySettingsStore(private var saved: VertoSettings? = null) : SettingsStore {
    override fun load(): VertoSettings? = saved
    override fun save(settings: VertoSettings) {
        saved = settings
    }
}

class SettingsRepository(private val store: SettingsStore) : SettingsSource {

    private val _settings = MutableStateFlow(store.load() ?: VertoSettings())
    val settings: StateFlow<VertoSettings> = _settings.asStateFlow()

    override fun current(): VertoSettings = _settings.value

    fun update(change: (VertoSettings) -> VertoSettings) {
        val next = change(_settings.value)
        store.save(next)
        _settings.value = next
    }
}
