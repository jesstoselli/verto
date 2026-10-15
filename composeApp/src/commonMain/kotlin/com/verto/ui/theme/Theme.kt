package com.verto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Betta palette (#FEC4C6, #E8A2F5, #9C79F4, #6473CA, #2B74A9, #6EB7CC). Dark is the home theme:
// the pastels carry accents on deep indigo surfaces. In light they move into containers and the
// deeper blues carry text, since pastel text on white fails contrast. The "error" slots hold a
// soft rose used only for gentle hints: the spec rules out alarm red anywhere in the app.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4E5BB0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF12195C),
    secondary = Color(0xFF2B74A9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3EDF4),
    onSecondaryContainer = Color(0xFF002D3A),
    tertiary = Color(0xFF7550C4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5DDFA),
    onTertiaryContainer = Color(0xFF2E0B3A),
    error = Color(0xFF8E4B5C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEC4C6),
    onErrorContainer = Color(0xFF3B0F18),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B24),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B24),
    surfaceVariant = Color(0xFFE3E1F2),
    onSurfaceVariant = Color(0xFF464659),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FD),
    surfaceContainer = Color(0xFFEFECF8),
    surfaceContainerHigh = Color(0xFFE9E6F3),
    surfaceContainerHighest = Color(0xFFE3E1ED),
    outline = Color(0xFF76768C),
    outlineVariant = Color(0xFFC7C5DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9C79F4),
    onPrimary = Color(0xFF160B3A),
    primaryContainer = Color(0xFF3A2D6B),
    onPrimaryContainer = Color(0xFFE4DAFF),
    secondary = Color(0xFF6EB7CC),
    onSecondary = Color(0xFF00303B),
    secondaryContainer = Color(0xFF1D4A5E),
    onSecondaryContainer = Color(0xFFC6EDF7),
    tertiary = Color(0xFFE8A2F5),
    onTertiary = Color(0xFF3D0F47),
    tertiaryContainer = Color(0xFF553063),
    onTertiaryContainer = Color(0xFFF8D8FD),
    error = Color(0xFFFEC4C6),
    onError = Color(0xFF4A1A1E),
    errorContainer = Color(0xFF5A2E38),
    onErrorContainer = Color(0xFFFFE2E3),
    background = Color(0xFF121220),
    onBackground = Color(0xFFE6E4F2),
    surface = Color(0xFF121220),
    onSurface = Color(0xFFE6E4F2),
    surfaceVariant = Color(0xFF2C2F4A),
    onSurfaceVariant = Color(0xFFC5C8E6),
    surfaceContainerLowest = Color(0xFF0D0D18),
    surfaceContainerLow = Color(0xFF181829),
    surfaceContainer = Color(0xFF1C1C2F),
    surfaceContainerHigh = Color(0xFF26273A),
    surfaceContainerHighest = Color(0xFF313246),
    outline = Color(0xFF8A8FB5),
    outlineVariant = Color(0xFF3A3E5C),
)

/** The betta pink, same in both themes: it marks the day's energy, the one control that sets the tone. */
val EnergyPink = Color(0xFFFEC4C6)
val OnEnergyPink = Color(0xFF4A1A1E)

@Composable
fun VertoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
    )
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = typography, content = content)
}
