package com.example.ui.gallery

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.MediaAlbum
import com.example.data.MediaType
import com.example.ui.theme.ReelboxThemeTokens

/**
 * Custom "Folders" bottom sheet for filtering the Photos or Videos grid.
 * Lists "All photos"/"All videos" first, followed only by non-empty folders of that media type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerBottomSheet(
    mediaType: MediaType,
    totalCount: Int,
    allCoverUri: Uri?,
    folders: List<MediaAlbum>,
    selectedBucketId: String?,
    onSelectFolder: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val nonEmptyFolders = remember(folders, mediaType) {
        folders.filter { album ->
            val count = if (mediaType == MediaType.VIDEO) album.videoCount else album.photoCount
            count > 0
        }
    }

    val allLabel = stringResource(
        if (mediaType == MediaType.VIDEO) R.string.folders_all_videos else R.string.folders_all_photos,
    )
    val countPluralRes = if (mediaType == MediaType.VIDEO) {
        R.plurals.count_videos
    } else {
        R.plurals.count_photos
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm, bottom = spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.divider),
                )
            }
        },
        modifier = Modifier.testTag("folders_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = spacing.md),
        ) {
            Text(
                text = stringResource(R.string.folders_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.xs)
                    .testTag("folders_sheet_title"),
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .testTag("folders_sheet_list"),
                contentPadding = PaddingValues(vertical = spacing.xs),
            ) {
                item(key = "all_items_folder") {
                    FolderRow(
                        name = allLabel,
                        countLabel = pluralStringResource(countPluralRes, totalCount, totalCount),
                        coverUri = allCoverUri,
                        isSelected = selectedBucketId == null,
                        onClick = { onSelectFolder(null) },
                        testTag = "folder_row_all",
                    )
                }

                items(
                    items = nonEmptyFolders,
                    key = { it.bucketId },
                ) { album ->
                    val typeCount = if (mediaType == MediaType.VIDEO) album.videoCount else album.photoCount
                    FolderRow(
                        name = album.name,
                        countLabel = pluralStringResource(countPluralRes, typeCount, typeCount),
                        coverUri = album.coverUri,
                        isSelected = selectedBucketId == album.bucketId,
                        onClick = { onSelectFolder(album.bucketId) },
                        testTag = "folder_row_${album.bucketId}",
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    countLabel: String,
    coverUri: Uri?,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val interactionSource = remember { MutableInteractionSource() }
    val thumbSizePx = remember(density) { with(density) { 48.dp.roundToPx() } }

    val imageRequest = remember(coverUri, thumbSizePx) {
        coverUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(thumbSizePx)
                .crossfade(120)
                .build()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colors.textPrimary.copy(alpha = 0.12f)),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { selected = isSelected }
            .padding(horizontal = spacing.lg, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceRaised)
                .border(0.5.dp, colors.divider, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.label_selected),
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .testTag("${testTag}_selected_check"),
            )
        }
    }
}
