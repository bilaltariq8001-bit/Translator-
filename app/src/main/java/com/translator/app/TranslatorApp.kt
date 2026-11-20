package com.translator.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.translator.app.core.ads.ProcessForegroundTransitions
import java.lang.ref.WeakReference

class TranslatorApp : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    val container: AppContainer by lazy { AppContainer(this) }
    private val processTransitions = ProcessForegroundTransitions()
    private val main = Handler(Looper.getMainLooper())
    private var currentMainActivity = WeakReference<MainActivity>(null)
    private var foregroundReturnPending = false
    /** The activity covered in black for a resume App Open ad, until that ad shows or turns out not to. */
    private var coveredForResume = WeakReference<Activity>(null)

    override fun onCreate() {
        super<Application>.onCreate()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        com.translator.app.core.i18n.AppLocale.init(this)
        com.translator.app.core.util.Perf.init(this)
        com.translator.app.core.util.CrashLog.install(this)
        // Build the database, HTTP client and AI clients on a background thread while the first screen is being set up;
        // MainActivity waits on the same lazy value only if it gets there first.
        Thread({ container }, "container-init").start()
    }

    override fun onStart(owner: LifecycleOwner) {
        val isBackgroundReturn = processTransitions.onProcessStarted()
        Log.d(TAG, "process foregrounded; backgroundReturn=$isBackgroundReturn")
        if (isBackgroundReturn) {
            foregroundReturnPending = true
            // The resume App Open ad is about to show: make the screen black before the app draws its first frame, so the
            // app's own screen never flashes between coming back and the ad appearing.
            coverForResumeAd()
            // ProcessLifecycleOwner reports after Activity start; waiting for onResume avoids showing on a half-ready Activity.
            main.post(::showPendingForegroundAdWhenResumed)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        processTransitions.onProcessStopped()
        Log.d(TAG, "process backgrounded")
        // Fill the one-ad cache while another application is in front whenever consent and connectivity permit.
        container.ads.appOpen.onAppBackgrounded()
    }

    override fun onActivityStarted(activity: Activity) {
        (activity as? MainActivity)?.let { currentMainActivity = WeakReference(it) }
    }

    override fun onActivityResumed(activity: Activity) {
        (activity as? MainActivity)?.let {
            currentMainActivity = WeakReference(it)
            showPendingForegroundAdWhenResumed()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (currentMainActivity.get() === activity) currentMainActivity.clear()
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentMainActivity.get() === activity) currentMainActivity.clear()
    }

    private fun coverForResumeAd() {
        val activity = currentMainActivity.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            if (!container.ads.appOpen.willShowOnReturn() || !activity.canShowAppOpenAd()) return
        } catch (_: Throwable) { return }
        if (com.translator.app.core.ads.AdBackdrop.isShowing(activity)) return
        com.translator.app.core.ads.AdBackdrop.show(activity)
        coveredForResume = WeakReference(activity)
        // Safety net: never leave the app covered if the ad does not come up.
        main.postDelayed(::uncoverIfNoResumeAd, COVER_TIMEOUT_MS)
    }

    /** Takes the black cover away again when the resume ad did not show (no ad, not allowed, or it failed). */
    private fun uncoverIfNoResumeAd() {
        val activity = coveredForResume.get() ?: return
        coveredForResume.clear()
        // While the ad is up it owns the backdrop and removes it itself when it closes.
        if (!container.ads.appOpen.isShowing) com.translator.app.core.ads.AdBackdrop.hide(activity)
    }

    private fun showPendingForegroundAdWhenResumed() {
        if (!foregroundReturnPending) return
        val activity = currentMainActivity.get() ?: return
        if (activity.isFinishing || activity.isDestroyed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        // Consume this process transition before attempting an ad. A missing/blocked ad is not retried late over app content.
        foregroundReturnPending = false
        container.ads.appOpen.onAppForegrounded(activity) { activity.canShowAppOpenAd() }
        uncoverIfNoResumeAd()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onTerminate() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        unregisterActivityLifecycleCallbacks(this)
        container.ads.release()
        super<Application>.onTerminate()
    }

    private companion object {
        const val TAG = "TranslatorAppLifecycle"
        const val COVER_TIMEOUT_MS = 1_500L
    }
}
