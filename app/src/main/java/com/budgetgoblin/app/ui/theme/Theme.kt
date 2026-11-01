package com.budgetgoblin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// surfaceVariant is the beige header panel; tertiary is the "remaining budget" green.
private val DarkColorScheme = darkColorScheme(
    primary = CoralDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2C22),
    onPrimaryContainer = Color(0xFFFFD9C9),
    secondary = Color(0xFFC9CCD8),
    secondaryContainer = Color(0xFF4A2C22),
    onSecondaryContainer = Color(0xFFFFD9C9),
    tertiary = Mint,
    onTertiary = Color.White,
    background = Night,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightHeader,
    onSurfaceVariant = Color(0xFFA8A6A3),
    surfaceContainerHigh = NightSurface,
    outlineVariant = Color(0xFF3A3D47),
    inverseSurface = NightText,
    inverseOnSurface = Night,
)

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6DB),
    onPrimaryContainer = Color(0xFF7A2E10),
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6DB), // selected segmented buttons and chips
    onSecondaryContainer = Color(0xFF7A2E10),
    tertiary = Mint,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Color.White,
    outlineVariant = Hairline,
    inverseSurface = SlateDeep,
    inverseOnSurface = Color.White,
)

@Composable
fun BudGetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
