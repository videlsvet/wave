package com.wave.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.wave.app.R
import com.wave.app.model.PlayingTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ✅ Карусель prev/current/next
 * - Ушки торчат больше
 * - Реальное "замещение": анимация уезда/заезда -> потом смена трека
 * - ✅ НЕТ вспышки цвета: цвет берётся из кэша + префетч prev/next
 */
@Composable
fun PlayerBar(
    track: PlayingTrack,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    prevTrack: PlayingTrack? = null,
    nextTrack: PlayingTrack? = null,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val peekDp = 10.dp
    val gapDp = 12.dp

    val cardHeight = 68.dp
    val shapeOuter = RoundedCornerShape(22.dp)
    val shapeCard = RoundedCornerShape(20.dp)

    // ✅ state cache (перерисует UI, когда цвет готов)
    val colorCache = remember { mutableStateMapOf<String, Color>() }

    suspend fun ensureColor(url: String?): Color? {
        val u = url?.takeIf { it.isNotBlank() } ?: return null
        colorCache[u]?.let { return it }
        val c = computeDominantColor(context, u)
        colorCache[u] = c
        return c
    }

    // лёгкий префетч в фоне
    LaunchedEffect(track.image, prevTrack?.image, nextTrack?.image) {
        listOfNotNull(
            track.image.takeIf { it.isNotBlank() },
            prevTrack?.image?.takeIf { it.isNotBlank() },
            nextTrack?.image?.takeIf { it.isNotBlank() }
        ).distinct().forEach { url ->
            if (!colorCache.containsKey(url)) {
                launch { colorCache[url] = computeDominantColor(context, url) }
            }
        }
    }

    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val cardWidth = maxWidth - (peekDp * 2)
        val density = LocalDensity.current
        val neighborShiftPx = with(density) {
            (cardWidth.toPx() - peekDp.toPx() + gapDp.toPx()).coerceAtLeast(1f)
        }

        val prevBaseX = -neighborShiftPx
        val currBaseX = 0f
        val nextBaseX = +neighborShiftPx

        fun x(base: Float) = base + offset.value

        val distanceThreshold = neighborShiftPx * 0.22f
        val velocityThreshold = 1050f
        val maxDrag = neighborShiftPx * 0.65f

        fun applyEdgeResistance(delta: Float): Float {
            val newVal = offset.value + delta
            val atLeftEdge = prevTrack == null && newVal > 0f
            val atRightEdge = nextTrack == null && newVal < 0f
            return if (atLeftEdge || atRightEdge) delta * 0.30f else delta
        }

        suspend fun animateBack() {
            offset.animateTo(
                0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 320f)
            )
        }

        suspend fun animateSwitchToNext() {
            if (switching || nextTrack == null) return
            switching = true

            // ✅ ГАРАНТИЯ: цвет next уже посчитан ДО анимации/переключения
            ensureColor(nextTrack.image)

            offset.animateTo(
                targetValue = -nextBaseX,
                animationSpec = tween(210, easing = FastOutSlowInEasing)
            )

            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onNext()

            offset.snapTo(0f)
            switching = false
        }

        suspend fun animateSwitchToPrev() {
            if (switching || prevTrack == null) return
            switching = true

            // ✅ ГАРАНТИЯ: цвет prev уже посчитан ДО анимации/переключения
            ensureColor(prevTrack.image)

            offset.animateTo(
                targetValue = -prevBaseX,
                animationSpec = tween(210, easing = FastOutSlowInEasing)
            )

            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPrevious()

            offset.snapTo(0f)
            switching = false
        }

        val dragState = rememberDraggableState { rawDelta ->
            if (switching) return@rememberDraggableState
            dragging = true
            val delta = applyEdgeResistance(rawDelta)
            scope.launch {
                if (offset.isRunning) offset.stop()
                offset.snapTo((offset.value + delta).coerceIn(-maxDrag, maxDrag))
            }
        }

        val swipeModifier = Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = dragState,
            onDragStopped = { velocity ->
                if (switching) return@draggable
                dragging = false

                val xNow = offset.value
                val goNext = ((xNow <= -distanceThreshold) || (velocity < -velocityThreshold)) && nextTrack != null
                val goPrev = ((xNow >= distanceThreshold) || (velocity > velocityThreshold)) && prevTrack != null

                scope.launch {
                    when {
                        goNext -> animateSwitchToNext()
                        goPrev -> animateSwitchToPrev()
                        else -> animateBack()
                    }
                }
            }
        )

        fun neighborAlpha(base: Float): Float {
            val dx = abs(x(base))
            val t = (1f - (dx / neighborShiftPx)).coerceIn(0f, 1f)
            return 0.50f + t * 0.50f
        }

        fun neighborScale(base: Float): Float {
            val dx = abs(x(base))
            val t = (1f - (dx / neighborShiftPx)).coerceIn(0f, 1f)
            return 0.93f + t * 0.05f
        }

        val safeExpand: () -> Unit = { if (!dragging && !switching) onExpand() }
        val safePlayPause: () -> Unit = { if (!dragging && !switching) onPlayPause() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .then(swipeModifier),
            contentAlignment = Alignment.Center
        ) {
            // PREV
            if (prevTrack != null) {
                SinglePlayerBar(
                    track = prevTrack,
                    bgColor = colorCache[prevTrack.image],
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = 1L,
                    onPlayPause = { },
                    onExpand = safeExpand,
                    isFavorite = false,
                    onToggleFavorite = null,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .offset { IntOffset(x(prevBaseX).roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = 1f // ✅ больше нет затемнения
                            scaleX = 1f
                            scaleY = 1f
                        }
                        .clip(shapeCard),
                    shapeOuter = shapeOuter
                )
            }


            // NEXT
            if (nextTrack != null) {
                SinglePlayerBar(
                    track = nextTrack,
                    bgColor = colorCache[nextTrack.image],
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = 1L,
                    onPlayPause = { },
                    onExpand = safeExpand,
                    isFavorite = false,
                    onToggleFavorite = null,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .offset { IntOffset(x(nextBaseX).roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = 1f // ✅ больше нет затемнения
                            scaleX = 1f
                            scaleY = 1f
                        }
                        .clip(shapeCard),
                    shapeOuter = shapeOuter
                )
            }


            SinglePlayerBar(
                track = track,
                bgColor = colorCache[track.image],
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onPlayPause = safePlayPause,
                onExpand = safeExpand,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .offset { IntOffset(x(currBaseX).roundToInt(), 0) }
                    .graphicsLayer {
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                    }
                    .clip(shapeCard),
                shapeOuter = shapeOuter
            )
        }
    }
}

