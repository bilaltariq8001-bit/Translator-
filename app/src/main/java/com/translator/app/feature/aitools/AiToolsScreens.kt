package com.translator.app.feature.aitools

import com.translator.app.core.analytics.Analytics
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.translator.app.core.i18n.str
import com.translator.app.R
import com.translator.app.core.ads.BannerFrame
import com.translator.app.core.config.RcKeys
import com.translator.app.core.designsystem.AppDialog
import com.translator.app.feature.history.relativeTime
import com.translator.app.core.data.ChatSession
import com.translator.app.core.i18n.localName
import com.translator.app.core.i18n.categoryLabel
import com.translator.app.core.i18n.optionLabel
import androidx.annotation.StringRes
import com.translator.app.core.designsystem.ActionStyle
import com.translator.app.core.designsystem.ActionButton
import com.translator.app.core.designsystem.PhraseSkeleton
import com.translator.app.core.designsystem.ThinkingBubble
import com.translator.app.core.designsystem.AiThinkingCard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import com.translator.app.core.designsystem.HistoryAction
import com.translator.app.core.designsystem.ScreenHeader
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.translator.app.core.ai.AiWriterKind
import com.translator.app.core.audio.rememberTtsPlayer
import com.translator.app.core.designsystem.AppCard
import com.translator.app.core.designsystem.AppChip
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.IconAction
import com.translator.app.core.designsystem.PrimaryButton
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.SectionLabel
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.core.model.Language
import com.translator.app.feature.translate.message

/** The five AI tools, each on its own view model so state survives moving between them. */
class AiToolsViewModels(
    val chat: AiChatViewModel,
    val email: AiWriterViewModel,
    val grammar: AiWriterViewModel,
    val slang: AiWriterViewModel,
    val travel: AiTravelViewModel,
)

private enum class ToolScreen { List, Chat, ChatHistory, Email, Grammar, Slang, Travel, Keyboard }

@Composable
fun AiToolsHost(
    vms: AiToolsViewModels,
    aiAllowed: Boolean,
    speechRate: Float,
    targetLang: Language,
    onTranslateText: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenOffline: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(ToolScreen.List.name) }
    val current = ToolScreen.valueOf(screen)
    val openToolAd = com.translator.app.core.backpress.rememberNavigationAd(com.translator.app.core.config.RcKeys.AI_TOOL_OPEN)
    // An opened tool takes the whole screen: the bottom tabs hide while it is open.
    com.translator.app.core.designsystem.HideTabBar(current != ToolScreen.List)
    // A tool that is loading or streaming an answer is "busy": leaving it shows no ad.
    val busy by remember(vms) {
        kotlinx.coroutines.flow.combine(
            listOf(
                vms.chat.state.map { it.sending }, vms.email.state.map { it.loading }, vms.grammar.state.map { it.loading },
                vms.slang.state.map { it.loading }, vms.travel.state.map { it.loading },
            ),
        ) { flags -> flags.any { it } }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    com.translator.app.core.backpress.ReportBusy("ai_tool", busy)
    // Back from a tool to the list goes through one path (system Back and the arrow) and may show an interstitial.
    // Chat history back to the chat is just navigation inside the tool: no ad.
    val back = com.translator.app.core.backpress.rememberBackPressAd(com.translator.app.core.config.RcKeys.AI_TOOL_BACK, enabled = current != ToolScreen.List && current != ToolScreen.ChatHistory) { screen = ToolScreen.List.name }
    BackHandler(enabled = current == ToolScreen.ChatHistory) { screen = ToolScreen.Chat.name }
    when (current) {
        ToolScreen.List -> ToolsList(openToolAd, onOpenHistory, onOpenPhoto, onOpenOffline) { screen = it.name }
        ToolScreen.Chat -> BannerFrame(RcKeys.SLOT_TOOLS) { ChatScreen(vms.chat, aiAllowed, back) { screen = ToolScreen.ChatHistory.name } }
        ToolScreen.ChatHistory -> BannerFrame(RcKeys.SLOT_TOOLS) {
            ChatHistoryScreen(
                vms.chat, onBack = { screen = ToolScreen.Chat.name },
                onOpen = { id -> vms.chat.onOpen(id); screen = ToolScreen.Chat.name },
                onNew = { vms.chat.onClear(); screen = ToolScreen.Chat.name },
            )
        }
        ToolScreen.Email -> BannerFrame(RcKeys.SLOT_TOOLS) { WriterScreen(WriterSpec.Email, vms.email, aiAllowed, back, onTranslateText) }
        ToolScreen.Grammar -> BannerFrame(RcKeys.SLOT_TOOLS) { WriterScreen(WriterSpec.Grammar, vms.grammar, aiAllowed, back, onTranslateText) }
        ToolScreen.Slang -> BannerFrame(RcKeys.SLOT_TOOLS) { WriterScreen(WriterSpec.Slang, vms.slang, aiAllowed, back, onTranslateText) }
        ToolScreen.Travel -> BannerFrame(RcKeys.SLOT_TOOLS) { TravelScreen(vms.travel, aiAllowed, speechRate, targetLang, back) }
        ToolScreen.Keyboard -> KeyboardSetupScreen(back)
    }
}

