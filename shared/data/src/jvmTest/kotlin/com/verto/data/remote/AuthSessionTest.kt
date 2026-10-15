package com.verto.data.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthSessionTest {

    private val server = FakeVertoServer()

    @Test
    fun login_storesTheSession_andSignsIn() = runTest {
        val tokens = InMemoryTokenStore()
        val session = AuthSession(server.api, tokens)

        session.login("  jess@verto.app ", server.password)

        assertEquals(AccountState.SignedIn(server.email), session.state.value)
        assertEquals(server.email, tokens.load()?.email)
    }

    @Test
    fun login_wrongPassword_staysSignedOut() = runTest {
        val session = AuthSession(server.api, InMemoryTokenStore())

        val error = assertFailsWith<UnauthorizedException> { session.login(server.email, "errada") }

        assertEquals("invalid_credentials", error.code)
        assertEquals(AccountState.SignedOut, session.state.value)
    }

    @Test
    fun authorized_withoutASession_isSignedOut() = runTest {
        val session = AuthSession(server.api, InMemoryTokenStore())

        assertFailsWith<SignedOutException> { session.authorized { server.api.pull(it, 0, 10) } }
    }

    @Test
    fun authorized_on401_refreshesOnce_andRetries() = runTest {
        val tokens = InMemoryTokenStore(server.signedInSession())
        val session = AuthSession(server.api, tokens)
        server.expireAccessTokens()

        val page = session.authorized { server.api.pull(it, 0, 10) }

        assertEquals(0, page.changes.size)
        assertEquals(1, server.refreshCalls)
    }

    @Test
    fun concurrent401s_shareOneRefresh() = runTest {
        val session = AuthSession(server.api, InMemoryTokenStore(server.signedInSession()))
        server.expireAccessTokens()
        val gate = CompletableDeferred<Unit>()
        server.refreshGate = gate

        val calls = List(2) { async { session.authorized { server.api.pull(it, 0, 10) } } }
        repeat(5) { yield() }
        gate.complete(Unit)
        calls.awaitAll()

        assertEquals(1, server.refreshCalls)
        assertEquals(AccountState.SignedIn(server.email), session.state.value)
    }

    @Test
    fun refreshRefused_expiresTheSession_andForgetsTheTokens() = runTest {
        val tokens = InMemoryTokenStore(server.signedInSession())
        val session = AuthSession(server.api, tokens)
        server.expireAccessTokens()
        server.revokeRefreshTokens()

        assertFailsWith<SessionExpiredException> { session.authorized { server.api.pull(it, 0, 10) } }

        assertEquals(AccountState.Expired(server.email), session.state.value)
        assertNull(tokens.load())
    }

    @Test
    fun logout_forgetsTheTokens_evenOffline() = runTest {
        val tokens = InMemoryTokenStore(server.signedInSession())
        val session = AuthSession(server.api, tokens)
        server.offline = true

        session.logout()

        assertNull(tokens.load())
        assertEquals(AccountState.SignedOut, session.state.value)
    }
}
