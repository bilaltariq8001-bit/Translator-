package com.translator.app.core.ai

/** A line as the text reader reported it, before clean-up. [confidence] is 0 to 1, or 0 when the reader gave none. */
data class RawOcrLine(val line: ScannedLine, val confidence: Float)

private val fileNameLine = Regex(
    "^[\\w\\-. ()]+\\.(json|py|kt|kts|xml|png|jpe?g|webp|gif|apk|aab|txt|md|gradle|js|ts|tsx|html|css|zip|pdf|csv|yml|yaml|properties|toml)$",
    RegexOption.IGNORE_CASE,
)

private const val OPENERS = "¿¡(\"“‘'[«"
private const val CLOSERS = ".,!?;:)\"”’']»…%"

/**
 * Tidies one recognised line: removes the bullets, arrows, check marks and icon shapes the reader sometimes reports at the
 * start of a line (and stray marks at the end), and collapses extra spaces. Opening quotes and Spanish marks are kept.
 */
internal fun cleanOcrLine(raw: String): String {
    var t = raw.replace(' ', ' ').replace("​", "").trim()
    t = t.dropWhile { !it.isLetterOrDigit() && it !in OPENERS }
    t = t.trimEnd { !it.isLetterOrDigit() && it !in CLOSERS }
    return t.replace(Regex("\\s+"), " ").trim()
}

/** A line that is only a file name (like google-services.json) is screen clutter, not text to translate. */
internal fun isFileNameLine(text: String): Boolean = fileNameLine.matches(text.trim())

/**
 * Keeps the real text of the page: cleans every line, then drops lines that are file names, nearly all symbols, one
 * character long, low confidence, or much smaller than the rest (background text, labels on a screen behind the page).
 */
internal fun cleanScannedLines(raw: List<RawOcrLine>): List<ScannedLine> {
    val cleaned = raw.mapNotNull { r ->
        val text = cleanOcrLine(r.line.text)
        val alnum = text.count { it.isLetterOrDigit() }
        when {
            alnum < 2 -> null
            alnum * 2 < text.length -> null
            isFileNameLine(text) -> null
            r.confidence in 0.01f..0.4f -> null
            else -> RawOcrLine(r.line.copy(text = text), r.confidence)
        }
    }
    if (cleaned.size < 5) return cleaned.map { it.line }
    val heights = cleaned.map { it.line.bottom - it.line.top }.sorted()
    val median = heights[heights.size / 2]
    val kept = cleaned.filter { it.line.bottom - it.line.top >= median * 0.45f }
    return (if (kept.isEmpty()) cleaned else kept).map { it.line }
}
