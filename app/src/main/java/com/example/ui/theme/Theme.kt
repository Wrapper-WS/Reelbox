package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val ReelboxDarkColorScheme = darkColorScheme(
    primary = ReelboxAccent,
    onPrimary = ReelboxTextPrimary,
    primaryContainer = ReelboxSurfaceRaised,
    onPrimaryContainer = ReelboxTextPrimary,
    secondary = ReelboxTextPrimary,
    onSecondary = ReelboxOnLightButton,
    secondaryContainer = ReelboxSurfaceRaised,
    onSecondaryContainer = ReelboxTextPrimary,
    tertiary = ReelboxTextSecondary,
    onTertiary = ReelboxBackground,
    background = ReelboxBackground,
    onBackground = ReelboxTextPrimary,
    surface = ReelboxSurface,
    onSurface = ReelboxTextPrimary,
    surfaceVariant = ReelboxSurfaceRaised,
    onSurfaceVariant = ReelboxTextSecondary,
    surfaceContainerLowest = ReelboxBackground,
    surfaceContainerLow = ReelboxSurface,
    surfaceContainer = ReelboxSurface,
    surfaceContainerHigh = ReelboxSurfaceRaised,
    surfaceContainerHighest = ReelboxSurfaceRaised,
    outline = ReelboxDivider,
    outlineVariant = ReelboxDivider,
    scrim = ReelboxScrimDark,
)

private val LocalReelboxColors = staticCompositionLocalOf { ReelboxExtendedColors() }
private val LocalReelboxSpacing = staticCompositionLocalOf { ReelboxSpacing() }

object ReelboxThemeTokens {
    val colors: ReelboxExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalReelboxColors.current

    val spacing: ReelboxSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalReelboxSpacing.current
}

@Composable
fun ReelboxTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalReelboxColors provides ReelboxExtendedColors(),
        LocalReelboxSpacing provides ReelboxSpacing(),
    ) {
        MaterialTheme(
            colorScheme = ReelboxDarkColorScheme,
            typography = ReelboxTypography,
            shapes = ReelboxShapes,
            content = content,
        )
    }
}
