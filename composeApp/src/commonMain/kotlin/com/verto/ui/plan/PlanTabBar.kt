package com.verto.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Trajeto's inner lenses on a quiet stretch of path. The active one is told apart by weight of text,
 * a bigger dot and a small fold, never by color alone; switching is immediate, nothing animates.
 */
@Composable
internal fun PlanTabBar(selected: PlanTab, onSelect: (PlanTab) -> Unit, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val active = MaterialTheme.colorScheme.primary
    val fold = MaterialTheme.colorScheme.tertiary
    Box(modifier.fillMaxWidth()) {
        // The path runs behind the dots; purely decorative, so it carries no semantics.
        Canvas(Modifier.matchParentSize()) {
            val y = size.height - 10.dp.toPx()
            drawLine(line, Offset(size.width / 6f, y), Offset(size.width * 5f / 6f, y), 2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            PlanTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        tab.label,
                        modifier = Modifier.padding(top = 8.dp),
                        style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                        val center = Offset(size.width / 2f, size.height - 10.dp.toPx())
                        if (isSelected) {
                            val r = 6.dp.toPx()
                            drawCircle(active, r, center)
                            val path = Path().apply {
                                moveTo(center.x + r * 0.2f, center.y - r * 1.9f)
                                lineTo(center.x + r * 1.6f, center.y - r * 1.9f)
                                lineTo(center.x + r * 1.6f, center.y - r * 0.5f)
                                close()
                            }
                            drawPath(path, fold)
                        } else {
                            drawCircle(idle, 4.dp.toPx(), center)
                        }
                    }
                }
            }
        }
    }
}
