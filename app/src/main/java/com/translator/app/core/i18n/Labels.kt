package com.translator.app.core.i18n

import com.translator.app.R
import com.translator.app.core.model.Language
import com.translator.app.core.model.Tone
import java.util.Locale

// Some choices are stored or sent to the AI under a fixed English name (so a saved choice survives a change of app
// language and the prompt stays the same). These turn that name into the text shown on screen.

/** The label for a writer option such as a tone or the language to explain in. Unknown values show as they are. */
fun optionLabel(value: String): String = when (value) {
    "Formal" -> str(R.string.opt_formal)
    "Friendly" -> str(R.string.opt_friendly)
    "Business" -> str(R.string.opt_business)
    "Spanish" -> str(R.string.opt_spanish)
    "Urdu" -> str(R.string.opt_urdu)
    "Arabic" -> str(R.string.opt_arabic)
    "Hindi" -> str(R.string.opt_hindi)
    else -> value
}

/** The label for a travel-phrase category. */
fun categoryLabel(value: String): String = when (value) {
    "Restaurant" -> str(R.string.cat_restaurant)
    "Directions" -> str(R.string.cat_directions)
    "Hotel" -> str(R.string.cat_hotel)
    "Emergency" -> str(R.string.cat_emergency)
    else -> value
}

/** The label for a translation style. The saved value stays the English name. */
fun toneLabel(tone: Tone): String = when (tone) {
    Tone.General -> str(R.string.tone_general)
    Tone.Business -> str(R.string.tone_business)
    Tone.Travel -> str(R.string.tone_travel)
    Tone.Casual -> str(R.string.tone_casual)
}

/** The language's name in the app language ("español" in a Spanish app), or its English name when Android has none. */
fun Language.localName(): String {
    val app = AppLocale.locale
    if (app.language == "en") return name
    val shown = Locale(code).getDisplayLanguage(app)
    return if (shown.isBlank() || shown.equals(code, ignoreCase = true)) name else shown.replaceFirstChar { it.titlecase(app) }
}