// ---------------------------------------------------------------- List

private data class ToolCardSpec(val title: String, val body: String, val icon: ImageVector, val onClick: (() -> Unit)?)

@Composable
private fun ToolsList(openToolAd: (onNavigate: () -> Unit) -> Unit, onOpenHistory: () -> Unit, onOpenPhoto: () -> Unit, onOpenOffline: () -> Unit, onOpen: (ToolScreen) -> Unit) {
    val ads = com.translator.app.core.ads.LocalAds.current
    androidx.compose.runtime.LaunchedEffect(Unit) { ads?.interstitials?.warm(com.translator.app.core.config.RcKeys.AI_TOOL_OPEN) }
    val grid = listOf(
        ToolCardSpec(str(R.string.email_writer), str(R.string.a_clear_email_in_the), AppIcons.Mail) { openToolAd { onOpen(ToolScreen.Email) } },
        ToolCardSpec(str(R.string.grammar_and_style), str(R.string.fix_mistakes_and_see_why), AppIcons.Check) { openToolAd { onOpen(ToolScreen.Grammar) } },
        ToolCardSpec(str(R.string.slang_translator), str(R.string.what_street_phrases_really_mean), AppIcons.Quote) { openToolAd { onOpen(ToolScreen.Slang) } },
        ToolCardSpec(str(R.string.travel_phrases), str(R.string.ready_phrases_for_where_you), AppIcons.Plane) { openToolAd { onOpen(ToolScreen.Travel) } },
        ToolCardSpec(str(R.string.offline_languages), str(R.string.download_packs_to_translate_without), AppIcons.Globe) { onOpenOffline() },
        ToolCardSpec(str(R.string.translator_keyboard), str(R.string.type_in_your_language_send), AppIcons.Keyboard) { openToolAd { onOpen(ToolScreen.Keyboard) } },
    )
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenHeader(str(R.string.ai_tools), Modifier.padding(horizontal = pad - 8.dp)) { HistoryAction(onOpenHistory) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(str(R.string.advanced_features), style = Type.Headline, color = Tk.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(str(R.string.ai_chat), str(R.string.ask_practise_or_get_a), AppIcons.Chat, listOf(Tk.Primary, Color(0xFF4C86F0)), true, Modifier.weight(1f)) { openToolAd { onOpen(ToolScreen.Chat) } }
                    FeatureTile(str(R.string.photo_translate), str(R.string.signs_and_menus_translated), AppIcons.Camera, listOf(Color(0xFF0E8F7E), Color(0xFF3CC5A8)), true, Modifier.weight(1f)) { onOpenPhoto() }
                }
                Text(str(R.string.ai_tools), style = Type.Headline, color = Tk.Text, modifier = Modifier.padding(top = 6.dp))
                grid.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c -> GridCard(c, Modifier.weight(1f)) { c.onClick?.invoke() } }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                Box(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeatureTile(title: String, body: String, icon: ImageVector, colors: List<Color>, available: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(176.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(colors))
            .clickable(enabled = available, role = Role.Button, onClick = onClick).padding(16.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { AppIcon(icon, Color.White, size = 26.dp) }
        Box(Modifier.weight(1f))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(if (available) body else str(R.string.not_available_yet), color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun GridCard(c: ToolCardSpec, modifier: Modifier, onClick: () -> Unit) {
    val enabled = c.onClick != null
    Column(
        modifier.height(164.dp).clip(Radius.Card).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(16.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) Tk.PrimaryContainer else Tk.Surface2), contentAlignment = Alignment.Center) {
            AppIcon(c.icon, if (enabled) Tk.Primary else Tk.Muted, size = 26.dp)
        }
        Box(Modifier.weight(1f))
        Text(c.title, color = if (enabled) Tk.Text else Tk.Soft, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(if (enabled) c.body else str(R.string.not_available_yet), color = Tk.Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
    }
}

@Composable
private fun ToolHeader(title: String, subtitle: String?, onBack: () -> Unit, pad: androidx.compose.ui.unit.Dp, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = pad - 8.dp, end = pad, top = pad), verticalAlignment = Alignment.CenterVertically) {
        IconAction(AppIcons.Back, str(R.string.back), onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Headline, color = Tk.Text)
            if (subtitle != null) Text(subtitle, color = Tk.Muted, fontSize = 13.sp)
        }
        trailing()
    }
}

// ---------------------------------------------------------------- Chat

private fun chatSuggestions() = listOf(
    str(R.string.explain_ser_vs_estar_in),
    str(R.string.make_this_natural_i_am),
    str(R.string.how_do_i_politely_decline),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatScreen(vm: AiChatViewModel, aiAllowed: Boolean, onBack: () -> Unit, onHistory: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle(context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val listState = rememberLazyListState()
    // A new message scrolls into view smoothly; while an answer is streaming in, the list just stays pinned to its end
    // (re-starting a scroll animation for every few words made the text jitter).
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex + 1)
    }
    LaunchedEffect(state.messages.lastOrNull()?.text?.length) {
        if (state.sending && state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex + 1)
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            ToolHeader(str(R.string.ai_chat), str(R.string.ask_practise_or_get_a), onBack, pad) {
                IconAction(AppIcons.Clock, str(R.string.chat_history), onHistory)
                if (state.messages.isNotEmpty()) IconAction(AppIcons.Refresh, str(R.string.new_chat), vm::onClear)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = pad, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Bubble(str(R.string.hi_ask_me_to_explain), fromUser = false, cursor = false) }
                if (state.messages.isEmpty()) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chatSuggestions().forEach { s -> AppChip(s, selected = false, onClick = { if (aiAllowed) vm.onSend(s) }) }
                    }
                }
                items(state.messages, key = { it.id }) { m ->
                    val waiting = state.sending && !m.fromUser && m.id == state.messages.last().id
                    if (waiting && m.text.isEmpty()) ThinkingBubble(Modifier.fillMaxWidth(0.5f))
                    else Bubble(m.text, m.fromUser, cursor = waiting)
                }
                state.error?.let { e -> item { Text(e.message(), color = Tk.Danger, fontSize = 13.sp) } }
            }
            if (!aiAllowed) Text(str(R.string.cloud_note), color = Tk.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(start = pad, end = pad, top = 6.dp, bottom = pad),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Tk.Surface2).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    BasicTextField(
                        state.input, vm::onInput, maxLines = 4, cursorBrush = SolidColor(Tk.Primary),
                        textStyle = Type.Body.copy(color = Tk.Text), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> Box { if (state.input.isEmpty()) Text(str(R.string.ask_anything_about_language), color = Tk.Muted, style = Type.Body); inner() } },
                    )
                }
                if (state.sending) {
                    CircleButton(AppIcons.Stop, str(R.string.stop), Tk.Coral, vm::onStop)
                } else {
                    CircleButton(AppIcons.Arrow, str(R.string.send), if (state.input.isNotBlank() && aiAllowed) Tk.Primary else Tk.Off) { if (aiAllowed) vm.onSend() }
                }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(color).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, Color.White, size = 20.dp) }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean, cursor: Boolean) {
    val shape = RoundedCornerShape(20.dp, 20.dp, if (fromUser) 6.dp else 20.dp, if (fromUser) 20.dp else 6.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.fillMaxWidth(0.86f).clip(shape)
                .background(if (fromUser) Tk.Primary else Tk.Surface)
                .then(if (fromUser) Modifier else Modifier.border(1.dp, Tk.Line, shape))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                if (cursor) "$text▍" else text,
                color = if (fromUser) Color.White else Tk.Text, fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
    }
}

