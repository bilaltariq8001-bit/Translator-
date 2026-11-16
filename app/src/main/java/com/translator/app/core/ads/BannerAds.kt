package com.translator.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.translator.app.core.config.AdPosition
import com.translator.app.core.config.ConfigManager
import com.translator.app.core.config.InScreenAdModel
import com.translator.app.core.config.InScreenAdType

enum class BannerState { Loading, Loaded, Failed }

/**
 * Anchored adaptive banners, one per slot, kept between visits so switching tabs does not send a new request each time (a
 * slot reloads when its ad is older than a minute). The SDK view lives here; screens only host it. Every load asks [AdGate].
 */
class BannerAdManager(
    private val config: ConfigManager,
    private val gate: AdGate,
    private val initializer: AdMobInitializer,
) {
    private class Entry(val view: AdView, val startedAt: Long) {
        var loadedAt = 0L
        var ad: BannerAd? = null

        /** Frees the SDK ad and view; the view is taken out of any screen first. */
        fun destroy() {
            (view.parent as? ViewGroup)?.removeView(view)
            try { ad?.destroy() } catch (_: Throwable) { }
            try { view.destroy() } catch (_: Throwable) { }
        }
    }

    /** The banner on screen (or last shown) per slot, and the request in flight per slot. */
    private val shown = HashMap<String, Entry>()
    private val loading = HashMap<String, Entry>()
    private val failedAt = HashMap<String, Long>()
    val states = mutableStateMapOf<String, BannerState>()
    private val revisions = mutableStateMapOf<String, Int>()

    fun viewFor(slot: String): AdView? {
        revisions[slot]
        return shown[slot]?.takeIf { states[slot] == BannerState.Loaded }?.view
    }

    fun ensureLoaded(activity: Activity, model: InScreenAdModel, size: AdSize) {
        val slot = model.slot
        if (!model.enabled || model.type != InScreenAdType.BANNER || model.adId.isEmpty()) { states[slot] = BannerState.Failed; return }
        if (!gate.canLoad()) return
        val now = SystemClock.elapsedRealtime()
        val inFlight = loading[slot]
        if (inFlight != null && now - inFlight.startedAt < LOAD_GIVE_UP_MS) return
        val current = shown[slot]
        if (current != null && now - current.loadedAt < REFRESH_MS) return
        val failed = failedAt[slot]
        if (current == null && failed != null && now - failed < RETRY_MS) return
        if (states[slot] == null || states[slot] == BannerState.Failed) states[slot] = BannerState.Loading
        initializer.whenReady(onFail = { fail(slot) }) { load(activity, model, size) }
    }

    private fun fail(slot: String) {
        loading.remove(slot)?.destroy()
        failedAt[slot] = SystemClock.elapsedRealtime()
        if (shown[slot] == null) states[slot] = BannerState.Failed
    }

    private fun load(activity: Activity, model: InScreenAdModel, size: AdSize) {
        val slot = model.slot
        if (!gate.canLoad() || activity.isFinishing || activity.isDestroyed) { fail(slot); return }
        val view = try { AdView(activity) } catch (_: Throwable) { fail(slot); return }
        view.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val entry = Entry(view, SystemClock.elapsedRealtime())
        loading.remove(slot)?.destroy()
        loading[slot] = entry
        val builder = BannerAdRequest.Builder(model.adId, size)
        if (model.collapsing) {
            builder.setGoogleExtrasBundle(Bundle().apply { putString("collapsible", if (model.position == AdPosition.TOP) "top" else "bottom") })
        }
        AdEvents.request(slot, FORMAT)
        try {
            view.loadAd(builder.build(), object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    activity.runOnUiThread {
                        if (loading[slot] !== entry) { try { ad.destroy() } catch (_: Throwable) { }; entry.destroy(); return@runOnUiThread }
                        loading.remove(slot)
                        entry.ad = ad
                        entry.loadedAt = SystemClock.elapsedRealtime()
                        AdEvents.loaded(slot, FORMAT, entry.loadedAt - entry.startedAt)
                        ad.adEventCallback = object : BannerAdEventCallback {
                            override fun onAdImpression() { AdEvents.shown(slot, FORMAT) }
                            override fun onAdClicked() { AdEvents.clicked(slot, FORMAT) }
                        }
                        // The new ad replaces the old one; the old one is destroyed once the new one is the one on screen.
                        val old = shown.put(slot, entry)
                        states[slot] = BannerState.Loaded
                        revisions[slot] = (revisions[slot] ?: 0) + 1
                        old?.destroy()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    activity.runOnUiThread {
                        AdEvents.failed(slot, FORMAT, adError.code.toString())
                        if (loading[slot] === entry) fail(slot) else entry.destroy()
                    }
                }
            })
        } catch (_: Throwable) {
            if (loading[slot] === entry) fail(slot)
        }
    }

    /** Destroys every banner (the activity is going away). */
    fun release() {
        loading.values.forEach { it.destroy() }
        shown.values.forEach { it.destroy() }
        loading.clear()
        shown.clear()
        states.clear()
        revisions.clear()
    }

    private companion object {
        const val FORMAT = "banner"
        const val REFRESH_MS = 60_000L
        const val RETRY_MS = 45_000L
        const val LOAD_GIVE_UP_MS = 30_000L
    }
}

