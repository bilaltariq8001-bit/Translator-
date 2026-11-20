package com.translator.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val appLanguage: String = "en",
    val onboardingDone: Boolean = false,
    val cloudConsent: Boolean = true,
    val crashReports: Boolean = false,
    val sourceLang: String = "en",
    val targetLang: String = "es",
    val tone: String = "General",
    val speakAloud: Boolean = true,
    val haptics: Boolean = true,
    /** "standard" or "large": multiplies Android's font size app-wide. */
    val textSize: String = "standard",
    /** "standard" or "slower": text-to-speech rate. */
    val readSpeed: String = "standard",
    /** Translate with the AI model (Firebase AI Logic) instead of the standard service. Off until the user opts in. */
    val aiTranslation: Boolean = false,
    /** Let the AI model translate when the free translator is refusing requests. Needs the cloud setting too. */
    val aiFallback: Boolean = true,
) {
    val fontScaleBoost: Float get() = if (textSize == "large") 1.15f else 1f
    val speechRate: Float get() = if (readSpeed == "slower") 0.75f else 1f
}

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setAppLanguage(code: String)
    suspend fun completeOnboarding(cloudConsent: Boolean, crashReports: Boolean)
    suspend fun setPair(source: String, target: String)
    suspend fun setTone(tone: String)
    suspend fun setCloudConsent(value: Boolean)
    suspend fun setSpeakAloud(value: Boolean)
    suspend fun setHaptics(value: Boolean)
    suspend fun setTextSize(value: String)
    suspend fun setReadSpeed(value: String)
    suspend fun setAiTranslation(value: Boolean)
    suspend fun setAiFallback(value: Boolean)

    /** Erases every stored setting; the app returns to first-run onboarding. */
    suspend fun clearAll()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val store = context.applicationContext.dataStore
    private val syncPrefs = com.translator.app.core.util.SyncPrefs(context)

    private object K {
        val appLanguage = stringPreferencesKey("app_language")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val cloudConsent = booleanPreferencesKey("cloud_consent")
        val crashReports = booleanPreferencesKey("crash_reports")
        val source = stringPreferencesKey("source_lang")
        val target = stringPreferencesKey("target_lang")
        val tone = stringPreferencesKey("tone")
        val speakAloud = booleanPreferencesKey("speak_aloud")
        val haptics = booleanPreferencesKey("haptics")
        val textSize = stringPreferencesKey("text_size")
        val readSpeed = stringPreferencesKey("read_speed")
        val aiTranslation = booleanPreferencesKey("ai_translation")
        val aiFallback = booleanPreferencesKey("ai_fallback")
    }

    override val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        val isDone = p[K.onboardingDone] ?: d.onboardingDone
        if (isDone && !syncPrefs.onboardingDone) {
            syncPrefs.onboardingDone = true
        }
        AppSettings(
            // Until the person picks one, the app speaks the phone's language when it has it (English otherwise).
            // The app language itself lives in AppLocale (saved synchronously, applied before the first screen). A choice saved
            // here by an older version of the app is taken over once.
            appLanguage = run {
                val older = p[K.appLanguage]
                if (older != null && !com.translator.app.core.i18n.AppLocale.hasSavedChoice()) com.translator.app.core.i18n.AppLocale.set(older)
                com.translator.app.core.i18n.AppLocale.current
            },
            onboardingDone = isDone,
            // The cloud features (AI, voice and camera reading) are always available now; there is no switch for them any more,
            // and the first-run privacy screen tells the person what is sent before they agree. A value saved by an older
            // version is ignored.
            cloudConsent = true,
            crashReports = p[K.crashReports] ?: d.crashReports,
            sourceLang = p[K.source] ?: d.sourceLang,
            targetLang = p[K.target] ?: d.targetLang,
            tone = p[K.tone] ?: d.tone,
            speakAloud = p[K.speakAloud] ?: d.speakAloud,
            haptics = p[K.haptics] ?: d.haptics,
            textSize = p[K.textSize] ?: d.textSize,
            readSpeed = p[K.readSpeed] ?: d.readSpeed,
            aiTranslation = p[K.aiTranslation] ?: d.aiTranslation,
            aiFallback = p[K.aiFallback] ?: d.aiFallback,
        )
    }

    override suspend fun setAppLanguage(code: String) {
        com.translator.app.core.i18n.AppLocale.set(code)
        store.edit { it[K.appLanguage] = com.translator.app.core.i18n.AppLocale.current }
    }

    override suspend fun completeOnboarding(cloudConsent: Boolean, crashReports: Boolean) {
        syncPrefs.onboardingDone = true
        store.edit {
            it[K.cloudConsent] = cloudConsent
            it[K.crashReports] = crashReports
            it[K.onboardingDone] = true
        }
    }

    override suspend fun setPair(source: String, target: String) {
        store.edit { it[K.source] = source; it[K.target] = target }
    }

    override suspend fun setTone(tone: String) { store.edit { it[K.tone] = tone } }
    override suspend fun setCloudConsent(value: Boolean) { store.edit { it[K.cloudConsent] = value } }
    override suspend fun setSpeakAloud(value: Boolean) { store.edit { it[K.speakAloud] = value } }
    override suspend fun setHaptics(value: Boolean) { store.edit { it[K.haptics] = value } }
    override suspend fun setTextSize(value: String) { store.edit { it[K.textSize] = value } }
    override suspend fun setReadSpeed(value: String) { store.edit { it[K.readSpeed] = value } }
    override suspend fun setAiTranslation(value: Boolean) { store.edit { it[K.aiTranslation] = value } }
    override suspend fun setAiFallback(value: Boolean) { store.edit { it[K.aiFallback] = value } }
    override suspend fun clearAll() {
        syncPrefs.onboardingDone = false
        store.edit { it.clear() }
    }
}
