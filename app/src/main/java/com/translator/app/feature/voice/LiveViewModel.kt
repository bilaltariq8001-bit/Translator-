package com.translator.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.core.data.TranslateTextUseCase
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three hands-free voice modes. All use the device speech recognizer and the normal translation path. */
enum class LiveMode(@androidx.annotation.StringRes val title: Int) {
    /** Two people take turns; each has a mic for their own language. */
    Conversation(com.translator.app.R.string.conversation),

    /** One speaker, continuous listening, translations appear as subtitles. */
    Realtime(com.translator.app.R.string.real_time),

    /** Like real-time, and each translation is spoken aloud (for wearing headphones). */
    Headphone(com.translator.app.R.string.headphone),
}

/** One thing somebody said. [side] 0 spoke the "from" language, 1 spoke the "to" language. */
data class LiveLine(
    val id: Long,
    val side: Int,
    val heard: String,
    val translation: String = "",
    val pending: Boolean = true,
    val error: TranslationError? = null,
)

data class LiveUiState(
    val lines: List<LiveLine> = emptyList(),
    val partial: String = "",
    val partialSide: Int = 0,
)

class LiveViewModel(private val translateText: TranslateTextUseCase) : ViewModel() {
    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()
    private var nextId = 1L

    fun onPartial(text: String, side: Int) = _state.update { it.copy(partial = text, partialSide = side) }

    /** Adds what was heard at once, then fills in its translation when it arrives. */
    fun onHeard(text: String, side: Int, from: String, to: String, tone: String) {
        val id = nextId++
        _state.update { it.copy(lines = it.lines + LiveLine(id, side, text), partial = "") }
        viewModelScope.launch {
            val (outcome, _) = translateText(TranslateRequest(text, from, to, tone))
            _state.update { s ->
                s.copy(
                    lines = s.lines.map { line ->
                        if (line.id != id) line else when (outcome) {
                            is TranslationOutcome.Success -> line.copy(translation = outcome.value.text, pending = false)
                            is TranslationOutcome.Failure -> line.copy(pending = false, error = outcome.error)
                        }
                    },
                )
            }
        }
    }

    fun clear() { _state.value = LiveUiState() }
}
