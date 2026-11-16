package com.translator.app.core.analytics

/** Every event name the app may send. Anything else is dropped by [Analytics.log]. */
object Ev {
    const val SCREEN_ENTER = "screen_enter"
    const val STARTUP_READY = "startup_ready"
    const val ONBOARDING_STARTED = "onboarding_started"
    const val ONBOARDING_STEP = "onboarding_step"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val CONSENT_RESULT = "consent_result"
    const val TEXT_TRANSLATE_STARTED = "text_translate_started"
    const val TEXT_TRANSLATE_SUCCESS = "text_translate_success"
    const val TEXT_TRANSLATE_FAILED = "text_translate_failed"
    const val VOICE_SESSION_STARTED = "voice_session_started"
    const val VOICE_SESSION_SUCCESS = "voice_session_success"
    const val VOICE_SESSION_FAILED = "voice_session_failed"
    const val VOICE_SESSION_CANCELLED = "voice_session_cancelled"
    const val PHOTO_TRANSLATE_STARTED = "photo_translate_started"
    const val PHOTO_TRANSLATE_SUCCESS = "photo_translate_success"
    const val PHOTO_TRANSLATE_FAILED = "photo_translate_failed"
    const val AI_TOOL_STARTED = "ai_tool_started"
    const val AI_TOOL_SUCCESS = "ai_tool_success"
    const val AI_TOOL_FAILED = "ai_tool_failed"
    const val RESULT_COPIED = "result_copied"
    const val RESULT_FAVORITED = "result_favorited"
    const val TTS_PLAYED = "tts_played"
    const val OFFLINE_PACK_DOWNLOAD_STARTED = "offline_pack_download_started"
    const val OFFLINE_PACK_DOWNLOAD_SUCCESS = "offline_pack_download_success"
    const val OFFLINE_PACK_DOWNLOAD_FAILED = "offline_pack_download_failed"
    const val ADS_REQUEST = "ads_request"
    const val ADS_LOADED = "ads_loaded"
    const val ADS_FAILED = "ads_failed"
    const val ADS_SHOWN = "ads_shown"
    const val ADS_CLICKED = "ads_clicked"
    const val ADS_DISMISSED = "ads_dismissed"

    val ALL: Set<String> = setOf(
        SCREEN_ENTER, STARTUP_READY, ONBOARDING_STARTED, ONBOARDING_STEP, ONBOARDING_COMPLETED, CONSENT_RESULT,
        TEXT_TRANSLATE_STARTED, TEXT_TRANSLATE_SUCCESS, TEXT_TRANSLATE_FAILED,
        VOICE_SESSION_STARTED, VOICE_SESSION_SUCCESS, VOICE_SESSION_FAILED, VOICE_SESSION_CANCELLED,
        PHOTO_TRANSLATE_STARTED, PHOTO_TRANSLATE_SUCCESS, PHOTO_TRANSLATE_FAILED,
        AI_TOOL_STARTED, AI_TOOL_SUCCESS, AI_TOOL_FAILED, RESULT_COPIED, RESULT_FAVORITED, TTS_PLAYED,
        OFFLINE_PACK_DOWNLOAD_STARTED, OFFLINE_PACK_DOWNLOAD_SUCCESS, OFFLINE_PACK_DOWNLOAD_FAILED,
        ADS_REQUEST, ADS_LOADED, ADS_FAILED, ADS_SHOWN, ADS_CLICKED, ADS_DISMISSED,
    )
}

/** The user properties the app sets. */
object Prop {
    const val USER_COHORT = "user_cohort"
    const val APP_LANGUAGE = "app_language"
    const val ENGAGEMENT_BUCKET = "engagement_bucket"
}
