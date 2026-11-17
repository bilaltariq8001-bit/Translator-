package com.translator.app.feature.translate

import com.translator.app.core.i18n.str
import com.translator.app.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.translator.app.core.audio.SpeechEngine
import com.translator.app.core.audio.SpeechError
import com.translator.app.core.audio.SpeechListener
import com.translator.app.core.designsystem.AppDialog
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.Tk

/** Speech typing for the input box: [onMic] starts or stops listening; [message] explains why nothing was typed. */
@Stable
class Dictation(val listening: Boolean, val message: String?, val onMic: () -> Unit, val dialogs: @Composable () -> Unit)

/**
 * Lets the user speak instead of typing. While they talk the words appear live in the input box (after whatever was
 * already typed); when they stop, the text stays there to edit or translate. Audio goes only to the device's speech
 * service and is not kept by the app.
 */
@Composable
fun rememberDictation(languageCode: String, currentText: String, onText: (String) -> Unit): Dictation {
    val context = LocalContext.current
    val engine = remember { SpeechEngine(context) }
    DisposableEffect(engine) { onDispose { engine.destroy() } }

    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var explainer by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf("") }
    val text by rememberUpdatedState(currentText)
    val push by rememberUpdatedState(onText)
    val lang by rememberUpdatedState(languageCode)

    fun joined(spoken: String) = if (base.isBlank()) spoken else base.trimEnd() + " " + spoken

    fun begin() {
        if (!engine.isAvailable()) { message = str(R.string.speech_typing_is_not_available); return }
        base = text
        message = null
        listening = true
        engine.start(lang, object : SpeechListener {
            override fun onPartial(text: String) = push(joined(text))
            override fun onFinal(text: String) { push(joined(text)); listening = false }
            override fun onError(error: SpeechError) {
                listening = false
                push(base)
                when (error) {
                    SpeechError.NoSpeech -> message = str(R.string.we_did_not_hear_anything)
                    SpeechError.PermissionDenied -> denied = true
                    SpeechError.Network -> message = str(R.string.speech_typing_needs_an_internet)
                    SpeechError.Unavailable -> message = str(R.string.speech_typing_is_not_available)
                    SpeechError.Other -> message = str(R.string.listening_stopped_tap_the_mic)
                }
            }
        })
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) begin() else denied = true }

    val onMic = {
        if (listening) {
            engine.stop()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            begin()
        } else {
            explainer = true
        }
    }

    val dialogs: @Composable () -> Unit = {
        if (explainer) {
            AppDialog(
                title = str(R.string.mic_allow_title),
                body = str(R.string.mic_allow_body),
                primaryLabel = str(R.string.mic_allow), onPrimary = { explainer = false; permission.launch(Manifest.permission.RECORD_AUDIO) },
                secondaryLabel = str(R.string.mic_dont_allow), onSecondary = { explainer = false },
                icon = AppIcons.Mic,
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(str(R.string.used_only_while_you_tap), color = Tk.Soft, fontSize = 13.sp)
                    Text(str(R.string.audio_goes_to_your_device), color = Tk.Soft, fontSize = 13.sp)
                }
            }
        }
        if (denied) {
            AppDialog(
                title = str(R.string.microphone_is_off),
                body = str(R.string.typing_by_voice_needs_the),
                primaryLabel = str(R.string.open_settings),
                onPrimary = {
                    denied = false
                    com.translator.app.core.util.SafeIntents.openAppSettings(context)
                },
                secondaryLabel = str(R.string.type_instead), onSecondary = { denied = false },
                icon = AppIcons.Mic, danger = true,
            )
        }
    }
    return Dictation(listening, message, onMic, dialogs)
}
