package com.example.ui.gallery

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.home.DeleteRequestResult
import com.example.ui.home.DeletedStatusBanner
import com.example.ui.home.MediaFormatters
import com.example.ui.home.PhotoVerticalFeed
import com.example.ui.home.ShareBottomSheet
import com.example.ui.home.ShareTargetResolver
import com.example.ui.home.VideoVerticalFeed
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
    onSetFullScreenLandscape: (Boolean) -> Unit,
    onClose: () -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onSetFavourite: (Long, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onMediaDeleted: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = ReelboxThemeTokens.colors
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

    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var legacyConfirmDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var deletedToastMessage by remember { mutableStateOf<String?>(null) }
    var shareSheetItem by remember { mutableStateOf<MediaItem?>(null) }

    val videoDeletedMessage = stringResource(R.string.feed_video_deleted)
    val photoDeletedMessage = stringResource(R.string.feed_photo_deleted)

    // Back button exits landscape first if in landscape, otherwise closes the viewer back to the grid.
    BackHandler {
        if (isFullScreenLandscape) {
            onSetFullScreenLandscape(false)
        } else {
            onClose()
        }
    }

    // Close viewer cleanly if the last item in the filtered grid is deleted.
    LaunchedEffect(items.isEmpty()) {
        if (items.isEmpty()) {
            onSetFullScreenLandscape(false)
            onClose()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isFullScreenLandscape) {
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
                        isPausedByOverlay = shareSheetItem != null,
                        onEnterFullScreen = { onSetFullScreenLandscape(true) },
                        onExitFullScreen = { onSetFullScreenLandscape(false) },
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
        if (!isFullScreenLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = spacing.md, vertical = spacing.xs)
                    .align(Alignment.TopCenter),
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
