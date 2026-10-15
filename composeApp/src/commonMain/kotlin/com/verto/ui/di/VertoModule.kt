package com.verto.ui.di

import com.verto.data.IdGenerator
import com.verto.data.SystemZone
import com.verto.data.UuidIdGenerator
import com.verto.data.ZoneProvider
import com.verto.data.remote.AuthSession
import com.verto.data.remote.VertoApi
import com.verto.data.repository.DayRepository
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.ResetRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.StepRepository
import com.verto.data.repository.TaskRepository
import com.verto.data.repository.UsageTracker
import com.verto.data.settings.SettingsRepository
import com.verto.data.sync.LocalStore
import com.verto.data.sync.SyncEngine
import com.verto.data.sync.SyncScheduler
import com.verto.domain.SettingsSource
import com.verto.ui.account.AccountViewModel
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
import com.verto.ui.today.TodayViewModel
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Expects a VertoDatabase, a TokenStore, a SettingsStore and an HttpClient from the platform module (androidApp). */
val vertoModule = module {
    single { SettingsRepository(get()) }
    single<SettingsSource> { get<SettingsRepository>() }
    single<Clock> { Clock.System }
    single<IdGenerator> { UuidIdGenerator }
    single<ZoneProvider> { SystemZone }
    single<Ticker> { ClockTicker(get()) }
    single { LocalStore(get()) }
    singleOf(::InboxRepository)
    singleOf(::TaskRepository)
    singleOf(::RoutineRepository)
    singleOf(::DayRepository)
    singleOf(::StepRepository)
    singleOf(::ResetRepository)
    singleOf(::UsageTracker)
    single { VertoApi(get()) }
    single { AuthSession(get(), get()) }
    single { SyncEngine(get(), get(), get(), get()) }
    // Lives as long as the process: syncing must not stop when a screen goes away.
    single { SyncScheduler.of(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    viewModelOf(::AccountViewModel)
    viewModelOf(::InboxViewModel)
    viewModelOf(::PlanViewModel)
    viewModel { (args: TaskEditorArgs) -> TaskEditorViewModel(args.taskId, get()) }
    viewModel { (args: AnchorEditorArgs) -> AnchorEditorViewModel(args.anchorId, get()) }
    viewModel { (args: RoutineEditorArgs) -> RoutineEditorViewModel(args, get(), get()) }
    viewModelOf(::TodayViewModel)
}
