package com.translator.app.core.ai

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One recognised line of text, with its box (fractions of the photo) and the photo colour around it. */
data class ScannedLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val background: Int)

data class ScannedText(val text: String, val lines: List<ScannedLine> = emptyList())

/** Reads the text out of a photo (JPEG bytes). Returns empty text when there is none. */
fun interface PhotoTextReader {
    suspend fun read(jpeg: ByteArray): ScannedText
}

/**
 * On-device text recognition (ML Kit): Latin, Chinese, Japanese, Korean and Devanagari scripts. The photo never leaves the
 * phone. Arabic, Urdu and other scripts are not supported by this reader.
 */
class MlKitPhotoTextReader : PhotoTextReader {
    override suspend fun read(jpeg: ByteArray): ScannedText = withContext(Dispatchers.Default) {
        val bitmap = try { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) } catch (_: OutOfMemoryError) { null } ?: return@withContext ScannedText("")
        val image = InputImage.fromBitmap(bitmap, 0)
        // Each script reader is a separate on-device model. They are now opened one at a time and closed straight after use,
        // instead of all five being held in memory together, which ran low-memory phones (many Vivo Y models) out of memory.
        val readers: List<() -> com.google.mlkit.vision.text.TextRecognizer> = listOf(
            { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) },
            { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) },
            { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) },
            { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) },
            { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) },
        )
        try {
            // The script is not known up front, so every reader looks and the one that finds the most text wins.
            val best = readers.map { make ->
                val r = try { make() } catch (_: Throwable) { null }
                try { r?.let { runCatching { it.process(image).await() }.getOrNull() } } finally { try { r?.close() } catch (_: Throwable) { } }
            }.maxByOrNull { it?.text?.trim()?.length ?: 0 }
                ?: return@withContext ScannedText("")
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val raw = best.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                if (line.text.isBlank()) return@mapNotNull null
                RawOcrLine(
                    ScannedLine(
                        line.text.trim(),
                        (box.left / w).coerceIn(0f, 1f), (box.top / h).coerceIn(0f, 1f), (box.right / w).coerceIn(0f, 1f), (box.bottom / h).coerceIn(0f, 1f),
                        surroundingColor(bitmap, box),
                    ),
                    line.confidence,
                )
            }
            // Only the cleaned lines count as the photo's text: no icon marks, file names or tiny background text.
            val lines = cleanScannedLines(raw)
            ScannedText(lines.joinToString("\n") { it.text }, lines)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            ScannedText("")
        }
    }

    /** The photo's colour just above and below a line, which is mostly background rather than letters. */
    private fun surroundingColor(bitmap: android.graphics.Bitmap, box: android.graphics.Rect): Int {
        var r = 0; var g = 0; var b = 0; var n = 0
        val ys = listOf((box.top - 3).coerceIn(0, bitmap.height - 1), (box.bottom + 3).coerceIn(0, bitmap.height - 1))
        for (y in ys) for (i in 0 until 10) {
            val x = (box.left + (box.width() * (i + 0.5f) / 10f).toInt()).coerceIn(0, bitmap.width - 1)
            val c = bitmap.getPixel(x, y)
            r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c); n++
        }
        return android.graphics.Color.rgb(r / n, g / n, b / n)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }
}

/**
 * Photo translation without the AI model: read the text on the phone, then translate it with the normal translator. The
 * lines are translated together so the result can be painted back onto the photo where each line was.
 */
