package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reelbox shape system:
 * - 12dp on grid tiles (small)
 * - 16dp on cards and sheets (medium / large)
 * - Fully round for icon buttons and primary pills (extraLarge)
 */
val ReelboxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = CircleShape,
)

/**
 * Strict 4dp spacing grid (4, 8, 12, 16, 24).
 */
@Immutable
data class ReelboxSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

/**
 * Motion tokens: 200 to 300ms ease-out for calm cinematic transitions,
 * plus a playful subtle bounce reserved for the favourite heart.
 */
object ReelboxMotion {
    const val DurationQuickMs: Int = 200
    const val DurationStandardMs: Int = 240
    const val DurationSheetMs: Int = 280

    val EaseOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> quickFade(): TweenSpec<T> = tween(
        durationMillis = DurationQuickMs,
        easing = EaseOut,
    )

    fun <T> standardFade(): TweenSpec<T> = tween(
        durationMillis = DurationStandardMs,
        easing = EaseOut,
    )

    fun <T> sheetSlide(): TweenSpec<T> = tween(
        durationMillis = DurationSheetMs,
        easing = EaseOut,
    )

    fun heartBounce(): SpringSpec<Float> = spring(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessMedium,
    )
}
