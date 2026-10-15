package com.verto.domain.rules

import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxState
import kotlin.time.Duration
import kotlin.time.Instant

object InboxRules {

    /** Moved aside silently: an untouched braindump should never turn into guilt. */
    fun toIncubate(items: List<InboxItem>, now: Instant, after: Duration): List<InboxItem> =
        items.filter { it.state == InboxState.Raw && now - it.rawSince >= after }
}
