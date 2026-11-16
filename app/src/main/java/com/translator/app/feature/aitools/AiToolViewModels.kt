package com.translator.app.feature.aitools

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.analytics.reason
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import com.translator.app.core.data.ChatSession
import com.translator.app.core.data.ChatRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.core.ai.AiChatSource
import com.translator.app.core.ai.AiPhrase
import com.translator.app.core.ai.AiResult
import com.translator.app.core.ai.AiToolSource
import com.translator.app.core.ai.AiTurn
import com.translator.app.core.ai.AiWriterKind
import com.translator.app.core.ai.toAiError
import com.translator.app.core.network.TranslationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_INPUT = 5_000

// ---------------------------------------------------------------- Chat

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: TranslationError? = null,
)

class AiChatViewModel(private val chat: AiChatSource, private val repo: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Every saved conversation, newest first, for the chat history. */
    val sessions: StateFlow<List<ChatSession>> = repo.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var job: Job? = null
    private var nextId = 1L
    private var sessionId: Long? = null

    fun onInput(text: String) = _state.update { it.copy(input = text.take(MAX_INPUT)) }

    /**
     * Sends [text] (or the typed input) and streams the reply into a new assistant message. The conversation is saved as it
     * goes: the first message creates it, each question is stored at once, and each answer when it is complete (or when
     * the user stops it part way, so nothing that was already on screen is lost).
     */
    fun onSend(text: String = _state.value.input) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.sending) return
        val reply = ChatMessage(nextId + 1, fromUser = false, text = "")
        val turns = _state.value.messages.map { AiTurn(it.fromUser, it.text) } + AiTurn(true, message)
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(nextId, fromUser = true, text = message) + reply,
                input = "", sending = true, error = null,
            )
        }
        nextId += 2
        val tap = com.translator.app.core.util.Perf.now()
        Analytics.log(Ev.AI_TOOL_STARTED) { token("tool", "chat"); bucket("chars", message.length.toLong()) }
        job = viewModelScope.launch {
            val saved = withContext(NonCancellable) {
                try {
                    val id = sessionId ?: repo.createSession(message.replace('\n', ' ').take(60)).also { sessionId = it }
                    repo.addMessage(id, true, message)
                    id
                } catch (e: Exception) { null }
            }
            try {
                var chunks = 0
                chat.reply(turns).collect { chunk ->
                    if (chunks == 0) com.translator.app.core.util.Perf.log("chat.firstChunk", tap)
                    chunks++
                    append(reply.id, chunk)
                }
                com.translator.app.core.util.Perf.log("chat.complete", tap, "$chunks chunks")
                Analytics.log(Ev.AI_TOOL_SUCCESS) { token("tool", "chat"); duration("t", (com.translator.app.core.util.Perf.now() - tap)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val err = e.toAiError()
                Analytics.log(Ev.AI_TOOL_FAILED) { token("tool", "chat"); token("reason", err.reason()) }
                _state.update { it.copy(error = err) }
            } finally {
                val answer = _state.value.messages.firstOrNull { it.id == reply.id }?.text.orEmpty()
                if (saved != null && answer.isNotEmpty()) withContext(NonCancellable) { try { repo.addMessage(saved, false, answer) } catch (e: Exception) { } }
                _state.update { st ->
                    // An empty assistant bubble (failed or stopped before any text) is removed.
                    st.copy(sending = false, messages = st.messages.filterNot { it.id == reply.id && it.text.isEmpty() })
                }
            }
        }
    }

    fun onStop() { job?.cancel() }

    /** Starts a fresh conversation. The one on screen stays saved in the chat history. */
    fun onClear() {
        job?.cancel()
        sessionId = null
        _state.value = ChatUiState()
    }

    /** Opens a saved conversation so it can be read and continued. */
    fun onOpen(id: Long) {
        job?.cancel()
        viewModelScope.launch {
            val stored = repo.messages(id)
            sessionId = id
            nextId = 1L
            _state.value = ChatUiState(messages = stored.map { ChatMessage(nextId++, it.fromUser, it.text) })
        }
    }

    fun onFavorite(session: ChatSession) {
        viewModelScope.launch { repo.setFavorite(session.id, !session.favorite) }
    }

    /** Removes a saved conversation from this phone. */
    fun onDelete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            if (sessionId == id) {
                job?.cancel()
                sessionId = null
                _state.value = ChatUiState()
            }
        }
    }

    private fun append(id: Long, chunk: String) = _state.update { st ->
        st.copy(messages = st.messages.map { if (it.id == id) it.copy(text = it.text + chunk) else it })
    }
}

