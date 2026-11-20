package com.example.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.MediaItem
import com.example.ui.theme.ReelboxGradients
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens

/**
 * Full-screen photo feed item rendered at true aspect ratio (ContentScale.Fit) on pure black.
 * Supports pinch-only zoom (1x–4x) while reserving double-tap exclusively for favouriting.
 */
@Composable
fun ZoomablePhotoPage(
    item: MediaItem,
    isCurrentPage: Boolean,
    isFavourite: Boolean,
    isClearMode: Boolean,
    onToggleFavourite: () -> Unit,
    onDoubleTapFavourite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onEnterClearMode: () -> Unit,
    onClearModeTap: () -> Unit,
    onZoomedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val spacing = ReelboxThemeTokens.spacing

    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(item.id) { mutableStateOf(IntSize.Zero) }
    var doubleTapHeartTrigger by remember(item.id) { mutableIntStateOf(0) }

    // Reset zoom cleanly whenever the user navigates away from this page.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && (scale != 1f || offset != Offset.Zero)) {
            scale = 1f
            offset = Offset.Zero
            onZoomedChanged(false)
        }
    }

    val imageRequest = remember(item.id, item.contentUri) {
        ImageRequest.Builder(context)
            .data(item.contentUri)
            .crossfade(180)
            .build()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .testTag("photo_feed_page_${item.id}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(item.id, isClearMode) {
                    detectTapGestures(
                        onTap = {
                            if (isClearMode) {
                                onClearModeTap()
                            }
                        },
                        onLongPress = { offset ->
                            val viewportWidth = size.width.toFloat()
                            if (viewportWidth > 0f &&
                                offset.x >= viewportWidth * 0.25f &&
                                offset.x <= viewportWidth * 0.75f
                            ) {
                                onShare()
                            }
                        },
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            doubleTapHeartTrigger++
                            onDoubleTapFavourite()
                        },
                    )
                }
                .pointerInput(item.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                                val nextOffset = clampOffset(
                                    offset = offset + panChange,
                                    scale = nextScale,
                                    size = containerSize,
                                )
                                scale = nextScale
                                offset = if (nextScale <= 1.01f) Offset.Zero else nextOffset
                                onZoomedChanged(nextScale > 1.02f)
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            } else if (scale > 1.02f && pressedCount == 1) {
                                val panChange = event.calculatePan()
                                offset = clampOffset(
                                    offset = offset + panChange,
                                    scale = scale,
                                    size = containerSize,
                                )
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (scale < 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomedChanged(false)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = MediaFormatters.displayTitle(context, item),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }

        AnimatedVisibility(
            visible = !isClearMode,
            enter = fadeIn(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)),
            exit = fadeOut(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Soft top gradient scrim for top tabs readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .align(Alignment.TopCenter)
                        .background(ReelboxGradients.TopOverlayScrim),
                )

                // Soft bottom gradient scrim for metadata readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .align(Alignment.BottomCenter)
                        .background(ReelboxGradients.BottomOverlayScrim),
                )

                // Right-side action column aligned at the bottom, level with the date & file size line
                FeedActionRail(
                    isFavourite = isFavourite,
                    onToggleFavourite = onToggleFavourite,
                    onShare = onShare,
                    onDelete = onDelete,
                    onEnterClearMode = onEnterClearMode,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = spacing.md, bottom = 20.dp),
                )

                // Bottom-left file name, date taken, and file size (kept clear of right buttons)
                FeedMetadataOverlay(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = spacing.md,
                            end = 84.dp,
                            bottom = 20.dp,
                        ),
                )
            }
        }

        // Centre double-tap popping heart
        DoubleTapHeartOverlay(
            triggerId = doubleTapHeartTrigger,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun clampOffset(
    offset: Offset,
    scale: Float,
    size: IntSize,
): Offset {
    if (scale <= 1f || size.width == 0 || size.height == 0) return Offset.Zero
    val maxX = ((size.width * scale) - size.width) / 2f
    val maxY = ((size.height * scale) - size.height) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}
