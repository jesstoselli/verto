package com.verto.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.common.EmptyState
import com.verto.ui.common.FoldedSurface
import com.verto.ui.common.ScreenHeader
import com.verto.ui.common.SectionHeader
import com.verto.ui.theme.BettaPeriwinkle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InboxScreen(onMakeRoutine: (String) -> Unit, viewModel: InboxViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Rascunho",
                subtitle = "Tira da cabeça sem decidir.",
            )
        }
        item {
            FoldedSurface(
                modifier = Modifier.fillMaxWidth(),
                foldColor = MaterialTheme.colorScheme.tertiary,
                foldSize = 20.dp,
            ) {
                Column(
                    Modifier.padding(start = 16.dp, end = 28.dp, top = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("O que apareceu?", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = viewModel::onDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escreve do jeito que vier.") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.capture() }),
                        trailingIcon = {
                            TextButton(onClick = viewModel::capture, enabled = state.draft.isNotBlank()) { Text("Guardar") }
                        },
                    )
                }
            }
        }
        if (state.raw.isEmpty()) {
            item {
                EmptyState(
                    title = "A cabeça respirou.",
                    body = "Nada aqui virou obrigação. Você escolhe o que ganha caminho quando estiver pronta.",
                )
            }
        }
        items(state.raw, key = { it.id }) { item ->
            InboxRow(
                text = item.text,
                muted = false,
                actions = listOf(
                    "Virar tarefa" to { viewModel.toTask(item) },
                    "Virar rotina" to { onMakeRoutine(item.id) },
                    "Incubar" to { viewModel.incubate(item.id) },
                    "Descartar" to { viewModel.discard(item.id) },
                ),
            )
        }
        item {
            SectionHeader(
                title = "Incubados",
                actionLabel = if (state.showIncubated) "Esconder" else "Ver",
                onAction = viewModel::toggleIncubated,
                accent = BettaPeriwinkle,
            )
        }
        if (state.showIncubated && state.incubated.isEmpty()) {
            item { Text("Nada incubado.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.showIncubated) {
            items(state.incubated, key = { "incubated-${it.id}" }) { item ->
                InboxRow(
                    text = item.text,
                    muted = true,
                    actions = listOf(
                        "Trazer de volta" to { viewModel.restore(item.id) },
                        "Descartar" to { viewModel.discard(item.id) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun InboxRow(text: String, muted: Boolean, actions: List<Pair<String, () -> Unit>>) {
    var menuOpen by remember { mutableStateOf(false) }
    FoldedSurface(
        modifier = Modifier.fillMaxWidth(),
        foldColor = if (muted) BettaPeriwinkle.copy(alpha = 0.55f) else MaterialTheme.colorScheme.secondary,
        foldSize = 10.dp,
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ações")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    actions.forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                menuOpen = false
                                action()
                            },
                        )
                    }
                }
            }
        }
    }
}
