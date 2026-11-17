package com.translator.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.R
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.AppLanguagePicker
import com.translator.app.core.designsystem.AppSwitch
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.core.i18n.str
import kotlin.math.abs
import kotlinx.coroutines.launch

/** The screens in order. The guide is one pager with three pages, so swiping or tapping Next moves through them. */
private enum class Step { Splash, AppLanguage, Guide, Consent }

private const val GUIDE_PAGES = 3

/** Language + three guide pages + privacy: the five positions the progress line counts. */
private const val POSITIONS = 5

/** Title and body styles shared by every screen: one family, a strong title, a calm body. */
private val TitleStyle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
private val BodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)

/**
 * Splash, app language, a swipeable three-page guide (voice, camera, all tools), then privacy. The order, what is saved and
 * what happens at the end are unchanged; only the look and the way it moves have been redesigned.
 *
 * Design decisions:
 *  - One idea per screen, a big picture, a short title and one or two lines of text.
 *  - Progress is a thin line across the top (overall) plus dots on the guide pages, instead of a label and a segmented bar.
 *  - Skip is always in the same place, as quiet text; Back appears only where going back is meaningful.
 *  - The pictures are drawn in code (see OnboardingArt) so they match the app exactly and cost no image files.
 */
@Composable
fun OnboardingFlow(
    appLanguage: String,
    onAppLanguage: (String) -> Unit,
    askAnalytics: Boolean = true,
    onFinish: (cloudConsent: Boolean, crashReports: Boolean, analytics: Boolean) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(Step.Splash) }
    // Off until the person switches it on: outside the regions where the consent form applies this is the only analytics consent.
    var analytics by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { com.translator.app.core.analytics.Analytics.log(com.translator.app.core.analytics.Ev.ONBOARDING_STARTED) }
    // Start loading the onboarding native card (during the splash, so it is usually ready on the first real screen) and the
    // interstitial shown when onboarding ends. Runs again once ads become allowed, if they were not at first.
    val ads = com.translator.app.core.ads.LocalAds.current
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val adsReady = ads?.adsReady() == true
    LaunchedEffect(adsReady) {
        if (ads != null && activity != null && adsReady) {
            try {
                ads.nativeAds.ensureLoaded(activity, ads.nativeModel(com.translator.app.core.config.RcKeys.NATIVE_ONBOARDING))
                ads.interstitials.warm(com.translator.app.core.config.RcKeys.ONBOARDING_DONE)
            } catch (_: Throwable) { }
        }
    }
    androidx.compose.runtime.LaunchedEffect(step) {
        com.translator.app.core.analytics.Analytics.log(com.translator.app.core.analytics.Ev.ONBOARDING_STEP) { token("step", step.name.lowercase()) }
    }
    var guidePage by rememberSaveable { mutableIntStateOf(0) }

    // The system Back button walks back through the same screens the on-screen Back does.
    BackHandler(enabled = step == Step.Guide || step == Step.Consent) {
        when (step) {
            Step.Consent -> { guidePage = GUIDE_PAGES - 1; step = Step.Guide }
            Step.Guide -> if (guidePage > 0) guidePage-- else step = Step.AppLanguage
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(Tk.Bg)) {
        AnimatedContent(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            targetState = step,
            transitionSpec = { (fadeIn(tween(320, easing = LinearEasing)) + slideInHorizontally(tween(320)) { it / 12 }).togetherWith(fadeOut(tween(140))) },
            label = "onboarding",
        ) { s ->
            when (s) {
                Step.Splash -> SplashScreen { step = Step.AppLanguage }
                Step.AppLanguage -> AppLanguageScreen(appLanguage, onAppLanguage) { guidePage = 0; step = Step.Guide }
                Step.Guide -> GuideScreen(
                    startPage = guidePage,
                    onPage = { guidePage = it },
                    onBack = { step = Step.AppLanguage },
                    onSkip = { step = Step.Consent },
                    onFinishGuide = { step = Step.Consent },
                )
                // The cloud features are always on and there is no crash-report option, so the two settings are fixed values.
                Step.Consent -> ConsentScreen(
                    askAnalytics = askAnalytics, analytics = analytics, onAnalytics = { analytics = it },
                    onBack = { guidePage = GUIDE_PAGES - 1; step = Step.Guide },
                ) { onFinish(true, false, analytics) }
            }
        }
        // A compact native card under every onboarding screen except the splash. It sits outside the animated content, so
        // the same ad stays put while the screens change above it, and it takes no room until an ad has loaded.
        if (step != Step.Splash) {
            Box(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
                com.translator.app.core.ads.NativeAdCard(com.translator.app.core.config.RcKeys.NATIVE_ONBOARDING, compact = true)
            }
        }
    }
}

