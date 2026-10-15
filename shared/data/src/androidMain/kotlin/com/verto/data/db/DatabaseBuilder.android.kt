package com.verto.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun vertoDatabase(context: Context): VertoDatabase {
    val app = context.applicationContext
    return Room.databaseBuilder<VertoDatabase>(app, app.getDatabasePath("verto.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
