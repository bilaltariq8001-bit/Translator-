package com.translator.app.core.designsystem

import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Motion for waiting on an AI answer. Every animated value is read only while drawing, so the animations repaint without
 * recomposing or re-measuring anything and stay light on the phone.
 */

private val Violet = Color(0xFF8C6CF2)

/** A soft glowing orb with a slowly turning sparkle: "the AI is working". */
@Composable
fun SparkleOrb(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Tk.Primary.copy(alpha = 0.30f), Color.Transparent), center = c, radius = r), radius = r * pulse, center = c)
        drawCircle(Brush.linearGradient(listOf(Tk.Primary, Violet)), radius = r * 0.62f, center = c)
        rotate(turn, pivot = c) {
            val star = Path().apply {
                val outer = r * 0.34f
                val inner = outer * 0.3f
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45 - 90).toDouble())
                    val d = if (i % 2 == 0) outer else inner
                    val x = c.x + (d * cos(a)).toFloat()
                    val y = c.y + (d * sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(star, Color.White)
        }
    }
}

/** Three dots that rise and fall in turn. */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier, color: Color = Tk.Primary, dot: Dp = 7.dp) {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.size(width = dot * 4.2f, height = dot * 2.2f)) {
        val d = dot.toPx()
        repeat(3) { i ->
            val lift = (0.5f + 0.5f * sin(phase - i * 0.9f)) * d * 0.6f
            drawCircle(color.copy(alpha = 0.55f + 0.45f * (lift / (d * 0.6f))), radius = d / 2f, center = Offset(d / 2f + i * d * 1.6f, size.height - d * 0.7f - lift))
        }
    }
}

/** A grey bar with a band of light sweeping across it. */
@Composable
fun ShimmerBar(modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val corner = CornerRadius(size.height / 2f)
        drawRoundRect(Tk.Line, cornerRadius = corner)
        val band = size.width * 0.45f
        val x = -band + (size.width + band) * sweep
        clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, corner)) }) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent), startX = x, endX = x + band),
                topLeft = Offset(x, 0f), size = Size(band, size.height),
            )
        }
    }
}

/** A card shown while an AI answer is on its way: the orb and dots, then lines of text that shimmer. */
@Composable
fun AiThinkingCard(modifier: Modifier = Modifier, lines: Int = 3) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Tk.Surface).border(1.dp, Tk.Line, shape).padding(16.dp)
            .semantics { contentDescription = str(R.string.waiting_for_the_ai_answer) },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SparkleOrb(size = 34.dp)
            ThinkingDots()
        }
        val widths = listOf(1f, 0.82f, 0.6f, 0.4f)
        repeat(lines) { i -> ShimmerBar(Modifier.fillMaxWidth(widths[i % widths.size])) }
        if (rememberSlow()) Text(str(R.string.ai_still_working), color = Tk.Muted, fontSize = 12.sp)
    }
}

/** The chat bubble shown before the first words of a reply arrive. */
@Composable
fun ThinkingBubble(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Row(
        modifier.clip(shape).background(Tk.Surface).border(1.dp, Tk.Line, shape).padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = str(R.string.the_assistant_is_thinking) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SparkleOrb(size = 28.dp)
        ThinkingDots()
        if (rememberSlow()) Text(str(R.string.ai_still_working), color = Tk.Muted, fontSize = 12.sp)
    }
}

/** True once an AI answer has been awaited for a while, so the screen can say it is still working. */
@Composable
internal fun rememberSlow(afterMillis: Long = 8_000): Boolean {
    val slow = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(afterMillis); slow.value = true }
    return slow.value
}

/** A phrase card that shimmers while the phrases are being written. */
@Composable
fun PhraseSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Tk.Surface).border(1.dp, Tk.Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShimmerBar(Modifier.fillMaxWidth(0.45f), 10.dp)
        ShimmerBar(Modifier.fillMaxWidth(0.85f), 18.dp)
    }
}
