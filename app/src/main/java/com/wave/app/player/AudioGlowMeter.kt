package com.wave.app.player

import android.media.audiofx.Visualizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

class AudioGlowMeter(
    private val scope: CoroutineScope
) {
    private var visualizer: Visualizer? = null
    private var job: Job? = null
    private var currentSessionId: Int? = null

    private val _level = MutableStateFlow(0f) // 0..1
    val level: StateFlow<Float> = _level

    private val _beat = MutableStateFlow(0L) // +1 на пик
    val beat: StateFlow<Long> = _beat

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (currentSessionId == audioSessionId) return

        release()
        currentSessionId = audioSessionId

        try {
            val v = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                enabled = true
            }
            visualizer = v

            job = scope.launch(Dispatchers.Default) {
                var smooth = 0f
                var prev = 0f
                var lastPeakMs = 0L

                while (isActive) {
                    val bytes = ByteArray(v.captureSize)
                    val ok = v.getWaveForm(bytes)

                    if (ok == Visualizer.SUCCESS) {
                        // RMS
                        var sum = 0.0
                        for (b in bytes) {
                            val x = (b.toInt() / 128.0)
                            sum += x * x
                        }
                        val rms = sqrt(sum / bytes.size).toFloat()

                        // сглаживание
                        smooth = smooth * 0.80f + rms * 0.20f

                        // усиление/нормализация (подстрой если надо)
                        val lvl = (smooth * 2.2f).coerceIn(0f, 1f)
                        _level.value = lvl

                        // детект пика (beat)
                        val now = System.currentTimeMillis()
                        val rising = lvl - prev
                        val isPeak = lvl > 0.55f && rising > 0.06f && (now - lastPeakMs) > 140

                        if (isPeak) {
                            _beat.value = _beat.value + 1
                            lastPeakMs = now
                        }
                        prev = lvl
                    }

                    delay(16L) // ~60fps
                }
            }
        } catch (_: Throwable) {
            // если визуализатор не завёлся на девайсе — просто не будет эффекта
            currentSessionId = null
            _level.value = 0f
        }
    }

    fun release() {
        job?.cancel()
        job = null
        try { visualizer?.enabled = false } catch (_: Throwable) {}
        try { visualizer?.release() } catch (_: Throwable) {}
        visualizer = null
        currentSessionId = null
        _level.value = 0f
    }
}
