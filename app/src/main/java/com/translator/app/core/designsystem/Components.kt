package com.translator.app.core.designsystem

import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Page padding: clamp(12, 5.2vw, 20). */
fun pagePadding(widthDp: Float): Dp = (widthDp * 0.052f).coerceIn(12f, 20f).dp

@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(Tk.PrimaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(code.uppercase(), color = Tk.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, maxLines = 1)
    }
}

@Composable
fun AppIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/**
 * The blue download circle. Drawn directly on a canvas instead of as a vector icon, because long lists create dozens of
 * these and building a vector painter for each one made scrolling stutter.
 */
@Composable
fun DownloadCircleButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
            val c = center
            val stroke = 2.dp.toPx()
            val r = size.minDimension / 2f - stroke
            drawCircle(Tk.Primary, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            val u = size.minDimension / 24f
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            drawLine(Tk.Primary, androidx.compose.ui.geometry.Offset(c.x, c.y - 5 * u), androidx.compose.ui.geometry.Offset(c.x, c.y + 3.5f * u), stroke, cap)
            drawLine(Tk.Primary, androidx.compose.ui.geometry.Offset(c.x - 3.5f * u, c.y + 0.2f * u), androidx.compose.ui.geometry.Offset(c.x, c.y + 3.7f * u), stroke, cap)
            drawLine(Tk.Primary, androidx.compose.ui.geometry.Offset(c.x + 3.5f * u, c.y + 0.2f * u), androidx.compose.ui.geometry.Offset(c.x, c.y + 3.7f * u), stroke, cap)
        }
    }
}

/** A 44dp round icon button; [description] is read by TalkBack. */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Tk.Soft,
    size: Dp = 22.dp,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, tint, size = size) }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    Row(
        modifier
            .height(height)
            .clip(Radius.Button)
            .background(if (enabled) Tk.Primary else Tk.Off)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIcon(icon, Color.White, size = 20.dp)
            Box(Modifier.width(10.dp))
        }
        Text(text, color = Color.White, style = Type.Button, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tinted: Boolean = false) {
    val bg = when {
        selected && !tinted -> Tk.Primary
        selected -> Tk.PrimaryContainer
        else -> Tk.Surface2
    }
    val fg = when {
        selected && !tinted -> Color.White
        selected -> Tk.Primary
        else -> Tk.Soft
    }
    Box(
        modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(Radius.Pill)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, Tk.Line),
    leftEdge: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .clip(Radius.Card)
            .background(Tk.Surface)
            .border(border, Radius.Card)
            .height(IntrinsicSize.Min),
    ) {
        if (leftEdge != null) Box(Modifier.width(4.dp).fillMaxHeight().background(leftEdge))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Tk.Muted) {
    Text(text.uppercase(), color = color, style = Type.Label, modifier = modifier)
}

enum class TranslateMode(@androidx.annotation.StringRes val label: Int, val icon: ImageVector) {
    Text(R.string.text, AppIcons.Text),
    Voice(R.string.voice, AppIcons.Mic),
    Camera(R.string.camera, AppIcons.Camera),
    Chat(R.string.chat, AppIcons.Chat),
}

