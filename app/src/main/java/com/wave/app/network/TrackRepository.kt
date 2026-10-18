package com.wave.app.network

import android.util.Log
import retrofit2.HttpException

enum class MusicService(val key: String) {
    SPOTIFY("spotify"),
    SOUNDCLOUD("soundcloud"),
    YANDEX("yandex"),
    VK("vkmusic")
}

class TrackRepository {

    private fun ensureNoApiError(service: MusicService, res: List<TrackResponse>): List<TrackResponse> {
        val apiErr = res.firstOrNull()?.error
        if (!apiErr.isNullOrBlank()) {
            Log.e("WaveSearch", "service=${service.key} apiError=$apiErr")
            throw IllegalStateException(apiErr)
        }

        return res.filter { !(it.title.isNullOrBlank() && it.artist.isNullOrBlank() && it.preview.isNullOrBlank()) }
    }


    suspend fun search(service: MusicService, query: String): List<TrackResponse> {
        val res = when (service) {
            MusicService.SPOTIFY ->
                WaveServer.api.searchTracks(service.key, query).orEmpty()


            MusicService.SOUNDCLOUD ->
                WaveServer.api.searchTracks(service.key, query).orEmpty()

            MusicService.YANDEX,
            MusicService.VK ->
                WaveServer.api.searchTracks(service.key, query).orEmpty()
        }

        Log.d("WaveSearch", "service=${service.key} q=$query size=${res.size} first=${res.firstOrNull()}")
        return ensureNoApiError(service, res)
    }

    suspend fun searchAll(query: String): List<TrackResponse> {
        val spotify = ensureNoApiError(
            MusicService.SPOTIFY,
            WaveServer.api.searchTracks("spotify", query).orEmpty()
        )


        val sc = ensureNoApiError(MusicService.SOUNDCLOUD, WaveServer.api.searchTracks("soundcloud", query).orEmpty())
        val yandex = ensureNoApiError(MusicService.YANDEX, WaveServer.api.searchTracks("yandex", query).orEmpty())
        val vk = ensureNoApiError(MusicService.VK, WaveServer.api.searchTracks("vkmusic", query).orEmpty())

        return spotify + sc + yandex + vk
    }
}



