package com.translator.app.core.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.translator.app.R
import com.translator.app.core.config.ConfigManager
import com.translator.app.core.config.InScreenAdModel
import com.translator.app.core.config.InScreenAdType
import com.translator.app.core.config.NativeAdModel

/**
 * Loads and keeps one native ad per slot. A slot is requested only once it is actually on screen, only while [AdGate]
 * allows it, and a failed request is retried after [RETRY_MS] instead of never again.
 */
class NativeAdManager(
    private val config: ConfigManager,
    private val gate: AdGate,
    private val initializer: AdMobInitializer,
) {
    private val main = Handler(Looper.getMainLooper())
    private val loadedAds = HashMap<String, NativeAd>()
    private val loading = HashSet<String>()
    private val failedAt = HashMap<String, Long>()

    /** Slots whose ad has finished loading; reading it in a composable makes the card appear when the ad arrives. */
    var adState by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    fun ensureLoaded(activity: Activity, model: NativeAdModel) {
        val slot = model.slot
        Log.d("AdsDebug", "NativeAd: ensureLoaded slot=$slot, enabled=${model.enabled}, adId=${model.adId}, canLoad=${gate.canLoad()}")
        if (!model.enabled || model.adId.isEmpty() || !gate.canLoad()) return
        if (loadedAds.containsKey(slot)) {
            Log.d("AdsDebug", "NativeAd: slot=$slot already loaded")
            return
        }
        if (loading.contains(slot)) return
        val failed = failedAt[slot]
        if (failed != null && SystemClock.elapsedRealtime() - failed < RETRY_MS) return

        loading.add(slot)
        fun fail(reason: String) {
            loading.remove(slot)
            failedAt[slot] = SystemClock.elapsedRealtime()
            Log.d("AdsDebug", "NativeAd: slot=$slot failed ($reason)")
        }
        val accepted = initializer.whenReady(onFail = { fail("SDK not ready") }) {
            if (!gate.canLoad()) { loading.remove(slot); return@whenReady }
            val startedAt = SystemClock.elapsedRealtime()
            AdEvents.request(slot, FORMAT)
            try {
                NativeAdLoader.load(
                    NativeAdRequest.Builder(model.adId, listOf(NativeAd.NativeAdType.NATIVE)).build(),
                    object : NativeAdLoaderCallback {
                        override fun onNativeAdLoaded(nativeAd: NativeAd) {
                            main.post {
                                loading.remove(slot)
                                failedAt.remove(slot)
                                loadedAds[slot] = nativeAd
                                Log.d("AdsDebug", "NativeAd: loaded successfully for slot=$slot in ${SystemClock.elapsedRealtime() - startedAt}ms")
                                AdEvents.loaded(slot, FORMAT, SystemClock.elapsedRealtime() - startedAt)
                                AdDiagnostics.loaded(FORMAT)
                                nativeAd.adEventCallback = object : NativeAdEventCallback {
                                    override fun onAdImpression() { AdEvents.shown(slot, FORMAT); Log.d("AdsDebug", "NativeAd: impression for slot=$slot") }
                                    override fun onAdClicked() { AdEvents.clicked(slot, FORMAT); Log.d("AdsDebug", "NativeAd: clicked for slot=$slot") }
                                }
                                adState = adState + (slot to true)
                            }
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            main.post {
                                AdEvents.failed(slot, FORMAT, adError.code.toString())
                                AdDiagnostics.record(FORMAT, adError.code.toString(), adError.message)
                                fail("code=${adError.code}, message=${adError.message}")
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                fail("exception: ${e.message}")
            }
        }
        if (!accepted) fail("ads not allowed")
    }

    fun getAd(slot: String): NativeAd? = loadedAds[slot]

    fun release() {
        loadedAds.values.forEach { try { it.destroy() } catch (_: Throwable) {} }
        loadedAds.clear()
        loading.clear()
        failedAt.clear()
        adState = emptyMap()
    }

    private companion object {
        const val FORMAT = "native"
        const val RETRY_MS = 12_000L
    }
}

/**
 * A native ad card for a slot that has its own native settings ([NativeAdCard]) or for a slot whose in-screen type is
 * NATIVE ([NativeAdSlot]). Draws nothing until the ad has loaded.
 */
@Composable
fun NativeAdCard(slot: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ads = LocalAds.current ?: return
    if (!ads.adsReady()) return
    // A slot configured as NATIVE is rendered by BannerFrame; drawing the inline card too would attach the same ad twice.
    if (ads.bannerModel(slot).type == InScreenAdType.NATIVE) return
    val model = ads.nativeModel(slot)
    NativeAdContent(ads, model, modifier, compact)
}

@Composable
fun NativeAdSlot(slot: String, model: InScreenAdModel, modifier: Modifier = Modifier) {
    val ads = LocalAds.current ?: return
    NativeAdContent(ads, NativeAdModel(slot = slot, adId = model.nativeAdId, enabled = model.enabled), modifier)
}

@Composable
private fun NativeAdContent(ads: AdsController, model: NativeAdModel, modifier: Modifier, compact: Boolean = false) {
    val activity = LocalContext.current as? Activity ?: return
    Log.d("AdsDebug", "NativeAdCard: slot=${model.slot}, enabled=${model.enabled}, adId=${model.adId}, adsReady=${ads.adsReady()}")
    if (!model.enabled || model.adId.isEmpty() || !ads.adsReady()) return

    // Keep asking while the card is on screen and has no ad: a no-fill or a slow SDK start used to leave the slot empty for
    // the whole screen (onboarding is shorter than the old 45 s retry), so the card never appeared.
    LaunchedEffect(model.slot, model.adId, activity) {
        while (ads.nativeAds.getAd(model.slot) == null) {
            ads.nativeAds.ensureLoaded(activity, model)
            kotlinx.coroutines.delay(NATIVE_RETRY_POLL_MS)
        }
    }

    // Subscribe to adState so this composable recomposes when the ad finishes loading.
    val adLoaded = ads.nativeAds.adState[model.slot] == true
    val nativeAd = if (adLoaded) ads.nativeAds.getAd(model.slot) else null
    if (nativeAd == null) return

    AndroidView(
        // Keep the full native-ad view visible so attribution, registered assets, and the SDK's
        // AdChoices overlay are not clipped by a fixed maximum height.
        // The compact card is placed by its caller, which already pads it to the page edges.
        modifier = if (compact) modifier.fillMaxWidth() else modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(if (compact) R.layout.native_ad_compact else R.layout.native_ad_layout, null) as NativeAdView
        },
        update = { adView ->
            if (adView.getTag(R.id.ad_media) === nativeAd) return@AndroidView
            try {
                val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
                val bodyView = adView.findViewById<TextView>(R.id.ad_body)
                val callToActionView = adView.findViewById<TextView>(R.id.ad_call_to_action)
                val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
                val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
                val mediaView = adView.findViewById<MediaView>(R.id.ad_media)

                adView.headlineView = headlineView
                adView.bodyView = bodyView
                adView.callToActionView = callToActionView
                adView.iconView = iconView
                adView.advertiserView = advertiserView

                headlineView?.text = nativeAd.headline
                bodyView?.text = nativeAd.body
                callToActionView?.text = nativeAd.callToAction
                advertiserView?.text = nativeAd.advertiser
                // Empty assets take no room (most matter on the compact card, where space is short).
                bodyView?.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
                advertiserView?.visibility = if (nativeAd.advertiser.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
                callToActionView?.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
                if (nativeAd.icon != null) {
                    iconView?.setImageDrawable(nativeAd.icon?.drawable)
                    iconView?.visibility = View.VISIBLE
                } else {
                    iconView?.visibility = View.GONE
                }

                adView.registerNativeAd(nativeAd, mediaView)
                adView.setTag(R.id.ad_media, nativeAd)
            } catch (e: Throwable) {
                // A malformed ad must not take the screen down; try the next composition/ad.
                Log.d("AdsDebug", "NativeAd: could not bind slot=${model.slot}: ${e.message}", e)
            }
        },
        onRelease = { adView ->
            adView.setTag(R.id.ad_media, null)
            try { adView.destroy() } catch (_: Throwable) {}
        },
    )
}

private const val NATIVE_RETRY_POLL_MS = 4_000L
