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
import com.verto.ui.editor.AnchorEditorArgs
import com.verto.ui.editor.AnchorEditorViewModel
import com.verto.ui.editor.RoutineEditorArgs
import com.verto.ui.editor.RoutineEditorViewModel
import com.verto.ui.editor.TaskEditorArgs
import com.verto.ui.editor.TaskEditorViewModel
import com.verto.ui.inbox.InboxViewModel
import com.verto.ui.plan.PlanViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
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
    viewModelOf(::InboxViewModel)
    viewModelOf(::PlanViewModel)
    viewModel { (args: TaskEditorArgs) -> TaskEditorViewModel(args.taskId, get()) }
    viewModel { (args: AnchorEditorArgs) -> AnchorEditorViewModel(args.anchorId, get()) }
    viewModel { (args: RoutineEditorArgs) -> RoutineEditorViewModel(args, get(), get()) }
}
