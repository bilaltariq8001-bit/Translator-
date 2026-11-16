package com.translator.app.feature.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.translator.app.core.ai.OverlayLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Paints translated lines over the original text of a photo, in the same place and on the photo's own colour. */
object OverlayRenderer {

    /** Returns a copy of [photo] with every line of [lines] painted on. [photo] is not changed. */
    fun render(photo: Bitmap, lines: List<OverlayLine>): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
        for (line in lines) {
            val box = RectF(line.left * w, line.top * h, line.right * w, line.bottom * h)
            val pad = max(2f, box.height() * 0.12f)
            val boxW = max(box.width(), 40f)
            val layout = fit(line.text, text, boxW, box.height(), line.background)
            val bottom = min(h, max(box.bottom, box.top + layout.height + pad))
            fill.color = line.background
            canvas.drawRoundRect(RectF(box.left - pad, box.top - pad, min(w, box.left + boxW + pad), bottom + pad), pad, pad, fill)
            canvas.save()
            canvas.translate(box.left, box.top)
            layout.draw(canvas)
            canvas.restore()
        }
        return out
    }

    /** The largest text size, starting from the original line's height, at which the translation fits the box width. */
    private fun fit(text: String, paint: TextPaint, width: Float, height: Float, background: Int): StaticLayout {
        paint.color = if (luminance(background) > 0.5f) Color.BLACK else Color.WHITE
        var size = max(10f, height * 0.85f)
        var layout = build(text, paint, width, size)
        // Longer translations wrap to more lines; shrink until they stay within about one and a half line heights.
        while (size > 9f && layout.height > height * 1.5f) {
            size *= 0.88f
            layout = build(text, paint, width, size)
        }
        return layout
    }

    private fun build(text: String, paint: TextPaint, width: Float, size: Float): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }

    private fun luminance(c: Int): Float = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    /** Saves [bitmap] to Pictures/AI Translator. Returns false if it could not be saved (or on Android 9 and older). */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "translated-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Translator")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        try {
            val ok = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } == true
            if (!ok) { resolver.delete(uri, null, null); return@withContext false }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
