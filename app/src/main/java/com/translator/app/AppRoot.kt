package com.translator.app

import androidx.compose.runtime.mutableStateOf
import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.translator.app.core.data.AppSettings
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.model.Languages
import com.translator.app.core.ai.AiWriterKind
import com.translator.app.feature.aitools.AiChatViewModel
import com.translator.app.feature.aitools.AiToolsHost
import com.translator.app.feature.aitools.AiToolsViewModels
import com.translator.app.feature.aitools.AiTravelViewModel
import com.translator.app.feature.aitools.AiWriterViewModel
import com.translator.app.feature.history.HistoryScreen
import com.translator.app.feature.history.HistoryViewModel
import com.translator.app.feature.onboarding.OnboardingFlow
import com.translator.app.feature.settings.SettingsScreen
import com.translator.app.feature.camera.PhotoScreen
import com.translator.app.feature.offline.OfflineScreen
import com.translator.app.feature.offline.OfflineViewModel
import com.translator.app.core.offline.LocalOfflineReady
import com.translator.app.feature.camera.PhotoViewModel
import com.translator.app.feature.voice.LiveViewModel
import com.translator.app.feature.voice.VoiceHome
import com.translator.app.feature.voice.VoiceViewModel
import com.translator.app.feature.translate.TranslateHome
import com.translator.app.feature.translate.TranslateViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.translator.app.core.ads.BannerFrame
import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.backpress.ReportBusy
import com.translator.app.core.backpress.rememberBackPressAd
import com.translator.app.core.config.RcKeys
import com.translator.app.core.consent.currentSignals
import com.translator.app.core.consent.applyAnalytics
import kotlinx.coroutines.launch

private enum class Tab(@androidx.annotation.StringRes val label: Int, val icon: ImageVector) {
    Text(R.string.text, AppIcons.Text),
    Voice(R.string.voice, AppIcons.Wave),
    Photo(R.string.photo, AppIcons.Camera),
    Tools(R.string.tools, AppIcons.Sparkle),
    Me(R.string.tab_me, AppIcons.Person),
}

@Composable
fun AppRoot(container: AppContainer) {
    val loaded by remember { container.settings.settings.map<AppSettings, AppSettings?> { it } }.collectAsStateWithLifecycle(initialValue = null)
    val settings = loaded ?: run { Box(Modifier.fillMaxSize().background(Tk.Bg)); return }
    val scope = rememberCoroutineScope()
    // Apply the saved app language (and any change made in Settings) to every string in the app.
    androidx.compose.runtime.SideEffect { com.translator.app.core.i18n.AppLocale.set(settings.appLanguage) }

    // Under an RTL app language (Urdu, Arabic) the whole layout mirrors.
    val rtl = Languages.byCodeOrNull(settings.appLanguage)?.rtl == true
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScaleBoost),
    ) {
        Box(Modifier.fillMaxSize().background(Tk.Bg).systemBarsPadding()) {
            if (!settings.onboardingDone) {
                // Where consent rules apply the analytics answer comes from the consent form; elsewhere it is asked here.
                // Reads Compose state, so it follows the consent result when that arrives.
                val askAnalytics = container.ads.askAnalytics()
                OnboardingFlow(
                    appLanguage = settings.appLanguage,
                    onAppLanguage = { scope.launch { container.settings.setAppLanguage(it) } },
                    askAnalytics = askAnalytics,
                    onFinish = { cloud, crash, analytics ->
                        val ask = container.ads.askAnalytics()
                        container.syncPrefs.analyticsChoice = if (ask) (if (analytics) 1 else 0) else -1
                        applyAnalytics(container.syncPrefs)
                        Analytics.log(Ev.ONBOARDING_COMPLETED) { flag("analytics_opt_in", analytics && ask) }
                        scope.launch { container.settings.completeOnboarding(cloud, crash) }
                    },
                )
            } else {
                MainNav(container)
            }
        }
    }
}

