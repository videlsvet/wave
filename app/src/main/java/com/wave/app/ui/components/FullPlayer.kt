package com.wave.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.wave.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.wave.app.model.PlayingTrack
import com.wave.app.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// Фирменный цвет
private val AccentViolet = Color(0xFFBB86FC)
private val BlackColor = Color.Black

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FullPlayer(
    track: PlayingTrack,
    isPlaying: Boolean,
    playlistTitle: String,
    positionMs: Long,
    durationMs: Long,
    bufferedPercent: Int,
    onPlayPause: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    showLyrics: Boolean,
    lyricsText: String,
    onToggleLyrics: () -> Unit,
    isShuffleOn: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    isFavorite: Boolean,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onToggleFavorite: () -> Unit,
    // ✅ подложка (соседние треки)
    nextCoverUrl: String? = null,
    prevCoverUrl: String? = null
) {
    /* ---------- Dominant Color (Same as PlayerBar) ---------- */

    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf(Color(0xFF111111)) }

    LaunchedEffect(track.image) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(track.image)
                .allowHardware(false)
                .build()

            val result = loader.execute(request) as? SuccessResult ?: return@LaunchedEffect
            val bmp = result.drawable.toBitmap()

            val palette = Palette.Builder(bmp).generate()
            val color = palette.getDominantColor(0xFF111111.toInt())

            dominantColor = Color(color)
                .ensureNotTooBright()
                .darken(0.08f)

        } catch (_: Exception) {
            dominantColor = Color(0xFF111111)
        }
    }

    val animatedBg by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "fullplayer_bg"
    )

    /* ---------- Prefetch соседних обложек ---------- */

    LaunchedEffect(nextCoverUrl) {
        val url = nextCoverUrl ?: return@LaunchedEffect
        try {
            ImageLoader(context).enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
            )
        } catch (_: Exception) {}
    }

    LaunchedEffect(prevCoverUrl) {
        val url = prevCoverUrl ?: return@LaunchedEffect
        try {
            ImageLoader(context).enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
            )
        } catch (_: Exception) {}
    }

    /* ---------- Swipe to Close (Vertical, whole screen) ---------- */

    var dragOffset by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    val backgroundAlpha = 1f


    /* ---------- COVER Swipe (Double-layer Stack + Slide) ---------- */

    var coverOffsetX by remember { mutableStateOf(0f) }
    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    var isCoverDragging by remember { mutableStateOf(false) }
    var isEnteringNewCover by remember { mutableStateOf(false) }


    // направление последнего “переключения” (для анимации входа)
    // -1 = NEXT (влево), +1 = PREV (вправо), 0 = авто/неизвестно
    var lastSwitchDir by remember { mutableStateOf(0) }

    // входящая анимация для новой обложки
    val enterOffsetAnim = remember { Animatable(0f) }
    val enterAlphaAnim = remember { Animatable(1f) }
    val enterScaleAnim = remember { Animatable(1f) }

    // чуть плавнее при отпускании без “переключения”
    val coverOffsetXAnimated by animateFloatAsState(
        targetValue = coverOffsetX,
        animationSpec = if (isCoverDragging)
            tween(40, easing = LinearEasing) // лёгкая “инерция” вместо snap()
        else
            spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessVeryLow),
        label = "cover_offset_anim"
    )


    // ✅ Когда реально сменился трек — делаем “въезд” новой обложки
    LaunchedEffect(track.image, coverSize) {
        val w = coverSize.width.toFloat().coerceAtLeast(1f)

        isEnteringNewCover = true   // ✅ гасим хвост свайпа
        coverOffsetX = 0f           // ✅ сброс интерактива сразу

        val startX = when (lastSwitchDir) {
            -1 -> +w * 0.35f // NEXT (свайп влево) -> новая приходит справа
            +1 -> -w * 0.35f // PREV (свайп вправо) -> новая приходит слева
            else -> 0f
        }

        enterOffsetAnim.snapTo(startX)
        enterAlphaAnim.snapTo(if (startX == 0f) 1f else 0.75f)
        enterScaleAnim.snapTo(if (startX == 0f) 1f else 0.98f)

        // старт анимаций как у тебя...
        launch { enterOffsetAnim.animateTo(0f, spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessVeryLow)) }
        launch { enterAlphaAnim.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch { enterScaleAnim.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }

        // ✅ через 1 кадр/чуть-чуть возвращаем интерактив
        launch {
            delay(32)
            isEnteringNewCover = false
            delay(450)
            lastSwitchDir = 0
    }
}

    val enterY = remember { Animatable(1f) }   // 1 = ниже экрана, 0 = на месте
    val enterA = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // старт: экран "ниже"
        enterY.snapTo(1f)
        enterA.snapTo(0f)

        // fade-in быстро
        launch { enterA.animateTo(1f, tween(180, easing = LinearOutSlowInEasing)) }

        // вылет снизу вверх с лёгким "доприземлением"
        enterY.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.90f,          // меньше -> больше "подпрыгивание"
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // 1 = экран ниже, 0 = на месте
                val h = size.height
                translationY = enterY.value * h
                alpha = enterA.value
            }
            .offset(y = dragOffset.dp)
            .background(animatedBg.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, drag ->
                        dragOffset = (dragOffset + drag * 0.4f).coerceIn(0f, 600f)
                    },
                    onDragEnd = {
                        if (dragOffset >= 160f) onClose()
                        else scope.launch {
                            val anim = Animatable(dragOffset)
                            anim.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) { dragOffset = value }
                        }
                    }
                )
            }
    ) {


    // Лёгкое затемнение для читаемости
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

        /* ---------- CONTENT ---------- */

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(top = 90.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            /* ----- Drag Handle ----- */

            Box(
                Modifier
                    .width(45.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x55FFFFFF))
            )

// ✅ НОВОЕ: название плейлиста сверху
            Spacer(Modifier.height(12.dp))
            Text(
                text = playlistTitle,
                color = Color.White.copy(0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp)) // можно 16-22 как нравится

            /* ----- Close Button ----- */


            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                    tint = Color.White,
                    contentDescription = "Close",
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            /* ----- COVER / LYRICS Switch ----- */

            val coverShape = RoundedCornerShape(22.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .onSizeChanged { coverSize = it }

                    // ✅ Рамки/тень только для обложки
                    .then(
                        if (!showLyrics) {
                            Modifier
                                .shadow(10.dp, coverShape)
                                .clip(coverShape)
                        } else {
                            Modifier // без рамок
                        }
                    )

                    // ✅ Swipe only on cover (no conflicts with slider/buttons)
                    .pointerInput(showLyrics, coverSize) {
                        if (showLyrics) return@pointerInput

                        detectHorizontalDragGestures(
                            onDragStart = { isCoverDragging = true },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consumePositionChange()
                                val max = coverSize.width * 0.45f
                                coverOffsetX = (coverOffsetX + dragAmount).coerceIn(-max, max)
                            },
                            onDragEnd = {
                                isCoverDragging = false

                                val w = coverSize.width.toFloat().coerceAtLeast(1f)
                                val threshold = coverSize.width * 0.18f

                                scope.launch {
                                    if (abs(coverOffsetX) >= threshold) {
                                        val dir = coverOffsetX.sign.takeIf { it != 0f } ?: -1f

                                        lastSwitchDir = if (dir > 0f) +1 else -1

                                        val anim = Animatable(coverOffsetX)
                                        anim.animateTo(
                                            targetValue = dir * w,
                                            animationSpec = tween(
                                                durationMillis = 260,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) { coverOffsetX = value }

                                        lastSwitchDir = if (dir < 0f) -1 else +1

                                        if (dir > 0) onPrevious() else onNext()

                                        coverOffsetX = 0f
                                    } else {
                                        val anim = Animatable(coverOffsetX)
                                        anim.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.95f,
                                                stiffness = Spring.StiffnessVeryLow
                                            )
                                        ) { coverOffsetX = value }
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        val d = 600
                        if (targetState)
                            (fadeIn(tween(d)) + slideInVertically { it / 3 }) togetherWith
                                    (fadeOut(tween(d)) + slideOutVertically { -it / 3 })
                        else
                            (fadeIn(tween(d)) + slideInVertically { -it / 3 }) togetherWith
                                    (fadeOut(tween(d)) + slideOutVertically { it / 3 })
                    },
                    label = "lyrics_switch"
                ) { show ->
                    if (show) {
                        SmartLyricsView(
                            track = track,
                            lyricsText = lyricsText,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeekToFraction = onSeekToFraction
                        )
                    } else {

                    // ---- Double-layer Stack + Slide ----

                        val w = coverSize.width.toFloat().coerceAtLeast(1f)
                        val dragProgress = (abs(coverOffsetXAnimated) / (w * 0.45f)).coerceIn(0f, 1f)

                        // Направление текущего “живого” свайпа
                        val swipeDir = coverOffsetXAnimated.sign

// ✅ если сейчас не тащим (0), используем lastSwitchDir чтобы подложка стояла "правильно"
                        val effectiveDir = when {
                            swipeDir != 0f -> swipeDir
                            lastSwitchDir != 0 -> lastSwitchDir.toFloat()   // -1 (NEXT) или +1 (PREV)
                            else -> 0f
                        }

// ✅ Какая обложка "внизу"
                        val behindUrl = when {
                            effectiveDir > 0f -> prevCoverUrl   // PREV -> слева
                            effectiveDir < 0f -> nextCoverUrl   // NEXT -> справа
                            else -> (nextCoverUrl ?: prevCoverUrl)
                        } ?: track.image

                        val behindAlphaBase = 0.28f
                        val behindAlpha = (behindAlphaBase + dragProgress * 0.67f).coerceIn(behindAlphaBase, 0.95f)
                        val behindScale = 0.94f + dragProgress * 0.04f

// ✅ Подложка сдвигается в ту сторону, откуда должна “появляться”
                        val behindShift = when {
                            effectiveDir > 0f -> -w * (0.035f + 0.03f * dragProgress) // PREV слева
                            effectiveDir < 0f -> +w * (0.035f + 0.03f * dragProgress) // NEXT справа
                            else -> 0f
                        }


                        // Верхняя обложка: интерактивный свайп + входящая анимация
                        val topRotation = 0f
                        val topAlpha = 1f
                        val topScale = (1f - dragProgress * 0.02f) * enterScaleAnim.value

                        val interactiveX = if (isEnteringNewCover) 0f else coverOffsetXAnimated
                        val topTranslation = interactiveX + enterOffsetAnim.value


                        var loadFailed by remember { mutableStateOf(false) }

                        Box(Modifier.fillMaxSize()) {
                            // 1) Подложка (сосед)
                            if (!behindUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = behindUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = behindAlpha
                                            scaleX = behindScale
                                            scaleY = behindScale
                                            translationX = behindShift
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color(0x22FFFFFF))
                                )
                            }

                            // 2) Верхняя (текущая) — “карта” стопки
                            if (!loadFailed && track.image.isNotBlank()) {
                                AsyncImage(
                                    model = track.image,
                                    contentDescription = null,
                                    onError = { loadFailed = true },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = topTranslation
                                            rotationZ = topRotation
                                            alpha = topAlpha.coerceIn(0f, 1f)
                                            scaleX = topScale
                                            scaleY = topScale
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = topTranslation
                                            rotationZ = topRotation
                                            alpha = topAlpha.coerceIn(0f, 1f)
                                            scaleX = topScale
                                            scaleY = topScale
                                        }
                                        .background(Color(0xFF1A1A1A)), // ✅ плотный фон
                                            contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Не удалось\nзагрузить\nобложку",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            /* ----- Title & Artist (big tap area + logs) ----- */

// ✅ Обертка расширяет touch-area на всю ширину
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    // 👇 ЛОГ на любое касание (даже если clickable не сработает)
                    .pointerInput(track.title, track.albumId, track.source) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val anyDown = e.changes.any { it.pressed }
                                if (anyDown) {
                                    android.util.Log.d(
                                        "FullPlayerTap",
                                        "DOWN on TITLE area: title='${track.title}' source='${track.source}' albumId='${track.albumId}' ownerId='${track.ownerId}'"
                                    )
                                }
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        android.util.Log.d(
                            "FullPlayerTap",
                            "CLICK TITLE: title='${track.title}' source='${track.source}' albumId='${track.albumId}' ownerId='${track.ownerId}' accessKey='${track.accessKey}'"
                        )
                        onOpenAlbum()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.artist,
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        android.util.Log.d(
                            "FullPlayerTap",
                            "CLICK ARTIST: artist='${track.artist}' source='${track.source}' artistId='${track.artistId}'"
                        )
                        onOpenArtist()
                    }
                )

                Spacer(Modifier.width(8.dp))
                SourceBadge(track.source)
            }

            Spacer(Modifier.height(20.dp))




            /* ----- Progress Bar ----- */

            var scrubbing by remember { mutableStateOf(false) }
            val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

            val animatedFraction by animateFloatAsState(
                targetValue = progress,
                animationSpec = if (scrubbing) snap() else tween(300),
                label = "progress"
            )

            val buffered = bufferedPercent / 100f

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            ) {

                Box(Modifier.height(28.dp)) {

                    Box(
                        Modifier
                            .fillMaxWidth(buffered)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x33FFFFFF))
                            .align(Alignment.CenterStart)
                    )

                    Box(
                        Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        dominantColor.copy(.9f),
                                        dominantColor.copy(.7f)
                                    )
                                )
                            )
                            .align(Alignment.CenterStart)
                    )

                    Slider(
                        value = animatedFraction,
                        onValueChange = {
                            scrubbing = true
                            onSeekToFraction(it)
                        },
                        onValueChangeFinished = { scrubbing = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(positionMs), color = Color(0xCCFFFFFF))
                    Text(formatTime(durationMs), color = Color(0xCCFFFFFF))
                }
            }



            /* ----- MAIN CONTROLS ----- */

            fun prevWithDir() {
                lastSwitchDir = +1
                onPrevious()
            }

            fun nextWithDir() {
                lastSwitchDir = -1
                onNext()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                AnimatedControlButton(
                    active = repeatMode != RepeatMode.OFF,
                    icon = Icons.Default.Repeat,
                    onClick = onToggleRepeat,
                    color = AccentViolet,
                    showOne = repeatMode == RepeatMode.ONE,
                    size = 50.dp,
                    backgroundAlpha = 0f
                )

                ControlButton(
                    icon = Icons.Filled.SkipPrevious,
                    onClick = { prevWithDir() },
                    tintColor = Color.White,
                    backgroundColor = Color.Transparent,
                    size = 70.dp,
                    iconSize = 30.dp
                )

                ControlButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    onClick = onPlayPause,
                    large = true,
                    tintColor = BlackColor,
                    backgroundColor = AccentViolet
                )

                ControlButton(
                    icon = Icons.Filled.SkipNext,
                    onClick = { nextWithDir() },
                    tintColor = Color.White,
                    backgroundColor = Color.Transparent,
                    size = 70.dp,
                    iconSize = 30.dp
                )

                AnimatedControlButton(
                    active = isShuffleOn,
                    icon = Icons.Default.Shuffle,
                    onClick = onToggleShuffle,
                    color = AccentViolet,
                    size = 50.dp,
                    backgroundAlpha = 0f
                )
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable { onToggleLyrics() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "T",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.size(42.dp))
                Spacer(Modifier.size(42.dp))
                Spacer(Modifier.size(42.dp))
                Spacer(Modifier.size(42.dp))

                FavoriteButton(
                    isFavorite = isFavorite,
                    onClick = onToggleFavorite,
                    dominantColor = AccentViolet,
                    inactiveTint = Color.White.copy(0.9f),
                    inactiveBackground = Color.Transparent,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}
