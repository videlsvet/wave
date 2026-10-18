package com.wave.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class WaveProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("WaveProfile", Context.MODE_PRIVATE)

    // JSON: {artist: {score: Int, lastPlayed: Long}}
    private val KEY_ARTISTS = "artists_json"

    // JSON: {genre: score}
    private val KEY_GENRES = "genres_json"

    // JSON array: ["track_key1", "track_key2", ...]
    private val KEY_HISTORY = "history_json"

    // JSON: {track_key: timestamp}
    private val KEY_DISLIKES = "dislikes_json"

    // JSON: {track_key: timestamp}
    private val KEY_SKIPS = "skips_json"

    private val historyMax = 500
    private val dislikesMax = 200

    // ════════════════════════════════════════════════════════════
    //                     TRACK KEY
    // ════════════════════════════════════════════════════════════

    fun makeTrackKey(artistRaw: String?, titleRaw: String?): String {
        val a = canonicalArtist(artistRaw)
        val t = canonicalTitle(titleRaw)
        if (a.isBlank() && t.isBlank()) return ""
        return "$a—$t"
    }

    // ════════════════════════════════════════════════════════════
    //                     ARTISTS
    // ════════════════════════════════════════════════════════════

    fun topArtists(n: Int): List<String> {
        val map = readArtists()
        val now = System.currentTimeMillis()

        return map.entries
            .map { (artist, data) ->
                val score = data.optInt("score", 0)
                val lastPlayed = data.optLong("lastPlayed", 0)

                // Decay: -1 балл за каждые 7 дней без прослушивания
                val daysSince = (now - lastPlayed) / (1000 * 60 * 60 * 24)
                val decay = (daysSince / 7).toInt()
                val finalScore = (score - decay).coerceAtLeast(0)

                artist to finalScore
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    fun adjustArtist(artistRaw: String?, delta: Int) {
        val artist = canonicalArtist(artistRaw)
        if (artist.isBlank()) return

        val map = readArtists().toMutableMap()
        val data = map[artist] ?: JSONObject()

        val currentScore = data.optInt("score", 0)
        val newScore = (currentScore + delta).coerceIn(-50, 999)

        data.put("score", newScore)
        data.put("lastPlayed", System.currentTimeMillis())

        if (newScore <= 0) {
            map.remove(artist)
        } else {
            map[artist] = data
        }

        writeArtists(map)
    }

    fun onLikeTrack(artist: String?) = adjustArtist(artist, +3)
    fun onPlayTrack(artist: String?) = adjustArtist(artist, +1)
    fun onDislikeTrack(artist: String?) = adjustArtist(artist, -5)
    fun onSkipTrack(artist: String?) = adjustArtist(artist, -1)

    // ════════════════════════════════════════════════════════════
    //                     GENRES
    // ════════════════════════════════════════════════════════════

    fun topGenres(n: Int): List<String> {
        val map = readGenres()
        return map.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
    }

    fun adjustGenre(genre: String?, delta: Int) {
        if (genre.isNullOrBlank()) return
        val normalized = genre.lowercase().trim()

        val map = readGenres().toMutableMap()
        val cur = map[normalized] ?: 0
        val next = (cur + delta).coerceIn(-20, 999)

        if (next <= 0) map.remove(normalized) else map[normalized] = next
        writeGenres(map)
    }

    // ════════════════════════════════════════════════════════════
    //                     HISTORY
    // ════════════════════════════════════════════════════════════

    fun historyKeysLast(n: Int): Set<String> {
        val list = readHistory()
        if (list.isEmpty()) return emptySet()
        return list.takeLast(n.coerceAtLeast(0)).filter { it.isNotBlank() }.toSet()
    }

    fun markPlayed(trackKey: String) {
        if (trackKey.isBlank()) return
        val list = readHistory().toMutableList()
        list.add(trackKey)

        val dedup = list
            .asReversed()
            .distinct()
            .asReversed()
            .takeLast(historyMax)

        writeHistory(dedup)
    }

    // ════════════════════════════════════════════════════════════
    //                     DISLIKES
    // ════════════════════════════════════════════════════════════

    fun markDisliked(trackKey: String) {
        if (trackKey.isBlank()) return
        val map = readDislikes().toMutableMap()
        map[trackKey] = System.currentTimeMillis()

        // Оставляем только последние dislikesMax
        if (map.size > dislikesMax) {
            val sorted = map.entries.sortedBy { it.value }
            map.clear()
            sorted.takeLast(dislikesMax).forEach { map[it.key] = it.value }
        }

        writeDislikes(map)
    }

    fun isDisliked(trackKey: String): Boolean {
        return readDislikes().containsKey(trackKey)
    }

    // ════════════════════════════════════════════════════════════
    //                     SKIPS
    // ════════════════════════════════════════════════════════════

    fun markSkipped(trackKey: String) {
        if (trackKey.isBlank()) return
        val map = readSkips().toMutableMap()
        val count = map[trackKey] ?: 0
        map[trackKey] = count + 1
        writeSkips(map)
    }

    fun skipCount(trackKey: String): Int {
        return readSkips()[trackKey] ?: 0
    }

    // ════════════════════════════════════════════════════════════
    //                     PRIVATE I/O
    // ════════════════════════════════════════════════════════════

    private fun readArtists(): Map<String, JSONObject> {
        val s = prefs.getString(KEY_ARTISTS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(s)
            val out = mutableMapOf<String, JSONObject>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = obj.getJSONObject(k)
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun writeArtists(map: Map<String, JSONObject>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_ARTISTS, obj.toString()).apply()
    }

    private fun readGenres(): Map<String, Int> {
        val s = prefs.getString(KEY_GENRES, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(s)
            val out = mutableMapOf<String, Int>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = obj.optInt(k, 0)
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun writeGenres(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_GENRES, obj.toString()).apply()
    }

    private fun readHistory(): List<String> {
        val s = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val obj = JSONObject(s)
            val arr = obj.optJSONArray("items")
            if (arr == null) emptyList()
            else List(arr.length()) { i -> arr.optString(i, "") }.filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writeHistory(list: List<String>) {
        val obj = JSONObject()
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        obj.put("items", arr)
        prefs.edit().putString(KEY_HISTORY, obj.toString()).apply()
    }

    private fun readDislikes(): Map<String, Long> {
        val s = prefs.getString(KEY_DISLIKES, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(s)
            val out = mutableMapOf<String, Long>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = obj.optLong(k, 0)
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun writeDislikes(map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_DISLIKES, obj.toString()).apply()
    }

    private fun readSkips(): Map<String, Int> {
        val s = prefs.getString(KEY_SKIPS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(s)
            val out = mutableMapOf<String, Int>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = obj.optInt(k, 0)
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun writeSkips(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_SKIPS, obj.toString()).apply()
    }
}