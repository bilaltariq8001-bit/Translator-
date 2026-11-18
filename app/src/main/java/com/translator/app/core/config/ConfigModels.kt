package com.translator.app.core.config

/** Every Remote Config key the app reads. Defaults for all of them live in res/xml/remote_config_defaults.xml. */
object RcKeys {
    const val CACHE_SIZE = "interstitial_cache_size"
    const val TIME_CAPPING = "is_interstitial_time_capping_enabled"
    const val COOLDOWN_SECONDS = "interstitial_cooldown_seconds"
    const val CLICK_CAP = "interstitial_click_cap"

    /** Interstitial placements: keys are <placement>_inter_{id,enabled,should_load,counter_enabled}. */
    const val SPLASH = "splash"
    const val HISTORY_BACK = "history_back"
    const val OFFLINE_BACK = "offline_back"
    const val VOICE_MODE_BACK = "voice_mode_back"
    const val AI_TOOL_BACK = "ai_tool_back"
    const val PHOTO_OPEN = "photo_open"
    const val AI_TOOL_OPEN = "ai_tool_open"
    const val VOICE_MODE_OPEN = "voice_mode_open"
    /** Shown after every Nth successful text translation on the main screen; N is [TRANSLATE_EVERY]. */
    const val TRANSLATE_COUNT = "translate_count"
    const val TRANSLATE_EVERY = "translate_count_inter_every"
    /** Inside a voice mode or AI tool: after every N uses of that feature, the next use shows an interstitial first. N is <placement>_inter_every. */
    const val VOICE_MODE_USE = "voice_mode_use"
    const val AI_TOOL_USE = "ai_tool_use"
    /** Shown once, to a first-time user, right after onboarding ends and before the app opens. Later starts get App Open only. */
    const val ONBOARDING_DONE = "onboarding_done"
    val BACK_PLACEMENTS = listOf(HISTORY_BACK, OFFLINE_BACK, VOICE_MODE_BACK, AI_TOOL_BACK, PHOTO_OPEN, AI_TOOL_OPEN, VOICE_MODE_OPEN, TRANSLATE_COUNT, VOICE_MODE_USE, AI_TOOL_USE)

    /** Native ad slots: keys are <slot>_native_ad_id and <slot>_native_enabled. */
    const val NATIVE_HISTORY = "history"
    const val NATIVE_FAVORITES = "favorites"
    const val NATIVE_CHAT_HISTORY = "chat_history"
    const val NATIVE_CHAT_FAVORITES = "chat_favorites"
    /** Compact native card at the bottom of the onboarding screens (language, guide, privacy). */
    const val NATIVE_ONBOARDING = "onboarding"

    /** Banner slots: keys are <slot>_banner_ad_id, _banner_is_collapsing, _in_screen_ad_enabled, _ad_position, _in_screen_ad_type. */
    const val SLOT_TEXT = "text"
    const val SLOT_VOICE = "voice"
    const val SLOT_TOOLS = "tools"
    const val SLOT_SETTINGS = "settings"
    const val SLOT_HISTORY = "history"
    const val SLOT_OFFLINE = "offline"

    const val APP_OPEN = "app_open"
}

enum class AdPosition { TOP, BOTTOM }

enum class InScreenAdType { BANNER, NATIVE }

/** One interstitial placement as configured. */
data class InterstitialAdModel(
    val placement: String,
    val adId: String,
    val enabled: Boolean,
    val shouldLoad: Boolean,
    val counterEnabled: Boolean,
)

/** One in-screen ad slot as configured. [adId] is used for a banner, [nativeAdId] when [type] is NATIVE. */
data class InScreenAdModel(
    val slot: String,
    val adId: String,
    val nativeAdId: String = "",
    val collapsing: Boolean,
    val enabled: Boolean,
    val position: AdPosition,
    val type: InScreenAdType,
)

data class AppOpenAdModel(
    val adId: String,
    val enabled: Boolean,
    val shouldLoad: Boolean,
)

data class NativeAdModel(
    val slot: String,
    val adId: String,
    val enabled: Boolean,
)

/** Capping settings shared by every interstitial placement. */
data class CappingConfig(
    val cacheSize: Int,
    val timeCapping: Boolean,
    val cooldownSeconds: Long,
    val clickCap: Long,
)
