package com.budgetgoblin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The Appearance setting. */
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

// surfaceVariant is a soft raised fill; outlineVariant is the card border; tertiary is "good" green.
private val DarkColorScheme = darkColorScheme(
    primary = CoralDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D2A24),
    onPrimaryContainer = Color(0xFFFFD9C9),
    secondary = Color(0xFFC9D3D8),
    secondaryContainer = Color(0xFF3D2A24),
    onSecondaryContainer = Color(0xFFFFD9C9),
    tertiary = Mint,
    onTertiary = Color.White,
    background = Night,
    onBackground = NightText,
    surface = Night,
    onSurface = NightText,
    surfaceVariant = NightRaised,
    onSurfaceVariant = NightMuted,
    surfaceContainerHigh = Night,
    surfaceContainer = NightRaised,
    outlineVariant = NightBorder,
    inverseSurface = NightRaised,
    inverseOnSurface = NightText,
    error = Color(0xFFFF6B6B),
)

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6DB),
    onPrimaryContainer = Color(0xFF7A2E10),
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6DB),
    onSecondaryContainer = Color(0xFF7A2E10),
    tertiary = Mint,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Paper,
    surfaceContainer = Sand,
    outlineVariant = Hairline,
    inverseSurface = SlateDeep,
    inverseOnSurface = Color.White,
)

@Composable
fun BudGetTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = scheme, typography = Typography) {
        // Text that doesn't pick its own colour follows the theme (light on dark in dark mode).
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