// ---------------------------------------------------------------- Email, grammar, slang

data class WriterUiState(
    val input: String = "",
    val option: String = "",
    val loading: Boolean = false,
    val result: String? = null,
    val error: TranslationError? = null,
)

class AiWriterViewModel(private val kind: AiWriterKind, private val source: AiToolSource) : ViewModel() {
    private val _state = MutableStateFlow(WriterUiState(option = defaultOption(kind)))
    val state: StateFlow<WriterUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onInput(text: String) = _state.update { it.copy(input = text.take(MAX_INPUT), error = null) }
    fun onOption(value: String) = _state.update { it.copy(option = value) }

    /** Runs the tool; also used for "Regenerate". */
    fun onRun() {
        val s = _state.value
        if (s.input.isBlank() || s.loading) return
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        val tool = kind.name.lowercase()
        Analytics.log(Ev.AI_TOOL_STARTED) { token("tool", tool); bucket("chars", s.input.length.toLong()) }
        job = viewModelScope.launch {
            when (val r = source.write(kind, s.input.trim(), s.option)) {
                is AiResult.Success -> { Analytics.log(Ev.AI_TOOL_SUCCESS) { token("tool", tool) }; _state.update { it.copy(loading = false, result = r.value) } }
                is AiResult.Failure -> { Analytics.log(Ev.AI_TOOL_FAILED) { token("tool", tool); token("reason", r.error.reason()) }; _state.update { it.copy(loading = false, error = r.error) } }
            }
        }
    }

    companion object {
        fun defaultOption(kind: AiWriterKind) = when (kind) {
            AiWriterKind.Email -> "Formal"
            AiWriterKind.Slang -> "Spanish"
            AiWriterKind.Grammar -> ""
        }
    }
}

// ---------------------------------------------------------------- Travel phrases

data class TravelUiState(
    val category: String = "Restaurant",
    val langCode: String = "",
    val loading: Boolean = false,
    val phrases: List<AiPhrase> = emptyList(),
    val error: TranslationError? = null,
)

val travelCategories = listOf("Restaurant", "Directions", "Hotel", "Emergency")

class AiTravelViewModel(private val source: AiToolSource) : ViewModel() {
    private val _state = MutableStateFlow(TravelUiState())
    val state: StateFlow<TravelUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onCategory(category: String) = _state.update { it.copy(category = category) }

    /** Loads phrases for the chosen category in [langCode]. Called when the user taps for them, never automatically. */
    fun onLoad(langCode: String) {
        if (_state.value.loading) return
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, langCode = langCode) }
        val category = _state.value.category
        Analytics.log(Ev.AI_TOOL_STARTED) { token("tool", "travel"); lang("lang", langCode) }
        job = viewModelScope.launch {
            when (val r = source.phrases(category, langCode)) {
                is AiResult.Success -> { Analytics.log(Ev.AI_TOOL_SUCCESS) { token("tool", "travel") }; _state.update { it.copy(loading = false, phrases = r.value) } }
                is AiResult.Failure -> { Analytics.log(Ev.AI_TOOL_FAILED) { token("tool", "travel"); token("reason", r.error.reason()) }; _state.update { it.copy(loading = false, error = r.error) } }
            }
        }
    }
}
