package com.example.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MediaItem
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HomeFeedMode {
    VIDEOS,
    IMAGES,
}

/**
 * Floating top bar over soft dark gradient:
 * centred "Videos" and "Images" selector + small round mute toggle in the top right.
 */
@Composable
fun HomeFeedTopBar(
    selectedMode: HomeFeedMode,
    onSelectMode: (HomeFeedMode) -> Unit,
    showMuteButton: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedModeTab(
                label = stringResource(R.string.feed_tab_videos),
                selected = selectedMode == HomeFeedMode.VIDEOS,
                onClick = { onSelectMode(HomeFeedMode.VIDEOS) },
                testTag = "feed_mode_videos",
            )
            FeedModeTab(
                label = stringResource(R.string.feed_tab_images),
                selected = selectedMode == HomeFeedMode.IMAGES,
                onClick = { onSelectMode(HomeFeedMode.IMAGES) },
                testTag = "feed_mode_images",
            )
        }

        AnimatedVisibility(
            visible = showMuteButton,
            enter = fadeIn(ReelboxMotion.quickFade()),
            exit = fadeOut(ReelboxMotion.quickFade()),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val muteDescription = stringResource(
                if (isMuted) R.string.feed_action_unmute else R.string.feed_action_mute,
            )
            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("feed_mute_button")
                    .semantics { contentDescription = muteDescription }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false, radius = 22.dp, color = colors.textPrimary),
                        role = Role.Button,
                        onClick = onToggleMute,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.background.copy(alpha = 0.46f))
                        .border(0.75.dp, colors.textPrimary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isMuted) {
                            Icons.AutoMirrored.Outlined.VolumeOff
                        } else {
                            Icons.AutoMirrored.Outlined.VolumeUp
                        },
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = ReelboxThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }

    val textColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = ReelboxMotion.quickFade(),
        label = "feed_mode_text_color",
    )

    val underlineScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = ReelboxMotion.standardFade(),
        label = "feed_mode_underline_scale",
    )

    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colors.textPrimary.copy(alpha = 0.16f)),
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(testTag)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = textColor,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .scale(scaleX = underlineScale, scaleY = 1f)
                .alpha(underlineScale)
                .clip(CircleShape)
                .background(colors.textPrimary),
        )
    }
}

/**
 * Right-side vertical column of 48dp round action buttons:
 * Favourite (heart with bounce), Share, and Delete.
 */
@Composable
fun FeedActionRail(
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
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
        label = "favourite_heart_tint",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeedRoundActionButton(
            icon = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavourite) R.string.feed_action_remove_favourite else R.string.feed_action_favourite,
            ),
            iconTint = heartTint,
            iconScale = heartScale.value,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onToggleFavourite()
            },
            testTag = "feed_action_favourite",
        )
        FeedRoundActionButton(
            icon = Icons.Outlined.Share,
            contentDescription = stringResource(R.string.feed_action_share),
            iconTint = colors.textPrimary,
            onClick = onShare,
            testTag = "feed_action_share",
        )
        FeedRoundActionButton(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.feed_action_delete),
            iconTint = colors.textPrimary,
            onClick = onDelete,
            testTag = "feed_action_delete",
        )
    }
}

@Composable
private fun FeedRoundActionButton(
    icon: ImageVector,
    contentDescription: String,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String,
    iconScale: Float = 1f,
) {
    val colors = ReelboxThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.background.copy(alpha = 0.44f))
            .border(0.75.dp, colors.textPrimary.copy(alpha = 0.14f), CircleShape)
            .clickable(
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
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale),
        )
    }
}

/**
 * Bottom-left metadata overlay: filename (1 line, ellipsis) + date taken & file size.
 */
@Composable
fun FeedMetadataOverlay(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val context = LocalContext.current

    val title = remember(item.id, item.displayName, item.type) {
        MediaFormatters.displayTitle(context, item)
    }
    val subtitle = remember(item.id, item.dateTakenMillis, item.dateAddedSeconds, item.sizeBytes) {
        MediaFormatters.formatMetadataSubtitle(item)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("feed_item_filename"),
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("feed_item_meta"),
            )
        }
    }
}