/** The segmented pill at the top of the four translate modes. Icons hide on narrow screens (<= 320dp). */
@Composable
fun ModeSwitch(selected: TranslateMode, onSelect: (TranslateMode) -> Unit, showIcons: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(Radius.Pill).background(Tk.Surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TranslateMode.entries.forEach { mode ->
            val on = mode == selected
            Row(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(Radius.Pill)
                    .background(if (on) Tk.Primary else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(mode) }
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (on) Color.White else Tk.Soft
                if (showIcons) {
                    AppIcon(mode.icon, tint, size = 16.dp)
                    Box(Modifier.width(6.dp))
                }
                Text(str(mode.label), color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Source and target language pill bar with the Swap chip. [compact] shows only the swap icon. */
@Composable
fun LanguagePillBar(
    fromCode: String,
    fromName: String,
    toCode: String,
    toName: String,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onSwap: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(Radius.Pill).background(Tk.Surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguagePill(fromCode, fromName, str(R.string.translate_from, fromName), onFrom, Modifier.weight(1f), compact)
        Row(
            Modifier
                .height(40.dp)
                .clip(Radius.Pill)
                .background(Tk.Bg)
                .border(1.dp, Tk.Line, Radius.Pill)
                .clickable(role = Role.Button, onClick = onSwap)
                .semantics { contentDescription = str(R.string.swap_languages) }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(AppIcons.Swap, Tk.Primary, size = 16.dp)
            if (!compact) Text(str(R.string.swap), color = Tk.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        LanguagePill(toCode, toName, str(R.string.translate_to, toName), onTo, Modifier.weight(1f), compact)
    }
}

@Composable
private fun LanguagePill(code: String, name: String, description: String, onClick: () -> Unit, modifier: Modifier, compact: Boolean) {
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(Radius.Pill)
            .background(Tk.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodeBadge(code, size = if (compact) 26.dp else 30.dp)
        Box(Modifier.width(if (compact) 4.dp else 8.dp))
        Text(name, color = Tk.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
    }
}

/** A switch drawn to the design (52x32 track, 24dp thumb). */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShapeFull)
            .background(if (checked) Tk.Primary else Tk.Off)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .semantics { contentDescription = description }
            .padding(horizontal = 4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White)) }
}

private val RoundedCornerShapeFull = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)


// ---------------------------------------------------------------- Redesign pieces

/** Centred screen title with an optional back arrow and trailing actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(56.dp)) {
        if (onBack != null) IconAction(AppIcons.Back, str(R.string.back), onBack, Modifier.align(Alignment.CenterStart))
        Text(
            title, style = Type.Headline.copy(fontSize = 20.sp), color = Tk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        )
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun HistoryAction(onClick: () -> Unit) = IconAction(AppIcons.Clock, str(R.string.history), onClick, tint = Tk.Primary, size = 24.dp)

/** Two language names with a swap button between them, in one white pill. */
@Composable
fun LanguageBarCard(from: String, to: String, onFrom: () -> Unit, onTo: () -> Unit, onSwap: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(28.dp)).background(Tk.Surface).border(1.dp, Tk.Line, RoundedCornerShape(28.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageName(from, str(R.string.translate_from, from), onFrom, Modifier.weight(1f))
        IconAction(AppIcons.Swap, str(R.string.swap_languages), onSwap, tint = Tk.Primary, size = 24.dp)
        LanguageName(to, str(R.string.translate_to, to), onTo, Modifier.weight(1f))
    }
}

@Composable
private fun LanguageName(name: String, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(64.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Tk.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            // A small badge with a chevron: more languages are behind this name.
            Box(Modifier.padding(start = 6.dp).size(22.dp).clip(CircleShape).background(Tk.PrimaryContainer), contentAlignment = Alignment.Center) {
                AppIcon(AppIcons.Down, Tk.Primary, size = 16.dp)
            }
        }
    }
}

/** A soft grey pill with an optional leading icon and a chevron, for pickers like Style. */
@Composable
fun SoftPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.height(44.dp).clip(Radius.Pill).background(Tk.Surface2).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { AppIcon(icon, Tk.Primary, size = 18.dp); Box(Modifier.width(6.dp)) }
        Text(text, color = Tk.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Box(Modifier.width(2.dp))
        AppIcon(AppIcons.Down, Tk.Muted, size = 16.dp)
    }
}

/** Round action button; [pulse] adds a soft expanding ring (used on the big mic). */
@Composable
fun CircleAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    filled: Boolean = false,
    pulse: Boolean = false,
) {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    Box(modifier.size(size + if (pulse) 28.dp else 0.dp), contentAlignment = Alignment.Center) {
        if (pulse) {
            val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
            val ring by t.animateFloat(
                1f, 1.28f,
                androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800), androidx.compose.animation.core.RepeatMode.Restart),
                label = "ring",
            )
            Box(Modifier.size(size).graphicsLayer { scaleX = ring; scaleY = ring; alpha = (1.28f - ring) * 2.2f }.clip(CircleShape).background(Tk.Primary))
        }
        Box(
            Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                .background(if (filled) Tk.Primary else Tk.Surface).border(if (filled) 0.dp else 1.dp, Tk.Line, CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) { AppIcon(icon, if (filled) Color.White else Tk.Text, size = if (filled) 36.dp else 26.dp) }
    }
}

/** Rounded white group that holds settings rows. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Radius.Card).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Card).padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

/** A grey line with a band of light sweeping across it, used while a result is loading. */
@Composable
fun ShimmerLine(widthFraction: Float, modifier: Modifier = Modifier) {
    ShimmerBar(modifier.fillMaxWidth(widthFraction), 16.dp)
}

/** Look of an [ActionButton]: [Filled] is the main action (blue gradient), [Tonal] a quieter one (soft blue fill). */
enum class ActionStyle { Filled, Tonal }

/**
 * A full button with an icon and a label: 48dp tall, rounded, a clear fill, and a small press-down effect. Used for the
 * actions under an AI result (regenerate, copy, translate).
 */
@Composable
fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ActionStyle = ActionStyle.Tonal,
    enabled: Boolean = true,
) {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val shape = RoundedCornerShape(16.dp)
    val filled = style == ActionStyle.Filled
    val content = if (filled) Color.White else Tk.Primary
    Row(
        modifier.height(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (filled) Modifier.shadow(8.dp, shape, ambientColor = Tk.Primary, spotColor = Tk.Primary) else Modifier)
            .clip(shape)
            .background(if (filled) Brush.linearGradient(listOf(Tk.Primary, Color(0xFF4C86F0))) else Brush.linearGradient(listOf(Tk.PrimaryContainer, Tk.PrimaryContainer)))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        AppIcon(icon, content, size = 18.dp)
        Box(Modifier.width(8.dp))
        Text(label, color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
