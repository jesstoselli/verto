package com.verto.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.data.remote.AccountState
import com.verto.ui.common.EditorSection
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.Hint
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountScreen(onClose: () -> Unit, viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EditorScaffold(title = "Conta e sync", onBack = onClose) {
        EditorSection(title = "Sincronização") {
            Text(state.syncLine, style = MaterialTheme.typography.bodyLarge)
        }
        when (val account = state.account) {
            is AccountState.SignedIn -> {
                EditorSection(title = "Sua conta") {
                    Text(account.email, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) { Text("Sincronizar agora") }
                    TextButton(onClick = viewModel::logout) { Text("Sair desta conta") }
                    Hint("Sair não apaga nada deste celular.")
                }
            }
            else -> {
                EditorSection(title = "Entrar", hint = "Leva seu Verto com você entre os dispositivos.") {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::setEmail,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Button(onClick = viewModel::login, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Entrar") }
                    state.message?.let { Hint(it) }
                }
            }
        }
    }
}
