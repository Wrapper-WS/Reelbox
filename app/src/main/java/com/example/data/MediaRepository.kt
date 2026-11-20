package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Scans device-local videos, photos, and albums from MediaStore.
 * Always sorts newest media first and handles denied permissions or empty libraries safely.
 */
class MediaRepository(context: Context) {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    suspend fun loadLibrary(): MediaLibrarySnapshot = withContext(Dispatchers.IO) {
        val videos = queryVideos()
        val photos = queryPhotos()
        val allMedia = (videos + photos).sortedWith(
            compareByDescending<MediaItem> { it.dateAddedSeconds }
                .thenByDescending { it.id },
        )

        MediaLibrarySnapshot(
            allMedia = allMedia,
            videos = videos,
            photos = photos,
            albums = buildAlbums(allMedia),
            videoAlbums = buildAlbums(videos),
            photoAlbums = buildAlbums(photos),
        )
    }

    /**
     * Emits whenever MediaStore notifies of newly saved, updated, or deleted media.
     */
    fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        val registered = runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        }.isSuccess

        awaitClose {
            if (registered) {
                runCatching { contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    private fun queryVideos(): List<MediaItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.MediaColumns.ORIENTATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            )
        } else {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            )
        }

        val selection = buildVisibleSelection(isVideo = true)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC, ${MediaStore.Video.Media._ID} DESC"

        return runQuerySafely(collection, projection, selection, sortOrder) { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            if (idIndex < 0) return@runQuerySafely emptyList()
            val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            val dateAddedIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
            val dateTakenIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
            val durationIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val widthIndex = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val orientationIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
            } else {
                -1
            }
            val sizeIndex = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val bucketIdIndex = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameIndex = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            buildList {
                while (cursor.moveToNext()) {
                    runCatching {
                        val id = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                        val rawBucketName = if (bucketNameIndex >= 0) {
                            cursor.getString(bucketNameIndex)?.trim()
                        } else {
                            null
                        }
                        val orientation = if (orientationIndex >= 0) cursor.getInt(orientationIndex) else 0
                        MediaItem(
                            id = id,
                            contentUri = contentUri,
                            displayName = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else "",
                            type = MediaType.VIDEO,
                            mimeType = (if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else "")
                                .ifEmpty { "video/*" },
                            dateAddedSeconds = if (dateAddedIndex >= 0) {
                                cursor.getLong(dateAddedIndex).coerceAtLeast(0L)
                            } else {
                                0L
                            },
                            dateTakenMillis = if (dateTakenIndex >= 0) {
                                cursor.getLong(dateTakenIndex).coerceAtLeast(0L)
                            } else {
                                0L
                            },
                            durationMillis = if (durationIndex >= 0) {
                                cursor.getLong(durationIndex).coerceAtLeast(0L)
                            } else {
                                0L
                            },
                            width = if (widthIndex >= 0) cursor.getInt(widthIndex).coerceAtLeast(0) else 0,
                            height = if (heightIndex >= 0) cursor.getInt(heightIndex).coerceAtLeast(0) else 0,
                            orientationDegrees = orientation,
                            sizeBytes = if (sizeIndex >= 0) {
                                cursor.getLong(sizeIndex).coerceAtLeast(0L)
                            } else {
                                0L
                            },
                            bucketId = (if (bucketIdIndex >= 0) cursor.getString(bucketIdIndex).orEmpty() else "")
                                .ifEmpty { DEFAULT_BUCKET_ID },
                            bucketName = if (rawBucketName.isNullOrEmpty()) DEFAULT_BUCKET_NAME else rawBucketName,
                        )
                    }.getOrNull()?.let { add(it) }
                }
            }
        }
    }

    private fun queryPhotos(): List<MediaItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )

        val selection = buildVisibleSelection(isVideo = false)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

        return runQuerySafely(collection, projection, selection, sortOrder) { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            if (idIndex < 0) return@runQuerySafely emptyList()
            val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val widthIndex = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
            val bucketIdIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            buildList {
                while (cursor.moveToNext()) {
                    runCatching {
                        val id = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                        val rawBucketName = if (bucketNameIndex >= 0) {
                            cursor.getString(bucketNameIndex)?.trim()
                        } else {
                            null
                        }
                        MediaItem(
                            id = id,
                            contentUri = contentUri,
                            displayName = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else "",
                            type = MediaType.PHOTO,
                            mimeType = (if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else "")
                                .ifEmpty { "image/*" },
                            dateAddedSeconds = if (dateAddedIndex >= 0) {
                                cursor.getLong(dateAddedIndex).coerceAtLeast(0L)
                            } else {
                                0L
                            },
                            dateTakenMillis = if (dateTakenIndex >= 0) {
                                cursor.getLong(dateTakenIndex).coerceAtLeast(0L)
                            } else {
                                0L
                            },
                            durationMillis = 0L,
                            width = if (widthIndex >= 0) cursor.getInt(widthIndex).coerceAtLeast(0) else 0,
                            height = if (heightIndex >= 0) cursor.getInt(heightIndex).coerceAtLeast(0) else 0,
                            sizeBytes = if (sizeIndex >= 0) {
                                cursor.getLong(sizeIndex).coerceAtLeast(0L)
                            } else {
                                0L
                            },
                            bucketId = (if (bucketIdIndex >= 0) cursor.getString(bucketIdIndex).orEmpty() else "")
                                .ifEmpty { DEFAULT_BUCKET_ID },
                            bucketName = if (rawBucketName.isNullOrEmpty()) DEFAULT_BUCKET_NAME else rawBucketName,
                        )
                    }.getOrNull()?.let { add(it) }
                }
            }
        }
    }

    private fun buildVisibleSelection(isVideo: Boolean): String {
        val clauses = mutableListOf<String>()
        val sizeColumn = if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Images.Media.SIZE
        clauses.add("$sizeColumn > 0")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clauses.add("${MediaStore.MediaColumns.IS_PENDING} = 0")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            clauses.add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
        }
        return clauses.joinToString(separator = " AND ")
    }

    private fun runQuerySafely(
        uri: Uri,
        projection: Array<String>,
        selection: String,
        sortOrder: String,
        mapper: (android.database.Cursor) -> List<MediaItem>,
    ): List<MediaItem> {
        return try {
            contentResolver.query(uri, projection, selection, null, sortOrder)?.use(mapper)
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildAlbums(items: List<MediaItem>): List<MediaAlbum> {
        if (items.isEmpty()) return emptyList()

        return items
            .groupBy { it.bucketId }
            .map { (bucketId, bucketItems) ->
                val newest = bucketItems.first()
                var videoCount = 0
                var photoCount = 0
                for (item in bucketItems) {
                    when (item.type) {
                        MediaType.VIDEO -> videoCount++
                        MediaType.PHOTO -> photoCount++
                    }
                }
                MediaAlbum(
                    bucketId = bucketId,
                    name = newest.bucketName,
                    coverUri = newest.contentUri,
                    itemCount = bucketItems.size,
                    videoCount = videoCount,
                    photoCount = photoCount,
                    latestDateAddedSeconds = newest.dateAddedSeconds,
                )
            }
            .sortedByDescending { it.latestDateAddedSeconds }
    }

    private companion object {
        const val DEFAULT_BUCKET_ID = "other"
        const val DEFAULT_BUCKET_NAME = "Other"
    }
}
