package com.verto.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.DueKind
import com.verto.domain.model.Task
import com.verto.ui.common.Hint
import com.verto.ui.common.ListRow
import com.verto.ui.common.SectionHeader
import com.verto.ui.common.hhmm
import com.verto.ui.common.label
import com.verto.ui.common.shortPt
import com.verto.ui.common.softDateLabel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlanScreen(
    onOpenTask: (String?) -> Unit,
    onOpenRoutine: (String?) -> Unit,
    onOpenAnchor: (String?) -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Tarefas", "Nova tarefa") { onOpenTask(null) } }
        if (state.openTasks.isEmpty()) item { Hint("Nenhuma tarefa aberta.") }
        items(state.openTasks, key = { it.id }) { task ->
            TaskRow(task, muted = false, onClick = { onOpenTask(task.id) }, onTogglePinned = { viewModel.togglePinned(task) })
        }
        if (state.doneTasks.isNotEmpty()) {
            item {
                TextButton(onClick = viewModel::toggleShowDone) { Text(if (state.showDone) "Esconder feitas" else "Ver feitas") }
            }
            if (state.showDone) {
                items(state.doneTasks, key = { "done-${it.id}" }) { task ->
                    TaskRow(task, muted = true, onClick = { onOpenTask(task.id) }, onTogglePinned = null)
                }
            }
        }

        item { SectionHeader("Rotinas", "Nova rotina") { onOpenRoutine(null) } }
        if (state.routines.isEmpty()) {
            item {
                Hint(
                    if (state.anchors.isEmpty()) "Crie uma âncora primeiro: é ela que diz quando a rotina acontece."
                    else "Nenhuma rotina ainda.",
                )
            }
        }
        items(state.routines, key = { "routine-${it.routine.id}" }) { row ->
            ListRow(
                title = row.routine.name,
                subtitle = listOfNotNull(row.anchorName?.let { "depois de \"$it\"" }, row.routine.weekdays.label()).joinToString(" · "),
                onClick = { onOpenRoutine(row.routine.id) },
            )
        }

        item { SectionHeader("Âncoras", "Nova âncora") { onOpenAnchor(null) } }
        if (state.anchors.isEmpty()) item { Hint("Âncoras são momentos do seu dia, tipo \"Acordei\" ou \"Almocei\".") }
        items(state.anchors, key = { "anchor-${it.id}" }) { anchor ->
            ListRow(anchor.name, "${anchor.windowStart.hhmm()}–${anchor.windowEnd.hhmm()}", onClick = { onOpenAnchor(anchor.id) })
        }
    }
}

@Composable
private fun TaskRow(task: Task, muted: Boolean, onClick: () -> Unit, onTogglePinned: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                task.dueDate?.let { due ->
                    Text(
                        if (task.dueKind == DueKind.Hard) "prazo ${due.shortPt()}" else softDateLabel(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onTogglePinned != null) {
                FilterChip(selected = task.pinnedToToday, onClick = onTogglePinned, label = { Text("Hoje") })
            }
        }
    }
}