class OcrPhotoDataSource(
    private val reader: PhotoTextReader,
    private val translator: TranslationDataSource,
) : AiPhotoSource {

    override suspend fun translate(jpeg: ByteArray, targetLang: String): AiResult<PhotoTranslation> = try {
        val scanned = reader.read(jpeg)
        val text = scanned.text.trim()
        if (text.isEmpty()) {
            AiResult.Failure(TranslationError.Validation("We could not find any text in that photo. Try a clearer, closer photo."))
        } else {
            val lines = scanned.lines.take(MAX_LINES)
            when (val r = translator.translate(TranslateRequest((if (lines.isEmpty()) text else lines.joinToString("\n") { it.text }).take(4_000), "auto", targetLang, "General"))) {
                is TranslationOutcome.Failure -> AiResult.Failure(r.error)
                is TranslationOutcome.Success -> {
                    val joined = r.value.text.split("\n").map { it.trim() }
                    // One translated line per original line lets us paint them back in place. If the translator merged or
                    // split lines, translate the lines one by one instead, so the overlay is never lost.
                    var limited: TranslationError? = null
                    val parts = when {
                        lines.isEmpty() -> emptyList()
                        joined.size == lines.size -> joined
                        else -> translateEach(lines, targetLang).let { (each, error) -> limited = error; each }
                    }
                    limited?.let { return AiResult.Failure(it) }
                    val overlay = if (parts.size == lines.size) {
                        lines.zip(parts).filter { (l, t) -> t.isNotEmpty() && !t.equals(l.text, ignoreCase = true) }
                            .map { (l, t) -> OverlayLine(t, l.left, l.top, l.right, l.bottom, l.background) }
                    } else emptyList()
                    val translation = if (lines.isNotEmpty() && parts.size == lines.size && joined.size != lines.size) parts.joinToString("\n") else r.value.text
                    AiResult.Success(PhotoTranslation(text, translation, overlay))
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        AiResult.Failure(TranslationError.Validation("This photo is too large for this phone to read. Try a smaller or closer photo."))
    } catch (e: Exception) {
        AiResult.Failure(TranslationError.Unknown(e))
    }

    /**
     * Translates each line by itself, two at a time. A line that fails comes back empty and is left unpainted. If the service
     * says "too many requests", it stops at once and reports that, instead of sending the remaining lines into the limit.
     */
    private suspend fun translateEach(lines: List<ScannedLine>, targetLang: String): Pair<List<String>, TranslationError?> {
        val out = ArrayList<String>(lines.size)
        for (chunk in lines.chunked(2)) {
            val results = coroutineScope {
                chunk.map { l -> async { translator.translate(TranslateRequest(l.text, "auto", targetLang, "General")) } }.awaitAll()
            }
            for (r in results) {
                if (r is TranslationOutcome.Failure && r.error is TranslationError.RateLimited) return out to r.error
                out += when (r) {
                    is TranslationOutcome.Success -> r.value.text.replace("\n", " ").trim()
                    is TranslationOutcome.Failure -> ""
                }
            }
        }
        return out to null
    }

    private companion object { const val MAX_LINES = 60 }
}

/** Uses [primary] (the AI model) and falls back to [secondary] when AI is not set up or has run out of quota. */
class FallbackPhotoSource(private val primary: AiPhotoSource, private val secondary: AiPhotoSource) : AiPhotoSource {
    override suspend fun translate(jpeg: ByteArray, targetLang: String): AiResult<PhotoTranslation> {
        val first = primary.translate(jpeg, targetLang)
        if (first is AiResult.Failure && (first.error is TranslationError.AiUnavailable || first.error is TranslationError.RateLimited)) {
            return secondary.translate(jpeg, targetLang)
        }
        return first
    }
}

/**
 * Reads the photo on the phone first, which lets the translation be painted back onto the photo. When the phone finds no
 * text (for example Arabic or Urdu, which it cannot read) the AI model gets a turn.
 */
class OcrFirstPhotoSource(private val ocr: AiPhotoSource, private val ai: AiPhotoSource) : AiPhotoSource {
    override suspend fun translate(jpeg: ByteArray, targetLang: String): AiResult<PhotoTranslation> {
        val first = ocr.translate(jpeg, targetLang)
        if (first !is AiResult.Failure || first.error !is TranslationError.Validation) return first
        val second = ai.translate(jpeg, targetLang)
        // If the AI is not available either, the clearer message is "no text found".
        return if (second is AiResult.Failure && second.error is TranslationError.AiUnavailable) first else second
    }
}
