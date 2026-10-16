package com.verto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val BettaBlush = Color(0xFFFEC4C6)
val BettaOrchid = Color(0xFFE8A2F5)
val BettaViolet = Color(0xFF9C79F4)
val BettaPeriwinkle = Color(0xFF6473CA)
val BettaBlue = Color(0xFF2B74A9)
val BettaLagoon = Color(0xFF6EB7CC)
val BettaInk = Color(0xFF1A1231)
val BettaMist = Color(0xFFF6F2F9)

val EnergyPink = BettaBlush
val OnEnergyPink = Color(0xFF4A1A1E)
val TaskAccent = BettaLagoon
val RoutineAccent = BettaViolet
val AnchorAccent = BettaOrchid

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B46B1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E2FF),
    onPrimaryContainer = BettaInk,
    secondary = Color(0xFF17647E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EFF7),
    onSecondaryContainer = Color(0xFF082E3B),
    tertiary = Color(0xFF86508F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7DCF8),
    onTertiaryContainer = Color(0xFF35113A),
    error = Color(0xFF8E4B5C),
    onError = Color.White,
    errorContainer = BettaBlush,
    onErrorContainer = Color(0xFF3B0F18),
    background = Color(0xFFF8F6FC),
    onBackground = BettaInk,
    surface = Color(0xFFF8F6FC),
    onSurface = BettaInk,
    surfaceVariant = Color(0xFFE7E3F0),
    onSurfaceVariant = Color(0xFF575167),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1EEF7),
    surfaceContainer = Color(0xFFECE8F3),
    surfaceContainerHigh = Color(0xFFE4DFED),
    surfaceContainerHighest = Color(0xFFDCD6E7),
    outline = Color(0xFF7A748A),
    outlineVariant = Color(0xFFCCC5D7),
)

private val DarkColors = darkColorScheme(
    primary = BettaViolet,
    onPrimary = Color(0xFF1E1048),
    primaryContainer = Color(0xFF3B2B70),
    onPrimaryContainer = Color(0xFFE7DFFF),
    secondary = BettaLagoon,
    onSecondary = Color(0xFF052F3A),
    secondaryContainer = Color(0xFF174656),
    onSecondaryContainer = Color(0xFFCDEEF5),
    tertiary = BettaOrchid,
    onTertiary = Color(0xFF421448),
    tertiaryContainer = Color(0xFF57305F),
    onTertiaryContainer = Color(0xFFF9DCF9),
    error = BettaBlush,
    onError = Color(0xFF4A1A1E),
    errorContainer = Color(0xFF5A2E38),
    onErrorContainer = Color(0xFFFFE2E3),
    background = Color(0xFF11111F),
    onBackground = BettaMist,
    surface = Color(0xFF11111F),
    onSurface = BettaMist,
    surfaceVariant = Color(0xFF303047),
    onSurfaceVariant = Color(0xFFC9C5D8),
    surfaceContainerLowest = Color(0xFF0C0C17),
    surfaceContainerLow = Color(0xFF191927),
    surfaceContainer = Color(0xFF1E1E30),
    surfaceContainerHigh = Color(0xFF27273B),
    surfaceContainerHighest = Color(0xFF313149),
    outline = Color(0xFF918BA4),
    outlineVariant = Color(0xFF3F3D54),
)

private val VertoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VertoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = rememberVertoTypography(),
        shapes = VertoShapes,
        content = content,
    )
}
