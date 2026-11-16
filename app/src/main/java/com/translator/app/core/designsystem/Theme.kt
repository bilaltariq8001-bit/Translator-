package com.translator.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every colour from the design spec. Screens read from here, never from literals. */
object Tk {
    val Bg = Color(0xFFF4F6FB)
    val Surface = Color(0xFFFFFFFF)
    val Surface2 = Color(0xFFEDF1F9)
    val Line = Color(0xFFE3E8F1)
    val Text = Color(0xFF0F1B33)
    val Soft = Color(0xFF475569)
    val Muted = Color(0xFF7B8599)
    val Primary = Color(0xFF1457F2)
    val PrimaryContainer = Color(0xFFE1EAFF)
    val Coral = Color(0xFFFF6B4A)
    val CoralContainer = Color(0xFFFFE7E0)
    val Sand = Color(0xFFE9B44C)
    val SandContainer = Color(0xFFF7E8C6)
    val Ok = Color(0xFF2E9E6A)
    val OkContainer = Color(0xFFDFF1E7)
    val OkText = Color(0xFF23784F)
    val Warn = Color(0xFFD98E04)
    val WarnContainer = Color(0xFFFBEFD6)
    val WarnText = Color(0xFF8A5A00)
    val Danger = Color(0xFFD64545)
    val DangerContainer = Color(0xFFF8E1DE)
    val OfflineBadge = Color(0xFF5B6573)
    val Off = Color(0xFFC5CDDD)
    val Faded = Color(0xFF9AA4B8)
}

/** Radii from the spec: 999 pills, 14-16 buttons, 20 cards, 24 composer/dialogs, 28 sheet tops. */
object Radius {
    val Pill = RoundedCornerShape(999.dp)
    val Button = RoundedCornerShape(16.dp)
    val Segment = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
    val Composer = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/**
 * Space Grotesk, Inter and the Noto family are not bundled yet (adding font files needs a download, which is
 * waiting for approval), so these use the system families. Only this object has to change when the fonts arrive.
 */
object Fonts {
    val Display: FontFamily = FontFamily.SansSerif
    val Body: FontFamily = FontFamily.SansSerif
}

object Type {
    val Title = TextStyle(fontFamily = Fonts.Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp)
    val Headline = TextStyle(fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val Result = TextStyle(fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp)
    val Input = TextStyle(fontFamily = Fonts.Body, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp)
    val Body = TextStyle(fontFamily = Fonts.Body, fontSize = 15.sp, lineHeight = 22.sp)
    val BodyStrong = TextStyle(fontFamily = Fonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val Caption = TextStyle(fontFamily = Fonts.Body, fontSize = 13.sp, lineHeight = 18.sp)
    val Label = TextStyle(fontFamily = Fonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp)
    val Button = TextStyle(fontFamily = Fonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
}

private val Scheme = lightColorScheme(
    primary = Tk.Primary,
    onPrimary = Color.White,
    primaryContainer = Tk.PrimaryContainer,
    onPrimaryContainer = Tk.Primary,
    secondary = Tk.Coral,
    background = Tk.Bg,
    onBackground = Tk.Text,
    surface = Tk.Surface,
    onSurface = Tk.Text,
    surfaceVariant = Tk.Surface2,
    onSurfaceVariant = Tk.Soft,
    outline = Tk.Line,
    error = Tk.Danger,
    errorContainer = Tk.DangerContainer,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TranslatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        shapes = AppShapes,
        typography = Typography(bodyLarge = Type.Body, bodyMedium = Type.Caption, titleLarge = Type.Headline),
        content = content,
    )
}
