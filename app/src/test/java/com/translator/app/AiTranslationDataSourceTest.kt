package com.translator.app

import com.translator.app.core.ai.AiNotConfiguredException
import com.translator.app.core.ai.AiTextGenerator
import com.translator.app.core.ai.AiTranslationDataSource
import com.translator.app.core.ai.SwitchingTranslationDataSource
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.Translation
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AiTranslationDataSourceTest {
    private val request = TranslateRequest("Where is the station?", "en", "es", "Business")

    private class FakeGenerator(val answer: suspend (String, String) -> String) : AiTextGenerator {
        var system = ""
        var user = ""
        override suspend fun generate(system: String, user: String): String {
            this.system = system
            this.user = user
            return answer(system, user)
        }
    }

    private fun failure(o: TranslationOutcome) = (o as TranslationOutcome.Failure).error

    @Test
    fun success_trimsAndStripsQuotes() = runBlocking {
        val g = FakeGenerator { _, _ -> "  \"¿Dónde está la estación?\"\n" }
        val o = AiTranslationDataSource(g).translate(request)
        assertEquals("¿Dónde está la estación?", (o as TranslationOutcome.Success).value.text)
    }

    @Test
    fun prompt_carriesLanguagesAndTone_andKeepsUserTextSeparate() = runBlocking {
        val g = FakeGenerator { _, _ -> "ok" }
        AiTranslationDataSource(g).translate(request)
        assertTrue(g.system.contains("English") && g.system.contains("Spanish") && g.system.contains("business"))
        assertEquals("Where is the station?", g.user)
        assertFalse(g.system.contains("Where is the station?"))
    }

    @Test
    fun blankAnswer_isAnError() = runBlocking {
        val o = AiTranslationDataSource(FakeGenerator { _, _ -> "   " }).translate(request)
        assertTrue(failure(o) is TranslationError.Unknown)
    }

    @Test
    fun notConfigured_mapsToAiUnavailable() = runBlocking {
        val o = AiTranslationDataSource(FakeGenerator { _, _ -> throw AiNotConfiguredException() }).translate(request)
        assertEquals(TranslationError.AiUnavailable, failure(o))
    }

    @Test
    fun ioFailure_mapsToNetwork() = runBlocking {
        val o = AiTranslationDataSource(FakeGenerator { _, _ -> throw IOException("down") }).translate(request)
        assertEquals(TranslationError.Network, failure(o))
    }

    @Test
    fun quotaException_mapsToRateLimited() = runBlocking {
        class QuotaExceededException : Exception("quota")
        val o = AiTranslationDataSource(FakeGenerator { _, _ -> throw QuotaExceededException() }).translate(request)
        assertEquals(TranslationError.RateLimited(60), failure(o))
    }

    @Test
    fun slowModel_timesOut() = runBlocking {
        val o = AiTranslationDataSource(FakeGenerator { _, _ -> awaitCancellation() }, timeoutMs = 50).translate(request)
        assertEquals(TranslationError.Timeout, failure(o))
    }

    @Test
    fun overlongText_isRejectedWithoutCallingTheModel() = runBlocking {
        val g = FakeGenerator { _, _ -> "x" }
        val o = AiTranslationDataSource(g).translate(request.copy(text = "a".repeat(5_001)))
        assertTrue(failure(o) is TranslationError.Validation)
        assertEquals("", g.user)
    }

    private fun source(label: String, fail: TranslationError? = null) = object : TranslationDataSource {
        var calls = 0
        override suspend fun translate(request: TranslateRequest): TranslationOutcome {
            calls++
            return if (fail != null) TranslationOutcome.Failure(fail) else TranslationOutcome.Success(Translation(label, null))
        }
    }

    @Test
    fun switching_plainTextUsesTheFreeTranslator() = runBlocking {
        val s = SwitchingTranslationDataSource(source("ai"), source("standard"))
        val r = s.translate(TranslateRequest("hi", "en", "es", "General")) as TranslationOutcome.Success
        assertEquals("standard", r.value.text)
        assertFalse(r.value.viaAi)
    }

    @Test
    fun switching_aStyleIsTheAiJob() = runBlocking {
        val s = SwitchingTranslationDataSource(source("ai"), source("standard"))
        assertEquals("ai", (s.translate(TranslateRequest("hi", "en", "es", "Casual")) as TranslationOutcome.Success).value.text)
    }

    @Test
    fun switching_aStyleFallsBackToPlainWhenTheAiCannotAnswer() = runBlocking {
        val s = SwitchingTranslationDataSource(source("ai", TranslationError.RateLimited(60)), source("standard"))
        assertEquals("standard", (s.translate(TranslateRequest("hi", "en", "es", "Casual")) as TranslationOutcome.Success).value.text)
    }

    @Test
    fun switching_aBusyFreeTranslatorIsBackedUpByTheAi_andSaysSo() = runBlocking {
        val s = SwitchingTranslationDataSource(source("ai"), source("standard", TranslationError.RateLimited(30)))
        val r = s.translate(TranslateRequest("hi", "en", "es", "General")) as TranslationOutcome.Success
        assertEquals("ai", r.value.text)
        assertTrue(r.value.viaAi)
    }

    @Test
    fun switching_noInternetIsNotHiddenByTheAi() = runBlocking {
        val ai = source("ai")
        val s = SwitchingTranslationDataSource(ai, source("standard", TranslationError.Network))
        assertTrue(s.translate(TranslateRequest("hi", "en", "es", "General")) is TranslationOutcome.Failure)
        assertEquals(0, ai.calls)
    }

    @Test
    fun switching_aDownloadedPackIsPreferredToTheAi() = runBlocking {
        val ai = source("ai")
        val s = SwitchingTranslationDataSource(ai, source("standard", TranslationError.RateLimited(30)), fallbackToAi = { false })
        assertTrue(s.translate(TranslateRequest("hi", "en", "es", "General")) is TranslationOutcome.Failure)
        assertEquals(0, ai.calls)
    }
}
