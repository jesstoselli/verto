package com.verto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Calm blue-grey palette. The "error" slots carry a muted amber used only for gentle hints:
// the spec rules out alarm red anywhere in the app.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6472),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE9F9),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF4C6269),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6EF),
    onSecondaryContainer = Color(0xFF071E24),
    tertiary = Color(0xFF5A5C7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E0FF),
    onTertiaryContainer = Color(0xFF171A37),
    error = Color(0xFF7A5A00),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDF9E),
    onErrorContainer = Color(0xFF261A00),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171C1E),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171C1E),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF40484B),
    outline = Color(0xFF70787C),
    outlineVariant = Color(0xFFBFC8CB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3CDDD),
    onPrimary = Color(0xFF033542),
    primaryContainer = Color(0xFF214C5A),
    onPrimaryContainer = Color(0xFFBFE9F9),
    secondary = Color(0xFFB3CAD3),
    onSecondary = Color(0xFF1E333A),
    secondaryContainer = Color(0xFF354A51),
    onSecondaryContainer = Color(0xFFCFE6EF),
    tertiary = Color(0xFFC2C3EB),
    onTertiary = Color(0xFF2C2F4D),
    tertiaryContainer = Color(0xFF424465),
    onTertiaryContainer = Color(0xFFE0E0FF),
    error = Color(0xFFF1C048),
    onError = Color(0xFF402D00),
    errorContainer = Color(0xFF5C4300),
    onErrorContainer = Color(0xFFFFDF9E),
    background = Color(0xFF0F1416),
    onBackground = Color(0xFFDEE3E6),
    surface = Color(0xFF0F1416),
    onSurface = Color(0xFFDEE3E6),
    surfaceVariant = Color(0xFF40484B),
    onSurfaceVariant = Color(0xFFBFC8CB),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF40484B),
)

@Composable
fun VertoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
    )
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = typography, content = content)
}
