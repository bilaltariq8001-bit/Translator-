package com.translator.app.core.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Small values that must be readable at once, from any thread, without suspending (DataStore cannot do that): the consent
 * result, the analytics choice and the launch count. Nothing here is personal data.
 */
class SyncPrefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    /** UMP's answer from the last check. False until the first successful check, so no ad is requested before consent is known. */
    var canRequestAds: Boolean
        get() = sp.getBoolean("can_request_ads", false)
        set(v) { sp.edit().putBoolean("can_request_ads", v).apply() }

    /** True when UMP says consent rules apply to this person (for example the EEA or UK). */
    var consentRegulated: Boolean
        get() = sp.getBoolean("consent_regulated", false)
        set(v) { sp.edit().putBoolean("consent_regulated", v).apply() }

    /** True when the privacy options entry (a UMP form) must be offered in Settings. */
    var privacyOptionsRequired: Boolean
        get() = sp.getBoolean("privacy_options_required", false)
        set(v) { sp.edit().putBoolean("privacy_options_required", v).apply() }

    /** IABTCF_PurposeConsents as last written by UMP ('0'/'1' per purpose); empty when unknown. */
    var tcfPurposes: String
        get() = sp.getString("tcf_purposes", "") ?: ""
        set(v) { sp.edit().putString("tcf_purposes", v).apply() }

    /** Synchronous check whether onboarding has been completed. False on fresh installs, true after finishing onboarding. */
    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done_sync", false)
        set(v) { sp.edit().putBoolean("onboarding_done_sync", v).apply() }

    /** Wall-clock time of the last App Open ad actually shown; survives process death for frequency capping. */
    var appOpenLastShownAtMillis: Long
        get() = sp.getLong("app_open_last_shown_at", 0L)
        set(v) { sp.edit().putLong("app_open_last_shown_at", v).apply() }

    /** The person's own analytics choice: -1 not asked, 0 off, 1 on. */
    var analyticsChoice: Int
        get() = sp.getInt("analytics_choice", -1)
        set(v) { sp.edit().putInt("analytics_choice", v).apply() }

    /** Counts app launches, for the coarse engagement bucket. */
    fun nextLaunchNumber(): Int {
        val n = sp.getInt("launches", 0) + 1
        sp.edit().putInt("launches", n).apply()
        return n
    }

    fun clear() { sp.edit().clear().apply() }
}
