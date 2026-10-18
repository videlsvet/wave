
package com.wave.app

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import android.content.Context
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.saveable.listSaver
import com.wave.app.ui.components.SourceBadge
import com.wave.app.ui.components.DiscBeatGlow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.basicMarquee
import com.wave.app.network.ArtistResponse
import com.wave.app.player.rememberAudioGlowState
import com.wave.app.ui.components.DiscBeatGlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextAlign
import com.wave.app.network.AlbumResponse
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.clipToBounds
import android.content.Intent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.draw.rotate
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.layout.ContentScale
import com.wave.app.data.WaveGenerator
import com.wave.app.data.WaveProfileStore
import com.wave.app.data.toPlayingTrack
import com.wave.app.network.TrackRepository
import androidx.compose.ui.res.painterResource
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.wave.app.model.PlayingTrack
import com.wave.app.model.Playlist
import com.wave.app.network.CloudflareServer
import com.wave.app.network.GeniusApi
import com.wave.app.network.LrcLibApi
import com.wave.app.network.TrackResponse
import com.wave.app.network.WaveServer
import com.wave.app.player.ExoPlayerHolder
import com.wave.app.player.PreviewPlayer
import com.wave.app.player.RepeatMode
import com.wave.app.ui.components.FullPlayer
import com.wave.app.ui.components.PlayerBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.compareTo

/* ──────────────────────────────────────────────── 🎨 UI tokens ──────────────────────────────────────────────── */


private object WaveDimens {
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val radiusS = 12.dp
    val radiusM = 16.dp
    val radiusL = 22.dp
}
// ──────────────────────────────── 🎨 Theme storage ────────────────────────────────

private const val PREFS_NAME = "WavePrefs"
private const val KEY_THEME = "app_theme"

private enum class WaveTheme(val title: String) {
    DARK("Тёмная"),
    NEON_PURPLE("Фиолетовая (неоновая)"),
    LIGHT("Белая"),
    OCEAN("Океан"),
    SUNSET("Закат")
}

private fun loadTheme(prefs: android.content.SharedPreferences): WaveTheme {
    val raw = prefs.getString(KEY_THEME, null)
    return WaveTheme.entries.firstOrNull { it.name == raw } ?: WaveTheme.DARK
}

private fun saveTheme(prefs: android.content.SharedPreferences, theme: WaveTheme) {
    prefs.edit().putString(KEY_THEME, theme.name).apply()
}
@Composable
private fun waveDarkScheme(): ColorScheme = darkColorScheme(
    // Accent (tech)
    primary = Color(0xFF7CF7FF),
    secondary = Color(0xFFB39DFF),
    tertiary = Color(0xFF7CFFB2),

    // ✅ OLED black stack (реально тёмная тема)
    background = Color(0xFF000000),
    surface = Color(0xFF05060A),
    surfaceVariant = Color(0xFF090B12),

    // Text
    onPrimary = Color(0xFF001014),
    onSecondary = Color(0xFF0B0620),
    onBackground = Color(0xFFEAF0FF),
    onSurface = Color(0xFFEAF0FF),
    onSurfaceVariant = Color(0xFF8F9AB8),

    // Lines
    outline = Color(0xFF1C2230)
)

@Composable
private fun waveNeonPurpleScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFE600FF),
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFF00E5FF),

    background = Color(0xFF050011),
    surface = Color(0xFF0B001A),
    surfaceVariant = Color(0xFF120028),

    onPrimary = Color(0xFF140014),
    onSecondary = Color(0xFF0E0620),
    onBackground = Color(0xFFF5E9FF),
    onSurface = Color(0xFFF5E9FF),
    onSurfaceVariant = Color(0xFFCAB8FF),

    outline = Color(0xFF2A1B4A)
)

@Composable
private fun waveLightScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF5B5BFF),
    secondary = Color(0xFF8A2BE2),
    tertiary = Color(0xFF00A3FF),

    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF0F8),

    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0E1220),
    onSurface = Color(0xFF0E1220),
    onSurfaceVariant = Color(0xFF5A6175),

    outline = Color(0xFFCDD3E2)
)

@Composable
private fun waveScheme(theme: WaveTheme): ColorScheme = when (theme) {
    WaveTheme.DARK -> waveDarkScheme()
    WaveTheme.NEON_PURPLE -> waveNeonPurpleScheme()
    WaveTheme.LIGHT -> waveLightScheme()
    WaveTheme.OCEAN -> darkColorScheme(
        primary = Color(0xFF2EE6FF),
        secondary = Color(0xFF00C2A8),
        tertiary = Color(0xFF7CA6FF),
        background = Color(0xFF030B10),
        surface = Color(0xFF06141B),
        surfaceVariant = Color(0xFF0A1C26),
        onPrimary = Color(0xFF001015),
        onSecondary = Color(0xFF00130F),
        onBackground = Color(0xFFE9FAFF),
        onSurface = Color(0xFFE9FAFF),
        onSurfaceVariant = Color(0xFF9CC4D1),
        outline = Color(0xFF1F3A46)
    )
    WaveTheme.SUNSET -> darkColorScheme(
        primary = Color(0xFFFF7A00),
        secondary = Color(0xFFFF2E88),
        tertiary = Color(0xFFFFE27A),
        background = Color(0xFF0B0506),
        surface = Color(0xFF120809),
        surfaceVariant = Color(0xFF1A0B10),
        onPrimary = Color(0xFF1A0B00),
        onSecondary = Color(0xFF20000F),
        onBackground = Color(0xFFFFF1F1),
        onSurface = Color(0xFFFFF1F1),
        onSurfaceVariant = Color(0xFFD7B7B7),
        outline = Color(0xFF3A2023)
    )
}

private object WaveAlpha {
    fun surfaceLow(cs: ColorScheme)  = cs.onSurface.copy(alpha = 0.05f)
    fun surfaceMid(cs: ColorScheme)  = cs.onSurface.copy(alpha = 0.08f)
    fun surfaceHigh(cs: ColorScheme) = cs.onSurface.copy(alpha = 0.12f)
    fun outlineSoft(cs: ColorScheme) = cs.outline.copy(alpha = 0.22f)
}

@Composable
private fun waveBackgroundBrush(theme: WaveTheme): Brush {
    val cs = MaterialTheme.colorScheme
    return when (theme) {
        WaveTheme.LIGHT -> Brush.verticalGradient(
            listOf(cs.background, Color(0xFFF2F4FF), Color(0xFFFFFFFF))
        )
        WaveTheme.NEON_PURPLE -> Brush.verticalGradient(
            listOf(cs.background, Color(0xFF120028), Color(0xFF000000))
        )
        else -> Brush.verticalGradient(
            listOf(
                Color(0xFF000000),
                Color(0xFF02030A),
                Color(0xFF000000)
            )
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    left: (@Composable (() -> Unit))? = null,
    right: (@Composable RowScope.() -> Unit)? = null,
    containerAlpha: Float = 0.78f,
    height: Dp = 54.dp
) {
    val cs = MaterialTheme.colorScheme
    val sideSlot = 48.dp
    val shape = RoundedCornerShape(18.dp)

    val techLine = Brush.horizontalGradient(
        listOf(
            cs.primary.copy(alpha = 0.00f),
            cs.primary.copy(alpha = 0.55f),
            cs.secondary.copy(alpha = 0.45f),
            cs.primary.copy(alpha = 0.00f)
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = cs.surface.copy(alpha = containerAlpha),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        shape = shape
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .border(1.dp, cs.outline.copy(alpha = 0.18f), shape)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.10f)
                            )
                        )
                    )
            )

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(techLine)
            )

            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(sideSlot), contentAlignment = Alignment.CenterStart) {
                    left?.invoke()
                }

                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(cs.primary.copy(alpha = 0.85f))
                    )
                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately
                        )
                    )
                }

                Box(Modifier.width(sideSlot), contentAlignment = Alignment.CenterEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        right?.invoke(this)
                    }
                }
            }
        }
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveBackTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    right: (@Composable RowScope.() -> Unit)? = null,
    containerAlpha: Float = 0.86f,
    height: Dp = 48.dp
) {
    WaveTopBar(
        title = title,
        modifier = modifier,
        height = height,
        containerAlpha = containerAlpha,
        left = {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
            }
        },
        right = right
    )
}



/* ──────────────────────────────────────────────── 🚀 MainActivity ──────────────────────────────────────────────── */

class MainActivity : ComponentActivity() {

    private lateinit var player: PreviewPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()

        // ✅ ВАЖНО: super.onCreate() должен быть ДО setOnExitAnimationListener на некоторых девайсах/темах,
        // чтобы вью сплэша инициализировалась корректно.
        super.onCreate(savedInstanceState)

        splash.setOnExitAnimationListener { provider ->
            // ✅ НЕ ТРОГАЕМ provider.iconView — он может кидать NPE внутри getIconView()
            val target = provider.view

            target.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(220)
                .withEndAction { provider.remove() }
                .start()
        }

