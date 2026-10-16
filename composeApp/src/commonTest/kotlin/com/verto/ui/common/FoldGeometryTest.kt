package com.verto.ui.common

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldGeometryTest {

    @Test
    fun foldCut_keepsTheRequestedSize_whenItFits() {
        // A 12dp fold next to 18dp corners used to vanish under the rounding; the cut keeps all of it.
        assertEquals(12f, foldCut(width = 300f, height = 60f, requested = 12f, radius = 18f))
    }

    @Test
    fun foldCut_neverEatsTheNeighbouringRoundedCorners() {
        assertEquals(42f, foldCut(width = 300f, height = 60f, requested = 80f, radius = 18f))
        assertEquals(0f, foldCut(width = 10f, height = 10f, requested = 8f, radius = 18f))
    }

    @Test
    fun foldFlap_sitsInsideTheCardCorner() {
        val points = foldFlap(width = 300f, height = 60f, size = 12f)

        assertEquals(listOf(Offset(288f, 60f), Offset(300f, 48f), Offset(288f, 48f)), points)
        assertTrue(points.all { it.x in 0f..300f && it.y in 0f..60f })
    }
}
