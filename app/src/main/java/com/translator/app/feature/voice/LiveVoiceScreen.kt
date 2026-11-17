package com.translator.app.feature.voice

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.backpress.ReportBusy
import com.translator.app.core.i18n.str
import com.translator.app.R
import com.translator.app.core.i18n.localName
import com.translator.app.core.designsystem.ShimmerBar
import com.translator.app.core.designsystem.ThinkingDots
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.translator.app.core.audio.SpeechEngine
import com.translator.app.core.audio.SpeechError
import com.translator.app.core.audio.SpeechListener
import com.translator.app.core.audio.rememberTtsPlayer
import com.translator.app.core.data.AppSettings
import com.translator.app.core.designsystem.AppDialog
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.LanguageBarCard
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.feature.translate.TranslateUiState
import com.translator.app.feature.translate.TranslateViewModel
import com.translator.app.feature.translate.message
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Conversation, Real-time and Headphone voice modes on the device speech recognizer. Conversation has one mic per
 * language and takes turns; the other two listen continuously (re-arming after every sentence) until stopped. Audio goes
 * only to the system recognizer and is not kept.
 */
@Composable
fun LiveVoiceScreen(
    mode: LiveMode,
    state: TranslateUiState,
    vm: TranslateViewModel,
    liveVm: LiveViewModel,
    settings: AppSettings,
    onPick: (String) -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by liveVm.state.collectAsStateWithLifecycle()
    val engine = remember { SpeechEngine(context) }
    val tts = rememberTtsPlayer()
    val current by rememberUpdatedState(state)
    val conversation = mode == LiveMode.Conversation

    var listeningSide by remember { mutableIntStateOf(-1) }
    var auto by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var showExplainer by rememberSaveable { mutableStateOf(false) }
    var showDenied by rememberSaveable { mutableStateOf(false) }
    var pendingSide by rememberSaveable { mutableIntStateOf(0) }
    // Everything already on screen when it opens counts as read; only lines that arrive afterwards are read aloud.
    var spokenId by remember { mutableLongStateOf(ui.lines.maxOfOrNull { it.id } ?: 0L) }

    DisposableEffect(engine) {
        onDispose {
            if (auto || listeningSide >= 0) Analytics.log(Ev.VOICE_SESSION_CANCELLED) { token("mode", mode.name.lowercase()) }
            auto = false; engine.destroy()
        }
    }
    // A live session (listening, or a line still being translated) is work: leaving it shows no ad.
    ReportBusy("voice_live", listeningSide >= 0 || auto || ui.lines.any { it.pending })

    lateinit var listen: (Int) -> Unit
    fun rearm(side: Int) {
        scope.launch { delay(300); if (auto) listen(side) }
    }
    listen = fun(side: Int) {
        if (!engine.isAvailable()) { auto = false; problem = str(R.string.speech_recognition_is_not_available); return }
        tts.stop()
        problem = null
        listeningSide = side
        val s = current
        val from = if (side == 0) s.source.code else s.target.code
        val to = if (side == 0) s.target.code else s.source.code
        engine.start(from, object : SpeechListener {
            override fun onPartial(text: String) = liveVm.onPartial(text, side)
            override fun onFinal(text: String) {
                listeningSide = -1
                liveVm.onHeard(text, side, from, to, s.tone.label)
                if (auto) rearm(side)
            }

            override fun onError(error: SpeechError) {
                listeningSide = -1
                liveVm.onPartial("", side)
                when (error) {
                    SpeechError.NoSpeech -> if (auto) rearm(side)
                    SpeechError.PermissionDenied -> { auto = false; showDenied = true }
                    SpeechError.Network -> { auto = false; problem = str(R.string.speech_recognition_needs_an_internet) }
                    SpeechError.Unavailable -> { auto = false; problem = str(R.string.speech_recognition_is_not_available) }
                    SpeechError.Other -> { auto = false; problem = str(R.string.listening_stopped_tap_to_start) }
                }
            }
        })
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen(pendingSide) else { auto = false; showDenied = true }
    }

    fun withMic(side: Int, action: () -> Unit) {
        pendingSide = side
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) action() else { auto = false; showExplainer = true }
    }

    fun logStart() = Analytics.log(Ev.VOICE_SESSION_STARTED) { token("mode", mode.name.lowercase()); lang("from", current.source.code); lang("to", current.target.code) }

    // Every 4th start in this mode (Remote Config voice_mode_use_inter_every = 3) shows an interstitial before listening starts.
    val useAd = com.translator.app.core.backpress.rememberFeatureUseAd(com.translator.app.core.config.RcKeys.VOICE_MODE_USE, mode.name)

    // Conversation: tap a mic to speak one turn (tap again to finish it).
    fun onTurn(side: Int) {
        if (listeningSide == side) {
            engine.stop()
            // Some phones never answer after "stop" when nothing was heard yet, which left the screen on "Listening". Give the
            // recognizer a moment, then close the turn ourselves, using the words heard so far if there are any.
            val s = current
            val from = if (side == 0) s.source.code else s.target.code
            val to = if (side == 0) s.target.code else s.source.code
            scope.launch {
                delay(2_500)
                if (listeningSide == side) {
                    val heard = liveVm.state.value.partial
                    engine.destroy(); listeningSide = -1; liveVm.onPartial("", side)
                    if (heard.isNotBlank()) liveVm.onHeard(heard, side, from, to, s.tone.label)
                }
            }
            return
        }
        useAd { withMic(side) { logStart(); listen(side) } }
    }

    // Real-time and Headphone: start or stop continuous listening.
    fun onToggle() {
        if (auto) { auto = false; engine.stop(); engine.destroy(); listeningSide = -1; liveVm.onPartial("", 0); return }
        useAd { withMic(0) { auto = true; logStart(); listen(0) } }
    }

    val speak = mode == LiveMode.Headphone || (conversation && settings.speakAloud)
    LaunchedEffect(ui.lines) {
        val last = ui.lines.lastOrNull() ?: return@LaunchedEffect
        if (!last.pending && last.translation.isNotEmpty() && last.id != spokenId) {
            spokenId = last.id
            Analytics.log(Ev.VOICE_SESSION_SUCCESS) { token("mode", mode.name.lowercase()); bucket("chars", last.heard.length.toLong()) }
            if (speak) tts.speak(last.translation, if (last.side == 0) current.target.code else current.source.code, settings.speechRate, "line-${last.id}")
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(ui.lines.size, ui.partial.isNotEmpty()) {
        val count = ui.lines.size + if (ui.partial.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxWidth()) {
        LanguageBarCard(
            state.source.localName(), state.target.localName(),
            onFrom = { onPick("from") }, onTo = { onPick("to") }, onSwap = vm::onSwap,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ui.lines.isEmpty() && ui.partial.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(24.dp)).background(Tk.PrimaryContainer), contentAlignment = Alignment.Center) {
                        AppIcon(when (mode) { LiveMode.Conversation -> AppIcons.Convo; LiveMode.Realtime -> AppIcons.Wave; LiveMode.Headphone -> AppIcons.Headphones }, Tk.Primary, size = 30.dp)
                    }
                    Text(
                        when (mode) {
                            LiveMode.Conversation -> str(R.string.pass_the_phone_back_and)
                            LiveMode.Realtime -> str(R.string.hands_free_subtitles)
                            LiveMode.Headphone -> str(R.string.listen_in_your_language)
                        },
                        style = Type.Headline, color = Tk.Text, modifier = Modifier.padding(top = 14.dp),
                    )
                    Text(
                        when (mode) {
                            LiveMode.Conversation -> str(R.string.each_person_taps_their_own)
                            LiveMode.Realtime -> str(R.string.tap_start_and_keep_talking)
                            LiveMode.Headphone -> str(R.string.tap_start_each_sentence_is)
                        },
                        color = Tk.Soft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                items(ui.lines, key = { it.id }) { line -> Bubble(line, conversation, state, tts, settings.speechRate) }
                if (ui.partial.isNotEmpty()) item(key = "partial") {
                    Text(
                        ui.partial, color = Tk.Muted, fontSize = 16.sp, lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    )
                }
            }
        }
        problem?.let {
            Text(it, color = Tk.WarnText, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth().clip(Radius.Card).background(Tk.WarnContainer).padding(12.dp))
        }
        if (conversation) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TurnButton(state.source.localName(), listeningSide == 0, Modifier.weight(1f)) { onTurn(0) }
                TurnButton(state.target.localName(), listeningSide == 1, Modifier.weight(1f)) { onTurn(1) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.weight(1f).heightIn(min = 64.dp).shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = if (auto) Tk.Coral else Tk.Primary, spotColor = if (auto) Tk.Coral else Tk.Primary)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(if (auto) listOf(CoralSoft, CoralDeep) else listOf(Tk.Primary, Color(0xFF4C86F0))))
                        .clickable(role = Role.Button, onClick = ::onToggle).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    if (auto) ListeningBars(Color.White, Modifier.size(22.dp)) else AppIcon(AppIcons.Mic, Color.White, size = 22.dp)
                    Box(Modifier.size(10.dp))
                    Text(if (auto) str(R.string.listening_tap_to_stop) else str(R.string.start_listening), color = Color.White, style = Type.Button)
                }
                if (ui.lines.isNotEmpty()) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Tk.Surface).border(1.dp, Tk.Line, RoundedCornerShape(18.dp))
                            .clickable(role = Role.Button) { liveVm.clear() }.semantics { contentDescription = str(R.string.clear_conversation) },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(AppIcons.Trash, Tk.Soft, size = 22.dp) }
                }
            }
        }
        if (conversation && ui.lines.isNotEmpty()) {
            Text(
                str(R.string.clear_conversation), color = Tk.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(role = Role.Button) { liveVm.clear() }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
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
                Text(str(R.string.used_only_while_you_are), color = Tk.Soft, fontSize = 13.sp)
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
private fun Bubble(line: LiveLine, conversation: Boolean, state: TranslateUiState, tts: com.translator.app.core.audio.TtsPlayer, speechRate: Float) {
    val right = conversation && line.side == 1
    Box(Modifier.fillMaxWidth(), contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(if (conversation) 0.88f else 1f).shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Tk.Primary.copy(alpha = 0.3f), spotColor = Tk.Primary.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(24.dp))
                .background(if (right) Tk.PrimaryContainer else Tk.Surface)
                .border(BorderStroke(1.dp, if (right) Tk.PrimaryContainer else Tk.Line), RoundedCornerShape(24.dp)).padding(18.dp),
        ) {
            Text(
                (if (line.side == 0) state.source.localName() else state.target.localName()).uppercase(),
                color = Tk.Muted, style = Type.Label,
            )
            Text(line.heard, color = Tk.Soft, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp))
            when {
                line.pending -> Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ThinkingDots(); ShimmerBar(Modifier.fillMaxWidth(0.7f), 12.dp) }
                line.error != null -> Text(line.error.message(), color = Tk.Danger, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                else -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        line.translation, color = Tk.Primary, style = Type.Result.copy(fontSize = 24.sp, lineHeight = 31.sp),
                        modifier = Modifier.weight(1f).semantics { contentDescription = str(R.string.translation, line.translation) },
                    )
                    // Hear it again, or stop it, in the language it was translated into.
                    SpeakButton(tts, "line-${line.id}", line.translation, if (line.side == 0) state.target.code else state.source.code, speechRate, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TurnButton(label: String, listening: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 104.dp).shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = if (listening) Tk.Coral else Tk.Primary, spotColor = if (listening) Tk.Coral else Tk.Primary)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(if (listening) listOf(CoralSoft, CoralDeep) else listOf(Tk.Primary, Color(0xFF4C86F0))))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (listening) str(R.string.stop_2, label) else str(R.string.speak, label) }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (listening) ListeningBars(Color.White, Modifier.size(30.dp)) else AppIcon(AppIcons.Mic, Color.White, size = 28.dp)
        Text(if (listening) str(R.string.listening_tap_to_finish) else label, color = Color.White, style = Type.Button, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
    }
}
