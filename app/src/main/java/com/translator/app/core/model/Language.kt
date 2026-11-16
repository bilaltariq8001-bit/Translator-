package com.translator.app.core.model

/**
 * [offline] means a language pack can be downloaded for it (on-device translation); it does not mean the pack is
 * installed. Which packs are installed is tracked by core/offline/OfflinePacks.
 */
data class Language(val code: String, val name: String, val nativeName: String, val rtl: Boolean = false, val offline: Boolean = false)

object Languages {
    // The first rows are the ones shown first in the picker; the rest follow A to Z.
    val all: List<Language> = listOf(
        Language("es", "Spanish", "Español", offline = true),
        Language("ur", "Urdu", "اردو", rtl = true, offline = true),
        Language("ja", "Japanese", "日本語", offline = true),
        Language("ar", "Arabic", "العربية", rtl = true, offline = true),
        Language("zh", "Chinese", "中文", offline = true),
        Language("en", "English", "English", offline = true),
        Language("fr", "French", "Français", offline = true),
        Language("de", "German", "Deutsch", offline = true),
        Language("hi", "Hindi", "हिन्दी", offline = true),
        Language("ko", "Korean", "한국어", offline = true),
        Language("tr", "Turkish", "Türkçe", offline = true),
        Language("af", "Afrikaans", "Afrikaans", offline = true),
        Language("sq", "Albanian", "Shqip", offline = true),
        Language("am", "Amharic", "አማርኛ"),
        Language("hy", "Armenian", "Հայերեն"),
        Language("az", "Azerbaijani", "Azərbaycan"),
        Language("eu", "Basque", "Euskara"),
        Language("be", "Belarusian", "Беларуская", offline = true),
        Language("bn", "Bengali", "বাংলা", offline = true),
        Language("bs", "Bosnian", "Bosanski"),
        Language("bg", "Bulgarian", "Български", offline = true),
        Language("my", "Burmese", "မြန်မာ"),
        Language("ca", "Catalan", "Català", offline = true),
        Language("ceb", "Cebuano", "Cebuano"),
        Language("ny", "Chichewa", "Chichewa"),
        Language("zh-TW", "Chinese (Traditional)", "繁體中文"),
        Language("co", "Corsican", "Corsu"),
        Language("hr", "Croatian", "Hrvatski", offline = true),
        Language("cs", "Czech", "Čeština", offline = true),
        Language("da", "Danish", "Dansk", offline = true),
        Language("nl", "Dutch", "Nederlands", offline = true),
        Language("eo", "Esperanto", "Esperanto", offline = true),
        Language("et", "Estonian", "Eesti", offline = true),
        Language("tl", "Filipino", "Filipino", offline = true),
        Language("fi", "Finnish", "Suomi", offline = true),
        Language("fy", "Frisian", "Frysk"),
        Language("gl", "Galician", "Galego", offline = true),
        Language("ka", "Georgian", "ქართული", offline = true),
        Language("el", "Greek", "Ελληνικά", offline = true),
        Language("gu", "Gujarati", "ગુજરાતી", offline = true),
        Language("ht", "Haitian Creole", "Kreyòl ayisyen", offline = true),
        Language("ha", "Hausa", "Hausa"),
        Language("haw", "Hawaiian", "ʻŌlelo Hawaiʻi"),
        Language("he", "Hebrew", "עברית", rtl = true, offline = true),
        Language("hmn", "Hmong", "Hmoob"),
        Language("hu", "Hungarian", "Magyar", offline = true),
        Language("is", "Icelandic", "Íslenska", offline = true),
        Language("ig", "Igbo", "Igbo"),
        Language("id", "Indonesian", "Bahasa Indonesia", offline = true),
        Language("ga", "Irish", "Gaeilge", offline = true),
        Language("it", "Italian", "Italiano", offline = true),
        Language("jv", "Javanese", "Basa Jawa"),
        Language("kn", "Kannada", "ಕನ್ನಡ", offline = true),
        Language("kk", "Kazakh", "Қазақша"),
        Language("km", "Khmer", "ខ្មែរ"),
        Language("rw", "Kinyarwanda", "Kinyarwanda"),
        Language("ku", "Kurdish (Kurmanji)", "Kurdî"),
        Language("ky", "Kyrgyz", "Кыргызча"),
        Language("lo", "Lao", "ລາວ"),
        Language("la", "Latin", "Latina"),
        Language("lv", "Latvian", "Latviešu", offline = true),
        Language("lt", "Lithuanian", "Lietuvių", offline = true),
        Language("lb", "Luxembourgish", "Lëtzebuergesch"),
        Language("mk", "Macedonian", "Македонски", offline = true),
        Language("mg", "Malagasy", "Malagasy"),
        Language("ms", "Malay", "Bahasa Melayu", offline = true),
        Language("ml", "Malayalam", "മലയാളം"),
        Language("mt", "Maltese", "Malti", offline = true),
        Language("mi", "Maori", "Māori"),
        Language("mr", "Marathi", "मराठी", offline = true),
        Language("mn", "Mongolian", "Монгол"),
        Language("ne", "Nepali", "नेपाली"),
        Language("no", "Norwegian", "Norsk", offline = true),
        Language("or", "Odia", "ଓଡ଼ିଆ"),
        Language("ps", "Pashto", "پښتو", rtl = true),
        Language("fa", "Persian", "فارسی", rtl = true, offline = true),
        Language("pl", "Polish", "Polski", offline = true),
        Language("pt", "Portuguese", "Português", offline = true),
        Language("pa", "Punjabi", "ਪੰਜਾਬੀ"),
        Language("ro", "Romanian", "Română", offline = true),
        Language("ru", "Russian", "Русский", offline = true),
        Language("sm", "Samoan", "Gagana Samoa"),
        Language("gd", "Scots Gaelic", "Gàidhlig"),
        Language("sr", "Serbian", "Српски"),
        Language("st", "Sesotho", "Sesotho"),
        Language("sn", "Shona", "chiShona"),
        Language("sd", "Sindhi", "سنڌي", rtl = true),
        Language("si", "Sinhala", "සිංහල"),
        Language("sk", "Slovak", "Slovenčina", offline = true),
        Language("sl", "Slovenian", "Slovenščina", offline = true),
        Language("so", "Somali", "Soomaali"),
        Language("su", "Sundanese", "Basa Sunda"),
        Language("sw", "Swahili", "Kiswahili", offline = true),
        Language("sv", "Swedish", "Svenska", offline = true),
        Language("tg", "Tajik", "Тоҷикӣ"),
        Language("ta", "Tamil", "தமிழ்", offline = true),
        Language("tt", "Tatar", "Татар"),
        Language("te", "Telugu", "తెలుగు", offline = true),
        Language("th", "Thai", "ไทย", offline = true),
        Language("tk", "Turkmen", "Türkmen"),
        Language("uk", "Ukrainian", "Українська", offline = true),
        Language("ug", "Uyghur", "ئۇيغۇرچە", rtl = true),
        Language("uz", "Uzbek", "Oʻzbek"),
        Language("vi", "Vietnamese", "Tiếng Việt", offline = true),
        Language("cy", "Welsh", "Cymraeg", offline = true),
        Language("xh", "Xhosa", "isiXhosa"),
        Language("yi", "Yiddish", "ייִדיש", rtl = true),
        Language("yo", "Yoruba", "Yorùbá"),
        Language("zu", "Zulu", "isiZulu"),
    )

    /** The six app languages offered on the first screen. */
    val appLanguages: List<Language> = listOf("en", "ur", "ar", "hi", "es", "ja").map(::byCode)

    /** The original languages: always listed first and shown without any download or offline marker. */
    val core: Set<String> = setOf("es", "ur", "ja", "ar", "zh", "en", "fr", "de", "hi", "ko", "tr")

    /** Languages that can be downloaded for offline use. */
    val downloadable: List<Language> = all.filter { it.offline && it.code != "en" }

    fun byCode(code: String): Language = all.first { it.code == code }
    fun byCodeOrNull(code: String?): Language? = all.firstOrNull { it.code == code }
}

enum class Tone(val label: String) { General("General"), Business("Business"), Travel("Travel"), Casual("Casual") }
