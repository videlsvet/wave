package com.wave.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.blur
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
class AuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Автопропуск: если уже есть токен — сразу в MainActivity
        val prefs = getSharedPreferences("WavePrefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("user_token", null)

        if (!savedToken.isNullOrBlank()) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
            return
        }

        setContent { AuthScreen() }
    }
}

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isRegister by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    // API keys
    var spotifyId by remember { mutableStateOf("") }
    var spotifySecret by remember { mutableStateOf("") }
    var soundcloudId by remember { mutableStateOf("") }
    var vkToken by remember { mutableStateOf("") }
    var yandexToken by remember { mutableStateOf("") }
    var showApiKeys by remember { mutableStateOf(false) }

    // Avatar
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> avatarUri = uri }

    // Status
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loginSuccess by remember { mutableStateOf(false) }

    if (loginSuccess) {
        LaunchedEffect(Unit) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            if (context is ComponentActivity) context.finish()
        }
    }

    val bg = Brush.verticalGradient(
        listOf(Color(0xFF000000), Color(0xFF0A0613), Color(0xFF000000))
    )

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF15151A),
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {

            // Лёгкий “неоновый” ореол
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 60.dp)
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFBB86FC).copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(28.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 42.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    painter = painterResource(id = R.drawable.wave_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Wave",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = if (isRegister) "Создайте аккаунт" else "Войдите в аккаунт",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.72f)
                )

                Spacer(Modifier.height(18.dp))

                // Glass Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {

                        // Tabs (Вход/Регистрация)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(4.dp)
                        ) {
                            SegTab(
                                text = "Вход",
                                selected = !isRegister,
                                onClick = { isRegister = false },
                                modifier = Modifier.weight(1f)
                            )
                            SegTab(
                                text = "Регистрация",
                                selected = isRegister,
                                onClick = { isRegister = true },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Avatar only in registration
                        AnimatedVisibility(visible = isRegister) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                        .clickable(enabled = !loading) { pickImageLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val painter = rememberAsyncImagePainter(avatarUri)
                                    Image(
                                        painter = painter,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(CircleShape)
                                    )
                                    if (avatarUri == null) {
                                        Text(
                                            "Фото",
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        GlassTextField(
                            label = "Никнейм",
                            value = nickname,
                            onChange = { nickname = it },
                            enabled = !loading,
                            leading = { Icon(Icons.Default.Person, null, tint = Color.White.copy(0.75f)) }
                        )

                        Spacer(Modifier.height(10.dp))

                        GlassTextField(
                            label = "Пароль",
                            value = password,
                            onChange = { password = it },
                            enabled = !loading,
                            isPassword = !passwordVisible,
                            leading = { Icon(Icons.Default.Lock, null, tint = Color.White.copy(0.75f)) },
                            trailing = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        )

                        // API keys (only in registration)
                        AnimatedVisibility(visible = isRegister) {
                            Column {
                                Spacer(Modifier.height(10.dp))

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable(enabled = !loading) { showApiKeys = !showApiKeys }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        null,
                                        tint = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "API ключи (необязательно)",
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        if (showApiKeys) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null,
                                        tint = Color.White.copy(alpha = 0.75f)
                                    )
                                }

                                AnimatedVisibility(visible = showApiKeys) {
                                    Column(Modifier.padding(top = 10.dp)) {
                                        GlassTextField("Spotify Client ID", spotifyId, { spotifyId = it }, enabled = !loading)
                                        Spacer(Modifier.height(8.dp))
                                        GlassTextField("Spotify Client Secret", spotifySecret, { spotifySecret = it }, enabled = !loading)
                                        Spacer(Modifier.height(8.dp))
                                        GlassTextField("SoundCloud Client ID", soundcloudId, { soundcloudId = it }, enabled = !loading)
                                        Spacer(Modifier.height(8.dp))
                                        GlassTextField("VK Access Token", vkToken, { vkToken = it }, enabled = !loading)
                                        Spacer(Modifier.height(8.dp))
                                        GlassTextField("Yandex Music Token", yandexToken, { yandexToken = it }, enabled = !loading)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        val canSubmit = nickname.isNotBlank() && password.length >= 3 && !loading

                        Button(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    message = null

                                    val res = try {
                                        if (isRegister) {
                                            registerUser(
                                                context = context,
                                                nickname = nickname.trim(),
                                                password = password,
                                                avatarUri = avatarUri,
                                                spotifyId = spotifyId.trim(),
                                                spotifySecret = spotifySecret.trim(),
                                                soundcloudId = soundcloudId.trim(),
                                                vkToken = vkToken.trim(),
                                                yandexToken = yandexToken.trim()
                                            )
                                        } else {
                                            loginUser(context, nickname.trim(), password)
                                        }
                                    } finally {
                                        loading = false
                                    }

                                    message = res
                                    if (res.contains("усп", true)) loginSuccess = true
                                }
                            },
                            enabled = canSubmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFBB86FC),
                                disabledContainerColor = Color(0xFFBB86FC).copy(alpha = 0.35f)
                            )
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                if (isRegister) "Создать аккаунт" else "Войти",
                                color = Color.Black,
                                fontWeight = FontWeight.Black
                            )
                        }

                        if (!message.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            val ok = message!!.contains("усп", true)
                            Text(
                                text = message!!,
                                color = if (ok) Color(0xFF7CFFB2) else Color(0xFFFF6B6B),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Продолжая, вы соглашаетесь с обработкой данных.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun SegTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color(0xFFBB86FC) else Color.Transparent
    val fg = if (selected) Color.Black else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun GlassTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    leading: (@Composable (() -> Unit))? = null,
    trailing: (@Composable (() -> Unit))? = null
) {
    TextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        placeholder = { Text(label, maxLines = 1) },
        leadingIcon = leading,
        trailingIcon = trailing,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.07f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            disabledContainerColor = Color.White.copy(alpha = 0.04f),

            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,

            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.6f),

            cursorColor = Color(0xFFBB86FC),

            focusedPlaceholderColor = Color.White.copy(alpha = 0.55f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.50f),
            disabledPlaceholderColor = Color.White.copy(alpha = 0.35f),

            focusedLeadingIconColor = Color.White.copy(alpha = 0.75f),
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.70f),

            focusedTrailingIconColor = Color.White.copy(alpha = 0.80f),
            unfocusedTrailingIconColor = Color.White.copy(alpha = 0.75f),
        )
    )
}


