package com.translator.app.core.ai

import com.translator.app.core.network.TranslationError
import java.io.IOException

/**
 * One mapping from AI failures to the app's typed errors, shared by every AI feature. Firebase AI Logic exceptions are
 * matched by name so a library change never breaks compilation. Suppresses technical codes (like 502) in favor of friendly messages.
 */
internal fun Throwable.toAiError(): TranslationError = (this as? Exception ?: RuntimeException(this)).toAiError()

internal fun Exception.toAiError(): TranslationError {
    try {
        val chain = generateSequence<Throwable>(this) { it.cause }.take(4).joinToString(" <- ") { "${it::class.simpleName}: ${it.message?.take(300)}" }
        android.util.Log.w("AiError", chain)
    } catch (_: Throwable) {}
    return mapAiError()
}

private fun Exception.mapAiError(): TranslationError = when {
    this is AiNotConfiguredException -> TranslationError.AiUnavailable
    this is IOException -> TranslationError.Network
    this.isTimeout() -> TranslationError.Timeout
    else -> when (this::class.simpleName) {
        "QuotaExceededException" -> TranslationError.RateLimited(60)
        "TimeoutCancellationException" -> TranslationError.Timeout
        "ServerException", "ServiceDisabledException", "UnsupportedUserLocationException", "UnknownException" -> {
            val msg = (message ?: "").lowercase()
            if (msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("overloaded") || msg.contains("unavailable") || msg.contains("server")) {
                TranslationError.Validation("Sorry, I couldn't get a response right now. Please try again.")
            } else {
                TranslationError.Server(503, retryable = false)
            }
        }
        "PromptBlockedException", "ResponseStoppedException", "InvalidAPIKeyException" -> TranslationError.Validation("This text can't be handled by AI.")
        else -> TranslationError.Validation("Sorry, I couldn't get a response right now. Please try again.")
    }
}
