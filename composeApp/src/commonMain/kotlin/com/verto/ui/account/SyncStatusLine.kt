package com.verto.ui.account

import com.verto.data.remote.AccountState
import com.verto.data.sync.SyncPhase
import com.verto.data.sync.SyncStatus
import com.verto.ui.common.hhmm
import com.verto.ui.common.shortPt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The one place sync speaks: calm, no counts, no "erro". */
fun syncStatusLine(account: AccountState, status: SyncStatus, now: Instant, zone: TimeZone): String {
    when (account) {
        AccountState.SignedOut -> return "Entre pra guardar seus dados no servidor."
        is AccountState.Expired -> return "Sua sessão expirou. Entra de novo: seus dados continuam aqui."
        is AccountState.SignedIn -> Unit
    }
    val line = when (status.phase) {
        SyncPhase.Running -> "Sincronizando…"
        SyncPhase.Offline -> "Sem conexão agora. Tento de novo sozinho."
        SyncPhase.SignedOut -> "Entre pra guardar seus dados no servidor."
        SyncPhase.SessionExpired -> "Sua sessão expirou. Entra de novo: seus dados continuam aqui."
        SyncPhase.Idle -> status.lastSyncedAt?.let { last ->
            val local = last.toLocalDateTime(zone)
            val today = now.toLocalDateTime(zone).date
            val day = if (local.date == today) "hoje" else "dia ${local.date.shortPt()}"
            "Tudo guardado. Última vez $day às ${local.time.hhmm()}."
        } ?: "Ainda não sincronizou."
    }
    return if (status.someRejected && status.phase == SyncPhase.Idle) {
        "$line Algumas mudanças não foram aceitas pelo servidor."
    } else {
        line
    }
}
