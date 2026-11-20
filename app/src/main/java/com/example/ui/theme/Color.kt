package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core Reelbox palette — cinematic, calm, obsidian dark
val ReelboxBackground = Color(0xFF0B0B0D)
val ReelboxSurface = Color(0xFF151518)
val ReelboxSurfaceRaised = Color(0xFF1E1E22)
val ReelboxTextPrimary = Color(0xFFF2F2F4)
val ReelboxTextSecondary = Color(0xFF9A9AA3)
val ReelboxDivider = Color(0xFF2A2A30)

// Single accent — reserved strictly for favourite heart, active tab, and progress bar
val ReelboxAccent = Color(0xFFFF5A36)

// Soft destructive red — reserved strictly for the Delete confirmation button
val ReelboxDestructive = Color(0xFFE5484D)

// Neutral overlay & scrim tones
val ReelboxOnLightButton = Color(0xFF0B0B0D)
val ReelboxScrimDark = Color(0xD10B0B0D)
val ReelboxScrimMedium = Color(0x8A0B0B0D)

@Immutable
data class ReelboxExtendedColors(
    val background: Color = ReelboxBackground,
    val surface: Color = ReelboxSurface,
    val surfaceRaised: Color = ReelboxSurfaceRaised,
    val textPrimary: Color = ReelboxTextPrimary,
    val textSecondary: Color = ReelboxTextSecondary,
    val divider: Color = ReelboxDivider,
    val accent: Color = ReelboxAccent,
    val destructive: Color = ReelboxDestructive,
)

/**
 * Reusable soft dark scrim gradients for video overlays so text remains legible
 * over any footage without harsh solid boxes.
 */
object ReelboxGradients {
    val TopOverlayScrim: Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to ReelboxScrimMedium,
            0.55f to Color(0x330B0B0D),
            1.0f to Color.Transparent,
        ),
    )

    val BottomOverlayScrim: Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.45f to Color(0x4D0B0B0D),
            1.0f to ReelboxScrimDark,
        ),
    )
}
