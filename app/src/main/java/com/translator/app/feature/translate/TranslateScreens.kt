package com.translator.app.feature.translate

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.ui.focus.focusRequester
import com.translator.app.core.i18n.localName
import androidx.compose.foundation.layout.width
import com.translator.app.core.i18n.toneLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.core.audio.rememberTtsPlayer
import com.translator.app.core.data.AppSettings
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.CircleAction
import com.translator.app.core.designsystem.CodeBadge
import com.translator.app.core.designsystem.HistoryAction
import com.translator.app.core.designsystem.IconAction
import com.translator.app.core.designsystem.LanguageBarCard
import com.translator.app.core.designsystem.PrimaryButton
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.ScreenHeader
import com.translator.app.core.designsystem.SectionLabel
import com.translator.app.core.designsystem.ShimmerLine
import com.translator.app.core.designsystem.SoftPill
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.core.model.Language
import com.translator.app.core.model.Languages
import com.translator.app.core.model.Tone

private fun dir(l: Language) = TextStyle(textDirection = if (l.rtl) TextDirection.Rtl else TextDirection.Ltr)

/**
 * The Text tab: language card, one big input card, and the result shown inline under it (source above, translation
 * below, AI insights beneath). A fixed "New translation" button replaces the input actions once there is a result.
 */
@Composable
fun TranslateHome(
    state: TranslateUiState,
    vm: TranslateViewModel,
    settings: AppSettings,
    onOpenHistory: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingStyle by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var focusInput by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val tts = rememberTtsPlayer()
    val result = state.result
    // The big mic types by voice: the spoken words land in the input box, ready to edit or translate.
    val dictation = rememberDictation(state.source.code, state.text, vm::onTextChange)

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenHeader("AI Translator", Modifier.padding(horizontal = pad - 8.dp)) { HistoryAction(onOpenHistory) }
            LanguageBarCard(
                state.source.localName(), state.target.localName(), { pickingFor = "from" }, { pickingFor = "to" }, vm::onSwap,
                Modifier.padding(horizontal = pad),
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (result == null) {
                    InputCard(state, vm, onStyle = { pickingStyle = true }, listening = dictation.listening, focusOnShow = focusInput)
                    dictation.message?.let { Text(it, color = Tk.Soft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp)) }
                    if (state.loading) Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerLine(0.9f); ShimmerLine(0.65f)
                    }
                    state.error?.let { Text(it.message(), color = Tk.Danger, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp)) }
                } else {
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 8 }, exit = fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ResultCard(
                                r = result,
                                onClear = vm::onNewTranslation,
                                onEdit = { focusInput = true; vm.onEditText() },
                                speakingId = tts.speakingId,
                                onSpeakSource = { if (tts.speakingId == "source") tts.stop() else tts.speak(result.sourceText, result.source.code, settings.speechRate, "source") },
                                onSpeakTarget = { if (tts.speakingId == "target") tts.stop() else tts.speak(result.text, result.target.code, settings.speechRate, "target") },
                                onCopy = { clipboard.setText(AnnotatedString(result.text)); Analytics.copied("text") },
                                onFavorite = vm::onToggleFavorite,
                                onFullscreen = { fullscreen = true },
                            )
                            InsightsCard(state.insights, settings.cloudConsent, vm::onLoadInsights, onClose = vm::onCloseInsights)
                        }
                    }
                }
            }
            if (result == null) {
                val context = LocalContext.current
                val clipMgr = remember { context.getSystemService(android.content.ClipboardManager::class.java) }
                Row(
                    Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleAction(AppIcons.Paste, str(R.string.paste_text), {
                        val pasteText = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString() ?: clipboard.getText()?.text
                        pasteText?.let(vm::onTextChange)
                    })
                    com.translator.app.feature.voice.MicOrb(
                        listening = dictation.listening, onClick = dictation.onMic, size = 72.dp,
                        idleColors = listOf(Tk.Primary, Color(0xFF4C86F0)),
                    )
                    CircleAction(AppIcons.Camera, str(R.string.translate_a_photo), onOpenPhoto)
                }
            } else {
                PrimaryButton(str(R.string.new_translation), vm::onNewTranslation, Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 12.dp), icon = AppIcons.Plus, height = 56.dp)
            }
        }
    }
    pickingFor?.let { which ->
        LanguageSheet(
            title = if (which == "from") str(R.string.translate_from_2) else str(R.string.translate_to_2),
            recent = listOf(state.source, state.target).distinct(),
            onPick = { lang -> if (which == "from") vm.onSource(lang) else vm.onTarget(lang); pickingFor = null },
            onDismiss = { pickingFor = null },
            selectedCode = if (which == "from") state.source.code else state.target.code,
        )
    }
    if (fullscreen && result != null) {
        FullscreenTranslation(result, speaking = tts.speakingId == "fullscreen", onSpeak = { if (tts.speakingId == "fullscreen") tts.stop() else tts.speak(result.text, result.target.code, settings.speechRate, "fullscreen") }, onClose = { fullscreen = false })
    }
    dictation.dialogs()
    if (pickingStyle) StyleSheet(state.tone, onPick = { vm.onTone(it); pickingStyle = false }, onDismiss = { pickingStyle = false })
}