@Composable
fun SourceBadge(source: String) {
    val s = source.lowercase().trim()

    val res = when (s) {
        "yandex", "itunes" -> R.drawable.ic_src_yandex
        "vk", "vkmusic" -> R.drawable.ic_src_vk
        "spotify" -> R.drawable.ic_src_spotify
        "soundcloud" -> R.drawable.ic_src_soundcloud
        "favorites" -> R.drawable.ic_favorite
        else -> null
    }

    if (res == null) return

    Image(
        painter = painterResource(res),
        contentDescription = s,
        modifier = Modifier.size(16.dp)
    )
}


/* ---------- SUPPORTING COMPONENTS ---------- */

@Composable
private fun ControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    large: Boolean = false,
    tintColor: Color,
    backgroundColor: Color,
    size: Dp = if (large) 74.dp else 60.dp,
    iconSize: Dp = if (large) 36.dp else 28.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    dominantColor: Color,
    inactiveTint: Color,
    inactiveBackground: Color,
    modifier: Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isFavorite) dominantColor.copy(0.95f) else inactiveBackground,
        animationSpec = tween(200),
        label = "fav_bg"
    )
    val tint by animateColorAsState(
        targetValue = if (isFavorite) BlackColor else inactiveTint,
        animationSpec = tween(200),
        label = "fav_tint"
    )

    Box(
        modifier = modifier
            .size(50.dp)
            .background(bgColor, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun AnimatedControlButton(
    active: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color,
    showOne: Boolean = false,
    size: Dp,
    backgroundAlpha: Float
) {
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(120),
        label = "ctl_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (active) color.copy(alpha = 0.22f) else BlackColor.copy(alpha = backgroundAlpha),
        animationSpec = tween(200),
        label = "ctl_bg"
    )

    val tint by animateColorAsState(
        targetValue = if (active) color else Color.White.copy(0.7f),
        animationSpec = tween(200),
        label = "ctl_tint"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .background(bgColor, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )

        if (showOne) {
            Text(
                "1",
                color = tint,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(140)
            pressed = false
        }
    }
}
@Composable
private fun SmartLyricsView(
    track: PlayingTrack,
    lyricsText: String,
    positionMs: Long,
    durationMs: Long,
    onSeekToFraction: (Float) -> Unit
) {
    val isLrc = remember(lyricsText) {
        Regex("""\[\d{1,2}:\d{2}(\.\d{1,3})?]""").containsMatchIn(lyricsText)
    }

    if (isLrc) {
        SyncedLyricsView(
            track = track,
            lrcText = lyricsText,
            positionMs = positionMs,
            durationMs = durationMs,
            onSeekToFraction = onSeekToFraction
        )
    } else {
        LyricsView(track, lyricsText)
    }
}

private data class LyricLine(val timeMs: Long, val text: String)

private val timeTag = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

private fun parseLrc(lrc: String): List<LyricLine> {
    val out = mutableListOf<LyricLine>()
    var offsetMs = 0L

    lrc.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach

        // [offset:123]  (мс)
        if (line.startsWith("[offset:", ignoreCase = true)) {
            val v = line.substringAfter(":").substringBefore("]").trim()
            offsetMs = v.toLongOrNull() ?: 0L
            return@forEach
        }

        val matches = timeTag.findAll(line).toList()
        if (matches.isEmpty()) return@forEach

        val text = line.replace(timeTag, "").trim().ifBlank { " " }

        for (m in matches) {
            val mm = m.groupValues[1].toLong()
            val ss = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val ms = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100L      // .5 -> 500ms
                2 -> frac.toLong() * 10L       // .50 -> 500ms
                else -> frac.take(3).toLong()  // .500
            }

            val t = (mm * 60_000L) + (ss * 1_000L) + ms + offsetMs
            out += LyricLine(timeMs = t.coerceAtLeast(0), text = text)
        }
    }

    return out.sortedBy { it.timeMs }
}

private fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val t = lines[mid].timeMs
        if (t <= positionMs) lo = mid + 1 else hi = mid - 1
    }
    return hi.coerceIn(0, lines.lastIndex)
}

@Composable
private fun SyncedLyricsView(
    track: PlayingTrack,
    lrcText: String,
    positionMs: Long,
    durationMs: Long,
    onSeekToFraction: (Float) -> Unit
) {
    val lines = remember(lrcText) { parseLrc(lrcText) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val currentIndex by remember(lines, positionMs) {
        derivedStateOf { currentLineIndex(lines, positionMs) }
    }

    // ✅ увеличили высоту строки под новый размер текста
    val lineBoxHeightDp = 64.dp
    val lineBoxHeightPx = with(density) { lineBoxHeightDp.toPx() }

    var viewportHeightPx by remember { mutableStateOf(0) }

    val centerOffsetPx = remember(viewportHeightPx, lineBoxHeightPx) {
        ((viewportHeightPx / 2f) - (lineBoxHeightPx / 2f))
            .coerceAtLeast(0f)
            .toInt()
    }

    var lastAutoScrollTime by remember { mutableStateOf(0L) }

    // ✅ стабильное центрирование без дергания
    LaunchedEffect(currentIndex, viewportHeightPx) {
        if (currentIndex < 0 || viewportHeightPx == 0) return@LaunchedEffect
        if (listState.isScrollInProgress) return@LaunchedEffect

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAutoScrollTime < 140) return@LaunchedEffect
        lastAutoScrollTime = now

        listState.animateScrollToItem(
            index = currentIndex,
            scrollOffset = -centerOffsetPx
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(lines.size) { i ->
                val isActive = i == currentIndex

                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.55f,
                    animationSpec = tween(220, easing = LinearOutSlowInEasing),
                    label = "lyric_alpha"
                )

                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.08f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.92f,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "lyric_scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lineBoxHeightDp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val t = lines[i].timeMs.coerceAtLeast(0L)
                            val frac =
                                if (durationMs > 0) t.toFloat() / durationMs.toFloat() else 0f

                            onSeekToFraction(frac.coerceIn(0f, 1f))

                            scope.launch {
                                if (viewportHeightPx > 0) {
                                    listState.animateScrollToItem(
                                        index = i,
                                        scrollOffset = -centerOffsetPx
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lines[i].text,
                        color = Color.White.copy(alpha = alpha),
                        fontSize = if (isActive) 22.sp else 18.sp,   // ⭐ увеличенный текст
                        lineHeight = if (isActive) 30.sp else 26.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        )
                    )
                }
            }
        }
    }
}





