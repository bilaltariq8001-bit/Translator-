package com.translator.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val Deep = Color(0xFF0A2FB8)
private val Brand = Color(0xFF1457F2)
private val Violet = Color(0xFF7B5CF0)
private val glyphs = listOf("文", "A", "ع", "あ", "Ñ", "한", "हि")

/**
 * The opening animation, shown over the app for about two seconds on every cold start. It begins from the system start screen (the logo in a blue disc),
 * so the app appears to start with the animation: the disc grows from the centre into a
 * gradient with drifting light, the logo lifts, the name rises in, and glass discs with letters from other writing
 * systems orbit the logo. It ends with a soft fade into the app. Every value is read while drawing, so it stays smooth.
 */
@Composable
fun StartupIntro(onShown: () -> Unit = {}, onHold: suspend () -> Unit = {}, onFinished: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    val letters = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "intro")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "orbit")
    val drift by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(5_000, easing = LinearEasing), RepeatMode.Restart), label = "drift")

    LaunchedEffect(Unit) {
        // Wait for the first drawn frame, tell the activity (which then lifts the system splash), and only then start the clock.
        androidx.compose.runtime.withFrameNanos { }
        onShown()
        androidx.compose.runtime.withFrameNanos { }
        coroutineScope {
            launch { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            launch { delay(250); letters.animateTo(1f, tween(950, easing = FastOutSlowInEasing)) }
            launch { delay(480); title.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        }
        delay(450)
        // The consent check and an optional splash ad run here, over the intro's last frame; every path comes back.
        onHold()
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }

    val measurer = rememberTextMeasurer()
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value; scaleX = 1f + 0.05f * exit.value; scaleY = 1f + 0.05f * exit.value }
            .background(Brand)
            .pointerInput(Unit) { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // The blue deepens into a gradient with a violet corner.
            // The blue grows from the middle of the blank start screen until it fills it.
            drawRect(Brush.linearGradient(listOf(Deep, Brand, Violet), start = Offset(0f, 0f), end = Offset(w, h)), alpha = reveal.value)
            // Soft light drifting slowly.
            val glow1 = Offset(w * (0.25f + 0.08f * sin(drift)), h * (0.2f + 0.05f * cos(drift)))
            val glow2 = Offset(w * (0.8f + 0.06f * cos(drift)), h * (0.78f + 0.05f * sin(drift)))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f * reveal.value), Color.Transparent), center = glow1, radius = w * 0.75f), radius = w * 0.75f, center = glow1)
            drawCircle(Brush.radialGradient(listOf(Violet.copy(alpha = 0.55f * reveal.value), Color.Transparent), center = glow2, radius = w * 0.7f), radius = w * 0.7f, center = glow2)

            // Letters from other writing systems on glass discs, orbiting the logo.
            val center = Offset(w / 2f, h / 2f - 24.dp.toPx() * reveal.value)
            val ring = minOf(150.dp.toPx(), w * 0.4f)
            val disc = 23.dp.toPx()
            glyphs.forEachIndexed { i, g ->
                val a = Math.toRadians((orbit + i * 360.0 / glyphs.size) - 90.0)
                val grow = letters.value
                val r = ring * (0.55f + 0.45f * grow)
                val c = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
                val alpha = grow
                drawCircle(Color.White.copy(alpha = 0.14f * alpha), radius = disc * (0.7f + 0.3f * grow), center = c)
                drawCircle(Color.White.copy(alpha = 0.38f * alpha), radius = disc * (0.7f + 0.3f * grow), center = c, style = Stroke(1.2.dp.toPx()))
                val layout = measurer.measure(g, TextStyle(color = Color.White.copy(alpha = alpha), fontSize = 19.sp, fontWeight = FontWeight.SemiBold))
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
            }
            // A faint ring the letters travel on.
            drawCircle(Color.White.copy(alpha = 0.10f * letters.value), radius = ring, center = center, style = Stroke(1.dp.toPx()))
        }

        // The same logo, at the same size and place as the system splash, then it settles upward and breathes.
        Image(
            painterResource(R.drawable.ic_splash_icon), null,
            Modifier.size(288.dp).offset(y = (-24).dp * reveal.value).graphicsLayer {
                val s = 1f - 0.14f * reveal.value + 0.015f * sin(drift)
                scaleX = s; scaleY = s
            },
        )

        Column(
            Modifier.offset(y = 168.dp).graphicsLayer { alpha = title.value; translationY = (1f - title.value) * 28.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("AI Translator", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(com.translator.app.core.i18n.str(com.translator.app.R.string.intro_tagline), color = Color.White.copy(alpha = 0.86f), fontSize = 15.sp, letterSpacing = 0.3.sp)
        }
    }
}