// ---------------------------------------------------------------- the frame every step shares

/**
 * The page frame: a calm background with two very soft colour glows, the progress line, Back and Skip, the content, and the
 * bottom area (optional dots and the main button). Content is capped to a comfortable width on tablets.
 */
@Composable
private fun Frame(
    position: Int,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    button: String,
    onButton: () -> Unit,
    dotsPage: Int? = null,
    trailingArrow: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(pad: Dp) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Tk.Bg).drawBehind {
            drawCircle(Brush.radialGradient(listOf(Tk.Primary.copy(alpha = 0.11f), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.04f), size.width * 0.8f), size.width * 0.8f, Offset(size.width * 0.9f, size.height * 0.04f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF7B5CF0).copy(alpha = 0.07f), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.62f), size.width * 0.7f), size.width * 0.7f, Offset(size.width * 0.05f, size.height * 0.62f))
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        val pad = pagePadding(maxWidth.value) + 4.dp
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
            TopProgress(position / POSITIONS.toFloat(), str(R.string.ob_step_of, position, POSITIONS), Modifier.padding(start = pad, end = pad, top = 14.dp))
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = pad - 4.dp, end = pad - 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.CenterStart) { if (onBack != null) BackButton(str(R.string.back), onBack) }
                Box(Modifier.weight(1f))
                if (onSkip != null) GhostButton(str(R.string.skip), onSkip)
            }
            content(pad)
            Column(Modifier.fillMaxWidth().padding(start = pad, end = pad, top = 8.dp, bottom = pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (dotsPage != null) PageDots(GUIDE_PAGES, dotsPage, str(R.string.ob_step_of, dotsPage + 1, GUIDE_PAGES))
                PremiumButton(button, onButton, trailingArrow = trailingArrow)
            }
        }
    }
}

// ---------------------------------------------------------------- splash

@Composable
private fun SplashScreen(onDone: () -> Unit) {
    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(target, tween(1500, easing = LinearEasing), label = "splash")
    LaunchedEffect(Unit) { target = 1f }
    LaunchedEffect(progress) { if (progress >= 1f) onDone() }
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0A2FB8), Tk.Primary, Color(0xFF5B3FD6)))).clickable(role = Role.Button, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_splash_icon), null, Modifier.size(210.dp))
            Text("AI Translator", color = Color.White, style = TitleStyle.copy(fontSize = 34.sp), modifier = Modifier.padding(top = 26.dp).reveal(100))
            Text(str(R.string.speak_snap_and_type_in), color = Color.White.copy(alpha = 0.86f), style = BodyStyle, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).reveal(250))
            Box(Modifier.padding(top = 44.dp).size(150.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
    }
}

// ---------------------------------------------------------------- 1. app language

@Composable
private fun AppLanguageScreen(selected: String, onSelect: (String) -> Unit, onContinue: () -> Unit) {
    Frame(position = 1, onBack = null, onSkip = null, button = str(R.string.continue_label), onButton = onContinue) { pad ->
        Column(Modifier.fillMaxWidth().padding(horizontal = pad).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.reveal(0)) { IconBadge(AppIcons.Globe, 52.dp) }
            Text(str(R.string.choose_app_language), color = Tk.Text, style = TitleStyle, modifier = Modifier.reveal(80))
            Text(str(R.string.this_is_the_language_of), color = Tk.Muted, style = BodyStyle, modifier = Modifier.reveal(140))
        }
        AppLanguagePicker(selected, onSelect, Modifier.weight(1f).padding(horizontal = pad).padding(top = 16.dp, bottom = 4.dp))
    }
}

// ---------------------------------------------------------------- 2-4. the guide pager

