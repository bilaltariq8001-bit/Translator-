package com.translator.app.core.ai

import android.graphics.BitmapFactory
import android.graphics.Color

/** A line the AI model found: what it says, its translation, and its box as fractions (0 to 1) of the photo. */
internal data class BoxedLine(val text: String, val translation: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

private val objectPattern = Regex("\\{[^{}]*\\}")
private val boxPattern = Regex("\"box_2d\"\\s*:\\s*\\[\\s*([-\\d.\\s,]+)\\]")

private fun jsonString(obj: String, name: String): String? =
    Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(obj)?.groupValues?.get(1)?.let(::unescapeJson)

private fun unescapeJson(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (val n = s[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'u' -> if (i + 5 < s.length) { out.append(s.substring(i + 2, i + 6).toIntOrNull(16)?.toChar() ?: '?'); i += 4 }
                else -> out.append(n)
            }
            i += 2
        } else {
            out.append(c); i++
        }
    }
    return out.toString()
}

/**
 * Reads the model's answer: a JSON array of `{"box_2d":[ymin,xmin,ymax,xmax],"text":"...","translation":"..."}` where the
 * box values are 0 to 1000. Tolerates code fences and extra words around the array. Lines without a usable box or without
 * both texts are skipped.
 */
internal fun parseBoxedLines(answer: String): List<BoxedLine> = objectPattern.findAll(answer).mapNotNull { m ->
    val obj = m.value
    val nums = boxPattern.find(obj)?.groupValues?.get(1)?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: return@mapNotNull null
    if (nums.size != 4) return@mapNotNull null
    val (ymin, xmin, ymax, xmax) = nums.map { (it / 1000f).coerceIn(0f, 1f) }
    if (xmax <= xmin || ymax <= ymin) return@mapNotNull null
    val text = jsonString(obj, "text")?.trim().orEmpty()
    val translation = jsonString(obj, "translation")?.trim().orEmpty()
    if (text.isEmpty() || translation.isEmpty()) null else BoxedLine(text, translation, xmin, ymin, xmax, ymax)
}.toList()

/** The photo's own colour just above and below each box (left, top, right, bottom as fractions), as ARGB. */
internal fun sampleBackgrounds(jpeg: ByteArray, boxes: List<FloatArray>): List<Int> {
    val bitmap = try { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) } catch (_: Throwable) { null } ?: return boxes.map { Color.WHITE }
    return boxes.map { b ->
        val left = (b[0] * bitmap.width).toInt()
        val width = ((b[2] - b[0]) * bitmap.width).toInt().coerceAtLeast(1)
        val ys = listOf(((b[1] * bitmap.height).toInt() - 3), ((b[3] * bitmap.height).toInt() + 3)).map { it.coerceIn(0, bitmap.height - 1) }
        var r = 0; var g = 0; var bl = 0; var n = 0
        for (y in ys) for (i in 0 until 10) {
            val x = (left + width * (i * 2 + 1) / 20).coerceIn(0, bitmap.width - 1)
            val c = bitmap.getPixel(x, y)
            r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++
        }
        Color.rgb(r / n, g / n, bl / n)
    }
}
