package com.translator.app.keyboard

import com.translator.app.core.i18n.str
import com.translator.app.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.translator.app.core.model.Language
import com.translator.app.core.model.Languages

interface KeyboardListener {
    fun onText(text: String)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onTranslate()
    fun onUndo()
    fun onSwitchKeyboard()
    fun onSwap()
    fun onPickLanguage(typing: Boolean, language: Language)
}

/**
 * The keyboard itself, built from plain views (an input method service cannot host Compose reliably). A top bar holds the
 * language pair and the Translate button; below it are the keys. Everything is drawn in the app's blue.
 */
class TranslatorKeyboardView(context: Context, private val listener: KeyboardListener) : LinearLayout(context) {
    private val ui = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    private var layout: KeyboardLayout = KeyboardLayouts.latin
    private var page = KeyPage.Letters
    private val shift = ShiftState()

    private val fromChip = languageChip(str(R.string.i_type_in))
    private val toChip = languageChip(str(R.string.translate_to_3))
    private val swapButton = chip("⇄", square = true)
    private val undoChip = chip(str(R.string.undo), tint = false).apply { visibility = View.GONE }
    private val translateButton = TextView(context)
    private val message = TextView(context)
    private val keyArea = LinearLayout(context)
    private val pickerPanel = FrameLayout(context)
    private val root = FrameLayout(context)

    private var busy = false
    private var translateEnabled = true
    private var enterLabel = "↵"

