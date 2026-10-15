package com.verto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.common.ClockMask
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.EditorSection
import com.verto.ui.common.Hint
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onOpenAccount: () -> Unit, onClose: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EditorScaffold(title = "Ajustes", onBack = onClose) {
        EditorSection("Virada do dia", hint = "Antes desse horário ainda conta como ontem.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).forEach { hour ->
                    FilterChip(
                        selected = state.dayStartHour == hour,
                        onClick = { viewModel.setDayStartHour(hour) },
                        label = { Text("0$hour:00") },
                    )
                }
            }
        }
        EditorSection("Volta depois de um tempo", hint = "Depois de quantos dias sem abrir o Verto oferece um dia limpo.") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.changeAbsenceDays(-1) }) { Text("−") }
                Text("${state.absenceDays} dias", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.changeAbsenceDays(+1) }) { Text("+") }
            }
        }
        EditorSection("Horário de silêncio", hint = "Quando o Verto tiver notificações, elas ficam quietas nesse intervalo.") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.quietStart,
                    onValueChange = viewModel::setQuietStart,
                    modifier = Modifier.weight(1f),
                    label = { Text("De") },
                    singleLine = true,
                    visualTransformation = ClockMask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = state.quietEnd,
                    onValueChange = viewModel::setQuietEnd,
                    modifier = Modifier.weight(1f),
                    label = { Text("Até") },
                    singleLine = true,
                    visualTransformation = ClockMask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Button(onClick = viewModel::saveQuietHours, modifier = Modifier.fillMaxWidth()) { Text("Salvar horário") }
            state.message?.let { Hint(it) }
        }
        EditorSection("Dia limpo", hint = "Tira tudo de Pra hoje e guarda os despejos soltos. Nada é apagado.") {
            OutlinedButton(onClick = viewModel::freshStart, modifier = Modifier.fillMaxWidth()) { Text("Começar do zero") }
            if (state.cleaned) Hint("Pronto. O dia tá limpo.")
        }
        EditorSection("Conta e sync", hint = "Login, sair e quando foi a última sincronização.") {
            TextButton(onClick = onOpenAccount) { Text("Abrir Conta e sync") }
        }
    }
}
