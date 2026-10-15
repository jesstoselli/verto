package com.verto.data.repository

import com.verto.data.sync.LocalStore
import com.verto.domain.SettingsSource
import com.verto.domain.rules.AbsenceRules
import kotlin.time.Clock

class UsageTracker(
    private val store: LocalStore,
    private val settings: SettingsSource,
    private val clock: Clock,
) {
    /**
     * Called every time the app comes to the front. Records the visit and says whether this one
     * comes after a long enough absence to offer a clean day; the next call already says no.
     */
    suspend fun checkIn(): Boolean {
        val now = clock.now()
        val last = store.lastOpenedAt()
        store.setLastOpenedAt(now)
        return AbsenceRules.shouldOfferFreshStart(last, now, settings.current().absenceThreshold)
    }
}