@Composable
private fun LyricsView(track: PlayingTrack, text: String) {
    val scroll = rememberScrollState()

    // Нормализация строк + куплетные интервалы
    val blocks = remember(text) {
        text
            .replace("\r\n", "\n")
            .lines()
            .map { it.trimEnd() }
    }

    val headerStyle = SpanStyle(
        color = Color(0xFFD8B4FF),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    )

    val bodyStyle = SpanStyle(
        color = Color.White.copy(alpha = 0.95f)
    )

    val formatted = remember(blocks) {
        buildAnnotatedString {
            var emptyRun = 0

            for (ln in blocks) {
                val line = ln.trim()

                // Сжимаем множественные пустые строки (чтобы не было "простыней" воздуха)
                if (line.isBlank()) {
                    emptyRun++
                    if (emptyRun <= 1) append("\n\n\n") // ✅ больше воздуха между блоками
                    continue
                } else emptyRun = 0

                // Заголовки в квадратных скобках
                val isTag = line.startsWith("[") && line.endsWith("]") && line.length <= 28
                val isSectionWord = line.matches(Regex("(?i)^(intro|outro|verse|chorus|bridge|куплет|припев|бридж|интро|аутро)\\b.*"))

                when {
                    isTag || isSectionWord -> {
                        withStyle(headerStyle) { append(line) }
                        append("\n\n")
                    }
                    else -> {
                        withStyle(bodyStyle) { append(line) }
                        append("\n")
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ✅ Без контейнера: просто текст + мягкая “виньетка” для читаемости
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // верх/низ затемняем чуть-чуть, чтобы текст всегда читался
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.22f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f)
                            )
                        )
                    )
            )

            Text(
                text = formatted,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                fontSize = 17.sp,
                lineHeight = 28.sp,          // ✅ больше расстояние между строками
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal
            )
        }
    }
}


private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val sec = (ms / 1000).toInt()
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

/* ---------- Color helpers ---------- */

private fun Color.darken(amount: Float): Color =
    Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

private fun Color.ensureNotTooBright(): Color {
    val lum = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    return if (lum > 0.75f) this.darken(0.35f) else this
}
