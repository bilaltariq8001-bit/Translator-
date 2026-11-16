package com.translator.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.core.audio.SpeechError
import com.translator.app.core.data.TranslateTextUseCase
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoicePhase { Idle, Listening, Translating, Done, NoSpeech, Failed }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val partial: String = "",
    val heard: String = "",
    val translation: String = "",
    val translationError: TranslationError? = null,
    val speechError: SpeechError? = null,
)

class VoiceViewModel(private val translateText: TranslateTextUseCase) : ViewModel() {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onListening() {
        job?.cancel()
        _state.value = VoiceUiState(phase = VoicePhase.Listening)
    }

    fun onPartial(text: String) = _state.update { it.copy(partial = text) }

    fun onSpeechError(error: SpeechError) = _state.update {
        it.copy(phase = if (error == SpeechError.NoSpeech) VoicePhase.NoSpeech else VoicePhase.Failed, speechError = error)
    }

    fun onHeard(text: String, source: String, target: String, tone: String) {
        _state.update { it.copy(phase = VoicePhase.Translating, heard = text, partial = "") }
        job?.cancel()
        job = viewModelScope.launch {
            val (outcome, _) = translateText(TranslateRequest(text, source, target, tone))
            _state.update {
                when (outcome) {
                    is TranslationOutcome.Success -> it.copy(phase = VoicePhase.Done, translation = outcome.value.text, translationError = null)
                    is TranslationOutcome.Failure -> it.copy(phase = VoicePhase.Failed, translationError = outcome.error)
                }
            }
        }
    }

    fun reset() {
        job?.cancel()
        _state.value = VoiceUiState()
    }
}
