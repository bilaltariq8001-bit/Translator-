package com.translator.app.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import com.translator.app.R

/**
 * The language of the app's own menus and buttons, kept apart from the languages being translated.
 *
 * Every string comes from the normal Android resources (res/values-xx/strings.xml); this object only decides which
 * language's resources are read, so the choice in Settings applies at once on every screen and survives a restart (the
 * code itself is saved in the settings store and applied at start-up). A language without a translation falls back to
 * English through the usual Android resource rules. Reading a string inside a composable registers a read of [version],
 * so the screen redraws the moment the language changes.
 */
object AppLocale {
    /** The interface languages that have a complete set of translated strings, in the order they are offered. */
    var supported: List<String> = listOf(
        "en", "es", "fr", "de", "pt", "it", "ru", "uk", "zh", "ja", "ko",
        "ar", "fa", "ur", "he", "hi", "bn", "ta", "te", "kn", "ml", "gu",
        "pa", "mr", "or", "si", "th", "vi", "id", "ms", "tl", "tr", "el",
        "sw", "am", "km", "my", "ku", "af", "be", "ceb", "co", "hr", "nl", "ny", "sq"
    )
        private set

    private var base: Context? = null
    private var resources: Resources? = null
    private var code: String = ""
    private var version by mutableIntStateOf(0)

    /** The locale of the app language, for dates and weekday names. */
    val locale: Locale get() { version; return Locale.forLanguageTag(code.ifEmpty { "en" }) }

    /** Returns true if the current active language is a Right-To-Left (RTL) script. */
    val isRtl: Boolean
        get() {
            val lang = locale.language.lowercase()
            return lang in listOf("ar", "fa", "ur", "he", "ku", "yi", "ps", "sd")
        }

    fun init(context: Context) {
        base = context.applicationContext
        // The list of languages that have translated strings comes from res/values/app_languages.xml.
        supported = context.resources.getStringArray(com.translator.app.R.array.app_language_codes).toList().ifEmpty { supported }
    }

    /** True if [code] has translated interface strings. */
    fun isSupported(code: String) = code in supported

    // Some Android versions know a few languages only under their old code (Hebrew "iw", Indonesian "in", Yiddish "ji"), so each
    // language is tried under both. A language is only used when its translated strings really load: four sample texts must
    // differ from the English ones. That is how "all of them work" is checked on the phone itself, not assumed.
    private val legacyCodes = mapOf("he" to "iw", "id" to "in", "yi" to "ji")
    private val probeIds = intArrayOf(R.string.skip, R.string.continue_label, R.string.translate, R.string.history)
    private val chosenLocale = java.util.concurrent.ConcurrentHashMap<String, Locale>()
    private var verifiedCodes: List<String>? = null

    private fun resourcesFor(context: Context, locale: Locale): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return context.createConfigurationContext(config).resources
    }

    /** The locale under which [code]'s strings load, or null if they do not load at all. */
    private fun pickLocale(context: Context, code: String): Locale? {
        if (code == "en") return Locale.ENGLISH
        chosenLocale[code]?.let { return it }
        val english = resourcesFor(context, Locale.ENGLISH)
        val sample = probeIds.map { english.getString(it) }
        val candidates = listOfNotNull(Locale.forLanguageTag(code), legacyCodes[code]?.let { Locale(it) })
        for (locale in candidates) {
            val res = resourcesFor(context, locale)
            if (probeIds.indices.any { i -> res.getString(probeIds[i]) != sample[i] }) { chosenLocale[code] = locale; return locale }
        }
        return null
    }

    /** The languages whose strings really load on this phone, in the order they are offered. Checked once, off the main thread. */
    suspend fun verified(): List<String> {
        verifiedCodes?.let { return it }
        val context = base ?: return supported
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            supported.filter { pickLocale(context, it) != null }.also { verifiedCodes = it }
        }
    }

    fun set(languageCode: String) {
        val context = base ?: return
        val locale = if (languageCode in supported) pickLocale(context, languageCode) else null
        val wanted = if (locale != null) languageCode else "en"
        if (wanted == code && resources != null) return
        resources = resourcesFor(context, locale ?: Locale.ENGLISH)
        code = wanted
        version++
    }

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
