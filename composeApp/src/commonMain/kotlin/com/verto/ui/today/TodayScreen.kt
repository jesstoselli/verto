package com.verto.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.Task
import com.verto.domain.rules.AnchorSection
import com.verto.domain.rules.RoutineCard
import com.verto.domain.rules.TaskCard
import com.verto.ui.common.EmptyState
import com.verto.ui.common.Hint
import com.verto.ui.common.ScreenHeader
import com.verto.ui.common.SectionHeader
import com.verto.ui.common.StepCheckRow
import com.verto.ui.common.deadlineLabel
import com.verto.ui.common.label
import com.verto.ui.common.longPt
import com.verto.ui.common.softDateLabel
import com.verto.ui.theme.EnergyPink
import com.verto.ui.theme.OnEnergyPink
import com.verto.ui.theme.TaskAccent
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

private data class FocusItem(
    val title: String,
    val context: String,
    val energy: StepEnergy?,
    val onComplete: () -> Unit,
)

@Composable
fun TodayScreen(onOpenTask: (String) -> Unit, viewModel: TodayViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val content = state as? TodayUiState.Content ?: return
    val today = content.today

    val routineFocus = today.sections.asSequence().flatMap { section ->
        section.routines.asSequence().mapNotNull { card ->
            val plan = content.shownPlans[card.routine.id] ?: card.suggestedPlan
            card.steps(plan).firstOrNull { !it.done }?.step?.let { step ->
                FocusItem(
                    title = step.title,
                    context = "${section.anchor.name} · ${card.routine.name}",
                    energy = step.energy,
                    onComplete = { viewModel.toggleRoutineStep(step.id) },
                )
            }
        }
    }.firstOrNull()
    val taskFocus = (today.pinned.asSequence() + today.deadlines.asSequence()).firstOrNull()?.let { card ->
        val step = card.nextStep
        FocusItem(
            title = step?.title ?: card.task.title,
            context = card.task.title.takeIf { step != null } ?: "Tarefa de hoje",
            energy = step?.energy,
            onComplete = if (step != null) {
                { viewModel.toggleTaskStep(step.id) }
            } else {
                { viewModel.completeTask(card.task.id) }
            },
        )
    }
    val focus = routineFocus ?: taskFocus

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "Hoje",
                subtitle = today.day.longPt().replaceFirstChar { it.uppercase() },
                action = { EnergyPicker(today.energy, viewModel::setEnergy) },
            )
        }

        if (focus != null) {
            item { FocusCard(focus) }
        }

        if (today.sections.isNotEmpty()) {
            item { SectionHeader("Fluxo do dia") }
            item(key = "anchor-flow") {
                Column {
                    today.sections.forEachIndexed { index, section ->
                        AnchorFlowCard(
                            section = section,
                            expanded = section.anchor.id in content.expandedIds,
                            isFirst = index == 0,
                            isLast = index == today.sections.lastIndex,
                            shownPlan = { card -> content.shownPlans[card.routine.id] ?: card.suggestedPlan },
                            onToggle = { viewModel.toggleSection(section.anchor.id) },
                            onTap = { viewModel.tapAnchor(section.anchor.id) },
                            onShowPlan = viewModel::showPlan,
                            onToggleStep = viewModel::toggleRoutineStep,
                            onSkip = viewModel::skip,
                            onUndo = viewModel::undo,
                        )
                    }
                }
            }
        }

        if (today.sections.isEmpty() && today.pinned.isEmpty() && today.deadlines.isEmpty() && today.recentlyDone.isEmpty()) {
            item {
                EmptyState(
                    title = "Nada pedindo sua atenção.",
                    body = "O dia pode começar leve. Quando aparecer algo, o Verto coloca no fluxo.",
                )
            }
        }
        if (today.pinned.isNotEmpty()) {
            item { SectionHeader("Pra hoje", accent = TaskAccent) }
            if (today.pinnedOverSoftLimit) {
                item { Hint("Tem mais de três coisas por aqui. Vai com calma e escolhe uma de cada vez.") }
            }
            items(today.pinned, key = { "pinned-${it.task.id}" }) { card ->
                TaskCardView(card, today.day, onOpenTask, viewModel::toggleTaskStep, viewModel::completeTask)
            }
        }
        if (today.deadlines.isNotEmpty()) {
            item { SectionHeader("Prazos chegando", accent = MaterialTheme.colorScheme.tertiary) }
            items(today.deadlines, key = { "deadline-${it.task.id}" }) { card ->
                TaskCardView(card, today.day, onOpenTask, viewModel::toggleTaskStep, viewModel::completeTask)
            }
        }
        if (today.recentlyDone.isNotEmpty()) {
            item(key = "recently-done") { RecentlyDone(today.recentlyDone, onOpenTask) }
        }
    }
}

