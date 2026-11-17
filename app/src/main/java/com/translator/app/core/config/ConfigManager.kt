package com.translator.app.core.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.translator.app.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Remote Config for the ads. Works with no Firebase at all: every value then comes from res/xml/remote_config_defaults.xml.
 * [refresh] fetches in the background with a 10 second limit and never throws; values are used from the next read.
 */
class ConfigManager(private val context: Context, ids: AdIdDefaults) {
    private val app = context.applicationContext
    private val defaults: Map<String, String> = readDefaults()
    private val minFetchSeconds = if (ids.debug) 0L else 3_600L

    private val remote: FirebaseRemoteConfig? by lazy {
        if (FirebaseApp.getApps(app).isEmpty()) null
        else try {
            FirebaseRemoteConfig.getInstance().also { rc ->
                rc.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(minFetchSeconds).build())
                rc.setDefaultsAsync(R.xml.remote_config_defaults)
            }
        } catch (_: Exception) { null }
    }

    private val reader = ConfigReader({ key -> try { remote?.getString(key) } catch (_: Exception) { null } }, defaults, ids)

    fun capping(): CappingConfig = reader.capping()
    fun interstitial(placement: String): InterstitialAdModel = reader.interstitial(placement)
    fun banner(slot: String): InScreenAdModel = reader.banner(slot)
    fun appOpen(): AppOpenAdModel = reader.appOpen()
    fun nativeAd(slot: String): NativeAdModel = reader.nativeAd(slot)
    fun translateEvery(): Int = reader.translateEvery()
    fun useEvery(placement: String): Int = reader.useEvery(placement)

    /** Fetches and activates. Returns false when Firebase is not set up, the fetch failed or 10 s ran out. */
    suspend fun refresh(timeoutMs: Long = 10_000L): Boolean {
        val rc = remote ?: return false
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                try {
                    rc.fetchAndActivate()
                        .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(false) }
                } catch (_: Exception) { if (cont.isActive) cont.resume(false) }
            }
        } ?: false
    }

    /** Reads res/xml/remote_config_defaults.xml so values exist without Firebase. */
    private fun readDefaults(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            context.resources.getXml(R.xml.remote_config_defaults).use { p ->
                var key: String? = null
                var tag = ""
                var event = p.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> { tag = p.name; if (tag == "value") map[key ?: ""] = "" }
                        org.xmlpull.v1.XmlPullParser.TEXT -> if (tag == "key") key = p.text.trim() else if (tag == "value" && key != null) map[key] = p.text.trim()
                        org.xmlpull.v1.XmlPullParser.END_TAG -> tag = ""
                    }
                    event = p.next()
                }
            }
        } catch (_: Exception) { }
        return map
    }
}
