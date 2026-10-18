package com.wave.app

import android.app.Application
import com.wave.app.player.ExoPlayerHolder

class WaveApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 🎧 Инициализация глобального экземпляра ExoPlayer
        ExoPlayerHolder.initializePlayer(this)
    }
}