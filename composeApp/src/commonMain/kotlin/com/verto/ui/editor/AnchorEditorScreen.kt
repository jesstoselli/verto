package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.common.ClockMask
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.Hint
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AnchorEditorScreen(
    anchorId: String?,
    onClose: () -> Unit,
    viewModel: AnchorEditorViewModel = koinViewModel(parameters = { parametersOf(AnchorEditorArgs(anchorId)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    EditorScaffold(title = if (state.id == null) "Nova âncora" else "Âncora", onBack = onClose) {
        if (!state.loaded) return@EditorScaffold
        Hint("Um momento do seu dia que puxa as rotinas, tipo \"Acordei\" ou \"Cheguei em casa\".")
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome") },
            singleLine = true,
        )
        Text("Janela de horário", style = MaterialTheme.typography.titleSmall)
        Hint("Se você não tocar na âncora, ela dispara sozinha quando a janela abre.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                state.start,
                viewModel::setStart,
                Modifier.weight(1f),
                label = { Text("De") },
                placeholder = { Text("12:00") },
                singleLine = true,
                visualTransformation = ClockMask,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                state.end,
                viewModel::setEnd,
                Modifier.weight(1f),
                label = { Text("Até") },
                placeholder = { Text("14:00") },
                singleLine = true,
                visualTransformation = ClockMask,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        if (state.id != null) {
            TextButton(onClick = viewModel::delete) { Text("Apagar âncora") }
        }
    }
}
