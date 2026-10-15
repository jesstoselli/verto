package com.verto.data

import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

fun interface IdGenerator {
    fun newId(): String
}

object UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}

/** Read on every use: the phone's zone can change while the app is open (travel). */
fun interface ZoneProvider {
    fun current(): TimeZone
}

object SystemZone : ZoneProvider {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}
