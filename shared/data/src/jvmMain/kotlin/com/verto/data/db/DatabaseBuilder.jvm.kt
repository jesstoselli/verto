package com.verto.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

fun vertoDatabase(file: File): VertoDatabase =
    Room.databaseBuilder<VertoDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Throwaway database for the JVM tests of this module and of composeApp. */
fun inMemoryVertoDatabase(): VertoDatabase =
    Room.inMemoryDatabaseBuilder<VertoDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
