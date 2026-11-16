package com.translator.app.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.translator.app.R
import com.translator.app.core.i18n.str
import com.translator.app.core.model.Languages
import java.util.Locale

/**
 * Speaks a translation with the device's text-to-speech engine. The engine is started on the first [speak], not when a
 * screen opens, because starting it binds to the system speech service and loads a voice. Text asked for while the
 * engine is still starting is spoken as soon as it is ready. Returns false if the voice is not available, and tells the
 * person why through [onProblem] (a phone may have no engine, or no voice for that language).
 *
 * [speakingId] holds the id given to [speak] while that text is being read, so a speaker button can show "stop" for the
 * line that is playing and "listen" for the others, whether the reading was started by the button or automatically.
 */
class TtsPlayer(context: Context, private val onProblem: (String) -> Unit = {}) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var pendingAt = 0L

    init { synchronized(players) { players.add(this) } }

    /** The id of the text being read aloud right now, or null when nothing is playing. */
    var speakingId by mutableStateOf<String?>(null)
        private set

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) { speakingId = utteranceId }
        override fun onDone(utteranceId: String?) { if (speakingId == utteranceId) speakingId = null }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { if (speakingId == utteranceId) speakingId = null }
        override fun onStop(utteranceId: String?, interrupted: Boolean) { if (speakingId == utteranceId) speakingId = null }
    }

    fun speak(text: String, langCode: String, rate: Float = 1f, id: String = "translation"): Boolean {
        com.translator.app.core.analytics.Analytics.log(com.translator.app.core.analytics.Ev.TTS_PLAYED) { lang("lang", langCode) }
        val engine = tts ?: try {
            TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                // Text asked for while the engine was starting is read when it is ready, unless that took so long that the
                // screen has moved on: reading it then would be a voice from nowhere.
                if (ready) { if (System.nanoTime() - pendingAt < 6_000_000_000L) pending?.invoke() } else onProblem(str(R.string.tts_unavailable))
                pending = null
            }
        } catch (e: Exception) {
            onProblem(str(R.string.tts_unavailable)); return false
        }.also { it.setOnUtteranceProgressListener(progress); tts = it }
        val run = { say(engine, text, langCode, rate, id) }
        if (!ready) { pending = { run() }; pendingAt = System.nanoTime(); return true }
        return run()
    }

    private fun say(engine: TextToSpeech, text: String, langCode: String, rate: Float, id: String): Boolean {
        // A phone's voice engine may not have a voice for every language (common on phones without Google's engine).
        val result = try { engine.setLanguage(Locale.forLanguageTag(langCode)) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            onProblem(str(R.string.tts_language_missing, Languages.byCodeOrNull(langCode)?.name ?: langCode))
            return false
        }
        engine.setSpeechRate(rate)
        val ok = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        if (!ok) onProblem(str(R.string.tts_unavailable))
        return ok
    }

    /** Stops reading at once (for the stop button, and before the microphone opens so it does not hear the voice). */
    fun stop() {
        pending = null
        tts?.stop()
        speakingId = null
    }

    companion object {
        private val players: MutableSet<TtsPlayer> = java.util.Collections.newSetFromMap(java.util.WeakHashMap())

        /** Stops every reader voice in the app (before a full-screen ad, so nothing talks over it). */
        fun stopAll() {
            val all = synchronized(players) { players.toList() }
            all.forEach { try { it.stop() } catch (_: Exception) { } }
        }
    }

    fun shutdown() {
        pending = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        speakingId = null
    }
}

@Composable
fun rememberTtsPlayer(): TtsPlayer {
    val context = LocalContext.current
    val player = remember { TtsPlayer(context) { message -> android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show() } }
    DisposableEffect(player) { onDispose { player.shutdown() } }
    return player
}
