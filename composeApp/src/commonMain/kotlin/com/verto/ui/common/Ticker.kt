package com.verto.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Drives "now" for screens that change with the clock (anchor windows, the 04:00 day turn). */
fun interface Ticker {
    fun ticks(): Flow<Instant>
}

class ClockTicker(private val clock: Clock, private val every: Duration = 1.minutes) : Ticker {
    override fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(every)
        }
    }
}
