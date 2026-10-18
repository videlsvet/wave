package com.wave.app.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@UnstableApi
object ExoPlayerHolder {
    private var _player: ExoPlayer? = null

    val player: ExoPlayer
        get() = _player ?: throw IllegalStateException("Player not initialized. Call initializePlayer first.")

    fun initializePlayer(context: Context) {
        if (_player == null) {
            _player = ExoPlayer.Builder(context).build()
        }
    }

    fun releasePlayer() {
        _player?.release()
        _player = null
    }

    // ──────────────────────────────── ✅ READY-TO-USE GLOW STATE ────────────────────────────────

    @Composable
    fun rememberGlowState(): GlowState {
        val exo = remember { player }
        val scope = rememberCoroutineScope()
        val meter = remember { AudioGlowMeter(scope) }

        // дождаться валидного audioSessionId и подключить Visualizer
        LaunchedEffect(exo) {
            repeat(30) {
                val id = exo.audioSessionId
                if (id != 0) {
                    meter.attach(id)
                    return@LaunchedEffect
                }
                delay(50)
            }
            meter.attach(exo.audioSessionId)
        }

        DisposableEffect(Unit) {
            onDispose { meter.release() }
        }

        val level by meter.level.collectAsState()
        val beat by meter.beat.collectAsState()

        return remember(level, beat) { GlowState(level = level, beat = beat) }
    }

    data class GlowState(val level: Float, val beat: Long)
}
