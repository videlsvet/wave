package com.wave.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun DiscBeatGlow(
    level: Float,     // 0..1
    beatTick: Long,   // увеличивается на каждом "пике"
    modifier: Modifier = Modifier,
    palette: List<Color> = listOf(
        Color(0xFF7CF7FF),
        Color(0xFFE600FF),
        Color(0xFFFF2E88),
        Color(0xFF00E5FF),
        Color(0xFF7CFFB2),
        Color(0xFFFFE27A),
        Color(0xFF7CA6FF),
        Color(0xFFFF7A00)
    ),
    content: @Composable () -> Unit
) {
    var target by remember { mutableStateOf(palette.random()) }

    // новый цвет на каждый beat, без повтора подряд
    LaunchedEffect(beatTick) {
        val candidates = palette.filter { it != target }
        target = (if (candidates.isEmpty()) palette else candidates)[Random.nextInt(
            (if (candidates.isEmpty()) palette else candidates).size
        )]
    }

    val glowColor by animateColorAsState(
        targetValue = target,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "glowColor"
    )

    val intensity = (0.10f + level * 0.60f).coerceIn(0.08f, 0.75f)
    val scale = 0.92f + level * 0.22f

    val inf = rememberInfiniteTransition(label = "breath")
    val breathe by inf.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(310.dp)
                .scale(scale * breathe)
                .clip(CircleShape)
                .alpha(intensity)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.85f),
                            glowColor.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        radius = 520f
                    )
                )
                .blur(26.dp)
        )

        Box(
            Modifier
                .size(250.dp)
                .scale(scale * 0.985f * breathe)
                .clip(CircleShape)
                .alpha(intensity * 0.75f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.70f),
                            Color.Transparent
                        ),
                        radius = 420f
                    )
                )
                .blur(18.dp)
        )

        content()
    }
}
