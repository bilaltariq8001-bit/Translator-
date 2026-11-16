package com.translator.app

import com.translator.app.core.network.CachingTranslationDataSource
import com.translator.app.core.network.Translation
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachingTranslationDataSourceTest {
    private class Counting(var fail: Boolean = false) : TranslationDataSource {
        var calls = 0
        override suspend fun translate(request: TranslateRequest): TranslationOutcome {
            calls++
            return if (fail) TranslationOutcome.Failure(TranslationError.Network) else TranslationOutcome.Success(Translation("T$calls:" + request.text, null))
        }
    }

    private fun req(text: String = "hello", to: String = "es", tone: String = "General") = TranslateRequest(text, "en", to, tone)

    @Test fun sameRequest_isAnsweredOnce() = runBlocking {
        val inner = Counting()
        val cache = CachingTranslationDataSource(inner)
        val first = cache.translate(req()) as TranslationOutcome.Success
        val second = cache.translate(req()) as TranslationOutcome.Success
        assertEquals(1, inner.calls)
        assertEquals(first.value.text, second.value.text)
    }

    @Test fun anythingDifferent_goesToTheEngine() = runBlocking {
        val inner = Counting()
        val cache = CachingTranslationDataSource(inner)
        cache.translate(req()); cache.translate(req(text = "bye")); cache.translate(req(to = "fr")); cache.translate(req(tone = "Casual"))
        assertEquals(4, inner.calls)
    }

    @Test fun engineVariant_isPartOfTheKey() = runBlocking {
        val inner = Counting()
        var variant = "std"
        val cache = CachingTranslationDataSource(inner, { variant })
        cache.translate(req()); variant = "ai"; cache.translate(req())
        assertEquals(2, inner.calls)
    }

    @Test fun failures_areNeverStored() = runBlocking {
        val inner = Counting(fail = true)
        val cache = CachingTranslationDataSource(inner)
        assertTrue(cache.translate(req()) is TranslationOutcome.Failure)
        inner.fail = false
        assertTrue(cache.translate(req()) is TranslationOutcome.Success)
        assertEquals(2, inner.calls)
    }

    @Test fun oldestEntriesAreDropped() = runBlocking {
        val inner = Counting()
        val cache = CachingTranslationDataSource(inner, capacity = 2)
        cache.translate(req("a")); cache.translate(req("b")); cache.translate(req("c"))   // "a" falls out
        cache.translate(req("a"))
        assertEquals(4, inner.calls)
    }
}
