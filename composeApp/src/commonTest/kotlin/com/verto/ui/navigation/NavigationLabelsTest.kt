package com.verto.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationLabelsTest {
    @Test
    fun primaryTabs_useApprovedLabelsInOrder() {
        assertEquals(listOf("Agora", "Rascunho", "Trajeto"), PrimaryTab.entries.map { it.label })
    }
}
