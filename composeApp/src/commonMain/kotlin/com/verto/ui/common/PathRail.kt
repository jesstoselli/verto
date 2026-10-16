package com.verto.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class PathNodeState { Past, Current, Future }

@Composable
fun PathRail(
    nodeState: PathNodeState,
    drawBefore: Boolean,
    drawAfter: Boolean,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val nodeColor = when (nodeState) {
        PathNodeState.Past -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        PathNodeState.Current -> MaterialTheme.colorScheme.primary
        PathNodeState.Future -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Canvas(modifier.width(28.dp).defaultMinSize(minHeight = 48.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = 2.dp.toPx()
        if (drawBefore) drawLine(lineColor, Offset(center.x, 0f), center, stroke)
        if (drawAfter) drawLine(lineColor, center, Offset(center.x, size.height), stroke)
        drawCircle(
            color = nodeColor,
            radius = if (nodeState == PathNodeState.Current) 7.dp.toPx() else 5.dp.toPx(),
            center = center,
        )
        if (nodeState == PathNodeState.Current) {
            drawCircle(Color.White.copy(alpha = 0.72f), 2.dp.toPx(), center)
        }
    }
}
