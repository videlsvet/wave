package com.wave.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wave.app.network.TrackResponse

@Composable
fun TrackListSoundCloud(
    tracks: List<TrackResponse>?,
    onPlay: (String) -> Unit
) {
    // ✅ Проверка на null или пустой список
    if (tracks.isNullOrEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Нет треков SoundCloud", color = Color.White)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        tracks.forEach { t ->
            TrackCard(
                imageUrl = t.image ?: "",
                title = t.title ?: "Без названия",
                subtitle = t.artist ?: "",
                onPlay = { if (!t.preview.isNullOrBlank()) onPlay(t.preview) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
