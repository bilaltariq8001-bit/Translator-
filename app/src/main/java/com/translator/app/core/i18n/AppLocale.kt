package com.translator.app.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import com.translator.app.R

/**
 * The language of the app's own menus and buttons, kept apart from the languages being translated.
 *
 * Every string comes from the normal Android resources (res/values-xx/strings.xml); this object only decides which
 * language's resources are read, so the choice applies at once on every screen and survives a restart (the code is saved
 * synchronously and applied in Application.onCreate, before the first screen). A language without a translation falls back to
 * English through the usual Android resource rules. Reading a string inside a composable registers a read of [version],
 * so the screen redraws the moment the language changes.
 */
object AppLocale {
    /** The interface languages that have a complete set of translated strings, in the order they are offered. */
    var supported: List<String> = listOf(
        "en", "es", "ur", "ja", "ar", "zh", "fr", "de", "hi", "ko", "tr", "af", "sq", "am", "hy", "az", "eu", "be", "bn", "bs", "bg", "my", "ca", "ceb", "ny", "zh-TW", "co", "hr", "cs", "da", "nl", "eo", "et", "tl", "fi", "fy", "gl", "ka", "el", "gu", "ht", "ha", "haw", "he", "hmn", "hu", "is", "ig", "id", "ga", "it", "jv", "kn", "kk", "km", "rw", "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mn", "ne", "no", "or", "ps", "fa", "pl", "pt", "pa", "ro", "ru", "sm", "gd", "sr", "st", "sn", "sd", "si", "sk", "sl", "so", "su", "sw", "sv", "tg", "ta", "tt", "te", "th", "tk", "uk", "ug", "uz", "vi", "cy", "xh", "yi", "yo", "zu"
    )
        private set

    private var base: Context? = null
    private var resources: Resources? = null
    private var appliedLocale: Locale = Locale.ENGLISH
    /** The app language now in use. Compose state, so every screen that reads it redraws the moment it changes. */
    private var currentCode by mutableStateOf("")
    private var version by mutableIntStateOf(0)
    @Volatile private var hadSavedChoice = false

    /** The code of the app language now in use ("en", "ur", "zh-TW"...). This is the one source of truth for it. */
    val current: String get() = currentCode.ifEmpty { "en" }

    /** The locale of the app language, for dates and weekday names. */
    val locale: Locale get() { version; return appliedLocale }

    /** Returns true if the current active language is a Right-To-Left (RTL) script. */
    val isRtl: Boolean
        get() {
            val lang = current.substringBefore('-').lowercase()
            return lang in listOf("ar", "fa", "ur", "he", "iw", "yi", "ji", "ps", "sd", "ug")
        }

    /**
     * Called once from Application.onCreate, before any screen, the keyboard or a notification reads a string. The app language
     * is decided right here, synchronously, so the very first frame is already in it:
     *  1. Android 13+: the language set for this app in the phone's own Settings (Apps > AI Translator > Language), when the
     *     person changed it there; the app always keeps that setting equal to its own choice, so a difference means they did.
     *  2. The language last chosen in the app.
     *  3. Otherwise the phone's language, when the app has it (English when it does not).
     */
    fun init(context: Context) {
        val app = context.applicationContext
        base = app
        // The list of languages that have translated strings comes from res/values/app_languages.xml.
        supported = try { app.resources.getStringArray(R.array.app_language_codes).toList().ifEmpty { supported } } catch (_: Throwable) { supported }
        val saved = saved(app)?.let { normalize(it) }
        hadSavedChoice = saved != null
        val system = systemAppLanguage(app)
        val start = when {
            system != null && system != saved -> system
            saved != null -> saved
            else -> deviceLanguage()
        }
        applyCode(app, start)
        persist(app, start)
        applyToSystem(app, start)
    }

    /** True if [code] has translated interface strings. */
    fun isSupported(code: String) = code in supported

