package com.example.ui.gallery

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.ui.ReelboxUiState
import com.example.ui.home.ClearModeHintBanner
import com.example.ui.home.ClearModeRestoreButton
import com.example.ui.home.DeleteConfirmationDialog
import com.example.ui.home.DeleteRequestResult
import com.example.ui.home.DeletedStatusBanner
import com.example.ui.home.MediaFormatters
import com.example.ui.home.PhotoVerticalFeed
import com.example.ui.home.ShareBottomSheet
import com.example.ui.home.ShareTargetResolver
import com.example.ui.home.VideoVerticalFeed
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens
import kotlinx.coroutines.delay

@Immutable
data class MediaViewerSession(
    val mediaType: MediaType,
    val bucketIdFilter: String?,
    val initialIndex: Int,
    val snapshotItemIds: List<Long>? = null,
)

/**
 * Full-screen viewer opened from Photos, Videos, or Favourites grid tiles.
 * Reuses the exact Home feed vertical viewer (gestures, landscape mode, 2x speed, Share to sheet,
 * favourite & system delete) scoped to the items currently displayed in the grid,
 * with a round top-left Back button that returns to the grid at its exact scroll position.
 */
@Composable
fun MediaViewerOverlay(
    session: MediaViewerSession,
    uiState: ReelboxUiState,
    isFullScreenLandscape: Boolean,
    isClearMode: Boolean,
    onSetFullScreenLandscape: (Boolean) -> Unit,
    onSetClearMode: (Boolean) -> Unit,
    onMarkClearModeHintSeen: () -> Unit,
    onClose: () -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onSetFavourite: (Long, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onMediaDeleted: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = ReelboxThemeTokens.spacing

    val items = remember(
        uiState.photos,
        uiState.videos,
        session.mediaType,
        session.bucketIdFilter,
        session.snapshotItemIds,
    ) {
        val base = if (session.mediaType == MediaType.VIDEO) uiState.videos else uiState.photos
        val snapshotIds = session.snapshotItemIds
        when {
            snapshotIds != null -> {
                val byId = base.associateBy { it.id }
                snapshotIds.mapNotNull { id -> byId[id] }
            }

            session.bucketIdFilter != null -> {
                base.filter { it.bucketId == session.bucketIdFilter }
            }

            else -> base
        }
    }

    var confirmDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var deletedToastMessage by remember { mutableStateOf<String?>(null) }
    var shareSheetItem by remember { mutableStateOf<MediaItem?>(null) }

    var clearModeRestoreVisible by remember { mutableStateOf(false) }
    var clearModeRestoreTick by remember { mutableIntStateOf(0) }
    var showFirstTimeClearHint by remember { mutableStateOf(false) }

    val videoDeletedMessage = stringResource(R.string.feed_video_deleted)
    val photoDeletedMessage = stringResource(R.string.feed_photo_deleted)

    val enterClearMode: () -> Unit = {
        onSetClearMode(true)
        if (!uiState.hasSeenClearModeHint) {
            showFirstTimeClearHint = true
            onMarkClearModeHintSeen()
        }
    }

    // Back button exits landscape first, then exits clear mode, otherwise closes viewer back to grid.
    BackHandler {
        when {
            isFullScreenLandscape -> onSetFullScreenLandscape(false)
            isClearMode -> onSetClearMode(false)
            else -> onClose()
        }
    }

    // Close viewer cleanly if the last item in the filtered grid is deleted.
    LaunchedEffect(items.isEmpty()) {
        if (items.isEmpty()) {
            onSetClearMode(false)
            onSetFullScreenLandscape(false)
            onClose()
        }
    }

    LaunchedEffect(isClearMode, clearModeRestoreTick) {
        if (!isClearMode || clearModeRestoreTick == 0) {
            clearModeRestoreVisible = false
            return@LaunchedEffect
        }
        clearModeRestoreVisible = true
        delay(2_000L)
        clearModeRestoreVisible = false
    }

    LaunchedEffect(isClearMode, showFirstTimeClearHint) {
        if (!isClearMode) {
            showFirstTimeClearHint = false
        } else if (showFirstTimeClearHint) {
            delay(3_000L)
            showFirstTimeClearHint = false
        }
    }

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
        if (confirmDeleteItem == null && pendingDeleteItem == null) {
            confirmDeleteItem = item
        }
    }

    val executeConfirmedDelete: (MediaItem) -> Unit = { item ->
        when (val outcome = MediaFormatters.prepareDeleteRequest(context, item)) {
            DeleteRequestResult.DeletedDirectly -> {
                confirmDeleteItem = null
                onMediaDeleted(item.id)
                deletedToastMessage = if (item.type == MediaType.VIDEO) {
                    videoDeletedMessage
                } else {
                    photoDeletedMessage
                }
            }

            is DeleteRequestResult.NeedsSystemConfirmation -> {
                pendingDeleteItem = item
                confirmDeleteItem = null
                val request = IntentSenderRequest.Builder(outcome.intentSender).build()
                runCatching { deleteResultLauncher.launch(request) }
                    .onFailure { pendingDeleteItem = null }
            }

            DeleteRequestResult.NeedsLegacyConfirmation -> {
                confirmDeleteItem = null
                if (MediaFormatters.deleteDirectly(context, item)) {
                    onMediaDeleted(item.id)
                    deletedToastMessage = if (item.type == MediaType.VIDEO) {
                        videoDeletedMessage
                    } else {
                        photoDeletedMessage
                    }
                }
            }

            DeleteRequestResult.Failed -> {
                confirmDeleteItem = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isFullScreenLandscape && !isClearMode) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                },
            )
            .testTag("media_viewer_overlay"),
    ) {
        if (items.isNotEmpty()) {
            when (session.mediaType) {
                MediaType.VIDEO -> {
                    VideoVerticalFeed(
                        videos = items,
                        favouriteIds = uiState.favouriteIds,
                        isMuted = uiState.isMuted,
                        isFullScreenLandscape = isFullScreenLandscape,
                        isClearMode = isClearMode && !isFullScreenLandscape,
                        isPausedByOverlay = shareSheetItem != null ||
                            confirmDeleteItem != null ||
                            pendingDeleteItem != null,
                        onEnterFullScreen = { onSetFullScreenLandscape(true) },
                        onExitFullScreen = { onSetFullScreenLandscape(false) },
                        onEnterClearMode = enterClearMode,
                        onClearModeTap = {
                            showFirstTimeClearHint = false
                            clearModeRestoreTick++
                        },
                        onToggleFavourite = onToggleFavourite,
                        onSetFavourite = onSetFavourite,
                        onOpenShareSheet = { item -> shareSheetItem = item },
                        onDelete = requestDeleteItem,
                        initialPage = session.initialIndex,
                    )
                }

                MediaType.PHOTO -> {
                    PhotoVerticalFeed(
                        photos = items,
                        favouriteIds = uiState.favouriteIds,
                        isClearMode = isClearMode,
                        onEnterClearMode = enterClearMode,
                        onClearModeTap = {
                            showFirstTimeClearHint = false
                            clearModeRestoreTick++
                        },
                        onToggleFavourite = onToggleFavourite,
                        onSetFavourite = onSetFavourite,
                        onOpenShareSheet = { item -> shareSheetItem = item },
                        onDelete = requestDeleteItem,
                        initialPage = session.initialIndex,
                    )
                }
            }
        }

        // Top controls in portrait: round Back button top-left + round Mute button top-right (videos)
        AnimatedVisibility(
            visible = !isFullScreenLandscape && !isClearMode,
            enter = fadeIn(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)),
            exit = fadeOut(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
            ) {
                ViewerTopCircleButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onClose,
                    testTag = "media_viewer_back_button",
                    modifier = Modifier.align(Alignment.CenterStart),
                )

                if (session.mediaType == MediaType.VIDEO && items.isNotEmpty()) {
                    ViewerTopCircleButton(
                        icon = if (uiState.isMuted) {
                            Icons.AutoMirrored.Outlined.VolumeOff
                        } else {
                            Icons.AutoMirrored.Outlined.VolumeUp
                        },
                        contentDescription = stringResource(
                            if (uiState.isMuted) R.string.feed_action_unmute else R.string.feed_action_mute,
                        ),
                        onClick = onToggleMute,
                        testTag = "media_viewer_mute_button",
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }

        // Clear mode bottom-right restore eye button (fades in on tap, auto-hides after 2 seconds)
        ClearModeRestoreButton(
            visible = isClearMode && !isFullScreenLandscape && clearModeRestoreVisible,
            onExitClearMode = { onSetClearMode(false) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = spacing.md, bottom = 20.dp),
        )

        // One-time clear mode first-use hint pill
        ClearModeHintBanner(
            visible = isClearMode && !isFullScreenLandscape && showFirstTimeClearHint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = spacing.md, vertical = spacing.xl),
        )

        DeletedStatusBanner(
            message = deletedToastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.xl),
        )
    }

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

    val itemPendingConfirmation = confirmDeleteItem
    if (itemPendingConfirmation != null) {
        DeleteConfirmationDialog(
            item = itemPendingConfirmation,
            isLandscape = isFullScreenLandscape,
            onDismiss = { confirmDeleteItem = null },
            onConfirmDelete = { executeConfirmedDelete(itemPendingConfirmation) },
        )
    }
}

@Composable
private fun ViewerTopCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 22.dp, color = colors.textPrimary),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.background.copy(alpha = 0.48f))
                .border(0.75.dp, colors.textPrimary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
