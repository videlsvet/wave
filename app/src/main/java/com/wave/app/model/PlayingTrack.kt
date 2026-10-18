package com.wave.app.model

data class PlayingTrack(
    val title: String,
    val artist: String,
    val image: String,
    val url: String,
    val source: String = "",
    val externalId: String? = null,  // ✅ для Яндекса/VK/и т.д.
    // ✅ ДОБАВЬ ЭТО
    val artistId: String? = null,
    val albumId: String? = null,
    val ownerId: String? = null,
    val accessKey: String? = null,
    val trackId: String? = null
)