        player = PreviewPlayer(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        setContent {
            val state = player.state.collectAsState().value

            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
            var theme by rememberSaveable { mutableStateOf(loadTheme(prefs)) }

            MaterialTheme(colorScheme = waveScheme(theme)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainNavigation(
                        player = player,

                        // ✅ ДОБАВИТЬ ЭТО
                        onOpenDetail = { detailState ->
                            // если у тебя есть navController — сюда вставляется navigation
                            // пока можно оставить пустым, чтобы собрать проект:
                        },

                        currentTrack = state.track,
                        isPlaying = state.isPlaying,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        bufferedPercent = state.bufferedPercent,
                        onPlayPause = { player.toggle() },
                        onSeekToFraction = { player.seekToFraction(it) },

                        theme = theme,
                        onThemeChange = { newTheme ->
                            theme = newTheme
                            saveTheme(prefs, newTheme)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}



/* ──────────────────────────────────────────────── 🧭 MainNavigation ──────────────────────────────────────────────── */

@Composable
private fun MainNavigation(
    player: PreviewPlayer,
    onOpenDetail: (DetailState) -> Unit,
    currentTrack: PlayingTrack?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPercent: Int,
    onPlayPause: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    theme: WaveTheme,
    onThemeChange: (WaveTheme) -> Unit
) {

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("WavePrefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

// ───────────── My Wave (Yandex + VK) ─────────────
    val waveStore = remember { WaveProfileStore(context) }
    val waveRepo = remember { TrackRepository() }
    val waveGen = remember { WaveGenerator(waveRepo, waveStore) }


// ✅ НОВЫЙ ViewModel для волны
    val waveViewModel = remember {
        com.wave.app.data.WaveViewModel(waveGen, waveStore)
    }
// ✅ Авто-обновление ссылки при смене трека (swipe / next / autoplay)
    LaunchedEffect(player) {
        player.setOnTrackChangedListener { track ->
            track ?: return@setOnTrackChangedListener

            val src = (track.source ?: "").lowercase()
            val service = if (src == "itunes") "yandex" else src

            onOpenDetail(
                DetailState.Track(
                    service = service,
                    artistId = track.artistId,
                    albumId = track.albumId
                )
            )
        }
    }

// ✅ отмечаем прослушивание (для профиля волны)
    LaunchedEffect(currentTrack?.title, currentTrack?.artist, currentTrack?.source) {
        val t = currentTrack ?: return@LaunchedEffect
        val key = waveStore.makeTrackKey(t.artist, t.title)
        waveStore.markPlayed(key)

        // ✅ Обратная связь для ViewModel
        val trackResp = TrackResponse(
            title = t.title,
            artist = t.artist,
            image = t.image,
            preview = t.url,
            source = t.source,
            externalId = t.externalId,
            artistId = t.artistId,
            albumId = t.albumId,
            ownerId = t.ownerId,
            accessKey = t.accessKey
        )
        waveViewModel.onTrackPlayed(trackResp)
    }
    var waveRunning by rememberSaveable { mutableStateOf(false) }
    var waveLoading by rememberSaveable { mutableStateOf(false) }
    var waveLoadingText by rememberSaveable { mutableStateOf("Готовим Мою Волну…") }

// Favorites — server
    var favorites by remember { mutableStateOf(listOf<TrackResponse>()) }
    var favoritesLoading by remember { mutableStateOf(false) }


// ✅ контекст для FullPlayer сверху
    var playlistContextTitle by rememberSaveable { mutableStateOf("Поиск") }

    fun startMyWave() {
        if (waveRunning || waveLoading) return

        waveLoading = true
        waveLoadingText = "Запускаем Мою Волну…"

        // можно сразу пометить как запущено, чтобы UI показал "Продолжить волну"
        waveRunning = true
        playlistContextTitle = "Моя волна"

        val seed = player.getCurrentTrack()

        scope.launch {
            try {
                waveLoadingText = "Собираем подборку…"

                waveViewModel.resetWave()
                waveViewModel.startWave(
                    seedArtist = seed?.artist,
                    seedTitle = seed?.title
                )

                waveLoadingText = "Ищем первые треки…"

                // ждём первые треки, но не бесконечно
                val tracks = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(15_000L) {
                        waveViewModel.waveFlow
                            .filter { it.isNotEmpty() }
                            .first()
                    } ?: emptyList()
                }

                if (tracks.isEmpty()) {
                    waveLoading = false
                    waveRunning = false
                    playlistContextTitle = "Поиск"
                    waveLoadingText = "Не удалось собрать волну 😕"
                    return@launch
                }

                waveLoadingText = "Запускаем воспроизведение…"

                val playingList = tracks
                    .filter { !it.preview.isNullOrBlank() }
                    .map { it.toPlayingTrack() }

                if (playingList.isEmpty()) {
                    waveLoading = false
                    waveRunning = false
                    playlistContextTitle = "Поиск"
                    waveLoadingText = "В волне нет доступных preview"
                    return@launch
                }

                player.setPlaylist(playingList, startIndex = 0)

                // ✅ вот тут волна "реально включилась"
                waveLoading = false

                // ✅ автодогрузка следующих треков (как было)
                scope.launch(Dispatchers.IO) {
                    while (waveRunning) {
                        val remaining = withContext(Dispatchers.Main) { player.remainingCount() }

                        if (remaining <= 12) {
                            waveViewModel.loadMoreWave()
                            kotlinx.coroutines.delay(500)

                            val newTracks = waveViewModel.waveFlow.value
                                .takeLast(20)
                                .filter { !it.preview.isNullOrBlank() }
                                .map { it.toPlayingTrack() }

                            if (newTracks.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    player.appendToPlaylist(newTracks)
                                }
                            }
                        }

                        kotlinx.coroutines.delay(900L)
                    }
                }

            } catch (e: Exception) {
                Log.e("Wave", "startMyWave error", e)
                waveLoading = false
                waveRunning = false
                playlistContextTitle = "Поиск"
                waveLoadingText = "Ошибка запуска волны"
            }
        }
    }


    fun stopMyWave() {
        waveRunning = false
        player.setOnTrackEndListener { }

        // ✅ если хочешь — после остановки волны вернём "Поиск"
        // (или можешь НЕ менять, если тебе удобнее оставлять "Моя волна")
        playlistContextTitle = "Поиск"
    }














    val cs = MaterialTheme.colorScheme
    val favoritesListState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }


    var selectedTab by rememberSaveable { mutableStateOf("home") } // home | search | favorites | profile
    var detail by remember { mutableStateOf<DetailState?>(null) }
    val tabBackStack = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    fun navigateToTab(tab: String) {
        if (selectedTab == tab) return
        tabBackStack.add(selectedTab)
        selectedTab = tab
    }



    fun splitArtists(artistLine: String?): List<String> {
        val s = (artistLine ?: "").trim()
        if (s.isBlank()) return emptyList()
        return s
            .replace(" feat. ", ", ", ignoreCase = true)
            .replace(" ft. ", ", ", ignoreCase = true)
            .replace(" & ", ", ")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun openAlbumFromPlaying(t: PlayingTrack) {
        val src = t.source.lowercase().trim()
        val service = when (src) {
            "itunes", "yandex" -> "yandex"
            "vk", "vkmusic" -> "vkmusic"
            else -> src
        }

        when (service) {
            "yandex" -> {
                if (t.albumId.isNullOrBlank()) return
                detail = DetailState.Album("yandex", t.albumId, null, null)
            }
            "vkmusic" -> {
                if (t.albumId.isNullOrBlank() || t.ownerId.isNullOrBlank()) return
                detail = DetailState.Album("vkmusic", t.albumId, t.ownerId, t.accessKey)
            }
        }
    }

    fun openArtistFromPlaying(t: PlayingTrack) {
        val src = t.source.lowercase().trim()
        val service = when (src) {
            "itunes", "yandex" -> "yandex"
            "vk", "vkmusic" -> "vkmusic"
            else -> src
        }
        if (service != "yandex" && service != "vkmusic") return

        val artists = splitArtists(t.artist)
        val name = artists.firstOrNull() ?: t.artist
        val id = if (artists.size <= 1) t.artistId else null

        detail = DetailState.Artist(service, id, name)
    }

    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var artistResp by remember { mutableStateOf<ArtistResponse?>(null) }
    var albumResp by remember { mutableStateOf<AlbumResponse?>(null) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(detail) {
        detailError = null
        artistResp = null
        albumResp = null

        val d = detail ?: return@LaunchedEffect
        detailLoading = true
        try {
            when (d) {
                is DetailState.Artist -> {
                    artistResp = WaveServer.api.getArtist(
                        service = d.service,
                        artistId = d.artistId,
                        artistName = d.artistName,
                        q = d.artistName
                    )
                }

                is DetailState.Album -> {
                    albumResp = WaveServer.api.getAlbum(
                        service = d.service,
                        albumId = d.albumId,
                        ownerId = d.ownerId,
                        accessKey = d.accessKey
                    )
                }

                is DetailState.Track -> {
                    // ✅ ничего не грузим (трек-экран не требует API)
                    // если захочешь — тут можно загрузить lyrics / похожее, но сейчас пусто
                }
            }
        } catch (t: Throwable) {
            detailError = t.message ?: "Ошибка загрузки"
        } finally {
            detailLoading = false
        }
    }



    // Player modes
    var isShuffleOn by rememberSaveable { mutableStateOf(false) }
    var repeatMode by rememberSaveable { mutableStateOf(RepeatMode.OFF) }

    // Lyrics
    var lyricsText by remember { mutableStateOf("🔍 Поиск текста...") }
    var showLyrics by remember { mutableStateOf(false) }

    // Auth / Profile
    var token by rememberSaveable { mutableStateOf(prefs.getString("user_token", null)) }
    var nickname by rememberSaveable { mutableStateOf(prefs.getString("nickname", null)) }
    var avatarUrl by rememberSaveable { mutableStateOf<String?>(prefs.getString("avatar_url", null)) }
    val isLoggedIn = token != null

    // Search
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPlaylist by rememberSaveable { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }




    var playlists by remember {
        mutableStateOf(
            listOf(
                Playlist(0, "Spotify", "spotify"),
                Playlist(1, "Я.Музыка", "itunes"),
                Playlist(2, "VK Music", "vkmusic"),
                Playlist(3, "SoundCloud", "soundcloud")
            )
        )
    }



    // Apply repeat & shuffle
    LaunchedEffect(repeatMode, isShuffleOn) {
        player.repeatMode = repeatMode
        player.isShuffleOn = isShuffleOn
    }

    // Lyrics auto-fetch (LRCLIB)
    LaunchedEffect(currentTrack) {
        if (currentTrack != null) {
            lyricsText = "🔍 Ищем текст..."
            lyricsText = try {
                LrcLibApi.fetchLyrics(
                    trackTitle = currentTrack.title,
                    artist = currentTrack.artist,
                )
            } catch (_: Exception) {
                "❌ Не удалось загрузить текст."
            }
        }
    }


    // -------- Favorites server actions --------//
    suspend fun loadFavoritesFromServer(onlyTrackId: String? = null): List<TrackResponse> {
        if (token.isNullOrBlank()) return emptyList()

        val wantsOne = !onlyTrackId.isNullOrBlank() // ✅ СНАЧАЛА

        if (!wantsOne) favoritesLoading = true      // ✅ теперь видно

        return try {
            val bodyObj = JSONObject()
                .put("action", "list_favorites")
                .put("token", token)

            if (wantsOne) {
                bodyObj.put("only_track_id", onlyTrackId)
                bodyObj.put("return_only_one", true)
            }

            val body = bodyObj
                .toString()
                .toRequestBody("application/json".toMediaType())

            val response = WaveServer.api.listFavorites(body).orEmpty()

            if (!wantsOne) {
                favorites = response
            } else {
                val updated = response.firstOrNull()
                if (updated != null) {
                    favorites = favorites.map {
                        if (it.id == updated.id) updated else it
                    }
                }
            }

            response
        } catch (e: Exception) {
            Log.e("Favorites", "Ошибка загрузки избранного", e)
            emptyList()
        } finally {
            if (!wantsOne) favoritesLoading = false // ✅ работает
        }
    }

    // ✅ Resolver для свайпов в избранном
    LaunchedEffect(Unit) {
        player.setUrlResolver { trackId ->
            val resp = loadFavoritesFromServer(trackId)
            resp.firstOrNull()?.preview
        }
    }






    suspend fun likeTrack(track: TrackResponse) {
        if (token.isNullOrBlank()) return
        try {
            waveStore.onLikeTrack(track.artist)
            waveViewModel.onTrackLiked(track)

            val src = (track.source ?: "").ifBlank { "unknown" }.lowercase()

            val trackJson = JSONObject().apply {
                put("title", track.title ?: "")
                put("artist", track.artist ?: "")
                put("image", track.image ?: "")
                put("preview", track.preview ?: "")
                put("source", src)
                put("id", track.externalId ?: "")

                // ✅ ДОБАВЛЯЕМ МЕТУ ДЛЯ ПЕРЕХОДОВ
                put("artist_id", track.artistId)
                put("album_id", track.albumId)
                put("album_title", track.albumTitle)
                put("album_year", track.albumYear)

                // ✅ VK extras (чтобы альбом/плейлист работал)
                put("owner_id", track.ownerId)
                put("audio_id", track.audioId)
                put("access_key", track.accessKey)
            }



            val body = JSONObject()
                .put("action", "like_track")
                .put("token", token)
                .put("track", trackJson)
                .toString()
                .toRequestBody("application/json".toMediaType())

            WaveServer.api.addFavorite(body)
            loadFavoritesFromServer()
        } catch (e: Exception) {
            Log.e("Favorites", "Ошибка лайка", e)
        }
    }

    suspend fun unlikeTrack(track: TrackResponse) {
        if (token.isNullOrBlank()) return
        try {
            waveStore.adjustArtist(track.artist, -3)

            // Если это элемент из favorites, track.id уже внутренний track_id.
            // Если из поиска — собираем internalId как на сервере: source:external_id (или source:title:artist)
            val internalId = if (!track.id.isNullOrBlank() && track.id!!.contains(":")) {
                track.id!!
            } else {
                val src = (track.source ?: "").ifBlank { "unknown" }.lowercase()
                val baseId = (track.externalId ?: "").ifBlank { "${track.title.orEmpty()}:${track.artist.orEmpty()}" }
                "$src:$baseId"
            }

            val body = JSONObject()
                .put("action", "remove_like")
                .put("token", token)
                .put("track_id", internalId)
                .toString()
                .toRequestBody("application/json".toMediaType())

            WaveServer.api.removeFavorite(body)
            loadFavoritesFromServer()
        } catch (e: Exception) {
            Log.e("Favorites", "Ошибка удаления лайка", e)
        }
    }


    // Load profile after login
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            scope.launch {
                val (nick, url) = fetchProfile(token)
                nickname = if (nick.isNotBlank()) nick else "Пользователь"
                prefs.edit().putString("nickname", nickname).apply()

                if (!url.isNullOrBlank()) {
                    avatarUrl = url
                    prefs.edit().putString("avatar_url", url).apply()
                }

                loadFavoritesFromServer()
            }
        } else {
            nickname = null
            avatarUrl = null
            favorites = emptyList()
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        val intent = Intent(context, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }

    fun nextTrack() {
        if (waveRunning && positionMs in 0..15_000L) {
            currentTrack?.let { track ->
                waveStore.adjustArtist(track.artist, -1)

                // ✅ ДОБАВЬ ЭТО
                val trackResp = TrackResponse(
                    title = track.title,
                    artist = track.artist,
                    image = track.image,
                    preview = track.url,
                    source = track.source,
                    externalId = track.externalId,
                    artistId = track.artistId,
                    albumId = track.albumId,
                    ownerId = track.ownerId,
                    accessKey = track.accessKey
                )
                waveViewModel.onTrackSkipped(trackResp)
            }
        }
        player.nextTrack()
    }


    fun previousTrack() {
        if (positionMs > 3000L) onSeekToFraction(0f) else player.previousTrack()
    }

    // Neighbor covers (correct with shuffle/repeat)
    val exo = remember { ExoPlayerHolder.player }
    val prevCoverUrl = remember(currentTrack, isShuffleOn, repeatMode) {
        val prevIdx = exo.previousMediaItemIndex
        if (prevIdx < 0 || prevIdx >= exo.mediaItemCount) null
        else exo.getMediaItemAt(prevIdx).mediaMetadata.artworkUri?.toString()
    }
    val nextCoverUrl = remember(currentTrack, isShuffleOn, repeatMode) {
        val nextIdx = exo.nextMediaItemIndex
        if (nextIdx < 0 || nextIdx >= exo.mediaItemCount) null
        else exo.getMediaItemAt(nextIdx).mediaMetadata.artworkUri?.toString()
    }
    val prevTrackLite = remember(currentTrack, isShuffleOn, repeatMode) {
        val idx = exo.previousMediaItemIndex
        if (idx < 0 || idx >= exo.mediaItemCount) null else PlayingTrack(
            title = exo.getMediaItemAt(idx).mediaMetadata.title?.toString() ?: "",
            artist = exo.getMediaItemAt(idx).mediaMetadata.artist?.toString() ?: "",
            image = exo.getMediaItemAt(idx).mediaMetadata.artworkUri?.toString() ?: "",
            url = "", source = "queue"
        )
    }

    val nextTrackLite = remember(currentTrack, isShuffleOn, repeatMode) {
        val idx = exo.nextMediaItemIndex
        if (idx < 0 || idx >= exo.mediaItemCount) null else PlayingTrack(
            title = exo.getMediaItemAt(idx).mediaMetadata.title?.toString() ?: "",
            artist = exo.getMediaItemAt(idx).mediaMetadata.artist?.toString() ?: "",
            image = exo.getMediaItemAt(idx).mediaMetadata.artworkUri?.toString() ?: "",
            url = "", source = "queue"
        )
    }
    val prevTrackPeek = remember(prevCoverUrl) {
        prevCoverUrl?.let {
            PlayingTrack(
                title = "",
                artist = "",
                image = it,
                url = "",
                source = ""
            )
        }
    }

    val nextTrackPeek = remember(nextCoverUrl) {
        nextCoverUrl?.let {
            PlayingTrack(
                title = "",
                artist = "",
                image = it,
                url = "",
                source = ""
            )
        }
    }



    // Bottom "glass" container that unifies NavigationBar + PlayerBar
    @Composable
    fun BottomChrome() {
        val cs = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

        // компактный “glass” без градиента
        val container = cs.surface.copy(alpha = 0.86f)
        val hairline = cs.outline.copy(alpha = 0.14f)

        val navItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = cs.primary,
            unselectedIconColor = cs.onSurfaceVariant,
            indicatorColor = cs.primary.copy(alpha = 0.10f) // было 0.14 — сделай мягче
        )


        // ✅ минимальные отступы + безопасная зона снизу
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = container,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // тонкая линия сверху
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(hairline)
                    )

                    // Mini player (очень компактно)
                    if (currentTrack != null) {
                        PlayerBar(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onPlayPause = onPlayPause,
                            onExpand = { isExpanded = true },

                            prevTrack = prevTrackLite,
                            nextTrack = nextTrackLite,
                            onPrevious = { previousTrack() },
                            onNext = { nextTrack() },

                            // ✅ лайк в мини-плеере
                            isFavorite = favorites.any {
                                (it.title ?: "") == currentTrack.title &&
                                        (it.artist ?: "") == currentTrack.artist
                            },
                            onToggleFavorite = {
                                if (!isLoggedIn) return@PlayerBar

                                val trackResp = favorites.find {
                                    (it.title ?: "") == currentTrack.title &&
                                            (it.artist ?: "") == currentTrack.artist
                                } ?: TrackResponse(
                                    title = currentTrack.title,
                                    artist = currentTrack.artist,
                                    image = currentTrack.image,
                                    preview = currentTrack.url,
                                    source = currentTrack.source,
                                    externalId = currentTrack.externalId,
                                    artistId = currentTrack.artistId,
                                    albumId = currentTrack.albumId,
                                    ownerId = currentTrack.ownerId,
                                    accessKey = currentTrack.accessKey
                                )

                                scope.launch {
                                    val isFavNow = favorites.any {
                                        (it.title ?: "") == currentTrack.title &&
                                                (it.artist ?: "") == currentTrack.artist
                                    }
                                    if (isFavNow) unlikeTrack(trackResp) else likeTrack(trackResp)
                                }
                            },

                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )





                        // разделитель
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(hairline)
                        )
                    }

                    // NavigationBar ниже — компактнее
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == "home",
                            onClick = {
                                detail = null
                                navigateToTab("home") },
                            icon = { Icon(Icons.Filled.Home, null) },
                            colors = navItemColors,
                            alwaysShowLabel = false
                        )

                        NavigationBarItem(
                            selected = selectedTab == "search",
                            onClick = { navigateToTab("search") },
                            icon = { Icon(Icons.Filled.Search, null) },
                            colors = navItemColors,
                            alwaysShowLabel = false
                        )

                        NavigationBarItem(
                            selected = selectedTab == "favorites",
                            onClick = { navigateToTab("favorites") },
                            icon = { Icon(Icons.Filled.Favorite, null) },
                            colors = navItemColors,
                            alwaysShowLabel = false
                        )

                        NavigationBarItem(
                            selected = selectedTab == "profile",
                            onClick = { navigateToTab("profile") },
                            icon = { Icon(Icons.Filled.Person, null) },
                            colors = navItemColors,
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        }
    }

    val activity = LocalContext.current as ComponentActivity

    BackHandler {
        when {
            detail != null -> detail = null                    // 1) закрыть detail
            tabBackStack.isNotEmpty() -> {                     // 2) вернуться на предыдущий таб
                selectedTab = tabBackStack.removeLast()
            }
            selectedTab != "home" -> selectedTab = "home"      // 3) если не home — вернуться на home
            else -> activity.finish()                          // 4) иначе выйти из приложения
        }
    }



    Scaffold(
        containerColor = cs.background,
        bottomBar = { if (!isExpanded) BottomChrome() }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(waveBackgroundBrush(theme))
                .padding(padding)
        ) {
            if (detail != null) {
                val currentService = when (val d = detail) {
                    is DetailState.Artist -> d.service
                    is DetailState.Album -> d.service
                    else -> "yandex"
                }
                DetailScreen(
                    loading = detailLoading,
                    error = detailError,
                    artist = artistResp,
                    album = albumResp,
                    currentTrack = currentTrack,
                    service = currentService,
                    onOpenDetail = { detail = it },
                    onBack = { detail = null },
                    onPlayTrack = { track ->
                        if (track.preview.isNullOrBlank()) return@DetailScreen

                        val list = (artistResp?.tracks ?: albumResp?.tracks ?: emptyList())
                            .filter { !it.preview.isNullOrBlank() }
                            .map {
                                PlayingTrack(
                                    title = it.title ?: "Без названия",
                                    artist = it.artist ?: "",
                                    image = it.image ?: "",
                                    url = it.preview!!,
                                    source = (it.source ?: "search").lowercase(),
                                    externalId = it.externalId,

                                    // ✅ ДОБАВЬ ЭТО
                                    artistId = it.artistId,
                                    albumId = it.albumId,
                                    ownerId = it.ownerId,
                                    accessKey = it.accessKey
                                )
                            }


                        val idx = list.indexOfFirst {
                            it.title == (track.title ?: "") && it.artist == (track.artist ?: "")
                        }.coerceAtLeast(0)

                        stopMyWave()
                        playlistContextTitle =
                            artistResp?.artist?.artistName?.let { "Артист: $it" }
                                ?: albumResp?.album?.albumTitle?.let { "Альбом: $it" }
                                        ?: "Подборка"
                        stopMyWave()
                        player.setPlaylist(list, idx)
                    },
                    favorites = favorites,
                    onToggleFavorite = { tr ->
                        if (!isLoggedIn) return@DetailScreen
                        scope.launch {
                            val isFav = favorites.any { it.title == tr.title && it.artist == tr.artist }
                            if (isFav) unlikeTrack(tr) else likeTrack(tr)
                        }
                    }
                )
                return@Box
            }

            when (selectedTab) {

                "home" -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onGoSearch = { navigateToTab("search") },
                    onStartMyWave = { startMyWave() },
                    waveRunning = waveRunning,
                    waveLoading = waveLoading,
                    waveLoadingText = waveLoadingText
                )

                "playlists" -> ImportFavoritesScreen(
                    token = token,
                    onBack = { selectedTab = "profile" },
                    onDoneOpenFavorites = {
                        selectedTab = "favorites"
                        if (isLoggedIn) scope.launch { loadFavoritesFromServer() } // ✅ ВОТ ЭТО ДОБАВЬ
                    }
                )



                "search" -> SearchScreen(
                    modifier = Modifier.fillMaxSize(),
                    player = player,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedPercent = bufferedPercent,
                    onPlayPause = onPlayPause,
                    onStopMyWave = { stopMyWave() },
                    onSeekToFraction = onSeekToFraction,
                    onExpand = { isExpanded = true },


                    query = query,
                    onQueryChange = { newValue: String -> query = newValue },

                    playlists = playlists,
                    onPlaylistsChange = { newList: List<Playlist> -> playlists = newList },

                    selectedPlaylist = selectedPlaylist,
                    onSelectPlaylist = { newIndex: Int -> selectedPlaylist = newIndex },

                    loading = loading,
                    onSetPlaylistContext = { playlistContextTitle = it },
                    onLoadingChange = { v: Boolean -> loading = v },

                    error = error,
                    onErrorChange = { e: String? -> error = e },

                    favorites = favorites,
                    onToggleFavorite = { track: TrackResponse ->
                        if (!isLoggedIn) return@SearchScreen
                        scope.launch {
                            val isFav = favorites.any {
                                (it.title ?: "") == (track.title ?: "") &&
                                        (it.artist ?: "") == (track.artist ?: "")
                            }
                            if (isFav) unlikeTrack(track) else likeTrack(track)
                        }
                    }
                )

                "favorites" -> FavoritesScreen(
                    listState = favoritesListState,
                    favorites = favorites,
                    loading = favoritesLoading,
                    onPlay = { track: TrackResponse ->
                        scope.launch {
                            // ✅ Обновим только этот трек (preview) и точечно обновим favorites
                            loadFavoritesFromServer(track.id)

                            // ✅ Для проигрывания используем весь текущий список favorites
                            val playable = favorites.filter { !it.preview.isNullOrBlank() }

                            val index = playable.indexOfFirst {
                                (it.title ?: "") == (track.title ?: "") &&
                                        (it.artist ?: "") == (track.artist ?: "")
                            }.coerceAtLeast(0)

                            val list = playable.map {
                                PlayingTrack(
                                    title = it.title ?: "",
                                    artist = it.artist ?: "",
                                    image = it.image ?: "",
                                    url = it.preview ?: "",
                                    source = (it.source ?: "favorites").lowercase(),
                                    externalId = it.externalId,

                                    // ✅ ДОБАВЬ ЭТО
                                    artistId = it.artistId,
                                    albumId = it.albumId,
                                    ownerId = it.ownerId,
                                    accessKey = it.accessKey,
                                    trackId = it.id
                                )
                            }

                            if (list.isNotEmpty()) {
                                stopMyWave()
                                playlistContextTitle = "Избранное"
                                player.setPlaylist(list, index)
                            }
                        }
                    },
                    isCurrent = { t: TrackResponse ->
                        currentTrack?.title == (t.title ?: "") && currentTrack?.artist == (t.artist ?: "")
                    },
                    isPlaying = isPlaying,
                    onOpenDetail = { detail = it },
                    onToggleFavorite = { t: TrackResponse ->
                        if (!isLoggedIn) return@FavoritesScreen
                        scope.launch { unlikeTrack(t) }
                    }
                )



                "profile" -> ProfileScreen(
                    modifier = Modifier.fillMaxSize(),
                    isLoggedIn = isLoggedIn,
                    username = nickname,
                    avatarUrl = avatarUrl,
                    onLogin = {
                        val intent = Intent(context, AuthActivity::class.java)
                        context.startActivity(intent)
                    },
                    onLogout = { logout() },
                    onAvatarUploaded = { newUrl ->
                        avatarUrl = newUrl
                        prefs.edit().putString("avatar_url", newUrl).apply()
                    },
                    onOpenPlaylists = {
                        selectedTab = "playlists"
                    },
                    onOpenFavorites = {
                        selectedTab = "favorites"
                        if (isLoggedIn) scope.launch { loadFavoritesFromServer() }
                    },
                    theme = theme,
                    onThemeChange = onThemeChange
                )
            }
        }
    }

    AnimatedVisibility(
        visible = isExpanded && currentTrack != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220))
    ) {
        val playlistTitle = when (currentTrack?.source?.lowercase()?.trim()) {
            "favorites" -> "Избранное"
            "wave", "mywave" -> "Моя волна"
            "spotify", "soundcloud", "itunes", "vkmusic", "yandex", "vk" -> "Поиск"
            else -> "Музыка"
        }

        FullPlayer(
            track = currentTrack!!,
            isPlaying = isPlaying,
            playlistTitle = playlistContextTitle,
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedPercent = bufferedPercent,
            onPlayPause = onPlayPause,
            onSeekToFraction = onSeekToFraction,
            onPrevious = { previousTrack() },
            onNext = { nextTrack() },
            onClose = { isExpanded = false },
            showLyrics = showLyrics,
            lyricsText = lyricsText,
            onToggleLyrics = { showLyrics = !showLyrics },
            isShuffleOn = isShuffleOn,
            repeatMode = repeatMode,
            onToggleShuffle = { isShuffleOn = !isShuffleOn },
            onToggleRepeat = {
                repeatMode = when (repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
            },
            isFavorite = favorites.any { it.title == currentTrack!!.title && (it.artist ?: "") == currentTrack!!.artist },
            onToggleFavorite = {
                val trackResp = favorites.find {
                    it.title == currentTrack!!.title && (it.artist ?: "") == currentTrack!!.artist
                } ?: TrackResponse(
                    title = currentTrack!!.title,
                    artist = currentTrack!!.artist,
                    image = currentTrack!!.image,
                    preview = currentTrack!!.url,
                    source = currentTrack!!.source,
                    externalId = currentTrack!!.externalId,
                    artistId = currentTrack!!.artistId,
                    albumId = currentTrack!!.albumId,
                    ownerId = currentTrack!!.ownerId,
                    accessKey = currentTrack!!.accessKey
                )


                scope.launch {
                    val isFavNow = favorites.any {
                        it.title == currentTrack!!.title && (it.artist ?: "") == currentTrack!!.artist
                    }
                    if (isFavNow) unlikeTrack(trackResp) else likeTrack(trackResp)
                }
            },
            onOpenAlbum = {
                val t = currentTrack ?: return@FullPlayer
                isExpanded = false
                openAlbumFromPlaying(t)
            },
            onOpenArtist = {
                val t = currentTrack ?: return@FullPlayer
                isExpanded = false
                openArtistFromPlaying(t)
            },
            nextCoverUrl = nextCoverUrl,
            prevCoverUrl = prevCoverUrl
        )
    }
}

