package com.translator.app.core.model

/**
 * Country flags for the language lists, one flag per language. A language is not a country, so each one is shown with
 * the flag of the country where it is native (the first one listed below; the second is only noted for reference and
 * never shown). A language with no single home, such as Esperanto or Hmong, shows a globe instead of a wrong flag.
 */
object Flags {
    private val countries: Map<String, List<String>> = mapOf(
        "es" to listOf("ES", "MX"), "ur" to listOf("PK"), "ja" to listOf("JP"), "ar" to listOf("SA", "EG"), "zh" to listOf("CN", "TW"),
        "en" to listOf("GB", "US"), "fr" to listOf("FR", "CA"), "de" to listOf("DE"), "hi" to listOf("IN"), "ko" to listOf("KR"),
        "tr" to listOf("TR"), "af" to listOf("ZA"), "sq" to listOf("AL"), "am" to listOf("ET"), "hy" to listOf("AM"), "az" to listOf("AZ"),
        "eu" to listOf("ES"), "be" to listOf("BY"), "bn" to listOf("BD", "IN"), "bs" to listOf("BA"), "bg" to listOf("BG"), "my" to listOf("MM"),
        "ca" to listOf("ES", "AD"), "ceb" to listOf("PH"), "ny" to listOf("MW"), "co" to listOf("FR"), "hr" to listOf("HR"), "cs" to listOf("CZ"),
        "da" to listOf("DK"), "nl" to listOf("NL", "BE"), "et" to listOf("EE"), "tl" to listOf("PH"), "fi" to listOf("FI"), "fy" to listOf("NL"),
        "gl" to listOf("ES"), "ka" to listOf("GE"), "el" to listOf("GR"), "gu" to listOf("IN"), "ht" to listOf("HT"), "ha" to listOf("NG", "NE"),
        "haw" to listOf("US"), "he" to listOf("IL"), "hu" to listOf("HU"), "is" to listOf("IS"), "ig" to listOf("NG"), "id" to listOf("ID"),
        "ga" to listOf("IE"), "it" to listOf("IT", "CH"), "jv" to listOf("ID"), "kn" to listOf("IN"), "kk" to listOf("KZ"), "km" to listOf("KH"),
        "rw" to listOf("RW"), "ku" to listOf("IQ", "TR"), "ky" to listOf("KG"), "lo" to listOf("LA"), "la" to listOf("VA"), "lv" to listOf("LV"),
        "lt" to listOf("LT"), "lb" to listOf("LU"), "mk" to listOf("MK"), "mg" to listOf("MG"), "ms" to listOf("MY", "SG"), "ml" to listOf("IN"),
        "mt" to listOf("MT"), "mi" to listOf("NZ"), "mr" to listOf("IN"), "mn" to listOf("MN"), "ne" to listOf("NP"), "no" to listOf("NO"),
        "or" to listOf("IN"), "ps" to listOf("AF"), "fa" to listOf("IR", "AF"), "pl" to listOf("PL"), "pt" to listOf("PT", "BR"), "pa" to listOf("IN", "PK"),
        "ro" to listOf("RO"), "ru" to listOf("RU"), "sm" to listOf("WS"), "gd" to listOf("GB"), "sr" to listOf("RS"), "st" to listOf("LS"),
        "sn" to listOf("ZW"), "sd" to listOf("PK"), "si" to listOf("LK"), "sk" to listOf("SK"), "sl" to listOf("SI"), "so" to listOf("SO"),
        "su" to listOf("ID"), "sw" to listOf("TZ", "KE"), "sv" to listOf("SE"), "tg" to listOf("TJ"), "ta" to listOf("IN", "LK"), "tt" to listOf("RU"),
        "te" to listOf("IN"), "th" to listOf("TH"), "tk" to listOf("TM"), "uk" to listOf("UA"), "ug" to listOf("CN"), "uz" to listOf("UZ"),
        "vi" to listOf("VN"), "cy" to listOf("GB"), "xh" to listOf("ZA"), "yi" to listOf("IL"), "yo" to listOf("NG"), "zu" to listOf("ZA"),
    )

    /** The single flag emoji for [code], or a globe when the language has no single home. */
    fun of(code: String): String {
        val iso = countries[code]?.first() ?: return "🌐"
        return iso.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }
}
