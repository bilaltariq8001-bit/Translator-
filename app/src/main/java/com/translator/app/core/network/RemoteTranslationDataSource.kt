package com.translator.app.core.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

private const val MAX_TEXT_CHARS = 5_000

/**
 * Online translation on the provided [TranslateApi].
 *
 * - A plain suspend call on [dispatcher]; the caller's scope owns the work. Cancelling it cancels the HTTP call and
 *   any backoff wait, and a [CancellationException] is always rethrown, never mapped to an error.
 * - [timeoutMs] bounds one attempt (15 s for translate); the client's connect timeout (10 s) is set where it is built.
 * - Only Network, Timeout and retryable Server errors are retried, at most [backoffMs].size times (1 s, then 2 s,
 *   plus jitter). Validation, Auth and RateLimited are never retried.
 * - Never logs or stores the text.
 */
class RemoteTranslationDataSource(
    private val api: TranslateApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Long = 15_000,
    private val backoffMs: List<Long> = listOf(1_000, 2_000),
    private val jitterMs: () -> Long = { Random.nextLong(0, 250) },
    private val clock: () -> Long = System::currentTimeMillis,
    maxParallel: Int = 2,
) : TranslationDataSource {

    // The free translation service answers "too many requests" when it is hit in bursts, and keeps answering it for a while
    // if it is hit again. So after one such answer no request is sent until the wait is over (the screen then shows the true
    // time left), and at most [maxParallel] requests are ever in flight together.
    @Volatile private var blockedUntil = 0L
    private val gate = kotlinx.coroutines.sync.Semaphore(maxParallel)

    fun warmUp() = api.warmUp()

    override suspend fun translate(request: TranslateRequest): TranslationOutcome {
        if (request.text.length > MAX_TEXT_CHARS) {
            return TranslationOutcome.Failure(TranslationError.Validation("Text is longer than 5,000 characters"))
        }
        val waitMs = blockedUntil - clock()
        if (waitMs > 0) return TranslationOutcome.Failure(TranslationError.RateLimited(((waitMs + 999) / 1000).toInt()))
        var retries = 0
        while (true) {
            val outcome = gate.withPermit { attempt(request) }
            val error = (outcome as? TranslationOutcome.Failure)?.error ?: return outcome
            if (error is TranslationError.RateLimited) blockedUntil = clock() + error.retryAfterSec * 1_000L
            if (!error.isRetryable() || retries >= backoffMs.size) return outcome
            delay(backoffMs[retries] + jitterMs())
            retries++
        }
    }

    private suspend fun attempt(request: TranslateRequest): TranslationOutcome = try {
        val t0 = com.translator.app.core.util.Perf.now()
        val text = withContext(dispatcher) {
            withTimeoutOrNull(timeoutMs) { api.translate(request.text, request.sourceLang, request.targetLang) }
        }
        com.translator.app.core.util.Perf.log("http.translate", t0)
        if (text == null) {
            TranslationOutcome.Failure(TranslationError.Timeout)
        } else {
            TranslationOutcome.Success(Translation(text, detectedLang = null))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TranslationOutcome.Failure(e.toTranslationError())
    }
}

private fun TranslationError.isRetryable(): Boolean = when (this) {
    TranslationError.Network, TranslationError.Timeout -> true
    is TranslationError.Server -> retryable
    else -> false
}

internal fun Exception.toTranslationError(): TranslationError = when (this) {
    is ApiHttpException -> when (code) {
        429 -> TranslationError.RateLimited(retryAfterSec ?: 30)
        400, 422 -> TranslationError.Validation("Please check the text and try again")
        401, 403 -> TranslationError.Auth
        in 500..599 -> TranslationError.Server(code, retryable = code in listOf(502, 503, 504))
        else -> TranslationError.Unknown(this)
    }
    is SocketTimeoutException -> TranslationError.Timeout
    is IOException -> TranslationError.Network
    is JSONException -> TranslationError.Unknown(this)
    else -> TranslationError.Unknown(this)
}
