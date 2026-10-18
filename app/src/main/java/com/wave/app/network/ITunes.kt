package com.wave.app.network

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class ITunesSearchResponse(
    val resultCount: Int,
    val results: List<ITunesTrack>
)

data class ITunesTrack(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val previewUrl: String,
    val artworkUrl100: String
)

interface ITunesService {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("limit") limit: Int = 25
    ): ITunesSearchResponse
}

object ITunesApi {

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory()) // ✅ ВАЖНОЕ ДОБАВЛЕНИЕ
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi)) // ✅ Используем moshi с адаптером
            .build()
    }

    private val service by lazy { retrofit.create(ITunesService::class.java) }

    suspend fun searchTracks(query: String): List<ITunesTrack> {
        return service.search(query).results
    }
}
