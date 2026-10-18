package com.wave.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import java.net.HttpURLConnection
import java.net.URL
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.wave.app.MainActivity
import com.wave.app.R
import kotlinx.coroutines.*


@UnstableApi
class WaveMediaService : MediaSessionService(), Player.Listener {

    private lateinit var player: ExoPlayer
    private lateinit var compatSession: MediaSessionCompat
    private lateinit var mediaSession: MediaSession

    private val notificationId = 42
    private val channelId = "wave_music_channel"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var isInForeground = false

    private var lastArtUrl: String? = null
    private var lastArtBitmap: Bitmap? = null


    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayerHolder.player
        player.addListener(this)

        mediaSession = MediaSession.Builder(this, player)
            .setId("WaveSession")
            .build()

        compatSession = MediaSessionCompat(this, "WaveCompatSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = player.play()
                override fun onPause() = player.pause()
                override fun onSkipToNext() = player.seekToNextMediaItem()
                override fun onSkipToPrevious() = player.seekToPreviousMediaItem()
                override fun onSeekTo(pos: Long) = player.seekTo(pos)
            })
            isActive = true
        }

        createNotificationChannel()

        // ✅ ВАЖНО: стартуем foreground МГНОВЕННО (без сети и без обложки)
        startForeground(notificationId, buildPlaceholderNotification())
        isInForeground = true

        // Уже потом — нормальное уведомление (может грузить обложку)
        pushNotification()

        startProgressUpdater()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Wave Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wave Player Notifications"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildPlaceholderNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Wave")
            .setContentText("Запуск воспроизведения…")
            .setSmallIcon(R.drawable.wave_splash_logo)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setStyle(
                MediaStyle()
                    .setMediaSession(compatSession.sessionToken)
            )
            .build()
    }

    private fun setSessionMetadata(largeIcon: Bitmap?) {
        val md = player.mediaMetadata
        val duration = player.duration.takeIf { it > 0 } ?: -1L

        val compatMd = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, md.title?.toString() ?: "Wave")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, md.artist?.toString() ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .apply {
                if (largeIcon != null) {
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIcon)
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ART, largeIcon)
                }
            }
            .build()

        compatSession.setMetadata(compatMd)
    }

    private fun buildPlaybackState(isPlaying: Boolean): PlaybackStateCompat {
        val speed = if (isPlaying) 1f else 0f
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                player.currentPosition.coerceAtLeast(0L),
                speed
            )
            .setBufferedPosition(player.bufferedPosition.coerceAtLeast(0L))
            .build()
    }

    private suspend fun buildMediaNotification(): Notification = coroutineScope {
        val md = player.mediaMetadata
        val title = md.title?.toString() ?: "Wave"
        val artist = md.artist?.toString() ?: ""
        val artUrl = md.artworkUri?.toString()
        val isPlaying = player.isPlaying

        android.util.Log.d(
            "WaveArt",
            "mediaId=${player.currentMediaItem?.mediaId} artUrl=$artUrl"
        )

        // ✅ Обложку грузим, но это НЕ мешает старту foreground (он уже выполнен)
        val largeIcon: Bitmap? = withContext(Dispatchers.IO) {
            try {
                val raw = artUrl ?: run {
                    android.util.Log.w("WaveMediaService", "ART: artUrl is null")
                    return@withContext null
                }

                val proxiedUrl =
                    "https://d5dn6r204cm4tpgfe0ms.kf69zffa.apigw.yandexcloud.net/search" +
                            "?type=img&u=" + java.net.URLEncoder.encode(raw, "UTF-8")

                android.util.Log.i("WaveMediaService", "ART: raw=$raw")
                android.util.Log.i("WaveMediaService", "ART: proxied=$proxiedUrl")

                if (proxiedUrl == lastArtUrl && lastArtBitmap != null) {
                    android.util.Log.i("WaveMediaService", "ART: cache hit")
                    return@withContext lastArtBitmap
                }

                val conn = (URL(proxiedUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "image/*,*/*;q=0.8")
                }

                val code = conn.responseCode
                val ct = conn.contentType
                android.util.Log.i("WaveMediaService", "ART: http=$code contentType=$ct")

                val stream =
                    if (code in 200..299) conn.inputStream
                    else conn.errorStream

                val bytes = stream.use { it.readBytes() }

                android.util.Log.i("WaveMediaService", "ART: bytes=${bytes.size} head=" +
                        bytes.take(80).toByteArray().toString(Charsets.UTF_8).replace("\n", "\\n")
                )

                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                android.util.Log.i("WaveMediaService", "ART: bmp=${bmp?.width}x${bmp?.height}")

                if (bmp != null) {
                    lastArtUrl = proxiedUrl
                    lastArtBitmap = bmp
                }

                bmp
            } catch (e: Exception) {
                android.util.Log.e("WaveMediaService", "ART LOAD ERROR", e)
                null
            }
        }





        setSessionMetadata(largeIcon)
        compatSession.setPlaybackState(buildPlaybackState(isPlaying))

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseAction = if (isPlaying)
            PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY

        val contentPi = PendingIntent.getActivity(
            this@WaveMediaService,
            0,
            Intent(this@WaveMediaService, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        NotificationCompat.Builder(this@WaveMediaService, channelId)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(largeIcon)
            .setSmallIcon(R.drawable.wave_splash_logo)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .addAction(
                R.drawable.ic_previous,
                "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@WaveMediaService,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
            .addAction(
                playPauseIcon,
                "Play/Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@WaveMediaService,
                    playPauseAction
                )
            )
            .addAction(
                R.drawable.ic_next,
                "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@WaveMediaService,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(compatSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .build()
    }

    private fun pushNotification() {
        scope.launch {
            val n = buildMediaNotification()
            // мы уже в foreground; просто обновляем
            nm.notify(notificationId, n)
        }
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                compatSession.setPlaybackState(buildPlaybackState(player.isPlaying))
                // pushNotification() УБРАТЬ ОТСЮДА
                delay(1000L)
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(compatSession, intent)
        return START_STICKY
    }

    override fun onEvents(player: Player, events: Player.Events) {
        pushNotification()
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        pushNotification()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        pushNotification()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop()
        player.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        progressJob?.cancel()
        player.removeListener(this)
        compatSession.release()
        mediaSession.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        scope.cancel()
        super.onDestroy()
    }
}
