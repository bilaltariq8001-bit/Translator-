package com.translator.app.core.ads

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.translator.app.BuildConfig
import com.translator.app.core.backpress.BackPressAds
import com.translator.app.core.config.AdIdDefaults
import com.translator.app.core.config.ConfigManager
import com.translator.app.core.config.InScreenAdModel
import com.translator.app.core.config.NativeAdModel
import com.translator.app.core.consent.ConsentManager
import com.translator.app.core.util.SyncPrefs

/** Everything about ads, consent and remote config in one place, created by the container. */
class AdsController(context: Context, val prefs: SyncPrefs, isOnline: () -> Boolean) {
    val config = ConfigManager(
        context,
        AdIdDefaults(
            banner = BuildConfig.ADMOB_BANNER_ID,
            interstitial = BuildConfig.ADMOB_INTERSTITIAL_ID,
            appOpen = BuildConfig.ADMOB_APP_OPEN_ID,
            native = BuildConfig.ADMOB_NATIVE_ID,
            testBanner = BuildConfig.ADMOB_TEST_BANNER_ID,
            testInterstitial = BuildConfig.ADMOB_TEST_INTERSTITIAL_ID,
            testAppOpen = BuildConfig.ADMOB_TEST_APP_OPEN_ID,
            testNative = BuildConfig.ADMOB_TEST_NATIVE_ID,
            debug = BuildConfig.DEBUG,
        ),
    )
    val gate = AdGate(prefs, isOnline)
    val initializer = AdMobInitializer(context, gate)
    val interstitials = InterstitialManager(config, gate, initializer)
    val backAds = BackPressAds(interstitials)
    val banners = BannerAdManager(config, gate, initializer)
    val appOpen = AppOpenAdManager(config, gate, initializer, prefs) {
        interstitials.showing || backAds.isHandling || backAds.busy.isBusy
    }
    val nativeAds = NativeAdManager(config, gate, initializer)
    val consent = ConsentManager(context, prefs)

    /** Bumped when the consent answer or the connection changes, so screens that depend on them recompose. */
    private var consentVersion by mutableIntStateOf(0)
    private var netVersion by mutableIntStateOf(0)

    init {
        if (!BuildConfig.DEBUG && BuildConfig.ADS_USING_TEST_IDS) android.util.Log.w("Ads", "Release build is using Google test ad ids; set real ids in local.properties.")
        consent.onChanged = { consentVersion++ }
        try {
            context.getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { netVersion++; tryPreload() }
                override fun onLost(network: android.net.Network) { netVersion++ }
            })
        } catch (_: Throwable) { }
        tryPreload()
    }

    private fun tryPreload() {
        initializer.whenReady {
            appOpen.loadAd()
        }
    }

    /** True when ads may be requested right now. Reads Compose state, so a banner appears as soon as consent or the connection arrives. */
    fun adsReady(): Boolean {
        consentVersion; netVersion
        return gate.canLoad()
    }

    /** True when the first-run screen must ask about analytics: no consent rules apply, or the person switched it off before. */
    fun askAnalytics(): Boolean {
        consentVersion
        return !prefs.consentRegulated || prefs.analyticsChoice == 0
    }

    /** Bumped when new Remote Config values arrive, so banner slots re-read their settings. */
    private var configVersion by mutableIntStateOf(0)
    private val bannerModels = HashMap<String, InScreenAdModel>()
    private val nativeModels = HashMap<String, NativeAdModel>()

    /** The settings of a banner slot; reading it in a composable makes the screen follow later config changes. */
    fun bannerModel(slot: String): InScreenAdModel {
        configVersion
        return bannerModels.getOrPut(slot) { config.banner(slot) }
    }

    fun nativeModel(slot: String): NativeAdModel {
        configVersion
        return nativeModels.getOrPut(slot) { config.nativeAd(slot) }
    }

    suspend fun refreshConfig() {
        if (config.refresh()) {
            bannerModels.clear()
            nativeModels.clear()
            configVersion++
        }
    }

    fun release() {
        banners.release()
        nativeAds.release()
    }
}
