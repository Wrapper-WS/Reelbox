package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ReelboxThemeTokens

/**
 * Quiet, unobtrusive banner shown when Android 14 selected-items access is active,
 * allowing the user to pick more photos and videos whenever they like.
 */
@Composable
fun PartialAccessBanner(
    onChooseMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing
    val chooseMoreDescription = stringResource(R.string.action_choose_more_description)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("partial_access_banner"),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.md, end = spacing.xs, top = spacing.xxs, bottom = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.partial_access_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = spacing.xs),
            )
            TextButton(
                onClick = onChooseMore,
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = colors.surfaceRaised,
                    contentColor = colors.textPrimary,
                ),
                contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = chooseMoreDescription }
                    .testTag("partial_access_choose_more_button"),
            ) {
                Text(
                    text = stringResource(R.string.partial_access_action),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