@Composable
private fun EnergyPicker(energy: EnergyLevel, onPick: (EnergyLevel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(energy.label()) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = EnergyPink,
                labelColor = OnEnergyPink,
                trailingIconContentColor = OnEnergyPink,
            ),
            border = null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EnergyLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label()) },
                    onClick = {
                        open = false
                        onPick(level)
                    },
                    trailingIcon = if (level == energy) {
                        { Icon(Icons.Filled.Check, contentDescription = "Atual") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun FocusCard(focus: FocusItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Só 1 coisa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text(focus.title, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    focus.context,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                )
                focus.energy?.let {
                    Text(it.label(), style = MaterialTheme.typography.labelMedium)
                }
                Button(onClick = focus.onComplete) { Text("Feito") }
            }
        }
    }
}

@Composable
private fun RecentlyDone(tasks: List<Task>, onOpenTask: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Feito recentemente", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (open) {
            tasks.forEach { task ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(5.dp).size(16.dp),
                        )
                    }
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AnchorFlowCard(
    section: AnchorSection,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    shownPlan: (RoutineCard) -> Plan,
    onToggle: () -> Unit,
    onTap: () -> Unit,
    onShowPlan: (String, Plan) -> Unit,
    onToggleStep: (String) -> Unit,
    onSkip: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = when {
        section.triggered -> MaterialTheme.colorScheme.secondary
        expanded -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(Modifier.fillMaxHeight().width(30.dp)) {
            val x = size.width / 2f
            val middle = 28.dp.toPx()
            if (!isFirst) drawLine(railColor, Offset(x, 0f), Offset(x, middle), strokeWidth = 2.dp.toPx())
            if (!isLast) drawLine(railColor, Offset(x, middle), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(dotColor, radius = if (expanded) 7.dp.toPx() else 5.dp.toPx(), center = Offset(x, middle))
            drawCircle(Color.White.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = Offset(x, middle))
        }
        Surface(
            modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 10.dp),
            color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onToggle),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(section.anchor.name, style = MaterialTheme.typography.titleMedium)
                        if (!expanded) {
                            Text(
                                section.routines.joinToString(" · ") { it.routine.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!section.triggered) {
                        OutlinedButton(onClick = onTap) { Text("Já foi") }
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "Âncora concluída", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (expanded) {
                    section.routines.forEach { card ->
                        RoutineBlock(card, shownPlan(card), onShowPlan, onToggleStep, onSkip, onUndo)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineBlock(
    card: RoutineCard,
    plan: Plan,
    onShowPlan: (String, Plan) -> Unit,
    onToggleStep: (String) -> Unit,
    onSkip: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    val routineId = card.routine.id
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(card.routine.name, style = MaterialTheme.typography.titleSmall)
        if (card.outcome == RoutineOutcome.Skipped) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pulado hoje", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onUndo(routineId) }) { Text("Desfazer") }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = plan == Plan.A, onClick = { onShowPlan(routineId, Plan.A) }, label = { Text("Ideal") })
            FilterChip(
                selected = plan == Plan.C,
                onClick = { onShowPlan(routineId, Plan.C) },
                label = { Text(if (card.suggestedPlan == Plan.C) "Mínimo sugerido" else "Mínimo") },
            )
        }
        card.steps(plan).forEach { state ->
            StepCheckRow(state.step.title, state.done, state.step.energy) { onToggleStep(state.step.id) }
        }
        when (card.outcome) {
            null -> TextButton(onClick = { onSkip(routineId) }) { Text("Pular hoje") }
            RoutineOutcome.A -> Text("Feito.", color = MaterialTheme.colorScheme.secondary)
            RoutineOutcome.C -> Text("Feito no mínimo. Conta igual.", color = MaterialTheme.colorScheme.secondary)
            RoutineOutcome.Skipped -> Unit
        }
    }
}

@Composable
private fun TaskCardView(
    card: TaskCard,
    day: LocalDate,
    onOpen: (String) -> Unit,
    onToggleStep: (String) -> Unit,
    onComplete: (String) -> Unit,
) {
    Surface(
        onClick = { onOpen(card.task.id) },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row {
            Surface(Modifier.width(4.dp).height(54.dp), color = TaskAccent) {}
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.task.title, style = MaterialTheme.typography.titleMedium)
                card.task.dueDate?.let { due ->
                    Text(
                        if (card.task.dueKind == DueKind.Hard) deadlineLabel(due, day) else softDateLabel(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val next = card.nextStep
                if (next != null) {
                    StepCheckRow("Próximo: ${next.title}", checked = false, energy = next.energy) { onToggleStep(next.id) }
                }
                TextButton(onClick = { onComplete(card.task.id) }) { Text("Concluir") }
            }
        }
    }
}
