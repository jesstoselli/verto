package com.verto.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verto.domain.model.StepEnergy
import com.verto.domain.rules.TunnelItem
import com.verto.ui.common.Hint
import com.verto.ui.common.label

internal fun TunnelItem.title(): String = when (this) {
    is TunnelItem.RoutineStep -> step.title
    is TunnelItem.TaskStep -> step?.title ?: task.title
}

internal fun TunnelItem.context(): String = when (this) {
    is TunnelItem.RoutineStep -> "${anchor.name} · ${routine.name}"
    is TunnelItem.TaskStep -> if (step != null) task.title else "Tarefa de hoje"
}

internal fun TunnelItem.energy(): StepEnergy? = when (this) {
    is TunnelItem.RoutineStep -> step.energy
    is TunnelItem.TaskStep -> step?.energy
}

internal fun TunnelItem.stepId(): String? = when (this) {
    is TunnelItem.RoutineStep -> step.id
    is TunnelItem.TaskStep -> step?.id
}

/** Everything else out of sight: one thing, and the four ways out of it. */
@Composable
internal fun TunnelOverlay(
    item: TunnelItem?,
    canSkip: Boolean,
    onDone: (TunnelItem) -> Unit,
    onOther: () -> Unit,
    onSplit: (stepId: String, titles: List<String>) -> Unit,
    onExit: () -> Unit,
) {
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (item == null) {
                    Text("Nada pra agora. Tá tudo bem.", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Sair") }
                    return@Column
                }
                var splitting by remember(item.key) { mutableStateOf(false) }
                Text(item.context(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.title(), style = MaterialTheme.typography.headlineMedium)
                item.energy()?.let {
                    Text(it.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                val stepId = item.stepId()
                if (splitting && stepId != null) {
                    SplitForm(
                        onCancel = { splitting = false },
                        onConfirm = { titles ->
                            onSplit(stepId, titles)
                            splitting = false
                        },
                    )
                } else {
                    Button(onClick = { onDone(item) }, modifier = Modifier.fillMaxWidth()) { Text("Feito") }
                    OutlinedButton(onClick = onOther, enabled = canSkip, modifier = Modifier.fillMaxWidth()) { Text("Outro") }
                    if (stepId != null) {
                        TextButton(onClick = { splitting = true }) { Text("Tá grande demais") }
                    }
                    TextButton(onClick = onExit) { Text("Sair") }
                }
            }
        }
    }
}

@Composable
private fun SplitForm(onCancel: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var pieces by remember { mutableStateOf(listOf("", "")) }
    Hint("Quebra em pedaços menores. O primeiro vira o próximo.")
    pieces.forEachIndexed { index, text ->
        OutlinedTextField(
            value = text,
            onValueChange = { value -> pieces = pieces.toMutableList().also { it[index] = value } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pedaço ${index + 1}") },
            singleLine = true,
        )
    }
    TextButton(onClick = { pieces = pieces + "" }) { Text("Mais um pedaço") }
    Button(onClick = { onConfirm(pieces) }, enabled = pieces.any { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) {
        Text("Trocar por esses passos")
    }
    TextButton(onClick = onCancel) { Text("Cancelar") }
}
