package com.translator.app.core.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.translator.app.core.config.ConfigManager
import com.translator.app.core.util.SyncPrefs

/** Process-scoped App Open ad cache. All state transitions and SDK calls are serialized on the main thread. */
class AppOpenAdManager(
    private val config: ConfigManager,
    private val gate: AdGate,
    private val initializer: AdMobInitializer,
    private val prefs: SyncPrefs,
    private val anotherFullscreenAdShowing: () -> Boolean,
) {
    private val main = Handler(Looper.getMainLooper())

    var appOpenAd: AppOpenAd? = null
        private set
    private var isLoading = false
    private var loadTimeElapsed = 0L

    @Volatile
    var isShowing = false
        private set

    @Volatile
    private var startupInProgress = false

    fun setStartupInProgress(inProgress: Boolean) {
        onMain { startupInProgress = inProgress }
    }

    /** Request a cache fill when app startup or backgrounding leaves time for the SDK to load. */
    fun loadAd() = onMain {
        val model = config.appOpen()
        Log.d(TAG, "load requested enabled=${model.enabled}, shouldLoad=${model.shouldLoad}, canLoad=${gate.canLoad()}, loading=$isLoading, cached=${appOpenAd != null}")
        if (!model.enabled || !model.shouldLoad || !gate.canLoad() || isLoading || hasFreshAd()) return@onMain
        if (model.adId.isEmpty()) return@onMain

        isLoading = true
        val accepted = initializer.whenReady(onFail = {
            isLoading = false
            Log.d(TAG, "SDK initialization failed; a later lifecycle event can retry")
        }) {
            if (!gate.canLoad()) {
                isLoading = false
                return@whenReady
            }
            val startedAt = SystemClock.elapsedRealtime()
            AdEvents.request(PLACEMENT, FORMAT)
            try {
                AppOpenAd.load(AdRequest.Builder(model.adId).build(), object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) = onMain {
                        isLoading = false
                        // There is only one in-flight request, but don't replace a valid cache if a future caller adds one.
                        if (hasFreshAd()) return@onMain
                        appOpenAd = ad
                        loadTimeElapsed = SystemClock.elapsedRealtime()
                        Log.d(TAG, "ad loaded in ${loadTimeElapsed - startedAt}ms")
                        AdEvents.loaded(PLACEMENT, FORMAT, loadTimeElapsed - startedAt)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) = onMain {
                        isLoading = false
                        Log.d(TAG, "load failed code=${error.code}, message=${error.message}")
                        AdEvents.failed(PLACEMENT, FORMAT, error.code.toString())
                    }
                })
            } catch (error: Throwable) {
                isLoading = false
                Log.e(TAG, "load threw", error)
            }
        }
        if (!accepted) isLoading = false
    }

    /** Called by ProcessLifecycleOwner after a real process background-to-foreground transition. */
    fun onAppForegrounded(activity: Activity, stillAllowed: () -> Boolean) = onMain {
        if (startupInProgress) {
            Log.d(TAG, "foreground show skipped while startup flow is active")
            loadAd()
            return@onMain
        }
        showAdIfAvailable(activity, stillAllowed)
    }

    /** Background time is the best opportunity to refill the single-use cache before the next return. */
    fun onAppBackgrounded() = onMain { loadAd() }

    /** Cold-start handoff: StartupFlow waits only on its loading screen, then makes this immediate attempt. */
    fun showAdIfAvailable(activity: Activity, stillAllowed: () -> Boolean = { true }) = onMain {
        if (!prefs.onboardingDone) {
            Log.d(TAG, "show skipped: onboarding not completed yet")
            return@onMain
        }
        val model = config.appOpen()
        if (!model.enabled || !gate.canLoad() || isShowing || anotherFullscreenAdShowing() ||
            activity.isFinishing || activity.isDestroyed || !stillAllowed()
        ) {
            Log.d(TAG, "show skipped by eligibility")
            loadAd()
            return@onMain
        }

        val ad = appOpenAd
        if (ad == null) {
            Log.d(TAG, "no cached ad at foreground; loading for the next opportunity")
            loadAd()
            return@onMain
        }
        if (!hasFreshAd()) {
            Log.d(TAG, "cached ad expired")
            appOpenAd = null
            loadAd()
            return@onMain
        }
        if (isWithinCooldown()) {
            Log.d(TAG, "show skipped by persisted frequency cap")
            return@onMain
        }

        // Consume before show to prevent a second lifecycle callback racing this one.
        appOpenAd = null
        isShowing = true
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                val now = System.currentTimeMillis()
                prefs.appOpenLastShownAtMillis = now
                Log.d(TAG, "fullscreen content shown")
                AdEvents.shown(PLACEMENT, FORMAT)
            }

            override fun onAdDismissedFullScreenContent() = onMain {
                Log.d(TAG, "fullscreen content dismissed")
                AdEvents.dismissed(PLACEMENT, FORMAT)
                isShowing = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) = onMain {
                Log.w(TAG, "show failed: ${error.message}")
                AdEvents.failed(PLACEMENT, FORMAT, "show_failed")
                isShowing = false
                loadAd()
            }

            override fun onAdClicked() {
                Log.d(TAG, "ad clicked")
                AdEvents.clicked(PLACEMENT, FORMAT)
            }
        }
        try {
            Log.d(TAG, "showing cached ad")
            ad.show(activity)
        } catch (error: Throwable) {
            Log.e(TAG, "show threw", error)
            isShowing = false
            loadAd()
        }
    }

    private fun hasFreshAd(): Boolean =
        appOpenAd != null && SystemClock.elapsedRealtime() - loadTimeElapsed <= AD_VALID_FOR_MS

    private fun isWithinCooldown(): Boolean {
        val lastShown = prefs.appOpenLastShownAtMillis
        if (lastShown <= 0L) return false
        val sinceShown = System.currentTimeMillis() - lastShown
        // A backwards wall-clock adjustment must not defeat the frequency cap.
        return sinceShown < COOLDOWN_MS
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val TAG = "AppOpenAdManager"
        const val PLACEMENT = "app_open"
        const val FORMAT = "app_open"
        const val COOLDOWN_MS = 60_000L
        const val AD_VALID_FOR_MS = 4 * 60 * 60_000L
    }
}
