package com.example.data

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class MediaType {
    VIDEO,
    PHOTO,
}

@Immutable
data class MediaItem(
    val id: Long,
    val contentUri: Uri,
    val displayName: String,
    val type: MediaType,
    val mimeType: String,
    val dateAddedSeconds: Long,
    val dateTakenMillis: Long,
    val durationMillis: Long,
    val width: Int,
    val height: Int,
    val orientationDegrees: Int = 0,
    val sizeBytes: Long,
    val bucketId: String,
    val bucketName: String,
) {
    val effectiveAspectRatio: Float
        get() {
            if (width <= 0 || height <= 0) return 0f
            val normalized = ((orientationDegrees % 360) + 360) % 360
            return if (normalized == 90 || normalized == 270) {
                height.toFloat() / width.toFloat()
            } else {
                width.toFloat() / height.toFloat()
            }
        }
}

@Immutable
data class MediaAlbum(
    val bucketId: String,
    val name: String,
    val coverUri: Uri,
    val itemCount: Int,
    val videoCount: Int,
    val photoCount: Int,
    val latestDateAddedSeconds: Long,
)

@Immutable
data class MediaLibrarySnapshot(
    val allMedia: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val photos: List<MediaItem> = emptyList(),
    val albums: List<MediaAlbum> = emptyList(),
    val videoAlbums: List<MediaAlbum> = emptyList(),
    val photoAlbums: List<MediaAlbum> = emptyList(),
)
