package com.wave.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

object VKMusicAuth {
    const val ACCESS_TOKEN = "vk1.a.NCmZ9Ym3zbgf5LT-AImTr7QjrIGBZ_m0OSRve5frjMVQke2YsYiLSYyJRvlBu6r52A7tJglCBMMDJtiwaXo1OjGywQLfPEQdhRAytqb-QzI20OiVowu5r4kkYGsOIpsKFXQpxtrmyo0CjXFbBPgCrD4I5acURusM1VwB_1o-_FQVBmB9ICj4W_UkZROoCriMbD_OscRZ7uieDOzScfI_FA"
}

interface VKMusicService {
    @GET("audio.search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("access_token") token: String = VKMusicAuth.ACCESS_TOKEN,
        @Query("v") version: String = "5.199",
        @Query("count") count: Int = 50
    ): VKSearchResponse
}

object VKMusicApi {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.vk.com/method/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service: VKMusicService = retrofit.create(VKMusicService::class.java)

    suspend fun searchTracks(query: String): List<VKTrack> {
        return try {
            println("🔍 VK Search: $query")
            val response = service.searchTracks(query)
            val tracks = response.response.items ?: emptyList()
            println("✅ VK Found: ${tracks.size} tracks")

            // Анализ доступности треков
            var playableCount = 0
            tracks.forEach { track ->
                val isPlayable = !track.url.isNullOrBlank() && track.url.startsWith("http")
                if (isPlayable) playableCount++

                println("${if (isPlayable) "✅" else "❌"} ${track.title} - ${track.artist}")
                if (!isPlayable) {
                    println("   🔗 URL: ${track.url ?: "null"}")
                }
            }

            println("🎯 VK Summary: $playableCount/${tracks.size} tracks are playable")

            // Возвращаем все треки, но в UI нужно будет проверять url перед воспроизведением
            tracks
        } catch (e: Exception) {
            println("❌ VK Search error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}

// Простые data классы
data class VKSearchResponse(
    val response: VKResponse
)

data class VKResponse(
    val count: Int,
    val items: List<VKTrack>?
)

data class VKTrack(
    val id: Int,
    val owner_id: Int,
    val artist: String,
    val title: String,
    val duration: Int,
    val url: String?,
    val album: VKAlbum?
)

data class VKAlbum(
    val id: Int,
    val title: String,
    val thumb: VKThumb?
)

data class VKThumb(
    @Json(name = "photo_300") val photo_300: String?
)