package com.verto.data.settings

import com.verto.domain.VertoSettings
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {

    @Test
    fun nothingSaved_meansTheDefaults() {
        assertEquals(VertoSettings(), SettingsRepository(InMemorySettingsStore()).current())
    }

    @Test
    fun whatWasSaved_isWhatItStartsWith() {
        val repository = SettingsRepository(InMemorySettingsStore(VertoSettings(dayStartsAt = LocalTime(3, 0))))

        assertEquals(LocalTime(3, 0), repository.current().dayStartsAt)
    }

    @Test
    fun update_savesAndPublishes() {
        val store = InMemorySettingsStore()
        val repository = SettingsRepository(store)

        repository.update { it.copy(quietStart = LocalTime(23, 0)) }

        assertEquals(LocalTime(23, 0), repository.current().quietStart)
        assertEquals(LocalTime(23, 0), repository.settings.value.quietStart)
        assertEquals(LocalTime(23, 0), store.load()?.quietStart)
    }
}
