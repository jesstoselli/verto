package com.verto.android

import android.app.Application
import com.verto.data.db.vertoDatabase
import com.verto.ui.di.vertoModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class VertoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VertoApplication)
            modules(
                module { single { vertoDatabase(androidContext()) } },
                vertoModule,
            )
        }
    }
}
