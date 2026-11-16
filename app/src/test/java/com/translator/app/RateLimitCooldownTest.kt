package com.translator.app

import com.translator.app.core.network.ApiHttpException
import com.translator.app.core.network.RemoteTranslationDataSource
import com.translator.app.core.network.TranslateApi
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitCooldownTest {
    private class Api(var limited: Boolean = true) : TranslateApi {
        var calls = 0
        override suspend fun translate(text: String, sourceCode: String, targetCode: String): String {
            calls++
            if (limited) throw ApiHttpException(429, 12)
            return "ok"
        }
    }

    private val request = TranslateRequest("hello", "en", "es", "General")

    @Test fun afterTooManyRequests_nothingIsSentUntilTheWaitIsOver() = runBlocking {
        var now = 1_000_000L
        val api = Api()
        val source = RemoteTranslationDataSource(api, Dispatchers.Unconfined, clock = { now })

        val first = source.translate(request) as TranslationOutcome.Failure
        assertEquals(TranslationError.RateLimited(12), first.error)
        assertEquals(1, api.calls)

        now += 5_000                                        // still inside the 12 s wait
        val second = source.translate(request) as TranslationOutcome.Failure
        assertEquals(TranslationError.RateLimited(7), second.error)   // the true time left
        assertEquals(1, api.calls)                           // and no request was sent

        now += 8_000                                        // the wait is over
        api.limited = false
        assertTrue(source.translate(request) is TranslationOutcome.Success)
        assertEquals(2, api.calls)
    }
}
