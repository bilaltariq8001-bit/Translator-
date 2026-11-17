package com.translator.app.feature.translate

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.analytics.reason
import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.core.ai.AiInsights
import com.translator.app.core.ai.AiInsightsSource
import com.translator.app.core.ai.InsightsOutcome
import com.translator.app.core.ai.InsightsRequest
import com.translator.app.core.data.HistoryItem
import com.translator.app.core.data.HistoryRepository
import com.translator.app.core.data.SettingsStore
import com.translator.app.core.data.TranslateTextUseCase
import com.translator.app.core.model.Language
import com.translator.app.core.model.Languages
import com.translator.app.core.model.Tone
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_CHARS = 5_000

data class TranslateResult(val historyId: Long, val source: Language, val target: Language, val sourceText: String, val text: String, val favorite: Boolean, val viaAi: Boolean = false)

data class TranslateUiState(
    val source: Language = Languages.byCode("en"),
    val target: Language = Languages.byCode("es"),
    val tone: Tone = Tone.General,
    val text: String = "",
    val loading: Boolean = false,
    val error: TranslationError? = null,
    val result: TranslateResult? = null,
    val recent: List<HistoryItem> = emptyList(),
    val insights: InsightsUiState = InsightsUiState.Idle,
)

/** AI insights for the current result: loaded only when the user asks. */
sealed interface InsightsUiState {
    data object Idle : InsightsUiState
    data object Loading : InsightsUiState
    data class Loaded(val value: AiInsights) : InsightsUiState
    data class Failed(val error: TranslationError) : InsightsUiState
}

sealed interface TranslateEffect {
    data object ShowResult : TranslateEffect
    /** A text translation succeeded; [count] is how many have succeeded since the app started. */
    data class Translated(val count: Int) : TranslateEffect
}