/**
 * Banner area for one slot. Draws nothing when the slot is off, ads are not allowed, the keyboard is open, or the request
 * failed. While loading it reserves the banner's height as empty space (no shimmer), so the screen does not jump when the ad
 * arrives. Place it only where the tab bar is visible; the callers do that.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BannerSlot(slot: String, modifier: Modifier = Modifier) {
    val ads = LocalAds.current ?: return
    val activity = LocalContext.current as? Activity ?: return
    val model = ads.bannerModel(slot)
    // Reads Compose state, so the ad appears as soon as consent or the connection becomes available.
    val ready = ads.adsReady()
    if (!model.enabled || !ready) return
    // The same slot can serve either format; the type comes from Remote Config (`<slot>_in_screen_ad_type`).
    if (model.type == InScreenAdType.NATIVE) {
        NativeAdSlot(slot, model, modifier)
        return
    }
    if (WindowInsets.isImeVisible) return

    val widthDp = LocalConfiguration.current.screenWidthDp
    val size = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp) }
    LaunchedEffect(slot, widthDp, model, ready) { ads.banners.ensureLoaded(activity, model, size) }

    val state = ads.banners.states[slot] ?: BannerState.Loading
    if (state == BannerState.Failed) return
    val heightDp = with(LocalDensity.current) { size.getHeightInPixels(activity).toDp() }
    Box(modifier.fillMaxWidth().height(heightDp)) {
        if (state == BannerState.Loaded) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(heightDp),
                factory = { FrameLayout(it) },
                update = { container ->
                    val view = ads.banners.viewFor(slot)
                    if (view != null && view.parent !== container) {
                        (view.parent as? ViewGroup)?.removeView(view)
                        container.removeAllViews()
                        container.addView(view)
                    }
                },
                onRelease = { container -> container.removeAllViews() },
            )
        }
    }
}

/**
 * Wraps a screen with its banner above or below it (position from Remote Config; the default is below). The structure is
 * the same whether or not a banner is shown, so the screen inside keeps its state when the banner comes and goes.
 */
@Composable
fun BannerFrame(slot: String?, show: Boolean = true, content: @Composable () -> Unit) {
    val ads = LocalAds.current
    val active = slot != null && ads != null && show
    val top = active && ads!!.bannerModel(slot!!).position == AdPosition.TOP
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        if (active && top) BannerSlot(slot!!)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (active && !top) BannerSlot(slot!!)
    }
}

/** The ads system, for screens that need it. Null in previews and tests, where every ad composable then draws nothing. */
val LocalAds = compositionLocalOf<AdsController?> { null }
