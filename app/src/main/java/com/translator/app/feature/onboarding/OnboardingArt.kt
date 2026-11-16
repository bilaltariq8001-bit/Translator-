package com.translator.app.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.R
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.i18n.str
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// The three illustrations of the guide. They are drawn in code (no image files), use the app's own icons and colours, and
// move only a little: floating, a waveform, a scan line. Every animated value is read while drawing, and the endless
// motions stop when system animations are off. The art is decorative, so screen readers skip it (the page text carries
// the meaning).

private const val ART_W = 310
private const val ART_H = 330

/** Scales a fixed-size drawing down (or slightly up) to whatever room the page leaves, so it never overflows. */
@Composable
internal fun FitArt(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value / ART_W, maxHeight.value / ART_H, 1.12f).coerceAtLeast(0.55f)
        Box(Modifier.size(ART_W.dp, ART_H.dp).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) { content() }
    }
}

/** A soft glow behind the art, so it floats on the page instead of sitting on it. */
@Composable
private fun Glow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center = c, radius = size.minDimension / 2), radius = size.minDimension / 2, center = c)
    }
}

@Composable
private fun loop(enabled: Boolean, periodMillis: Int, label: String): State<Float> {
    if (!enabled) return remember { object : State<Float> { override val value = 0f } }
    return rememberInfiniteTransition(label = label).animateFloat(0f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart), label = label)
}

private fun Modifier.floating(phase: State<Float>, amplitudeDp: Float, offset: Float = 0f): Modifier =
    graphicsLayer { translationY = sin((phase.value + offset) * 2f * PI.toFloat()) * amplitudeDp.dp.toPx() }

// ---------------------------------------------------------------- 1. Voice