    /**
     * True once a language was chosen in the app (or migrated from an older version). Until then [current] is the phone's
     * language, and a choice saved by an older version of the app in the settings store should be adopted.
     */
    fun hasSavedChoice(): Boolean = hadSavedChoice

    // Some Android versions know a few languages only under their old code (Hebrew "iw", Indonesian "in", Yiddish "ji"), so each
    // language is tried under both. A language is only used when its translated strings really load: four sample texts must
    // differ from the English ones. That is how "all of them work" is checked on the phone itself, not assumed.
    private val legacyCodes = mapOf("he" to "iw", "id" to "in", "yi" to "ji")
    private val probeIds = intArrayOf(R.string.skip, R.string.continue_label, R.string.translate, R.string.history)
    private val chosenLocale = java.util.concurrent.ConcurrentHashMap<String, Locale>()
    @Volatile private var englishSample: List<String>? = null

    /**
     * Resources in [locale]. Only the language (and text direction) is overridden: the rest of the configuration (dark mode,
     * screen size, font scale) stays the phone's own and keeps following it.
     */
    private fun resourcesFor(context: Context, locale: Locale): Resources =
        context.createConfigurationContext(localeOnly(locale)).resources

    private fun localeOnly(locale: Locale) = Configuration().apply { setLocale(locale); setLayoutDirection(locale) }

    /** The locale under which [code]'s strings load, or null if they do not load at all. */
    private fun pickLocale(context: Context, code: String): Locale? {
        if (code == "en") return Locale.ENGLISH
        chosenLocale[code]?.let { return it }
        return try {
            val sample = englishSample ?: probeIds.map { resourcesFor(context, Locale.ENGLISH).getString(it) }.also { englishSample = it }
            val candidates = listOfNotNull(Locale.forLanguageTag(code), legacyCodes[code]?.let { Locale(it) })
            candidates.firstOrNull { locale ->
                val res = resourcesFor(context, locale)
                probeIds.indices.any { i -> res.getString(probeIds[i]) != sample[i] }
            }?.also { chosenLocale[code] = it }
        } catch (_: Throwable) { null }
    }

    suspend fun verified(): List<String> = supported

    /**
     * Switches the app language. Takes effect at once on every screen (all text is read through [string], which redraws on
     * [version]), is saved synchronously so it survives the app being closed straight away, and is also given to Android 13+
     * as this app's language, so the phone's per-app language setting, system dialogs and the keyboard agree with it.
     * Safe to call from any thread and as often as wanted: the same language twice does nothing.
     */
    fun set(languageCode: String) {
        val context = base ?: return
        val wanted = normalize(languageCode)
        hadSavedChoice = true
        if (wanted == currentCode && resources != null) { persist(context, wanted); applyToSystem(context, wanted); return }
        applyCode(context, wanted)
        persist(context, wanted)
        applyToSystem(context, wanted)
    }

    /**
     * Android 13+: the person changed this app's language in the phone's own Settings while the app was running. Follow it.
     * Called on every configuration change; when the app itself made the change it finds nothing to do.
     */
    fun syncFromSystem(context: Context) {
        val app = base ?: context.applicationContext
        val system = systemAppLanguage(app) ?: return
        if (system == currentCode) return
        hadSavedChoice = true
        applyCode(app, system)
        persist(app, system)
    }

    private fun applyCode(context: Context, code: String) {
        val locale = pickLocale(context, code) ?: Locale.forLanguageTag(code)
        resources = try { resourcesFor(context, locale) } catch (_: Throwable) { context.resources }
        appliedLocale = locale
        currentCode = code
        version++
    }

    private fun persist(context: Context, code: String) {
        // commit (not apply): written before this returns, so it is there even if the app is closed the next instant.
        try { prefs(context).edit().putString(PREF_KEY, code).commit() } catch (_: Throwable) { }
    }

