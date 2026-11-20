package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.FavouritesRepository
import com.example.data.MediaAlbum
import com.example.data.MediaItem
import com.example.data.MediaLibrarySnapshot
import com.example.data.MediaPermissionState
import com.example.data.MediaRepository
import com.example.data.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ReelboxViewModel(
    private val mediaRepository: MediaRepository,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {

    private val permissionStateFlow = MutableStateFlow(MediaPermissionState.CHECKING)
    private val loadStateFlow = MutableStateFlow<LibraryLoadState>(LibraryLoadState.Idle)
    private val snapshotFlow = MutableStateFlow(MediaLibrarySnapshot())
    private val selectedPhotoBucketIdFlow = MutableStateFlow<String?>(null)
    private val selectedVideoBucketIdFlow = MutableStateFlow<String?>(null)

    private var activeLoadJob: Job? = null

    private val prefsFlow = combine(
        favouritesRepository.hasRequestedPermission,
        favouritesRepository.favouriteIds,
        favouritesRepository.isMuted,
    ) { hasRequested, favouriteIds, isMuted ->
        Triple(hasRequested, favouriteIds, isMuted)
    }

    private val selectedFoldersFlow = combine(
        selectedPhotoBucketIdFlow,
        selectedVideoBucketIdFlow,
    ) { photoBucket, videoBucket ->
        photoBucket to videoBucket
    }

    val uiState: StateFlow<ReelboxUiState> = combine(
        permissionStateFlow,
        loadStateFlow,
        snapshotFlow,
        prefsFlow,
        selectedFoldersFlow,
    ) { permissionState, loadState, snapshot, (hasRequested, favouriteIds, isMuted), (photoBucket, videoBucket) ->
        val favouriteItems = if (favouriteIds.isEmpty()) {
            emptyList()
        } else {
            snapshot.allMedia.filter { item -> item.id in favouriteIds }
        }
        val validPhotoBucket = photoBucket?.takeIf { bucket ->
            snapshot.photoAlbums.any { it.bucketId == bucket && it.photoCount > 0 }
        }
        val validVideoBucket = videoBucket?.takeIf { bucket ->
            snapshot.videoAlbums.any { it.bucketId == bucket && it.videoCount > 0 }
        }
        ReelboxUiState(
            permissionState = permissionState,
            hasRequestedPermission = hasRequested,
            libraryLoadState = loadState,
            allMedia = snapshot.allMedia,
            videos = snapshot.videos,
            photos = snapshot.photos,
            favourites = favouriteItems,
            favouriteIds = favouriteIds,
            isMuted = isMuted,
            albums = snapshot.albums,
            videoAlbums = snapshot.videoAlbums,
            photoAlbums = snapshot.photoAlbums,
            selectedPhotoBucketId = validPhotoBucket,
            selectedVideoBucketId = validVideoBucket,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = ReelboxUiState(),
    )

    init {
        viewModelScope.launch {
            permissionStateFlow
                .map { it.hasAnyAccess }
                .distinctUntilChanged()
                .flatMapLatest { hasAccess ->
                    if (hasAccess) {
                        mediaRepository.observeMediaChanges()
                    } else {
                        emptyFlow()
                    }
                }
                .collectLatest {
                    delay(MEDIA_CHANGE_DEBOUNCE_MS)
                    refreshLibrary(showLoadingIndicator = false)
                }
        }
    }

    fun onPermissionEvaluated(
        newState: MediaPermissionState,
        forceReload: Boolean = false,
    ) {
        val previousState = permissionStateFlow.value
        permissionStateFlow.value = newState

        when {
            newState.hasAnyAccess -> {
                val shouldLoad = forceReload ||
                    !previousState.hasAnyAccess ||
                    loadStateFlow.value == LibraryLoadState.Idle
                if (shouldLoad) {
                    refreshLibrary(showLoadingIndicator = snapshotFlow.value.allMedia.isEmpty())
                }
            }

            newState == MediaPermissionState.DENIED ||
                newState == MediaPermissionState.PERMANENTLY_DENIED -> {
                activeLoadJob?.cancel()
                snapshotFlow.value = MediaLibrarySnapshot()
                loadStateFlow.value = LibraryLoadState.Idle
            }

            else -> Unit
        }
    }

    fun markPermissionRequested() {
        viewModelScope.launch {
            favouritesRepository.markPermissionRequested()
        }
    }

    fun refreshLibrary(showLoadingIndicator: Boolean = false) {
        if (!permissionStateFlow.value.hasAnyAccess) return

        activeLoadJob?.cancel()
        activeLoadJob = viewModelScope.launch {
            if (showLoadingIndicator || loadStateFlow.value == LibraryLoadState.Idle) {
                loadStateFlow.value = LibraryLoadState.Loading
            }
            val result = runCatching { mediaRepository.loadLibrary() }
            result.onSuccess { snapshot ->
                snapshotFlow.value = snapshot
                loadStateFlow.value = LibraryLoadState.Ready
                if (permissionStateFlow.value == MediaPermissionState.FULL_ACCESS) {
                    val existingIds = snapshot.allMedia.mapTo(HashSet(snapshot.allMedia.size)) { it.id }
                    favouritesRepository.pruneMissingIds(existingIds)
                }
            }.onFailure {
                loadStateFlow.value = LibraryLoadState.Error
            }
        }
    }

    fun toggleFavourite(mediaId: Long) {
        viewModelScope.launch {
            favouritesRepository.toggleFavourite(mediaId)
        }
    }

    fun setFavourite(mediaId: Long, isFavourite: Boolean) {
        viewModelScope.launch {
            favouritesRepository.setFavourite(mediaId, isFavourite)
        }
    }

    fun toggleMute() {
        val nextMuted = !uiState.value.isMuted
        viewModelScope.launch {
            favouritesRepository.setMuted(nextMuted)
        }
    }

    fun selectPhotoFolder(bucketId: String?) {
        selectedPhotoBucketIdFlow.value = bucketId
    }

    fun selectVideoFolder(bucketId: String?) {
        selectedVideoBucketIdFlow.value = bucketId
    }

    fun onMediaDeleted(mediaId: Long) {
        val current = snapshotFlow.value
        val remainingAll = current.allMedia.filterNot { it.id == mediaId }
        val remainingVideos = current.videos.filterNot { it.id == mediaId }
        val remainingPhotos = current.photos.filterNot { it.id == mediaId }
        val updatedPhotoAlbums = rebuildAlbums(remainingPhotos)
        val updatedVideoAlbums = rebuildAlbums(remainingVideos)

        snapshotFlow.value = MediaLibrarySnapshot(
            allMedia = remainingAll,
            videos = remainingVideos,
            photos = remainingPhotos,
            albums = rebuildAlbums(remainingAll),
            videoAlbums = updatedVideoAlbums,
            photoAlbums = updatedPhotoAlbums,
        )

        // Reset chosen folder if its last item was just deleted.
        selectedPhotoBucketIdFlow.value?.let { selected ->
            if (updatedPhotoAlbums.none { it.bucketId == selected }) {
                selectedPhotoBucketIdFlow.value = null
            }
        }
        selectedVideoBucketIdFlow.value?.let { selected ->
            if (updatedVideoAlbums.none { it.bucketId == selected }) {
                selectedVideoBucketIdFlow.value = null
            }
        }

        viewModelScope.launch {
            favouritesRepository.setFavourite(mediaId, isFavourite = false)
        }
        refreshLibrary(showLoadingIndicator = false)
    }

    private fun rebuildAlbums(items: List<MediaItem>): List<MediaAlbum> {
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

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReelboxViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ReelboxViewModel(
                mediaRepository = MediaRepository(application),
                favouritesRepository = FavouritesRepository(application),
            ) as T
        }
    }

    private companion object {
        const val MEDIA_CHANGE_DEBOUNCE_MS = 450L
    }
}
