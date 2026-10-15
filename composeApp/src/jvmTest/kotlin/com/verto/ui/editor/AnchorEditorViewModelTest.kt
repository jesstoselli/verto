package com.verto.ui.editor

import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.StepDraft
import com.verto.domain.model.StepEnergy
import com.verto.ui.ViewModelTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

class AnchorEditorViewModelTest : ViewModelTest() {

    @Test
    fun save_acceptsShortTimes() = runTest {
        val vm = AnchorEditorViewModel(null, graph.routines)
        vm.setName("Almocei")
        vm.setStart("12")
        vm.setEnd("14:30")

        vm.save()

        vm.state.first { it.closed }
        val anchor = graph.routines.observeAnchors().first().single()
        assertEquals(LocalTime(12, 0), anchor.windowStart)
        assertEquals(LocalTime(14, 30), anchor.windowEnd)
    }

    @Test
    fun save_windowEndingBeforeItStarts_explainsInsteadOfSaving() = runTest {
        val vm = AnchorEditorViewModel(null, graph.routines)
        vm.setName("Jantei")
        vm.setStart("20:00")
        vm.setEnd("19:00")

        vm.save()

        assertNotNull(vm.state.value.message)
        assertTrue(graph.routines.observeAnchors().first().isEmpty())
    }

    @Test
    fun delete_anchorInUse_keepsItAndExplains() = runTest {
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(6, 0), LocalTime(9, 0)))
        graph.routines.saveRoutine(
            RoutineDraft(null, "Exercício", anchorId, DayOfWeek.entries.toSet(), listOf(StepDraft(null, "treino", StepEnergy.Medium)), listOf(StepDraft(null, "alongar", StepEnergy.Low))),
        )
        val vm = AnchorEditorViewModel(anchorId, graph.routines)
        vm.state.first { it.loaded }

        vm.delete()

        vm.state.first { it.message != null }
        assertEquals(1, graph.routines.observeAnchors().first().size)
    }

    @Test
    fun save_tappedTwice_createsOneAnchor() = runTest {
        val vm = AnchorEditorViewModel(null, graph.routines)
        vm.setName("Almocei")
        vm.setStart("12")
        vm.setEnd("14")

        vm.save()
        vm.save()

        vm.state.first { it.closed }
        // Writes are serialized, so once this one lands any second save has landed too.
        graph.routines.saveAnchor(AnchorDraft(null, "sentinela", LocalTime(20, 0), LocalTime(21, 0)))
        assertEquals(1, graph.routines.observeAnchors().first().count { it.name == "Almocei" })
    }

    @Test
    fun timeFields_holdOnlyDigitsForTheMask() = runTest {
        val vm = AnchorEditorViewModel(null, graph.routines)

        vm.setStart("12:3x0")

        assertEquals("1230", vm.state.value.start)
    }

    @Test
    fun existingAnchor_loadsItsTimesAsDigits() = runTest {
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(6, 0), LocalTime(9, 30)))

        val state = AnchorEditorViewModel(anchorId, graph.routines).state.first { it.loaded }

        assertEquals("0600", state.start)
        assertEquals("0930", state.end)
    }
}
