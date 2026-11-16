package com.translator.app.core.ads

import android.os.Handler
import android.os.Looper
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.translator.app.BuildConfig
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts the ads SDK once, on a background thread, and only when [AdGate] allows requests. It is never started by the
 * container or the first frame: the startup flow asks for it after consent is known. Callers pass what they want to do when
 * it is ready (main thread) and what to do if it cannot start, so nobody waits forever and later calls can retry.
 */
class AdMobInitializer(context: Context, private val gate: AdGate) {
    private class Waiter(val onReady: () -> Unit, val onFail: () -> Unit)

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    @Volatile private var ready = false
    private val waiting = CopyOnWriteArrayList<Waiter>()

    val isReady: Boolean get() = ready

    /** Runs [then] on the main thread once the SDK is ready, or [onFail] if it could not start. False (nothing runs) when ads may not be requested. */
    fun whenReady(onFail: () -> Unit = {}, then: () -> Unit): Boolean {
        if (!gate.canRequestAds()) return false
        if (ready) { main.post(then); return true }
        val waiter = Waiter(then, onFail)
        waiting.add(waiter)
        // Each caller gets its own deadline. A slow phone or network fails only this caller; the SDK start keeps going and
        // serves later callers, instead of a second initialize being started on top of the first one.
        main.postDelayed({ if (waiting.remove(waiter)) { AdDiagnostics.record("init", "timeout"); waiter.onFail() } }, INIT_TIMEOUT_MS)
        if (started.compareAndSet(false, true)) {
            Thread({
                try {
                    MobileAds.initialize(
                        app,
                        InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID)
                            .setNativeValidatorDisabled()
                            .build(),
                    ) {
                        ready = true
                        AdDiagnostics.sdkReady = true
                        main.post {
                            val run = waiting.toList()
                            waiting.removeAll(run.toSet())
                            run.forEach { it.onReady() }
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e(AdDiagnostics.TAG, "MobileAds.initialize failed", t)
                    AdDiagnostics.record("init", t.javaClass.simpleName)
                    started.set(false)
                    failAll()
                }
            }, "ads-init").start()
        }
        return true
    }

    /** Tells every waiter it will not get an ad now, and lets a later call try to start the SDK again. */
    private fun failAll() {
        main.post {
            val run = waiting.toList()
            waiting.removeAll(run.toSet())
            run.forEach { it.onFail() }
        }
    }

    private companion object { const val INIT_TIMEOUT_MS = 15_000L }
}
