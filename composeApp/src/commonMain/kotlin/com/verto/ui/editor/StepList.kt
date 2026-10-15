package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verto.data.repository.StepDraft
import com.verto.domain.model.StepEnergy
import com.verto.ui.common.label

/** [key] is a UI-only identity so rows keep focus while titles change; [id] is the stored step, if any. */
data class EditableStep(val key: String, val id: String?, val title: String, val energy: StepEnergy)

class KeyFactory {
    private var next = 0
    fun next(): String = "k${next++}"
}

object StepListOps {

    fun add(steps: List<EditableStep>, key: String): List<EditableStep> =
        steps + EditableStep(key, null, "", StepEnergy.Low)

    fun update(steps: List<EditableStep>, key: String, change: (EditableStep) -> EditableStep): List<EditableStep> =
        steps.map { if (it.key == key) change(it) else it }

    fun remove(steps: List<EditableStep>, key: String): List<EditableStep> = steps.filterNot { it.key == key }

    fun move(steps: List<EditableStep>, key: String, delta: Int): List<EditableStep> {
        val from = steps.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in steps.indices) return steps
        return steps.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun toDrafts(steps: List<EditableStep>): List<StepDraft> =
        steps.filter { it.title.isNotBlank() }.map { StepDraft(it.id, it.title.trim(), it.energy) }
}

@Composable
fun StepListEditor(
    steps: List<EditableStep>,
    onAdd: () -> Unit,
    onTitle: (String, String) -> Unit,
    onEnergy: (String, StepEnergy) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = step.title,
                        onValueChange = { onTitle(step.key, it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Passo ${index + 1}") },
                        singleLine = true,
                    )
                    IconButton(onClick = { onMove(step.key, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir")
                    }
                    IconButton(onClick = { onMove(step.key, 1) }, enabled = index < steps.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descer")
                    }
                    IconButton(onClick = { onRemove(step.key) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepEnergy.entries.forEach { energy ->
                        FilterChip(
                            selected = step.energy == energy,
                            onClick = { onEnergy(step.key, energy) },
                            label = { Text(energy.label()) },
                        )
                    }
                }
            }
        }
        TextButton(onClick = onAdd) { Text("Adicionar passo") }
    }
}
