package com.wave.app.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.ShuffleOrder
import com.wave.app.model.PlayingTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val track: PlayingTrack? = null,
    val nextCoverUrl: String? = null,
    val prevCoverUrl: String? = null
)

class PreviewPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var progressJob: Job? = null
    private var refreshJob: Job? = null

    private var onTrackEndListener: (() -> Unit)? = null
    private var onTrackChangedListener: ((PlayingTrack?) -> Unit)? = null

    fun setOnTrackChangedListener(listener: (PlayingTrack?) -> Unit) {
        onTrackChangedListener = listener
    }

    // трекId -> url (для избранного/очереди без url)
    private var urlResolver: (suspend (trackId: String) -> String?)? = null
    fun setUrlResolver(resolver: suspend (trackId: String) -> String?) {
        urlResolver = resolver
    }

    // локальная копия плейлиста
    private val playlist: MutableList<PlayingTrack> = mutableListOf()

    var repeatMode: RepeatMode = RepeatMode.OFF
        set(value) {
            field = value
            val p = ExoPlayerHolder.player
            p.repeatMode = when (value) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
            pushState()
        }

    var isShuffleOn: Boolean = false
        set(value) {
            field = value
            val p = ExoPlayerHolder.player

            if (p.mediaItemCount <= 1) {
                p.shuffleModeEnabled = value
                pushState()
                return
            }

            val currentIndex = p.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
            val currentPos = p.currentPosition

            if (value) {
                p.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(p.mediaItemCount))
                p.shuffleModeEnabled = true
            } else {
                p.shuffleModeEnabled = false
            }

            p.seekTo(
                currentIndex.coerceIn(0, (p.mediaItemCount - 1).coerceAtLeast(0)),
                currentPos
            )
            p.playWhenReady = true

            pushState()
        }

    init {
        ExoPlayerHolder.initializePlayer(context)
        val player = ExoPlayerHolder.player

        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                ensureProgressLoop(isPlaying)
            }

            // fallback: если трек сменился на item без uri — резолвим прямо тут (как у тебя)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val p = ExoPlayerHolder.player
                val uri = mediaItem?.localConfiguration?.uri?.toString().orEmpty()

                if (uri.isBlank()) {
                    val ex = mediaItem?.mediaMetadata?.extras
                    val trackId = ex?.getString("trackId") ?: mediaItem?.mediaId
                    val resolver = urlResolver

                    if (!trackId.isNullOrBlank() && resolver != null) {
                        scope.launch(Dispatchers.Main) {
                            val newUrl = withContext(Dispatchers.IO) { resolver(trackId) }
                            val currentIdx = p.currentMediaItemIndex

                            if (!newUrl.isNullOrBlank() && currentIdx != C.INDEX_UNSET) {
                                if (currentIdx in playlist.indices) {
                                    playlist[currentIdx] = playlist[currentIdx].copy(url = newUrl)
                                }

                                val baseTrack = playlist.getOrNull(currentIdx)
                                if (baseTrack != null) {
                                    val mediaIdSafe = mediaItem?.mediaId ?: return@launch
                                    val newItem = buildMediaItem(baseTrack.copy(url = newUrl), mediaIdSafe)

                                    p.replaceMediaItem(currentIdx, newItem)
                                    p.seekTo(currentIdx, 0L)
                                    p.prepare()
                                    p.playWhenReady = true
                                    p.play()
                                }
                            }
                            pushState()
                        }
                        return
                    }
                }

                pushState()
            }

            // fallback: если упали на текущем item без uri — тоже резолвим
            override fun onPlayerError(error: PlaybackException) {
                val p = ExoPlayerHolder.player
                val item = p.currentMediaItem ?: return
                val uri = item.localConfiguration?.uri?.toString().orEmpty()

                if (uri.isBlank()) {
                    val ex = item.mediaMetadata.extras
                    val trackId = ex?.getString("trackId") ?: item.mediaId
                    val resolver = urlResolver ?: return

                    scope.launch(Dispatchers.Main) {
                        val newUrl = withContext(Dispatchers.IO) { resolver(trackId) }
                        val idx = p.currentMediaItemIndex

                        if (!newUrl.isNullOrBlank() && idx != C.INDEX_UNSET) {
                            if (idx in playlist.indices) {
                                playlist[idx] = playlist[idx].copy(url = newUrl)
                            }

                            val base = playlist.getOrNull(idx)
                            if (base != null) {
                                val newItem = buildMediaItem(base.copy(url = newUrl), item.mediaId)
                                p.replaceMediaItem(idx, newItem)
                                p.seekTo(idx, 0L)
                                p.prepare()
                                p.playWhenReady = true
                                p.play()
                            }
                        }
                        pushState()
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ONE -> {
                            player.seekTo(player.currentMediaItemIndex, 0L)
                            player.prepare()
                            player.playWhenReady = true
                        }
                        Player.REPEAT_MODE_ALL -> {
                            if (!player.hasNextMediaItem() && player.mediaItemCount > 0) {
                                player.seekTo(0, 0L)
                                player.playWhenReady = true
                            }
                        }
                        else -> Unit
                    }
                    onTrackEndListener?.invoke()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                pushState()
            }
        })
    }

    // ────────────────────────────────────────────────
    // Auto refresh URLs (каждые 30 минут)
    // ────────────────────────────────────────────────

    private suspend fun refreshUrlsOnce() {
        val resolver = urlResolver ?: return
        if (playlist.isEmpty()) return

        for (i in playlist.indices) {
            val t = playlist[i]
            val id = t.trackId ?: continue

            val newUrl = withContext(Dispatchers.IO) { resolver(id) }
            if (!newUrl.isNullOrBlank()) {
                playlist[i] = t.copy(url = newUrl)
            }
        }
    }

    private fun startAutoRefreshUrls() {
        refreshJob?.cancel()

        if (urlResolver == null) return

        refreshJob = scope.launch {
            while (isActive) {
                delay(30 * 60 * 1000L) // 30 минут
                try {
                    refreshUrlsOnce()
                } catch (_: Throwable) {
                    // молча: это фоновый refresh, не должен валить плеер
                }
            }
        }
    }

    private fun stopAutoRefreshUrls() {
        refreshJob?.cancel()
        refreshJob = null
    }

    // ────────────────────────────────────────────────
    // Meta persistence
    // ────────────────────────────────────────────────

    private fun PlayingTrack.toExtrasBundle(): Bundle =
        Bundle().apply {
            putString("source", source)
            putString("externalId", externalId)
            putString("artistId", artistId)
            putString("albumId", albumId)
            putString("ownerId", ownerId)
            putString("accessKey", accessKey)
            putString("image", image)
            putString("trackId", trackId)
        }

    private fun MediaMetadata.toPlayingTrack(url: String?, fallbackSource: String = ""): PlayingTrack {
        val ex = this.extras
        return PlayingTrack(
            title = this.title?.toString().orEmpty(),
            artist = this.artist?.toString().orEmpty(),
            image = ex?.getString("image") ?: this.artworkUri?.toString().orEmpty(),
            url = url.orEmpty(),
            source = ex?.getString("source") ?: fallbackSource,
            externalId = ex?.getString("externalId"),
            artistId = ex?.getString("artistId"),
            albumId = ex?.getString("albumId"),
            ownerId = ex?.getString("ownerId"),
            trackId = ex?.getString("trackId"),
            accessKey = ex?.getString("accessKey")
        )
    }

    private fun buildMediaItem(track: PlayingTrack, mediaId: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(track.image.takeIf { it.isNotBlank() }?.toUri())
            .setExtras(track.toExtrasBundle())
            .build()

        val b = MediaItem.Builder()
            .setUri(track.url)
            .setMediaId(mediaId)
            .setMediaMetadata(meta)

        if (track.url.contains("type=stream")) {
            b.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        return b.build()
    }

    // ────────────────────────────────────────────────
    // Playlist ops
    // ────────────────────────────────────────────────

    fun setPlaylist(tracks: List<PlayingTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        playlist.clear()
        playlist.addAll(tracks)

        val p = ExoPlayerHolder.player

        val mediaItems = tracks.mapIndexed { i, track ->
            val mediaId = track.trackId ?: "${track.source}||${track.title}::${track.artist}::$i"
            buildMediaItem(track, mediaId)
        }

        val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        p.setMediaItems(mediaItems, safeIndex, 0L)

        p.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }

        if (isShuffleOn && mediaItems.size > 1) {
            p.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(mediaItems.size))
        }
        p.shuffleModeEnabled = isShuffleOn

        p.prepare()
        p.playWhenReady = true

        startMediaService()
        ensureProgressLoop(true)
        pushState()

        // ✅ запускаем автоподновление ссылок
        startAutoRefreshUrls()

        // (опционально) один раз обновим в фоне сразу, чтобы избранное быстрее ожило
        scope.launch {
            try { refreshUrlsOnce() } catch (_: Throwable) {}
        }
    }

    fun appendToPlaylist(tracks: List<PlayingTrack>) {
        if (tracks.isEmpty()) return

        playlist.addAll(tracks)

        val p = ExoPlayerHolder.player
        val mediaItems = tracks.mapIndexed { i, track ->
            val mediaId =
                "${track.source}||${track.title}::${track.artist}::append::$i::${System.currentTimeMillis()}"
            buildMediaItem(track, mediaId)
        }

        p.addMediaItems(mediaItems)
        pushState()
    }

    fun remainingCount(): Int {
        val p = ExoPlayerHolder.player
        val count = p.mediaItemCount
        if (count <= 0) return 0
        val idx = p.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
        return (count - idx).coerceAtLeast(0)
    }

    private fun startMediaService() {
        try {
            val intent = Intent(context, WaveMediaService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggle() {
        val p = ExoPlayerHolder.player
        if (p.isPlaying) p.pause() else p.play()
    }

    fun nextTrack() {
        val p = ExoPlayerHolder.player
        if (p.mediaItemCount == 0) return

        if (repeatMode == RepeatMode.ONE) p.seekTo(p.currentMediaItemIndex, 0L)
        else p.seekToNextMediaItem()

        p.playWhenReady = true
        p.play()
        pushState()
    }

    fun previousTrack() {
        val p = ExoPlayerHolder.player
        if (p.mediaItemCount == 0) return

        if (repeatMode == RepeatMode.ONE) p.seekTo(p.currentMediaItemIndex, 0L)
        else p.seekToPreviousMediaItem()

        p.playWhenReady = true
        p.play()
        pushState()
    }

    fun seekToFraction(fraction: Float) {
        val p = ExoPlayerHolder.player
        val duration = p.duration.takeIf { it > 0 } ?: return
        val position = (duration * fraction.coerceIn(0f, 1f)).toLong()
        p.seekTo(position)
        _state.value = _state.value.copy(positionMs = position)
    }

    private fun ensureProgressLoop(shouldRun: Boolean) {
        if (shouldRun && progressJob?.isActive != true) {
            progressJob = scope.launch {
                while (isActive) {
                    pushStatePositionsOnly()
                    delay(300L)
                }
            }
        } else if (!shouldRun) {
            progressJob?.cancel()
            progressJob = null
        }
    }

    private fun pushStatePositionsOnly() {
        val p = ExoPlayerHolder.player
        _state.value = _state.value.copy(
            positionMs = p.currentPosition,
            durationMs = p.duration.takeIf { it > 0 } ?: _state.value.durationMs,
            bufferedPercent = p.bufferedPercentage,
            isPlaying = p.isPlaying
        )
        maybeNotifyQueueLow()
    }

    private fun pushState() {
        val p = ExoPlayerHolder.player

        val currentItem = p.currentMediaItem
        val currentMeta = currentItem?.mediaMetadata
        val trackUrl = currentItem?.localConfiguration?.uri?.toString()

        val mediaId = currentItem?.mediaId.orEmpty()
        val fallbackSrc = mediaId.substringBefore("||", "").lowercase()

        val track = currentMeta?.toPlayingTrack(trackUrl, fallbackSource = fallbackSrc)

        val nextCover = p.getCoverForIndex(p.nextMediaItemIndex)
        val prevCover = p.getCoverForIndex(p.previousMediaItemIndex)

        _state.value = _state.value.copy(
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition,
            durationMs = p.duration.takeIf { it > 0 } ?: _state.value.durationMs,
            bufferedPercent = p.bufferedPercentage,
            track = track,
            nextCoverUrl = nextCover,
            prevCoverUrl = prevCover
        )

        onTrackChangedListener?.invoke(track)
    }

    private fun Player.getCoverForIndex(index: Int): String? {
        if (index == C.INDEX_UNSET) return null
        if (index < 0 || index >= mediaItemCount) return null
        return getMediaItemAt(index).mediaMetadata.artworkUri?.toString()
    }

    fun stop() {
        val p = ExoPlayerHolder.player
        p.stop()
        p.clearMediaItems()
        context.stopService(Intent(context, WaveMediaService::class.java))
        _state.value = PlayerState()
        ensureProgressLoop(false)
        stopAutoRefreshUrls()
    }

    fun setOnTrackEndListener(listener: () -> Unit) {
        onTrackEndListener = listener
    }

    fun release() {
        progressJob?.cancel()
        stopAutoRefreshUrls()
        scope.cancel()
    }

    fun getCurrentTrack(): PlayingTrack? = _state.value.track
    fun isPlaying(): Boolean = _state.value.isPlaying
}

// ────────────────────────────────────────────────
// Queue low helper
// ────────────────────────────────────────────────

private var onQueueLowListener: (() -> Unit)? = null
private var queueLowThreshold: Int = 6
private var queueLowTriggeredAtIndex: Int = -1

fun setOnQueueLowListener(threshold: Int = 6, listener: () -> Unit) {
    queueLowThreshold = threshold.coerceAtLeast(1)
    onQueueLowListener = listener
    queueLowTriggeredAtIndex = -1
}

private fun maybeNotifyQueueLow() {
    val p = ExoPlayerHolder.player
    if (p.mediaItemCount <= 0) return

    val idx = p.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
    val remaining = (p.mediaItemCount - idx).coerceAtLeast(0)

    if (remaining <= queueLowThreshold && idx != queueLowTriggeredAtIndex) {
        queueLowTriggeredAtIndex = idx
        onQueueLowListener?.invoke()
    }
}
