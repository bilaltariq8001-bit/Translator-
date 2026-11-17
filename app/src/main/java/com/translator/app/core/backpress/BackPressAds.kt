package com.translator.app.core.backpress

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.translator.app.core.ads.InterstitialManager
import com.translator.app.core.audio.TtsPlayer

/**
 * Screens that are doing work (listening, translating, a live session, an AI answer, a photo being read) report it here, so
 * leaving them never shows an ad on top of the work.
 */
class BusyRegistry {
    private val flags = mutableStateMapOf<String, Boolean>()
    fun set(key: String, busy: Boolean) { if (busy) flags[key] = true else flags.remove(key) }
    val isBusy: Boolean get() = flags.isNotEmpty()
}

/**
 * One path for every ad-backed Back: the system Back and the on-screen arrow call the same lambda. Busy screens, and
 * anything without an ad, just navigate. A second press while the first is being handled is swallowed (re-entry flag, with a
 * safety timeout), and navigation runs only after the ad is closed (or at once when there is none), exactly once.
 */
class BackPressAds(private val interstitials: InterstitialManager, val busy: BusyRegistry = BusyRegistry()) {
    private val main = Handler(Looper.getMainLooper())
    private var inFlight = false

    /** True while a Back placement is waiting for, or presenting, its interstitial. */
    val isHandling: Boolean get() = inFlight

    fun handle(activity: Activity?, placement: String, navigate: () -> Unit) {
        if (inFlight) return
        if (activity == null || busy.isBusy) { navigate(); return }
        // The microphone is idle here (a listening screen is "busy"); the reader voice could still be talking.
        TtsPlayer.stopAll()
        inFlight = true
        var done = false
        val finish = {
            if (!done) { done = true; inFlight = false; navigate() }
        }
        // Safety for the short wait before the ad appears; cancelled once it is on screen, because people may watch it for long.
        val safety = Runnable { finish() }
        main.postDelayed(safety, SAFETY_MS)
        interstitials.show(activity, placement, onShown = { main.removeCallbacks(safety) }, onDone = { main.removeCallbacks(safety); finish() })
    }

    fun handleNavigation(activity: Activity?, placement: String, navigate: () -> Unit) {
        if (inFlight) return
        if (activity == null || busy.isBusy) { navigate(); return }
        TtsPlayer.stopAll()
        inFlight = true
        var done = false
        val finish = {
            if (!done) { done = true; inFlight = false; navigate() }
        }
        val safety = Runnable { finish() }
        main.postDelayed(safety, SAFETY_MS)
        interstitials.showNavigation(activity, placement, onShown = { main.removeCallbacks(safety) }, onDone = { main.removeCallbacks(safety); finish() })
    }

    private companion object { const val SAFETY_MS = 6_000L }
}

val LocalBackAds = staticCompositionLocalOf<BackPressAds?> { null }

/** Marks this screen busy for as long as [busy] is true. */
@Composable
fun ReportBusy(key: String, busy: Boolean) {
    val registry = LocalBackAds.current?.busy ?: return
    DisposableEffect(key, busy) {
        registry.set(key, busy)
        onDispose { registry.set(key, false) }
    }
}

/**
 * Returns the Back lambda for a screen with an ad [placement]. It is also registered as the system Back handler while
 * [enabled], so the arrow and the system Back run the same code. [onNavigate] is the screen's normal leave action.
 */
@Composable
fun rememberBackPressAd(placement: String, enabled: Boolean = true, onNavigate: () -> Unit): () -> Unit {
    val backAds = LocalBackAds.current
    val activity = LocalContext.current as? Activity
    val navigate by rememberUpdatedState(onNavigate)
    val back: () -> Unit = remember(backAds, activity, placement) {
        {
            if (backAds == null) navigate() else backAds.handle(activity, placement) { navigate() }
        }
    }
    BackHandler(enabled = enabled) { back() }
    return back
}

/**
 * Returns a navigation lambda that shows an interstitial ad for [placement] before running [onNavigate].
 */
@Composable
fun rememberNavigationAd(placement: String): (onNavigate: () -> Unit) -> Unit {
    val backAds = LocalBackAds.current
    val activity = LocalContext.current as? Activity
    return remember(backAds, activity, placement) {
        { onNavigate ->
            if (backAds == null) onNavigate() else backAds.handleNavigation(activity, placement, onNavigate)
        }
    }
}

/** Uses of each feature since the app started (voice modes and AI tools). Not saved: a restart starts every count again. */
object FeatureUseCounter {
    private val counts = HashMap<String, Int>()

    /** Counts one more use of [feature] and returns its number (1 for the first). */
    @Synchronized fun next(feature: String): Int = ((counts[feature] ?: 0) + 1).also { counts[feature] = it }
}

/**
 * Returns a wrapper for one use of a feature (a mic tap in a voice mode, a Generate tap in an AI tool). The first N uses
 * run straight away; use N+1, 2N+1, ... shows the [placement] interstitial first, then runs. N comes from Remote Config
 * (`<placement>_inter_every`, 3 by default). Each [feature] keeps its own count. The ad is warmed on use N so it is ready.
 */
@Composable
fun rememberFeatureUseAd(placement: String, feature: String): (action: () -> Unit) -> Unit {
    val ads = com.translator.app.core.ads.LocalAds.current
    val showAd = rememberNavigationAd(placement)
    return remember(ads, showAd, placement, feature) {
        { action ->
            val every = ads?.config?.useEvery(placement) ?: 3
            val n = FeatureUseCounter.next("$placement:$feature")
            if (n > every && (n - 1) % every == 0) {
                showAd(action)
            } else {
                if (n % every == 0) ads?.interstitials?.warm(placement)
                action()
            }
        }
    }
}
