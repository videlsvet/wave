package com.wave.app.data

import com.wave.app.model.PlayingTrack
import com.wave.app.network.TrackResponse

fun TrackResponse.toPlayingTrack(defaultSource: String = ""): PlayingTrack {
    return PlayingTrack(
        title = this.title.orEmpty(),
        artist = this.artist.orEmpty(),
        image = this.image.orEmpty(),
        url = this.preview.orEmpty(),
        source = (this.source ?: defaultSource).lowercase(),
        externalId = this.externalId,
        artistId = this.artistId,
        albumId = this.albumId,
        ownerId = this.ownerId,
        accessKey = this.accessKey
    )
}


// ✅ ДОБАВЬ ЭТО
fun TrackResponse.waveKey(): String {
    val a = canonicalArtist(this.artist)
    val t = canonicalTitle(this.title)
    return "$a—$t"
}