/* ──────────────────────────────────────────────── 🏠 Home screen (Главная) ───────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    currentTrack: PlayingTrack?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onGoSearch: () -> Unit,
    onStartMyWave: () -> Unit,
    waveRunning: Boolean,
    waveLoading: Boolean,
    waveLoadingText: String
) {
    val cs = MaterialTheme.colorScheme

    // ✅ glow state (AudioGlowMeter внутри)
    val glow = rememberAudioGlowState()
    val level by glow.meter.level.collectAsState()
    val beatTick by glow.meter.beat.collectAsState()

    // ✅ attach/release Visualizer (ждём пока audioSessionId станет != 0)
    LaunchedEffect(isPlaying, currentTrack?.url) {
        if (!isPlaying) {
            glow.meter.release()
            return@LaunchedEffect
        }

        // ждём сессию (на некоторых девайсах она появляется чуть позже)
        repeat(30) { // ~30 * 50ms = 1.5s максимум
            val sessionId = com.wave.app.player.ExoPlayerHolder.player.audioSessionId
            if (sessionId != 0) {
                glow.meter.attach(sessionId)
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(50L)
        }
    }


    // ✅ release when screen leaves composition
    DisposableEffect(Unit) {
        onDispose { glow.meter.release() }
    }

    Box(modifier = modifier.fillMaxSize()) {

        Column(Modifier.fillMaxSize()) {

            // Header
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                cs.background.copy(alpha = 0.98f),
                                cs.background.copy(alpha = 0.90f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(
                        start = WaveDimens.m,
                        top = WaveDimens.s,
                        end = WaveDimens.m,
                        bottom = WaveDimens.s
                    )
            ) {
                WaveTopBar(
                    title = "Главная",
                    containerAlpha = 0.30f,
                    right = {
                        IconButton(onClick = onGoSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                    }
                )
            }

            // Content
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = WaveDimens.m)
                    .padding(bottom = 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                // Hero card (диск + CTA)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.30f)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        DiscBeatGlow(
                            level = if (isPlaying) level else 0f,
                            beatTick = beatTick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .padding(vertical = 6.dp)
                        ) {
                            MyWaveDisc(
                                spin = waveRunning && isPlaying,
                                enabled = !waveLoading,
                                onPlayClick = onStartMyWave
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = onStartMyWave,
                            enabled = !waveLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (waveRunning) "Продолжить волну" else "Запустить Мою Волну",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = if (waveRunning) "Подборка догружается автоматически" else "Подборка по твоим лайкам и избранному",
                            color = cs.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Overlay loading — оставляем как было
        AnimatedVisibility(
            visible = waveLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cs.surface.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Моя Волна", fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                            Text(waveLoadingText, fontSize = 13.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterTopAppBarTitleWithAction(
    title: String,
    onAction: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    TopAppBar(
        title = {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = cs.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            IconButton(onClick = onAction) {
                Icon(Icons.Filled.Search, contentDescription = "Поиск")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}


@Composable
fun DiscGlow(
    active: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val inf = rememberInfiniteTransition(label = "discGlow")

    val pulse by inf.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 1600 else 2600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = AnimRepeatMode.Reverse
        ),
        label = "pulse"
    )

    val baseAlpha by inf.animateFloat(
        initialValue = if (active) 0.22f else 0.14f,
        targetValue = if (active) 0.30f else 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 1400 else 2400,
                easing = LinearEasing
            ),
            repeatMode = AnimRepeatMode.Reverse
        ),
        label = "baseAlpha"
    )

    val a = if (loading) (baseAlpha + 0.06f).coerceAtMost(0.40f) else baseAlpha

    Box(
        modifier = modifier, // ✅ теперь позицию контролируем снаружи
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(292.dp)
                .scale(pulse)
                .alpha(a)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            cs.onSurface.copy(alpha = 0.28f),
                            cs.onSurface.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        radius = 520f
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(244.dp)
                .scale(pulse * 0.985f)
                .alpha(a * 0.85f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            cs.onSurface.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        radius = 420f
                    )
                )
        )

        content()
    }
}




// ─────────────────────── Detail state (Artist / Album) ───────────────────────
sealed interface DetailState {

    data class Artist(
        val service: String,
        val artistId: String?,
        val artistName: String?
    ) : DetailState

    data class Album(
        val service: String,
        val albumId: String?,
        val ownerId: String?,
        val accessKey: String?
    ) : DetailState

    // ✅ ДОБАВИЛИ
    data class Track(
        val service: String,
        val artistId: String? = null,
        val albumId: String? = null
    ) : DetailState
}



/* ──────────────────────────────────────────────── 🔎 Search screen (Поиск) ───────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    player: PreviewPlayer,
    currentTrack: PlayingTrack?,
    isPlaying: Boolean,
    positionMs: Long,
    onStopMyWave: () -> Unit,
    durationMs: Long,
    bufferedPercent: Int,
    onPlayPause: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onExpand: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    playlists: List<Playlist>,
    onPlaylistsChange: (List<Playlist>) -> Unit,
    selectedPlaylist: Int,
    onSetPlaylistContext: (String) -> Unit,
    onSelectPlaylist: (Int) -> Unit,
    loading: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    error: String?,
    onErrorChange: (String?) -> Unit,
    favorites: List<TrackResponse>,
    onToggleFavorite: (TrackResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    val listState = rememberLazyListState()
    val headerVisible by rememberHeaderVisibilityByScrollStable(listState)

    // ─────────────────────── Detail state (Artist / Album) ───────────────────────
    var detail by remember { mutableStateOf<DetailState?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var artistResp by remember { mutableStateOf<ArtistResponse?>(null) }
    var albumResp by remember { mutableStateOf<AlbumResponse?>(null) }

    // ─────────────────────── More menu (⋮) bottom sheet ───────────────────────
    var showMoreSheet by remember { mutableStateOf(false) }
    var moreTrack by remember { mutableStateOf<TrackResponse?>(null) }

    fun splitArtists(artistLine: String?): List<String> {
        val s = (artistLine ?: "").trim()
        if (s.isBlank()) return emptyList()
        return s
            .replace(" feat. ", ", ", ignoreCase = true)
            .replace(" ft. ", ", ", ignoreCase = true)
            .replace(" & ", ", ")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun openArtist(track: TrackResponse) {
        val src = (track.source ?: playlists[selectedPlaylist].source).lowercase()
        val service = if (src == "itunes") "yandex" else src
        if (service != "yandex" && service != "vkmusic") return

        detail = DetailState.Artist(
            service = service,
            artistId = track.artistId,
            artistName = track.artist ?: query
        )
    }

    fun openAlbum(track: TrackResponse) {
        val src = (track.source ?: playlists[selectedPlaylist].source).lowercase()
        val service = if (src == "itunes") "yandex" else src

        if (service == "yandex") {
            if (track.albumId.isNullOrBlank()) return
            detail = DetailState.Album(service = "yandex", albumId = track.albumId, ownerId = null, accessKey = null)
            return
        }

        if (service == "vkmusic") {
            if (track.albumId.isNullOrBlank() || track.ownerId.isNullOrBlank()) return
            detail = DetailState.Album(
                service = "vkmusic",
                albumId = track.albumId,
                ownerId = track.ownerId,
                accessKey = track.accessKey
            )
        }
    }

    LaunchedEffect(detail) {
        detailError = null
        artistResp = null
        albumResp = null

        val d = detail ?: return@LaunchedEffect
        detailLoading = true

        try {
            when (d) {

                is DetailState.Artist -> {
                    artistResp = WaveServer.api.getArtist(
                        service = d.service,
                        artistId = d.artistId,
                        artistName = d.artistName,
                        q = d.artistName
                    )
                }

                is DetailState.Album -> {
                    albumResp = WaveServer.api.getAlbum(
                        service = d.service,
                        albumId = d.albumId,
                        ownerId = d.ownerId,
                        accessKey = d.accessKey
                    )
                }

                // ✅ НОВОЕ — чтобы swipe не ломал when
                is DetailState.Track -> {
                    detailLoading = false
                    return@LaunchedEffect
                }
            }
        } catch (t: Throwable) {
            detailError = t.message ?: "Ошибка загрузки"
        } finally {
            detailLoading = false
        }
    }


    // ─────────────────────── If detail open -> render DetailScreen only ───────────────────────
    if (detail != null) {
        val currentService = when (val d = detail) {
            is DetailState.Artist -> d.service
            is DetailState.Album -> d.service
            else -> "yandex"
        }
        DetailScreen(
            loading = detailLoading,
            error = detailError,
            artist = artistResp,
            album = albumResp,
            service = currentService,
            onOpenDetail = { detail = it },
            onBack = { detail = null },
            onPlayTrack = { track ->
                if (track.preview.isNullOrBlank()) return@DetailScreen

                val list = (artistResp?.tracks ?: albumResp?.tracks ?: emptyList())
                    .filter { !it.preview.isNullOrBlank() }
                    .map {
                        PlayingTrack(
                            title = it.title ?: "Без названия",
                            artist = it.artist ?: "",
                            image = it.image ?: "",
                            url = it.preview!!,
                            source = (it.source ?: "search").lowercase(),
                            externalId = it.externalId,

                            // ✅ ДОБАВЬ ЭТО
                            artistId = it.artistId,
                            albumId = it.albumId,
                            ownerId = it.ownerId,
                            accessKey = it.accessKey
                        )

                    }

                val idx = list.indexOfFirst { it.title == (track.title ?: "") && it.artist == (track.artist ?: "") }
                    .coerceAtLeast(0)

                onStopMyWave()
                onSetPlaylistContext(
                    artistResp?.artist?.artistName?.let { "Артист: $it" }
                        ?: albumResp?.album?.albumTitle?.let { "Альбом: $it" }
                        ?: "Подборка"
                )

                player.setPlaylist(list, idx)
            },
            favorites = favorites,
            onToggleFavorite = onToggleFavorite,
            currentTrack = currentTrack
        )
        return
    }

    // ─────────────────────── Search logic ───────────────────────
    fun performSearch() {
        val q = query.trim()
        if (q.isBlank()) return

        onLoadingChange(true)
        onErrorChange(null)

        scope.launch {
            val playlist = playlists[selectedPlaylist]
            val service = playlist.source.lowercase()

            try {
                val response: List<TrackResponse> = when (service) {
                    "spotify" -> WaveServer.api.searchTracks("spotify", q).orEmpty()

                    "soundcloud" -> WaveServer.api.searchTracks("soundcloud", q).orEmpty()

                    else -> WaveServer.api.searchTracks(service, q).orEmpty()
                }


                val updated = playlist.copy(tracks = response)
                val newPlaylists = playlists.map { if (it.id == playlist.id) updated else it }
                onPlaylistsChange(newPlaylists)

            } catch (t: Throwable) {
                onErrorChange("Ошибка: ${t.message}")
            } finally {
                onLoadingChange(false)
            }
        }
    }

    fun playTrack(track: TrackResponse) {
        if (track.preview.isNullOrBlank()) return

        val sourceList = playlists[selectedPlaylist].tracks
        val playable = sourceList.filter { !it.preview.isNullOrBlank() }

        val service = playlists[selectedPlaylist].source.lowercase()

        val playingList = playable.map {
            val src = (it.source ?: service).lowercase()
            PlayingTrack(
                title = it.title ?: "Без названия",
                artist = it.artist ?: "",
                image = it.image ?: "",
                url = it.preview!!,
                source = src,
                externalId = it.externalId,
                artistId = it.artistId,
                albumId = it.albumId,
                ownerId = it.ownerId,
                accessKey = it.accessKey
            )
        }

        val idx = playable.indexOf(track).coerceAtLeast(0)

        onStopMyWave()
        onSetPlaylistContext("Поиск")

        player.setPlaylist(playingList, idx)
    }

// ─────────────────────── UI ───────────────────────
    val bottomPad = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding() + 12.dp

    Column(
        modifier = modifier.fillMaxSize()
    ) {

        // 🔝 Верхняя часть: TopBar всегда видимый
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            cs.background.copy(alpha = 0.98f),
                            cs.background.copy(alpha = 0.92f),
                            cs.background.copy(alpha = 0.00f)
                        )
                    )
                )
                .padding(
                    start = WaveDimens.m,
                    top = WaveDimens.s,
                    end = WaveDimens.m,
                    bottom = WaveDimens.s
                )
        ) {
            WaveTopBar(
                title = "Поиск",
                containerAlpha = 0.30f,
                right = {
                    AnimatedVisibility(
                        visible = !headerVisible,
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(120))
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Показать поиск")
                        }
                    }
                }
            )


            // 🔎 Схлопываемая панель поиска
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Spacer(Modifier.height(WaveDimens.s))

                    SearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        onSearch = { performSearch() },
                        onClear = { onQueryChange("") }
                    )

                    Spacer(Modifier.height(WaveDimens.s))

                    PlaylistChips(
                        playlists = playlists,
                        selected = selectedPlaylist,
                        onSelect = onSelectPlaylist
                    )

                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(WaveDimens.s))
                        AssistChip(
                            onClick = { },
                            label = { Text(error, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.ErrorOutline, null) }
                        )
                    }
                }
            }
        }

        // 📜 Результаты
        val results = playlists[selectedPlaylist].tracks

        if (!loading && results.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPad),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Введи запрос для поиска",
                        color = cs.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState, // ✅ важно!
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = WaveDimens.m,
                    top = WaveDimens.s,
                    end = WaveDimens.m,
                    bottom = bottomPad
                )
            ) {
                if (loading) {
                    items(6) { ShimmerSkeletonRow() }
                } else {
                    items(results) { track ->
                        val isFav = favorites.any { it.title == track.title && it.artist == track.artist }

                        TrackRow(
                            imageUrl = track.image,
                            title = track.title ?: "",
                            subtitle = track.artist ?: "",
                            source = track.source,
                            canPlay = !track.preview.isNullOrBlank(),
                            isCurrent = currentTrack?.title == track.title && currentTrack?.artist == track.artist,
                            isPlaying = isPlaying && currentTrack?.title == track.title,
                            isFavorite = isFav,
                            onPlay = { playTrack(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onMore = {
                                moreTrack = track
                                showMoreSheet = true
                            }
                        )
                    }
                }
            }
        }
    }


    // ─────────────────────── Bottom sheet (⋮) ───────────────────────
    if (showMoreSheet) {
        val t = moreTrack
        val src = (t?.source ?: playlists[selectedPlaylist].source).lowercase()
        val service = if (src == "itunes") "yandex" else src

        val artists = splitArtists(t?.artist)
        val canArtist = (service == "yandex" || service == "vkmusic") && artists.isNotEmpty()
        val canAlbumYandex = (service == "yandex") && !(t?.albumId.isNullOrBlank())
        val canAlbumVk = (service == "vkmusic") && !(t?.albumId.isNullOrBlank()) && !(t?.ownerId.isNullOrBlank())

        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = (t?.title ?: "").ifBlank { "Трек" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t?.artist.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(14.dp))

                // ── Album
                if (canAlbumYandex || canAlbumVk) {
                    ListItem(
                        headlineContent = { Text("Перейти к альбому") },
                        leadingContent = { Icon(Icons.Filled.Album, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMoreSheet = false
                                if (t != null) openAlbum(t)
                            }
                    )
                }

                // ── Artist(s)
                if (canArtist) {
                    val label = if (artists.size >= 2) "Перейти к артистам" else "Перейти к артисту"

                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(Icons.Filled.Person, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (t == null) return@clickable
                                if (artists.size == 1) {
                                    showMoreSheet = false
                                    detail = DetailState.Artist(
                                        service = service,
                                        artistId = t.artistId,
                                        artistName = artists.first()
                                    )
                                }
                                // если артистов 2+ — ниже будет список, по нему и выбираем
                            }
                    )

                    if (artists.size >= 2) {
                        Spacer(Modifier.height(6.dp))
                        artists.forEach { aName ->
                            ListItem(
                                headlineContent = { Text(aName) },
                                leadingContent = { Icon(Icons.Filled.ChevronRight, null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showMoreSheet = false
                                        detail = DetailState.Artist(
                                            service = service,
                                            artistId = null,
                                            artistName = aName
                                        )
                                    }
                            )
                        }
                    }
                }

                if (!canAlbumYandex && !canAlbumVk && !canArtist) {
                    Text(
                        "Нет доступных переходов для этого источника.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}


/* ──────────────────────────────────────────────── 👤/💿 Detail screen (Artist/Album) ───────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    loading: Boolean,
    error: String?,
    artist: ArtistResponse?,
    album: AlbumResponse?,
    service: String,
    onBack: () -> Unit,
    onPlayTrack: (TrackResponse) -> Unit,
    favorites: List<TrackResponse>,
    onToggleFavorite: (TrackResponse) -> Unit,
    currentTrack: PlayingTrack?,
    onOpenDetail: (DetailState) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    val isArtistScreen = artist != null && album == null
    val isAlbumScreen = album != null

    val title = artist?.artist?.artistName
        ?: album?.album?.albumTitle
        ?: "..."

    val cover = artist?.artist?.image
        ?: album?.album?.image

    val tracks = artist?.tracks ?: album?.tracks ?: emptyList()
    val playable = remember(tracks) { tracks.filter { !it.preview.isNullOrBlank() } }
    val trackCount = tracks.size

    val albumYearText = remember(isAlbumScreen, tracks) {
        if (!isAlbumScreen) null
        else tracks.firstNotNullOfOrNull { it.albumYear }?.toString()?.takeIf { it.isNotBlank() }
    }

    val subtitle = when {
        isAlbumScreen -> if (!albumYearText.isNullOrBlank()) "Альбом • $albumYearText" else "Альбом"
        isArtistScreen -> "Артист"
        else -> ""
    }

    fun playAll() {
        val first = playable.firstOrNull() ?: return
        onPlayTrack(first)
    }

    fun shufflePlay() {
        val pick = playable.randomOrNull() ?: return
        onPlayTrack(pick)
    }

    fun isNowPlaying(t: TrackResponse): Boolean {
        val ct = currentTrack ?: return false
        val tt = (t.title ?: "").trim()
        val ta = (t.artist ?: "").trim()
        return tt.isNotBlank() &&
                ta.isNotBlank() &&
                tt.equals(ct.title.trim(), ignoreCase = true) &&
                ta.equals(ct.artist.trim(), ignoreCase = true)
    }

    // ───────────────────────── Artist: Top 10 + Expand ─────────────────────────
    var showAllArtistTracks by rememberSaveable { mutableStateOf(false) }
    val topLimit = 10

    val tracksToShow = remember(isArtistScreen, tracks, showAllArtistTracks) {
        if (!isArtistScreen) tracks
        else if (showAllArtistTracks) tracks
        else tracks.take(topLimit)
    }

    // ───────────────────────── Albums from tracks (no API needed) ─────────────────────────
    data class UiAlbum(
        val albumId: String,
        val title: String,
        val year: String?,
        val cover: String?,
        val ownerId: String?,
        val accessKey: String?
    )

    val artistAlbums: List<UiAlbum> = remember(isArtistScreen, tracks, service) {
        if (!isArtistScreen) return@remember emptyList()

        val candidates = tracks.filter { !it.albumId.isNullOrBlank() && !it.albumTitle.isNullOrBlank() }

        val byKey = LinkedHashMap<String, TrackResponse>()
        for (t in candidates) {
            val key = when (service.lowercase()) {
                "vkmusic" -> "${t.ownerId.orEmpty()}::${t.albumId.orEmpty()}"
                else -> t.albumId.orEmpty()
            }
            if (!byKey.containsKey(key)) byKey[key] = t
        }

        byKey.values.map { t ->
            UiAlbum(
                albumId = t.albumId!!,
                title = t.albumTitle!!,
                year = t.albumYear?.toString(),
                cover = t.image,
                ownerId = t.ownerId,
                accessKey = t.accessKey
            )
        }.sortedWith(
            compareByDescending<UiAlbum> { it.year?.toIntOrNull() ?: -1 }
                .thenBy { it.title.lowercase() }
        )
    }

    fun canOpenAlbum(a: UiAlbum): Boolean {
        return when (service.lowercase()) {
            "vkmusic" -> a.albumId.isNotBlank() && !a.ownerId.isNullOrBlank()
            else -> a.albumId.isNotBlank()
        }
    }

    fun openAlbum(a: UiAlbum) {
        if (!canOpenAlbum(a)) return
        onOpenDetail(
            DetailState.Album(
                service = service,
                albumId = a.albumId,
                ownerId = if (service.lowercase() == "vkmusic") a.ownerId else null,
                accessKey = if (service.lowercase() == "vkmusic") a.accessKey else null
            )
        )
    }

    val listState = rememberLazyListState()

    Column(
        Modifier.fillMaxSize()
    ) {

        // ✅ Header как в остальных вкладках
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            cs.background.copy(alpha = 0.98f),
                            cs.background.copy(alpha = 0.92f),
                            cs.background.copy(alpha = 0.00f)
                        )
                    )
                )
                .padding(
                    start = WaveDimens.m,
                    top = WaveDimens.s,
                    end = WaveDimens.m,
                    bottom = WaveDimens.s
                )
        ) {
            WaveBackTopBar(
                title = title,
                onBack = onBack,
                containerAlpha = 0.30f,
                height = 54.dp
            )
        }

        // ✅ ОДИН LazyColumn + ✅ скроллится (weight)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
            )
        ) {

            // ───────────────────── HERO ─────────────────────
            item {
                val albumArtistText = remember(isAlbumScreen, tracks) {
                    if (!isAlbumScreen) null
                    else tracks.firstOrNull()?.artist?.trim()?.takeIf { it.isNotBlank() }
                }

                val secondLine = remember(isAlbumScreen, albumArtistText, albumYearText, isArtistScreen) {
                    when {
                        isAlbumScreen -> {
                            val a = albumArtistText
                            val y = albumYearText
                            when {
                                !a.isNullOrBlank() && !y.isNullOrBlank() -> "$a • $y"
                                !a.isNullOrBlank() -> a
                                !y.isNullOrBlank() -> y
                                else -> "Альбом"
                            }
                        }
                        isArtistScreen -> "Артист"
                        else -> ""
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WaveDimens.m)
                        .padding(top = WaveDimens.s, bottom = WaveDimens.m)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = WaveAlpha.surfaceLow(cs))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clipToBounds()
                        ) {
                            if (!cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(WaveAlpha.surfaceLow(cs)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Album,
                                        null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = title,
                        color = cs.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (secondLine.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = secondLine,
                            color = cs.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Треков: $trackCount") },
                            leadingIcon = { Icon(Icons.Filled.QueueMusic, null) }
                        )
                        if (playable.size != trackCount) {
                            AssistChip(
                                onClick = { },
                                label = { Text("Preview: ${playable.size}") },
                                leadingIcon = { Icon(Icons.Filled.PlayCircle, null) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { playAll() },
                            enabled = playable.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = { shufflePlay() },
                            enabled = playable.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }


            // ───────────────────── TRACKS HEADER ─────────────────────
            item {
                Text(
                    text = when {
                        isAlbumScreen -> "Треки"
                        isArtistScreen -> "Популярное"
                        else -> "Треки"
                    },
                    color = cs.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = WaveDimens.m, vertical = 6.dp)
                )
            }

            // ───────────────────── TRACKS LIST ─────────────────────
            when {
                loading -> item {
                    Column(Modifier.padding(horizontal = WaveDimens.m)) { repeat(10) { ShimmerSkeletonRow() } }
                }

                error != null -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("Ошибка: $error", color = cs.onBackground)
                    }
                }

                tracks.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("Нет треков", color = cs.onBackground)
                    }
                }

                else -> {
                    itemsIndexed(tracksToShow) { index, t ->
                        val isFav = favorites.any { it.title == t.title && it.artist == t.artist }
                        val canPlay = !t.preview.isNullOrBlank()
                        val now = isNowPlaying(t)

                        val container = if (now) cs.primary.copy(alpha = 0.18f) else WaveAlpha.surfaceLow(cs)
                        val outline = if (now) cs.primary.copy(alpha = 0.35f) else WaveAlpha.outlineSoft(cs)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = WaveDimens.m, vertical = 6.dp)
                                .clip(RoundedCornerShape(WaveDimens.radiusM))
                                .clickable(enabled = canPlay) { onPlayTrack(t) },
                            colors = CardDefaults.cardColors(containerColor = container),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (now) 2.dp else 0.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, outline, RoundedCornerShape(WaveDimens.radiusM))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (index + 1).toString(),
                                        color = if (now) cs.primary else cs.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Box {
                                    AsyncImage(
                                        model = t.image,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .alpha(if (canPlay) 1f else 0.55f),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (now) {
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(cs.primary)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (t.title ?: "").ifBlank { "Без названия" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        t.artist.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (now) cs.primary else cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { onToggleFavorite(t) }) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFav) Color(0xFFFF55AA) else cs.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (isArtistScreen && tracks.size > topLimit) {
                        item {
                            TextButton(
                                onClick = { showAllArtistTracks = !showAllArtistTracks },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = WaveDimens.m, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showAllArtistTracks) "Скрыть" else "Показать ещё",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ───────────────────── ALBUMS (artist only) ─────────────────────
            if (isArtistScreen) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Альбомы",
                        color = cs.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = WaveDimens.m, vertical = 6.dp)
                    )
                }

                if (artistAlbums.isEmpty()) {
                    item {
                        Text(
                            text = "Альбомы не найдены в треках артиста.",
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = WaveDimens.m, vertical = 8.dp)
                        )
                    }
                } else {
                    items(artistAlbums) { a ->
                        val canOpen = canOpenAlbum(a)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = WaveDimens.m, vertical = 6.dp)
                                .clip(RoundedCornerShape(WaveDimens.radiusM))
                                .clickable(enabled = canOpen) { openAlbum(a) },
                            colors = CardDefaults.cardColors(containerColor = WaveAlpha.surfaceLow(cs)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, WaveAlpha.outlineSoft(cs), RoundedCornerShape(WaveDimens.radiusM))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = a.cover,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        a.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = if (!a.year.isNullOrBlank()) "Альбом • ${a.year}" else "Альбом"
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}









@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterTopAppBarTitle(
    title: String,
    height: Dp = 44.dp,
    titleSize: TextUnit = 18.sp
) {
    val cs = MaterialTheme.colorScheme
    val side = 44.dp // ширина "слота" слева/справа (под иконки)

    TopAppBar(
        modifier = Modifier.height(height),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // left placeholder (равный правому)
                Spacer(Modifier.width(side))

                // true center
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = cs.onBackground,
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // right placeholder
                Spacer(Modifier.width(side))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}



@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Найду любой трек", maxLines = 1) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotBlank()) IconButton(onClick = onClear) { Icon(Icons.Filled.Close, null) }
                IconButton(onClick = onSearch) { Icon(Icons.Filled.ArrowForward, null) }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp)), // вместо 999.dp — выглядит более “взросло”
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.55f),
            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.40f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = cs.primary,
            focusedTextColor = cs.onSurface,
            unfocusedTextColor = cs.onSurface,
            focusedLeadingIconColor = cs.onSurfaceVariant,
            unfocusedLeadingIconColor = cs.onSurfaceVariant,
            focusedTrailingIconColor = cs.onSurfaceVariant,
            unfocusedTrailingIconColor = cs.onSurfaceVariant,
            focusedPlaceholderColor = cs.onSurfaceVariant.copy(alpha = 0.70f),
            unfocusedPlaceholderColor = cs.onSurfaceVariant.copy(alpha = 0.70f),
        ),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
private fun rememberHeaderVisibilityByScrollStable(
    listState: androidx.compose.foundation.lazy.LazyListState,
    hideThresholdPx: Int = 140,
    showThresholdPx: Int = 40
): State<Boolean> {
    val visible = remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        var accumulated = 0

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->

            val delta = when {
                index == lastIndex -> offset - lastOffset
                index > lastIndex -> offset + 200    // перескок вниз
                else -> -(lastOffset + 200)          // перескок вверх
            }

            accumulated += delta

            // всегда показываем в самом верху
            if (index == 0 && offset == 0) {
                visible.value = true
                accumulated = 0
            } else {
                if (accumulated > hideThresholdPx) {
                    visible.value = false
                    accumulated = 0
                } else if (accumulated < -showThresholdPx) {
                    visible.value = true
                    accumulated = 0
                }
            }

            lastIndex = index
            lastOffset = offset
        }
    }

    return visible
}


@Composable
private fun PlaylistChips(
    playlists: List<Playlist>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(WaveAlpha.surfaceLow(cs))
            .border(1.dp, WaveAlpha.outlineSoft(cs), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        playlists.forEach { playlist ->
            val isSelected = selected == playlist.id
            val bg = if (isSelected) cs.primary.copy(alpha = 0.22f) else Color.Transparent

            val animAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.86f,
                label = "chipAlpha"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .clickable { onSelect(playlist.id) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playlist.title,
                    color = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(animAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
/* ──────────────────────────────────────────────── ✨ Shimmer skeleton ───────────────────────────────────────────── */

