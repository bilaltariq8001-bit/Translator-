package com.translator.app.keyboard

/** The text typed on the keyboard that has not been translated or finished yet. The editor shows it as "composing" text. */
class TypingBuffer {
    private val sb = StringBuilder()
    val text: String get() = sb.toString()
    val isEmpty: Boolean get() = sb.isEmpty()
    val isBlank: Boolean get() = sb.isBlank()

    fun append(s: String) { sb.append(s) }

    /** Removes the last character (a whole emoji or other surrogate pair counts as one). Returns false if there was nothing. */
    fun backspace(): Boolean {
        if (sb.isEmpty()) return false
        val last = sb.length - 1
        val cut = if (last >= 1 && Character.isLowSurrogate(sb[last]) && Character.isHighSurrogate(sb[last - 1])) 2 else 1
        sb.setLength(sb.length - cut)
        return true
    }

    fun replaceWith(s: String) { sb.setLength(0); sb.append(s) }
    fun clear() { sb.setLength(0) }
}

enum class Shift { Off, Once, Lock }

/** Shift key behaviour: one tap capitalises the next letter, a quick second tap locks capitals. */
class ShiftState(private val doubleTapMs: Long = 400) {
    var state: Shift = Shift.Off
        private set
    private var lastTap = -1_000_000L

    fun tap(nowMs: Long) {
        state = when {
            state == Shift.Lock -> Shift.Off
            state == Shift.Once && nowMs - lastTap <= doubleTapMs -> Shift.Lock
            state == Shift.Once -> Shift.Off
            else -> Shift.Once
        }
        lastTap = nowMs
    }

    /** Called after a letter is typed: a single shift is used up. */
    fun afterLetter() { if (state == Shift.Once) state = Shift.Off }

    fun apply(letter: String): String = if (state == Shift.Off) letter else letter.uppercase()
}

enum class KeyPage { Letters, Extra, Symbols }

/** The characters on each page of a layout. [extra] is a second page of letters for scripts that need more than one. */
data class KeyboardLayout(
    val id: String,
    val letters: List<List<String>>,
    val extra: List<List<String>>?,
    val symbols: List<List<String>>,
    val comma: String,
    val period: String,
    val rtl: Boolean,
    val extraLabel: String? = null,
    val lettersLabel: String = "ABC",
)

object KeyboardLayouts {
    private fun row(s: String) = s.split(" ")

    private val latinSymbols = listOf(
        row("1 2 3 4 5 6 7 8 9 0"),
        row("- / : ; ( ) $ & @ \""),
        row(". , ? ! ' % + = #"),
    )

    val latin = KeyboardLayout(
        id = "latin",
        letters = listOf(row("q w e r t y u i o p"), row("a s d f g h j k l"), row("z x c v b n m")),
        extra = null,
        symbols = latinSymbols,
        comma = ",", period = ".", rtl = false,
    )

    val urdu = KeyboardLayout(
        id = "urdu",
        letters = listOf(row("ق و ع ر ت ے ء ی ہ پ"), row("ا س د ف گ ھ ج ک ل"), row("ز ش چ ط ب ن م")),
        extra = listOf(row("ٹ ڈ ڑ ں ث ذ ظ غ ض ص"), row("ژ خ ؤ ئ ۓ ۃ آ ح"), row("ّ ً ٍ ُ ِ َ ٔ")),
        symbols = listOf(
            row("۱ ۲ ۳ ۴ ۵ ۶ ۷ ۸ ۹ ۰"),
            row("- / : ؛ ( ) ٪ & @ \""),
            row("۔ ، ؟ ! ' « » ٫ ٬"),
        ),
        comma = "،", period = "۔", rtl = true, extraLabel = "ٹڈ", lettersLabel = "اب",
    )

    /** Urdu has its own layout; every other language uses the Latin one (with its letters typed as they are). */
    fun forLanguage(code: String): KeyboardLayout = if (code == "ur") urdu else latin
}