    init {
        orientation = VERTICAL
        setBackgroundColor(BG)
        addView(root, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val column = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(8)) }
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Top bar: typing language, swap, translate-to language, then Undo and Translate on the right.
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(fromChip, LayoutParams(0, dp(48), 1f))
        bar.addView(swapButton, LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4); marginEnd = dp(4) })
        bar.addView(toChip, LayoutParams(0, dp(48), 1f))
        column.addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val action = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        message.apply { textSize = 13f; setTextColor(SOFT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; text = str(R.string.type_then_tap_translate) }
        action.addView(message, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        action.addView(undoChip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) })
        translateButton.apply {
            text = str(R.string.kb_translate) + "  ➜"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            background = ripple(gradient(PRIMARY, PRIMARY_LIGHT, dp(12).toFloat()), 0x33FFFFFF)
            setOnClickListener { if (!busy && translateEnabled) listener.onTranslate() }
        }
        action.addView(translateButton, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        column.addView(action, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6); bottomMargin = dp(6) })

        keyArea.orientation = VERTICAL
        column.addView(keyArea, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        pickerPanel.visibility = View.GONE
        pickerPanel.setBackgroundColor(BG)
        root.addView(pickerPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fromChip.setOnClickListener { openPicker(typing = true) }
        toChip.setOnClickListener { openPicker(typing = false) }
        swapButton.setOnClickListener { tap(); listener.onSwap() }
        undoChip.setOnClickListener { tap(); listener.onUndo() }
        renderKeys()
    }

    // ------------------------------------------------------------------ state set by the service

    private var currentTyping: Language = Languages.byCode("en")
    private var currentTarget: Language = Languages.byCode("es")

    fun setLanguages(typing: Language, translateTo: Language) {
        currentTyping = typing; currentTarget = translateTo
        chipName(fromChip).text = typing.name
        chipName(toChip).text = translateTo.name
        val newLayout = KeyboardLayouts.forLanguage(typing.code)
        if (newLayout.id != layout.id) { layout = newLayout; page = KeyPage.Letters; renderKeys() }
    }

    fun setBusy(value: Boolean) {
        busy = value
        translateButton.text = if (value) str(R.string.kb_translating) else str(R.string.kb_translate) + "  ➜"
        translateButton.alpha = if (value) 0.7f else 1f
    }

    fun setTranslateEnabled(value: Boolean) {
        translateEnabled = value
        translateButton.alpha = if (value) 1f else 0.4f
        if (!value) showMessage(str(R.string.translation_is_off_in_password))
    }

    fun setUndoVisible(value: Boolean) { undoChip.visibility = if (value) View.VISIBLE else View.GONE }

    fun showMessage(text: String?) { message.text = text ?: str(R.string.type_then_tap_translate) ; message.setTextColor(if (text != null && text.startsWith("!")) DANGER else SOFT); if (text != null && text.startsWith("!")) message.text = text.drop(1) }

    fun setEnterLabel(label: String) { enterLabel = label; renderKeys() }

    fun showNumbers() { page = KeyPage.Symbols; renderKeys() }

    fun resetPage() { if (page != KeyPage.Letters) { page = KeyPage.Letters; renderKeys() } }

    // ------------------------------------------------------------------ keys

    private fun renderKeys() {
        keyArea.removeAllViews()
        val rows = when (page) {
            KeyPage.Letters -> layout.letters
            KeyPage.Extra -> layout.extra ?: layout.letters
            KeyPage.Symbols -> layout.symbols
        }
        rows.forEachIndexed { i, r ->
            val line = row()
            if (i == 2) {
                line.addView(
                    if (page == KeyPage.Letters && !layout.rtl) shiftKey()
                    else if (page == KeyPage.Letters && layout.extra != null) modeKey(layout.extraLabel ?: "…", KeyPage.Extra)
                    else if (page == KeyPage.Symbols) View(context)
                    else modeKey(layout.lettersLabel, KeyPage.Letters),
                    keyParams(1.4f),
                )
            }
            r.forEach { ch -> line.addView(letterKey(ch), keyParams(1f)) }
            if (i == 2) line.addView(backspaceKey(), keyParams(1.4f))
            keyArea.addView(line, rowParams())
        }
        // The first two rows are shorter in some layouts; the weights keep every key the same width regardless.
        val bottom = row()
        bottom.addView(
            if (page == KeyPage.Symbols) modeKey(layout.lettersLabel, KeyPage.Letters, function = true) else modeKey("123", KeyPage.Symbols, function = true),
            keyParams(1.5f),
        )
        bottom.addView(plainKey("🌐", function = true) { listener.onSwitchKeyboard() }, keyParams(1.2f))
        bottom.addView(letterKey(layout.comma, function = true), keyParams(1f))
        bottom.addView(plainKey(if (layout.rtl) "فاصلہ" else "space", function = false) { listener.onSpace() }, keyParams(4.2f))
        bottom.addView(letterKey(layout.period, function = true), keyParams(1f))
        bottom.addView(plainKey(enterLabel, accent = true) { listener.onEnter() }, keyParams(1.5f))
        keyArea.addView(bottom, rowParams())
    }

    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
    private fun rowParams() = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(3); bottomMargin = dp(3) }
    private fun keyParams(weight: Float) = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { marginStart = dp(3); marginEnd = dp(3) }

    private fun letterKey(ch: String, function: Boolean = false): View {
        val shown = if (page == KeyPage.Letters && !layout.rtl && ch.length == 1 && ch[0].isLetter()) shift.apply(ch) else ch
        return plainKey(shown, function = function) {
            listener.onText(shown)
            if (page == KeyPage.Letters && !layout.rtl && ch[0].isLetter()) { val before = shift.state; shift.afterLetter(); if (before != shift.state) renderKeys() }
        }
    }

    private fun shiftKey(): View {
        val label = when (shift.state) { Shift.Off -> "⇧"; Shift.Once -> "⬆"; Shift.Lock -> "⇪" }
        return plainKey(label, function = true, accent = shift.state != Shift.Off) { shift.tap(System.currentTimeMillis()); renderKeys() }
    }

    private fun modeKey(label: String, target: KeyPage, function: Boolean = true): View = plainKey(label, function = function) { page = target; renderKeys() }

    private fun backspaceKey(): View {
        val key = plainKey("⌫", function = true) { }
        var repeating = false
        val repeat = object : Runnable {
            override fun run() { if (repeating) { listener.onBackspace(); ui.postDelayed(this, 55) } }
        }
        key.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; tap(); listener.onBackspace(); repeating = true; ui.postDelayed(repeat, 380) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; repeating = false; ui.removeCallbacks(repeat) }
            }
            true
        }
        return key
    }

    private fun plainKey(label: String, function: Boolean = false, accent: Boolean = false, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = if (label.length > 2) 14f else 21f
        typeface = if (label.length > 2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (accent) Color.WHITE else TEXT)
        background = ripple(
            if (accent) gradient(PRIMARY, PRIMARY_LIGHT, dp(10).toFloat()) else solid(if (function) FUNCTION else KEY, dp(10).toFloat(), stroke = true),
            if (accent) 0x33FFFFFF else 0x22000000,
        )
        isClickable = true
        setOnClickListener { tap(); onClick() }
    }

    // ------------------------------------------------------------------ language list

    private fun openPicker(typing: Boolean) {
        pickerPanel.removeAllViews()
        val selected = if (typing) currentTyping else currentTarget
        var query = ""
        val col = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(6)) }

        // Title and a round close button.
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = if (typing) str(R.string.i_type_in_2) else str(R.string.translate_into); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(TEXT); setPadding(dp(4), 0, 0, 0)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = "✕"; textSize = 15f; gravity = Gravity.CENTER; setTextColor(SOFT)
            background = ripple(solid(Color.WHITE, dp(16).toFloat(), stroke = true), 0x22000000)
            setOnClickListener { tap(); closePicker() }
        }, LayoutParams(dp(32), dp(32)))
        col.addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // The search box. The keyboard's own letter keys below type into it, since a keyboard cannot use a normal text field.
        val search = TextView(context).apply {
            textSize = 15f; setTextColor(TEXT); gravity = Gravity.CENTER_VERTICAL; maxLines = 1
            setPadding(dp(14), 0, dp(14), 0)
            background = solid(Color.WHITE, dp(12).toFloat(), stroke = true)
        }
        col.addView(search, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(6); bottomMargin = dp(4) })

        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        scroll.addView(list)
        col.addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun pick(lang: Language) { tap(); closePicker(); listener.onPickLanguage(typing, lang) }

        fun fill() {
            search.text = if (query.isEmpty()) str(R.string.search_language) else str(R.string.text_2, query)
            search.setTextColor(if (query.isEmpty()) SOFT else TEXT)
            list.removeAllViews()
            val q = query.trim()
            val matches = Languages.all.filter { q.isEmpty() || it.name.contains(q, true) || it.nativeName.contains(q, true) || it.code.equals(q, true) }
                .sortedWith(compareByDescending<Language> { it.code == selected.code }.thenBy { it.name })
            if (matches.isEmpty()) list.addView(TextView(context).apply { text = str(R.string.no_language_found); textSize = 14f; setTextColor(SOFT); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0) })
            matches.forEach { lang ->
                val on = lang.code == selected.code
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(12), 0)
                    background = ripple(solid(if (on) PRIMARY_CONTAINER else Color.TRANSPARENT, dp(12).toFloat()), 0x22000000)
                    setOnClickListener { pick(lang) }
                }
                row.addView(TextView(context).apply {
                    text = com.translator.app.core.model.Flags.of(lang.code); textSize = 15f; gravity = Gravity.CENTER; maxLines = 1
                }, LayoutParams(dp(44), dp(30)))
                row.addView(TextView(context).apply {
                    text = lang.name; textSize = 15f; typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; setTextColor(TEXT); maxLines = 1
                    setPadding(dp(10), 0, dp(6), 0)
                }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(context).apply { text = lang.nativeName; textSize = 13f; setTextColor(SOFT); maxLines = 1 })
                if (on) row.addView(TextView(context).apply { text = "✓"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(PRIMARY); setPadding(dp(8), 0, 0, 0) })
                list.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
            }
            scroll.scrollTo(0, 0)
        }

        // Three compact rows of letters that type into the search box, with a delete key.
        fun letterKey(label: String, weight: Float = 1f, onClick: () -> Unit) = TextView(context).apply {
            text = label; textSize = 16f; gravity = Gravity.CENTER; setTextColor(TEXT)
            background = ripple(solid(Color.WHITE, dp(8).toFloat(), stroke = true), 0x22000000)
            setOnClickListener { tap(); onClick() }
        }.also { it.layoutParams = LayoutParams(0, dp(36), weight).apply { marginStart = dp(2); marginEnd = dp(2) } }
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { i, rowKeys ->
            val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            if (i == 1) line.addView(View(context), LayoutParams(0, dp(36), 0.5f))
            rowKeys.forEach { ch -> line.addView(letterKey(ch.toString()) { query += ch; fill() }) }
            if (i == 1) line.addView(View(context), LayoutParams(0, dp(36), 0.5f))
            if (i == 2) line.addView(letterKey("⌫", 1.5f) { query = query.dropLast(1); fill() })
            col.addView(line, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        }

        fill()
        pickerPanel.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // The panel covers exactly the keys, never more: the keyboard window must not grow to fill the screen.
        pickerPanel.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, root.getChildAt(0).height.coerceAtLeast(dp(260)))
        pickerPanel.visibility = View.VISIBLE
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#64708A")); letterSpacing = 0.08f
        setPadding(dp(4), dp(10), 0, dp(6))
    }

    private fun closePicker() { pickerPanel.visibility = View.GONE }

    // ------------------------------------------------------------------ drawing helpers

    private fun chip(label: String, square: Boolean = false, tint: Boolean = true) = TextView(context).apply {
        text = label; textSize = if (square) 18f else 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(PRIMARY); gravity = Gravity.CENTER
        maxLines = 1
        background = ripple(solid(PRIMARY_CONTAINER, dp(12).toFloat()), 0x22000000)
        isClickable = true
        if (!tint) setTextColor(TEXT)
    }

    /**
     * A tappable language selector: a small caption, the language name, and a round badge with a drawn chevron on the
     * right so it is clear that more languages are behind it. Child 0 holds the caption (0) and the name (1).
     */
    private fun languageChip(caption: String) = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true
        setPadding(dp(12), 0, dp(8), 0)
        background = ripple(solid(PRIMARY_CONTAINER, dp(14).toFloat()), 0x22000000)
        val texts = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        texts.addView(TextView(context).apply {
            text = caption; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f; setTextColor(SOFT); maxLines = 1
        })
        texts.addView(TextView(context).apply {
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(PRIMARY); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ChevronBadge(context), LayoutParams(dp(26), dp(26)).apply { marginStart = dp(4) })
    }.let { inner ->
        // Wrapped so the caller can reach the texts through getChildAt(0) while the whole chip stays clickable.
        FrameLayout(context).apply {
            isClickable = true
            background = inner.background
            inner.background = null
            addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            inner.isClickable = false
        }
    }

    /** The language-name text of a chip made by [languageChip]: wrapper, then the row, then its text column, then the name. */
    private fun chipName(chip: FrameLayout): TextView = (((chip.getChildAt(0) as LinearLayout).getChildAt(0)) as LinearLayout).getChildAt(1) as TextView

    /** A white round badge with a blue downward chevron, drawn directly so it is crisp at any density. */
    private inner class ChevronBadge(context: Context) : View(context) {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY; style = android.graphics.Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        private val path = android.graphics.Path()
        override fun onDraw(canvas: android.graphics.Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, width / 2f, fill)
            val w = width * 0.2f
            path.reset()
            path.moveTo(cx - w, cy - w * 0.45f)
            path.lineTo(cx, cy + w * 0.55f)
            path.lineTo(cx + w, cy - w * 0.45f)
            canvas.drawPath(path, line)
        }
    }

    private fun dp(v: Int) = (v * density + 0.5f).toInt()
    private fun tap() { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    private fun solid(color: Int, radius: Float, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke) setStroke(1, LINE)
    }

    private fun gradient(a: Int, b: Int, radius: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply { cornerRadius = radius }

    private fun ripple(content: GradientDrawable, rippleColor: Int) = RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)

    companion object {
        private val BG = Color.parseColor("#EEF1F8")
        private val KEY = Color.WHITE
        private val FUNCTION = Color.parseColor("#D9E1F2")
        private val LINE = Color.parseColor("#D3DBEA")
        private val TEXT = Color.parseColor("#0F1B33")
        private val SOFT = Color.parseColor("#475569")
        private val PRIMARY = Color.parseColor("#1457F2")
        private val PRIMARY_LIGHT = Color.parseColor("#4C86F0")
        private val PRIMARY_CONTAINER = Color.parseColor("#E1EAFF")
        private val DANGER = Color.parseColor("#D64545")
    }
}
