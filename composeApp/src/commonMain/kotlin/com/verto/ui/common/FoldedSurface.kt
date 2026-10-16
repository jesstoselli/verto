package com.verto.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Fold size that stays clear of the neighbouring rounded corners (0 when the card is too small for one). */
internal fun foldCut(width: Float, height: Float, requested: Float, radius: Float): Float =
    requested.coerceIn(0f, (min(width, height) - radius).coerceAtLeast(0f))

/** The folded-over flap: a triangle inside the card, against the diagonal cut. */
internal fun foldFlap(width: Float, height: Float, size: Float): List<Offset> = listOf(
    Offset(width - size, height),
    Offset(width, height - size),
    Offset(width - size, height - size),
)

/**
 * Rounded on three corners and cut diagonally at the bottom end. A rounded corner hid any fold
 * smaller than its radius; with the cut, the fold is always as big as asked.
 */
private class FoldedShape(private val radius: Dp, private val fold: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        val f = foldCut(size.width, size.height, with(density) { fold.toPx() }, r)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
            lineTo(w, h - f)
            lineTo(w - f, h)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun FoldedSurface(
    modifier: Modifier = Modifier,
    foldColor: Color,
    foldSize: Dp = 24.dp,
    // Lists sit one step above the navigation colour, as the spec asks (#242437–#27273B in dark).
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    cornerRadius: Dp = 18.dp,
    shadowElevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(cornerRadius, foldSize) { FoldedShape(cornerRadius, foldSize) }
    val body: @Composable () -> Unit = { Box(content = content) }
    Box(modifier.defaultMinSize(minHeight = 48.dp)) {
        if (onClick == null) {
            Surface(color = color, shape = shape, shadowElevation = shadowElevation, content = body)
        } else {
            Surface(onClick = onClick, color = color, shape = shape, shadowElevation = shadowElevation, content = body)
        }
        // Drawn over the card, outside the semantics, and with no pointer input: taps still reach the card.
        Canvas(Modifier.matchParentSize()) {
            val radiusPx = cornerRadius.toPx().coerceAtMost(min(size.width, size.height) / 2f)
            val f = foldCut(size.width, size.height, foldSize.toPx(), radiusPx)
            if (f <= 0f) return@Canvas
            val points = foldFlap(size.width, size.height, f)
            drawPath(
                Path().apply {
                    moveTo(points[0].x, points[0].y)
                    lineTo(points[1].x, points[1].y)
                    lineTo(points[2].x, points[2].y)
                    close()
                },
                foldColor,
            )
        }
    }
}

/** Focus surface from the spec (#2D2C47–#25243A in dark), with a light counterpart. */
@Composable
fun focusSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.red < 0.5f) Color(0xFF29283F) else Color(0xFFEAE5F7)
