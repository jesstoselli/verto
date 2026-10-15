package com.verto.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class Session(val accessToken: String, val refreshToken: String, val email: String)

/** Where the tokens live: private, never in Room, never in a backup (see androidApp's PrefsTokenStore). */
interface TokenStore {
    fun load(): Session?
    fun save(session: Session?)
}

class InMemoryTokenStore(private var session: Session? = null) : TokenStore {
    override fun load(): Session? = session
    override fun save(session: Session?) {
        this.session = session
    }
}

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val email: String) : AccountState

    /** The server stopped accepting the refresh token; local data is untouched. */
    data class Expired(val email: String) : AccountState
}

class SignedOutException : Exception("no session")

class SessionExpiredException : Exception("session expired")

class AuthSession(private val api: VertoApi, private val tokens: TokenStore) {

    private val refreshLock = Mutex()
    private val _state = MutableStateFlow(tokens.load()?.let { AccountState.SignedIn(it.email) } ?: AccountState.SignedOut)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    suspend fun login(email: String, password: String) {
        val auth = api.login(email.trim(), password)
        tokens.save(Session(auth.accessToken, auth.refreshToken, auth.user.email))
        _state.value = AccountState.SignedIn(auth.user.email)
    }

    /** Forgets the tokens first: being offline must never keep her signed in. Telling the server is best-effort. */
    suspend fun logout() {
        val session = tokens.load() ?: return
        tokens.save(null)
        _state.value = AccountState.SignedOut
        try {
            api.logout(session.refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The token simply expires on its own.
        }
    }

    suspend fun <T> authorized(call: suspend (accessToken: String) -> T): T {
        val session = tokens.load() ?: throw SignedOutException()
        return try {
            call(session.accessToken)
        } catch (_: UnauthorizedException) {
            call(refreshedAfter(session.accessToken))
        }
    }

    /**
     * One refresh at a time: the server rotates refresh tokens and treats a reused one as stolen,
     * revoking the whole family. A caller that waited finds the new token already there.
     */
    private suspend fun refreshedAfter(staleAccessToken: String): String = refreshLock.withLock {
        val current = tokens.load() ?: throw SignedOutException()
        if (current.accessToken != staleAccessToken) return current.accessToken
        val auth = try {
            api.refresh(current.refreshToken)
        } catch (_: UnauthorizedException) {
            tokens.save(null)
            _state.value = AccountState.Expired(current.email)
            throw SessionExpiredException()
        }
        tokens.save(current.copy(accessToken = auth.accessToken, refreshToken = auth.refreshToken))
        auth.accessToken
    }
}
