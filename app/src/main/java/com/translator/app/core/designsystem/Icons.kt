package com.translator.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The stroke icons from the design (24x24, 2px round strokes), built from the same SVG path data. */
object AppIcons {
    /** [mirror]: the arrow flips to point the other way in right-to-left languages (back and "go on" arrows). */
    private fun stroke(name: String, d: String, fill: Boolean = false, mirror: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Text = stroke("text", "M4 6h16M4 12h10M4 18h7")
    val Mic = stroke("mic", "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M5 11a7 7 0 0 0 14 0 M12 18v3")
    val Camera = stroke("camera", "M4 8h3l2-3h6l2 3h3v11H4z M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z")
    val Chat = stroke("chat", "M4 5h16v11H9l-5 4z")
    val Swap = stroke("swap", "M7 7h13l-3-3M17 17H4l3 3")
    val Arrow = stroke("arrow", "M5 12h14M13 6l6 6-6 6")
    val Back = stroke("back", "M15 5l-7 7 7 7", mirror = true)
    val Speaker = stroke("speaker", "M4 9v6h4l5 4V5L8 9z M16 9a4 4 0 0 1 0 6 M18.5 6.5a8 8 0 0 1 0 11")
    val Copy = stroke("copy", "M9 9h11v11H9z M5 15H4V4h11v1")
    val Star = stroke("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")
    val StarFilled = stroke("starFilled", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z", fill = true)
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Check = stroke("check", "M5 12l5 5 9-10")
    val Shield = stroke("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z")
    val Translate = stroke("translate", "M4 5h8M8 3v2M5.5 5c1 3.5 3.5 6 6.5 7.5M10.5 5c-1 3.5-3.5 6-6.5 7.5M13 21l4-9 4 9M14.5 18h5")
    val Sparkle = stroke("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z")
    val Clock = stroke("clock", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 7v5l3 2")
    val Sliders = stroke("sliders", "M4 7h10M18 7h2M4 17h2M10 17h10 M16 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z M8 15a2 2 0 1 0 0 4 2 2 0 0 0 0-4z")
    val Close = stroke("close", "M6 6l12 12M18 6L6 18")
    val Mail = stroke("mail", "M4 6h16v12H4z M4 7l8 6 8-6")
    val Plane = stroke("plane", "M21 3L3 11l7 3 3 7z M10 14l11-11")
    val Chevron = stroke("chevron", "M9 5l7 7-7 7", mirror = true)
    val Stop = stroke("stop", "M7 7h10v10H7z", fill = true)
    val Refresh = stroke("refresh", "M20 12a8 8 0 1 1-2.3-5.7 M20 4v5h-5")
    val Keyboard = stroke("keyboard", "M3 7h18v10H3z M7 11h.01M11 11h.01M15 11h.01M8 14h8")
    val Pen = stroke("pen", "M4 20l4-1 11-11-3-3L5 16z")
    val Quote = stroke("quote", "M5 8h5v5H7l-2 3z M14 8h5v5h-3l-2 3z")
    val Convo = stroke("convo", "M4 5h10v7H8l-3 3z M10 12h10v7h-3l-3 3v-3h-4z")
    val Wave = stroke("wave", "M5 10v4M9 6v12M13 9v6M17 7v10M21 11v2")
    val Person = stroke("person", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 20c1-4 4-6 8-6s7 2 8 6")
    val Trash = stroke("trash", "M4 7h16 M10 11v6 M14 11v6 M6 7l1 13h10l1-13 M9 7V4h6v3")
    val Bookmark = stroke("bookmark", "M6 4h12v17l-6-4-6 4z")
    val Paste = stroke("paste", "M9 4h6v3H9z M7 5H5v16h14V5h-2")
    val ThumbUp = stroke("thumbUp", "M7 11v9H4v-9z M7 11l4-8c1.5 0 2.5 1 2.5 2.5V9h5.5a2 2 0 0 1 2 2.3l-1.2 7A2 2 0 0 1 17.8 20H7")
    val Dots = stroke("dots", "M5 12h.01M12 12h.01M19 12h.01")
    val Globe = stroke("globe", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M3 12h18 M12 3c3 3 3 15 0 18 M12 3c-3 3-3 15 0 18")
    val Info = stroke("info", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 8v.5 M12 11v5")
    val Down = stroke("down", "M6 9l6 6 6-6")
    val Headphones = stroke("headphones", "M4 14v-2a8 8 0 0 1 16 0v2 M4 14h3v5H4z M17 14h3v5h-3z")
    val DownloadCircle = stroke("downloadCircle", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 7v8 M8.5 12l3.5 3.5 3.5-3.5")
    val Gallery = stroke("gallery", "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M9 9h.01")
    val Flash = stroke("flash", "M13 2L5 13h6l-1 9 8-11h-6z")
    val Expand = stroke("expand", "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5")
    val Search = stroke("search", "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z M20 20l-4-4")
}
