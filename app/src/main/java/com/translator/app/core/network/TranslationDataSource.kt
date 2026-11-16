package com.translator.app.core.network

data class TranslateRequest(val text: String, val sourceLang: String /* or "auto" */, val targetLang: String, val tone: String)

/** [viaAi] is true when the AI model answered because the free translator could not (the screen says so). */
data class Translation(val text: String, val detectedLang: String?, val viaAi: Boolean = false)

sealed interface TranslationOutcome {
    data class Success(val value: Translation) : TranslationOutcome
    data class Failure(val error: TranslationError) : TranslationOutcome
}

sealed interface TranslationError {
    data object Network : TranslationError
    data object Timeout : TranslationError
    data class RateLimited(val retryAfterSec: Int) : TranslationError
    data class Validation(val message: String) : TranslationError
    data object Auth : TranslationError
    data class Server(val code: Int, val retryable: Boolean) : TranslationError
    data class Unknown(val cause: Throwable?) : TranslationError

    /** AI translation is on but Firebase is not configured in this build. */
    data object AiUnavailable : TranslationError
}

/**
 * The only door to the translation service. The real implementation (RemoteTranslationDataSource) is written on the
 * API client the manager provides; until then [FakeTranslationDataSource] stands in.
 */
interface TranslationDataSource {
    suspend fun translate(request: TranslateRequest): TranslationOutcome
}
