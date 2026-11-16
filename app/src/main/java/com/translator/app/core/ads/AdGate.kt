package com.translator.app.core.ads

import com.translator.app.BuildConfig
import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.util.SyncPrefs

/**
 * The single question every ad load and show asks first: is it allowed to request an ad right now? Needs the consent result
 * (UMP canRequestAds) and a connection. Nothing reaches the ads SDK without passing here.
 * In debug builds, ads are allowed automatically for testing convenience.
 */
class AdGate(private val prefs: SyncPrefs, private val isOnline: () -> Boolean) {
    fun canRequestAds(): Boolean = prefs.canRequestAds || BuildConfig.DEBUG
    fun canLoad(): Boolean = (prefs.canRequestAds || BuildConfig.DEBUG) && isOnline()
}

/** Ad events for analytics: only the placement, the format and a coarse result. */
internal object AdEvents {
    fun request(placement: String, format: String) = Analytics.log(Ev.ADS_REQUEST) { token("placement", placement); token("format", format) }
    fun loaded(placement: String, format: String, millis: Long) = Analytics.log(Ev.ADS_LOADED) { token("placement", placement); token("format", format); duration("wait", millis) }
    fun failed(placement: String, format: String, code: String) = Analytics.log(Ev.ADS_FAILED) { token("placement", placement); token("format", format); code("error", code) }
    fun shown(placement: String, format: String) = Analytics.log(Ev.ADS_SHOWN) { token("placement", placement); token("format", format) }
    fun clicked(placement: String, format: String) = Analytics.log(Ev.ADS_CLICKED) { token("placement", placement); token("format", format) }
    fun dismissed(placement: String, format: String) = Analytics.log(Ev.ADS_DISMISSED) { token("placement", placement); token("format", format) }
}
