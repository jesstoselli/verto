package com.verto.domain.rules

import kotlin.time.Duration
import kotlin.time.Instant

object AbsenceRules {

    fun shouldOfferFreshStart(lastOpenedAt: Instant?, now: Instant, threshold: Duration): Boolean =
        lastOpenedAt != null && now - lastOpenedAt >= threshold
}
