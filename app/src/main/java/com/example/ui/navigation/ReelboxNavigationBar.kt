package com.example.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens

/**
 * Slim, dark bottom navigation bar with no heavy borders.
 * Active tab uses the Reelbox accent colour (#FF5A36) with a subtle 4dp dot indicator.
 */
@Composable
fun ReelboxBottomBar(
    currentRoute: String?,
    onSelectTab: (ReelboxTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.divider.copy(alpha = 0.7f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = spacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReelboxTab.entries.forEach { tab ->
                val isSelected = currentRoute == tab.route
                ReelboxNavItem(
                    tab = tab,
                    selected = isSelected,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Adaptive side navigation rail for tablets and wide landscape layouts.
 */
@Composable
fun ReelboxNavigationRail(
    currentRoute: String?,
    onSelectTab: (ReelboxTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val spacing = ReelboxThemeTokens.spacing

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .padding(vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReelboxTab.entries.forEach { tab ->
                val isSelected = currentRoute == tab.route
                ReelboxNavItem(
                    tab = tab,
                    selected = isSelected,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        VerticalDivider(
            thickness = 0.5.dp,
            color = colors.divider.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ReelboxNavItem(
    tab: ReelboxTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelboxThemeTokens.colors
    val label = stringResource(tab.labelRes)
    val interactionSource = remember { MutableInteractionSource() }

    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = ReelboxMotion.quickFade(),
        label = "nav_item_color",
    )

    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = ReelboxMotion.standardFade(),
        label = "nav_dot_scale",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected && tab == ReelboxTab.FAVOURITES) 1.08f else 1f,
        animationSpec = if (tab == ReelboxTab.FAVOURITES) {
            ReelboxMotion.heartBounce()
        } else {
            ReelboxMotion.quickFade()
        },
        label = "nav_icon_scale",
    )

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = colors.textPrimary.copy(alpha = 0.14f),
                ),
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .testTag(tab.testTag)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .scale(dotScale)
                .alpha(dotScale)
                .clip(CircleShape)
                .background(colors.accent),
        )
    }
}
