package com.verto.ui.today

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.DueKind
import com.verto.domain.model.Plan
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.rules.AnchorSection
import com.verto.domain.rules.RoutineCard
import com.verto.domain.rules.TaskCard
import com.verto.ui.common.Hint
import com.verto.ui.common.SectionHeader
import com.verto.ui.common.StepCheckRow
import com.verto.ui.common.deadlineLabel
import com.verto.ui.common.label
import com.verto.ui.common.longPt
import com.verto.ui.common.softDateLabel
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayScreen(viewModel: TodayViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val content = state as? TodayUiState.Content ?: return
    val today = content.today

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(today.day.longPt().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall)
                AssistChip(onClick = viewModel::cycleEnergy, label = { Text("Energia: ${today.energy.label()}") })
            }
        }
        if (today.sections.isEmpty() && today.pinned.isEmpty() && today.deadlines.isEmpty()) {
            item { Hint("Nada marcado pra hoje. Tá tudo bem.") }
        }
        items(today.sections, key = { it.anchor.id }) { section ->
            AnchorSectionCard(
                section = section,
                expanded = section.anchor.id in content.expandedIds,
                shownPlan = { card -> content.shownPlans[card.routine.id] ?: card.suggestedPlan },
                onToggle = { viewModel.toggleSection(section.anchor.id) },
                onTap = { viewModel.tapAnchor(section.anchor.id) },
                onShowPlan = viewModel::showPlan,
                onToggleStep = viewModel::toggleRoutineStep,
                onSkip = viewModel::skip,
                onUndo = viewModel::undo,
            )
        }
        if (today.pinned.isNotEmpty()) {
            item { SectionHeader("Pra hoje") }
            if (today.pinnedOverSoftLimit) {
                item { Hint("Mais de três coisas pra hoje. Tudo bem, só vai com calma.") }
            }
            items(today.pinned, key = { "pinned-${it.task.id}" }) { card ->
                TaskCardView(card, today.day, viewModel::toggleTaskStep, viewModel::completeTask)
            }
        }
        if (today.deadlines.isNotEmpty()) {
            item { SectionHeader("Prazos chegando") }
            items(today.deadlines, key = { "deadline-${it.task.id}" }) { card ->
                TaskCardView(card, today.day, viewModel::toggleTaskStep, viewModel::completeTask)
            }
        }
    }
}

@Composable
private fun AnchorSectionCard(
    section: AnchorSection,
    expanded: Boolean,
    shownPlan: (RoutineCard) -> Plan,
    onToggle: () -> Unit,
    onTap: () -> Unit,
    onShowPlan: (String, Plan) -> Unit,
    onToggleStep: (String) -> Unit,
    onSkip: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                label = { Text(if (card.suggestedPlan == Plan.C) "Mínimo · sugerido" else "Mínimo") },
            )
        }
        card.steps(plan).forEach { state ->
            StepCheckRow(state.step.title, state.done, state.step.energy) { onToggleStep(state.step.id) }
        }
        when (card.outcome) {
            null -> TextButton(onClick = { onSkip(routineId) }) { Text("Pular hoje") }
            RoutineOutcome.A -> Text("Feito.", color = MaterialTheme.colorScheme.primary)
            RoutineOutcome.C -> Text("Feito no mínimo. Conta igual.", color = MaterialTheme.colorScheme.primary)
            RoutineOutcome.Skipped -> Unit
        }
    }
}

@Composable
private fun TaskCardView(card: TaskCard, day: LocalDate, onToggleStep: (String) -> Unit, onComplete: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            } else if (card.totalSteps == 0) {
                TextButton(onClick = { onComplete(card.task.id) }) { Text("Concluir") }
            }
        }
    }
}
