package com.translator.app.core.config

/** Ad ids a build starts with, and the Google test ids that debug builds always use. */
data class AdIdDefaults(
    val banner: String,
    val interstitial: String,
    val appOpen: String,
    val native: String,
    val testBanner: String,
    val testInterstitial: String,
    val testAppOpen: String,
    val testNative: String,
    val debug: Boolean,
)

/**
 * Typed readers over any key-value source (Remote Config in the app, a map in tests). A value the source does not have, or
 * cannot be read as the wanted type, falls back to [defaults] (the same table as res/xml/remote_config_defaults.xml).
 */
class ConfigReader(
    private val lookup: (String) -> String?,
    private val defaults: Map<String, String>,
    private val ids: AdIdDefaults,
) {
    fun string(key: String): String = (lookup(key) ?: defaults[key] ?: "").trim()

    fun bool(key: String): Boolean = string(key).lowercase().let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> defaults[key]?.trim()?.lowercase() == "true"
        }
    }

    fun long(key: String): Long = string(key).toLongOrNull() ?: defaults[key]?.trim()?.toLongOrNull() ?: 0L

    fun capping(): CappingConfig = CappingConfig(
        cacheSize = long(RcKeys.CACHE_SIZE).coerceIn(1L, 5L).toInt(),
        timeCapping = bool(RcKeys.TIME_CAPPING),
        cooldownSeconds = long(RcKeys.COOLDOWN_SECONDS).coerceAtLeast(0L),
        clickCap = long(RcKeys.CLICK_CAP).coerceAtLeast(1L),
    )

    fun interstitial(placement: String): InterstitialAdModel = InterstitialAdModel(
        placement = placement,
        adId = resolveId(string("${placement}_inter_id"), ids.interstitial, ids.testInterstitial),
        enabled = bool("${placement}_inter_enabled"),
        shouldLoad = bool("${placement}_inter_should_load"),
        counterEnabled = bool("${placement}_inter_counter_enabled"),
    )

    /** How many successful translations between two translate_count interstitials (at least 1). */
    fun translateEvery(): Int = long(RcKeys.TRANSLATE_EVERY).coerceIn(1L, 1_000L).toInt()

    /** How many uses of a feature come free before the next one shows the [placement] interstitial (at least 1). */
    fun useEvery(placement: String): Int = long("${placement}_inter_every").coerceIn(1L, 1_000L).toInt()

    fun banner(slot: String): InScreenAdModel = InScreenAdModel(
        slot = slot,
        adId = resolveId(string("${slot}_banner_ad_id"), ids.banner, ids.testBanner),
        nativeAdId = resolveId(string("${slot}_native_ad_id"), ids.native, ids.testNative),
        collapsing = bool("${slot}_banner_is_collapsing"),
        enabled = bool("${slot}_in_screen_ad_enabled"),
        position = if (string("${slot}_ad_position").equals("TOP", ignoreCase = true)) AdPosition.TOP else AdPosition.BOTTOM,
        // The console key was documented but never read, so a slot set to NATIVE silently rendered a banner.
        type = if (string("${slot}_in_screen_ad_type").equals("NATIVE", ignoreCase = true)) InScreenAdType.NATIVE else InScreenAdType.BANNER,
    )

    fun appOpen(): AppOpenAdModel = AppOpenAdModel(
        adId = resolveId(string("app_open_ad_id"), ids.appOpen, ids.testAppOpen),
        enabled = bool("app_open_enabled"),
        shouldLoad = bool("app_open_should_load"),
    )

    fun nativeAd(slot: String): NativeAdModel = NativeAdModel(
        slot = slot,
        adId = resolveId(string("${slot}_native_ad_id"), ids.native, ids.testNative),
        enabled = bool("${slot}_native_enabled"),
    )

    /** Debug builds ignore Remote Config ids. Otherwise a remote id wins; empty means the build default. */
    private fun resolveId(remote: String, buildDefault: String, test: String): String =
        if (ids.debug) test else remote.ifEmpty { buildDefault }.ifEmpty { test }
}