class TranslateViewModel(
    private val settings: SettingsStore,
    private val translateText: TranslateTextUseCase,
    private val history: HistoryRepository,
    private val insightsSource: AiInsightsSource,
    private val warmUp: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    private val effects = Channel<TranslateEffect>(Channel.BUFFERED)
    val effectFlow = effects.receiveAsFlow()

    private var job: Job? = null
    private var insightsJob: Job? = null
    private var successCount = 0

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _state.update {
                it.copy(
                    source = Languages.byCodeOrNull(s.sourceLang) ?: it.source,
                    target = Languages.byCodeOrNull(s.targetLang) ?: it.target,
                    tone = Tone.entries.firstOrNull { t -> t.label == s.tone } ?: it.tone,
                )
            }
        }
        viewModelScope.launch {
            history.items.collect { items ->
                _state.update { st ->
                    val fav = st.result?.let { r -> items.firstOrNull { it.id == r.historyId }?.favorite }
                    st.copy(recent = items.take(3), result = st.result?.copy(favorite = fav ?: st.result.favorite))
                }
            }
        }
    }

    private var warmedUp = false

    fun onTextChange(text: String) {
        cancelPending()
        // The first time anything is typed, open the connection to the translation service in the background.
        if (!warmedUp && text.isNotBlank()) { warmedUp = true; warmUp() }
        _state.update { it.copy(text = text.take(MAX_CHARS), error = null) }
    }

    fun onSample(text: String) = onTextChange(text)

    fun onTone(tone: Tone) {
        cancelPending()
        _state.update { it.copy(tone = tone) }
        viewModelScope.launch { settings.setTone(tone.label) }
    }

    fun onSwap() {
        cancelPending()
        _state.update { it.copy(source = it.target, target = it.source) }
        persistPair()
    }

    fun onSource(lang: Language) {
        cancelPending()
        _state.update { it.copy(source = lang, target = if (lang == it.target) it.source else it.target) }
        persistPair()
    }

    fun onTarget(lang: Language) {
        cancelPending()
        _state.update { it.copy(target = lang, source = if (lang == it.source) it.target else it.source) }
        persistPair()
    }

    private fun persistPair() {
        val s = _state.value
        viewModelScope.launch { settings.setPair(s.source.code, s.target.code) }
    }

    fun onTranslate() {
        val s = _state.value
        if (s.text.isBlank() || s.loading) return
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        val tap = com.translator.app.core.util.Perf.now()
        Analytics.log(Ev.TEXT_TRANSLATE_STARTED) { lang("from", s.source.code); lang("to", s.target.code); bucket("chars", s.text.length.toLong()) }
        job = viewModelScope.launch {
            val request = TranslateRequest(s.text, s.source.code, s.target.code, s.tone.label)
            when (val outcome = translateText.translate(request)) {
                is TranslationOutcome.Success -> {
                    // Show the translation first; saving it to history happens right after and never holds it back.
                    _state.update {
                        it.copy(
                            loading = false,
                            insights = InsightsUiState.Idle,
                            result = TranslateResult(0, s.source, s.target, s.text, outcome.value.text, favorite = false, viaAi = outcome.value.viaAi),
                        )
                    }
                    successCount++
                    effects.trySend(TranslateEffect.Translated(successCount))
                    com.translator.app.core.util.Perf.log("translate.total", tap, "${s.text.length} chars")
                    Analytics.log(Ev.TEXT_TRANSLATE_SUCCESS) { lang("from", s.source.code); lang("to", s.target.code); duration("t", com.translator.app.core.util.Perf.now() - tap); flag("ai", outcome.value.viaAi) }
                    val id = translateText.save(request, outcome.value.text)
                    _state.update { st ->
                        val r = st.result
                        if (r != null && r.historyId == 0L && r.sourceText == s.text && r.text == outcome.value.text) st.copy(result = r.copy(historyId = id)) else st
                    }
                }
                is TranslationOutcome.Failure -> {
                    Analytics.log(Ev.TEXT_TRANSLATE_FAILED) { lang("from", s.source.code); lang("to", s.target.code); token("reason", outcome.error.reason()) }
                    _state.update { it.copy(loading = false, error = outcome.error) }
                }
            }
        }
    }

    /** Drops a translation that is still being fetched, because the text, the languages or the style it was asked for changed. */
    private fun cancelPending() {
        if (!_state.value.loading) return
        job?.cancel()
        _state.update { it.copy(loading = false) }
    }

    fun onToggleFavorite() {
        val r = _state.value.result ?: return
        if (r.historyId == 0L) return   // still being saved: a moment later it can be starred
        if (!r.favorite) Analytics.log(Ev.RESULT_FAVORITED) { token("source", "text") }
        viewModelScope.launch { history.setFavorite(r.historyId, !r.favorite) }
    }

    /** Goes back to the text box with the text kept, so it can be changed and translated again. */
    fun onEditText() {
        insightsJob?.cancel()
        _state.update { it.copy(result = null, error = null, insights = InsightsUiState.Idle) }
    }

    fun onNewTranslation() {
        insightsJob?.cancel()
        _state.update { it.copy(text = "", result = null, error = null, insights = InsightsUiState.Idle) }
    }

    /** Closes the insights, and stops them if they are still loading. */
    fun onCloseInsights() {
        insightsJob?.cancel()
        _state.update { it.copy(insights = InsightsUiState.Idle) }
    }

    /** Asks the AI for meaning, variants, examples and synonyms. Only runs when the user taps for it. */
    fun onLoadInsights() {
        val r = _state.value.result ?: return
        if (_state.value.insights is InsightsUiState.Loading) return
        insightsJob?.cancel()
        _state.update { it.copy(insights = InsightsUiState.Loading) }
        insightsJob = viewModelScope.launch {
            val outcome = insightsSource.insights(InsightsRequest(r.sourceText, r.text, r.source.code, r.target.code))
            _state.update {
                it.copy(
                    insights = when (outcome) {
                        is InsightsOutcome.Success -> InsightsUiState.Loaded(outcome.value)
                        is InsightsOutcome.Failure -> InsightsUiState.Failed(outcome.error)
                    },
                )
            }
        }
    }
}

/** One defined UI message per error, from the design spec's error table. */
fun TranslationError.message(): String = when (this) {
    TranslationError.Network -> str(R.string.no_internet_connection)
    TranslationError.Timeout -> str(R.string.that_took_too_long)
    is TranslationError.RateLimited -> str(R.string.too_many_requests_you_can, retryAfterSec) + "\n" + str(R.string.rate_limit_ai_hint)
    is TranslationError.Validation -> message
    TranslationError.Auth -> str(R.string.please_try_again)
    is TranslationError.Server -> str(R.string.something_went_wrong_on_our, code)
    is TranslationError.Unknown -> str(R.string.something_went_wrong_please_try)
    TranslationError.AiUnavailable -> str(R.string.ai_translation_isn_t_set)
}
