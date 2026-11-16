package com.translator.app.feature.voice

import com.translator.app.core.i18n.str
import com.translator.app.R
import com.translator.app.core.i18n.localName
import com.translator.app.core.ads.BannerFrame
import com.translator.app.core.config.RcKeys
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.core.data.AppSettings
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.HistoryAction
import com.translator.app.core.designsystem.LanguageBarCard
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.ScreenHeader
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.feature.translate.LanguageSheet
import com.translator.app.feature.translate.TranslateUiState
import com.translator.app.feature.translate.TranslateViewModel
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Voice tab: a hero with a moving wave, then the voice modes as cards. Manual, Conversation, Real-time and Headphone
 * all run on the device speech recognizer.
 */
@Composable
fun VoiceHome(
    state: TranslateUiState,
    vm: TranslateViewModel,
    voiceVm: VoiceViewModel,
    liveVms: Map<LiveMode, LiveViewModel>,
    settings: AppSettings,
    onOpenHistory: () -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // null = the mode list; otherwise the open mode's name ("Manual" or a LiveMode name)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val ads = com.translator.app.core.ads.LocalAds.current
    androidx.compose.runtime.LaunchedEffect(Unit) { ads?.interstitials?.warm(RcKeys.VOICE_MODE_OPEN) }
    val openVoiceAd = com.translator.app.core.backpress.rememberNavigationAd(RcKeys.VOICE_MODE_OPEN)
    // Back (system and arrow) leaves the open mode through one path; an interstitial may show first (never while busy).
    val closeMode = com.translator.app.core.backpress.rememberBackPressAd(com.translator.app.core.config.RcKeys.VOICE_MODE_BACK, enabled = open != null) { open = null }
    // A voice mode takes the whole screen: the bottom tabs hide while one is open.
    com.translator.app.core.designsystem.HideTabBar(open != null)

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pad = pagePadding(maxWidth.value)
        val narrow = maxWidth < 400.dp
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            val live = LiveMode.entries.firstOrNull { it.name == open }
            if (open == "manual") {
                ScreenHeader(str(R.string.manual_mode), Modifier.padding(horizontal = pad - 8.dp), onBack = closeMode)
                BannerFrame(RcKeys.SLOT_VOICE) {
                    VoiceScreen(
                        state, vm, voiceVm, settings.speakAloud, settings.speechRate, settings.haptics, narrow,
                        onPick = { pickingFor = it }, onTypeInstead = onTypeInstead,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
                    )
                }
            } else if (live != null) {
                ScreenHeader(str(live.title), Modifier.padding(horizontal = pad - 8.dp), onBack = closeMode)
                BannerFrame(RcKeys.SLOT_VOICE) {
                    LiveVoiceScreen(
                        live, state, vm, liveVms.getValue(live), settings,
                        onPick = { pickingFor = it }, onTypeInstead = onTypeInstead,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
                    )
                }
            } else {
                ScreenHeader(str(R.string.voice_translator), Modifier.padding(horizontal = pad - 8.dp)) { HistoryAction(onOpenHistory) }
                LanguageBarCard(state.source.localName(), state.target.localName(), { pickingFor = "from" }, { pickingFor = "to" }, vm::onSwap, Modifier.padding(horizontal = pad))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WaveHero()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeTile(AppIcons.Convo, str(R.string.conversation), str(R.string.two_people_one_phone_take), listOf(Tk.Primary, Color(0xFF4C86F0)), Modifier.weight(1f)) { openVoiceAd { open = LiveMode.Conversation.name } }
                        ModeTile(AppIcons.Wave, str(R.string.real_time), str(R.string.hands_free_subtitles_as_you), listOf(Color(0xFF5B3FD6), Color(0xFF8C6CF2)), Modifier.weight(1f)) { openVoiceAd { open = LiveMode.Realtime.name } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeTile(AppIcons.Headphones, str(R.string.headphone), str(R.string.hear_each_translation_read_aloud), listOf(Color(0xFF0B7A6C), Color(0xFF1FA68F)), Modifier.weight(1f)) { openVoiceAd { open = LiveMode.Headphone.name } }
                        ModeTile(AppIcons.Chat, str(R.string.manual), str(R.string.tap_speak_read_simple_and), listOf(CoralDeep, CoralSoft), Modifier.weight(1f)) { openVoiceAd { open = "manual" } }
                    }
                }
            }
        }
    }
    pickingFor?.let { which ->
        LanguageSheet(
            title = if (which == "from") str(R.string.translate_from_2) else str(R.string.translate_to_2),
            recent = listOf(state.source, state.target).distinct(),
            onPick = { lang -> if (which == "from") vm.onSource(lang) else vm.onTarget(lang); pickingFor = null },
            onDismiss = { pickingFor = null },
        )
    }
}

/**
 * The hero: a deep blue card with soft light circles and a moving wave. The animation phase is read only while drawing,
 * so the bars repaint every frame without recomposing or re-measuring anything.
 */
@Composable
private fun WaveHero() {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Box(
        Modifier.fillMaxWidth().height(184.dp).shadow(14.dp, RoundedCornerShape(32.dp), ambientColor = Tk.Primary, spotColor = Tk.Primary)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B3FD9), Tk.Primary, Color(0xFF4C86F0)))),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.75f, center = Offset(size.width * 0.9f, -size.height * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.55f, center = Offset(size.width * 0.05f, size.height * 1.05f))
        }
        Canvas(Modifier.padding(top = 26.dp).size(width = 225.dp, height = 64.dp)) {
            val barW = 5.dp.toPx()
            val gap = 5.dp.toPx()
            val radius = CornerRadius(3.dp.toPx())
            repeat(23) { i ->
                val h = 12f + 48f * (0.5f + 0.5f * sin(phase + i * 0.55f)) * (0.55f + 0.45f * sin(i * 0.3f + 1f).let { it * it })
                val hPx = h.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f + 0.4f * (h / 60f)),
                    topLeft = Offset(i * (barW + gap), (size.height - hPx) / 2f),
                    size = Size(barW, hPx),
                    cornerRadius = radius,
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(str(R.string.talk_to_anyone), color = Color.White, style = Type.Headline.copy(fontSize = 22.sp))
            Text(str(R.string.speak_in_your_language_and), color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
        }
    }
}

/** A mode tile: a coloured gradient card with a glass icon, a title and one line. */
@Composable
private fun ModeTile(icon: ImageVector, title: String, body: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.height(172.dp).shadow(10.dp, shape, ambientColor = colors.last(), spotColor = colors.first())
            .clip(shape).background(Brush.linearGradient(colors))
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) { AppIcon(icon, Color.White, size = 26.dp) }
        Box(Modifier.weight(1f))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(body, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
    }
}
