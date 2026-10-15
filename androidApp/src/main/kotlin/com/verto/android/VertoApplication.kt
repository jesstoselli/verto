package com.verto.android

import android.app.Application
import com.verto.data.db.vertoDatabase
import com.verto.data.remote.TokenStore
import com.verto.data.remote.vertoHttpClient
import com.verto.data.sync.SyncScheduler
import com.verto.ui.di.vertoModule
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class VertoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@VertoApplication)
            modules(
                module {
                    single { vertoDatabase(androidContext()) }
                    single<TokenStore> { PrefsTokenStore(androidContext()) }
                    single { vertoHttpClient(OkHttp.create()) }
                },
                vertoModule,
            )
        }.koin
        koin.get<SyncScheduler>().start()
        SyncWorker.schedule(this)
    }
}