@Composable
fun ShimmerSkeletonRow(height: Int = 74) {
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "shimmerX"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            WaveAlpha.surfaceLow(cs),
            WaveAlpha.surfaceHigh(cs),
            WaveAlpha.surfaceLow(cs)
        ),
        start = androidx.compose.ui.geometry.Offset(x - 400f, 0f),
        end = androidx.compose.ui.geometry.Offset(x, 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(WaveDimens.radiusM))
            .background(brush)
    )
}

/* ──────────────────────────────────────────────── 🎵 Track row ──────────────────────────────────────────────── */

@Composable
fun TrackRow(
    imageUrl: String?,
    title: String,
    subtitle: String,
    source: String?,
    canPlay: Boolean,
    onPlay: () -> Unit,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit // ✅ новое
) {
    val cs = MaterialTheme.colorScheme

    val container = when {
        isCurrent -> cs.primary.copy(alpha = 0.18f)
        else -> WaveAlpha.surfaceLow(cs)
    }

    val outline = when {
        isCurrent -> cs.primary.copy(alpha = 0.35f)
        else -> WaveAlpha.outlineSoft(cs)
    }

    val artAlpha = if (canPlay) 1f else 0.55f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(WaveDimens.radiusM))
            .clickable(enabled = canPlay) { onPlay() },
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 2.dp else 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, outline, RoundedCornerShape(WaveDimens.radiusM))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ✅ Обложка + fallback "Нет обложки"
            Box {
                var loadFailed by remember(imageUrl) { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .alpha(artAlpha)
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!loadFailed && !imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = { loadFailed = true }
                        )
                    } else {
                        Text(
                            text = "Нет\nобложки",
                            color = cs.onSurface.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 11.sp
                        )
                    }
                }

                if (isCurrent && isPlaying) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(cs.primary)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!canPlay) {
                    Spacer(Modifier.height(6.dp))
                    AssistChip(
                        onClick = { },
                        label = { Text("Preview недоступен") },
                        leadingIcon = { Icon(Icons.Filled.Block, null) }
                    )
                }
            }

            // ❤️
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFF55AA) else cs.onSurface
                )
            }

            // ✅ Source icon
            if (!source.isNullOrBlank()) {
                Box(Modifier.padding(end = 2.dp)) {
                    SourceBadge(source)
                }
            }

            // ⋮
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = cs.onSurfaceVariant)
            }
        }
    }
}