@Composable
private fun InputCard(state: TranslateUiState, vm: TranslateViewModel, onStyle: () -> Unit, listening: Boolean = false, focusOnShow: Boolean = false) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Coming back from a result to change the text: put the cursor in the box and open the keyboard.
    androidx.compose.runtime.LaunchedEffect(focusOnShow) { if (focusOnShow) runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().clip(Radius.Composer).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Composer).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.source.localName(), color = Tk.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state.text.isNotEmpty()) IconAction(AppIcons.Close, str(R.string.clear_text), { vm.onTextChange("") }, tint = Tk.Muted, size = 18.dp)
        }
        // The caret lives in the field's own value. Whenever the text is replaced from outside (coming back to edit after a
        // translation, a paste, the microphone) it goes to the END of the text, where the person carries on writing.
        var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(state.text, androidx.compose.ui.text.TextRange(state.text.length))) }
        if (field.text != state.text) field = androidx.compose.ui.text.input.TextFieldValue(state.text, androidx.compose.ui.text.TextRange(state.text.length))
        BasicTextField(
            value = field,
            onValueChange = { field = it; if (it.text != state.text) vm.onTextChange(it.text) },
            // The direction follows the letters actually typed (English stays left to right, Urdu right to left); an empty box
            // starts in the direction of the chosen language.
            textStyle = Type.Result.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = Tk.Text, textDirection = if (state.source.rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr),
            cursorBrush = SolidColor(Tk.Primary),
            // A fixed-height box that scrolls inside, so long or pasted text never pushes the buttons below it.
            modifier = Modifier.fillMaxWidth().height(190.dp).focusRequester(focus).verticalScroll(rememberScrollState()).padding(top = 6.dp),
            decorationBox = { inner ->
                Box { if (state.text.isEmpty()) Text(if (listening) str(R.string.listening_speak_now) else str(R.string.enter_text), color = if (listening) Tk.Primary else Tk.Off, style = Type.Result.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium)); inner() }
            },
        )
        if (state.text.isNotEmpty()) Text("%,d / %,d".format(state.text.length, MAX_CHARS), color = Tk.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            SoftPill(toneLabel(state.tone), onStyle, icon = AppIcons.Sparkle)
            Box(Modifier.weight(1f))
            PrimaryButton(
                if (state.loading) str(R.string.kb_translating) else str(R.string.translate),
                vm::onTranslate, enabled = state.text.isNotBlank() && !state.loading, height = 48.dp,
            )
        }
    }
}