// ---------------------------------------------------------------- Email, grammar, slang

private enum class WriterSpec(
    val kind: AiWriterKind,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val label: Int,
    @StringRes val hint: Int,
    val options: List<String>,
    @StringRes val optionLabel: Int,
    @StringRes val action: Int,
) {
    Email(AiWriterKind.Email, R.string.email_writer, R.string.spec_email_sub, R.string.spec_email_label, R.string.spec_email_hint, listOf("Formal", "Friendly", "Business"), R.string.spec_tone, R.string.spec_write_email),
    Grammar(AiWriterKind.Grammar, R.string.grammar_and_style, R.string.fix_mistakes_and_see_why, R.string.spec_grammar_label, R.string.spec_grammar_hint, emptyList(), R.string.spec_tone, R.string.spec_check_text),
    Slang(AiWriterKind.Slang, R.string.slang_translator, R.string.spec_slang_sub, R.string.spec_slang_label, R.string.spec_slang_hint, listOf("Spanish", "Urdu", "Arabic", "Hindi"), R.string.spec_explain_in, R.string.spec_explain),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriterScreen(spec: WriterSpec, vm: AiWriterViewModel, aiAllowed: Boolean, onBack: () -> Unit, onTranslateText: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle(context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val clipboard = LocalClipboardManager.current
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            ToolHeader(str(spec.title), str(spec.subtitle), onBack, pad)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard(Modifier.fillMaxWidth()) {
                    SectionLabel(str(spec.label))
                    BasicTextField(
                        state.input, vm::onInput, cursorBrush = SolidColor(Tk.Primary),
                        textStyle = Type.Input.copy(color = Tk.Text, fontSize = 18.sp, lineHeight = 26.sp),
                        // Fixed height, scrolls inside: pasted text never moves the action button.
                        modifier = Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                        decorationBox = { inner -> Box { if (state.input.isEmpty()) Text(str(spec.hint), color = Tk.Muted, fontSize = 18.sp, lineHeight = 26.sp); inner() } },
                    )
                }
                if (spec.options.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str(spec.optionLabel), color = Tk.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    spec.options.forEach { o -> AppChip(optionLabel(o), o == state.option, { vm.onOption(o) }) }
                }
                if (!aiAllowed) Text(str(R.string.cloud_note), color = Tk.Muted, fontSize = 13.sp)
                PrimaryButton(if (state.loading) str(R.string.working) else str(spec.action), vm::onRun, Modifier.fillMaxWidth(), enabled = aiAllowed && state.input.isNotBlank() && !state.loading)
                state.error?.let { Text(it.message(), color = Tk.Danger, fontSize = 14.sp) }
                androidx.compose.animation.AnimatedVisibility(visible = state.loading, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()) { AiThinkingCard() }
                state.result?.let { result ->
                    AppCard(Modifier.fillMaxWidth(), border = BorderStroke(2.dp, Tk.Primary)) {
                        SectionLabel(str(R.string.result), color = Tk.Primary)
                        Text(result, color = Tk.Text, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 6.dp))
                        var copied by rememberSaveable { mutableStateOf(false) }
                        androidx.compose.runtime.LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1_600); copied = false } }
                        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton(str(R.string.regenerate), AppIcons.Refresh, { vm.onRun() }, Modifier.weight(1f))
                                ActionButton(if (copied) str(R.string.copied) else str(R.string.copy), if (copied) AppIcons.Check else AppIcons.Copy, { clipboard.setText(AnnotatedString(result)); Analytics.copied("ai_tool"); copied = true }, Modifier.weight(1f))
                            }
                            ActionButton(str(R.string.translate_result), AppIcons.Translate, { onTranslateText(result) }, Modifier.fillMaxWidth(), style = ActionStyle.Filled)
                        }
                    }
                    Text(str(R.string.written_by_ai_so_double), color = Tk.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Travel phrases

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravelScreen(vm: AiTravelViewModel, aiAllowed: Boolean, speechRate: Float, lang: Language, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle(context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val tts = rememberTtsPlayer()
    val clipboard = LocalClipboardManager.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            ToolHeader(str(R.string.travel_phrases), str(R.string.for_your_trip, lang.localName()), onBack, pad)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    travelCategories.forEach { c -> AppChip(categoryLabel(c), c == state.category, { vm.onCategory(c) }) }
                }
                if (!aiAllowed) Text(str(R.string.cloud_note), color = Tk.Muted, fontSize = 13.sp)
                PrimaryButton(if (state.loading) str(R.string.working) else str(R.string.get_phrases, categoryLabel(state.category).lowercase()), { vm.onLoad(lang.code) }, Modifier.fillMaxWidth(), enabled = aiAllowed && !state.loading)
                state.error?.let { Text(it.message(), color = Tk.Danger, fontSize = 14.sp) }
                if (state.loading) repeat(3) { PhraseSkeleton() }
                state.phrases.forEach { p ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(p.english, color = Tk.Muted, fontSize = 13.sp)
                        Text(
                            p.native, color = Tk.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, modifier = Modifier.padding(top = 2.dp),
                            style = TextStyle(textDirection = if (lang.rtl) TextDirection.Rtl else TextDirection.Ltr),
                        )
                        if (p.romanization != null) Text(p.romanization, color = Tk.Primary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        Row(Modifier.padding(top = 4.dp)) {
                            val phraseId = "phrase-" + p.native
                            val playing = tts.speakingId == phraseId
                            IconAction(
                                if (playing) AppIcons.Stop else AppIcons.Speaker, str(if (playing) R.string.stop_reading else R.string.listen),
                                { if (playing) tts.stop() else tts.speak(p.native, state.langCode.ifEmpty { lang.code }, speechRate, phraseId) },
                                tint = if (playing) Tk.Primary else Tk.Soft,
                            )
                            IconAction(AppIcons.Copy, str(R.string.copy), { clipboard.setText(AnnotatedString(p.native)); Analytics.copied("travel") })
                        }
                    }
                }
                if (state.phrases.isNotEmpty()) Text(str(R.string.translated_automatically_so_double_check), color = Tk.Muted, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Translator keyboard set-up

@Composable
private fun KeyboardSetupScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    fun refresh() {
        val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        enabled = imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true
        val current = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        selected = current.startsWith(context.packageName + "/")
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { refresh(); onPauseOrDispose { } }
    // The keyboard picker is a dialog, so the screen is not paused and resumed: check again while this screen is open.
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { refresh(); kotlinx.coroutines.delay(700) } }
    var test by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            ToolHeader(str(R.string.translator_keyboard), str(R.string.type_in_your_language_send), onBack, pad)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupStep(1, str(R.string.turn_on_the_keyboard), str(R.string.switch_on_ai_translator_keyboard), enabled, str(R.string.open_keyboard_settings), true) {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                SetupStep(2, str(R.string.choose_it_for_typing), str(R.string.pick_ai_translator_keyboard_as), selected, str(R.string.choose_keyboard), enabled) {
                    context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showInputMethodPicker()
                }
                AppCard(Modifier.fillMaxWidth()) {
                    SectionLabel(str(R.string.try_it_here))
                    BasicTextField(
                        test, { test = it }, cursorBrush = SolidColor(Tk.Primary),
                        textStyle = Type.Input.copy(color = Tk.Text, fontSize = 18.sp, lineHeight = 26.sp),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).padding(top = 8.dp),
                        decorationBox = { inner -> Box { if (test.isEmpty()) Text(str(R.string.tap_here_type_something_then), color = Tk.Muted, fontSize = 16.sp, lineHeight = 24.sp); inner() } },
                    )
                }
                AppCard(Modifier.fillMaxWidth()) {
                    SectionLabel(str(R.string.how_it_works))
                    Text(str(R.string.pick_the_language_you_type), color = Tk.Soft, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(str(R.string.type_as_usual_urdu_has), color = Tk.Soft, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
                    Text(str(R.string.tap_translate_your_words_are), color = Tk.Soft, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    str(R.string.privacy_nothing_leaves_your_phone),
                    color = Tk.Muted, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, body: String, done: Boolean, button: String, buttonEnabled: Boolean, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(if (done) Tk.OkText else Tk.PrimaryContainer), contentAlignment = Alignment.Center) {
                if (done) AppIcon(AppIcons.Check, Color.White, size = 18.dp) else Text("$number", color = Tk.Primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Tk.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (done) str(R.string.done) else body, color = if (done) Tk.OkText else Tk.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        if (!done) PrimaryButton(button, onClick, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = buttonEnabled, height = 48.dp)
    }
}

// ---------------------------------------------------------------- Saved chats

@Composable
private fun ChatHistoryScreen(vm: AiChatViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<ChatSession?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val shown = sessions.filter { (!favoritesOnly || it.favorite) && (q.isEmpty() || it.title.contains(q, true) || it.preview.contains(q, true)) }
    // All and Favorites each have their own native ad, shown only while that tab has chats in it. An empty tab shows no ad.
    val nativeSlot = if (favoritesOnly) RcKeys.NATIVE_CHAT_FAVORITES else RcKeys.NATIVE_CHAT_HISTORY
    val tabHasItems = if (favoritesOnly) sessions.any { it.favorite } else sessions.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            // A soft gradient header: the title, a line about privacy, then All / Favorites.
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Tk.PrimaryContainer, Tk.Bg)))) {
                ToolHeader(str(R.string.chat_history), str(R.string.chats_sub), onBack, pad)
                Row(
                    Modifier.fillMaxWidth().padding(start = pad, end = pad, top = 14.dp, bottom = 10.dp).clip(Radius.Pill).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Pill).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(false to R.string.chats_tab_all, true to R.string.favorites).forEach { (fav, label) ->
                        val on = favoritesOnly == fav
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(Radius.Pill)
                                .background(if (on) Brush.linearGradient(listOf(Tk.Primary, Color(0xFF4C86F0))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickable(role = Role.Tab) { favoritesOnly = fav },
                            contentAlignment = Alignment.Center,
                        ) { Text(str(label), color = if (on) Color.White else Tk.Soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                if (sessions.size > 4) Row(
                    Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 10.dp).clip(Radius.Pill).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Pill)
                        .padding(horizontal = 14.dp).defaultMinSize(minHeight = 44.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(AppIcons.Search, Tk.Muted, size = 18.dp)
                    BasicTextField(
                        query, { query = it }, singleLine = true, cursorBrush = SolidColor(Tk.Primary), textStyle = Type.Body.copy(color = Tk.Text), modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text(str(R.string.search_chats), color = Tk.Muted, style = Type.Body); inner() } },
                    )
                }
            }
            if (shown.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = pad + 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(
                        Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Tk.PrimaryContainer, Color.White))).border(1.dp, Tk.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(if (favoritesOnly) AppIcons.Star else AppIcons.Chat, Tk.Primary, size = 38.dp) }
                    Text(
                        str(when { q.isNotEmpty() -> R.string.nothing_matches_your_search; favoritesOnly -> R.string.chats_no_favs_title; else -> R.string.chats_empty_title }),
                        color = Tk.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 18.dp),
                    )
                    if (q.isEmpty()) Text(
                        str(if (favoritesOnly) R.string.chats_no_favs_body else R.string.chats_empty_body),
                        color = Tk.Muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                // Search found nothing, but the tab itself has chats: its ad stays. An empty tab shows no ad.
                if (tabHasItems) com.translator.app.core.ads.NativeAdCard(nativeSlot)
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tk.Surface).border(1.dp, if (c.favorite) Tk.Sand.copy(alpha = 0.7f) else Tk.Line, RoundedCornerShape(24.dp))
                                .clickable(role = Role.Button) { onOpen(c.id) }.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Tk.Primary, Color(0xFF7B5CF0)))),
                                contentAlignment = Alignment.Center,
                            ) { AppIcon(AppIcons.Sparkle, Color.White, size = 24.dp) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(c.title, color = Tk.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (c.preview.isNotBlank()) Text(
                                    c.preview.replace('\n', ' '), color = Tk.Soft, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                                )
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        relativeTime(c.updatedAt), color = Tk.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clip(Radius.Pill).background(Tk.PrimaryContainer).padding(horizontal = 10.dp, vertical = 3.dp),
                                    )
                                    Text(str(R.string.chat_messages_count, c.messageCount), color = Tk.Muted, fontSize = 12.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconAction(
                                    if (c.favorite) AppIcons.StarFilled else AppIcons.Star,
                                    str(if (c.favorite) R.string.chat_unfavorite else R.string.chat_favorite), { vm.onFavorite(c) }, tint = Tk.Sand, size = 22.dp,
                                )
                                IconAction(AppIcons.Trash, str(R.string.chat_delete), { deleting = c }, tint = Tk.Muted, size = 20.dp)
                            }
                        }
                    }
                    // Native card below the chat list, not above it.
                    item(key = nativeSlot) { com.translator.app.core.ads.NativeAdCard(nativeSlot) }
                }
            }
            PrimaryButton(str(R.string.new_chat), onNew, Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = pad, top = 4.dp), icon = AppIcons.Plus, height = 56.dp)
        }
    }
    deleting?.let { c ->
        AppDialog(
            title = str(R.string.chat_delete_title), body = str(R.string.chat_delete_body),
            primaryLabel = str(R.string.delete), onPrimary = { vm.onDelete(c.id); deleting = null },
            secondaryLabel = str(R.string.cancel), onSecondary = { deleting = null }, danger = true,
        )
    }
}