/* ──────────────────────────────────────────────── 💜 Favorites ──────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    listState: androidx.compose.foundation.lazy.LazyListState,
    favorites: List<TrackResponse>,
    loading: Boolean,
    onPlay: (TrackResponse) -> Unit,
    isCurrent: (TrackResponse) -> Boolean,
    isPlaying: Boolean,
    onOpenDetail: (DetailState) -> Unit,
    onToggleFavorite: (TrackResponse) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // ✅ используем ПЕРЕДАННЫЙ listState, НЕ создаём новый
    val headerVisible by rememberHeaderVisibilityByScrollStable(listState)

    var showMoreSheet by remember { mutableStateOf(false) }
    var moreTrack by remember { mutableStateOf<TrackResponse?>(null) }


    fun splitArtists(artistLine: String?): List<String> {
        val s = (artistLine ?: "").trim()
        if (s.isBlank()) return emptyList()
        return s
            .replace(" feat. ", ", ", ignoreCase = true)
            .replace(" ft. ", ", ", ignoreCase = true)
            .replace(" & ", ", ")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun openAlbumFromTrack(t: TrackResponse) {
        val src = (t.source ?: "").lowercase()
        val service = if (src == "itunes") "yandex" else src

        if (service == "yandex") {
            if (t.albumId.isNullOrBlank()) return
            onOpenDetail(DetailState.Album("yandex", t.albumId, null, null))
            return
        }

        if (service == "vkmusic") {
            if (t.albumId.isNullOrBlank() || t.ownerId.isNullOrBlank()) return
            onOpenDetail(DetailState.Album("vkmusic", t.albumId, t.ownerId, t.accessKey))
        }
    }

    fun openArtistFromName(t: TrackResponse, name: String, useId: Boolean) {
        val src = (t.source ?: "").lowercase()
        val service = if (src == "itunes") "yandex" else src
        if (service != "yandex" && service != "vkmusic") return

        onOpenDetail(
            DetailState.Artist(
                service = service,
                artistId = if (useId) t.artistId else null,
                artistName = name
            )
        )
    }

    var localQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf("recent") } // recent | title | artist

    val totalCount = favorites.size

    val filtered = remember(favorites, localQuery, sortMode) {
        val q = localQuery.trim().lowercase()
        val base = if (q.isBlank()) favorites else favorites.filter {
            (it.title ?: "").lowercase().contains(q) || (it.artist ?: "").lowercase().contains(q)
        }
        when (sortMode) {
            "title" -> base.sortedBy { (it.title ?: "").lowercase() }
            "artist" -> base.sortedBy { (it.artist ?: "").lowercase() }
            else -> base
        }
    }

    // ✅ ОДИН общий контейнер экрана: header + list
    Column(
        Modifier.fillMaxSize()
    ) {

        // ✅ Header container (TopBar всегда + остальное схлопывается)
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                )

                .background(
                    Brush.verticalGradient(
                        listOf(
                            cs.background.copy(alpha = 0.98f),
                            cs.background.copy(alpha = 0.92f),
                            cs.background.copy(alpha = 0.00f)
                        )
                    )
                )
                .padding(
                    start = WaveDimens.m,
                    top = WaveDimens.s,
                    end = WaveDimens.m,
                    bottom = WaveDimens.s
                )
        ) {
            WaveTopBar(
                title = "Избранное",
                containerAlpha = 0.30f,
                right = {
                    AnimatedVisibility(
                        visible = !headerVisible,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(120))
                    ) {
                        IconButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } }
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Показать фильтры")
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        expandVertically(
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top
                        ),
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        shrinkVertically(
                            animationSpec = tween(360, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        )
            ) {

                Column {
                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Треков: $totalCount",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(WaveDimens.s))

                    SearchField(
                        value = localQuery,
                        onValueChange = { localQuery = it },
                        onSearch = { },
                        onClear = { localQuery = "" }
                    )

                    Spacer(Modifier.height(WaveDimens.s))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sortMode == "recent",
                            onClick = { sortMode = "recent" },
                            label = { Text("Недавние") }
                        )
                        FilterChip(
                            selected = sortMode == "artist",
                            onClick = { sortMode = "artist" },
                            label = { Text("Артист") }
                        )
                        FilterChip(
                            selected = sortMode == "title",
                            onClick = { sortMode = "title" },
                            label = { Text("Название") }
                        )
                    }
                }
            }
        }

        // ✅ Список (внутри того же Column)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = WaveDimens.m,
                top = WaveDimens.s,
                end = WaveDimens.m,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
            )
        ) {
            if (loading) {
                items(10) { ShimmerSkeletonRow() }  // ✅ анимация загрузки
            } else if (filtered.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Нет избранных треков 💜",
                            color = cs.onBackground,
                            fontSize = 18.sp
                        )
                    }
                }
            } else {
                items(filtered) { track ->
                    TrackRow(
                        imageUrl = track.image,
                        title = track.title ?: "",
                        subtitle = track.artist ?: "",
                        source = track.source,
                        canPlay = !track.preview.isNullOrBlank(),
                        onPlay = { onPlay(track) },
                        isCurrent = isCurrent(track),
                        isPlaying = isPlaying,
                        isFavorite = true,
                        onToggleFavorite = { onToggleFavorite(track) },
                        onMore = {
                            moreTrack = track
                            showMoreSheet = true
                        }
                    )
                }
            }
        }
    }

    // ✅ BottomSheet — ВНУТРИ FavoritesScreen (после Column)
    if (showMoreSheet) {
        val t = moreTrack
        val src = (t?.source ?: "").lowercase()
        val service = if (src == "itunes") "yandex" else src

        val artists = splitArtists(t?.artist)
        val canArtist = (service == "yandex" || service == "vkmusic") && artists.isNotEmpty()
        val canAlbumYandex = (service == "yandex") && !(t?.albumId.isNullOrBlank())
        val canAlbumVk = (service == "vkmusic") && !(t?.albumId.isNullOrBlank()) && !(t?.ownerId.isNullOrBlank())

        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = (t?.title ?: "").ifBlank { "Трек" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t?.artist.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(14.dp))

                if (canAlbumYandex || canAlbumVk) {
                    ListItem(
                        headlineContent = { Text("Перейти к альбому") },
                        leadingContent = { Icon(Icons.Filled.Album, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMoreSheet = false
                                if (t != null) openAlbumFromTrack(t)
                            }
                    )
                }

                if (canArtist) {
                    val label = if (artists.size >= 2) "Перейти к артистам" else "Перейти к артисту"
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(Icons.Filled.Person, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (t == null) return@clickable
                                if (artists.size == 1) {
                                    showMoreSheet = false
                                    openArtistFromName(t, artists.first(), useId = true)
                                }
                            }
                    )

                    if (artists.size >= 2) {
                        Spacer(Modifier.height(6.dp))
                        artists.forEach { aName ->
                            ListItem(
                                headlineContent = { Text(aName) },
                                leadingContent = { Icon(Icons.Filled.ChevronRight, null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (t == null) return@clickable
                                        showMoreSheet = false
                                        openArtistFromName(t, aName, useId = false)
                                    }
                            )
                        }
                    }
                }

                if (!canAlbumYandex && !canAlbumVk && !canArtist) {
                    Text(
                        "Нет доступных переходов для этого источника.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}




/* ──────────────────────────────────────────────── 👤 Profile ──────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean,
    username: String?,
    avatarUrl: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onAvatarUploaded: (String) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenFavorites: () -> Unit,
    theme: WaveTheme,
    onThemeChange: (WaveTheme) -> Unit
) {


    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("WavePrefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val token = prefs.getString("user_token", null)

    var localNick by remember { mutableStateOf(username ?: "") }
    var localAvatar by remember { mutableStateOf(avatarUrl) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loadingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && localNick.isBlank()) {
            loadingProfile = true
            val (nick, url) = fetchProfile(token)
            if (nick.isNotBlank()) localNick = nick
            if (!url.isNullOrBlank()) {
                localAvatar = url
                prefs.edit().putString("avatar_url", url).apply()
            }
            loadingProfile = false
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null && isLoggedIn) {
                scope.launch {
                    message = "Загрузка..."
                    val (ok, newUrl, serverMsg) = uploadAvatar(context, token, uri)
                    message = serverMsg
                    if (ok && !newUrl.isNullOrBlank()) {
                        val bustedUrl = "$newUrl?cb=${System.currentTimeMillis()}"
                        localAvatar = bustedUrl
                        prefs.edit().putString("avatar_url", bustedUrl).apply()
                        onAvatarUploaded(bustedUrl)
                    }
                }
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(waveBackgroundBrush(theme)),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = WaveDimens.m,
                top = WaveDimens.l,
                end = WaveDimens.m,
                bottom = 140.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Header card (cleaner + consistent)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        cs.primary.copy(alpha = 0.55f),
                                        cs.surfaceVariant.copy(alpha = 0.30f)
                                    )
                                )
                            )
                            .padding(WaveDimens.l),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(cs.onSurface.copy(alpha = 0.10f))
                                    .border(2.dp, cs.onSurface.copy(alpha = 0.22f), CircleShape)
                                    .clickable { if (isLoggedIn) pickImageLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                val a = localAvatar
                                when {
                                    a.isNullOrBlank() -> Text(
                                        if (isLoggedIn) "Добавить фото" else "Гость",
                                        color = cs.onSurfaceVariant
                                    )

                                    a.startsWith("data:image") -> {
                                        val base64Data = a.substringAfter(",")
                                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        val bmp =
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(110.dp)
                                                .clip(CircleShape)
                                        )
                                    }

                                    else -> {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = a),
                                            contentDescription = "Avatar",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier
                                                .size(110.dp)
                                                .clip(CircleShape)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(WaveDimens.s))

                            Text(
                                text = if (isLoggedIn) (localNick.ifBlank { "Пользователь" }) else "Гость",
                                color = cs.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )

                            if (loadingProfile) {
                                Spacer(Modifier.height(8.dp))
                                Text("Загрузка профиля...", color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(WaveDimens.l))
            }

            item {
                // Actions as list cards
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLoggedIn) {
                        ProfileActionCard(
                            icon = Icons.Filled.PhotoCamera,
                            title = "Изменить фото",
                            subtitle = "Обнови аватар профиля",
                            onClick = { pickImageLauncher.launch("image/*") }
                        )
                        ProfileActionCard(
                            icon = Icons.Filled.Favorite,
                            title = "Избранное",
                            subtitle = "Твои любимые треки",
                            onClick = onOpenFavorites
                        )
                        ProfileActionCard(
                            icon = Icons.Filled.Palette,
                            title = "Тема оформления",
                            subtitle = "Сейчас: ${theme.title}",
                            onClick = { showThemeSheet = true }
                        )
                        ProfileActionCard(
                            icon = Icons.Filled.QueueMusic,
                            title = "Плейлисты",
                            subtitle = "Источник и подборки (в разработке)",
                            onClick = onOpenPlaylists
                        )
                        ProfileActionCard(
                            icon = Icons.Filled.Logout,
                            title = "Выйти",
                            subtitle = "Завершить сессию",
                            destructive = true,
                            onClick = onLogout
                        )
                    } else {
                        ProfileActionCard(
                            icon = Icons.Filled.Login,
                            title = "Войти",
                            subtitle = "Авторизуйся, чтобы сохранять избранное",
                            emphasized = true,
                            onClick = onLogin
                        )
                    }
                }
            }

            if (message != null) {
                item {
                    Spacer(Modifier.height(WaveDimens.s))
                    AssistChip(
                        onClick = { },
                        label = { Text(message!!, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(
                                if (message!!.contains(
                                        "Ошибка",
                                        true
                                    )
                                ) Icons.Filled.ErrorOutline else Icons.Filled.Info,
                                null
                            )
                        }
                    )
                }
            }
        }
    }

// ✅ ВАЖНО: sheet ДОЛЖЕН быть здесь (в ProfileScreen), а не в ProfileActionCard
    if (showThemeSheet) {
        ThemePickerSheet(
            current = theme,
            onSelect = {
                onThemeChange(it)
                showThemeSheet = false
            },
            onDismiss = { showThemeSheet = false }
        )
    }
}

@Composable
private fun ProfileActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    destructive: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val container = when {
        destructive -> Color(0xFFFF3B30).copy(alpha = 0.12f)
        emphasized -> cs.primary.copy(alpha = 0.18f)
        else -> WaveAlpha.surfaceLow(cs)
    }
    val outline = when {
        destructive -> Color(0xFFFF3B30).copy(alpha = 0.35f)
        emphasized -> cs.primary.copy(alpha = 0.35f)
        else -> WaveAlpha.outlineSoft(cs)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WaveDimens.radiusL))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, outline, RoundedCornerShape(WaveDimens.radiusL))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.onSurface.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (destructive) Color(0xFFFF3B30) else cs.onSurface)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant)
        }
    }
}


/* ──────────────────────────────────────────────── 🌐 Profile: API ──────────────────────────────────────────────── */

