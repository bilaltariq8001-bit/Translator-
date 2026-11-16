package com.translator.app

import com.translator.app.core.network.ApiHttpException
import com.translator.app.core.network.RemoteTranslationDataSource
import com.translator.app.core.network.TranslateApi
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class RemoteTranslationDataSourceTest {
    private val request = TranslateRequest("Hello", "en", "es", "General")

    /** Plays back one step per call; the last step repeats. */
    private class ScriptedApi(private vararg val steps: suspend () -> String) : TranslateApi {
        var calls = 0
        override suspend fun translate(text: String, sourceCode: String, targetCode: String): String =
            steps[minOf(calls++, steps.lastIndex)]()
    }

    private fun source(api: TranslateApi, timeoutMs: Long = 5_000, backoff: List<Long> = listOf(0, 0)) =
        RemoteTranslationDataSource(api, Dispatchers.Unconfined, timeoutMs, backoff, jitterMs = { 0 })

    private fun failure(o: TranslationOutcome) = (o as TranslationOutcome.Failure).error

    @Test
    fun success_returnsTranslation() = runBlocking {
        val o = source(ScriptedApi({ "Hola" })).translate(request)
        assertEquals("Hola", (o as TranslationOutcome.Success).value.text)
    }

    @Test
    fun statusCodes_mapToErrors() = runBlocking {
        fun errorFor(code: Int, retryAfter: Int? = null) =
            failure(runBlocking { source(ScriptedApi({ throw ApiHttpException(code, retryAfter) }), backoff = emptyList()).translate(request) })

        assertEquals(TranslationError.RateLimited(12), errorFor(429, 12))
        assertEquals(TranslationError.RateLimited(30), errorFor(429))
        assertTrue(errorFor(400) is TranslationError.Validation)
        assertTrue(errorFor(422) is TranslationError.Validation)
        assertEquals(TranslationError.Auth, errorFor(401))
        assertEquals(TranslationError.Auth, errorFor(403))
        assertEquals(TranslationError.Server(500, retryable = false), errorFor(500))
        assertEquals(TranslationError.Server(503, retryable = true), errorFor(503))
        assertTrue(errorFor(404) is TranslationError.Unknown)
    }

    @Test
    fun ioProblems_mapToNetworkAndTimeout() = runBlocking {
        val net = source(ScriptedApi({ throw IOException("no route") }), backoff = emptyList()).translate(request)
        assertEquals(TranslationError.Network, failure(net))
        val slow = source(ScriptedApi({ throw SocketTimeoutException() }), backoff = emptyList()).translate(request)
        assertEquals(TranslationError.Timeout, failure(slow))
    }

    @Test
    fun retryableError_isRetriedThenSucceeds() = runBlocking {
        val api = ScriptedApi({ throw ApiHttpException(503) }, { "Hola" })
        val o = source(api).translate(request)
        assertEquals(2, api.calls)
        assertTrue(o is TranslationOutcome.Success)
    }

    @Test
    fun retries_stopAfterTwo() = runBlocking {
        val api = ScriptedApi({ throw IOException() })
        val o = source(api).translate(request)
        assertEquals(3, api.calls)
        assertEquals(TranslationError.Network, failure(o))
    }

    @Test
    fun validationAuthAndRateLimit_areNotRetried() = runBlocking {
        for (code in listOf(400, 401, 429)) {
            val api = ScriptedApi({ throw ApiHttpException(code) })
            source(api).translate(request)
            assertEquals("code $code", 1, api.calls)
        }
    }

    @Test
    fun slowCall_timesOut() = runBlocking {
        val api = ScriptedApi({ awaitNever() })
        val o = source(api, timeoutMs = 50, backoff = emptyList()).translate(request)
        assertEquals(TranslationError.Timeout, failure(o))
    }

    @Test
    fun overlongText_isRejectedWithoutCallingTheApi() = runBlocking {
        val api = ScriptedApi({ "x" })
        val o = source(api).translate(request.copy(text = "a".repeat(5_001)))
        assertTrue(failure(o) is TranslationError.Validation)
        assertEquals(0, api.calls)
    }

    @Test
    fun cancellation_cancelsTheCall_andIsNotRetried() = runBlocking {
        var cancelled = false
        val api = object : TranslateApi {
            var calls = 0
            override suspend fun translate(text: String, sourceCode: String, targetCode: String): String {
                calls++
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val job = async(start = CoroutineStart.UNDISPATCHED) { source(api).translate(request) }
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertEquals(1, api.calls)
        assertTrue(job.isCancelled)
    }

    private suspend fun awaitNever(): String = awaitCancellation()
}