/**
 * Thin accent progress bar sitting at the very bottom of a video.
 * Touching and dragging lets the user scrub; while scrubbing it grows thicker
 * and displays current time / total time above the bar.
 */
@Composable
fun VideoScrubBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onScrubStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
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
        positionMs.coerceIn(0L, safeDuration.coerceAtLeast(0L))
    }

    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 2.5.dp,
        animationSpec = ReelboxMotion.quickFade(),
        label = "scrub_bar_height",
    )

    val thumbAlpha by animateFloatAsState(
        targetValue = if (isScrubbing) 1f else 0f,
        animationSpec = ReelboxMotion.quickFade(),
        label = "scrub_thumb_alpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_scrub_bar"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = isScrubbing && safeDuration > 0L,
            enter = fadeIn(ReelboxMotion.quickFade()),
            exit = fadeOut(ReelboxMotion.quickFade()),
            modifier = Modifier.padding(bottom = spacing.sm),
        ) {
            Surface(
                shape = CircleShape,
                color = colors.background.copy(alpha = 0.72f),
                border = BorderStroke(0.75.dp, colors.divider),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = MediaFormatters.formatDuration(displayedPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = MediaFormatters.formatDuration(safeDuration),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // Generous bottom touch zone so touching and dragging feels effortless
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
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
            contentAlignment = Alignment.BottomStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .background(colors.textPrimary.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayedFraction)
                        .height(trackHeight)
                        .background(colors.accent),
                )
            }

            if (thumbAlpha > 0.01f) {
                val thumbSizePx = with(density) { 12.dp.roundToPx() }
                val thumbOffsetX = ((barWidthPx * displayedFraction) - thumbSizePx / 2f)
                    .roundToInt()
                    .coerceIn(0, (barWidthPx - thumbSizePx).coerceAtLeast(0))

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(x = thumbOffsetX, y = 3) }
                        .size(12.dp)
                        .alpha(thumbAlpha)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
    }
}

/**
 * Large popping accent heart displayed at the centre when double-tapping to favourite.
 */
@Composable
fun DoubleTapHeartOverlay(
    triggerId: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(triggerId) {
        if (triggerId <= 0) return@LaunchedEffect
        scale.snapTo(0.45f)
        alpha.snapTo(0.96f)
        scale.animateTo(
            targetValue = 1.12f,
            animationSpec = ReelboxMotion.heartBounce(),
        )
        delay(220L)
        launch {
            scale.animateTo(
                targetValue = 1.28f,
                animationSpec = tween(durationMillis = 240, easing = ReelboxMotion.EaseOut),
            )
        }
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 240, easing = ReelboxMotion.EaseOut),
        )
    }

    if (alpha.value > 0.01f) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .size(88.dp)
                    .scale(scale.value)
                    .alpha(alpha.value),
            )
        }
    }
}

/**
 * Faint large play icon shown when tapping to pause or resume, smoothly fading out.
 */
@Composable
fun TapPlayIndicatorOverlay(
    triggerId: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(triggerId) {
        if (triggerId <= 0) return@LaunchedEffect
        scale.snapTo(0.85f)
        alpha.snapTo(0.68f)
        launch {
            scale.animateTo(
                targetValue = 1.06f,
                animationSpec = tween(durationMillis = 260, easing = ReelboxMotion.EaseOut),
            )
        }
        delay(220L)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 280, easing = ReelboxMotion.EaseOut),
        )
    }

    if (alpha.value > 0.01f) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .clip(CircleShape)
                    .background(colors.background.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

/**
 * Calm bottom toast pill shown briefly after deleting a video or photo.
 */
@Composable
fun DeletedStatusBanner(
    message: String?,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(ReelboxMotion.quickFade()) + slideInVertically(ReelboxMotion.sheetSlide()) { it / 2 },
        exit = fadeOut(ReelboxMotion.quickFade()) + slideOutVertically(ReelboxMotion.quickFade()) { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceRaised,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.testTag("feed_deleted_toast"),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
        }
    }
}