@Composable
private fun ResultCard(
    r: TranslateResult,
    onClear: () -> Unit,
    onEdit: () -> Unit,
    speakingId: String?,
    onSpeakSource: () -> Unit,
    onSpeakTarget: () -> Unit,
    onCopy: () -> Unit,
    onFavorite: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(Radius.Composer).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Composer).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.source.localName(), color = Tk.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            IconAction(if (speakingId == "source") AppIcons.Stop else AppIcons.Speaker, str(if (speakingId == "source") R.string.stop_reading else R.string.listen_to_the_original), onSpeakSource, tint = if (speakingId == "source") Tk.Primary else Tk.Muted, size = 20.dp)
            Box(Modifier.weight(1f))
            IconAction(AppIcons.Close, str(R.string.clear), onClear, tint = Tk.Muted, size = 18.dp)
        }
        Text(r.sourceText, style = dir(r.source).merge(Type.Result.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)), color = Tk.Text,
            // Tapping the original text goes back to the text box with the words kept.
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = str(R.string.edit_text)) { onEdit() }.padding(bottom = 14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tk.Line))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(r.target.localName(), color = Tk.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            IconAction(if (speakingId == "target") AppIcons.Stop else AppIcons.Speaker, str(if (speakingId == "target") R.string.stop_reading else R.string.listen), onSpeakTarget, tint = Tk.Primary, size = 20.dp)
            Box(Modifier.weight(1f))
            IconAction(AppIcons.Expand, str(R.string.show_full_screen), onFullscreen, tint = Tk.Soft, size = 20.dp)
            IconAction(AppIcons.Copy, str(R.string.copy), onCopy, tint = Tk.Soft, size = 20.dp)
            IconAction(if (r.favorite) AppIcons.StarFilled else AppIcons.Star, if (r.favorite) str(R.string.remove_favorite) else str(R.string.favorite), onFavorite, tint = Tk.Sand, size = 22.dp)
        }
        Text(r.text, style = dir(r.target).merge(Type.Result.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)), color = Tk.Primary, modifier = Modifier.padding(bottom = 6.dp))
        if (r.viaAi) Text(str(R.string.translated_with_ai_note), color = Tk.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleSheet(current: Tone, onPick: (Tone) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tk.Surface, shape = Radius.Sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(str(R.string.pick_your_style), style = Type.Headline, color = Tk.Text, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(str(R.string.the_style_changes_how_the), color = Tk.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            Tone.entries.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(role = Role.RadioButton) { onPick(t) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(toneLabel(t), color = Tk.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (t == current) AppIcon(AppIcons.Check, Tk.Primary)
                }
            }
        }
    }
}

/**
 * The language list as a plain bottom panel in a dialog window. It is deliberately simpler than the Material bottom
 * sheet (no drag anchors, nested-scroll hand-offs or sheet animations) because this list is long and scrolled a lot.
 * Tap outside or press back to close.
 */
@Composable
internal fun LanguageSheet(title: String, recent: List<Language>, onPick: (Language) -> Unit, onDismiss: () -> Unit, selectedCode: String? = null) {
    var query by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).fillMaxHeight(0.88f).clip(Radius.Sheet).background(Tk.Surface)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                    .imePadding().padding(horizontal = 16.dp),
            ) {
                Text(title, style = Type.Headline, color = Tk.Text, modifier = Modifier.padding(top = 20.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(Radius.Pill).background(Tk.Surface2).padding(horizontal = 14.dp).defaultMinSize(minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(AppIcons.Search, Tk.Muted, size = 18.dp)
                    BasicTextField(
                        query, { query = it },
                        textStyle = Type.Body.copy(color = Tk.Text), singleLine = true, cursorBrush = SolidColor(Tk.Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text(str(R.string.search_languages_2), color = Tk.Muted, style = Type.Body); inner() } },
                    )
                }
                val filtered = remember(query) { Languages.all.filter { query.isBlank() || it.name.contains(query, true) || it.nativeName.contains(query, true) || it.code.equals(query, true) } }
                // Read once here and pass plain values down, so rows already on screen are not redone while scrolling.
                val ready = com.translator.app.core.offline.LocalOfflineReady.current
                val busy = com.translator.app.core.offline.LocalOfflineBusy.current
                val download = com.translator.app.core.offline.LocalOfflineDownload.current
                LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    if (query.isBlank()) {
                        item(key = "h-recent", contentType = "label") { SectionLabel(str(R.string.recent), Modifier.padding(vertical = 8.dp)) }
                        items(recent, key = { "r" + it.code }, contentType = { "row" }) { LanguageRow(it, it.code in ready, it.code in busy, onPick, download, it.code == selectedCode) }
                        item(key = "h-all", contentType = "label") { SectionLabel(str(R.string.all_languages), Modifier.padding(vertical = 8.dp)) }
                    }
                    items(filtered, key = { it.code }, contentType = { "row" }) { LanguageRow(it, it.code in ready, it.code in busy, onPick, download, it.code == selectedCode) }
                }
            }
        }
    }
}

