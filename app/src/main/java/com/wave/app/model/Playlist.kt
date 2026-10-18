package com.wave.app.model

import com.wave.app.network.TrackResponse

data class Playlist(
    val id: Int,
    val title: String,
    val source: String, // "spotify", "itunes", "vkmusic", "soundcloud"
    val tracks: List<TrackResponse> = emptyList()
)
