package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.EnergyLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayRepositoryTest {

    private val graph = DataGraph()
    private val oct15 = LocalDate(2026, 10, 15)

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun energy_isNullUntilSet() = runTest {
        assertNull(graph.days.observeEnergy(oct15).first())
    }

    @Test
    fun setEnergy_twiceTheSameDay_keepsOneRecordWithTheLatestLevel() = runTest {
        graph.days.setEnergy(oct15, EnergyLevel.High)
        graph.days.setEnergy(oct15, EnergyLevel.Low)

        assertEquals(EnergyLevel.Low, graph.days.observeEnergy(oct15).first())
        assertEquals(1, graph.db.outboxDao().all().count { it.entityType == "day_energy" })
    }
}
