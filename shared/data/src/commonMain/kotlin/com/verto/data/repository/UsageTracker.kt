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
     * Called every time the app comes to the front. Says whether this visit comes after a long
     * enough absence to offer a clean day. While an offer is pending the visit is not recorded:
     * if the app is killed or closed before she answers, the next open offers it again.
     */
    suspend fun checkIn(): Boolean {
        val now = clock.now()
        val offer = AbsenceRules.shouldOfferFreshStart(store.lastOpenedAt(), now, settings.current().absenceThreshold)
        if (!offer) store.setLastOpenedAt(now)
        return offer
    }

    /** She chose Reset or "Ver como estava": this return is settled. */
    suspend fun answered() = store.setLastOpenedAt(clock.now())
}
