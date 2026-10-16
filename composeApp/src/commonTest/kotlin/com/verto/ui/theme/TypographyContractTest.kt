package com.verto.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class TypographyContractTest {
    @Test
    fun voiceAndInterfaceRoles_useApprovedFamiliesAndWeights() {
        val typography = buildVertoTypography(FontFamily.Serif, FontFamily.Monospace)

        assertEquals(FontFamily.Serif, typography.headlineMedium.fontFamily)
        assertEquals(FontWeight.Normal, typography.headlineMedium.fontWeight)
        assertEquals(FontFamily.Serif, typography.bodyLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typography.bodySmall.fontFamily)
        assertEquals(FontWeight.SemiBold, typography.labelLarge.fontWeight)
        assertEquals(FontWeight.Medium, typography.labelMedium.fontWeight)
    }
}
