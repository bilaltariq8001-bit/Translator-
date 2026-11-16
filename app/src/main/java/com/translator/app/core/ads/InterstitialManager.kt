package com.translator.app.core.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.translator.app.core.config.ConfigManager
import com.translator.app.core.config.InterstitialAdModel
import com.translator.app.core.config.RcKeys
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Keeps a small cache of loaded interstitials (size from Remote Config, 1-5), shows the oldest one on request and refills.
 * Back-press placements never wait: with no ad ready, or capped, or off, the caller continues at once. All work happens on the
 * main thread; every load and show first asks [AdGate].
 */
class InterstitialManager(
    private val config: ConfigManager,
    private val gate: AdGate,
    private val initializer: AdMobInitializer,
    private val capping: InterstitialCapping = InterstitialCapping(),
) {
    private class Cached(val ad: InterstitialAd, val loadedAt: Long, val adId: String)

    private val main = Handler(Looper.getMainLooper())
    private val cache = ArrayDeque<Cached>()
    private var pending = 0
    private val pendingUnits = ArrayList<String>()

    /** True while a full-screen ad is on screen or about to be. */
    var showing = false
        private set

    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }

    /**
     * Fills the cache up to its size. Each enabled back-press placement first gets one ad of its own unit (the [prefer]red
     * placement first, evicting an ad of another unit if the cache is full), then spare slots are filled the same way.
     */
    fun warm(prefer: String? = null) = onMain {
        if (!gate.canLoad()) return@onMain
        dropExpired()
        val limit = config.capping().cacheSize
        val placements = (listOfNotNull(prefer) + RcKeys.BACK_PLACEMENTS).distinct()
        for (placement in placements) {
            val model = config.interstitial(placement)
            if (!model.enabled || !model.shouldLoad) continue
            if (cache.any { it.adId == model.adId } || pendingUnits.contains(model.adId)) continue
            if (cache.size + pending >= limit) {
                if (placement == prefer && cache.isNotEmpty()) cache.removeFirst() else break
            }
            request(model) { }
        }
        for (placement in placements) {
            if (cache.size + pending >= limit) break
            val model = config.interstitial(placement)
            if (model.enabled && model.shouldLoad) request(model) { }
        }
    }

    private fun dropExpired() {
        // Ads expire after about an hour; drop them a little earlier.
        val now = SystemClock.elapsedRealtime()
        cache.removeAll { now - it.loadedAt > 55 * 60_000L }
    }

    /** Sends one request if allowed and a slot is free. [done] gets the ad (also when the request cannot be made: null). */
    private fun request(model: InterstitialAdModel, intoCache: Boolean = true, done: (InterstitialAd?) -> Unit): Boolean {
        if (!gate.canLoad() || model.adId.isEmpty()) { done(null); return false }
        if (intoCache) {
            if (cache.size + pending >= config.capping().cacheSize) { done(null); return false }
            pending++
            pendingUnits.add(model.adId)
        }
        fun release() { if (intoCache) { if (pending > 0) pending--; pendingUnits.remove(model.adId) } }
        val accepted = initializer.whenReady(onFail = { release(); done(null) }) {
            if (!gate.canLoad()) { release(); done(null); return@whenReady }
            val startedAt = SystemClock.elapsedRealtime()
            AdEvents.request(model.placement, FORMAT)
            try {
                InterstitialAd.load(AdRequest.Builder(model.adId).build(), object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) = onMain {
                        AdEvents.loaded(model.placement, FORMAT, SystemClock.elapsedRealtime() - startedAt)
                        AdDiagnostics.loaded(FORMAT)
                        if (intoCache) { release(); cache.addLast(Cached(ad, SystemClock.elapsedRealtime(), model.adId)) }
                        done(ad)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) = onMain {
                        AdEvents.failed(model.placement, FORMAT, adError.code.toString())
                        AdDiagnostics.record(FORMAT, adError.code.toString(), adError.message)
                        release()
                        done(null)
                    }
                })
            } catch (_: Throwable) {
                release()
                done(null)
            }
        }
        if (!accepted) { release(); done(null) }
        return accepted
    }

    /**
     * Shows the ad for a back-press [placement], then calls [onDone] exactly once. [onDone] also runs at once when there is
     * nothing to show (off, capped, no ad ready, not allowed), so the caller always continues. [onShown] runs when the ad
     * is on screen.
     */
    fun show(activity: Activity, placement: String, onShown: () -> Unit = {}, onDone: () -> Unit) = onMain {
        val model = config.interstitial(placement)
        if (showing || !model.enabled || !gate.canLoad() || activity.isFinishing || activity.isDestroyed) { onDone(); return@onMain }
        capping.recordEligible()
        dropExpired()
        if (!capping.allows(config.capping(), model.counterEnabled)) { warm(placement); onDone(); return@onMain }
        val index = cache.indexOfFirst { it.adId == model.adId }
        if (index < 0) { warm(prefer = placement); onDone(); return@onMain }
        val cached = cache.removeAt(index)
        present(cached.ad, activity, placement, onShown, onDone)
    }

    private fun present(ad: InterstitialAd, activity: Activity, placement: String, onShown: () -> Unit, onDone: () -> Unit) {
        showing = true
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            showing = false
            onDone()
            warm()
        }
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() = onMain {
                capping.onShown()
                AdEvents.shown(placement, FORMAT)
                onShown()
            }

            override fun onAdDismissedFullScreenContent() = onMain {
                AdEvents.dismissed(placement, FORMAT)
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = onMain {
                AdEvents.failed(placement, FORMAT, "show_failed")
                finish()
            }

            override fun onAdClicked() { AdEvents.clicked(placement, FORMAT) }
        }
        try {
            ad.show(activity)
        } catch (_: Throwable) {
            finish()
        }
    }

    /**
     * The splash interstitial: waits at most [waitMs] for the ad to load, then shows it and returns when it is closed. Returns
     * at once (false) when it is off, [stillAllowed] says no at the last moment, capped, not allowed or does not load in time.
     */
    suspend fun showSplash(activity: Activity, waitMs: Long = 3_000L, stillAllowed: () -> Boolean = { true }): Boolean {
        val model = config.interstitial(RcKeys.SPLASH)
        if (!model.enabled || !model.shouldLoad || !gate.canLoad() || showing) return false
        if (!capping.allows(config.capping(), model.counterEnabled)) return false
        val ad = withTimeoutOrNull(waitMs) {
            suspendCancellableCoroutine<InterstitialAd?> { cont ->
                onMain { request(model, intoCache = false) { if (cont.isActive) cont.resume(it) } }
            }
        } ?: return false
        // Re-check right before showing: the world may have changed while the ad loaded.
        if (activity.isFinishing || activity.isDestroyed || showing || !stillAllowed()) return false
        return suspendCancellableCoroutine { cont ->
            onMain { present(ad, activity, RcKeys.SPLASH, onShown = { }, onDone = { if (cont.isActive) cont.resume(true) }) }
        }
    }

    private companion object { const val FORMAT = "interstitial" }
}
