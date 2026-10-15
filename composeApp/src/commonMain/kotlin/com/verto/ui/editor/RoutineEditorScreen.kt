package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.Plan
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.Hint
import com.verto.ui.common.weekdayShortNames
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RoutineEditorScreen(
    routineId: String?,
    fromInboxId: String?,
    onNewAnchor: () -> Unit,
    onClose: () -> Unit,
    viewModel: RoutineEditorViewModel = koinViewModel(parameters = { parametersOf(RoutineEditorArgs(routineId, fromInboxId)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    EditorScaffold(title = if (state.id == null) "Nova rotina" else "Rotina", onBack = onClose) {
        if (!state.loaded) return@EditorScaffold
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome") },
            singleLine = true,
        )

        Text("Depois de quê?", style = MaterialTheme.typography.titleSmall)
        if (state.anchors.isEmpty()) Hint("Você ainda não tem âncoras.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.anchors.forEach { anchor ->
                FilterChip(selected = state.anchorId == anchor.id, onClick = { viewModel.setAnchor(anchor.id) }, label = { Text(anchor.name) })
            }
            TextButton(onClick = onNewAnchor) { Text("Nova âncora") }
        }

        Text("Dias", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekdayShortNames.forEach { (day, name) ->
                FilterChip(selected = day in state.weekdays, onClick = { viewModel.toggleWeekday(day) }, label = { Text(name) })
            }
        }

        Text("Plano A · ideal", style = MaterialTheme.typography.titleSmall)
        Hint("O que você faria num dia bom.")
        StepListEditor(
            steps = state.stepsA,
            onAdd = { viewModel.addStep(Plan.A) },
            onTitle = { key, title -> viewModel.setStepTitle(Plan.A, key, title) },
            onEnergy = { key, energy -> viewModel.setStepEnergy(Plan.A, key, energy) },
            onMove = { key, delta -> viewModel.moveStep(Plan.A, key, delta) },
            onRemove = { key -> viewModel.removeStep(Plan.A, key) },
        )

        Text("Plano C · mínimo", style = MaterialTheme.typography.titleSmall)
        Hint("O mínimo que ainda conta. Ex.: 5 minutos de alongamento.")
        StepListEditor(
            steps = state.stepsC,
            onAdd = { viewModel.addStep(Plan.C) },
            onTitle = { key, title -> viewModel.setStepTitle(Plan.C, key, title) },
            onEnergy = { key, energy -> viewModel.setStepEnergy(Plan.C, key, energy) },
            onMove = { key, delta -> viewModel.moveStep(Plan.C, key, delta) },
            onRemove = { key -> viewModel.removeStep(Plan.C, key) },
        )

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        if (state.id != null) {
            TextButton(onClick = viewModel::delete) { Text("Apagar rotina") }
        }
    }
}
