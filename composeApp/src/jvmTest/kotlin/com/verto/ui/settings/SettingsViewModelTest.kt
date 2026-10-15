package com.verto.ui.settings

import com.verto.data.repository.TaskDraft
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class SettingsViewModelTest : ViewModelTest() {

    private fun viewModel() = SettingsViewModel(graph.settings, graph.reset)

    @Test
    fun dayStart_isSavedRightAway() {
        val vm = viewModel()

        vm.setDayStartHour(5)

        assertEquals(LocalTime(5, 0), graph.settings.current().dayStartsAt)
        assertEquals(5, vm.state.value.dayStartHour)
    }

    @Test
    fun absenceDays_stayBetweenTwoAndFourteen() {
        val vm = viewModel()

        repeat(5) { vm.changeAbsenceDays(-1) }
        assertEquals(2, vm.state.value.absenceDays)

        repeat(20) { vm.changeAbsenceDays(+1) }
        assertEquals(14, vm.state.value.absenceDays)
        assertEquals(14.days, graph.settings.current().absenceThreshold)
    }

    @Test
    fun quietHours_needTwoDifferentValidTimes() {
        val vm = viewModel()

        vm.setQuietStart("25")
        vm.saveQuietHours()
        assertEquals("Use horários como 22:00.", vm.state.value.message)

        vm.setQuietStart("2300")
        vm.setQuietEnd("2300")
        vm.saveQuietHours()
        assertEquals("O silêncio precisa começar e terminar em horários diferentes.", vm.state.value.message)

        vm.setQuietEnd("0700")
        vm.saveQuietHours()
        assertEquals("Horário de silêncio salvo.", vm.state.value.message)
        assertEquals(LocalTime(23, 0), graph.settings.current().quietStart)
        assertEquals(LocalTime(7, 0), graph.settings.current().quietEnd)
    }

    @Test
    fun freshStart_clearsToday_andSaysSo() = runTest {
        graph.tasks.save(TaskDraft(null, "Fazer IR", pinnedToToday = true))
        val vm = viewModel()

        vm.freshStart()

        vm.state.first { it.cleaned }
        graph.tasks.observeTasks().first { tasks -> tasks.none { it.pinnedToToday } }
    }
}
