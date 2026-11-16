package com.translator.app.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

enum class SpeechError { NoSpeech, PermissionDenied, Unavailable, Network, Other }

interface SpeechListener {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(error: SpeechError)
}

/**
 * Manual voice input on the device's speech recognizer. Audio goes to the system recognizer only; this class keeps
 * no audio and logs no text.
 */
class SpeechEngine(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    /** Google's recognizer, if it is installed: phone makers' own default recognizers often lack languages or need accounts. */
    private fun googleService(): ComponentName? = try {
        context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .firstOrNull { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
            ?.let { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
    } catch (e: Exception) {
        null
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context) || googleService() != null

    fun start(langCode: String, listener: SpeechListener) {
        destroy()
        val r = try {
            googleService()?.let { SpeechRecognizer.createSpeechRecognizer(context, it) } ?: SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            try { SpeechRecognizer.createSpeechRecognizer(context) } catch (e2: Exception) { listener.onError(SpeechError.Unavailable); return }
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let(listener::onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = first(results)
                if (text.isNullOrBlank()) listener.onError(SpeechError.NoSpeech) else listener.onFinal(text)
            }

            override fun onError(error: Int) {
                listener.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NoSpeech
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.PermissionDenied
                        // The recognizer cannot do this language on this phone (error codes 12 and 13, added in Android 12).
                        12, 13 -> SpeechError.Unavailable
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> SpeechError.Network
                        else -> SpeechError.Other
                    },
                )
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(langCode).toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Some phones throw here when the recognition service cannot be reached or the permission was just withdrawn.
        try {
            r.startListening(intent)
        } catch (e: SecurityException) {
            listener.onError(SpeechError.PermissionDenied)
        } catch (e: Exception) {
            listener.onError(SpeechError.Unavailable)
        }
    }

    /** Ends the listening turn; the recognizer then delivers what it heard. */
    fun stop() { recognizer?.stopListening() }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun first(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