@Composable
private fun GuideScreen(startPage: Int, onPage: (Int) -> Unit, onBack: () -> Unit, onSkip: () -> Unit, onFinishGuide: () -> Unit) {
    val pager = rememberPagerState(initialPage = startPage) { GUIDE_PAGES }
    val scope = rememberCoroutineScope()
    val motion = rememberMotionEnabled()
    // The system Back (handled above) changes the saved page; follow it, and report swipes back.
    LaunchedEffect(startPage) { if (pager.currentPage != startPage) pager.animateScrollToPage(startPage) }
    LaunchedEffect(pager.currentPage) { onPage(pager.currentPage) }
    val last = pager.currentPage == GUIDE_PAGES - 1
    Frame(
        position = 2 + pager.currentPage,
        onBack = { if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onBack() },
        // Nothing is left to skip on the last page.
        onSkip = if (last) null else onSkip,
        button = if (last) str(R.string.continue_label) else str(R.string.next),
        onButton = { if (last) onFinishGuide() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
        dotsPage = pager.currentPage,
    ) { pad ->
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            // How far this page is from being centred: 0 when it is, -1 or 1 a full page away. The picture drifts against
            // the swipe (parallax) and the text fades faster than the picture, so the page change feels layered.
            fun offset() = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = pad).graphicsLayer {
                        val o = offset()
                        translationX = o * size.width * 0.3f
                        alpha = 1f - abs(o) * 0.75f
                        scaleX = 1f - abs(o) * 0.1f; scaleY = scaleX
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> VoiceArt(motion)
                        1 -> CameraArt(motion)
                        else -> ToolsArt(motion)
                    }
                }
                val (icon, title, body) = guideText(page)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = pad).padding(top = 6.dp, bottom = 4.dp).graphicsLayer { alpha = (1f - abs(offset()) * 1.5f).coerceAtLeast(0f) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Eyebrow(icon, str(R.string.ob_tour))
                    Text(title, color = Tk.Text, style = TitleStyle, textAlign = TextAlign.Center)
                    Text(body, color = Tk.Muted, style = BodyStyle, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

private data class GuideText(val icon: ImageVector, val title: String, val body: String)

@Composable
private fun guideText(page: Int): GuideText = when (page) {
    0 -> GuideText(AppIcons.Mic, str(R.string.talk_with_anyone_in_real), str(R.string.speak_and_hear_the_translation))
    1 -> GuideText(AppIcons.Camera, str(R.string.point_your_camera_at_any), str(R.string.menus_signs_and_documents_in))
    else -> GuideText(AppIcons.Sparkle, str(R.string.ob_all_title), str(R.string.ob_all_body))
}

// ---------------------------------------------------------------- 5. privacy

@Composable
private fun ConsentScreen(
    askAnalytics: Boolean,
    analytics: Boolean,
    onAnalytics: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAgree: () -> Unit,
) {
    Frame(position = 5, onBack = onBack, onSkip = null, button = str(R.string.agree_and_start), onButton = onAgree, trailingArrow = false) { pad ->
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.reveal(0)) { IconBadge(AppIcons.Shield, 56.dp, listOf(Color(0xFF0B7A6C), Color(0xFF1FA68F))) }
            Text(str(R.string.your_privacy_your_choice), color = Tk.Text, style = TitleStyle, modifier = Modifier.reveal(80))
            Text(str(R.string.no_account_no_name_no), color = Tk.Muted, style = BodyStyle, modifier = Modifier.reveal(140))
            // What is sent, in plain words. There is nothing to switch: tapping "Agree and start" accepts it.
            Text(str(R.string.audio_and_photos_are_sent), color = Tk.Soft, style = BodyStyle, modifier = Modifier.reveal(200))
            SoftCard(Modifier.padding(top = 4.dp).reveal(260)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Tk.Ok.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { AppIcon(AppIcons.Check, Tk.Ok, size = 22.dp) }
                    Text(str(R.string.history_and_favorites_stay_on), color = Tk.Soft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            }
            // The one optional choice: anonymous usage statistics, off unless switched on. Ads and statistics in regions with a
            // consent form follow that form instead, so this row is not shown there.
            if (askAnalytics) SoftCard(Modifier.reveal(320)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(str(R.string.analytics_share_title), color = Tk.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(str(R.string.analytics_share_body), color = Tk.Soft, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    AppSwitch(analytics, onAnalytics, str(R.string.analytics_share_title))
                }
            }
        }
    }
}