    /** The app language saved by the last [set], readable synchronously; null before the first choice. */
    fun saved(context: Context): String? = try { prefs(context).getString(PREF_KEY, null) } catch (_: Throwable) { null }

    /**
     * [newBase] with the app language applied, for Activity.attachBaseContext, so the Activity's own resources (system
     * pickers and dialogs) are in the app language too. Android 13+ already does this itself from the per-app language
     * (which [set] keeps equal to the app language), and overriding it there would pin the Activity to the language it
     * started in, so this only acts on older versions.
     */
    fun wrap(newBase: Context): Context {
        if (android.os.Build.VERSION.SDK_INT >= 33) return newBase
        val wanted = (if (currentCode.isNotEmpty()) currentCode else saved(newBase)?.let { normalize(it) }) ?: return newBase
        return try {
            val locale = pickLocale(newBase, wanted) ?: Locale.forLanguageTag(wanted)
            newBase.createConfigurationContext(localeOnly(locale))
        } catch (_: Throwable) { newBase }
    }

    /**
     * The app's own code for a language tag from anywhere (Android, the phone, an older saved value): "iw" is "he",
     * "nb" is "no", "fil" is "tl", Traditional Chinese (zh-Hant, TW, HK, MO) is "zh-TW", other Chinese is "zh", a regional
     * variant is its language ("pt-BR" is "pt"). Anything the app has no translation for is "en".
     */
    fun normalize(tag: String): String {
        val t = tag.trim().replace('_', '-')
        if (t.isEmpty()) return "en"
        if (t in supported) return t
        val locale = Locale.forLanguageTag(t)
        val lang = when (val l = locale.language.lowercase().ifEmpty { t.substringBefore('-').lowercase() }) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            "nb", "nn" -> "no"
            "fil" -> "tl"
            else -> l
        }
        if (lang == "zh") {
            val traditional = locale.script.equals("Hant", true) || locale.country.uppercase() in setOf("TW", "HK", "MO")
            return if (traditional && "zh-TW" in supported) "zh-TW" else "zh"
        }
        return if (lang in supported) lang else "en"
    }

    /**
     * The phone's own language, when the app has it (otherwise English): the app language before the person has chosen one,
     * so someone whose phone is in Urdu or Spanish sees the app in Urdu or Spanish from the first screen.
     */
    fun deviceLanguage(): String {
        val system = try {
            android.content.res.Resources.getSystem().configuration.locales.get(0)
        } catch (_: Throwable) { null } ?: return "en"
        return normalize(system.toLanguageTag())
    }

    /** Android 13+: the language set for this app in the phone's Settings, or null when none is set (or older Android). */
    private fun systemAppLanguage(context: Context): String? {
        if (android.os.Build.VERSION.SDK_INT < 33) return null
        return try {
            val locales = context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales ?: return null
            if (locales.isEmpty) null else normalize(locales.get(0).toLanguageTag())
        } catch (_: Throwable) { null }
    }

    private fun applyToSystem(context: Context, wanted: String) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        try {
            val manager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
            val current = manager.applicationLocales
            if (!current.isEmpty && normalize(current.get(0).toLanguageTag()) == wanted) return
            manager.applicationLocales = android.os.LocaleList.forLanguageTags(wanted)
        } catch (_: Throwable) { }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("app_locale", Context.MODE_PRIVATE)

    private const val PREF_KEY = "app_language"

    fun string(@StringRes id: Int, vararg args: Any): String {
        version // registers a read, so composables that show this string redraw when the language changes
        val res = resources ?: base?.resources ?: return ""
        return try {
            if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
        } catch (e: Exception) {
            // Fallback to base resources or empty string if key is missing in custom config
            try {
                base?.resources?.getString(id, *args) ?: ""
            } catch (ex: Exception) {
                ""
            }
        }
    }
}

/** The string for [id] in the app language. Works in composables, view models and the keyboard alike. */
fun str(@StringRes id: Int, vararg args: Any): String = AppLocale.string(id, *args)
