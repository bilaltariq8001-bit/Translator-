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

    private fun showPendingForegroundAdWhenResumed() {
        if (!foregroundReturnPending) return
        val activity = currentMainActivity.get() ?: return
        if (activity.isFinishing || activity.isDestroyed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        // Consume this process transition before attempting an ad. A missing/blocked ad is not retried late over app content.
        foregroundReturnPending = false
        container.ads.appOpen.onAppForegrounded(activity) { activity.canShowAppOpenAd() }
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

    private companion object { const val TAG = "TranslatorAppLifecycle" }
}