@Composable
private fun SinglePlayerBar(
    track: PlayingTrack,
    bgColor: Color?, // ✅ приходит сразу из кэша
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shapeOuter: RoundedCornerShape = RoundedCornerShape(22.dp)
) {
    // ✅ НИКАКИХ локальных стейтов/эффектов для фона -> нет кадра с "дефолтом"
    val safeBg = bgColor ?: Color(0xFF111111)

    val rawProgress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(550, easing = LinearEasing),
        label = "bar_progress_single"
    )

    val shapeCard = RoundedCornerShape(20.dp)
    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shapeOuter)
            .shadow(18.dp, shapeOuter)
            .background(safeBg)
            .drawBehind {
                drawRect(
                    color = Color.White.copy(alpha = 0.10f),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width * progress,
                        height = size.height
                    )
                )
            }
            .clickable(interactionSource = noRipple, indication = null) { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        CurrentBarCard(
            t = track,
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier.fillMaxSize().clip(shapeCard)
        )
    }
}



@Composable
private fun CurrentBarCard(
    t: PlayingTrack,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val playInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .background(Color.Black.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✅ Обложка + fallback
        var loadFailed by remember(t.image) { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (!loadFailed && t.image.isNotBlank()) {
                AsyncImage(
                    model = t.image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { loadFailed = true }
                )
            } else {
                Text(
                    text = "Нет\nобложки",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 2
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = t.title.ifBlank { " " },
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = t.artist.ifBlank { " " },
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // ✅ SOURCE: всегда занимает место, не "появляется" с задержкой
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            SourceBadge(t.source) // если внутри res=null -> просто ничего не нарисует
        }

        // ✅ LIKE: место есть всегда, просто становится видимой/активной когда доступно
        Spacer(Modifier.width(6.dp))
        val likeEnabled = onToggleFavorite != null
        IconButton(
            onClick = { onToggleFavorite?.invoke() },
            enabled = likeEnabled,
            modifier = Modifier.alpha(if (likeEnabled) 1f else 0f)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF55AA) else Color.White
            )
        }
        Spacer(Modifier.width(2.dp))

        // ▶️ / ⏸️
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = playInteraction,
                    indication = null
                ) { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}


@Composable
private fun SourceBadge(source: String?) {
    val s = (source ?: "").lowercase().trim()

    val res = when (s) {
        "yandex", "itunes" -> R.drawable.ic_src_yandex
        "vk", "vkmusic" -> R.drawable.ic_src_vk
        "spotify" -> R.drawable.ic_src_spotify
        "soundcloud" -> R.drawable.ic_src_soundcloud
        "favorites" -> R.drawable.ic_favorite
        else -> null
    }

    Box(
        modifier = Modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (res != null) {
            Image(
                painter = painterResource(res),
                contentDescription = s,
                modifier = Modifier.fillMaxSize().alpha(0.95f)
            )
        } else {
            // placeholder — чтобы не "всплывало"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.0f) // можно 0.15f если хочешь видеть заглушку
            )
        }
    }
}


/* ---------- Color compute + helpers ---------- */

private suspend fun computeDominantColor(context: android.content.Context, url: String): Color =
    withContext(Dispatchers.Default) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()

            val result = loader.execute(request) as? SuccessResult
                ?: return@withContext Color(0xFF111111)

            val bmp = result.drawable.toBitmap()
            val palette = Palette.Builder(bmp).generate()
            val colorInt = palette.getDominantColor(0xFF111111.toInt())

            Color(colorInt)
                .ensureNotTooBright()
                .darken(0.08f)
        } catch (_: Exception) {
            Color(0xFF111111)
        }
    }

private fun Color.darken(amount: Float): Color =
    Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

private fun Color.ensureNotTooBright(): Color {
    val lum = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    return if (lum > 0.75f) this.darken(0.35f) else this
}
