package com.verto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.verto.ui.generated.resources.Res
import com.verto.ui.generated.resources.average_sans_regular
import com.verto.ui.generated.resources.encode_sans_condensed_medium
import com.verto.ui.generated.resources.encode_sans_condensed_regular
import com.verto.ui.generated.resources.encode_sans_condensed_semibold
import org.jetbrains.compose.resources.Font

internal fun buildVertoTypography(
    voice: FontFamily,
    interfaceFont: FontFamily,
): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = voice, fontWeight = FontWeight.Normal),
        displayMedium = displayMedium.copy(fontFamily = voice, fontWeight = FontWeight.Normal),
        displaySmall = displaySmall.copy(
            fontFamily = voice,
            fontWeight = FontWeight.Normal,
            fontSize = 38.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.6).sp,
        ),
        headlineLarge = headlineLarge.copy(fontFamily = voice, fontWeight = FontWeight.Normal),
        headlineMedium = headlineMedium.copy(
            fontFamily = voice,
            fontWeight = FontWeight.Normal,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.35).sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontFamily = voice,
            fontWeight = FontWeight.Normal,
            fontSize = 24.sp,
            lineHeight = 30.sp,
        ),
        titleLarge = titleLarge.copy(fontFamily = voice, fontWeight = FontWeight.Normal, fontSize = 22.sp),
        titleMedium = titleMedium.copy(fontFamily = voice, fontWeight = FontWeight.Normal, fontSize = 18.sp),
        titleSmall = titleSmall.copy(fontFamily = voice, fontWeight = FontWeight.Normal, fontSize = 15.sp),
        bodyLarge = bodyLarge.copy(
            fontFamily = voice,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 25.sp,
        ),
        bodyMedium = bodyMedium.copy(
            fontFamily = voice,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        ),
        bodySmall = bodySmall.copy(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        ),
        labelLarge = labelLarge.copy(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        ),
        labelMedium = labelMedium.copy(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        ),
        labelSmall = labelSmall.copy(fontFamily = interfaceFont, fontWeight = FontWeight.Normal),
    )
}

@Composable
internal fun rememberVertoTypography(): Typography {
    val voice = FontFamily(Font(Res.font.average_sans_regular, FontWeight.Normal))
    val interfaceFont = FontFamily(
        Font(Res.font.encode_sans_condensed_regular, FontWeight.Normal),
        Font(Res.font.encode_sans_condensed_medium, FontWeight.Medium),
        Font(Res.font.encode_sans_condensed_semibold, FontWeight.SemiBold),
    )
    return buildVertoTypography(voice, interfaceFont)
}
