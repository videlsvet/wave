package com.wave.app.network

import com.squareup.moshi.Json
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// 🔹 Модель трека (универсальная для всех источников)
data class TrackResponse(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val image: String? = null,
    val preview: String? = null,
    val duration: Int? = null,
    val source: String? = null,

    @Json(name = "error") val error: String? = null,

    @Json(name = "externalId") val externalId: String? = null,

    @Json(name = "artist_id") val artistId: String? = null,
    @Json(name = "album_id") val albumId: String? = null,
    @Json(name = "album_title") val albumTitle: String? = null,
    @Json(name = "album_year") val albumYear: Int? = null,

    @Json(name = "owner_id") val ownerId: String? = null,
    @Json(name = "audio_id") val audioId: String? = null,
    @Json(name = "access_key") val accessKey: String? = null
)


// ──────────────────────────────────────────────── Artist / Album models ────────────────────────────────────────────────

data class ArtistMeta(
    @Json(name = "artist_id") val artistId: String? = null,
    @Json(name = "artist_name") val artistName: String? = null,
    val image: String? = null
)
// 🔹 Ответ preview endpoint
data class PreviewResponse(
    val preview: String? = null
)

data class AlbumMeta(
    @Json(name = "album_id") val albumId: String? = null,
    @Json(name = "album_title") val albumTitle: String? = null,
    @Json(name = "album_year") val albumYear: Int? = null,
    val image: String? = null,

    // VK playlist album meta
    @Json(name = "owner_id") val ownerId: String? = null,
    @Json(name = "access_key") val accessKey: String? = null
)

data class ArtistResponse(
    val type: String? = null,
    val source: String? = null,
    val artist: ArtistMeta? = null,
    val tracks: List<TrackResponse> = emptyList(),
    val warning: String? = null
)

data class AlbumResponse(
    val type: String? = null,
    val source: String? = null,
    val album: AlbumMeta? = null,
    val tracks: List<TrackResponse> = emptyList(),
    val warning: String? = null
)

// ──────────────────────────────────────────────── API ────────────────────────────────────────────────

interface WaveServerApi {

    // ✅ SEARCH (type=search по умолчанию на твоём backend)
    @GET("search")
    suspend fun searchTracks(
        @Query("service") service: String,
        @Query("q") query: String
    ): List<TrackResponse>?

    // ✅ ARTIST (Yandex + VK)
    @GET("search")
    suspend fun getArtist(
        @Query("service") service: String,
        @Query("type") type: String = "artist",
        @Query("artist_id") artistId: String? = null,
        @Query("artist_name") artistName: String? = null,
        // backend допускает q как fallback
        @Query("q") q: String? = null
    ): ArtistResponse

    // ✅ ALBUM (Yandex + VK playlist-album)
    @GET("search")
    suspend fun getAlbum(
        @Query("service") service: String,
        @Query("type") type: String = "album",
        // Yandex:
        @Query("album_id") albumId: String? = null,
        // VK playlist-album:
        @Query("owner_id") ownerId: String? = null,
        @Query("access_key") accessKey: String? = null
    ): AlbumResponse

    // ✅ PREVIEW (ленивая подгрузка preview по track_id)
    @GET("search")
    suspend fun getPreview(
        @Query("service") service: String,
        @Query("type") type: String = "preview",
        @Query("track_id") trackId: String
    ): TrackResponse


    // ✅ Favorites / Profile backend (2-я функция)
    @POST("https://functions.yandexcloud.net/d4e55oqrims414ecgmtd")
    suspend fun addFavorite(@Body body: RequestBody): ResponseBody

    @POST("https://functions.yandexcloud.net/d4e55oqrims414ecgmtd")
    suspend fun removeFavorite(@Body body: RequestBody): ResponseBody

    @POST("https://functions.yandexcloud.net/d4e55oqrims414ecgmtd")
    suspend fun listFavorites(@Body body: RequestBody): List<TrackResponse>?
}