@Composable
private fun MainNav(container: AppContainer) {
    val nav = rememberNavController()
    val vm: TranslateViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TranslateViewModel(container.settings, container.translateText, container.history, container.aiInsights, container::warmUpTranslation) }
        },
    )
    // Collected without waiting for the next frame, so the text box and the state never disagree while typing. The value is
    // only read inside the tab that shows it: reading it here would rebuild the whole navigation on every keystroke.
    val state by vm.state.collectAsStateWithLifecycle(context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val voiceVm: VoiceViewModel = viewModel(factory = viewModelFactory { initializer { VoiceViewModel(container.translateText) } })
    // Conversation, Real-time and Headphone each have their own view model, so what was said in one never shows up in another.
    val liveVmList = com.translator.app.feature.voice.LiveMode.entries.map { mode ->
        viewModel<LiveViewModel>(key = "live-" + mode.name, factory = viewModelFactory { initializer { LiveViewModel(container.translateText) } })
    }
    // Kept as the same map object while the view models stay the same, so screens that take it can skip redrawing.
    val liveVms: Map<com.translator.app.feature.voice.LiveMode, LiveViewModel> = remember(liveVmList) { com.translator.app.feature.voice.LiveMode.entries.zip(liveVmList).toMap() }
    val offlineVm: OfflineViewModel = viewModel(factory = viewModelFactory { initializer { OfflineViewModel(container.offlinePacks) } })
    val offlineReady by container.offlinePacks.ready.collectAsStateWithLifecycle()
    val offlineBusy by container.offlinePacks.busy.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(1_500); container.offlinePacks.refresh() }
    val photoVm: PhotoViewModel = viewModel(factory = viewModelFactory { initializer { PhotoViewModel(container.aiPhoto) } })
    val historyVm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container.history) } })
    val chatVm: AiChatViewModel = viewModel(key = "ai-chat", factory = viewModelFactory { initializer { AiChatViewModel(container.aiChat, container.chats) } })
    val emailVm: AiWriterViewModel = viewModel(key = "ai-email", factory = viewModelFactory { initializer { AiWriterViewModel(AiWriterKind.Email, container.aiTools) } })
    val grammarVm: AiWriterViewModel = viewModel(key = "ai-grammar", factory = viewModelFactory { initializer { AiWriterViewModel(AiWriterKind.Grammar, container.aiTools) } })
    val slangVm: AiWriterViewModel = viewModel(key = "ai-slang", factory = viewModelFactory { initializer { AiWriterViewModel(AiWriterKind.Slang, container.aiTools) } })
    val travelVm: AiTravelViewModel = viewModel(key = "ai-travel", factory = viewModelFactory { initializer { AiTravelViewModel(container.aiTools) } })
    val toolsVms = remember(chatVm, emailVm, grammarVm, slangVm, travelVm) { AiToolsViewModels(chatVm, emailVm, grammarVm, slangVm, travelVm) }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings(onboardingDone = true))
    val historyCount by historyVm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabBar = remember { com.translator.app.core.designsystem.TabBarController() }
    val goTo: (Tab) -> Unit = { tab = it.ordinal }

    // Screens doing work are "busy": leaving them never shows an ad on top of the work.
    val photoWorking by remember { photoVm.state.map { it is com.translator.app.feature.camera.PhotoUiState.Working }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = false)
    BusyWhile("translate") { state.loading }
    ReportBusy("photo", photoWorking)
    LaunchedEffect(tab) { Analytics.screen(Tab.entries[tab].name.lowercase()) }
    // Analytics switch in Settings: shows what is really in effect (consent rules can keep it off).
    var analyticsOn by remember { mutableStateOf(currentSignals(container.syncPrefs).analyticsStorage) }
    val hostActivity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    var privacyOptions by remember { mutableStateOf(container.syncPrefs.privacyOptionsRequired) }

    // Android 13 and newer ask before an app may show notifications; ask once, right after the first-run screens.
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = appContext.getSharedPreferences("prompts", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notifications_asked", false)) {
                prefs.edit().putBoolean("notifications_asked", true).apply()
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val openHistory = { nav.navigate("history") }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalOfflineReady provides offlineReady,
        com.translator.app.core.offline.LocalOfflineBusy provides offlineBusy,
        com.translator.app.core.offline.LocalOfflineDownload provides offlineVm::onDownload,
    ) {
    NavHost(
        nav, startDestination = "main",
        enterTransition = { fadeIn(androidx.compose.animation.core.tween(160)) },
        exitTransition = { fadeOut(androidx.compose.animation.core.tween(100)) },
        popEnterTransition = { fadeIn(androidx.compose.animation.core.tween(160)) },
        popExitTransition = { fadeOut(androidx.compose.animation.core.tween(100)) },
    ) {
        composable("main") {
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            var confirmExit by rememberSaveable { mutableStateOf(false) }
            // Back from any other tab goes to the Text tab; only from the Text tab does it offer to leave the app. Screens opened
            // from a tab (a voice mode, an AI tool) handle Back themselves first, because they are composed after this.
            androidx.activity.compose.BackHandler(enabled = !confirmExit) { if (tab != 0) tab = 0 else confirmExit = true }
            if (confirmExit) {
                com.translator.app.core.designsystem.AppDialog(
                    title = str(R.string.exit_title), body = str(R.string.exit_body),
                    primaryLabel = str(R.string.exit_yes), onPrimary = { confirmExit = false; activity?.finish() },
                    secondaryLabel = str(R.string.cancel), onSecondary = { confirmExit = false },
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(com.translator.app.core.designsystem.LocalTabBar provides tabBar) {
            MainScaffold(tab, { tab = it }, tabBar.hidden) { current ->
                when (current) {
                    Tab.Text -> TranslateHome(state, vm, settings, onOpenHistory = { openHistory() }, onOpenVoice = { goTo(Tab.Voice) }, onOpenPhoto = { goTo(Tab.Photo) })
                    Tab.Voice -> VoiceHome(state, vm, voiceVm, liveVms, settings, onOpenHistory = { openHistory() }, onTypeInstead = { goTo(Tab.Text) })
                    Tab.Photo -> PhotoScreen(state, vm, photoVm, settings, onOpenHistory = { openHistory() })
                    Tab.Tools -> AiToolsHost(
                        vms = toolsVms,
                        aiAllowed = settings.cloudConsent,
                        speechRate = settings.speechRate,
                        targetLang = state.target,
                        // "Translate result" hands the text to the Text tab, ready to translate.
                        onTranslateText = { text -> vm.onTextChange(text); goTo(Tab.Text) },
                        onOpenHistory = { openHistory() },
                        onOpenPhoto = { goTo(Tab.Photo) },
                        onOpenOffline = { nav.navigate("offline") },
                    )
                    Tab.Me -> SettingsScreen(
                        settings = settings,
                        historyCount = historyCount.all.size,
                        offlineCount = offlineReady.size,
                        onOpenOffline = { nav.navigate("offline") },
                        onAppLanguage = { scope.launch { container.settings.setAppLanguage(it) } },
                        onSpeakAloud = { scope.launch { container.settings.setSpeakAloud(it) } },
                        onHaptics = { scope.launch { container.settings.setHaptics(it) } },
                        onTextSize = { scope.launch { container.settings.setTextSize(it) } },
                        onReadSpeed = { scope.launch { container.settings.setReadSpeed(it) } },
                        onClearHistory = { historyVm.onClearAll() },
                        onDeleteAll = { scope.launch { container.deleteAllData(); vm.onNewTranslation(); analyticsOn = currentSignals(container.syncPrefs).analyticsStorage } },
                        analyticsOn = analyticsOn,
                        onAnalytics = { on ->
                            container.syncPrefs.analyticsChoice = if (on) 1 else 0
                            applyAnalytics(container.syncPrefs)
                            analyticsOn = currentSignals(container.syncPrefs).analyticsStorage
                        },
                        showPrivacyOptions = privacyOptions,
                        onPrivacyOptions = {
                            hostActivity?.let { a ->
                                container.ads.consent.showPrivacyOptions(a) {
                                    analyticsOn = currentSignals(container.syncPrefs).analyticsStorage
                                    privacyOptions = container.syncPrefs.privacyOptionsRequired
                                }
                            }
                        },
                    )
                }
            }
            }
        }
        composable(
            "history",
            enterTransition = { slideInHorizontally(androidx.compose.animation.core.tween(200)) { it / 5 } + fadeIn(androidx.compose.animation.core.tween(200)) },
            popExitTransition = { slideOutHorizontally(androidx.compose.animation.core.tween(180)) { it / 5 } + fadeOut(androidx.compose.animation.core.tween(180)) },
        ) {
            LaunchedEffect(Unit) { Analytics.screen("history") }
            val back = rememberBackPressAd(RcKeys.HISTORY_BACK) { nav.popBackStack() }
            BannerFrame(RcKeys.SLOT_HISTORY) { HistoryScreen(historyVm, onBack = back, onStartTranslating = { goTo(Tab.Text); nav.popBackStack() }) }
        }
        composable(
            "offline",
            enterTransition = { slideInHorizontally(androidx.compose.animation.core.tween(200)) { it / 5 } + fadeIn(androidx.compose.animation.core.tween(200)) },
            popExitTransition = { slideOutHorizontally(androidx.compose.animation.core.tween(180)) { it / 5 } + fadeOut(androidx.compose.animation.core.tween(180)) },
        ) {
            LaunchedEffect(Unit) { Analytics.screen("offline") }
            val back = rememberBackPressAd(RcKeys.OFFLINE_BACK) { nav.popBackStack() }
            BannerFrame(RcKeys.SLOT_OFFLINE) { OfflineScreen(offlineVm, onBack = back) }
        }
    }
    }
}

/**
 * Shows one tab. Only the new tab is built when you switch (the old one is dropped at once) and it fades in quickly. A
 * cross-fade keeps both tabs alive for the whole change, which made switching to a heavy tab stutter.
 */
@Composable
private fun TabContent(current: Tab, content: @Composable (Tab) -> Unit) {
    androidx.compose.runtime.key(current) {
        val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(Unit) { alpha.animateTo(1f, androidx.compose.animation.core.tween(120)) }
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) { content(current) }
    }
}

