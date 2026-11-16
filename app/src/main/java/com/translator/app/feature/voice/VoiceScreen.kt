package com.translator.app.feature.voice

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.backpress.ReportBusy
import kotlinx.coroutines.launch
import com.translator.app.core.i18n.str
import com.translator.app.R
import com.translator.app.core.i18n.localName
import com.translator.app.core.designsystem.AiThinkingCard
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.translator.app.core.audio.SpeechEngine
import com.translator.app.core.audio.SpeechError
import com.translator.app.core.audio.SpeechListener
import com.translator.app.core.audio.rememberTtsPlayer
import com.translator.app.core.designsystem.AppCard
import com.translator.app.core.designsystem.AppDialog
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.LanguageBarCard
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.SectionLabel
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.feature.translate.TranslateUiState
import com.translator.app.feature.translate.TranslateViewModel
import com.translator.app.feature.translate.message


/**
 * Manual voice: tap the mic, speak, see and hear the translation. Conversation, Real-time and Headphone need the
 * cloud speech service and are not built yet.
 */
@Composable
fun VoiceScreen(
    state: TranslateUiState,
    vm: TranslateViewModel,
    voiceVm: VoiceViewModel,
    speakAloud: Boolean,
    speechRate: Float,
    haptics: Boolean,
    narrow: Boolean,
    onPick: (String) -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val ui by voiceVm.state.collectAsStateWithLifecycle()
    val engine = remember { SpeechEngine(context) }
    val stopScope = androidx.compose.runtime.rememberCoroutineScope()
    val tts = rememberTtsPlayer()
    DisposableEffect(engine) {
        onDispose {
            engine.destroy()
            val phase = voiceVm.state.value.phase
            if (phase == VoicePhase.Listening || phase == VoicePhase.Translating) Analytics.log(Ev.VOICE_SESSION_CANCELLED) { token("mode", "manual") }
        }
    }
    // Leaving while it listens or translates shows no ad (the work is stopped by leaving, as before).
    ReportBusy("voice_manual", ui.phase == VoicePhase.Listening || ui.phase == VoicePhase.Translating)

    var showExplainer by rememberSaveable { mutableStateOf(false) }
    var showDenied by rememberSaveable { mutableStateOf(false) }
    val current by rememberUpdatedState(state)

    val listener = remember {
        object : SpeechListener {
            override fun onPartial(text: String) = voiceVm.onPartial(text)
            override fun onFinal(text: String) = voiceVm.onHeard(text, current.source.code, current.target.code, current.tone.label)
            override fun onError(error: SpeechError) {
                if (error == SpeechError.PermissionDenied) showDenied = true
                voiceVm.onSpeechError(error)
            }
        }
    }

    fun begin() {
        tts.stop()
        if (!engine.isAvailable()) { voiceVm.onSpeechError(SpeechError.Unavailable); return }
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        voiceVm.onListening()
        Analytics.log(Ev.VOICE_SESSION_STARTED) { token("mode", "manual"); lang("from", current.source.code); lang("to", current.target.code) }
        engine.start(current.source.code, listener)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else showDenied = true
    }

    // The previous phase starts as whatever it is when the screen opens, so a finished translation that is already there is not
    // read out again; only one that finishes while the screen is open is.
    var previousPhase by remember { mutableStateOf(ui.phase) }
    LaunchedEffect(ui.phase, ui.translation) {
        if (speakAloud && ui.phase == VoicePhase.Done && previousPhase != VoicePhase.Done && ui.translation.isNotEmpty()) {
            tts.speak(ui.translation, state.target.code, speechRate, "manual")
        }
        if (ui.phase != previousPhase) when (ui.phase) {
            VoicePhase.Done -> Analytics.log(Ev.VOICE_SESSION_SUCCESS) { token("mode", "manual"); bucket("chars", ui.heard.length.toLong()) }
            VoicePhase.Failed -> Analytics.log(Ev.VOICE_SESSION_FAILED) { token("mode", "manual"); token("reason", ui.speechError?.name?.lowercase() ?: "translation") }
            VoicePhase.NoSpeech -> Analytics.log(Ev.VOICE_SESSION_FAILED) { token("mode", "manual"); token("reason", "nospeech") }
            else -> Unit
        }
        previousPhase = ui.phase
    }

    fun onMic() {
        if (ui.phase == VoicePhase.Listening) {
            engine.stop()
            // The same guard as in Conversation: if the recognizer never answers after "stop", end the turn here.
            stopScope.launch {
                kotlinx.coroutines.delay(2_500)
                val now = voiceVm.state.value
                if (now.phase == VoicePhase.Listening) {
                    engine.destroy()
                    if (now.partial.isNotBlank()) voiceVm.onHeard(now.partial, current.source.code, current.target.code, current.tone.label)
                    else voiceVm.onSpeechError(SpeechError.NoSpeech)
                }
            }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) begin() else showExplainer = true
    }

    Column(modifier.fillMaxWidth()) {
        LanguageBarCard(
            state.source.localName(), state.target.localName(),
            onFrom = { onPick("from") }, onTo = { onPick("to") }, onSwap = vm::onSwap,
            modifier = Modifier.padding(top = 4.dp),
        )
        run {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val heard = ui.partial.ifEmpty { ui.heard }
                if (heard.isNotEmpty()) {
                    AppCard(Modifier.fillMaxWidth()) {
                        SectionLabel(state.source.localName())
                        Text(heard, color = Tk.Soft, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 2.dp), style = TextStyle(textDirection = if (state.source.rtl) TextDirection.Rtl else TextDirection.Ltr))
                    }
                }
                if (ui.phase == VoicePhase.Translating) AiThinkingCard(lines = 2)
                if (ui.translation.isNotEmpty()) {
                    AppCard(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(2.dp, Tk.Primary)) {
                        SectionLabel(state.target.localName(), color = Tk.Primary)
                        Text(ui.translation, style = Type.Result.copy(fontSize = 28.sp), color = Tk.Text, modifier = Modifier.padding(top = 6.dp).semantics { contentDescription = str(R.string.translation, ui.translation) })
                        Row(Modifier.padding(top = 6.dp)) {
                            SpeakButton(tts, "manual", ui.translation, state.target.code, speechRate)
                        }
                    }
                }
                if (ui.phase == VoicePhase.NoSpeech) {
                    Problem(str(R.string.we_did_not_hear_anything_2), str(R.string.hold_the_phone_closer_and))
                }
                if (ui.phase == VoicePhase.Failed) {
                    val text = ui.translationError?.message() ?: when (ui.speechError) {
                        SpeechError.Unavailable -> str(R.string.speech_recognition_is_not_available)
                        SpeechError.Network -> str(R.string.speech_recognition_needs_an_internet)
                        SpeechError.PermissionDenied -> str(R.string.microphone_is_off_2)
                        else -> str(R.string.something_went_wrong_please_try_2)
                    }
                    Problem(str(R.string.voice_translation_did_not_work), text)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val listening = ui.phase == VoicePhase.Listening
                MicOrb(listening, ::onMic, size = 84.dp)
                Text(
                    when (ui.phase) {
                        VoicePhase.Listening -> str(R.string.listening_tap_to_stop)
                        VoicePhase.NoSpeech, VoicePhase.Failed -> str(R.string.tap_to_try_again)
                        else -> str(R.string.tap_to_speak)
                    },
                    color = Tk.Soft, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 0.dp),
                )
            }
        }
    }

    if (showExplainer) {
        AppDialog(
            title = str(R.string.mic_allow_title),
            body = str(R.string.mic_allow_body),
            primaryLabel = str(R.string.mic_allow), onPrimary = { showExplainer = false; permission.launch(Manifest.permission.RECORD_AUDIO) },
            secondaryLabel = str(R.string.mic_dont_allow), onSecondary = { showExplainer = false },
            icon = AppIcons.Mic,
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(str(R.string.used_only_while_you_tap), color = Tk.Soft, fontSize = 13.sp)
                Text(str(R.string.audio_goes_to_your_device), color = Tk.Soft, fontSize = 13.sp)
            }
        }
    }
    if (showDenied) {
        AppDialog(
            title = str(R.string.microphone_is_off),
            body = str(R.string.voice_translation_needs_the_microphone),
            primaryLabel = str(R.string.open_settings),
            onPrimary = {
                showDenied = false
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
            secondaryLabel = str(R.string.type_instead), onSecondary = { showDenied = false; onTypeInstead() },
            icon = AppIcons.Mic, danger = true,
        )
    }
}

@Composable
private fun Problem(title: String, body: String) {
    AppCard(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, Tk.Warn)) {
        Text(title, color = Tk.WarnText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(body, color = Tk.Soft, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