private const val PROFILE_ENDPOINT = "https://functions.yandexcloud.net/d4e55oqrims414ecgmtd"

suspend fun fetchProfile(token: String?): Pair<String, String?> = withContext(Dispatchers.IO) {
    if (token.isNullOrBlank()) return@withContext "" to null
    try {
        val client = OkHttpClient()
        val body = JSONObject()
            .put("action", "get_profile")
            .put("token", token)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder().url(PROFILE_ENDPOINT).post(body).build()
        val res = client.newCall(req).execute()
        val str = res.body?.string() ?: return@withContext "" to null
        val json = JSONObject(str)

        val nickname = json.optString("nickname", "")
        val avatar = json.optString("avatar_url", null)
        nickname to avatar
    } catch (e: Exception) {
        Log.e("Profile", "Ошибка профиля", e)
        "" to null
    }
}

/* ──────────────────────────────────────────────── 🖼️ Upload avatar ───────────────────────────────────────────── */

suspend fun uploadAvatar(
    context: Context,
    token: String?,
    uri: Uri
): Triple<Boolean, String?, String> = withContext(Dispatchers.IO) {
    if (token.isNullOrBlank()) return@withContext Triple(false, null, "Ошибка: не авторизован")

    try {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            ?: return@withContext Triple(false, null, "Ошибка чтения файла")

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUri = "data:image/png;base64,$base64"

        val client = OkHttpClient()

        val body = JSONObject()
            .put("action", "update_avatar")
            .put("token", token)
            .put("avatar_url", dataUri)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder().url(PROFILE_ENDPOINT).post(body).build()
        val res = client.newCall(req).execute()
        val str = res.body?.string() ?: return@withContext Triple(false, null, "Пустой ответ")
        val json = JSONObject(str)

        val newUrl = json.optString("avatar_url", null)
        if (newUrl.isNullOrBlank()) Triple(false, null, "Ошибка обновления аватара")
        else Triple(true, newUrl, "Аватар обновлён ✅")
    } catch (e: Exception) {
        Log.e("Avatar", "Ошибка", e)
        Triple(false, null, "Ошибка: ${e.message}")
    }
}
// ──────────────────────────────── 🎧 "Моя Волна" + диск (Empty state) ────────────────────────────────
// Вставь этот composable КУДА-ТО В ФАЙЛ (лучше рядом с TrackRow / ShimmerSkeletonRow), НЕ внутрь MainScreen.

