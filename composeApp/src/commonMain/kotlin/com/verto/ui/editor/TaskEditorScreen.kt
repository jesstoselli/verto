package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.DueKind
import com.verto.ui.common.EditorSection
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.shortPt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

@Composable
fun TaskEditorScreen(
    taskId: String?,
    onClose: () -> Unit,
    viewModel: TaskEditorViewModel = koinViewModel(parameters = { parametersOf(TaskEditorArgs(taskId)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    EditorScaffold(title = if (state.id == null) "Nova tarefa" else "Tarefa", onBack = onClose) {
        if (!state.loaded) return@EditorScaffold
        EditorSection(title = "A tarefa") {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("O que é") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas (opcional)") },
                minLines = 2,
            )
            FilterChip(selected = state.pinned, onClick = { viewModel.setPinned(!state.pinned) }, label = { Text("Pra hoje") })
        }

        EditorSection(title = "Quando", hint = "Prazo real chama atenção. Data flexível não vira cobrança.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { pickingDate = true }, label = { Text(state.dueDate?.shortPt() ?: "Escolher data") })
                if (state.dueDate != null) {
                    TextButton(onClick = { viewModel.setDueDate(null) }) { Text("Sem data") }
                }
            }
            if (state.dueDate != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.dueKind == DueKind.Hard, { viewModel.setDueKind(DueKind.Hard) }, label = { Text("Prazo real") })
                    FilterChip(state.dueKind == DueKind.Soft, { viewModel.setDueKind(DueKind.Soft) }, label = { Text("Seria bom até") })
                }
            }
        }

        EditorSection(title = "Passos", hint = "Quebra até o menor movimento possível, como “abrir o site da Receita”.") {
            StepListEditor(
                steps = state.steps,
                onAdd = viewModel::addStep,
                onTitle = viewModel::setStepTitle,
                onEnergy = viewModel::setStepEnergy,
                onMove = viewModel::moveStep,
                onRemove = viewModel::removeStep,
            )
        }

        Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        if (state.id != null) {
            OutlinedButton(onClick = viewModel::toggleDone, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.done) "Reabrir" else "Concluir")
            }
            TextButton(onClick = viewModel::delete) { Text("Apagar tarefa") }
        }
    }

    if (pickingDate) {
        DueDatePicker(
            initial = state.dueDate,
            onPick = {
                viewModel.setDueDate(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
fun DueDatePicker(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The Material picker speaks UTC-midnight millis; converting through UTC keeps the calendar date intact.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis == null) onDismiss() else onPick(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
            }) { Text("Ok") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = pickerState)
    }
}
