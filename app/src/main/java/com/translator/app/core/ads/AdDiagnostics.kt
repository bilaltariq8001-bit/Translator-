package com.translator.app.core.ads

import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.translator.app.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Why ads did or did not show on this phone, in plain values: the build, the consent answer, whether the SDK started and the
 * last error per ad format. Every failure is also written to Logcat under the tag "Ads", so `adb logcat -s Ads` on a tester's
 * phone shows the reason directly. The text is added to Settings > About > Diagnostics. Contains no personal data.
 */
object AdDiagnostics {
    const val TAG = "Ads"
    @Volatile var sdkReady = false
    private val lastError = ConcurrentHashMap<String, String>()
    private val loaded = ConcurrentHashMap<String, Int>()

    /** [code] is the SDK's error code (3 = no fill: the ad network had no ad for this request). */
    fun record(format: String, code: String, message: String? = null) {
        lastError[format] = code
        Log.w(TAG, "$format failed: code=$code${message?.let { ", $it" } ?: ""}")
    }

    fun loaded(format: String) { loaded.merge(format, 1, Int::plus) }

    fun report(context: Context): String = buildString {
        val info: ConsentInformation? = try { UserMessagingPlatform.getConsentInformation(context.applicationContext) } catch (_: Throwable) { null }
        append("Ads build: ").append(if (BuildConfig.DEBUG) "debug (Google test ads)" else "release").append('\n')
        append("Ad ids: ").append(if (BuildConfig.ADS_USING_TEST_IDS) "TEST ids (no real ads)" else "real ids").append('\n')
        append("Consent status: ").append(when (info?.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> "not required"
            ConsentInformation.ConsentStatus.REQUIRED -> "required, not given"
            ConsentInformation.ConsentStatus.OBTAINED -> "obtained"
            ConsentInformation.ConsentStatus.UNKNOWN -> "unknown (check did not finish)"
            else -> "unreadable"
        }).append(", can request ads: ").append(info?.canRequestAds()).append('\n')
        append("Ads SDK started: ").append(sdkReady).append('\n')
        append("Loaded this session: ").append(if (loaded.isEmpty()) "none" else loaded.entries.joinToString { "${it.key} ${it.value}" }).append('\n')
        append("Last errors: ").append(if (lastError.isEmpty()) "none" else lastError.entries.joinToString { "${it.key} code ${it.value}" }).append('\n')
    }
}
