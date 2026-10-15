package com.verto.ui.editor

import com.verto.domain.model.StepEnergy
import kotlin.test.Test
import kotlin.test.assertEquals

class StepListOpsTest {

    private fun step(key: String, title: String = key) = EditableStep(key, null, title, StepEnergy.Low)

    @Test
    fun move_swapsWithTheNeighbour_andIgnoresTheEdges() {
        val steps = listOf(step("a"), step("b"), step("c"))

        assertEquals(listOf("a", "c", "b"), StepListOps.move(steps, "c", -1).map { it.key })
        assertEquals(steps, StepListOps.move(steps, "a", -1))
        assertEquals(steps, StepListOps.move(steps, "c", 1))
    }

    @Test
    fun toDrafts_dropsBlankSteps_andTrims() {
        val drafts = StepListOps.toDrafts(listOf(step("a", " abrir site "), step("b", "  ")))

        assertEquals(listOf("abrir site"), drafts.map { it.title })
    }
}
