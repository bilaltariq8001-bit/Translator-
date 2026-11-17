package com.translator.app.core.ai

import android.graphics.BitmapFactory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** The AI service the app talks to. Every AI feature (translation now, insights and tools later) goes through this. */
interface AiTextGenerator {
    /** Returns the model's text for [user], steered by [system]. Throws on failure; callers map the error. */
    suspend fun generate(system: String, user: String): String

    /**
     * Streams the model's reply to a conversation ([turns] ends with the user's latest message), chunk by chunk.
     * The default answers in one piece, so fakes only need [generate].
     */
    fun stream(system: String, turns: List<AiTurn>): Flow<String> = flow { emit(generate(system, turns.last().text)) }

    /** Sends a photo (JPEG bytes) with [prompt] and returns the model's text. Fakes that do not read images throw. */
    suspend fun generateFromImage(system: String, prompt: String, jpeg: ByteArray): String =
        throw UnsupportedOperationException("This generator does not read images")
}

/** One message of a conversation sent to the model. */
data class AiTurn(val fromUser: Boolean, val text: String)

/** Thrown when Firebase is not configured in this build (no google-services.json). */
class AiNotConfiguredException(cause: Throwable? = null) : IllegalStateException("AI is not set up in this build", cause)

/**
 * Firebase AI Logic with the Gemini Developer API. Needs google-services.json in the app module; without it
 * [generate] throws [AiNotConfiguredException]. No keys live in code: Firebase reads its config from that file.
 * Never logs the prompt or the answer.
 *
 * Gemini models are retired and overloaded from time to time, so a request tries the models in [models] one after another
 * and moves on when a model is retired, busy, or silent for too long.
 */
class FirebaseAiTextGenerator(private val models: List<String> = DEFAULT_MODELS) : AiTextGenerator {

    // One model object per model name and instruction, instead of building a new one for every request.
    private val modelCache = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, com.google.firebase.ai.GenerativeModel>()

    private fun modelFor(name: String, system: String) = try {
        modelCache.getOrPut(name to system) {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName = name, systemInstruction = content { text(system) })
        }
    } catch (e: IllegalStateException) {
        // FirebaseApp.getInstance() throws this when the default app was never initialised.
        throw AiNotConfiguredException(e)
    }

    /** Runs [call] on each model in turn until one answers; only "this model cannot help right now" errors move on. */
    private suspend fun <T> onFirstWorkingModel(call: suspend (String) -> T): T {
        var last: Exception? = null
        for (name in models) {
            try {
                return withTimeout(GENERATE_MS) { call(name) }
            } catch (e: TimeoutCancellationException) {
                last = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The SDK wraps our own timeout in its UnknownException, so look through the causes.
                if (!e.isTimeout() && !e.canTryAnotherModel()) throw e
                last = e
            }
        }
        throw last ?: IllegalStateException("No AI model is configured")
    }

    override suspend fun generate(system: String, user: String): String =
        onFirstWorkingModel { name -> modelFor(name, system).generateContent(user).text.orEmpty() }

    override suspend fun generateFromImage(system: String, prompt: String, jpeg: ByteArray): String {
        val bitmap = try { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) } catch (_: OutOfMemoryError) { null } ?: throw IllegalArgumentException("Not an image")
        return onFirstWorkingModel { name -> modelFor(name, system).generateContent(content { image(bitmap); text(prompt) }).text.orEmpty() }
    }

    override fun stream(system: String, turns: List<AiTurn>): Flow<String> = flow {
        val history = turns.dropLast(1).map { t -> content(role = if (t.fromUser) "user" else "model") { text(t.text) } }
        var last: Exception? = null
        for (name in models) {
            var started = false
            var attempt = 0
            val maxAttempts = 3
            while (attempt < maxAttempts) {
                try {
                    modelFor(name, system).startChat(history).sendMessageStream(turns.last().text)
                        .mapNotNull { it.text }
                        .withFirstItemTimeout(FIRST_WORD_MS)
                        .collect { started = true; emit(it) }
                    return@flow
                } catch (e: TimeoutCancellationException) {
                    if (started) throw e
                    last = e
                    attempt++
                    if (attempt < maxAttempts) {
                        kotlinx.coroutines.delay(1000L * (1L shl (attempt - 1))) // 1s, 2s, 4s
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Once words have reached the screen the answer cannot switch models half way.
                    if (started || (!e.isTimeout() && !e.canTryAnotherModel() && !e.isTransientNetworkError())) throw e
                    last = e
                    attempt++
                    if (attempt < maxAttempts) {
                        kotlinx.coroutines.delay(1000L * (1L shl (attempt - 1)))
                    }
                }
            }
        }
        throw last ?: IllegalStateException("No AI model is configured")
    }

    companion object {
        /** The lighter, faster model first (these tasks are short), then the full model. "latest" names follow Google's current model. */
        val DEFAULT_MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-3.8-flash")
        /** A whole answer from one model may take this long before the next model is tried. */
        private const val GENERATE_MS = 20_000L

        /** A chat reply must START (first words) within this, or the next model is tried: replies usually start in a couple of seconds. */
        private const val FIRST_WORD_MS = 12_000L
    }
}

/** True if this error, or one of its causes, is a timeout. */
internal fun Throwable.isTimeout(): Boolean =
    generateSequence(this) { it.cause }.take(5).any { it is TimeoutCancellationException || (it.message ?: "").startsWith("Timed out waiting") }

/** True for failures that another model might not have: retired, busy, unavailable or not found. */
private fun Exception.canTryAnotherModel(): Boolean {
    if (this is AiNotConfiguredException || this is IOException) return false
    val text = (message ?: "").lowercase()
    if (this::class.simpleName == "QuotaExceededException") return true
    return when (this::class.simpleName) {
        "ServerException", "ServiceDisabledException", "UnknownException" ->
            listOf("high demand", "no longer available", "not found", "overloaded", "unavailable", "try again later", "deprecated").any { it in text }
        else -> false
    }
}

/** True if this error represents a temporary network failure or HTTP 502/5xx. */
internal fun Throwable.isTransientNetworkError(): Boolean {
    val text = (message ?: "").lowercase()
    return this is IOException || text.contains("502") || text.contains("503") || text.contains("504") || text.contains("overloaded") || text.contains("timeout") || text.contains("connection")
}

/** Fails with a timeout if the first item takes longer than [ms]; later items are not timed. */
private fun <T> Flow<T>.withFirstItemTimeout(ms: Long): Flow<T> = flow {
    coroutineScope {
        val channel = produceIn(this)
        try {
            val first = withTimeout(ms) { channel.receiveCatching() }
            if (first.isClosed) {
                first.exceptionOrNull()?.let { throw it }
                return@coroutineScope
            }
            emit(first.getOrThrow())
            for (item in channel) emit(item)
        } finally {
            channel.cancel()
        }
    }
}
