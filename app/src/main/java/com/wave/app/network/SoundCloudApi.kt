package com.wave.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val SOUNDCLOUD_CLIENT_ID = "MHDG7vIKasWstY0FaB07rK5WUoUjjCDC"

interface SoundCloudService {
    @GET("search/tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("client_id") clientId: String = SOUNDCLOUD_CLIENT_ID,
        @Query("limit") limit: Int = 25
    ): SCSearchResponse
}

object SoundCloudApi {

    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { 
                level = HttpLoggingInterceptor.Level.BODY 
            })
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api-v2.soundcloud.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(http)
            .build()
    }

    private val service by lazy { retrofit.create(SoundCloudService::class.java) }

    suspend fun searchTracks(query: String): List<SCSimpleTrack> {
        return try {
            println("🎵 [SoundCloud] SEARCH: '$query'")
            val resp = service.searchTracks(query = query)
            val items = resp.collection.orEmpty()

            println("✅ [SoundCloud] Found ${items.size} tracks")

            items.mapNotNull { t ->
                try {
                    val artist = t.user?.username ?: "Unknown Artist"
                    val art = t.artwork_url?.replace("-large", "-t500x500")
                    val progressive = t.media?.transcodings?.firstOrNull { it.format?.protocol == "progressive" }
                    val fallback = t.media?.transcodings?.firstOrNull()
                    val chosen = progressive ?: fallback

                    if (chosen?.url == null) {
                        println("❌ No transcoding URL for: ${t.title}")
                        null
                    } else {
                        println("🔗 Transcoding URL: ${chosen.url}")
                        SCSimpleTrack(
                            id = t.id?.toString() ?: "",
                            title = t.title ?: "Untitled",
                            artist = artist,
                            artworkUrl = art,
                            transcodingUrl = chosen.url,
                            protocol = chosen.format?.protocol
                        )
                    }
                } catch (e: Exception) {
                    println("❌ Error processing track: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            println("❌ SoundCloud search error: ${e.message}")
            emptyList()
        }
    }

    suspend fun resolveStream(transcodingUrl: String): String {
        println("=".repeat(50))
        println("🔄 [SoundCloud] RESOLVE START")
        println("📥 Input URL: $transcodingUrl")
        
        try {
            // Шаг 1: Формируем URL
            val separator = if (transcodingUrl.contains("?")) "&" else "?"
            val resolveUrl = "$transcodingUrl${separator}client_id=$SOUNDCLOUD_CLIENT_ID"
            println("🔗 Full resolve URL: $resolveUrl")

            // Шаг 2: Создаем запрос
            val request = Request.Builder()
                .url(resolveUrl)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            println("📤 Request created")

            // Шаг 3: Выполняем запрос
            println("🚀 Sending HTTP request...")
            val response = http.newCall(request).execute()
            println("📥 Response received")
            println("📊 Response code: ${response.code}")
            println("📊 Response message: ${response.message}")
            println("📊 Response headers: ${response.headers}")

            // Шаг 4: Проверяем статус
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                println("❌ HTTP Error ${response.code}: $errorBody")
                throw Exception("HTTP ${response.code}: $errorBody")
            }
            println("✅ HTTP request successful")

            // Шаг 5: Читаем тело
            println("📖 Reading response body...")
            val responseBody = response.body?.string()
            println("📦 Response body length: ${responseBody?.length ?: 0}")
            println("📦 Response body: ${responseBody?.take(500)}...") // Первые 500 символов

            if (responseBody.isNullOrEmpty()) {
                println("❌ Empty response body")
                throw Exception("Empty response from SoundCloud")
            }

            // Шаг 6: Парсим JSON
            println("🔍 Starting JSON parsing...")
            val jsonAdapter = moshi.adapter(SCResolveResponse::class.java)
            println("🔍 JSON adapter created")
            
            val result = jsonAdapter.fromJson(responseBody)
            println("🔍 JSON parsing result: $result")

            if (result == null) {
                println("❌ JSON parsing returned null")
                throw Exception("JSON parsing failed - result is null")
            }

            val audioUrl = result.url
            println("🔍 Extracted URL: $audioUrl")

            if (audioUrl.isNullOrEmpty()) {
                println("❌ URL is null or empty")
                throw Exception("No audio URL found in response")
            }

            println("✅ SUCCESS! Final audio URL: $audioUrl")
            println("=".repeat(50))
            
            return audioUrl

        } catch (e: Exception) {
            println("💥 CATCH BLOCK: ${e.javaClass.simpleName}")
            println("💥 Exception message: '${e.message}'")
            println("💥 Stack trace:")
            e.printStackTrace()
            println("=".repeat(50))
            throw Exception("SoundCloud resolve failed: ${e.message ?: "Unknown error"}")
        }
    }
}

/* ------------ DATA MODELS ------------ */

data class SCSearchResponse(val collection: List<SCTrack>?)

data class SCTrack(
    val id: Long?,
    val title: String?,
    val artwork_url: String?,
    val user: SCUser?,
    val media: SCMedia?
)

data class SCUser(val username: String?)
data class SCMedia(val transcodings: List<SCTranscoding>?)
data class SCTranscoding(val url: String, val format: SCFormat?)
data class SCFormat(val protocol: String?, @Json(name = "mime_type") val mimeType: String?)

data class SCSimpleTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val transcodingUrl: String?,
    val protocol: String?
)

data class SCResolveResponse(
    @Json(name = "url") val url: String?
)