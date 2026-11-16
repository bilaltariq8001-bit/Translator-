package com.translator.app.feature.camera

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.analytics.reason
import com.translator.app.core.i18n.str
import com.translator.app.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.core.ai.AiPhotoSource
import com.translator.app.core.ai.AiResult
import com.translator.app.core.ai.PhotoTranslation
import com.translator.app.core.network.TranslationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** A photo ready to send: JPEG bytes (long side at most 1600 px) and the same picture as a preview. */
class PreparedPhoto(val jpeg: ByteArray, val preview: Bitmap)

object PhotoImage {
    private const val MAX_SIDE = 1600

    /** Decodes [uri], fixes its rotation, shrinks it and returns JPEG bytes. Returns null if it cannot be read as a picture. */
    suspend fun load(context: Context, uri: Uri): PreparedPhoto? = withContext(Dispatchers.IO) {
        // A very large photo can use more memory than the phone has to spare. That is an Error, not an Exception, and used to
        // close the app; now the photo is read again at half the size, and only then given up on.
        loadOnce(context, uri, extraShrink = 1) ?: loadOnce(context, uri, extraShrink = 2)
    }

    private fun loadOnce(context: Context, uri: Uri, extraShrink: Int): PreparedPhoto? {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = extraShrink
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return null
            val rotation = resolver.openInputStream(uri)?.use { rotationOf(ExifInterface(it)) } ?: 0
            val scale = min(1f, MAX_SIDE / max(raw.width, raw.height).toFloat())
            val matrix = Matrix().apply { postRotate(rotation.toFloat()); postScale(scale, scale) }
            val bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            return PreparedPhoto(out.toByteArray(), bitmap)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
    }

    private fun rotationOf(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

sealed interface PhotoUiState {
    data object Idle : PhotoUiState
    data class Working(val preview: Bitmap) : PhotoUiState
    /** [translated] is the photo with the translation painted over the original text, or null when no line positions exist. */
    data class Done(val preview: Bitmap, val result: PhotoTranslation, val translated: Bitmap? = null) : PhotoUiState
    data class Failed(val preview: Bitmap?, val error: TranslationError) : PhotoUiState
}

class PhotoViewModel(private val source: AiPhotoSource) : ViewModel() {
    private val _state = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var last: PreparedPhoto? = null

    fun onPhoto(photo: PreparedPhoto, targetLang: String) {
        last = photo
        run(photo, targetLang)
    }

    fun onRetry(targetLang: String) { last?.let { run(it, targetLang) } }

    fun onUnreadable() { _state.value = PhotoUiState.Failed(null, TranslationError.Validation(str(R.string.we_could_not_open_that))) }

    fun onReset() {
        job?.cancel()
        last = null
        _state.value = PhotoUiState.Idle
    }

    private fun run(photo: PreparedPhoto, targetLang: String) {
        job?.cancel()
        _state.value = PhotoUiState.Working(photo.preview)
        Analytics.log(Ev.PHOTO_TRANSLATE_STARTED) { lang("to", targetLang) }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        job = viewModelScope.launch {
            _state.value = try {
                when (val r = source.translate(photo.jpeg, targetLang)) {
                    is AiResult.Success -> PhotoUiState.Done(
                        photo.preview, r.value,
                        // Painting the translation onto the photo is a bonus: if it fails, the translated text is still shown.
                        if (r.value.overlay.isEmpty()) null else try {
                            withContext(Dispatchers.Default) { OverlayRenderer.render(photo.preview, r.value.overlay) }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            null
                        },
                    )
                    is AiResult.Failure -> PhotoUiState.Failed(photo.preview, r.error)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                PhotoUiState.Failed(photo.preview, TranslationError.Validation(str(R.string.photo_too_large)))
            } catch (e: Throwable) {
                PhotoUiState.Failed(photo.preview, TranslationError.Unknown(e as? Exception))
            }
            when (val done = _state.value) {
                is PhotoUiState.Done -> Analytics.log(Ev.PHOTO_TRANSLATE_SUCCESS) { lang("to", targetLang); duration("t", android.os.SystemClock.elapsedRealtime() - startedAt) }
                is PhotoUiState.Failed -> Analytics.log(Ev.PHOTO_TRANSLATE_FAILED) { lang("to", targetLang); token("reason", done.error.reason()) }
                else -> Unit
            }
        }
    }
}
