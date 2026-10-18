package com.wave.app.data

import com.wave.app.network.MusicService
import com.wave.app.network.TrackRepository
import com.wave.app.network.TrackResponse
import android.util.Log
import kotlin.random.Random
import retrofit2.HttpException

class WaveGenerator(
    private val repo: TrackRepository,
    private val store: WaveProfileStore
) {

    /**
     * 🌊 Генерация "Моей Волны" — бесконечный персонализированный поток
     *
     * Режимы:
     * - COLD_START: нет истории → универсальные хиты + seed
     * - EXPLORATION: есть история → 70% знакомое, 30% открытия
     * - FLOW: активное прослушивание → плавные переходы
     */
    suspend fun generate(
        seedArtistRaw: String? = null,
        seedTitleRaw: String? = null,
        amount: Int = 30,
        mode: WaveMode = WaveMode.EXPLORATION
    ): List<TrackResponse> {

        val seedArtist = canonicalArtist(seedArtistRaw)
        val seedTitle = canonicalTitle(seedTitleRaw)

        // Профиль пользователя
        val topArtists = store.topArtists(10).map { canonicalArtist(it) }.filter { it.isNotBlank() }
        val topGenres = store.topGenres(5)
        val historySet = store.historyKeysLast(200)

        // ═══════════════════════════════════════════════════════
        //          COLD START — нет истории
        // ═══════════════════════════════════════════════════════

        if (topArtists.isEmpty() && seedArtist.isBlank()) {
            return generateColdStart(amount)
        }

        // ═══════════════════════════════════════════════════════
        //          ФОРМИРУЕМ QUERIES
        // ═══════════════════════════════════════════════════════

        val queries = buildList {
            // 1. Seed-трек (если есть)
            if (seedArtist.isNotBlank()) {
                add(seedArtist)
                if (seedTitle.isNotBlank()) add("$seedArtist $seedTitle")
            }

            // 2. Топ артисты профиля
            topArtists.take(5).forEach { a ->
                if (a.isNotBlank() && a != seedArtist) add(a)
            }

            // 3. Жанры (для разнообразия)
            topGenres.take(2).forEach { genre ->
                add(genre)
            }

            // 4. Сильное слово из seed-названия
            if (seedTitle.isNotBlank()) {
                val strongWord = extractStrongWord(seedTitle)
                if (strongWord != null) add(strongWord)
            }

            // 5. Случайные артисты для открытий
            if (mode == WaveMode.EXPLORATION) {
                val randomQueries = listOf("new music", "indie", "electronic", "chill")
                add(randomQueries.random())
            }
        }
            .map { it.normalizeQuery() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        if (queries.isEmpty()) return generateColdStart(amount)

        // ═══════════════════════════════════════════════════════
        //          ПОИСК КАНДИДАТОВ
        // ═══════════════════════════════════════════════════════

        val candidates = mutableListOf<TrackResponse>()

        for (q in queries) {
            candidates += safeSearch(q, MusicService.YANDEX)
            candidates += safeSearch(q, MusicService.VK)
        }

        // Похожие артисты (коллаборации)
        val coArtists = extractCoArtists(candidates, seedArtist, topArtists.toSet())
        for (a in coArtists.take(3)) {
            candidates += safeSearch(a, MusicService.YANDEX)
        }

        // ═══════════════════════════════════════════════════════
        //          ФИЛЬТРАЦИЯ
        // ═══════════════════════════════════════════════════════

        val filtered = candidates.asSequence()
            .filter { it.isPlayable() }
            .filter { it.isAllowedSource() }
            .filter { !store.isDisliked(it.waveKey()) }
            .filter { it.waveKey() !in historySet }
            .filter { store.skipCount(it.waveKey()) < 3 } // Макс 3 пропуска
            .distinctBy { it.waveKey() }
            .toList()

        if (filtered.isEmpty()) return generateColdStart(amount)

        // Дедупликация по "ядру" названия
        val seenTitleCore = HashSet<String>()
        val filtered2 = filtered.filter { tr ->
            val core = titleCore(tr)
            if (core.isBlank()) true
            else seenTitleCore.add("${canonicalArtist(tr.artist)}—$core")
        }

        if (filtered2.isEmpty()) return generateColdStart(amount)

        // ═══════════════════════════════════════════════════════
        //          СКОРИНГ И СОРТИРОВКА
        // ═══════════════════════════════════════════════════════

        val scored = filtered2.map { it to scoreTrack(it, seedArtist, topArtists.toSet(), seedTitle) }
            .sortedByDescending { it.second }

        // ═══════════════════════════════════════════════════════
        //          УМНЫЙ MIX: знакомое + открытия
        // ═══════════════════════════════════════════════════════

        val topSet = topArtists.toSet()
        val familiar = scored.filter { (tr, _) ->
            val a = canonicalArtist(tr.artist)
            a.isNotBlank() && a in topSet
        }

        val discoveries = scored.filter { (tr, _) ->
            val a = canonicalArtist(tr.artist)
            a.isNotBlank() && a !in topSet
        }

        // Процент открытий зависит от режима
        val discoveryRatio = when (mode) {
            WaveMode.COLD_START -> 0.5
            WaveMode.EXPLORATION -> 0.3
            WaveMode.FLOW -> 0.2
        }

        val targetDiscoveries = (amount * discoveryRatio).toInt().coerceIn(3, amount - 5)

        val byArtistCount = HashMap<String, Int>()
        val picked = ArrayList<TrackResponse>(amount)

        fun tryAdd(tr: TrackResponse): Boolean {
            val artist = canonicalArtist(tr.artist)
            val count = byArtistCount[artist] ?: 0
            if (artist.isNotBlank() && count >= 2) return false // Макс 2 трека на артиста
            picked += tr
            byArtistCount[artist] = count + 1
            return true
        }

        // 1) Сначала добираем открытия
        var discoveryCount = 0
        for ((tr, _) in discoveries) {
            if (picked.size >= amount) break
            if (discoveryCount >= targetDiscoveries) break
            if (tryAdd(tr)) discoveryCount++
        }

        // 2) Затем добиваем знакомыми
        for ((tr, _) in familiar + discoveries) {
            if (picked.size >= amount) break
            tryAdd(tr)
        }

        return picked
    }

    // ═══════════════════════════════════════════════════════════
    //                  COLD START — нет данных
    // ═══════════════════════════════════════════════════════════

    private suspend fun generateColdStart(amount: Int): List<TrackResponse> {
        val popularQueries = listOf(
            "top hits 2024", "popular music", "trending songs",
            "indie pop", "electronic chill", "lo-fi beats"
        )

        val candidates = mutableListOf<TrackResponse>()
        for (q in popularQueries.take(4)) {
            candidates += safeSearch(q, MusicService.YANDEX)
            candidates += safeSearch(q, MusicService.VK)
        }

        return candidates
            .filter { it.isPlayable() }
            .distinctBy { it.waveKey() }
            .shuffled()
            .take(amount)
    }

    // ═══════════════════════════════════════════════════════════
    //                  SCORING
    // ═══════════════════════════════════════════════════════════

    private fun scoreTrack(
        track: TrackResponse,
        seedArtist: String,
        topArtists: Set<String>,
        seedTitle: String
    ): Double {
        var score = 0.0
        val a = canonicalArtist(track.artist)
        val t = canonicalTitle(track.title)

        // Топ-артист профиля → +5
        if (a.isNotBlank() && a in topArtists) score += 5.0

        // Seed-артист → +3
        if (seedArtist.isNotBlank() && a == seedArtist) score += 3.0

        // Похожее название → +2
        if (seedTitle.isNotBlank() && t.isNotBlank()) {
            val seedWords = seedTitle.wordsSet()
            val overlap = t.wordsSet().intersect(seedWords).size
            score += overlap * 0.5
        }

        // История пропусков → штраф
        val skips = store.skipCount(track.waveKey())
        score -= skips * 1.5

        // Случайность для разнообразия
        score += Random.nextDouble(0.0, 1.0)

        return score
    }

    // ═══════════════════════════════════════════════════════════
    //                  HELPERS
    // ═══════════════════════════════════════════════════════════



    private suspend fun safeSearch(q: String, service: MusicService): List<TrackResponse> {
        return try {
            repo.search(service = service, query = q)
                .map { tr -> tr.copy(source = service.key) }
        } catch (e: HttpException) {
            val err = e.response()?.errorBody()?.string()
            android.util.Log.e(
                "WaveGen",
                "safeSearch HTTP ${e.code()} service=${service.key} q='$q' body=$err",
                e
            )
            emptyList()
        } catch (t: Throwable) {
            android.util.Log.e("WaveGen", "safeSearch FAIL service=${service.key} q='$q': ${t.message}", t)
            emptyList()
        }
    }



    private fun extractCoArtists(
        candidates: List<TrackResponse>,
        seedArtist: String,
        topArtists: Set<String>
    ): List<String> {
        val coArtistCounts = HashMap<String, Int>()
        for (tr in candidates) {
            extractCoArtistsRaw(tr.artist).forEach { a ->
                if (a.isNotBlank() && a != seedArtist && a !in topArtists) {
                    coArtistCounts[a] = (coArtistCounts[a] ?: 0) + 1
                }
            }
        }
        return coArtistCounts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(5)
    }

    private fun extractCoArtistsRaw(artistRaw: String?): List<String> {
        val raw = (artistRaw ?: "").lowercase()
        if (raw.isBlank()) return emptyList()

        val cleaned = raw.replace("ё", "е")
        return cleaned
            .split(" feat. ", " feat ", " ft. ", " ft ", " featuring ", " при участии ", " & ", " x ", ", ")
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(4)
            .drop(1)
            .map { canonicalArtist(it) }
            .filter { it.isNotBlank() }
    }

    private fun extractStrongWord(title: String): String? {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "with", "at", "by",
            "love", "night", "day", "remix", "mix", "edit", "radio", "version", "live", "remastered",
            "feat", "ft", "featuring",
            "и", "а", "но", "на", "в", "во", "к", "ко", "у", "от", "за", "про", "без", "для", "по",
            "ты", "я", "мы", "вы", "он", "она", "они",
            "ремикс", "микс", "версия"
        )

        return title
            .replace(Regex("[^a-z0-9а-яё\\s]"), " ")
            .lowercase()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .firstOrNull { it.length >= 5 && it !in stopWords }
    }

    private fun titleCore(tr: TrackResponse): String {
        val t = canonicalTitle(tr.title)
        return t.split(" ").take(6).joinToString(" ")
    }
}

// ════════════════════════════════════════════════════════════
//                     WAVE MODE
// ════════════════════════════════════════════════════════════



// ════════════════════════════════════════════════════════════
//                     EXTENSIONS
// ════════════════════════════════════════════════════════════

private fun TrackResponse.isPlayable(): Boolean =
    !this.preview.isNullOrBlank()

private fun TrackResponse.isAllowedSource(): Boolean {
    val s = (this.source ?: "").lowercase().trim()
    return s == "yandex" || s == "vk" || s == "vkmusic"
}


private fun String.normalizeQuery(): String =
    trim().replace(Regex("\\s+"), " ")

private fun String.wordsSet(): Set<String> =
    split(" ").map { it.trim() }.filter { it.length >= 3 }.toSet()