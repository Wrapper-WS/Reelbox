package com.example.ui.home

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.MediaItem

/**
 * Maintains at most two prepared Media3 ExoPlayer instances at rest:
 * the currently visible video and the next video in the vertical feed.
 * Tracks decoded video aspect ratios and playback speed cleanly.
 */
@Stable
class FeedPlayerPool(context: Context) {

    private val appContext: Context = context.applicationContext
    private val players = mutableStateMapOf<Long, ExoPlayer>()
    private val listeners = mutableMapOf<Long, Player.Listener>()
    private val erroredVideoIds = mutableStateMapOf<Long, Boolean>()
    private val videoAspectRatios = mutableStateMapOf<Long, Float>()

    private var currentMuted: Boolean = false
    private var released: Boolean = false

    var isAnyVideoPlaying: Boolean by mutableStateOf(false)
        private set

    fun playerFor(mediaId: Long): ExoPlayer? = players[mediaId]

    fun hasError(mediaId: Long): Boolean = erroredVideoIds[mediaId] == true

    /**
     * Returns the decoded display aspect ratio if available from ExoPlayer,
     * falling back to MediaStore's rotation-adjusted dimensions.
     */
    fun aspectRatioFor(item: MediaItem): Float {
        return videoAspectRatios[item.id] ?: item.effectiveAspectRatio
    }

    fun syncWindow(
        activeItem: MediaItem?,
        preloadItem: MediaItem?,
        shouldPlayActive: Boolean,
        isMuted: Boolean,
    ) {
        if (released) return
        currentMuted = isMuted

        val keepIds = buildSet {
            if (activeItem != null) add(activeItem.id)
            if (preloadItem != null) add(preloadItem.id)
        }

        // Release any player outside the active + next window.
        val staleIds = players.keys.filter { it !in keepIds }
        for (staleId in staleIds) {
            releasePlayer(staleId)
        }

        // Ensure active and next players exist and are prepared.
        if (activeItem != null) {
            ensurePlayer(activeItem)
        }
        if (preloadItem != null && preloadItem.id != activeItem?.id) {
            ensurePlayer(preloadItem)
        }

        // Only the active visible video plays; everything else pauses immediately.
        for ((id, player) in players) {
            player.volume = if (currentMuted) 0f else 1f
            val isActive = activeItem != null && id == activeItem.id
            val isErrored = erroredVideoIds[id] == true
            val targetPlay = isActive && shouldPlayActive && !isErrored

            // Recover gracefully if Android reclaimed codecs/surfaces while backgrounded.
            if (isActive && targetPlay && player.playbackState == Player.STATE_IDLE) {
                runCatching { player.prepare() }
                    .onFailure { erroredVideoIds[id] = true }
            }

            if (player.playWhenReady != targetPlay) {
                player.playWhenReady = targetPlay
            }
            if (!isActive) {
                if (player.playbackParameters.speed != 1f) {
                    player.setPlaybackSpeed(1f)
                }
                if (player.currentPosition > 0L) {
                    player.seekTo(0L)
                }
            }
        }
        updatePlayingState()
    }

    fun recoverOnForeground(activeMediaId: Long?, shouldPlayActive: Boolean) {
        if (released || activeMediaId == null) return
        val player = players[activeMediaId] ?: return
        if (erroredVideoIds[activeMediaId] == true) return

        runCatching {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            if (shouldPlayActive && !player.playWhenReady) {
                player.playWhenReady = true
            }
        }.onFailure {
            erroredVideoIds[activeMediaId] = true
        }
        updatePlayingState()
    }

    fun setPlaybackSpeed(mediaId: Long, speed: Float) {
        val player = players[mediaId] ?: return
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        if (player.playbackParameters.speed != safeSpeed) {
            runCatching { player.setPlaybackSpeed(safeSpeed) }
        }
    }

    fun setMuted(muted: Boolean) {
        currentMuted = muted
        val volume = if (muted) 0f else 1f
        players.values.forEach { it.volume = volume }
    }

    fun pauseAll() {
        players.values.forEach { player ->
            runCatching {
                if (player.playbackParameters.speed != 1f) {
                    player.setPlaybackSpeed(1f)
                }
                if (player.playWhenReady) {
                    player.playWhenReady = false
                }
            }
        }
        isAnyVideoPlaying = false
    }

    fun releaseAll() {
        released = true
        isAnyVideoPlaying = false
        val allIds = players.keys.toList()
        for (id in allIds) {
            releasePlayer(id)
        }
        erroredVideoIds.clear()
        videoAspectRatios.clear()
    }

    private fun ensurePlayer(item: MediaItem): ExoPlayer? {
        players[item.id]?.let { return it }

        val player = runCatching {
            ExoPlayer.Builder(appContext).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                volume = if (currentMuted) 0f else 1f
            }
        }.getOrElse {
            erroredVideoIds[item.id] = true
            return null
        }

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                erroredVideoIds[item.id] = true
                player.playWhenReady = false
                updatePlayingState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    erroredVideoIds.remove(item.id)
                }
                updatePlayingState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayingState()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) /
                        videoSize.height.toFloat()
                    if (ratio > 0f && ratio.isFinite()) {
                        videoAspectRatios[item.id] = ratio
                    }
                }
            }
        }

        listeners[item.id] = listener
        player.addListener(listener)
        players[item.id] = player
        runCatching {
            player.setMediaItem(Media3MediaItem.fromUri(item.contentUri))
            player.prepare()
        }.onFailure {
            erroredVideoIds[item.id] = true
            player.playWhenReady = false
            updatePlayingState()
        }
        return player
    }

    private fun releasePlayer(mediaId: Long) {
        val player = players.remove(mediaId) ?: return
        val listener = listeners.remove(mediaId)
        if (listener != null) {
            player.removeListener(listener)
        }
        erroredVideoIds.remove(mediaId)
        videoAspectRatios.remove(mediaId)
        runCatching {
            player.playWhenReady = false
            player.release()
        }
        updatePlayingState()
    }

    private fun updatePlayingState() {
        if (released) {
            isAnyVideoPlaying = false
            return
        }
        isAnyVideoPlaying = players.values.any { player ->
            runCatching { player.isPlaying }.getOrDefault(false)
        }
    }
}