/** Bottom bar on phones; a left rail when the window is wider than 560dp (landscape, tablets). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MainScaffold(selected: Int, onSelect: (Int) -> Unit, barHidden: Boolean, content: @Composable (Tab) -> Unit) {
    val current = Tab.entries[selected]
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > 560.dp) {
            Row(Modifier.fillMaxSize()) {
                if (!barHidden) Column(
                    Modifier.fillMaxHeight().width(88.dp).background(Tk.Surface).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { Tab.entries.forEachIndexed { i, t -> NavItem(t, i == selected) { onSelect(i) } } }
                Box(Modifier.weight(1f).fillMaxHeight()) { BannerFrame(bannerSlotFor(current), show = !barHidden) { TabContent(current, content) } }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { BannerFrame(bannerSlotFor(current), show = !barHidden) { TabContent(current, content) } }
                // With the keyboard open the tab bar would sit between the text box and the keys, leaving a wide gap.
                if (!androidx.compose.foundation.layout.WindowInsets.Companion.isImeVisible && !barHidden) {
                    HorizontalDivider(color = Tk.Line)
                    Row(Modifier.fillMaxWidth().background(Tk.Surface).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
                        Tab.entries.forEachIndexed { i, t -> NavItem(t, i == selected, Modifier.weight(1f)) { onSelect(i) } }
                    }
                }
            }
        }
    }
}

/** The banner slot of a tab. Photo has none (the camera is never shown next to an ad). */
private fun bannerSlotFor(tab: Tab): String? = when (tab) {
    Tab.Text -> RcKeys.SLOT_TEXT
    Tab.Voice -> RcKeys.SLOT_VOICE
    Tab.Tools -> RcKeys.SLOT_TOOLS
    Tab.Me -> RcKeys.SLOT_SETTINGS
    Tab.Photo -> null
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = if (selected) Tk.Primary else Tk.Muted
    Column(
        modifier.height(60.dp).clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.height(32.dp).width(56.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Tk.PrimaryContainer else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { AppIcon(tab.icon, color, size = 24.dp) }
        Text(str(tab.label), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Reports a screen as busy from its own small scope, so a change in [busy] redraws only this and not the caller. */
@Composable
private fun BusyWhile(key: String, busy: () -> Boolean) = ReportBusy(key, busy())
