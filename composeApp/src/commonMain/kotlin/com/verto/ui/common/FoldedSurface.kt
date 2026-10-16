package com.verto.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal fun foldTriangle(width: Float, height: Float, requestedSize: Float): List<Offset> {
    val size = min(requestedSize.coerceAtLeast(0f), min(width.coerceAtLeast(0f), height.coerceAtLeast(0f)))
    return listOf(
        Offset(width - size, height),
        Offset(width, height - size),
        Offset(width, height),
    )
}

@Composable
fun FoldedSurface(
    modifier: Modifier = Modifier,
    foldColor: Color,
    foldSize: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val surfaceModifier = modifier.defaultMinSize(minHeight = 48.dp)
    val body: @Composable () -> Unit = {
        Box {
            Canvas(Modifier.matchParentSize()) {
                val points = foldTriangle(size.width, size.height, foldSize.toPx())
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    lineTo(points[1].x, points[1].y)
                    lineTo(points[2].x, points[2].y)
                    close()
                }
                drawPath(path, foldColor)
            }
            content()
        }
    }

    if (onClick == null) {
        Surface(
            modifier = surfaceModifier,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    }
}
