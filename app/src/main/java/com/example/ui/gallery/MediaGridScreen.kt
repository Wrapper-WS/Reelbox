package com.example.ui.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.MediaAlbum
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.ui.LibraryLoadState
import com.example.ui.ReelboxUiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PartialAccessBanner
import com.example.ui.home.MediaFormatters
import com.example.ui.theme.ReelboxThemeTokens

/**
 * Reusable 3-column gallery grid screen for the Photos tab and the Videos tab.
 * Square 12dp tiles with 2dp gaps, Coil-sized thumbnails, video duration badge,
 * favourite heart badge, folder filtering bottom sheet, and full-screen item opening.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaGridScreen(
    mediaType: MediaType,
    uiState: ReelboxUiState,
    onSelectFolder: (String?) -> Unit,
    onOpenItem: (index: Int, bucketId: String?) -> Unit,
    onChooseMoreMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    val allItemsOfType: List<MediaItem> = remember(uiState.photos, uiState.videos, mediaType) {
        if (mediaType == MediaType.VIDEO) uiState.videos else uiState.photos
    }
    val foldersOfType: List<MediaAlbum> = remember(uiState.photoAlbums, uiState.videoAlbums, mediaType) {
        val raw = if (mediaType == MediaType.VIDEO) uiState.videoAlbums else uiState.photoAlbums
        raw.filter { album ->
            val count = if (mediaType == MediaType.VIDEO) album.videoCount else album.photoCount
            count > 0
        }
    }
    val selectedBucketId: String? = if (mediaType == MediaType.VIDEO) {
        uiState.selectedVideoBucketId
    } else {
        uiState.selectedPhotoBucketId
    }
    val selectedFolderName: String? = remember(selectedBucketId, foldersOfType) {
        selectedBucketId?.let { id -> foldersOfType.firstOrNull { it.bucketId == id }?.name }
    }

    val displayedItems: List<MediaItem> = remember(allItemsOfType, selectedBucketId) {
        if (selectedBucketId == null) {
            allItemsOfType
        } else {
            allItemsOfType.filter { it.bucketId == selectedBucketId }
        }
    }

    val gridState = rememberLazyGridState()
    var lastScrolledBucketId by rememberSaveable(mediaType) { mutableStateOf(selectedBucketId) }
    var showFolderSheet by remember { mutableStateOf(false) }

    // Scroll back to top only when the user switches folders, preserving scroll across tab switches.
    LaunchedEffect(selectedBucketId) {
        if (lastScrolledBucketId != selectedBucketId) {
            lastScrolledBucketId = selectedBucketId
            gridState.scrollToItem(0)
        }
    }

    val isLoading = uiState.libraryLoadState == LibraryLoadState.Loading ||
        uiState.libraryLoadState == LibraryLoadState.Idle

    val titleRes = if (mediaType == MediaType.VIDEO) R.string.nav_videos else R.string.nav_photos
    val countPluralRes = if (mediaType == MediaType.VIDEO) R.plurals.count_videos else R.plurals.count_photos
    val emptyTitleRes = if (mediaType == MediaType.VIDEO) R.string.empty_videos_title else R.string.empty_photos_title
    val emptySubtitleRes = if (mediaType == MediaType.VIDEO) {
        R.string.empty_videos_subtitle
    } else {
        R.string.empty_photos_subtitle
    }
    val emptyIcon = if (mediaType == MediaType.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Image
    val screenTag = if (mediaType == MediaType.VIDEO) "screen_videos" else "screen_photos"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .testTag(screenTag),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: title + count beside it, optional chosen folder name below
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = spacing.md,
                        end = spacing.md,
                        top = spacing.md,
                        bottom = spacing.sm,
                    ),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isLoading) {
                        Text(
                            text = pluralStringResource(
                                countPluralRes,
                                displayedItems.size,
                                displayedItems.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .align(Alignment.Bottom)
                                .padding(bottom = 3.dp)
                                .testTag("${screenTag}_count"),
                        )
                    }
                }

                if (selectedFolderName != null) {
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("${screenTag}_folder_subtitle"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = selectedFolderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (uiState.isPartialAccess) {
                PartialAccessBanner(
                    onChooseMore = onChooseMoreMedia,
                    modifier = Modifier.padding(
                        start = spacing.md,
                        end = spacing.md,
                        bottom = spacing.sm,
                    ),
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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

                displayedItems.isEmpty() -> {
                    EmptyStateView(
                        icon = emptyIcon,
                        title = stringResource(emptyTitleRes),
                        subtitle = stringResource(emptySubtitleRes),
                        actionLabel = if (uiState.isPartialAccess) {
                            stringResource(R.string.partial_access_action)
                        } else {
                            null
                        },
                        onActionClick = if (uiState.isPartialAccess) onChooseMoreMedia else null,
                        testTag = "empty_state_${mediaType.name.lowercase()}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                else -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        val density = LocalDensity.current
                        val tileSizePx = remember(maxWidth, density) {
                            with(density) {
                                ((maxWidth - 8.dp) / 3f).roundToPx().coerceIn(180, 480)
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(
                                start = 2.dp,
                                end = 2.dp,
                                top = 2.dp,
                                bottom = 88.dp,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("${screenTag}_grid"),
                        ) {
                            itemsIndexed(
                                items = displayedItems,
                                key = { _, item -> item.id },
                                contentType = { _, item -> item.type },
                            ) { index, item ->
                                MediaGridTile(
                                    item = item,
                                    isFavourite = item.id in uiState.favouriteIds,
                                    tileSizePx = tileSizePx,
                                    onClick = { onOpenItem(index, selectedBucketId) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom-right floating round Folder button (just above bottom tab bar)
        if (!isLoading && allItemsOfType.isNotEmpty()) {
            FloatingFolderButton(
                onClick = { showFolderSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = spacing.md, bottom = spacing.md),
                testTag = "${screenTag}_folder_fab",
            )
        }
    }

    if (showFolderSheet) {
        FolderPickerBottomSheet(
            mediaType = mediaType,
            totalCount = allItemsOfType.size,
            allCoverUri = allItemsOfType.firstOrNull()?.contentUri,
            folders = foldersOfType,
            selectedBucketId = selectedBucketId,
            onSelectFolder = { bucketId ->
                showFolderSheet = false
                onSelectFolder(bucketId)
            },
            onDismiss = { showFolderSheet = false },
        )
    }
}

@Composable
internal fun MediaGridTile(
    item: MediaItem,
    isFavourite: Boolean,
    tileSizePx: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = ReelboxThemeTokens.colors
    val tileShape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val imageRequest = remember(item.id, item.contentUri, tileSizePx) {
        ImageRequest.Builder(context)
            .data(item.contentUri)
            .size(tileSizePx)
            .memoryCacheKey("grid_thumb_${item.id}_$tileSizePx")
            .allowHardware(true)
            .crossfade(140)
            .build()
    }

    val durationText = remember(item.id, item.durationMillis, item.type) {
        if (item.type == MediaType.VIDEO) {
            MediaFormatters.formatDuration(item.durationMillis)
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(tileShape)
            .background(colors.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colors.textPrimary.copy(alpha = 0.22f)),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("media_tile_${item.id}"),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = MediaFormatters.displayTitle(context, item),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (isFavourite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.background.copy(alpha = 0.48f))
                    .testTag("media_tile_heart_${item.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.label_favourited),
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (durationText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.background.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("media_tile_duration_${item.id}"),
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun FloatingFolderButton(
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val openFoldersDescription = stringResource(R.string.action_open_folders)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .size(56.dp)
            .semantics { contentDescription = openFoldersDescription }
            .testTag(testTag),
        shape = CircleShape,
        color = colors.surfaceRaised,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = colors.textPrimary.copy(alpha = 0.22f)),
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