@Composable
internal fun VoiceArt(motion: Boolean) {
    val float = loop(motion, 5200, "voice-float")
    val wave = loop(motion, 1500, "voice-wave")
    val ring = loop(motion, 2400, "voice-ring")
    FitArt(Modifier.fillMaxSize()) {
        Glow(Tk.Primary)
        // What you said.
        Column(
            Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 26.dp).width(214.dp).floating(float, 4f)
                .graphicsLayer { rotationZ = -2.5f }
                .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Tk.Primary.copy(alpha = 0.08f), spotColor = Tk.Primary.copy(alpha = 0.22f))
                .clip(RoundedCornerShape(26.dp)).background(Tk.Surface).border(1.dp, Tk.Line.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(str(R.string.you_english), color = Tk.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(str(R.string.good_morning), color = Tk.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        // What they hear.
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 26.dp).width(214.dp).floating(float, 4f, offset = 0.5f)
                .graphicsLayer { rotationZ = 2.5f }
                .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Tk.Primary.copy(alpha = 0.12f), spotColor = Tk.Primary.copy(alpha = 0.45f))
                .clip(RoundedCornerShape(26.dp)).background(BrandGradient)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(str(R.string.them_japanese), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("おはようございます", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        // The microphone with two rings spreading out of it, and a waveform behind.
        Canvas(Modifier.size(150.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            for (k in 0..1) {
                val p = (ring.value + k * 0.5f) % 1f
                drawCircle(Tk.Coral.copy(alpha = (1f - p) * 0.32f), radius = size.minDimension * (0.2f + 0.3f * p), center = c)
            }
            // Seven bars, each with its own phase.
            val barW = 5.dp.toPx()
            for (i in 0 until 7) {
                val x = c.x + (i - 3) * 14.dp.toPx() + (if (i < 3) -34.dp.toPx() else if (i > 3) 34.dp.toPx() else 0f)
                val h = (10 + 26 * abs(sin((wave.value * 2f + i * 0.17f) * PI.toFloat()))).dp.toPx()
                drawLine(Tk.Primary.copy(alpha = 0.55f), Offset(x, c.y - h / 2), Offset(x, c.y + h / 2), strokeWidth = barW, cap = StrokeCap.Round)
            }
        }
        Box(
            Modifier.size(62.dp).shadow(14.dp, CircleShape, ambientColor = Tk.Coral.copy(alpha = 0.2f), spotColor = Tk.Coral.copy(alpha = 0.6f))
                .clip(CircleShape).background(Brush.linearGradient(listOf(Tk.Coral, Color(0xFFFF9A7A)))),
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Mic, Color.White, size = 30.dp) }
    }
}

// ---------------------------------------------------------------- 2. Camera

@Composable
internal fun CameraArt(motion: Boolean) {
    val scan = loop(motion, 3200, "camera-scan")
    val float = loop(motion, 5200, "camera-float")
    FitArt(Modifier.fillMaxSize()) {
        Glow(Color(0xFF0B7A6C))
        // The viewfinder.
        Box(
            Modifier.size(214.dp, 286.dp)
                .shadow(22.dp, RoundedCornerShape(34.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(34.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0B1220)))),
            contentAlignment = Alignment.Center,
        ) {
            // The sign being read.
            Box(
                Modifier.padding(horizontal = 26.dp).fillMaxSize().padding(vertical = 78.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xFF1E40AF)).border(2.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("出口", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp) }
            // Corner brackets and the scan line, drawn together.
            Canvas(Modifier.fillMaxSize()) {
                val m = 22.dp.toPx(); val len = 30.dp.toPx(); val w = 3.5.dp.toPx(); val white = Color.White
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(white, Offset(x, y), Offset(x + dx * len, y), w, StrokeCap.Round)
                    drawLine(white, Offset(x, y), Offset(x, y + dy * len), w, StrokeCap.Round)
                }
                corner(m, m, 1f, 1f); corner(size.width - m, m, -1f, 1f); corner(m, size.height - m, 1f, -1f); corner(size.width - m, size.height - m, -1f, -1f)
                val y = m + (size.height - 2 * m) * scan.value
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Tk.Coral.copy(alpha = 0.35f), Color.Transparent), startY = y - 26.dp.toPx(), endY = y + 26.dp.toPx()), Offset(m, y - 26.dp.toPx()), Size(size.width - 2 * m, 52.dp.toPx()))
                drawLine(Tk.Coral, Offset(m, y), Offset(size.width - m, y), 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
        // The translation, floating over the bottom edge.
        androidx.compose.foundation.layout.Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).floating(float, 3f)
                .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.28f))
                .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Tk.Ok), contentAlignment = Alignment.Center) { AppIcon(AppIcons.Check, Color.White, size = 18.dp) }
            Text(str(R.string.exit), color = Color(0xFF0F172A), fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ---------------------------------------------------------------- 3. All tools

@Composable
internal fun ToolsArt(motion: Boolean) {
    val float = loop(motion, 5600, "tools-float")
    val spin = loop(motion, 24000, "tools-spin")
    FitArt(Modifier.fillMaxSize()) {
        Glow(Color(0xFF5B3FD6))
        // Thin spokes from the centre to each tool.
        Canvas(Modifier.size(260.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * 0.36f
            drawCircle(Tk.Primary.copy(alpha = 0.10f), radius = r, center = c, style = Stroke(1.5.dp.toPx()))
            for (k in 0 until 4) {
                val a = (k * 90 + 45) * PI.toFloat() / 180f + spin.value * 2f * PI.toFloat() * 0.0f
                drawLine(Tk.Primary.copy(alpha = 0.14f), c, Offset(c.x + r * cos(a), c.y + r * sin(a)), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        ToolTile(AppIcons.Text, str(R.string.text), listOf(Tk.Primary, Color(0xFF4C86F0)), Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 30.dp).floating(float, 5f, 0f))
        ToolTile(AppIcons.Wave, str(R.string.voice), listOf(Color(0xFF5B3FD6), Color(0xFF8C6CF2)), Modifier.align(Alignment.TopEnd).padding(end = 22.dp, top = 52.dp).floating(float, 5f, 0.25f))
        ToolTile(AppIcons.Camera, str(R.string.photo), listOf(Color(0xFF0B7A6C), Color(0xFF1FA68F)), Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 52.dp).floating(float, 5f, 0.5f))
        ToolTile(AppIcons.Sparkle, str(R.string.tools), listOf(Tk.Coral, Color(0xFFFF9A7A)), Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 30.dp).floating(float, 5f, 0.75f))
        // The app itself, in the middle.
        Box(
            Modifier.size(92.dp).shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = Tk.Primary.copy(alpha = 0.2f), spotColor = Tk.Primary.copy(alpha = 0.55f))
                .clip(RoundedCornerShape(30.dp)).background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Translate, Color.White, size = 46.dp) }
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, colors: List<Color>, modifier: Modifier) {
    Column(
        modifier.width(84.dp)
            .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = colors.first().copy(alpha = 0.08f), spotColor = colors.first().copy(alpha = 0.3f))
            .clip(RoundedCornerShape(24.dp)).background(Tk.Surface).border(1.dp, Tk.Line.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) { AppIcon(icon, Color.White, size = 21.dp) }
        Text(label, color = Tk.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 7.dp, start = 4.dp, end = 4.dp))
    }
}
