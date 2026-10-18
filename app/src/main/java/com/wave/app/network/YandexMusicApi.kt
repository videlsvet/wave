package com.wave.app.network

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

// 🔐 Перенеси токен в BuildConfig для безопасности!
private const val YANDEX_TOKEN = "y0__AgAAAAA2f5b9AATuwYAAAAF4re6ZBhje-AYgqrno4hR4qizr0w30lFVxrQp90hkmjpiscQ"

interface YandexService {
    @GET("search")
    suspend fun searchTracks(
        @Header("Authorization") auth: String,
        @Query("text") query: String,
        @Query("type") type: String = "track",
        @Query("page") page: Int = 0, // ✅ Обязательный параметр!
        @Query("pageSize") pageSize: Int = 20 // ✅ Обязательный параметр!
    ): YandexSearchResponse

    @GET("tracks/{trackId}/download-info")
    suspend fun downloadInfo(
        @Header("Authorization") auth: String,
        @Path("trackId") trackId: String // ✅ Исправлено имя параметра
    ): List<YandexDownloadInfo>
}

object YandexMusicApi {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("yandex_api", Context.MODE_PRIVATE)
    }

    private fun getDeviceId(): String {
        val stored = prefs?.getString("device_id", null)
        if (stored != null) return stored
        val newId = "ym${UUID.randomUUID()}" // ✅ Формат как в официальном приложении
        prefs?.edit()?.putString("device_id", newId)?.apply()
        return newId
    }

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .addHeader("User-Agent", "Yandex-Music-API")
                    .addHeader("X-Yandex-Music-Client", "Android/24.2.1")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Device-ID", getDeviceId()) // ✅ Правильное имя заголовка
                    // Authorization НЕ добавляем здесь - он будет в каждом запросе отдельно
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply { 
                level = HttpLoggingInterceptor.Level.BODY 
            })
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.music.yandex.net/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(http)
            .build()
    }

    private val service by lazy { retrofit.create(YandexService::class.java) }

    suspend fun searchTracks(query: String): List<YandexTrack> {
        return try {
            val response = service.searchTracks(
                auth = "OAuth $YANDEX_TOKEN", // ✅ Правильный формат
                query = query,
                type = "track",
                page = 0,
                pageSize = 20
            )
            
            val items = response.result?.tracks?.results.orEmpty()
            items.map { track ->
                val artist = track.artists?.firstOrNull()?.name ?: "Unknown Artist"
                val cover = track.albums?.firstOrNull()?.coverUri?.let { coverUri ->
                    // ✅ Правильное формирование URL обложки
                    when {
                        coverUri.startsWith("http") -> coverUri
                        coverUri.startsWith("//") -> "https:$coverUri"
                        else -> "https://${coverUri.replace("%%", "400x400")}"
                    }
                }
                
                YandexTrack(
                    id = track.id ?: "",
                    title = track.title ?: "Unknown Title",
                    artist = artist,
                    cover = cover
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // ✅ Возвращаем пустой список вместо ошибки
        }
    }

    suspend fun getStreamUrl(trackId: String): String? {
        return try {
            val infos = service.downloadInfo(
                auth = "OAuth $YANDEX_TOKEN",
                trackId = trackId
            )
            
            val firstInfo = infos.firstOrNull() ?: return null
            val infoUrl = when {
                firstInfo.downloadInfoUrl.startsWith("http") -> firstInfo.downloadInfoUrl
                firstInfo.downloadInfoUrl.startsWith("//") -> "https:${firstInfo.downloadInfoUrl}"
                else -> "https://${firstInfo.downloadInfoUrl.trimStart('/')}"
            }

            // ✅ Упрощенный запрос для получения direct link
            val client = OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(infoUrl)
                .addHeader("Authorization", "OAuth $YANDEX_TOKEN")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val adapter = moshi.adapter(DirectStreamResponse::class.java)
                val parsed = adapter.fromJson(body)
                parsed?.directLink
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// 🎵 Data модели (остаются без изменений)
data class YandexSearchResponse(
    val result: Result?
) {
    data class Result(
        val tracks: Tracks?
    ) {
        data class Tracks(
            @Json(name = "results") val results: List<Track>?
        ) {
            data class Track(
                val id: String?,
                val title: String?,
                val artists: List<Artist>?,
                val albums: List<Album>?
            )
            data class Artist(val name: String?)
            data class Album(@Json(name = "coverUri") val coverUri: String?)
        }
    }
}

data class YandexDownloadInfo(
    @Json(name = "downloadInfoUrl") val downloadInfoUrl: String
)

data class DirectStreamResponse(
    @Json(name = "directLink") val directLink: String?
)

data class YandexTrack(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String?
)