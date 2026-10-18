package com.wave.app.data


internal fun canonicalArtist(raw: String?): String {
    var s = (raw ?: "").lowercase().trim()
    if (s.isBlank()) return ""
    s = s.replace("ё", "е")

    val splitters = listOf(" feat. ", " feat ", " ft. ", " ft ", " featuring ", " при участии ", " & ", " x ", ", ")
    for (sp in splitters) {
        val idx = s.indexOf(sp)
        if (idx > 0) { s = s.substring(0, idx); break }
    }

    return s.replace(Regex("[^a-z0-9а-я\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun canonicalTitle(raw: String?): String {
    var s = (raw ?: "").lowercase().trim()
    if (s.isBlank()) return ""
    s = s.replace("ё", "е")

    s = s.replace(Regex("\\b(feat\\.?|ft\\.?|featuring)\\b.*$"), " ").trim()

    s = s.replace(Regex("\\(([^)]*)\\)")) { m ->
        val inside = m.groupValues[1]
        if (inside.containsVersionWords()) " " else m.value
    }
    s = s.replace(Regex("\\[([^]]*)\\]")) { m ->
        val inside = m.groupValues[1]
        if (inside.containsVersionWords()) " " else m.value
    }

    s = s.replace(Regex("\\s-\\s(.+)$")) { m ->
        val tail = m.groupValues[1]
        if (tail.containsVersionWords()) " " else m.value
    }

    s = s.replace(
        Regex("\\b(remaster(ed)?|remastered|live|edit|radio edit|extended|mix|version|deluxe|bonus|demo|instrumental|acoustic|karaoke)\\b.*$"),
        " "
    )

    s = s.replace(Regex("\\b(19\\d{2}|20\\d{2})\\b"), " ")

    return s.replace(Regex("[^a-z0-9а-я\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.containsVersionWords(): Boolean {
    val x = lowercase()
    return listOf(
        "remix","mix","edit","radio","extended","version",
        "live","remaster","remastered","acoustic","demo",
        "instrumental","karaoke"
    ).any { x.contains(it) }
}
