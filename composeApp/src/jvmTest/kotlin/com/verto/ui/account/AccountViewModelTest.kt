package com.verto.ui.account

import com.verto.data.ZoneProvider
import com.verto.data.remote.AccountState
import com.verto.data.remote.AuthSession
import com.verto.data.remote.InMemoryTokenStore
import com.verto.data.remote.VertoApi
import com.verto.data.remote.vertoHttpClient
import com.verto.data.sync.SyncOutcome
import com.verto.data.sync.SyncScheduler
import com.verto.ui.TestClock
import com.verto.ui.TestZone
import com.verto.ui.ViewModelTest
import com.verto.ui.uiAt
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountViewModelTest : ViewModelTest() {

    private var answer: String = "ok"
    private var syncs = 0

    private val engine = MockEngine { _ ->
        when (answer) {
            "ok" -> respond(
                """{"accessToken":"a","refreshToken":"r","user":{"id":"1","email":"jess@verto.app","displayName":"Jess"}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
            "wrong" -> respond(
                """{"error":"invalid_credentials"}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
            else -> throw IOException("sem rede")
        }
    }

    private fun viewModel(): AccountViewModel {
        val session = AuthSession(VertoApi(vertoHttpClient(engine), "https://fake.verto"), InMemoryTokenStore())
        val scheduler = SyncScheduler(
            sync = {
                syncs++
                SyncOutcome.SignedOut
            },
            pendingChanges = flowOf(0),
            lastSyncedAt = { null },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        return AccountViewModel(session, scheduler, TestClock(uiAt(15, 10)), ZoneProvider { TestZone })
    }

    @Test
    fun login_signsIn_clearsThePassword_andSyncs() = runTest {
        val vm = viewModel()
        vm.setEmail("jess@verto.app")
        vm.setPassword("segredo")

        vm.login()

        val state = vm.state.first { it.account is AccountState.SignedIn }
        assertEquals("", state.password)
        assertEquals(1, syncs)
    }

    @Test
    fun wrongPassword_explainsWithoutDrama() = runTest {
        answer = "wrong"
        val vm = viewModel()
        vm.setEmail("jess@verto.app")
        vm.setPassword("errada")

        vm.login()

        assertEquals("E-mail ou senha não conferem.", vm.state.first { it.message != null }.message)
    }

    @Test
    fun offline_saysSoAndKeepsWhatSheTyped() = runTest {
        answer = "offline"
        val vm = viewModel()
        vm.setEmail("jess@verto.app")
        vm.setPassword("segredo")

        vm.login()

        val state = vm.state.first { it.message != null }
        assertEquals("Sem conexão agora. Tenta de novo daqui a pouco.", state.message)
        assertEquals("jess@verto.app", state.email)
    }

    @Test
    fun blankFields_askForThem() = runTest {
        val vm = viewModel()

        vm.login()

        assertEquals("Preenche e-mail e senha.", vm.state.value.message)
    }
}