@Composable
fun MyWaveDisc(
    modifier: Modifier = Modifier,
    spin: Boolean,
    enabled: Boolean = true,
    onPlayClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    val infinite = rememberInfiniteTransition(label = "discSpin")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(220.dp)
            .rotate(if (spin) rotation else 0f)
            .clip(CircleShape)
            .background(cs.onSurface.copy(alpha = 0.06f))
            .border(1.dp, cs.outline.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // ✅ ВАЖНО: без padding + Crop (чтобы не было визуального смещения)
        Image(
            painter = painterResource(id = R.drawable.mywave),
            contentDescription = "Wave",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // затемнение для читаемости кнопки (можно убрать)
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.10f))
        )

        // Центральная кнопка
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(cs.surface.copy(alpha = 0.82f))
                .border(1.dp, cs.outline.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.95f))
                    .alpha(if (enabled) 1f else 0.6f)
                    .clickable(enabled = enabled) { onPlayClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = cs.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}



@Composable
fun SearchTabStub(
    modifier: Modifier = Modifier,
    onGoHome: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Поиск",
                color = cs.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Сюда перенесём настоящий поиск 👇",
                color = cs.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGoHome) { Text("Назад на Главную") }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePickerSheet(
    current: WaveTheme,
    onSelect: (WaveTheme) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "Выбери тему",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            WaveTheme.entries.forEach { t ->
                val selected = t == current
                ListItem(
                    headlineContent = { Text(t.title) },
                    leadingContent = {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(t) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(t) }
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

// ──────────────────────────────────────────────── ⭐ Import to Favorites ────────────────────────────────────────────────

data class ImportProgress(
    val status: String,
    val done: Int,
    val total: Int,
    val current: String
)

private suspend fun profilePostRaw(body: JSONObject): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
    val client = okhttp3.OkHttpClient()
    val reqBody = body.toString()
        .toRequestBody("application/json".toMediaType())
    val req = okhttp3.Request.Builder()
        .url(PROFILE_ENDPOINT)
        .post(reqBody)
        .build()

    client.newCall(req).execute().use { res ->

        val raw = res.body?.string().orEmpty()

        // ✅ ЛОГ ЗАПРОСА И ОТВЕТА
        Log.d("IMPORT_DEBUG", "REQUEST = ${body}")
        Log.d("IMPORT_DEBUG", "RESPONSE = $raw")

        raw
    }
}

private suspend fun profilePostJson(body: JSONObject): JSONObject {
    val raw = profilePostRaw(body)
    return try {
        JSONObject(raw.ifBlank { "{}" })
    } catch (_: Throwable) {
        JSONObject("{}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportFavoritesScreen(
    token: String?,
    onBack: () -> Unit,
    onDoneOpenFavorites: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var source by rememberSaveable { mutableStateOf("vkmusic") }
    var text by rememberSaveable { mutableStateOf("") }

    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    // итог
    var resultAdded by remember { mutableStateOf<Int?>(null) }
    var resultFailed by remember { mutableStateOf<Int?>(null) }
    var resultTotal by remember { mutableStateOf<Int?>(null) }
    var resultErrors by remember { mutableStateOf<List<String>>(emptyList()) }

    // локальный прогресс (без обращения к playlist_import_progress)
    var doneCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }

    fun startImport() {
        if (token.isNullOrBlank()) {
            err = "Нужно войти в аккаунт"
            return
        }

        val lines = text
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            err = "Вставь список треков"
            return
        }

        running = true
        err = null
        resultAdded = null
        resultFailed = null
        resultTotal = null
        resultErrors = emptyList()

        doneCount = 0
        totalCount = lines.size
        totalCount = totalCount.coerceAtLeast(1)

        scope.launch {
            try {
                val batchSize = 2 // ✅ 1..3 (1 — самый безопасный против таймаута)
                var offset = 0

                var addedSum = 0
                var failedSum = 0
                val errors = mutableListOf<String>()

                while (offset < lines.size) {
                    val req = JSONObject()
                        .put("action", "import_text_to_favorites")
                        .put("token", token)
                        .put("text", text) // отправляем весь текст, сервер режет по offset
                        .put("service", source)
                        .put("offset", offset)
                        .put("batch_size", batchSize)

                    Log.d("IMPORT_DEBUG", "REQUEST = $req")
                    val resp = profilePostJson(req)
                    Log.d("IMPORT_DEBUG", "RESPONSE = $resp")

                    // сервер может вернуть error
                    val serverError = resp.optString("error", "")
                    if (serverError.isNotBlank()) {
                        err = serverError
                        break
                    }

                    // суммируем
                    val total = resp.optInt("total", lines.size)
                    val added = resp.optInt("added", 0)
                    val failed = resp.optInt("failed", 0)

                    addedSum += added
                    failedSum += failed
                    resultTotal = total
                    resultAdded = addedSum
                    resultFailed = failedSum

                    // ошибки батча
                    val errsJson = resp.optJSONArray("errors")
                    if (errsJson != null) {
                        for (i in 0 until errsJson.length()) {
                            val s = errsJson.optString(i, "").trim()
                            if (s.isNotBlank()) errors.add(s)
                        }
                    }
                    resultErrors = errors

                    val nextOffset = resp.optInt("next_offset", offset)
                    val doneFlag = resp.optBoolean("done", false)

                    // локальный прогресс
                    doneCount = nextOffset.coerceIn(0, totalCount)

                    offset = nextOffset

                    if (doneFlag) break

                    // чуть-чуть разгружаем
                    kotlinx.coroutines.delay(250L)
                }

                // если ошибок нет — открываем избранное
                if (err.isNullOrBlank()) {
                    if (resultErrors.isEmpty()) {
                        onDoneOpenFavorites()
                    } else {
                        err = "Импорт завершён с ошибками: ${resultErrors.size}"
                    }
                }
            } catch (t: Throwable) {
                err = t.message ?: "Ошибка импорта"
            } finally {
                running = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(waveBackgroundBrush(WaveTheme.DARK))
    ) {
        WaveBackTopBar(
            title = "Импорт в избранное",
            onBack = onBack,
            containerAlpha = 0.30f,
            height = 54.dp
        )

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = WaveAlpha.surfaceLow(cs)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, WaveAlpha.outlineSoft(cs), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Вставь треки построчно",
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Формат: артист - название (любые варианты тоже ок, сервер попробует найти).",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))

                Text("Источник поиска", color = cs.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("vkmusic", "yandex", "spotify", "soundcloud")) { s ->
                        FilterChip(
                            selected = source == s,
                            onClick = { if (!running) source = s },
                            label = { Text(s) }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (!running) text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 170.dp,
                            max = 260.dp // ✅ ограничиваем рост, кнопка больше не уезжает
                        ),
                    placeholder = { Text("unki - BALLOUT\nOG Buda - ...\n...") },
                    label = { Text("Список треков") },
                    enabled = !running,
                    maxLines = 12 // ✅ после 12 строк поле скроллится внутри
                )


                Spacer(Modifier.height(10.dp))

                val linesCount = remember(text) {
                    text.split("\n").count { it.trim().isNotEmpty() }
                }
                Text(
                    "Строк: $linesCount",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp
                )

                if (!err.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(err!!, color = Color(0xFFFF5577))
                }

                // ✅ прогресс (локальный, по offset)
                if (running) {
                    Spacer(Modifier.height(10.dp))
                    val total = totalCount.coerceAtLeast(1)
                    val frac = (doneCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    Text(
                        "Прогресс: $doneCount/$total",
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                }

                if (resultAdded != null || resultFailed != null) {
                    Spacer(Modifier.height(10.dp))
                    val a = resultAdded ?: 0
                    val f = resultFailed ?: 0
                    val t = resultTotal ?: (a + f)
                    Text(
                        "Готово: добавлено $a из $t, пропущено $f",
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    if (resultErrors.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ошибки (первые 3):",
                            color = cs.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        resultErrors.take(3).forEach {
                            Text("• $it", color = cs.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = { startImport() },
                    enabled = !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (!running) {
                        Icon(Icons.Filled.CloudUpload, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Добавить в избранное", fontWeight = FontWeight.SemiBold)
                    } else {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Импортируем…")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}




// ──────────────────────────────────────────────── 🎬 End ────────────────────────────────────────────────

