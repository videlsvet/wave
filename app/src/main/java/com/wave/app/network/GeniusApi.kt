package com.wave.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

object GeniusAuth {
    // ⚙️ Эти данные оставляем без изменений
    private const val CLIENT_ID = "DwP2QJFs3zzqQLqWKQOEaGA9CIMi2DWvr67eBviKvsPxJT0naxm95ciQfW_QXA7C"
    private const val CLIENT_SECRET = "9sysH87HE3x2NFSn-zwfmL-yXqvvcQgNCFUNSBtjKqfj6kljiHzHGAqJlk87e8Oo1dm9jynJzULZvW49BExZpg"

    // 🔥 Рабочий Access Token
    const val ACCESS_TOKEN = "cbWEmOQ57RX7XLndVc0Z0XUOMcq5-3OqrqoPnpiSkBHTj6joT3IWpVub2RXhbN0f"
}

interface GeniusService {
    @GET("search")
    suspend fun searchSongs(
        @Header("Authorization") auth: String,
        @Query("q") query: String
    ): GeniusSearchResponse
}

object GeniusApi {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.genius.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val service: GeniusService by lazy {
        retrofit.create(GeniusService::class.java)
    }

    // 🧠 Главная функция — поиск текста
    suspend fun searchLyrics(trackTitle: String?, artist: String?): String {
        return withContext(Dispatchers.IO) {
            if (trackTitle.isNullOrBlank()) return@withContext "❌ Неизвестное название трека."
            try {
                val query = listOfNotNull(trackTitle, artist).joinToString(" ")
                val token = "Bearer ${GeniusAuth.ACCESS_TOKEN}"
                val response = service.searchSongs(token, query)

                val song = response.response.hits.firstOrNull()?.result
                if (song != null) {
                    val rawLyrics = extractLyricsFromUrl(song.url)
                    val clean = cleanGeniusLyrics(rawLyrics)
                    if (clean.isNotEmpty()) {
                        "🎵 ${song.title} — ${song.primary_artist.name}\n\n$clean"
                    } else {
                        "🎵 ${song.title} — ${song.primary_artist.name}\n\n" +
                                "Не удалось извлечь текст. Ссылка: ${song.url}"
                    }
                } else {
                    "❌ Текст не найден на Genius для \"$trackTitle\"."
                }
            } catch (e: Exception) {
                "❌ Ошибка при поиске текста: ${e.message}"
            }
        }
    }

    // 📄 Извлекаем текст песни с HTML-страницы Genius
    private suspend fun extractLyricsFromUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(10_000)
                .get()

            // Genius хранит текст в разных контейнерах, выбираем все возможные
            val selectors = listOf(
                "div[data-lyrics-container=true]",
                "div[class*=\"Lyrics__Container\"]",
                "div.lyrics",
                "section[class*=\"lyrics\"]"
            )

            val collected = buildString {
                for (selector in selectors) {
                    val elements = doc.select(selector)
                    if (elements.isNotEmpty()) {
                        elements.forEach { e ->
                            appendLine(e.wholeText())
                        }
                        break
                    }
                }
            }

            collected.ifBlank { doc.text() }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    // 🧹 Очистка и форматирование текста
    private fun cleanGeniusLyrics(raw: String): String {
        return raw
            .replace(Regex("<[^>]+>"), "") // убираем HTML
            .replace(Regex("(?i)(Translations?|Romanization|Contributors?)"), "")
            .replace(Regex("(?<=\\[).*?(?=])")) { it.value.trim() }
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\n{2,}"), "\n\n")
            .trim()
    }
}

// ✅ Модели API
data class GeniusSearchResponse(val response: GeniusResponse)
data class GeniusResponse(val hits: List<GeniusHit>)
data class GeniusHit(val result: GeniusSong)
data class GeniusSong(
    val id: Int,
    val title: String,
    val url: String,
    @Json(name = "primary_artist") val primary_artist: GeniusArtist
)
data class GeniusArtist(val name: String)