/* 🌐 SERVER */
private const val ENDPOINT = "https://functions.yandexcloud.net/d4e55oqrims414ecgmtd"

suspend fun registerUser(
    context: Context,
    nickname: String,
    password: String,
    avatarUri: Uri?,
    spotifyId: String,
    spotifySecret: String,
    soundcloudId: String,
    vkToken: String,
    yandexToken: String
): String = withContext(Dispatchers.IO) {

    val client = OkHttpClient()
    val media = "application/json".toMediaType()

    try {
        val avatarBase64 = avatarUri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: ByteArray(0)
            "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } ?: ""

        // REGISTER
        val regBody = JSONObject()
            .put("action", "register")
            .put("nickname", nickname)
            .put("password", password)
            .put("avatar_url", avatarBase64)
            .toString()
            .toRequestBody(media)

        val regRes = client.newCall(
            Request.Builder().url(ENDPOINT).post(regBody).build()
        ).execute()

        val regStr = regRes.body?.string() ?: return@withContext "Ошибка: пустой ответ"
        Log.d("Auth", "📥 Register: $regStr")

        // LOGIN (получаем token)
        val loginMsg = loginUser(context, nickname, password)
        if (!loginMsg.contains("усп", true)) return@withContext loginMsg

        val prefs = context.getSharedPreferences("WavePrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("user_token", null) ?: return@withContext "Ошибка: токен не получен"

        // SAVE KEYS, если есть
        if (listOf(spotifyId, spotifySecret, soundcloudId, vkToken, yandexToken).any { it.isNotBlank() }) {
            val keysBody = JSONObject()
                .put("action", "save_keys")
                .put("token", token)
                .put("spotify_id", spotifyId)
                .put("spotify_secret", spotifySecret)
                .put("soundcloud_id", soundcloudId)
                .put("yandex_token", yandexToken)
                .put("vkmusic_token", vkToken)
                .toString()
                .toRequestBody(media)

            client.newCall(Request.Builder().url(ENDPOINT).post(keysBody).build()).execute()
        }

        return@withContext "Аккаунт создан успешно! 🎉"

    } catch (e: Exception) {
        Log.e("Auth", "❌ REG ERROR", e)
        return@withContext "Ошибка: ${e.localizedMessage}"
    }
}

suspend fun loginUser(context: Context, nickname: String, password: String): String =
    withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("action", "login")
                .put("nickname", nickname)
                .put("password", password)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val res = OkHttpClient().newCall(
                Request.Builder().url(ENDPOINT).post(body).build()
            ).execute()

            val str = res.body?.string() ?: return@withContext "Ошибка: пустой ответ"

            val json = JSONObject(str)
            val token = json.optString("token", "")

            return@withContext if (token.isNotBlank()) {
                context.getSharedPreferences("WavePrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("user_token", token)
                    .apply()
                "Вход выполнен успешно!"
            } else {
                "Ошибка входа: ${json.optString("error", "неизвестная")}"
            }

        } catch (e: Exception) {
            "Ошибка: ${e.localizedMessage}"
        }
    }
