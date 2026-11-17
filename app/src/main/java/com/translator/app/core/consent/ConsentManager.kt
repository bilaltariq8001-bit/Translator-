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

    /** True once a consent check has answered in this process. Until then [retryIfNeeded] may ask again. */
    @Volatile var checkedThisProcess = false
        private set
    private var lastRetryAt = 0L

    /**
     * When the check at start-up failed or was too slow (a weak connection, a slow phone), ads would otherwise stay off for the
     * whole session even where no consent is needed. This asks again, at most every 30 seconds, and shows the form only where
     * UMP says it is required.
     */
    suspend fun retryIfNeeded(activity: Activity) {
        if (checkedThisProcess) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRetryAt < 30_000L) return
        lastRetryAt = now
        if (requestInfoUpdate(activity) && !activity.isFinishing && !activity.isDestroyed) showFormIfRequired(activity)
    }

    /** Copies UMP's cached answer into the prefs. Cheap; safe on the main thread at start. */
    fun loadCached() = persist()

    /** Asks UMP for the latest consent state. Returns false if the check failed (the saved answer stays). */
    suspend fun requestInfoUpdate(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
            info.requestConsentInfoUpdate(
                activity, params,
                { checkedThisProcess = true; persist(); if (cont.isActive) cont.resume(true) },
                { error -> android.util.Log.w("Ads", "consent check failed: ${error.errorCode} ${error.message}"); persist(); allowOutsideConsentRegions(); if (cont.isActive) cont.resume(false) },
            )
        } catch (_: Throwable) { allowOutsideConsentRegions(); if (cont.isActive) cont.resume(false) }
    }

    /**
     * Some phones (several Vivo, Oppo and Xiaomi models among them) cannot complete the consent check: the request fails or
     * never answers because of the maker's network or WebView setup. Ads then stayed off for good, even in countries where no
     * consent form is ever needed. When the check failed, UMP has never said consent rules apply, and the phone's SIM, network
     * and region are all outside the EEA, UK and Switzerland, ads are allowed (the ads SDK still reads any consent string).
     */
    fun allowOutsideConsentRegions() {
        try {
            if (prefs.canRequestAds || prefs.consentRegulated) return
            val status = try { info.consentStatus } catch (_: Throwable) { ConsentInformation.ConsentStatus.UNKNOWN }
            if (status == ConsentInformation.ConsentStatus.REQUIRED || status == ConsentInformation.ConsentStatus.OBTAINED) return
            if (inConsentRegion()) return
            prefs.canRequestAds = true
            android.util.Log.w("Ads", "consent check failed outside consent regions; ads allowed")
            com.translator.app.core.ads.AdDiagnostics.record("consent", "fallback_allowed")
            applyAnalytics(prefs)
            onChanged()
        } catch (_: Throwable) { }
    }

    private fun inConsentRegion(): Boolean {
        val countries = mutableListOf<String>()
        try {
            val tm = app.getSystemService(android.telephony.TelephonyManager::class.java)
            tm?.simCountryIso?.let(countries::add)
            tm?.networkCountryIso?.let(countries::add)
        } catch (_: Throwable) { }
        countries += java.util.Locale.getDefault().country
        val known = countries.map { it.trim().uppercase(java.util.Locale.US) }.filter { it.length == 2 }
        // Nothing known about where the phone is: stay careful.
        if (known.isEmpty()) return true
        return known.any { it in CONSENT_REGIONS }
    }

    private companion object {
        /** EEA, the UK and Switzerland: where Google requires a consent message before ads. */
        val CONSENT_REGIONS = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO", "GB", "UK", "CH",
        )
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
