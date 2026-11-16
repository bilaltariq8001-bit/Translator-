package com.translator.app.core.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.translator.app.AppContainer
import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.analytics.Prop
import com.translator.app.core.consent.applyAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * What happens between the intro and the app, in this order: consent check (UMP) and Remote Config fetch run during the intro;
 * when the intro is ready to leave, the consent form is shown if required, ads start on a background thread, and, when allowed,
 * a splash interstitial is awaited for at most 3 seconds. Every path returns, so the app always continues.
 */
class StartupFlow(private val container: AppContainer, private val scope: CoroutineScope) {
    private val ads get() = container.ads
    private var consentCheck: Deferred<Boolean>? = null
    private var firstLaunchCheck: Deferred<Boolean>? = null
    private val startedAt = android.os.SystemClock.elapsedRealtime()

    /** True once [finish] has completed. */
    @Volatile var startupFinished = false

    /** At the first drawn frame (main thread): use the saved consent answer, then ask for the new one in the background. */
    fun begin(activity: Activity) {
        ads.appOpen.setStartupInProgress(true)
        ads.consent.loadCached()
        consentCheck = scope.async { ads.consent.requestInfoUpdate(activity) }
        scope.launch { ads.refreshConfig() }
        // Read the settings now, in the background, so the hold at the end of the intro does not wait for them.
        firstLaunchCheck = scope.async { !(withTimeoutOrNull(2_000) { container.settings.settings.first() }?.onboardingDone ?: false) }
    }

    /** Sets the user properties (no-op while analytics is off). Called after consent and whenever the choice changes. */
    suspend fun setUserProperties() {
        val settings = withTimeoutOrNull(2_000) { container.settings.settings.first() } ?: return
        Analytics.setUserProperty(Prop.USER_COHORT, if (settings.onboardingDone) "returning" else "new")
        Analytics.setUserProperty(Prop.APP_LANGUAGE, settings.appLanguage)
        Analytics.setUserProperty(Prop.ENGAGEMENT_BUCKET, Analytics.engagementBucket(container.launchNumber))
    }

    /**
     * Runs when the intro has played. The whole hold is bounded by [BUDGET_MS] (consent wait, settings read and the ad's load
     * wait together); the consent form and an ad the person is watching do not count against it. [updateActive] is true while
     * the Play update flow is showing.
     */
    suspend fun finish(activity: Activity, updateActive: () -> Boolean, allowStartupAds: Boolean = true) {
        val started = android.os.SystemClock.elapsedRealtime()
        var excluded = 0L
        fun remaining() = BUDGET_MS - (android.os.SystemClock.elapsedRealtime() - started - excluded)
        try {
            val checked = withTimeoutOrNull(minOf(CHECK_WAIT_MS, remaining()).coerceAtLeast(1)) { consentCheck?.await() } ?: false
            var formShown = false
            if (checked && !activity.isFinishing && !activity.isDestroyed) {
                val formStart = android.os.SystemClock.elapsedRealtime()
                formShown = try { ads.consent.showFormIfRequired(activity) } catch (_: Throwable) { false }
                excluded += android.os.SystemClock.elapsedRealtime() - formStart
            }
            applyAnalytics(ads.prefs)
            // User properties are not on the critical path.
            scope.launch { setUserProperties() }
            Analytics.log(Ev.CONSENT_RESULT) {
                flag("regulated", ads.prefs.consentRegulated)
                flag("can_request_ads", ads.prefs.canRequestAds)
                flag("form_shown", formShown)
                flag("checked", checked)
            }
            // Ads start only now, after consent, and never on the main thread.
            if (allowStartupAds && ads.gate.canLoad()) {
                // Initialize the SDK and load the app open ad.  We then wait a bounded amount of time
                // for the ad to arrive so we can show it immediately at startup — if it is not ready
                // in time the app opens normally and the cache is kept for a later genuine foreground transition.
                val adReadyDeferred = scope.async {
                    suspendCancellableCoroutine<Boolean> { cont ->
                        val loadStarted = android.os.SystemClock.elapsedRealtime()
                        val handler = Handler(Looper.getMainLooper())
                        ads.initializer.whenReady(onFail = { if (cont.isActive) cont.resume(false) }) {
                            ads.interstitials.warm()
                            ads.appOpen.loadAd()
                            // Poll every 200 ms until the ad is loaded or we run out of budget.
                            val poll = object : Runnable {
                                override fun run() {
                                    when {
                                        !cont.isActive -> Unit
                                        ads.appOpen.appOpenAd != null -> cont.resume(true)
                                        android.os.SystemClock.elapsedRealtime() - loadStarted >= AD_LOAD_WAIT_MS ->
                                            cont.resume(false)
                                        else -> handler.postDelayed(this, 200)
                                    }
                                }
                            }
                            handler.postDelayed(poll, 200)
                        }
                    }
                }

                val firstLaunch = withTimeoutOrNull(remaining().coerceIn(1, 500)) { firstLaunchCheck?.await() } ?: true
                // Wait for the ad (or for time to run out) before deciding what to show.
                withTimeoutOrNull(remaining().coerceAtLeast(1)) { adReadyDeferred.await() }

                if (!firstLaunch) {
                    // Once per process: a recreated Activity must not show the startup interstitial again.
                    if (!splashAttempted && remaining() > 0 && canShowNow(activity, updateActive)) {
                        splashAttempted = true
                        val shown = ads.interstitials.showSplash(activity, minOf(3_000L, remaining()), stillAllowed = { canShowNow(activity, updateActive) })
                        if (!shown) {
                            ads.appOpen.showAdIfAvailable(activity) { canShowNow(activity, updateActive) }
                        }
                    } else {
                        ads.appOpen.showAdIfAvailable(activity) { canShowNow(activity, updateActive) }
                    }
                } else {
                    // Google's App Open guidance recommends waiting until after the first app start.
                    Log.d(TAG, "App Open ad skipped on first app start")
                }
            }
        } catch (_: Throwable) {
            // Whatever went wrong, the app continues.
        } finally {
            ads.appOpen.setStartupInProgress(false)
            startupFinished = true
            Analytics.log(Ev.STARTUP_READY) { duration("t", android.os.SystemClock.elapsedRealtime() - startedAt) }
        }
    }

    private fun canShowNow(activity: Activity, updateActive: () -> Boolean) =
        !updateActive() && !ads.backAds.busy.isBusy && !activity.isFinishing && !activity.isDestroyed

    private companion object {
        const val TAG = "StartupFlow"
        const val CHECK_WAIT_MS = 2_500L
        const val BUDGET_MS = 4_000L
        /** Max milliseconds to wait for the app open ad to load before proceeding. */
        const val AD_LOAD_WAIT_MS = 3_000L

        /** Process-wide: true once the splash ad was tried in this process. */
        @Volatile var splashAttempted = false
    }
}
