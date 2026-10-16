package com.verto.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.verto.ui.common.BrandListRow
import com.verto.ui.common.FoldedSurface
import com.verto.ui.common.Hint
import com.verto.ui.common.ScreenHeader
import com.verto.ui.common.SectionHeader
import com.verto.ui.common.hhmm
import com.verto.ui.common.label
import com.verto.ui.common.relativeDay
import com.verto.ui.common.shortPt
import com.verto.ui.common.softDateLabel
import com.verto.ui.theme.AnchorAccent
import com.verto.ui.theme.RoutineAccent
import com.verto.ui.theme.TaskAccent
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlanScreen(
    onOpenTask: (String?) -> Unit,
    onOpenRoutine: (String?) -> Unit,
    onOpenAnchor: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(
                title = "Trajeto",
                subtitle = "Dá forma ao que vem depois.",
                action = {
                    val (newLabel, onNew) = when (state.selectedTab) {
                        PlanTab.Tasks -> "Nova tarefa" to { onOpenTask(null) }
                        PlanTab.Routines -> "Nova rotina" to { onOpenRoutine(null) }
                        PlanTab.Anchors -> "Nova âncora" to { onOpenAnchor(null) }
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = newLabel) }
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") }
                    }
                },
            )
        }
        item {
            Text(
                trajectorySummary(state.taskCount, state.routineCount, state.anchorCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { PlanTabBar(selected = state.selectedTab, onSelect = viewModel::selectTab) }

        when (state.selectedTab) {
            PlanTab.Tasks -> {
                item { SectionHeader("Por decidir", accent = TaskAccent) }
                if (state.openTasks.isEmpty()) {
                    item {
                        EmptyAction(
                            hint = "Nenhuma tarefa aberta. Quando algo pedir mais de um passo, ele mora aqui.",
                            action = "Nova tarefa",
                            onAction = { onOpenTask(null) },
                        )
                    }
                }
                items(state.openTasks, key = { it.id }) { task ->
                    TaskRow(task, state.today, muted = false, onClick = { onOpenTask(task.id) }, onUnpin = { viewModel.unpin(task) })
                }
                if (state.doneTasks.isNotEmpty()) {
                    item {
                        TextButton(onClick = viewModel::toggleShowDone) {
                            Text(if (state.showDone) "Esconder feitas" else "Ver feitas")
                        }
                    }
                    if (state.showDone) {
                        items(state.doneTasks, key = { "done-${it.id}" }) { task ->
                            TaskRow(task, state.today, muted = true, onClick = { onOpenTask(task.id) }, onUnpin = null)
                        }
                    }
                }
            }
            PlanTab.Routines -> {
                item { SectionHeader("Seus ritmos", accent = RoutineAccent) }
                if (state.routines.isEmpty()) {
                    item {
                        // Without an anchor a routine cannot be saved, so the way forward is the anchor editor.
                        if (state.anchors.isEmpty()) {
                            EmptyAction(
                                hint = "Crie uma âncora primeiro: é ela que diz quando a rotina acontece.",
                                action = "Nova âncora",
                                onAction = { onOpenAnchor(null) },
                            )
                        } else {
                            EmptyAction(
                                hint = "Nenhuma rotina ainda. Uma rotina acontece depois de uma âncora, com um plano ideal e um mínimo.",
                                action = "Nova rotina",
                                onAction = { onOpenRoutine(null) },
                            )
                        }
                    }
                }
                items(state.routines, key = { "routine-${it.routine.id}" }) { row ->
                    BrandListRow(
                        title = row.routine.name,
                        subtitle = listOfNotNull(row.anchorName?.let { "depois de “$it”" }, row.routine.weekdays.label()).joinToString(" · "),
                        accent = RoutineAccent,
                        onClick = { onOpenRoutine(row.routine.id) },
                    )
                }
            }
            PlanTab.Anchors -> {
                item { SectionHeader("Pontos do dia", accent = AnchorAccent) }
                if (state.anchors.isEmpty()) {
                    item {
                        EmptyAction(
                            hint = "Âncoras são momentos do seu dia, como “Acordei” ou “Almocei”. As rotinas acontecem depois delas.",
                            action = "Nova âncora",
                            onAction = { onOpenAnchor(null) },
                        )
                    }
                }
                items(state.anchors, key = { "anchor-${it.id}" }) { anchor ->
                    BrandListRow(
                        title = anchor.name,
                        subtitle = "${anchor.windowStart.hhmm()}–${anchor.windowEnd.hhmm()}",
                        accent = AnchorAccent,
                        onClick = { onOpenAnchor(anchor.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, today: LocalDate?, muted: Boolean, onClick: () -> Unit, onUnpin: (() -> Unit)?) {
    FoldedSurface(
        modifier = Modifier.fillMaxWidth(),
        foldColor = TaskAccent.copy(alpha = 0.72f),
        foldSize = 12.dp,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp)) { Surface(Modifier.fillMaxSize(), color = TaskAccent) {} }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp).weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                val due = task.dueDate
                when {
                    onUnpin == null -> Unit
                    task.pinnedToToday -> FilterChip(selected = true, onClick = onUnpin, label = { Text("Pra hoje") })
                    due != null && today != null -> Text(
                        relativeDay(due, today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** An empty tab explains its role and offers the one action that fills it. */
@Composable
private fun EmptyAction(hint: String, action: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Hint(hint)
        TextButton(onClick = onAction) { Text(action) }
    }
}
