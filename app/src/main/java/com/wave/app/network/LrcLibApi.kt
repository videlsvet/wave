package com.wave.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ===== API =====

private interface LrcLibService {
    // GET https://lrclib.net/api/get?track_name=...&artist_name=...&album_name=...&duration=...
    @GET("api/get")
    suspend fun getByMeta(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationMs: Long? = null, // LRCLIB: duration в ms (опционально)
    ): LrcLibRecord

    // GET https://lrclib.net/api/search?query=...
    @GET("api/search")
    suspend fun search(@Query("query") query: String): List<LrcLibRecord>

    // GET https://lrclib.net/api/get/{id}
    @GET("api/get/{id}")
    suspend fun getById(@Path("id") id: Long): LrcLibRecord
}

/**
 * LRCLIB в разных клиентах/версии может отдавать camelCase или snake_case.
 * Поэтому держим "дублёры" и берём первое не-null.
 */
data class LrcLibRecord(
    val id: Long? = null,

    @Json(name = "trackName") val trackName1: String? = null,
    @Json(name = "track_name") val trackName2: String? = null,

    @Json(name = "artistName") val artistName1: String? = null,
    @Json(name = "artist_name") val artistName2: String? = null,

    @Json(name = "albumName") val albumName1: String? = null,
    @Json(name = "album_name") val albumName2: String? = null,

    // duration может быть в ms или sec — мы в логике не полагаемся на это поле
    val duration: Long? = null,

    val instrumental: Boolean? = null,

    @Json(name = "plainLyrics") val plainLyrics1: String? = null,
    @Json(name = "plain_lyrics") val plainLyrics2: String? = null,

    @Json(name = "syncedLyrics") val syncedLyrics1: String? = null,
    @Json(name = "synced_lyrics") val syncedLyrics2: String? = null,
) {
    val trackName: String? get() = trackName1 ?: trackName2
    val artistName: String? get() = artistName1 ?: artistName2
    val albumName: String? get() = albumName1 ?: albumName2
    val plainLyrics: String? get() = plainLyrics1 ?: plainLyrics2
    val syncedLyrics: String? get() = syncedLyrics1 ?: syncedLyrics2
}

// ===== Client =====

object LrcLibApi {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                // LRCLIB просит ставить понятный User-Agent (рекомендация для API)
                val req = chain.request().newBuilder()
                    .header("User-Agent", "WaveApp/1.0 (Android)")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val service: LrcLibService by lazy {
        retrofit.create(LrcLibService::class.java)
    }

    /**
     * Возвращает:
     * - syncedLyrics (LRC) если есть
     * - иначе plainLyrics
     * - иначе понятное сообщение
     */
    suspend fun fetchLyrics(
        trackTitle: String?,
        artist: String?,
        album: String? = null,
        durationMs: Long? = null,
    ): String = withContext(Dispatchers.IO) {

        val t = trackTitle?.trim().orEmpty()
        val a = artist?.trim().orEmpty()
        if (t.isBlank() || a.isBlank()) {
            return@withContext "❌ Неизвестные метаданные трека (title/artist)."
        }

        // 1) пробуем точное совпадение через /api/get
        val direct = try {
            service.getByMeta(
                trackName = t,
                artistName = a,
                albumName = album?.takeIf { it.isNotBlank() },
                durationMs = durationMs // можно передавать, можно null
            )
        } catch (e: HttpException) {
            null // не найдено / ошибка
        } catch (e: Exception) {
            null
        }

        val picked = when {
            direct?.syncedLyrics?.isNullOrBlank() == false -> direct
            direct?.plainLyrics?.isNullOrBlank() == false -> direct
            else -> null
        }

        if (picked != null) {
            return@withContext picked.syncedLyrics ?: picked.plainLyrics ?: ""
        }

        // 2) fallback: /api/search -> /api/get/{id}
        val query = "$t $a"
        val search = try {
            service.search(query)
        } catch (e: Exception) {
            emptyList()
        }

        val bestId = search.firstOrNull { it.id != null }?.id
        if (bestId != null) {
            val byId = try {
                service.getById(bestId)
            } catch (e: Exception) {
                null
            }

            val text = byId?.syncedLyrics ?: byId?.plainLyrics
            if (!text.isNullOrBlank()) return@withContext text
        }

        return@withContext "❌ Текст/таймкоды не найдены в LRCLIB для \"$t — $a\"."
    }
}
