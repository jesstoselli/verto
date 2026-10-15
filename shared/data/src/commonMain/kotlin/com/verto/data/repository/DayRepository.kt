package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.DayEnergyEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.domain.model.EnergyLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class DayRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = store.db.dayEnergyDao()

    fun observeEnergy(day: LocalDate): Flow<EnergyLevel?> =
        dao.observeLatest(day.toString()).map { row -> row?.level?.toEnum<EnergyLevel>() }

    suspend fun setEnergy(day: LocalDate, level: EnergyLevel) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val row = dao.latest(day.toString())
                ?: DayEnergyEntity(ids.newId(), day.toString(), level.wire(), nowMs, nowMs, null)
            put(row.copy(level = level.wire(), setAt = nowMs, updatedAt = nowMs))
        }
    }
}
