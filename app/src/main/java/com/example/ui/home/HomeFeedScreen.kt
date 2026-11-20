package com.example.ui.home

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.ui.LibraryLoadState
import com.example.ui.ReelboxUiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PartialAccessBanner
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home tab: full-screen vertical swipe feed for local Videos (default) and Images.
 * Supports immersive landscape full screen for videos, 2x hold playback, and the "Share to" sheet.
 */
@Composable
fun HomeFeedScreen(
    uiState: ReelboxUiState,
    isFullScreenLandscape: Boolean,
    onSetFullScreenLandscape: (Boolean) -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onSetFavourite: (Long, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onMediaDeleted: (Long) -> Unit,
    onChooseMoreMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    var feedMode by rememberSaveable { mutableStateOf(HomeFeedMode.VIDEOS) }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var legacyConfirmDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var deletedToastMessage by remember { mutableStateOf<String?>(null) }
    var shareSheetItem by remember { mutableStateOf<MediaItem?>(null) }

    val videoDeletedMessage = stringResource(R.string.feed_video_deleted)
    val photoDeletedMessage = stringResource(R.string.feed_photo_deleted)

    // System Back in full-screen landscape returns smoothly to portrait on the same video.
    BackHandler(enabled = isFullScreenLandscape) {
        onSetFullScreenLandscape(false)
    }

    // If all videos are deleted while in landscape, return gracefully to portrait empty state.
    LaunchedEffect(uiState.videos.isEmpty(), feedMode, isFullScreenLandscape) {
        if (isFullScreenLandscape && (feedMode != HomeFeedMode.VIDEOS || uiState.videos.isEmpty())) {
            onSetFullScreenLandscape(false)
        }
    }

    // Auto-dismiss the deletion confirmation pill calmly.
    LaunchedEffect(deletedToastMessage) {
        if (deletedToastMessage != null) {
            delay(2_200L)
            deletedToastMessage = null
        }
    }

    val deleteResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val item = pendingDeleteItem
        pendingDeleteItem = null
        if (result.resultCode == Activity.RESULT_OK && item != null) {
            val completed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                true
            } else {
                MediaFormatters.deleteDirectly(context, item)
            }
            if (completed) {
                onMediaDeleted(item.id)
                deletedToastMessage = if (item.type == MediaType.VIDEO) {
                    videoDeletedMessage
                } else {
                    photoDeletedMessage
                }
            }
        }
    }

    val requestDeleteItem: (MediaItem) -> Unit = { item ->
        when (val outcome = MediaFormatters.prepareDeleteRequest(context, item)) {
            DeleteRequestResult.DeletedDirectly -> {
                onMediaDeleted(item.id)
                deletedToastMessage = if (item.type == MediaType.VIDEO) {
                    videoDeletedMessage
                } else {
                    photoDeletedMessage
                }
            }

            is DeleteRequestResult.NeedsSystemConfirmation -> {
                pendingDeleteItem = item
                val request = IntentSenderRequest.Builder(outcome.intentSender).build()
                runCatching { deleteResultLauncher.launch(request) }
                    .onFailure { pendingDeleteItem = null }
            }

            DeleteRequestResult.NeedsLegacyConfirmation -> {
                legacyConfirmDeleteItem = item
            }

            DeleteRequestResult.Failed -> Unit
        }
    }

    val isLoading = uiState.libraryLoadState == LibraryLoadState.Loading ||
        uiState.libraryLoadState == LibraryLoadState.Idle

    val showMuteButton = !isLoading &&
        !isFullScreenLandscape &&
        feedMode == HomeFeedMode.VIDEOS &&
        uiState.videos.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("screen_home"),
    ) {
        if (isLoading) {
            FeedLoadingState()
        } else {
            AnimatedContent(
                targetState = feedMode,
                transitionSpec = {
                    fadeIn(ReelboxMotion.standardFade()) togetherWith fadeOut(ReelboxMotion.quickFade())
                },
                modifier = Modifier.fillMaxSize(),
                label = "home_feed_mode_crossfade",
            ) { activeMode ->
                when (activeMode) {
                    HomeFeedMode.VIDEOS -> {
                        if (uiState.videos.isEmpty()) {
                            EmptyStateView(
                                icon = Icons.Outlined.Videocam,
                                title = stringResource(R.string.empty_videos_title),
                                subtitle = stringResource(R.string.empty_videos_subtitle),
                                actionLabel = if (uiState.isPartialAccess) {
                                    stringResource(R.string.partial_access_action)
                                } else {
                                    null
                                },
                                onActionClick = if (uiState.isPartialAccess) onChooseMoreMedia else null,
                                testTag = "empty_state_home_videos",
                            )
                        } else {
                            VideoVerticalFeed(
                                videos = uiState.videos,
                                favouriteIds = uiState.favouriteIds,
                                isMuted = uiState.isMuted,
                                isFullScreenLandscape = isFullScreenLandscape,
                                isPausedByOverlay = shareSheetItem != null,
                                onEnterFullScreen = { onSetFullScreenLandscape(true) },
                                onExitFullScreen = { onSetFullScreenLandscape(false) },
                                onToggleFavourite = onToggleFavourite,
                                onSetFavourite = onSetFavourite,
                                onOpenShareSheet = { item -> shareSheetItem = item },
                                onDelete = requestDeleteItem,
                            )
                        }
                    }

                    HomeFeedMode.IMAGES -> {
                        if (uiState.photos.isEmpty()) {
                            EmptyStateView(
                                icon = Icons.Outlined.Image,
                                title = stringResource(R.string.empty_photos_title),
                                subtitle = stringResource(R.string.empty_photos_subtitle),
                                actionLabel = if (uiState.isPartialAccess) {
                                    stringResource(R.string.partial_access_action)
                                } else {
                                    null
                                },
                                onActionClick = if (uiState.isPartialAccess) onChooseMoreMedia else null,
                                testTag = "empty_state_home_images",
                            )
                        } else {
                            PhotoVerticalFeed(
                                photos = uiState.photos,
                                favouriteIds = uiState.favouriteIds,
                                onToggleFavourite = onToggleFavourite,
                                onSetFavourite = onSetFavourite,
                                onOpenShareSheet = { item -> shareSheetItem = item },
                                onDelete = requestDeleteItem,
                            )
                        }
                    }
                }
            }
        }

        // Centred "Videos" | "Images" switcher + top-right mute button (hidden in landscape full screen)
        if (!isFullScreenLandscape) {
            val activeFeedHasItems = when (feedMode) {
                HomeFeedMode.VIDEOS -> uiState.videos.isNotEmpty()
                HomeFeedMode.IMAGES -> uiState.photos.isNotEmpty()
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                HomeFeedTopBar(
                    selectedMode = feedMode,
                    onSelectMode = { nextMode ->
                        if (feedMode != nextMode) {
                            feedMode = nextMode
                        }
                    },
                    showMuteButton = showMuteButton,
                    isMuted = uiState.isMuted,
                    onToggleMute = onToggleMute,
                )
                if (!isLoading && uiState.isPartialAccess && activeFeedHasItems) {
                    PartialAccessBanner(
                        onChooseMore = onChooseMoreMedia,
                        modifier = Modifier
                            .displayCutoutPadding()
                            .padding(horizontal = spacing.md, vertical = spacing.xxs),
                    )
                }
            }
        }

        // Friendly delete confirmation message at the bottom
        DeletedStatusBanner(
            message = deletedToastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.xl),
        )
    }

    // Custom "Share to" bottom sheet (pauses video while open and resumes on close)
    val itemToShare = shareSheetItem
    if (itemToShare != null) {
        ShareBottomSheet(
            item = itemToShare,
            onDismiss = { shareSheetItem = null },
            onSelectApp = { target ->
                shareSheetItem = null
                ShareTargetResolver.launchDirectShare(context, itemToShare, target)
            },
            onSelectMore = {
                shareSheetItem = null
                MediaFormatters.shareMedia(context, itemToShare)
            },
        )
    }

    // Confirmation dialog fallback for Android 9 and below (API 24–28)
    val confirmItem = legacyConfirmDeleteItem
    if (confirmItem != null) {
        AlertDialog(
            onDismissRequest = { legacyConfirmDeleteItem = null },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            shape = MaterialTheme.shapes.medium,
            title = {
                Text(
                    text = stringResource(
                        if (confirmItem.type == MediaType.VIDEO) {
                            R.string.feed_delete_video_title
                        } else {
                            R.string.feed_delete_photo_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.feed_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            dismissButton = {
                TextButton(onClick = { legacyConfirmDeleteItem = null }) {
                    Text(
                        text = stringResource(R.string.feed_delete_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        legacyConfirmDeleteItem = null
                        if (MediaFormatters.deleteDirectly(context, confirmItem)) {
                            onMediaDeleted(confirmItem.id)
                            deletedToastMessage = if (confirmItem.type == MediaType.VIDEO) {
                                videoDeletedMessage
                            } else {
                                photoDeletedMessage
                            }
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.feed_delete_confirm),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                    )
                }
            },
        )
    }
}

@Composable
internal fun VideoVerticalFeed(
    videos: List<MediaItem>,
    favouriteIds: Set<Long>,
    isMuted: Boolean,
    isFullScreenLandscape: Boolean,
    isPausedByOverlay: Boolean,
    onEnterFullScreen: () -> Unit,
    onExitFullScreen: () -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onSetFavourite: (Long, Boolean) -> Unit,
    onOpenShareSheet: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    initialPage: Int = 0,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPool = remember(context) { FeedPlayerPool(context) }
    val clampedInitial = initialPage.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = clampedInitial) { videos.size }

    var isAppResumed by remember { mutableStateOf(true) }
    var isUserPaused by remember { mutableStateOf(false) }
    var is2xSpeedActive by remember { mutableStateOf(false) }

    val safeCurrentIndex = pagerState.currentPage.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val safeSettledIndex = pagerState.settledPage.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val activeVideo = videos.getOrNull(safeCurrentIndex)

    // Keep at most two players: active + preloaded next at rest; avoid churn mid-swipe.
    val preloadVideo = if (pagerState.isScrollInProgress) {
        if (safeCurrentIndex == safeSettledIndex) {
            videos.getOrNull(safeSettledIndex + 1)
        } else {
            videos.getOrNull(safeSettledIndex)
        }
    } else {
        videos.getOrNull(safeSettledIndex + 1)
    }

    // Auto-resume playback and reset 2x speed whenever moving to a different video.
    LaunchedEffect(activeVideo?.id) {
        isUserPaused = false
        is2xSpeedActive = false
    }

    val shouldPlayActive = isAppResumed && !isPausedByOverlay && (!isUserPaused || is2xSpeedActive)

    // Synchronize the 2-player window whenever visible page, mute, pause, overlay, or lifecycle changes.
    LaunchedEffect(
        activeVideo?.id,
        preloadVideo?.id,
        shouldPlayActive,
        isMuted,
    ) {
        playerPool.syncWindow(
            activeItem = activeVideo,
            preloadItem = preloadVideo,
            shouldPlayActive = shouldPlayActive,
            isMuted = isMuted,
        )
    }

    LaunchedEffect(activeVideo?.id, is2xSpeedActive) {
        val currentId = activeVideo?.id ?: return@LaunchedEffect
        playerPool.setPlaybackSpeed(
            mediaId = currentId,
            speed = if (is2xSpeedActive) 2f else 1f,
        )
    }

    LaunchedEffect(isMuted) {
        playerPool.setMuted(isMuted)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isAppResumed = true
        playerPool.recoverOnForeground(
            activeMediaId = activeVideo?.id,
            shouldPlayActive = !isPausedByOverlay && (!isUserPaused || is2xSpeedActive),
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        isAppResumed = false
        is2xSpeedActive = false
        playerPool.pauseAll()
    }

    DisposableEffect(playerPool) {
        onDispose {
            playerPool.releaseAll()
        }
    }

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.25f,
    )

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = !isFullScreenLandscape,
        flingBehavior = flingBehavior,
        key = { index -> videos.getOrNull(index)?.id ?: index },
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_videos_pager"),
    ) { pageIndex ->
        val item = videos.getOrNull(pageIndex) ?: return@VerticalPager
        val isCurrent = pageIndex == pagerState.currentPage
        VideoFeedPage(
            item = item,
            player = playerPool.playerFor(item.id),
            aspectRatio = playerPool.aspectRatioFor(item),
            hasPlaybackError = playerPool.hasError(item.id),
            isCurrentPage = isCurrent,
            isPlaying = isCurrent && shouldPlayActive,
            isFavourite = item.id in favouriteIds,
            isFullScreenLandscape = isFullScreenLandscape,
            isShareSheetOpen = isPausedByOverlay,
            hasPrevious = pageIndex > 0,
            hasNext = pageIndex < videos.lastIndex,
            onTogglePlayPause = {
                if (isCurrent) {
                    isUserPaused = !isUserPaused
                }
            },
            onToggleFavourite = { onToggleFavourite(item.id) },
            onDoubleTapFavourite = { onSetFavourite(item.id, true) },
            onOpenShareSheet = { onOpenShareSheet(item) },
            onDelete = { onDelete(item) },
            onEnterFullScreen = onEnterFullScreen,
            onExitFullScreen = onExitFullScreen,
            onPrevious = {
                val targetIndex = (pageIndex - 1).coerceAtLeast(0)
                if (targetIndex != pageIndex) {
                    coroutineScope.launch { pagerState.scrollToPage(targetIndex) }
                }
            },
            onNext = {
                val targetIndex = (pageIndex + 1).coerceAtMost(videos.lastIndex)
                if (targetIndex != pageIndex) {
                    coroutineScope.launch { pagerState.scrollToPage(targetIndex) }
                }
            },
            onSpeedHoldChanged = { holding ->
                if (isCurrent) {
                    is2xSpeedActive = holding
                }
            },
        )
    }
}

@Composable
internal fun PhotoVerticalFeed(
    photos: List<MediaItem>,
    favouriteIds: Set<Long>,
    onToggleFavourite: (Long) -> Unit,
    onSetFavourite: (Long, Boolean) -> Unit,
    onOpenShareSheet: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    initialPage: Int = 0,
) {
    val clampedInitial = initialPage.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = clampedInitial) { photos.size }
    var isCurrentPhotoZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isCurrentPhotoZoomed = false
    }

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.25f,
    )

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = !isCurrentPhotoZoomed,
        flingBehavior = flingBehavior,
        key = { index -> photos.getOrNull(index)?.id ?: index },
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_images_pager"),
    ) { pageIndex ->
        val item = photos.getOrNull(pageIndex) ?: return@VerticalPager
        val isCurrent = pageIndex == pagerState.currentPage
        ZoomablePhotoPage(
            item = item,
            isCurrentPage = isCurrent,
            isFavourite = item.id in favouriteIds,
            onToggleFavourite = { onToggleFavourite(item.id) },
            onDoubleTapFavourite = { onSetFavourite(item.id, true) },
            onShare = { onOpenShareSheet(item) },
            onDelete = { onDelete(item) },
            onZoomedChanged = { zoomed ->
                if (isCurrent) {
                    isCurrentPhotoZoomed = zoomed
                }
            },
        )
    }
}

@Composable
private fun FeedLoadingState() {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("home_feed_loading"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            CircularProgressIndicator(
                color = colors.accent,
                trackColor = colors.divider,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(R.string.loading_library),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
