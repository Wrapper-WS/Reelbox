package com.example.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MediaItem
import com.example.ui.theme.ReelboxThemeTokens

/**
 * Custom Reelbox "Share to" bottom sheet:
 * 16dp top corners, subtle drag handle, circular installed app icons (up to 8, most common first),
 * and a final "More" button that opens the standard Android share menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
    onSelectApp: (ShareAppTarget) -> Unit,
    onSelectMore: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var targets by remember(item.id) { mutableStateOf<List<ShareAppTarget>>(emptyList()) }

    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    LaunchedEffect(item.id, item.mimeType) {
        val iconSizePx = with(density) { 56.dp.roundToPx() }
        targets = ShareTargetResolver.loadShareTargets(
            context = context,
            item = item,
            iconSizePx = iconSizePx,
        )
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
        modifier = Modifier.testTag("share_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.share_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.xs)
                    .testTag("share_sheet_title"),
            )

            Spacer(modifier = Modifier.height(spacing.sm))

            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_sheet_app_row"),
            ) {
                items(
                    items = targets,
                    key = { "${it.packageName}/${it.activityName}" },
                ) { target ->
                    ShareAppItem(
                        target = target,
                        onClick = { onSelectApp(target) },
                    )
                }

                item(key = "share_more") {
                    ShareMoreItem(onClick = onSelectMore)
                }
            }
        }
    }
}

@Composable
private fun ShareAppItem(
    target: ShareAppTarget,
    onClick: () -> Unit,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colors.textPrimary.copy(alpha = 0.16f)),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = spacing.xxs)
            .testTag("share_app_${target.packageName}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.surfaceRaised)
                .border(0.75.dp, colors.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (target.icon != null) {
                Image(
                    bitmap = target.icon,
                    contentDescription = target.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = target.label,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = target.label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShareMoreItem(
    onClick: () -> Unit,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val interactionSource = remember { MutableInteractionSource() }
    val moreLabel = stringResource(R.string.share_sheet_more)

    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = colors.textPrimary.copy(alpha = 0.16f)),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = spacing.xxs)
            .testTag("share_sheet_more_button"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.surfaceRaised)
                .border(1.dp, colors.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = moreLabel,
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = moreLabel,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
