package com.translator.app.feature.voice

import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.Tk
import kotlin.math.sin

val CoralDeep = Color(0xFFE8573A)
val CoralSoft = Color(0xFFFF7A57)

/** Five bars that rise and fall while listening. The motion is read only while drawing, so nothing recomposes. */
@Composable
fun ListeningBars(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "phase")
    Canvas(modifier) {
        val barW = size.width / 9f
        repeat(5) { i ->
            val h = size.height * (0.3f + 0.7f * (0.5f + 0.5f * sin(phase + i * 0.9f)))
            drawRoundRect(color, Offset(barW * (1 + i * 2f), (size.height - h) / 2f), Size(barW, h), CornerRadius(barW / 2f))
        }
    }
}

/**
 * The big round mic button: a coral gradient orb with a soft glow. While listening it shows moving bars and two rings
 * that expand outwards.
 */
@Composable
fun MicOrb(
    listening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    idleColors: List<Color> = listOf(CoralSoft, CoralDeep),
) {
    val t = rememberInfiniteTransition(label = "orb")
    val ring by t.animateFloat(1f, 1.7f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ring")
    val ring2 by t.animateFloat(1f, 1.7f, infiniteRepeatable(tween(1600, 800, FastOutSlowInEasing), RepeatMode.Restart), label = "ring2")
    Box(modifier.size(size * 1.8f), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(Modifier.size(size).graphicsLayer { scaleX = ring; scaleY = ring; alpha = (1.7f - ring) * 0.7f }.background(Tk.Coral, CircleShape))
            Box(Modifier.size(size).graphicsLayer { scaleX = ring2; scaleY = ring2; alpha = (1.7f - ring2) * 0.7f }.background(Tk.Coral, CircleShape))
        } else {
            Box(Modifier.size(size * 1.28f).background(idleColors.last().copy(alpha = 0.14f), CircleShape))
        }
        Box(
            Modifier.size(size)
                .shadow(14.dp, CircleShape, ambientColor = if (listening) Tk.Coral else idleColors.last(), spotColor = if (listening) Tk.Coral else idleColors.last())
                .clip(CircleShape).background(Brush.linearGradient(if (listening) listOf(CoralSoft, CoralDeep) else idleColors))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (listening) str(R.string.stop_listening) else str(R.string.tap_to_speak) },
            contentAlignment = Alignment.Center,
        ) {
            if (listening) ListeningBars(Color.White, Modifier.size(size * 0.42f))
            else AppIcon(AppIcons.Mic, Color.White, size = size * 0.42f)
        }
    }
}

/**
 * The speaker button shown with every translation in the voice screens (Manual, Conversation, Real-time, Headphone). Tap to
 * hear the translation read aloud; while that line is playing it becomes a stop button. It works the same whether the
 * reading was started by the button or automatically after the translation arrived.
 */
@Composable
fun SpeakButton(
    player: com.translator.app.core.audio.TtsPlayer,
    id: String,
    text: String,
    langCode: String,
    rate: Float,
    modifier: Modifier = Modifier,
) {
    val speaking = player.speakingId == id
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .background(if (speaking) Tk.Primary else Tk.PrimaryContainer)
            .clickable(role = Role.Button) { if (speaking) player.stop() else player.speak(text, langCode, rate, id) }
            .semantics { contentDescription = if (speaking) str(R.string.stop_reading) else str(R.string.listen) },
        contentAlignment = Alignment.Center,
    ) { AppIcon(if (speaking) AppIcons.Stop else AppIcons.Speaker, if (speaking) Color.White else Tk.Primary, size = 24.dp) }
}
