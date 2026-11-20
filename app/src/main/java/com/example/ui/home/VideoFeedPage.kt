package com.example.ui.home

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.MediaItem
import com.example.ui.theme.ReelboxGradients
import com.example.ui.theme.ReelboxThemeTokens
import kotlinx.coroutines.delay

private const val WIDE_VIDEO_ASPECT_THRESHOLD = 1.3f
private const val LANDSCAPE_CONTROLS_AUTO_HIDE_MS = 3_000L

/**
 * Full-screen video feed item backed by a pooled Media3 ExoPlayer.
 * Supports portrait feed overlays, wide-video Full screen button, immersive landscape controls,
 * outer-quarter 2x speed press-and-hold, and centre press-and-hold share sheet.
 */
@Composable
fun VideoFeedPage(
    item: MediaItem,
    player: ExoPlayer?,
    aspectRatio: Float,
    hasPlaybackError: Boolean,
    isCurrentPage: Boolean,
    isPlaying: Boolean,
    isFavourite: Boolean,
    isFullScreenLandscape: Boolean,
    isShareSheetOpen: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDoubleTapFavourite: () -> Unit,
    onOpenShareSheet: () -> Unit,
    onDelete: () -> Unit,
    onEnterFullScreen: () -> Unit,
    onExitFullScreen: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedHoldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val view = LocalView.current

    var playIndicatorTrigger by remember(item.id) { mutableIntStateOf(0) }
    var doubleTapHeartTrigger by remember(item.id) { mutableIntStateOf(0) }
    var positionMs by remember(item.id) { mutableLongStateOf(0L) }
    var durationMs by remember(item.id) { mutableLongStateOf(item.durationMillis.coerceAtLeast(0L)) }
    var isScrubbing by remember(item.id) { mutableStateOf(false) }
    var is2xSpeedHeld by remember(item.id) { mutableStateOf(false) }

    // Landscape immersive controls auto-hide after 3 seconds of inactivity.
    var landscapeControlsVisible by remember(isFullScreenLandscape) {
        mutableStateOf(isFullScreenLandscape)
    }
    var controlsInteractionTick by remember(item.id, isFullScreenLandscape) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(
        isFullScreenLandscape,
        landscapeControlsVisible,
        isScrubbing,
        isShareSheetOpen,
        controlsInteractionTick,
    ) {
        if (isFullScreenLandscape && landscapeControlsVisible && !isScrubbing && !isShareSheetOpen) {
            delay(LANDSCAPE_CONTROLS_AUTO_HIDE_MS)
            landscapeControlsVisible = false
        }
    }

    // Ensure 2x speed is never stuck if the user navigates away or exits while holding.
    DisposableEffect(item.id, isCurrentPage) {
        onDispose {
            if (is2xSpeedHeld) {
                is2xSpeedHeld = false
                onSpeedHoldChanged(false)
            }
        }
    }

    // Poll playback position smoothly only while this video is active and not being scrubbed.
    LaunchedEffect(player, isCurrentPage, isScrubbing, hasPlaybackError) {
        if (!isCurrentPage || player == null || hasPlaybackError) {
            if (!isCurrentPage) positionMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                val playerDuration = player.duration
                if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                    durationMs = playerDuration
                }
            }
            delay(60L)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_feed_page_${item.id}"),
    ) {
        val actionRailTopPadding = (maxHeight * 0.50f).coerceAtLeast(180.dp)
        val isWideVideo = aspectRatio >= WIDE_VIDEO_ASPECT_THRESHOLD

        // Video viewport + unified tap, double-tap, edge 2x hold, and centre share hold gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(item.id, hasPlaybackError, isFullScreenLandscape) {
                    detectTapGestures(
                        onPress = {
                            try {
                                tryAwaitRelease()
                            } finally {
                                if (is2xSpeedHeld) {
                                    is2xSpeedHeld = false
                                    onSpeedHoldChanged(false)
                                }
                            }
                        },
                        onLongPress = { offset ->
                            val viewportWidth = size.width.toFloat()
                            if (viewportWidth <= 0f) return@detectTapGestures
                            val isOuterQuarter =
                                offset.x < viewportWidth * 0.25f || offset.x > viewportWidth * 0.75f
                            if (isOuterQuarter) {
                                if (!hasPlaybackError) {
                                    is2xSpeedHeld = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSpeedHoldChanged(true)
                                }
                            } else {
                                onOpenShareSheet()
                            }
                        },
                        onTap = {
                            if (isFullScreenLandscape) {
                                landscapeControlsVisible = !landscapeControlsVisible
                                if (landscapeControlsVisible) {
                                    controlsInteractionTick++
                                }
                            } else if (!hasPlaybackError) {
                                playIndicatorTrigger++
                                onTogglePlayPause()
                            }
                        },
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            doubleTapHeartTrigger++
                            onDoubleTapFavourite()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (hasPlaybackError) {
                VideoPlaybackErrorView()
            } else {
                AndroidView(
                    factory = { context ->
                        (LayoutInflater.from(context).inflate(
                            R.layout.reelbox_player_view,
                            null,
                            false,
                        ) as PlayerView).apply {
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        if (playerView.player !== player) {
                            playerView.player = player
                        }
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (isFullScreenLandscape) {
            LandscapeVideoControls(
                visible = landscapeControlsVisible && !is2xSpeedHeld,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                isFavourite = isFavourite,
                showPlaybackError = hasPlaybackError,
                onExitFullScreen = onExitFullScreen,
                onPrevious = onPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onToggleFavourite = onToggleFavourite,
                onShare = onOpenShareSheet,
                onDelete = onDelete,
                onSeekTo = { targetMs ->
                    positionMs = targetMs
                    player?.seekTo(targetMs)
                },
                onScrubStateChanged = { scrubbing ->
                    isScrubbing = scrubbing
                },
                onUserInteraction = {
                    controlsInteractionTick++
                },
            )
        } else {
            // Soft top dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .align(Alignment.TopCenter)
                    .background(ReelboxGradients.TopOverlayScrim),
            )

            // Soft bottom dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(212.dp)
                    .align(Alignment.BottomCenter)
                    .background(ReelboxGradients.BottomOverlayScrim),
            )

            // "Full screen" button centred underneath wide videos (>= 1.3 aspect ratio)
            if (isWideVideo && !hasPlaybackError && isCurrentPage) {
                val renderedVideoHeight = (maxWidth / aspectRatio).coerceAtMost(maxHeight)
                val videoBottomY = (maxHeight + renderedVideoHeight) / 2f
                val buttonTopY = (videoBottomY + 12.dp)
                    .coerceIn(120.dp, (maxHeight - 124.dp).coerceAtLeast(120.dp))

                FullScreenPillButton(
                    onClick = onEnterFullScreen,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = buttonTopY),
                )
            }

            // Right-side vertical action buttons starting from the middle going down
            FeedActionRail(
                isFavourite = isFavourite,
                onToggleFavourite = onToggleFavourite,
                onShare = onOpenShareSheet,
                onDelete = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = actionRailTopPadding, end = spacing.md),
            )

            // Bottom-left metadata (clear of right buttons and progress bar)
            FeedMetadataOverlay(
                item = item,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = spacing.md,
                        end = 84.dp,
                        bottom = 28.dp,
                    ),
            )

            // Thin accent progress / scrub bar at the very bottom of the video
            if (!hasPlaybackError) {
                VideoScrubBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeekTo = { targetMs ->
                        positionMs = targetMs
                        player?.seekTo(targetMs)
                    },
                    onScrubStateChanged = { scrubbing ->
                        isScrubbing = scrubbing
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // Faint fading play indicator on tap in portrait
            TapPlayIndicatorOverlay(
                triggerId = playIndicatorTrigger,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Top-centre "2x speed" pill (works in both portrait and landscape)
        SpeedHoldBadge(
            visible = is2xSpeedHeld,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (isFullScreenLandscape) {
                        Modifier
                            .displayCutoutPadding()
                            .padding(top = spacing.md)
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .displayCutoutPadding()
                            .padding(top = 60.dp)
                    },
                ),
        )

        // Popping accent heart on double-tap (works in both portrait and landscape)
        DoubleTapHeartOverlay(
            triggerId = doubleTapHeartTrigger,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun VideoPlaybackErrorView() {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    Column(
        modifier = Modifier
            .padding(horizontal = spacing.lg)
            .testTag("video_playback_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.feed_video_error),
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}
