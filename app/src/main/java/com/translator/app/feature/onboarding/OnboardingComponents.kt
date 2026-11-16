package com.translator.app.feature.onboarding

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.Tk

// Shared building blocks of the onboarding. One shape language (28 dp pills and 20 dp cards), one shadow recipe (a soft
// shadow tinted with the brand blue) and one accent keep the screens quiet; the art supplies the colour.

/** The brand gradient used by the main button, the progress line and the icon badges. */
internal val BrandGradient: Brush get() = Brush.linearGradient(listOf(Tk.Primary, Color(0xFF3B74F5)))

/** True unless the person has switched system animations off; the endless motions in the art honour it. */
@Composable
internal fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

/**
 * The main button: brand gradient, a soft tinted shadow, and a small press-down scale. Disabled it turns flat grey with
 * lighter text (it never relies on colour alone: it also stops responding and says so to screen readers).
 */
@Composable
internal fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingArrow: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = Tk.Primary.copy(alpha = 0.2f), spotColor = Tk.Primary.copy(alpha = 0.45f))
            .clip(shape)
            .background(if (enabled) BrandGradient else Brush.linearGradient(listOf(Tk.Off, Tk.Off)))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (!enabled) disabled() }
            .defaultMinSize(minHeight = 58.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, color = Color.White.copy(alpha = if (enabled) 1f else 0.85f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)
            if (trailingArrow) {
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Box(Modifier.graphicsLayer { rotationZ = if (rtl) 180f else 0f }) { AppIcon(AppIcons.Arrow, Color.White, size = 20.dp) }
            }
        }
    }
}

/** A quiet text button for Skip: visible, but never competing with the main button. */
@Composable
internal fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 56.dp, minHeight = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Tk.Soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

/** The round back button: white, hairline border, a soft shadow. */
@Composable
internal fun BackButton(description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp)
            .shadow(3.dp, CircleShape, ambientColor = Tk.Primary.copy(alpha = 0.08f), spotColor = Tk.Primary.copy(alpha = 0.18f))
            .clip(CircleShape).background(Tk.Surface).border(1.dp, Tk.Line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AppIcon(AppIcons.Back, Tk.Text, size = 20.dp) }   // the icon mirrors itself in right-to-left languages
}

/** A hairline progress line across the top: it shows overall progress without needing any words. */
@Composable
internal fun TopProgress(fraction: Float, description: String, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction, tween(450), label = "progress")
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tk.Line).semantics { contentDescription = description }) {
        Box(Modifier.fillMaxWidth(animated).height(4.dp).clip(RoundedCornerShape(2.dp)).background(BrandGradient))
    }
}

/** Page dots for the guide pager: the current one stretches into a pill. */
@Composable
internal fun PageDots(count: Int, current: Int, description: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 28.dp else 8.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dot")
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(if (i == current) Tk.Primary else Tk.Off))
        }
    }
}

/** A small rounded-square icon badge in the brand gradient (or any gradient): the same family on every screen. */
@Composable
internal fun IconBadge(icon: ImageVector, size: Dp = 52.dp, colors: List<Color> = listOf(Tk.Primary, Color(0xFF3B74F5))) {
    val shape = RoundedCornerShape(size / 3.1f)
    Box(
        Modifier.size(size).shadow(8.dp, shape, ambientColor = colors.first().copy(alpha = 0.18f), spotColor = colors.first().copy(alpha = 0.4f))
            .clip(shape).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, Color.White, size = size / 2.1f) }
}

/** The small label above a title, naming what the screen is about. */
@Composable
internal fun Eyebrow(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Tk.PrimaryContainer).padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(icon, Tk.Primary, size = 15.dp)
        Text(text.uppercase(), color = Tk.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
    }
}

/**
 * Fades and lifts its content into place once, after [delayMillis]. Both values are read while drawing, so the animation
 * costs no recomposition.
 */
@Composable
internal fun Modifier.reveal(delayMillis: Int = 0, enabled: Boolean = true): Modifier {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (enabled) { kotlinx.coroutines.delay(delayMillis.toLong()); progress.animateTo(1f, tween(480)) } }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/** A white card with the shared soft shadow, used to group related rows. */
@Composable
internal fun SoftCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Tk.Primary.copy(alpha = 0.06f), spotColor = Tk.Primary.copy(alpha = 0.14f))
            .clip(shape).background(Tk.Surface).border(1.dp, Tk.Line.copy(alpha = 0.7f), shape),
        content = content,
    )
}
