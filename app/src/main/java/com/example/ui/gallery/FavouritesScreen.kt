package com.example.ui.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.ui.LibraryLoadState
import com.example.ui.ReelboxUiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PartialAccessBanner
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens

private enum class FavouritesTabMode {
    VIDEOS,
    PHOTOS,
}

/**
 * Favourites tab:
 * - Header with "Favourites" title + count and a Home-styled "Videos" / "Photos" switch (Videos default)
 * - Same 3-column square grid with 12dp corners, 2dp gaps, duration & heart badges
 * - Opens the full-screen viewer scoped to the currently shown favourites snapshot
 * - Calm heart empty state when no favourites exist for the selected type
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavouritesScreen(
    uiState: ReelboxUiState,
    onOpenItem: (mediaType: MediaType, index: Int, snapshotIds: List<Long>) -> Unit,
    onChooseMoreMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    var selectedMode by rememberSaveable { mutableStateOf(FavouritesTabMode.VIDEOS) }

    val favouriteVideos = remember(uiState.videos, uiState.favouriteIds) {
        if (uiState.favouriteIds.isEmpty()) {
            emptyList()
        } else {
            uiState.videos.filter { it.id in uiState.favouriteIds }
        }
    }
    val favouritePhotos = remember(uiState.photos, uiState.favouriteIds) {
        if (uiState.favouriteIds.isEmpty()) {
            emptyList()
        } else {
            uiState.photos.filter { it.id in uiState.favouriteIds }
        }
    }

    val isLoading = uiState.libraryLoadState == LibraryLoadState.Loading ||
        uiState.libraryLoadState == LibraryLoadState.Idle

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .testTag("screen_favourites"),
    ) {
        // Header: "Favourites" title + count beside it
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = spacing.md,
                    end = spacing.md,
                    top = spacing.md,
                    bottom = spacing.xs,
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_favourites),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isLoading) {
                Text(
                    text = pluralStringResource(
                        R.plurals.count_favourites,
                        uiState.favourites.size,
                        uiState.favourites.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 3.dp)
                        .testTag("favourites_count"),
                )
            }
        }

        // Small "Videos" / "Photos" switch styled like the Home screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FavouritesModeOption(
                label = stringResource(R.string.nav_videos),
                selected = selectedMode == FavouritesTabMode.VIDEOS,
                onClick = { selectedMode = FavouritesTabMode.VIDEOS },
                testTag = "favourites_mode_videos",
            )
            FavouritesModeOption(
                label = stringResource(R.string.nav_photos),
                selected = selectedMode == FavouritesTabMode.PHOTOS,
                onClick = { selectedMode = FavouritesTabMode.PHOTOS },
                testTag = "favourites_mode_photos",
            )
        }

        if (uiState.isPartialAccess) {
            PartialAccessBanner(
                onChooseMore = onChooseMoreMedia,
                modifier = Modifier.padding(
                    start = spacing.md,
                    end = spacing.md,
                    bottom = spacing.xs,
                ),
            )
        }

        if (isLoading) {
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
        } else {
            AnimatedContent(
                targetState = selectedMode,
                transitionSpec = {
                    fadeIn(ReelboxMotion.standardFade()) togetherWith fadeOut(ReelboxMotion.quickFade())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "favourites_mode_crossfade",
            ) { mode ->
                val itemsForMode: List<MediaItem> = when (mode) {
                    FavouritesTabMode.VIDEOS -> favouriteVideos
                    FavouritesTabMode.PHOTOS -> favouritePhotos
                }
                val mediaType = when (mode) {
                    FavouritesTabMode.VIDEOS -> MediaType.VIDEO
                    FavouritesTabMode.PHOTOS -> MediaType.PHOTO
                }

                if (itemsForMode.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.FavoriteBorder,
                        title = stringResource(R.string.empty_favourites_title),
                        subtitle = stringResource(R.string.empty_favourites_subtitle),
                        testTag = "empty_state_favourites_${mode.name.lowercase()}",
                    )
                } else {
                    FavouritesGrid(
                        items = itemsForMode,
                        onTapItem = { index ->
                            onOpenItem(
                                mediaType,
                                index,
                                itemsForMode.map { it.id },
                            )
                        },
                        testTag = "favourites_grid_${mode.name.lowercase()}",
                    )
                }
            }
        }
    }
}

@Composable
private fun FavouritesModeOption(
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
        label = "favourites_mode_text_color",
    )

    val underlineScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = ReelboxMotion.standardFade(),
        label = "favourites_mode_underline_scale",
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

@Composable
private fun FavouritesGrid(
    items: List<MediaItem>,
    onTapItem: (Int) -> Unit,
    testTag: String,
) {
    val gridState = rememberLazyGridState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                top = 4.dp,
                bottom = 24.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag(testTag),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.id },
                contentType = { _, item -> item.type },
            ) { index, item ->
                MediaGridTile(
                    item = item,
                    isFavourite = true,
                    tileSizePx = tileSizePx,
                    onClick = { onTapItem(index) },
                )
            }
        }
    }
}
