package com.wave.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wave.app.data.WaveGenerator
import com.wave.app.data.WaveMode
import com.wave.app.data.WaveProfileStore
import com.wave.app.data.waveKey
import com.wave.app.network.TrackResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WaveViewModel(
    private val generator: WaveGenerator,
    private val store: WaveProfileStore
) : ViewModel() {

    private val _waveFlow = MutableStateFlow<List<TrackResponse>>(emptyList())
    val waveFlow: StateFlow<List<TrackResponse>> = _waveFlow.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentMode = WaveMode.EXPLORATION

    // Запуск волны
    fun startWave(seedArtist: String? = null, seedTitle: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val tracks = generator.generate(
                    seedArtistRaw = seedArtist,
                    seedTitleRaw = seedTitle,
                    amount = 30,
                    mode = currentMode
                )
                _waveFlow.value = tracks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Подгрузка следующей порции
    fun loadMoreWave() {
        if (_isLoading.value) return // Не грузим если уже грузим

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val current = _waveFlow.value
                val lastTrack = current.lastOrNull()

                val newTracks = generator.generate(
                    seedArtistRaw = lastTrack?.artist,
                    seedTitleRaw = lastTrack?.title,
                    amount = 20,
                    mode = WaveMode.FLOW // Плавный переход
                )

                _waveFlow.value = current + newTracks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Обратная связь
    fun onTrackPlayed(track: TrackResponse) {
        store.markPlayed(track.waveKey())
        store.onPlayTrack(track.artist)
    }

    fun onTrackLiked(track: TrackResponse) {
        store.onLikeTrack(track.artist)
    }

    fun onTrackDisliked(track: TrackResponse) {
        store.markDisliked(track.waveKey())
        store.onDislikeTrack(track.artist)

        // Удаляем из текущей волны
        _waveFlow.value = _waveFlow.value.filter { it.waveKey() != track.waveKey() }

        // Подгружаем замену
        loadMoreWave()
    }

    fun onTrackSkipped(track: TrackResponse) {
        store.markSkipped(track.waveKey())
        store.onSkipTrack(track.artist)
    }

    // Сброс волны (для рестарта)
    fun resetWave() {
        _waveFlow.value = emptyList()
        currentMode = WaveMode.EXPLORATION
    }
}