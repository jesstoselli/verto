package com.verto.ui.account

import com.verto.data.remote.AccountState
import com.verto.data.sync.SyncPhase
import com.verto.data.sync.SyncStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusLineTest {

    private val zone = TimeZone.of("America/Sao_Paulo")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 10, day, hour, minute).toInstant(zone)
    private val signedIn = AccountState.SignedIn("jess@verto.app")

    @Test
    fun signedOutOrExpired_comesFirst() {
        val status = SyncStatus(SyncPhase.Idle, at(15, 9))
        assertEquals("Entre pra guardar seus dados no servidor.", syncStatusLine(AccountState.SignedOut, status, at(15, 10), zone))
        assertEquals(
            "Sua sessão expirou. Entra de novo: seus dados continuam aqui.",
            syncStatusLine(AccountState.Expired("jess@verto.app"), status, at(15, 10), zone),
        )
    }

    @Test
    fun phases_readCalmly() {
        assertEquals("Sincronizando…", syncStatusLine(signedIn, SyncStatus(SyncPhase.Running, null), at(15, 10), zone))
        assertEquals(
            "Sem conexão agora. Tento de novo sozinho.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Offline, at(15, 9)), at(15, 10), zone),
        )
        assertEquals("Ainda não sincronizou.", syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, null), at(15, 10), zone))
    }

    @Test
    fun lastSync_saysWhen() {
        assertEquals(
            "Tudo guardado. Última vez hoje às 09:05.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, at(15, 9, 5)), at(15, 10), zone),
        )
        assertEquals(
            "Tudo guardado. Última vez dia 12/10 às 22:40.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, at(12, 22, 40)), at(15, 10), zone),
        )
    }

    @Test
    fun refusedRecords_getOneQuietSentence() {
        assertEquals(
            "Tudo guardado. Última vez hoje às 09:05. Algumas mudanças não foram aceitas pelo servidor.",
            syncStatusLine(signedIn, SyncStatus(SyncPhase.Idle, at(15, 9, 5), someRejected = true), at(15, 10), zone),
        )
    }
}
