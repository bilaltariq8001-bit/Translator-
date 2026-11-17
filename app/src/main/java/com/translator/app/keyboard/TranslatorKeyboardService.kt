package com.translator.app.keyboard

import com.translator.app.core.i18n.str
import com.translator.app.R
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.translator.app.TranslatorApp
import com.translator.app.core.model.Language
import com.translator.app.core.model.Languages
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationOutcome
import com.translator.app.feature.translate.message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The translator keyboard: type in your language, tap Translate, and the translation replaces what you typed in whatever
 * app you are using. Nothing is sent anywhere until you tap Translate, and never from password fields. Uses the same
 * translator as the app (online, AI when on, and downloaded offline languages when there is no connection).
 */
class TranslatorKeyboardService : InputMethodService(), KeyboardListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val buffer = TypingBuffer()
    private lateinit var keyboard: TranslatorKeyboardView
    private var typing: Language = Languages.byCode("en")
    private var target: Language = Languages.byCode("es")
    private var job: Job? = null
    private var undo: Undo? = null
    private var protectedField = false

    private class Undo(val original: String, val insertedLength: Int)

    private val container get() = (application as TranslatorApp).container

    override fun onCreateInputView(): View {
        // The keyboard's own labels follow the app language too; read it before the view is built.
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(400) { container.settings.settings.first().appLanguage } }
            ?.let { com.translator.app.core.i18n.AppLocale.set(it) }
        keyboard = TranslatorKeyboardView(this, this)
        scope.launch {
            val s = container.settings.settings.first()
            typing = Languages.byCodeOrNull(s.sourceLang) ?: typing
            target = Languages.byCodeOrNull(s.targetLang) ?: target
            keyboard.setLanguages(typing, target)
        }
        keyboard.setLanguages(typing, target)
        return keyboard
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Some phones (Vivo among them) start input before the keyboard view exists; there is nothing to set up yet then.
        if (!::keyboard.isInitialized) return
        buffer.clear(); undo = null; job?.cancel(); keyboard.setBusy(false); keyboard.setUndoVisible(false); keyboard.showMessage(null)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        protectedField = (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        keyboard.setTranslateEnabled(!protectedField)
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) keyboard.showNumbers() else keyboard.resetPage()
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        keyboard.setEnterLabel(
            when (action) {
                EditorInfo.IME_ACTION_SEARCH -> "🔍"; EditorInfo.IME_ACTION_SEND -> "➤"; EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE -> "✓"
                else -> "↵"
            },
        )
    }

    override fun onFinishInput() {
        super.onFinishInput()
        buffer.clear(); undo = null; job?.cancel()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The user moved the cursor or the app took the composing text away: forget what we were holding.
        if (!buffer.isEmpty && candidatesStart == -1) buffer.clear()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ typing

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        undo = null
        if (!::keyboard.isInitialized) { buffer.clear(); return }
        keyboard.setUndoVisible(false)
        if (protectedField) { ic.commitText(text, 1); return }
        buffer.append(text)
        ic.setComposingText(buffer.text, 1)
    }

    override fun onSpace() = onText(" ")

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        undo = null
        if (!::keyboard.isInitialized) { buffer.clear(); return }
        keyboard.setUndoVisible(false)
        if (!protectedField && buffer.backspace()) {
            if (buffer.isEmpty) ic.commitText("", 1) else ic.setComposingText(buffer.text, 1)
        } else {
            ic.finishComposingText()
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        undo = null
        if (!::keyboard.isInitialized) { buffer.clear(); return }
        keyboard.setUndoVisible(false)
        ic.finishComposingText(); buffer.clear()
        val action = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction) ic.performEditorAction(action)
        else { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)); ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)) }
    }

    override fun onSwitchKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window?.window?.attributes?.token
            // Go to the previous keyboard; if there is none to go back to, show the picker.
            if (token == null || !imm.switchToLastInputMethod(token)) imm.showInputMethodPicker()
        } catch (_: Throwable) { }
    }

    // ------------------------------------------------------------------ translating

    override fun onTranslate() {
        val ic = currentInputConnection ?: return
        if (protectedField) return
        val original = buffer.text
        if (original.isBlank()) { keyboard.showMessage(str(R.string.type_something_first_then_tap)); return }
        job?.cancel()
        keyboard.setBusy(true); keyboard.showMessage(str(R.string.translating_to, target.name))
        job = scope.launch {
            val outcome = container.translationDataSource.translate(TranslateRequest(original.trim(), typing.code, target.code, "General"))
            keyboard.setBusy(false)
            when (outcome) {
                is TranslationOutcome.Success -> {
                    val translated = outcome.value.text
                    val conn = currentInputConnection ?: return@launch
                    conn.beginBatchEdit()
                    conn.setComposingText(translated, 1)
                    conn.finishComposingText()
                    conn.endBatchEdit()
                    buffer.clear()
                    undo = Undo(original, translated.length)
                    keyboard.setUndoVisible(true); keyboard.showMessage(str(R.string.translated_to, target.name))
                }
                is TranslationOutcome.Failure -> keyboard.showMessage("!" + outcome.error.message())
            }
        }
    }

    override fun onUndo() {
        val ic = currentInputConnection ?: return
        val u = undo ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(u.insertedLength, 0)
        buffer.replaceWith(u.original)
        ic.setComposingText(buffer.text, 1)
        ic.endBatchEdit()
        undo = null; keyboard.setUndoVisible(false); keyboard.showMessage(null)
    }

    // ------------------------------------------------------------------ languages

    override fun onSwap() {
        val t = typing; typing = target; target = t
        saveAndShow()
    }

    override fun onPickLanguage(typing: Boolean, language: Language) {
        if (typing) { this.typing = language; if (target == language) target = this.typing.let { if (it.code == "en") Languages.byCode("es") else Languages.byCode("en") } }
        else { target = language; if (this.typing == language) this.typing = if (language.code == "en") Languages.byCode("es") else Languages.byCode("en") }
        saveAndShow()
    }

    private fun saveAndShow() {
        keyboard.setLanguages(typing, target)
        scope.launch { container.settings.setPair(typing.code, target.code) }
    }
}
