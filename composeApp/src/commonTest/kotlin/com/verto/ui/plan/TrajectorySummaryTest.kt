package com.verto.ui.plan

import kotlin.test.Test
import kotlin.test.assertEquals

class TrajectorySummaryTest {

    @Test
    fun summary_pluralizesEachKind() {
        assertEquals("No caminho: 4 tarefas, 2 rotinas e 3 âncoras", trajectorySummary(4, 2, 3))
        assertEquals("No caminho: 1 tarefa, 1 rotina e 1 âncora", trajectorySummary(1, 1, 1))
    }

    @Test
    fun emptyKinds_readAsNone() {
        assertEquals("No caminho: nenhuma tarefa, nenhuma rotina e nenhuma âncora", trajectorySummary(0, 0, 0))
    }
}
