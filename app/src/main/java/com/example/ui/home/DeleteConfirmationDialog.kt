package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens

/**
 * Calm centred confirmation card shown before deleting any video or photo.
 * Adapts to portrait (stacked vertical card) and landscape (thumbnail left, text right, buttons side by side).
 */
@Composable
fun DeleteConfirmationDialog(
    item: MediaItem,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = ReelboxThemeTokens.colors
    val view = LocalView.current

    var cardVisible by remember(item.id) { mutableStateOf(false) }
    var isDeleteSubmitted by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        cardVisible = true
    }

    val titleText = stringResource(
        if (item.type == MediaType.VIDEO) {
            R.string.feed_delete_video_title
        } else {
            R.string.feed_delete_photo_title
        },
    )
    val fileName = remember(item.id, item.displayName, item.type) {
        MediaFormatters.displayTitle(context, item)
    }
    val bodyText = stringResource(R.string.feed_delete_body)
    val cancelLabel = stringResource(R.string.feed_delete_cancel)
    val deleteLabel = stringResource(R.string.feed_delete_confirm)

    val thumbSizePx = remember(density) { with(density) { 72.dp.roundToPx() } }
    val thumbRequest = remember(item.id, item.contentUri, thumbSizePx) {
        ImageRequest.Builder(context)
            .data(item.contentUri)
            .size(thumbSizePx)
            .allowHardware(true)
            .crossfade(140)
            .build()
    }

    val handleConfirmClick: () -> Unit = {
        if (!isDeleteSubmitted) {
            isDeleteSubmitted = true
            onConfirmDelete()
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isDeleteSubmitted) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.setDimAmount(0f)
                window.setBackgroundDrawableResource(android.R.color.transparent)
                if (isLandscape) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag("delete_confirmation_overlay"),
            contentAlignment = Alignment.Center,
        ) {
            val useLandscapeCard = isLandscape || maxWidth > maxHeight

            // Dimmed black backdrop (60%) — tapping outside closes the card
            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)),
                exit = fadeOut(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.60f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!isDeleteSubmitted) {
                                    onDismiss()
                                }
                            },
                        )
                        .testTag("delete_confirmation_scrim"),
                )
            }

            // Confirmation card: raised surface #1E1E22, 20dp corners, 24dp padding, 200ms fade + slight scale-up
            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(durationMillis = 200, easing = ReelboxMotion.EaseOut),
                    ),
                exit = fadeOut(tween(durationMillis = 200, easing = ReelboxMotion.EaseOut)) +
                    scaleOut(
                        targetScale = 0.94f,
                        animationSpec = tween(durationMillis = 200, easing = ReelboxMotion.EaseOut),
                    ),
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surfaceRaised,
                    border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = if (useLandscapeCard) 460.dp else 340.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .testTag("delete_confirmation_card"),
                ) {
                    val scrollState = rememberScrollState()

                    if (useLandscapeCard) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(24.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DeletePreviewThumbnail(
                                    imageRequest = thumbRequest,
                                    contentDescription = fileName,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = titleText,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                        modifier = Modifier.testTag("delete_confirmation_title"),
                                    )
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("delete_confirmation_filename"),
                                    )
                                    Text(
                                        text = bodyText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        modifier = Modifier.testTag("delete_confirmation_body"),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DeleteCancelButton(
                                    label = cancelLabel,
                                    enabled = !isDeleteSubmitted,
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                )
                                DeleteConfirmButton(
                                    label = deleteLabel,
                                    enabled = !isDeleteSubmitted,
                                    onClick = handleConfirmClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            DeletePreviewThumbnail(
                                imageRequest = thumbRequest,
                                contentDescription = fileName,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("delete_confirmation_title"),
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("delete_confirmation_filename"),
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("delete_confirmation_body"),
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            DeleteCancelButton(
                                label = cancelLabel,
                                enabled = !isDeleteSubmitted,
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            DeleteConfirmButton(
                                label = deleteLabel,
                                enabled = !isDeleteSubmitted,
                                onClick = handleConfirmClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletePreviewThumbnail(
    imageRequest: ImageRequest,
    contentDescription: String,
) {
    val colors = ReelboxThemeTokens.colors
    val thumbShape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(thumbShape)
            .background(colors.surface)
            .testTag("delete_confirmation_thumbnail"),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DeleteCancelButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("delete_confirmation_cancel_button"),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DeleteConfirmButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.destructive,
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.destructive.copy(alpha = 0.55f),
            disabledContentColor = colors.textPrimary.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("delete_confirmation_confirm_button"),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
