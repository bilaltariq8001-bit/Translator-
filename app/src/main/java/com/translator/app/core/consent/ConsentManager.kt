package com.translator.app.core.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.translator.app.core.analytics.Analytics
import com.translator.app.core.util.SyncPrefs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google's User Messaging Platform. The form appears only where the rules require it (UMP decides). The result (canRequestAds,
 * whether consent rules apply, whether a privacy-options entry is needed) is stored in [SyncPrefs] so every ad call can read it
 * at once. The saved result from the last launch is used until the new check answers.
 */
class ConsentManager(context: Context, private val prefs: SyncPrefs) {
    /** Called (main thread) whenever the stored consent answer may have changed. */
    var onChanged: () -> Unit = {}

    private val app = context.applicationContext
    private val info: ConsentInformation get() = UserMessagingPlatform.getConsentInformation(app)

    /** Copies UMP's cached answer into the prefs. Cheap; safe on the main thread at start. */
    fun loadCached() = persist()

    /** Asks UMP for the latest consent state. Returns false if the check failed (the saved answer stays). */
    suspend fun requestInfoUpdate(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
            info.requestConsentInfoUpdate(
                activity, params,
                { persist(); if (cont.isActive) cont.resume(true) },
                { persist(); if (cont.isActive) cont.resume(false) },
            )
        } catch (_: Throwable) { if (cont.isActive) cont.resume(false) }
    }

    /** Shows the consent form if (and only if) UMP says it is required. Returns true when a form was shown. */
    suspend fun showFormIfRequired(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        val wasRequired = info.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
        try {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                persist()
                if (cont.isActive) cont.resume(wasRequired)
            }
        } catch (_: Throwable) { if (cont.isActive) cont.resume(false) }
    }

    /** Opens the privacy options form (Settings entry), then re-applies the result. */
    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit = {}) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> persist(); onDone() }
        } catch (_: Throwable) { onDone() }
    }

    private fun persist() {
        val i = info
        prefs.canRequestAds = i.canRequestAds()
        prefs.consentRegulated = i.consentStatus == ConsentInformation.ConsentStatus.REQUIRED || i.consentStatus == ConsentInformation.ConsentStatus.OBTAINED
        prefs.privacyOptionsRequired = i.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        // UMP writes the per-purpose answers here (the IAB TCF string family); unreadable means denied.
        prefs.tcfPurposes = try { app.getSharedPreferences(app.packageName + "_preferences", Context.MODE_PRIVATE).getString("IABTCF_PurposeConsents", "") ?: "" } catch (_: Throwable) { "" }
        applyAnalytics(prefs)
        onChanged()
    }
}

/** Re-evaluates whether analytics may collect and tells it. Called after any consent or choice change. */
fun applyAnalytics(prefs: SyncPrefs) {
    Analytics.setAllowed(currentSignals(prefs))
}

/** The consent signals that apply right now. */
fun currentSignals(prefs: SyncPrefs): ConsentSignals =
    consentSignals(prefs.consentRegulated, prefs.canRequestAds, prefs.analyticsChoice, prefs.tcfPurposes)
