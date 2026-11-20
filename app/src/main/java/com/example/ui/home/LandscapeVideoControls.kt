package com.example.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ReelboxGradients
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens
import kotlin.math.roundToInt

/**
 * Small rounded "Full screen" button shown centred underneath wide videos (>= 1.3 aspect ratio).
 */
@Composable
fun FullScreenPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val interactionSource = remember { MutableInteractionSource() }
    val label = stringResource(R.string.feed_full_screen)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("feed_full_screen_button")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 48.dp, color = colors.textPrimary),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fullscreen,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

/**
 * Small pill at the top centre shown while holding either outer edge for 2x playback speed.
 */
@Composable
fun SpeedHoldBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(ReelboxMotion.quickFade()),
        exit = fadeOut(ReelboxMotion.quickFade()),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.background.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.testTag("feed_speed_2x_badge"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.feed_speed_2x),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

/**
 * Full-screen landscape immersive controls overlay:
 * - Top-left round "Exit full screen" button
 * - Bottom soft dark gradient with a larger scrubbable progress bar + current/total time
 * - One horizontal row: Previous, Play/Pause, Next on left; Favourite, Share, Delete on right.
 */
@Composable
fun LandscapeVideoControls(
    visible: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    isFavourite: Boolean,
    showPlaybackError: Boolean,
    onExitFullScreen: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubStateChanged: (Boolean) -> Unit,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val view = LocalView.current

    val heartScale = remember { Animatable(1f) }
    var previousFavourite by remember { mutableStateOf(isFavourite) }

    LaunchedEffect(isFavourite) {
        if (isFavourite && !previousFavourite) {
            heartScale.snapTo(0.76f)
            heartScale.animateTo(
                targetValue = 1.24f,
                animationSpec = tween(durationMillis = 120, easing = ReelboxMotion.EaseOut),
            )
            heartScale.animateTo(
                targetValue = 1f,
                animationSpec = ReelboxMotion.heartBounce(),
            )
        } else if (!isFavourite) {
            heartScale.snapTo(1f)
        }
        previousFavourite = isFavourite
    }

    val heartTint by animateColorAsState(
        targetValue = if (isFavourite) colors.accent else colors.textPrimary,
        animationSpec = ReelboxMotion.quickFade(),
        label = "landscape_heart_tint",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(ReelboxMotion.quickFade()),
        exit = fadeOut(ReelboxMotion.standardFade()),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("landscape_video_controls"),
        ) {
            // Soft top dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.TopCenter)
                    .background(ReelboxGradients.TopOverlayScrim),
            )

            // Top-left round "Exit full screen" button
            LandscapeRoundButton(
                icon = Icons.Outlined.FullscreenExit,
                contentDescription = stringResource(R.string.feed_exit_full_screen),
                tint = colors.textPrimary,
                onClick = {
                    onUserInteraction()
                    onExitFullScreen()
                },
                testTag = "landscape_exit_full_screen",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .displayCutoutPadding()
                    .padding(start = spacing.lg, top = spacing.md),
            )

            // Bottom soft dark gradient + progress bar + single horizontal control row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(ReelboxGradients.BottomOverlayScrim)
                    .displayCutoutPadding()
                    .padding(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.xl,
                        bottom = spacing.md,
                    ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    if (!showPlaybackError) {
                        LandscapeScrubBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeekTo = { target ->
                                onUserInteraction()
                                onSeekTo(target)
                            },
                            onScrubStateChanged = { scrubbing ->
                                onUserInteraction()
                                onScrubStateChanged(scrubbing)
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LandscapeRoundButton(
                                icon = Icons.Outlined.SkipPrevious,
                                contentDescription = stringResource(R.string.feed_control_previous),
                                tint = colors.textPrimary,
                                enabled = hasPrevious,
                                onClick = {
                                    onUserInteraction()
                                    onPrevious()
                                },
                                testTag = "landscape_prev_button",
                            )
                            LandscapeRoundButton(
                                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.feed_control_pause else R.string.feed_control_play,
                                ),
                                tint = colors.textPrimary,
                                enabled = !showPlaybackError,
                                onClick = {
                                    onUserInteraction()
                                    onTogglePlayPause()
                                },
                                testTag = "landscape_play_pause_button",
                            )
                            LandscapeRoundButton(
                                icon = Icons.Outlined.SkipNext,
                                contentDescription = stringResource(R.string.feed_control_next),
                                tint = colors.textPrimary,
                                enabled = hasNext,
                                onClick = {
                                    onUserInteraction()
                                    onNext()
                                },
                                testTag = "landscape_next_button",
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LandscapeRoundButton(
                                icon = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (isFavourite) {
                                        R.string.feed_action_remove_favourite
                                    } else {
                                        R.string.feed_action_favourite
                                    },
                                ),
                                tint = heartTint,
                                iconScale = heartScale.value,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onUserInteraction()
                                    onToggleFavourite()
                                },
                                testTag = "landscape_favourite_button",
                            )
                            LandscapeRoundButton(
                                icon = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.feed_action_share),
                                tint = colors.textPrimary,
                                onClick = {
                                    onUserInteraction()
                                    onShare()
                                },
                                testTag = "landscape_share_button",
                            )
                            LandscapeRoundButton(
                                icon = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.feed_action_delete),
                                tint = colors.textPrimary,
                                onClick = {
                                    onUserInteraction()
                                    onDelete()
                                },
                                testTag = "landscape_delete_button",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeScrubBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onScrubStateChanged: (Boolean) -> Unit,
) {
    val colors = ReelboxThemeTokens.colors
    val density = LocalDensity.current

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableIntStateOf(1) }

    val safeDuration = durationMs.coerceAtLeast(0L)
    val playbackFraction = if (safeDuration > 0L) {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayedFraction = if (isScrubbing) scrubFraction else playbackFraction
    val displayedPositionMs = if (isScrubbing) {
        (scrubFraction * safeDuration).toLong()
    } else {
        positionMs.coerceIn(0L, safeDuration)
    }

    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 7.dp else 4.dp,
        animationSpec = ReelboxMotion.quickFade(),
        label = "landscape_scrub_height",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("landscape_scrub_bar"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = MediaFormatters.formatDuration(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
            )
            Text(
                text = MediaFormatters.formatDuration(safeDuration),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { barWidthPx = it.width.coerceAtLeast(1) }
                .pointerInput(safeDuration) {
                    if (safeDuration <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val initialFraction = (down.position.x / barWidthPx.toFloat()).coerceIn(0f, 1f)
                        scrubFraction = initialFraction
                        isScrubbing = true
                        onScrubStateChanged(true)
                        onSeekTo((initialFraction * safeDuration).toLong())

                        do {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer != null && pointer.pressed) {
                                if (pointer.positionChanged()) {
                                    pointer.consume()
                                }
                                val fraction = (pointer.position.x / barWidthPx.toFloat()).coerceIn(0f, 1f)
                                scrubFraction = fraction
                                onSeekTo((fraction * safeDuration).toLong())
                            }
                        } while (event.changes.any { it.pressed })

                        isScrubbing = false
                        onScrubStateChanged(false)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = 0.24f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayedFraction)
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }

            val thumbSizeDp = if (isScrubbing) 14.dp else 10.dp
            val thumbSizePx = with(density) { thumbSizeDp.roundToPx() }
            val thumbOffsetX = ((barWidthPx * displayedFraction) - thumbSizePx / 2f)
                .roundToInt()
                .coerceIn(0, (barWidthPx - thumbSizePx).coerceAtLeast(0))

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = thumbOffsetX, y = 0) }
                    .size(thumbSizeDp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun LandscapeRoundButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconScale: Float = 1f,
) {
    val colors = ReelboxThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.36f)
            .clip(CircleShape)
            .background(colors.background.copy(alpha = 0.44f))
            .border(0.75.dp, colors.textPrimary.copy(alpha = 0.14f), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colors.textPrimary.copy(alpha = 0.25f)),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale),
        )
    }
}
