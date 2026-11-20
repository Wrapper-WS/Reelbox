package com.example.ui

import androidx.compose.runtime.Immutable
import com.example.data.MediaAlbum
import com.example.data.MediaItem
import com.example.data.MediaPermissionState

sealed interface LibraryLoadState {
    data object Idle : LibraryLoadState
    data object Loading : LibraryLoadState
    data object Ready : LibraryLoadState
    data object Error : LibraryLoadState
}

@Immutable
data class ReelboxUiState(
    val permissionState: MediaPermissionState = MediaPermissionState.CHECKING,
    val hasRequestedPermission: Boolean = false,
    val libraryLoadState: LibraryLoadState = LibraryLoadState.Idle,
    val allMedia: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val photos: List<MediaItem> = emptyList(),
    val favourites: List<MediaItem> = emptyList(),
    val favouriteIds: Set<Long> = emptySet(),
    val isMuted: Boolean = false,
    val albums: List<MediaAlbum> = emptyList(),
    val videoAlbums: List<MediaAlbum> = emptyList(),
    val photoAlbums: List<MediaAlbum> = emptyList(),
    val selectedPhotoBucketId: String? = null,
    val selectedVideoBucketId: String? = null,
) {
    val hasMediaAccess: Boolean
        get() = permissionState.hasAnyAccess

    val isPartialAccess: Boolean
        get() = permissionState == MediaPermissionState.PARTIAL_ACCESS
}
