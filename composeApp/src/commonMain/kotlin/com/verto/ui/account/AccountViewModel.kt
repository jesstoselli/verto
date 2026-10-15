package com.verto.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.ZoneProvider
import com.verto.data.remote.AccountState
import com.verto.data.remote.AuthSession
import com.verto.data.remote.UnauthorizedException
import com.verto.data.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class AccountForm(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

data class AccountUiState(
    val account: AccountState = AccountState.SignedOut,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val syncLine: String = "",
)

class AccountViewModel(
    private val session: AuthSession,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {

    private val form = MutableStateFlow(AccountForm())

    val state: StateFlow<AccountUiState> = combine(session.state, scheduler.status, form) { account, status, f ->
        AccountUiState(
            account = account,
            email = f.email,
            password = f.password,
            busy = f.busy,
            message = f.message,
            syncLine = syncStatusLine(account, status, clock.now(), zones.current()),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountUiState(account = session.state.value))

    fun setEmail(value: String) = form.update { it.copy(email = value, message = null) }

    fun setPassword(value: String) = form.update { it.copy(password = value, message = null) }

    fun login() {
        val current = form.value
        if (current.busy) return
        if (current.email.isBlank() || current.password.isBlank()) {
            form.update { it.copy(message = "Preenche e-mail e senha.") }
            return
        }
        form.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                session.login(current.email, current.password)
                scheduler.syncNow()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: UnauthorizedException) {
                "E-mail ou senha não conferem."
            } catch (_: Exception) {
                "Sem conexão agora. Tenta de novo daqui a pouco."
            }
            form.update {
                if (message == null) AccountForm() else it.copy(busy = false, message = message)
            }
        }
    }

    fun syncNow() = scheduler.syncNow()

    fun logout() {
        viewModelScope.launch { session.logout() }
    }
}
