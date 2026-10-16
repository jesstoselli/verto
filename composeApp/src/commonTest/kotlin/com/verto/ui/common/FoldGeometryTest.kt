package com.verto.ui.common

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldGeometryTest {
    @Test
    fun foldTriangle_clampsToSurfaceBounds() {
        val points = foldTriangle(width = 20f, height = 16f, requestedSize = 40f)

        assertEquals(3, points.size)
        assertTrue(points.all { it.x in 0f..20f && it.y in 0f..16f })
        assertEquals(Offset(20f, 16f), points.last())
    }
}
