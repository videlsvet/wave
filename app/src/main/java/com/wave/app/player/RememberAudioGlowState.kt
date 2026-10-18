package com.wave.app.player

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob

@Stable
class AudioGlowState internal constructor(
    val meter: AudioGlowMeter
)

@Composable
fun rememberAudioGlowState(): AudioGlowState {
    // отдельный scope для визуализатора
    val scope = remember {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    val meter = remember { AudioGlowMeter(scope) }

    DisposableEffect(Unit) {
        onDispose {
            meter.release()
            scope.cancel()
        }
    }

    return remember { AudioGlowState(meter) }
}
