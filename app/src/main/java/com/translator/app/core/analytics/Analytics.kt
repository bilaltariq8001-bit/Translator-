package com.translator.app.core.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/** Where events go. The Firebase one is the real sink; tests use a fake. */
interface AnalyticsSink {
    fun log(name: String, params: Map<String, Any>)
    fun setUserProperty(name: String, value: String?)
    fun setCollectionEnabled(enabled: Boolean)
    fun setConsent(signals: com.translator.app.core.consent.ConsentSignals)
}

/** Firebase Analytics. Every call checks that Firebase was set up (google-services.json present) and never throws. */
class FirebaseAnalyticsSink(context: Context) : AnalyticsSink {
    private val app = context.applicationContext
    private val instance: FirebaseAnalytics? get() = if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseAnalytics.getInstance(app) else null

    override fun log(name: String, params: Map<String, Any>) {
        if (FirebaseApp.getApps(app).isEmpty()) return
        try {
            val bundle = android.os.Bundle()
            params.forEach { (k, v) -> if (v is Long) bundle.putLong(k, v) else bundle.putString(k, v.toString()) }
            instance?.logEvent(name, bundle)
        } catch (_: Exception) { }
    }

    override fun setUserProperty(name: String, value: String?) {
        if (FirebaseApp.getApps(app).isEmpty()) return
        try { instance?.setUserProperty(name, value) } catch (_: Exception) { }
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        if (FirebaseApp.getApps(app).isEmpty()) return
        try { instance?.setAnalyticsCollectionEnabled(enabled) } catch (_: Exception) { }
    }

    override fun setConsent(signals: com.translator.app.core.consent.ConsentSignals) {
        if (FirebaseApp.getApps(app).isEmpty()) return
        try {
            fun s(on: Boolean) = if (on) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
            instance?.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to s(signals.analyticsStorage),
                    FirebaseAnalytics.ConsentType.AD_STORAGE to s(signals.adStorage),
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to s(signals.adUserData),
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to s(signals.adPersonalization),
                ),
            )
        } catch (_: Exception) { }
    }
}

/**
 * The one door for analytics. Safe to call from anywhere: it does nothing until [install] was called, until the person has
 * agreed ([setAllowed]), or when Firebase is not set up. Event names must be in [Ev.ALL]; parameters go through [EventBuilder].
 */
object Analytics {
    @Volatile private var sink: AnalyticsSink? = null
    @Volatile private var allowed = false
    @Volatile private var lastScreen = ""

    fun install(sink: AnalyticsSink?) { this.sink = sink; allowed = false; lastScreen = "" }

    /** Turns collection on or off. Off also tells Firebase to stop collecting. */
    fun setAllowed(signals: com.translator.app.core.consent.ConsentSignals) {
        allowed = signals.analyticsStorage
        sink?.setConsent(signals)
        sink?.setCollectionEnabled(signals.analyticsStorage)
    }

    val isAllowed: Boolean get() = allowed

    fun log(name: String, block: EventBuilder.() -> Unit = {}) {
        val s = sink ?: return
        if (!allowed || name !in Ev.ALL) return
        try { s.log(name, EventBuilder().apply(block).build()) } catch (_: Exception) { }
    }

    /** One screen_enter per change of screen. */
    fun screen(name: String) {
        if (name == lastScreen) return
        lastScreen = name
        log(Ev.SCREEN_ENTER) { token("screen", name) }
    }

    fun copied(source: String) = log(Ev.RESULT_COPIED) { token("source", source) }

    fun setUserProperty(name: String, value: String) {
        val s = sink ?: return
        if (!allowed) return
        try { s.setUserProperty(name, value.take(36)) } catch (_: Exception) { }
    }

    /** Coarse engagement from the launch count. */
    fun engagementBucket(launches: Int): String = when {
        launches <= 1 -> "s1"
        launches <= 3 -> "s2_3"
        launches <= 10 -> "s4_10"
        else -> "s11_plus"
    }
}
