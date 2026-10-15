package com.verto.ui.di

import com.verto.data.IdGenerator
import com.verto.data.SystemZone
import com.verto.data.UuidIdGenerator
import com.verto.data.ZoneProvider
import com.verto.data.repository.DayRepository
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.data.sync.LocalStore
import com.verto.domain.VertoSettings
import com.verto.ui.common.ClockTicker
import com.verto.ui.common.Ticker
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Clock

/** Expects a `VertoDatabase` from the platform module (androidApp). */
val vertoModule = module {
    single { VertoSettings() }
    single<Clock> { Clock.System }
    single<IdGenerator> { UuidIdGenerator }
    single<ZoneProvider> { SystemZone }
    single<Ticker> { ClockTicker(get()) }
    single { LocalStore(get()) }
    singleOf(::InboxRepository)
    singleOf(::TaskRepository)
    singleOf(::RoutineRepository)
    singleOf(::DayRepository)
}