private val LtrStyle = TextStyle(textDirection = TextDirection.Ltr)
private val RtlStyle = TextStyle(textDirection = TextDirection.Rtl)

/** One row: a fixed height and only a few elements, so scrolling has very little to measure and draw. */
@Composable
private fun LanguageRow(lang: Language, installed: Boolean, working: Boolean, onPick: (Language) -> Unit, onDownload: (Language) -> Unit, selected: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clickable(role = Role.Button) { onPick(lang) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The flag(s) of the countries the language is mainly spoken in; a globe when it has no single home.
        Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) { Text(com.translator.app.core.model.Flags.of(lang.code), fontSize = 20.sp, maxLines = 1, softWrap = false) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(lang.name, color = Tk.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, softWrap = false)
            Text(lang.nativeName, color = Tk.Muted, fontSize = 13.sp, maxLines = 1, softWrap = false, style = if (lang.rtl) RtlStyle else LtrStyle)
        }
        // The original languages and installed packs show nothing. Others that can work offline show a download circle;
        // phone storage is used only after the user taps it.
        when {
            selected -> AppIcon(AppIcons.Check, Tk.Primary, size = 22.dp)
            lang.code in Languages.core || installed || !lang.offline -> Unit
            working -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), color = Tk.Primary, strokeWidth = 2.5.dp)
            else -> com.translator.app.core.designsystem.DownloadCircleButton(str(R.string.download_for_offline_use, lang.name), { onDownload(lang) })
        }
    }
}

/** A calm "not available yet" state with an optional way forward. */
@Composable
fun ComingSoon(
    message: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (icon != null) {
            Box(Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 64.dp).clip(Radius.Card).background(Tk.PrimaryContainer), contentAlignment = Alignment.Center) { AppIcon(icon, Tk.Primary, size = 30.dp) }
        }
        Text(message, color = Tk.Soft, fontSize = 15.sp, lineHeight = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = if (icon != null) 16.dp else 0.dp))
        if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction, Modifier.padding(top = 20.dp))
    }
}

/** The translation as large as the screen allows, to show to someone else. Tap anywhere or the close button to leave. */
@Composable
private fun FullscreenTranslation(r: TranslateResult, speaking: Boolean, onSpeak: () -> Unit, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Tk.Primary).clickable(onClick = onClose)) {
            Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.target.localName().uppercase(), color = Color.White.copy(alpha = 0.8f), style = Type.Label)
                Text(
                    r.text, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = dir(r.target).merge(Type.Result.copy(fontSize = 44.sp, lineHeight = 54.sp)),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            IconAction(AppIcons.Close, str(R.string.close), onClose, Modifier.align(Alignment.TopEnd).padding(12.dp), tint = Color.White, size = 24.dp)
            IconAction(if (speaking) AppIcons.Stop else AppIcons.Speaker, str(if (speaking) R.string.stop_reading else R.string.listen), onSpeak, Modifier.align(Alignment.BottomCenter).padding(24.dp), tint = Color.White, size = 28.dp)
        }
    }
}
