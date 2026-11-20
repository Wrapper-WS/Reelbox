package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R

const val ROUTE_HOME = "home"
const val ROUTE_FAVOURITES = "favourites"
const val ROUTE_PHOTOS = "photos"
const val ROUTE_VIDEOS = "videos"

enum class ReelboxTab(
    val route: String,
    @StringRes val labelRes: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val testTag: String,
) {
    HOME(
        route = ROUTE_HOME,
        labelRes = R.string.nav_home,
        outlinedIcon = Icons.Outlined.PlayCircle,
        filledIcon = Icons.Filled.PlayCircle,
        testTag = "nav_tab_home",
    ),
    FAVOURITES(
        route = ROUTE_FAVOURITES,
        labelRes = R.string.nav_favourites,
        outlinedIcon = Icons.Outlined.FavoriteBorder,
        filledIcon = Icons.Filled.Favorite,
        testTag = "nav_tab_favourites",
    ),
    PHOTOS(
        route = ROUTE_PHOTOS,
        labelRes = R.string.nav_photos,
        outlinedIcon = Icons.Outlined.Image,
        filledIcon = Icons.Filled.Image,
        testTag = "nav_tab_photos",
    ),
    VIDEOS(
        route = ROUTE_VIDEOS,
        labelRes = R.string.nav_videos,
        outlinedIcon = Icons.Outlined.Videocam,
        filledIcon = Icons.Filled.Videocam,
        testTag = "nav_tab_videos",
    ),
}
