package com.wave.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import android.util.Base64
import com.wave.app.BuildConfig
import java.util.concurrent.TimeUnit

// ✅ ОДИНСТВЕННЫЙ SpotifyAuth — НЕ УДАЛЯТЬ
object SpotifyAuth {
    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0
    
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { 
                level = HttpLoggingInterceptor.Level.BASIC 
            })
            .build()
    }

    suspend fun fetchAccessToken(): String {
        // Используем кэшированный токен если он еще валиден
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return cachedToken!!
        }
        
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
        val creds = "$clientId:$clientSecret"
        val basic = "Basic " + Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
        val body = "grant_type=client_credentials"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val req = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .addHeader("Authorization", basic)
            .build()

        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("Spotify token error: ${res.code}")
            val moshi = Moshi.Builder().build()
            val adapter = moshi.adapter(TokenResponse::class.java)
            val parsed = adapter.fromJson(res.body!!.string())!!
            
            // Кэшируем токен (с запасом 5 минут до expiry)
            cachedToken = parsed.access_token
            tokenExpiryTime = System.currentTimeMillis() + (parsed.expires_in - 300) * 1000L
            
            return parsed.access_token
        }
    }

    data class TokenResponse(
        @Json(name = "access_token") val access_token: String,
        @Json(name = "token_type") val token_type: String,
        @Json(name = "expires_in") val expires_in: Int
    )
}

// ✅ Retrofit + API — бесконечный скролл на offset
interface SpotifyService {
    @GET("search")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0
    ): SpotifySearchResponse
}

object SpotifyApi {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/v1/")
            .addConverterFactory(MoshiConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(HttpLoggingInterceptor().apply { 
                        level = HttpLoggingInterceptor.Level.BASIC 
                    })
                    .build()
            )
            .build()
    }

    private val service by lazy { retrofit.create(SpotifyService::class.java) }

    suspend fun searchTracks(
        auth: String,
        query: String,
        offset: Int = 0,
        limit: Int = 25
    ): List<SpotifyTrack> {
        val resp = service.search(auth, query, "track", limit, offset)
        return resp.tracks.items.map {
            SpotifyTrack(
                id = it.id,
                name = it.name,
                artists = it.artists.map { a -> a.name },
                albumImage = it.album.images.firstOrNull()?.url,
                previewUrl = it.preview_url,
                durationMs = it.duration_ms,
                popularity = it.popularity
            )
        }
    }
}

// ✅ Data-модели
data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<String>,
    val albumImage: String?,
    val previewUrl: String?,
    val durationMs: Int? = null,
    val popularity: Int? = null
)

data class SpotifySearchResponse(
    val tracks: Tracks
) {
    data class Tracks(val items: List<Item>) {
        data class Item(
            val id: String,
            val name: String,
            val artists: List<Artist>,
            val album: Album,
            val preview_url: String?,
            val duration_ms: Int?,
            val popularity: Int?
        )
        data class Artist(val name: String)
        data class Album(val images: List<Image>) {
            data class Image(val url: String, val width: Int?, val height: Int?)
        }
    }
}